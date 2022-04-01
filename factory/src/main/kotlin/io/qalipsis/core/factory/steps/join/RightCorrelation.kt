package io.qalipsis.core.factory.steps.join

import io.qalipsis.api.context.CorrelationRecord
import io.qalipsis.api.context.StepName
import io.qalipsis.api.messaging.Topic

/**
 *
 * Specifies the link between a remote [io.qalipsis.core.factory.steps.decorators.OutputTopicStepDecorator] provided data
 * and a local [io.qalipsis.api.steps.Step] waiting for a record with the same key.
 *
 * @author Eric Jessé
 */
internal class RightCorrelation<T : Any>(

    /**
     * ID of the [io.qalipsis.api.steps.Step] providing the remote data.
     */
    val sourceStepName: StepName,

    /**
     * [Topic] from a [io.qalipsis.core.factory.steps.decorators.OutputTopicStepDecorator] to forward the records.
     */
    val topic: Topic<CorrelationRecord<T>>,

    /**
     * Specification of the key extractor based upon the received value.
     */
    val keyExtractor: ((record: CorrelationRecord<T>) -> Any?)
)
