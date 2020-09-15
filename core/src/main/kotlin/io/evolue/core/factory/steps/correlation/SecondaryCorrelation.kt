package io.evolue.core.factory.steps.correlation

import io.evolue.api.context.CorrelationRecord
import io.evolue.api.context.StepId
import io.evolue.api.messaging.Topic

/**
 *
 * Specifies the link between a remote [io.evolue.core.factory.steps.decorators.OutputTopicStepDecorator] provided data
 * and a local [io.evolue.api.steps.Step] waiting for a record with the same key.
 *
 * @author Eric Jessé
 */
class SecondaryCorrelation<T : Any>(

        /**
         * ID of the [io.evolue.api.steps.Step] providing the remote data.
         */
        val sourceStepId: StepId,

        /**
         * [Topic] from a [io.evolue.core.factory.steps.decorators.OutputTopicStepDecorator] to forward the records.
         */
        val topic: Topic<CorrelationRecord<T>>,

        /**
         * Specification of the key extractor based upon the received value.
         */
        val keyExtractor: ((record: CorrelationRecord<T>) -> Any?)
)