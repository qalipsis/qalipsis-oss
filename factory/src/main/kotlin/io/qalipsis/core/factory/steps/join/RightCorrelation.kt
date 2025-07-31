/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
class RightCorrelation<T : Any>(

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
