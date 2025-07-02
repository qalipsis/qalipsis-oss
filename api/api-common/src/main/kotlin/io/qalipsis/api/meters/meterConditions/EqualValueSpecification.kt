package io.qalipsis.api.meters.meterConditions

class EqualValueSpecification<T : Comparable<T>>(override val threshold: T) : ValueCheckSpecification<T> {

    override val type = SpecificationType.EQUAL
}