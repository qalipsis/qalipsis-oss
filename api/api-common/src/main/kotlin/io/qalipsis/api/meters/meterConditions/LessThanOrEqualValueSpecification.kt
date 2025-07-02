package io.qalipsis.api.meters.meterConditions

class LessThanOrEqualValueSpecification<T : Comparable<T>>(override val threshold: T) :
    ValueCheckSpecification<T> {

    override val type = SpecificationType.LESS_THAN_OR_EQUAL
}