package io.qalipsis.api.meters.meterConditions

class LessThanValueSpecification<T : Comparable<T>>(val threshold: T) : ValueCheckSpecification<T> {

    init {
        println("LESS THAN FAILURE SPEC $threshold")
    }
    override val type = SpecificationType.LESS_THAN
}