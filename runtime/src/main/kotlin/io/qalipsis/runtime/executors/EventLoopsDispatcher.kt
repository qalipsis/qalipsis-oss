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
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Coroutine dispatcher made of single-threaded event loops, each owning its timers and its queue of tasks, with work
 * stealing between the loops.
 *
 * Unlike a [java.util.concurrent.ScheduledThreadPoolExecutor], which serializes all the dispatches and timers of
 * its threads on one locked queue:
 * - a task dispatched from a loop is queued on that loop, without wake-up;
 * - a task dispatched from another thread is queued on a random loop, through a lock-free queue;
 * - a timer expires on the loop owning it and is run there directly, so timers due at the same time are
 *   executed by all the loops in parallel, keeping the precision of the delays;
 * - a loop without task takes tasks from the queues of the other loops before parking, and a loop keeping a backlog
 *   after its turn wakes a parked loop up, so that a busy loop does not delay its tasks while other loops are idle.
 *
 * The dispatcher is only suitable for non-blocking code: a blocked loop does not run its timers.
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
     * Count of the parked loops, so that the busy loops only look for a loop to wake up when there is one.
     */
    private val parkedLoops = AtomicInteger()

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
     * sent as a task to a random loop, since the timers of a loop are only accessed by its own thread; the loop
     * finally running that task, possibly after stealing it, owns the timer. In both cases, the deadline is computed
     * now, so the time to register the timer does not delay it.
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
     * Takes up to [STEAL_BATCH_SIZE] tasks from the queues of the loops other than [thief], starting from a random
     * one so that the thieves do not all compete for the same victim, and runs them on [thief].
     *
     * @return true when at least one task was stolen.
     */
    private fun steal(thief: EventLoop): Boolean {
        val start = ThreadLocalRandom.current().nextInt(loops.size)
        for (offset in loops.indices) {
            val victim = loops[(start + offset) % loops.size]
            if (victim !== thief && victim.hasTasks()) {
                var stolen = 0
                while (stolen < STEAL_BATCH_SIZE) {
                    thief.runSafely(victim.pollTask() ?: break)
                    stolen++
                }
                if (stolen > 0) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Returns true when a loop other than [loop] has tasks that [loop] could steal.
     */
    private fun hasStealableTasks(loop: EventLoop) = loops.any { it !== loop && it.hasTasks() }

    /**
     * Wakes a parked loop up, if there is one, so that it steals tasks from a loop that cannot run them all in its
     * turn.
     */
    private fun wakeUpParkedLoop() {
        if (parkedLoops.get() > 0) {
            val start = ThreadLocalRandom.current().nextInt(loops.size)
            for (offset in loops.indices) {
                if (loops[(start + offset) % loops.size].wakeUp()) {
                    return
                }
            }
        }
    }

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
     * Thread of the dispatcher running its own timers and the tasks of its queue, and stealing the tasks of the other
     * loops when it has nothing to do.
     *
     * Each turn of the loop:
     * 1. runs the timers that are due;
     * 2. runs up to [TURN_BATCH_SIZE] tasks of its queue, then wakes a parked loop up when tasks are left, so that it
     *    steals them;
     * 3. when its queue is empty, steals tasks from the other loops;
     * 4. parks the thread when nothing was stolen, until the next deadline of a timer or until it is woken up.
     *
     * The timers are only accessed by the thread of the loop, so they need no synchronization. The queue of tasks is
     * lock-free, since the other threads add tasks to it and the other loops steal from it.
     *
     * @property owner the dispatcher creating the loop, to recognize its own threads in [currentLoop].
     */
    private class EventLoop(val owner: EventLoopsDispatcher, name: String) : Thread(name) {

        /**
         * Tasks of the loop, dispatched from the loop itself or from other threads, and stolen by the other loops.
         */
        private val tasks = ConcurrentLinkedQueue<Runnable>()

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
         * Enqueues [task] from the thread of the loop itself. The loop is running, so it does not need to be woken up.
         * But when tasks were already waiting, a parked loop is woken up to steal them: the current task of this
         * loop might take long, and nothing else would wake a helper before the end of the turn.
         */
        fun enqueueLocally(task: Runnable) {
            val backlog = tasks.isNotEmpty()
            tasks.offer(task)
            if (backlog) {
                owner.wakeUpParkedLoop()
            }
        }

        /**
         * Enqueues [task] from another thread and wakes the loop up if it is parked.
         *
         * The task is offered before [parked] is read, and [park] sets [parked] before checking the queue: either
         * the loop sees the task before parking, or this function sees the flag and unparks the loop.
         */
        fun enqueueExternally(task: Runnable) {
            tasks.offer(task)
            wakeUp()
        }

        /**
         * Returns true when the queue contains tasks, for a thief to select its victim.
         */
        fun hasTasks() = tasks.isNotEmpty()

        /**
         * Removes the next task of the queue, from the loop itself or from a thief.
         */
        fun pollTask(): Runnable? = tasks.poll()

        /**
         * Unparks the loop when it is parked. The compare-and-set ensures that only one of the concurrent callers
         * unparks it.
         *
         * @return true when the loop was parked and is now woken up.
         */
        fun wakeUp(): Boolean {
            val wokenUp = parked.get() && parked.compareAndSet(true, false)
            if (wokenUp) {
                LockSupport.unpark(this)
            }
            return wokenUp
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
                val ranOwnTasks = runOwnTasks()
                if (!ranOwnTasks && !owner.steal(this)) {
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
         * Runs up to [TURN_BATCH_SIZE] tasks of the queue, then wakes a parked loop up when tasks are left.
         *
         * Bounding the batch keeps a turn short, even when the tasks enqueue new tasks (e.g. coroutines calling
         * `yield`): the timers are then checked often enough to keep their precision. The tasks left after the batch
         * are the ones another loop can take over, instead of waiting for the next turn.
         *
         * @return true when at least one task was run.
         */
        private fun runOwnTasks(): Boolean {
            var executed = 0
            while (executed < TURN_BATCH_SIZE) {
                runSafely(tasks.poll() ?: break)
                executed++
            }
            if (tasks.isNotEmpty()) {
                owner.wakeUpParkedLoop()
            }
            return executed > 0
        }

        /**
         * Parks the thread until the next deadline of a timer, or indefinitely when there is no timer, unless a task
         * was submitted meanwhile. [enqueueExternally], [wakeUp] and [shutdown] wake the thread up; a spurious
         * wake-up only starts a new turn.
         *
         * The queues of the other loops are checked after [parkedLoops] is incremented, because a busy loop only
         * wakes a parked loop up when it sees one: either the busy loop sees this loop as parked and wakes it up, or
         * this loop sees the tasks queued since its last stealing attempt and does not park.
         */
        private fun park() {
            parked.set(true)
            owner.parkedLoops.incrementAndGet()
            if (tasks.isEmpty() && !owner.hasStealableTasks(this) && running) {
                val nextDeadline = timers.peek()?.deadline
                when {
                    nextDeadline == null -> LockSupport.park(this)
                    else -> (nextDeadline - System.nanoTime()).takeIf { it > 0 }?.let { LockSupport.parkNanos(this, it) }
                }
            }
            owner.parkedLoops.decrementAndGet()
            parked.set(false)
        }

        /**
         * Runs [task] and logs its failure, so that an exception does not stop the loop and its other tasks.
         * The tasks of coroutines handle their own exceptions; this only protects against tasks that do not.
         */
        fun runSafely(task: Runnable) {
            try {
                task.run()
            } catch (e: Throwable) {
                log.error(e) { "An unexpected error occurred in the event loop $name: ${e.message}" }
            }
        }
    }

    private companion object {

        /**
         * Maximal count of tasks of its own queue that a loop runs in a turn, so that the timers are regularly checked
         * and the tasks left can be stolen.
         */
        const val TURN_BATCH_SIZE = 64

        /**
         * Maximal count of tasks a loop steals from another one at once.
         */
        const val STEAL_BATCH_SIZE = 16

        /**
         * Minimal count of timers in a loop, from which the disposed ones are purged.
         */
        const val MIN_TIMERS_PURGE_THRESHOLD = 4096

        val log = logger()
    }
}
