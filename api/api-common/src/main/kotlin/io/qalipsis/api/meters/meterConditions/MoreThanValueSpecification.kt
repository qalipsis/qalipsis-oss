package io.qalipsis.api.meters.meterConditions

class MoreThanValueSpecification<T : Comparable<T>>(override val threshold: T) : ValueCheckSpecification<T> {

    override val type = SpecificationType.MORE_THAN
}