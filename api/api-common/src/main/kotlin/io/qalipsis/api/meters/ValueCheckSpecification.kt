package io.qalipsis.api.meters

import io.qalipsis.api.meters.specification.SpecificationType

interface ValueCheckSpecification<T> {
    val threshold: T
    val type: SpecificationType
}