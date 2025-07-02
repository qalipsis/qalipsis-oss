package io.qalipsis.api.meters.meterConditions

class BetweenValueSpecification<T : Comparable<T>>(override val threshold: T, val lowerBound: T, val upperBound: T) :
    ValueCheckSpecification<T> {

    override val type = SpecificationType.BETWEEN
}