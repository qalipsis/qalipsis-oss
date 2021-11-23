package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.lang.isLongerThan
import java.time.Duration
import javax.validation.constraints.Positive

/**
 * Specification for a [io.qalipsis.core.factory.steps.CollectionStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class CollectionStepSpecification<INPUT>(
    @field:PositiveDuration val batchTimeout: Duration?,
    @field:Positive val batchSize: Int
) :
    AbstractStepSpecification<INPUT, List<INPUT>, CollectionStepSpecification<INPUT>>()

/**
 * Consumes the input data coming from all the minions, and releases them as a list,
 * once either the linger timeout or the full capacity of the batch is reached.
 *
 * @param timeout linger duration between two releases, defaults to no timeout
 * @param batchSize the maximal size of the batch to trigger to release, defaults to no limit
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.collect(
    timeout: Duration? = null,
    batchSize: Int = 0
): CollectionStepSpecification<INPUT> {
    if (timeout?.isLongerThan(0) != true && batchSize <= 0) {
        throw IllegalArgumentException("Either the timeout or the batch size or both should be set.")
    }
    val step = CollectionStepSpecification<INPUT>(timeout, batchSize)
    this.add(step)
    return step
}
