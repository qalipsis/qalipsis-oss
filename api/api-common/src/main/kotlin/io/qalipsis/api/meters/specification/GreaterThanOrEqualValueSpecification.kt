package io.qalipsis.api.meters.specification

import io.qalipsis.api.meters.ValueCheckSpecification

class GreaterThanOrEqualValueSpecification<T : Comparable<T>>(override val threshold: T) :
    ValueCheckSpecification<T> {

    override val type = SpecificationType.GREATER_THAN_OR_EQUAL
}