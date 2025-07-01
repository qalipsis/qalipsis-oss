package io.qalipsis.api.meters.meterConditions

interface ValueCheckSpecification<T> {
    val threshold: T
    val type: SpecificationType
}