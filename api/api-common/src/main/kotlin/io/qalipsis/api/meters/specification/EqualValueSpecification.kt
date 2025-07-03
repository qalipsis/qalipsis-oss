package io.qalipsis.api.meters.specification

import io.qalipsis.api.meters.ValueCheckSpecification

class EqualValueSpecification<T : Comparable<T>>(override val threshold: T) : ValueCheckSpecification<T> {

    override val type = SpecificationType.EQUAL
}