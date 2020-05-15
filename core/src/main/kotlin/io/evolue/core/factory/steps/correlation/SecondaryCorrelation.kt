package io.evolue.core.factory.steps.correlation

import io.evolue.api.context.CorrelationRecord
import io.evolue.api.context.StepId
import kotlinx.coroutines.channels.ReceiveChannel

/**
 *
 * @author Eric Jessé
 */
class SecondaryCorrelation(

    /**
     * ID of the [Step] providing the data.
     */
    val sourceStepId: StepId,

    /**
     * [ReceiveChannel] obtained from a [CorrelationOutputDecorator] to forward the records.
     */
    val subscriptionChannel: ReceiveChannel<CorrelationRecord<*>>,

    /**
     * Specification of the key extractor based upon the received value.
     */
    val keyExtractor: ((record: CorrelationRecord<*>) -> Any?)
)