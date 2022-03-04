package io.qalipsis.core.serialization

interface SerializationContext {

    val target: SerializationTarget

    companion object {

        val EMPTY: SerializationContext = object : SerializationContext {
            override val target = SerializationTarget.UNDEFINED
        }

        val CONTEXT: SerializationContext = object : SerializationContext {
            override val target = SerializationTarget.CONTEXT
        }
    }
}

interface DeserializationContext {

    val target: SerializationTarget

    companion object {

        val EMPTY: DeserializationContext = object : DeserializationContext {
            override val target = SerializationTarget.UNDEFINED
        }

        val CONTEXT: DeserializationContext = object : DeserializationContext {
            override val target = SerializationTarget.CONTEXT
        }
    }
}

enum class SerializationTarget {
    UNDEFINED,
    STATE_REGISTRY,
    CONTEXT
}