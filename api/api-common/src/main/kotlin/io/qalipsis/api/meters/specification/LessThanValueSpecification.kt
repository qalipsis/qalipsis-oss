package io.qalipsis.api.meters.specification

import io.qalipsis.api.meters.ValueCheckSpecification

class LessThanValueSpecification<T : Comparable<T>>(override val threshold: T) : ValueCheckSpecification<T> {

    override val type = SpecificationType.LESS_THAN
}