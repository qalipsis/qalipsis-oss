package io.qalipsis.api.meters.meterConditions

class BetweenValueSpecification<T : Comparable<T>>(override val threshold: T) : ValueCheckSpecification<T> {

    override val type = SpecificationType.IS_BETWEEN
}