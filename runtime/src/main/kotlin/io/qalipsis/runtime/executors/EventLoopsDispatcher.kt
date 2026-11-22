/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.runtime.executors

import io.qalipsis.api.logging.LoggerHelper.logger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.cancel
import java.util.ArrayDeque
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Coroutine dispatcher made of independent single-threaded event loops, each owning its task queues and its timers.
 *
 * Unlike a [java.util.concurrent.ScheduledThreadPoolExecutor], which serializes all the dispatches and timers of
 * its threads on one locked queue, nothing is shared between the loops:
 * - a task dispatched from a loop stays on that loop, without synchronization nor wake-up;
 * - a task dispatched from another thread is pushed to a random loop, through a lock-free queue;
 * - a timer expires on the loop owning it and is run there directly, so timers due at the same time are
 *   executed by all the loops in parallel, keeping the precision of the delays.
 *
 * There is no work stealing: a task blocking its thread delays all the other tasks of its loop.
 * The dispatcher is therefore only suitable for non-blocking code.
 *
 * @author Eric Jessé
 */
@OptIn(InternalCoroutinesApi::class)
internal class EventLoopsDispatcher(
    private val name: String,
    threadsCount: Int
) : ExecutorCoroutineDispatcher(), Delay {

    private val loops = Array(threadsCount) { EventLoop(this, "$name-${it + 1}") }

    /**
     * Set by [close]: the tasks dispatched afterwards are not accepted by the loops any more.
     */
    @Volatile
    private var closed = false

    override val executor: Executor = Executor { dispatch(EmptyCoroutineContext, it) }

    init {
        require(threadsCount > 0) { "The count of threads should be positive, but was $threadsCount" }
        loops.forEach(Thread::start)
    }

    /**
     * Enqueues [block] on the current loop when called from one of the loops of this dispatcher, or on a random loop
     * otherwise.
     *
     * After [close], the job of [context] is cancelled and [block] is executed on [Dispatchers.IO], so that the
     * coroutine can complete its cancellation, as the dispatchers of kotlinx.coroutines do when their executor
     * rejects a task.
     */
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (closed) {
            context.cancel(java.util.concurrent.CancellationException("The dispatcher $name was closed"))
            Dispatchers.IO.dispatch(context, block)
        } else {
            currentLoop()?.enqueueLocally(block) ?: randomLoop().enqueueExternally(block)
        }
    }

    /**
     * Implementation of `delay`: resumes [continuation] after [timeMillis] on the loop owning the timer, without
     * dispatching it again, since it already runs on a thread of this dispatcher.
     * The timer is disposed when the continuation is cancelled before.
     */
    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val timer = schedule(timeMillis) { with(continuation) { resumeUndispatched(Unit) } }
        continuation.invokeOnCancellation { timer.dispose() }
    }

    /**
     * Implementation of `withTimeout`: executes [block] after [timeMillis] on a loop of this dispatcher, unless the
     * returned handle is disposed before.
     */
    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle =
        schedule(timeMillis, block)

    /**
     * Creates a timer executing [task] after [timeMillis] and registers it on a loop.
     *
     * From a loop, the timer is added to the timers of that loop directly. From another thread, its registration is
     * sent as a task to a random loop, since the timers of a loop are only accessed by its own thread. In both cases,
     * the deadline is computed now, so the time to register the timer does not delay it.
     */
    private fun schedule(timeMillis: Long, task: Runnable): Timer {
        val timer = Timer(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeMillis.coerceAtLeast(0)), task)
        currentLoop()?.addTimer(timer) ?: randomLoop().enqueueExternally { (Thread.currentThread() as EventLoop).addTimer(timer) }
        return timer
    }

    /**
     * Returns the loop running the current thread, or null when the current thread is not a loop of this dispatcher
     * (the loops of other instances are ignored).
     */
    private fun currentLoop() = (Thread.currentThread() as? EventLoop)?.takeIf { it.owner === this }

    /**
     * Returns a loop chosen at random, to spread the tasks submitted by other threads over the loops without a shared
     * counter, which would be contended.
     */
    private fun randomLoop() = loops[ThreadLocalRandom.current().nextInt(loops.size)]

    /**
     * Stops the loops after their current turn. The tasks and timers still queued are not executed.
     */
    override fun close() {
        closed = true
        loops.forEach(EventLoop::shutdown)
    }

    override fun toString() = "EventLoopsDispatcher($name, ${loops.size} loops)"

    /**
     * Task to execute at [deadline], expressed in nanoseconds of [System.nanoTime].
     * A disposed timer is only marked and removed from its loop when due or when the loop purges its timers.
     */
    private class Timer(val deadline: Long, val task: Runnable) : Comparable<Timer>, DisposableHandle {

        @Volatile
        var disposed = false

        override fun dispose() {
            disposed = true
        }

        override fun compareTo(other: Timer) = deadline.compareTo(other.deadline)
    }

    /**
     * Thread of the dispatcher running its own tasks and timers, one after the other.
     *
     * Each turn of the loop:
     * 1. runs the timers that are due;
     * 2. moves up to [EXTERNAL_BATCH_SIZE] tasks from the external queue to the local queue, then runs all the tasks
     *    of the local queue present at the start of the run;
     * 3. parks the thread when no task is left, until the next deadline of a timer or until a task is submitted by
     *    another thread.
     *
     * The local queue and the timers are only accessed by the thread of the loop, so they need no synchronization.
     * Only the external queue and the [parked] flag are shared with other threads.
     *
     * @property owner the dispatcher creating the loop, to recognize its own threads in [currentLoop].
     */
    private class EventLoop(val owner: EventLoopsDispatcher, name: String) : Thread(name) {

        /**
         * Tasks submitted by other threads.
         */
        private val external = ConcurrentLinkedQueue<Runnable>()

        /**
         * Tasks dispatched from the loop itself, only accessed by its own thread.
         */
        private val local = ArrayDeque<Runnable>()

        /**
         * Timers of the loop, only accessed by its own thread.
         */
        private val timers = PriorityQueue<Timer>()

        /**
         * Count of timers above which the disposed ones are removed, adapted after each purge so that the purges
         * stay rare when many timers are active.
         */
        private var timersPurgeThreshold = MIN_TIMERS_PURGE_THRESHOLD

        /**
         * True while the thread is parked or about to park, so that a submitting thread knows it has to wake it up.
         */
        private val parked = AtomicBoolean()

        /**
         * Set to false by [shutdown] to end the loop after its current turn.
         */
        @Volatile
        private var running = true

        init {
            isDaemon = true
        }

        /**
         * Enqueues [task] from the thread of the loop itself: no synchronization and no wake-up are needed, since
         * the loop is running.
         */
        fun enqueueLocally(task: Runnable) {
            local.addLast(task)
        }

        /**
         * Enqueues [task] from another thread and wakes the loop up if it is parked.
         *
         * The task is offered before [parked] is read, and [park] sets [parked] before checking the external queue:
         * either the loop sees the task before parking, or this function sees the flag and unparks the loop. The
         * compare-and-set ensures that only one of the concurrent submitters calls [LockSupport.unpark].
         */
        fun enqueueExternally(task: Runnable) {
            external.offer(task)
            if (parked.get() && parked.compareAndSet(true, false)) {
                LockSupport.unpark(this)
            }
        }

        /**
         * Adds [timer] to the timers of the loop, only from the thread of the loop.
         *
         * Disposed timers are not removed when they are disposed, since that would require a synchronized removal
         * from the queue. They are skipped when due, and removed in bulk once the queue exceeds
         * [timersPurgeThreshold], which bounds the memory used by many cancelled timeouts.
         */
        fun addTimer(timer: Timer) {
            timers.add(timer)
            if (timers.size > timersPurgeThreshold) {
                timers.removeIf(Timer::disposed)
                timersPurgeThreshold = (timers.size * 2).coerceAtLeast(MIN_TIMERS_PURGE_THRESHOLD)
            }
        }

        /**
         * Requests the loop to stop and wakes it up in case it is parked.
         */
        fun shutdown() {
            running = false
            LockSupport.unpark(this)
        }

        /**
         * Executes the turns of the loop until [shutdown] is called.
         */
        override fun run() {
            while (running) {
                runDueTimers()
                runQueuedTasks()
                if (local.isEmpty() && external.isEmpty()) {
                    park()
                }
            }
        }

        /**
         * Runs the timers whose deadline passed, in the order of their deadlines, and drops the disposed ones.
         */
        private fun runDueTimers() {
            val now = System.nanoTime()
            while (timers.peek()?.let { it.deadline <= now } == true) {
                val timer = timers.poll()
                if (!timer.disposed) {
                    runSafely(timer.task)
                }
            }
        }

        /**
         * Runs the tasks queued so far, the ones added meanwhile are kept for the next turn, after the due timers.
         *
         * Taking a bounded batch from the external queue and only the tasks present at the start of the run keeps
         * a turn short, even when the tasks enqueue new tasks (e.g. coroutines calling `yield`): the timers are then
         * checked often enough to keep their precision, and the external tasks are not starved by the local ones.
         */
        private fun runQueuedTasks() {
            var transferred = 0
            while (transferred++ < EXTERNAL_BATCH_SIZE) {
                local.addLast(external.poll() ?: break)
            }
            repeat(local.size) { runSafely(local.pollFirst()) }
        }

        /**
         * Parks the thread until the next deadline of a timer, or indefinitely when there is no timer, unless a task
         * was submitted meanwhile. [enqueueExternally] and [shutdown] wake the thread up; a spurious wake-up only
         * starts a new turn.
         */
        private fun park() {
            parked.set(true)
            if (external.isEmpty() && running) {
                val nextDeadline = timers.peek()?.deadline
                when {
                    nextDeadline == null -> LockSupport.park(this)
                    else -> (nextDeadline - System.nanoTime()).takeIf { it > 0 }?.let { LockSupport.parkNanos(this, it) }
                }
            }
            parked.set(false)
        }

        /**
         * Runs [task] and logs its failure, so that an exception does not stop the loop and its other tasks.
         * The tasks of coroutines handle their own exceptions; this only protects against tasks that do not.
         */
        private fun runSafely(task: Runnable) {
            try {
                task.run()
            } catch (e: Throwable) {
                log.error(e) { "An unexpected error occurred in the event loop $name: ${e.message}" }
            }
        }
    }

    private companion object {

        /**
         * Maximal count of tasks taken from the external queue in a turn, so that the timers are regularly checked.
         */
        const val EXTERNAL_BATCH_SIZE = 256

        /**
         * Minimal count of timers in a loop, from which the disposed ones are purged.
         */
        const val MIN_TIMERS_PURGE_THRESHOLD = 4096

        val log = logger()
    }
}
