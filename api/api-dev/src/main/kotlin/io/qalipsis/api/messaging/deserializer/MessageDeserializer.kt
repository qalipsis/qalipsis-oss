package io.qalipsis.api.messaging.deserializer


/**
 * Deserializer from the [ByteArray] payload to a desired output type.
 */
interface MessageDeserializer<V> {

    /**
     * Deserializes the [body] to the specified type [V].
     *
     * @param body consumed.
     * @return [V] the specified type to return after deserialization.
     */
    fun deserialize(body: ByteArray): V
}