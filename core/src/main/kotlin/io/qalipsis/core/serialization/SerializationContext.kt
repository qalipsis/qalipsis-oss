package io.qalipsis.core.serialization

interface SerializationContext {

    val target: SerializationTarget

    companion object {

        val EMPTY: SerializationContext = object : SerializationContext {
            override val target = SerializationTarget.UNDEFINED
        }
    }
}

interface DeserializationContext {

    val target: SerializationTarget

    companion object {

        val EMPTY: DeserializationContext = object : DeserializationContext {
            override val target = SerializationTarget.UNDEFINED
        }
    }
}

enum class SerializationTarget {
    UNDEFINED,
    STATE_REGISTRY,
    STEP_CONTEXT
}