package io.qalipsis.core.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.CoreConfiguration
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveKey
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.ListDirective
import io.qalipsis.core.directives.ListDirectiveReference
import io.qalipsis.core.directives.QueueDirective
import io.qalipsis.core.directives.QueueDirectiveReference
import io.qalipsis.core.directives.SingleUseDirective
import io.qalipsis.core.directives.SingleUseDirectiveReference
import io.qalipsis.core.feedbacks.DirectiveFeedback
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Singleton

/**
 * Implementation of [DirectiveRegistry] hosting the [Directive]s into Redis,
 * used for deployments other than [STANDALONE].
 *
 * @property feedbackFactoryChannel Feedback factory channel for communication.
 * @property serializer Serializer for redis messages.
 * @property coreConfiguration Properties for configuration of the head.
 * @property redisCommands Redis Coroutines commands.
 *
 * @author Gabriel Moraes
 */
@Singleton
@Requires(notEnv = [STANDALONE])
@ExperimentalLettuceCoroutinesApi
internal class RedisDirectiveRegistry(
    private val feedbackFactoryChannel: FeedbackFactoryChannel,
    private val serializer: DistributionSerializer,
    private val coreConfiguration: CoreConfiguration,
    private val redisCommands: RedisCoroutinesCommands<String, String>
) : DirectiveRegistry {

    @LogInputAndOutput
    override suspend fun save(key: DirectiveKey, directive: Directive) {

        when (directive) {
            is QueueDirective<*, *> -> {
                val stringValues = directive.queue.map { serializer.serialize(it!!).decodeToString() }
                redisCommands.lpush(key, *stringValues.toTypedArray())
                redisCommands.sadd(coreConfiguration.pendingKey, key)
            }
            is ListDirective<*, *> -> {
                redisCommands.set(key, serializer.serialize(directive).decodeToString())
            }
            is SingleUseDirective<*, *> -> {
                redisCommands.lpush(key, serializer.serialize(directive).decodeToString())
            }
        }
    }

    override suspend fun keep(directive: Directive) {
        redisCommands.set(directive.key, serializer.serialize(directive).decodeToString())
    }

    @LogInputAndOutput
    override suspend fun <T> pop(reference: QueueDirectiveReference<T>): T? {
        return kotlin.runCatching { redisCommands.rpop(reference.key) }.getOrNull()?.let {

            if (isPending(reference)) {
                sendPendingDirective(reference)
            }

            if (isEmptyQueue(reference)) {
                sendCompletedDirective(reference)
            }

            serializer.deserialize(it.encodeToByteArray())
        }


    }

    private suspend fun <T> sendCompletedDirective(reference: QueueDirectiveReference<T>) {
        log.trace { "Evicting the empty queue directive ${reference.key}" }
        // Once the last value has been consumed, the directive is removed from redis.
        feedbackFactoryChannel.publish(
            DirectiveFeedback(directiveKey = reference.key, nodeId = "", status = FeedbackStatus.COMPLETED)
        )
    }

    private suspend fun <T> sendPendingDirective(reference: QueueDirectiveReference<T>) {
        feedbackFactoryChannel.publish(
            DirectiveFeedback(directiveKey = reference.key, nodeId = "", status = FeedbackStatus.IN_PROGRESS)
        )
        redisCommands.srem(coreConfiguration.pendingKey, reference.key)
    }

    private suspend fun <T> isPending(reference: QueueDirectiveReference<T>) =
        redisCommands.sismember(coreConfiguration.pendingKey, reference.key) == true

    private suspend fun <T> isEmptyQueue(reference: QueueDirectiveReference<T>) =
        kotlin.runCatching { redisCommands.llen(reference.key) == 0L }.getOrNull() ?: true

    @LogInputAndOutput
    override suspend fun <T> list(reference: ListDirectiveReference<T>): List<T> {
        return redisCommands.get(reference.key)
            ?.let { serializer.deserialize<ListDirective<T, ListDirectiveReference<T>>>(it.encodeToByteArray())?.set }
            ?: emptyList()
    }

    @LogInputAndOutput
    override suspend fun <T> read(reference: SingleUseDirectiveReference<T>): T? {
        return redisCommands.rpop(reference.key)?.let {
            serializer.deserialize<SingleUseDirective<T, SingleUseDirectiveReference<T>>>(it.encodeToByteArray())?.value
        }
    }

    override suspend fun get(key: DirectiveKey): Directive? {
        return redisCommands.get(key)?.let {
            serializer.deserialize(it.encodeToByteArray())
        }
    }

    override suspend fun remove(key: DirectiveKey) {
        redisCommands.unlink(key)
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}