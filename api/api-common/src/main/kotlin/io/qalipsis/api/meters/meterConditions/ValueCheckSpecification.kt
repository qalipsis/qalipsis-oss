package io.qalipsis.api.meters.meterConditions


interface ValueCheckSpecification<T> {
    val type: SpecificationType
}

enum class SpecificationType {
    LESS_THAN, MORE_THAN, IS_BETWEEN
}