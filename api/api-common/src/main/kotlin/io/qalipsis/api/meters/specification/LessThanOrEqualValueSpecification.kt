package io.qalipsis.api.meters.specification

import io.qalipsis.api.meters.ValueCheckSpecification

class LessThanOrEqualValueSpecification<T : Comparable<T>>(override val threshold: T) :
    ValueCheckSpecification<T> {

    override val type = SpecificationType.LESS_THAN_OR_EQUAL
}