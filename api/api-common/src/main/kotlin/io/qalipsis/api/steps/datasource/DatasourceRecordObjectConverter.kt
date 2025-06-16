/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.api.steps.datasource

import io.qalipsis.api.context.StepOutput
import java.util.concurrent.atomic.AtomicLong

/**
 * Wraps the object received from the datasource as a [DatasourceRecord].
 *
 * @author Eric Jessé
 */
class DatasourceRecordObjectConverter<R> : DatasourceObjectConverter<R, DatasourceRecord<R>> {

    override suspend fun supply(offset: AtomicLong, value: R, output: StepOutput<DatasourceRecord<R>>) {
        output.send(DatasourceRecord(offset.getAndIncrement(), value))
    }

}
