package io.qalipsis.api.meters.meterConditions

class NotBetweenValueSpecification<T : Comparable<T>>(override val threshold: T, val lowerBound: T, val upperBound: T) :
    ValueCheckSpecification<T> {

    override val type = SpecificationType.NOT_BETWEEN
}