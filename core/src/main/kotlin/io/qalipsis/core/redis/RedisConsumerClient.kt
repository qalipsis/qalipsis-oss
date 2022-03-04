package io.qalipsis.core.redis

import io.lettuce.core.Consumer
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisBusyException
import io.lettuce.core.StreamMessage
import io.lettuce.core.XGroupCreateArgs
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList

/**
 * Redis client used to consume messages using consumer groups.
 *
 * @property redisCommands Redis coroutines client used to perform Redis commands.
 * @property deserializer Deserializer factory of [T] from Redis.
 * @property onMessage Function to perform for every message received.
 * @property idGenerator Id generator for each consumer client.
 * @property pollSize Number of messages consumed from Redis for each read command. [See more here](https://redis.io/commands/xreadgroup).
 * @property consumerGroupName Name of the consumer group. It will create a new consumer group if it does not exist already.
 * @property streamsName Name of the stream to consume data from.
 *
 * @author Gabriel Moraes
 */
@ExperimentalLettuceCoroutinesApi
class RedisConsumerClient<T>(
    private val redisCommands: RedisCoroutinesCommands<String, String>,
    private val deserializer: (String) -> T,
    private val idGenerator: IdGenerator,
    private val consumerGroupName: String,
    private val streamsName: String,
    private val pollSize: Long = 10,
    private val onMessage: suspend (T) -> Unit,
) {

    private var running = false

    /**
     * Starts the consumer from Redis.
     */
    suspend fun start() {
        if (!running) {
            running = true
            readMessages()
        }
    }

    /**
     * Stops the consumer of data. To resume, it is needed to call [start] again.
     */
    fun stop() {
        running = false
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun readMessages() {
        createConsumerGroup(consumerGroupName, streamsName)
        val consumer = createConsumerForGroup(consumerGroupName)
        val streamOffset = XReadArgs.StreamOffset.lastConsumed(streamsName)
        while (running) {
            val result = kotlin.runCatching {
                val messages =
                    redisCommands.xreadgroup(consumer, XReadArgs.Builder.count(pollSize), streamOffset).toList()

                if (messages.isNotEmpty()) {
                    processMessages(messages)
                    acknowledgeMessages(messages, consumerGroupName, streamsName)
                }
            }

            if (result.isFailure) {
                if (result.exceptionOrNull() is CancellationException) {
                    running = false
                } else {
                    log.warn(result.exceptionOrNull()) { "Failure on consuming and processing Redis messages for consumer group: $consumerGroupName" }
                }
            }
        }
    }

    /**
     * Creates the consumer group if it already not existed. If it already exists, an exception is thrown by the client and is ignored here.
     */
    private suspend fun createConsumerGroup(consumerGroupName: String, streamsName: String) {

        try {
            redisCommands.xgroupCreate(
                XReadArgs.StreamOffset.from(streamsName, STREAM_OFFSET_BEGINNING),
                consumerGroupName,
                XGroupCreateArgs.Builder.mkstream(true)
            )
        } catch (e: RedisBusyException) {
            log.debug { "The consumer group $consumerGroupName already exists" }
        }
    }

    private suspend fun processMessages(messages: List<StreamMessage<String, String>>) {
        messages.map {
            try {
                it.body.values.onEach { value ->
                    log.trace { "Received redis message $value" }
                    onMessage(deserializer(value))
                }
            } catch (e: Exception) {
                log.warn(e) { "Error processing message for stream $streamsName" }
            }
        }
    }

    private fun createConsumerForGroup(consumerGroupName: String) = Consumer.from(
        consumerGroupName, idGenerator.long()
    )

    private suspend fun acknowledgeMessages(
        messages: List<StreamMessage<String, String>>,
        consumerGroupName: String,
        streamsName: String
    ) {
        log.trace { "Acknowledging messages for stream: $streamsName" }
        redisCommands.xack(
            streamsName,
            consumerGroupName,
            *messages.map { it.id }.toTypedArray()
        )
    }

    companion object {

        @JvmStatic
        private val log = logger()

        /**
         * Redis consumer group offset configuration to consume messages that were sent before consumer group was created.
         *
         * For more information, [see here](https://redis.io/topics/streams-intro).
         */
        private const val STREAM_OFFSET_BEGINNING = "0"
    }
}