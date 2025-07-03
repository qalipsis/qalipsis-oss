package io.qalipsis.api.meters.specification

import io.qalipsis.api.meters.ValueCheckSpecification

class BetweenValueSpecification<T : Comparable<T>>(override val threshold: T, val lowerBound: T, val upperBound: T) :
    ValueCheckSpecification<T> {

    override val type = SpecificationType.BETWEEN
}