package io.qalipsis.api.meters.meterConditions

class GreaterThanValueSpecification<T : Comparable<T>>(override val threshold: T) : ValueCheckSpecification<T> {

    override val type = SpecificationType.MORE_THAN
}