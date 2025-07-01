package io.qalipsis.api.meters.meterConditions

class LessThanValueSpecification<T : Comparable<T>>(override val threshold: T) : ValueCheckSpecification<T> {

    override val type = SpecificationType.LESS_THAN
}