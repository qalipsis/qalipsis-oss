package io.qalipsis.core.inmemory

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveKey
import io.qalipsis.api.orchestration.directives.DirectiveRegistry
import io.qalipsis.api.orchestration.directives.ListDirective
import io.qalipsis.api.orchestration.directives.ListDirectiveReference
import io.qalipsis.api.orchestration.directives.QueueDirective
import io.qalipsis.api.orchestration.directives.QueueDirectiveReference
import io.qalipsis.api.orchestration.directives.SingleUseDirective
import io.qalipsis.api.orchestration.directives.SingleUseDirectiveReference
import io.qalipsis.api.orchestration.feedbacks.DirectiveFeedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import jakarta.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import javax.annotation.PreDestroy

/**
 * Implementation of [DirectiveRegistry] hosting the [Directive]s into memory, used for deployments
 * where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [STANDALONE])
internal class InMemoryDirectiveRegistry(
    private val feedbackFactoryChannel: FeedbackFactoryChannel
) : DirectiveRegistry {

    /**
     * Cache of real [QueueDirective]s accessible by the keys of their references.
     */
    private val queueDirectives: Cache<DirectiveKey, QueueDirective<*, *>> =
        Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build()

    /**
     * List of [QueueDirective] for which a feedback with status in progress has to be published.
     */
    private val waitingQueueDirectivesInProgressFeedback = concurrentSet<DirectiveKey>()

    /**
     * Cache of real [ListDirective]s accessible by the keys of their references.
     */
    private val listDirectives: Cache<DirectiveKey, ListDirective<*, *>> =
        Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build()

    /**
     * Cache of real [SingleUseDirective]s accessible by the keys of their references.
     */
    private val singleUseDirectives: Cache<DirectiveKey, SingleUseDirective<*, *>> =
        Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build()

    /**
     * Map of mutexes to access the synchronized directives. There are evicted quite fast to avoid useless memory usage.
     */
    private val mutexesCache: LoadingCache<DirectiveKey, Mutex> =
        Caffeine.newBuilder().expireAfterAccess(10, TimeUnit.SECONDS).build { Mutex(false) }

    @LogInputAndOutput
    override suspend fun save(key: DirectiveKey, directive: Directive) {
        when (directive) {
            is QueueDirective<*, *> -> {
                queueDirectives.put(key, directive)
                waitingQueueDirectivesInProgressFeedback.add(key)
            }
            is ListDirective<*, *> -> {
                listDirectives.put(key, directive)
            }
            is SingleUseDirective<*, *> -> {
                singleUseDirectives.put(key, directive)
            }
        }
    }

    @LogInputAndOutput
    override suspend fun <T> pop(reference: QueueDirectiveReference<T>): T? {
        return mutexesCache[reference.key]!!.withLock {
            queueDirectives.getIfPresent(reference.key)?.queue?.let {
                val queue = it as LinkedList
                // Publish a IN_PROGRESS notification if not yet done.
                if (waitingQueueDirectivesInProgressFeedback.remove(reference.key)) {
                    feedbackFactoryChannel.publish(
                        DirectiveFeedback(directiveKey = reference.key, status = FeedbackStatus.IN_PROGRESS)
                    )
                }

                @Suppress("UNCHECKED_CAST") val value = queue.removeFirst() as T?
                if (queue.isEmpty()) {
                    log.trace { "Evicting the empty queue directive ${reference.key}" }
                    // Once the last value has been consumed, the directive is removed from the cache.
                    queueDirectives.invalidate(reference.key)
                    feedbackFactoryChannel.publish(
                        DirectiveFeedback(directiveKey = reference.key, status = FeedbackStatus.COMPLETED)
                    )
                }
                value
            }
        }
    }

    @LogInputAndOutput
    override suspend fun <T> list(reference: ListDirectiveReference<T>): List<T> {
        @Suppress("UNCHECKED_CAST")
        return listDirectives.getIfPresent(reference.key)?.set as List<T>? ?: emptyList()
    }

    @LogInputAndOutput
    override suspend fun <T> read(reference: SingleUseDirectiveReference<T>): T? {
        return mutexesCache[reference.key]!!.withLock {
            singleUseDirectives.getIfPresent(reference.key)?.let {
                log.trace { "Evicting the read once directive ${reference.key}" }
                singleUseDirectives.invalidate(reference.key)
                @Suppress("UNCHECKED_CAST")
                it.value as T?
            }
        }
    }

    @PreDestroy
    fun close() {
        kotlin.runCatching {
            queueDirectives.invalidateAll()
            queueDirectives.cleanUp()
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
