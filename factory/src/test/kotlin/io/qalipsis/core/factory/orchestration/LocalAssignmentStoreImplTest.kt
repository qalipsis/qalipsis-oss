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

package io.qalipsis.core.factory.orchestration

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.key
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.test.mockk.CleanMockkRecordedCalls
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

@CleanMockkRecordedCalls
internal class LocalAssignmentStoreImplTest {

    @MockK
    private lateinit var scenarioRegistry: ScenarioRegistry

    @BeforeAll
    internal fun setUpAll() {
        val scenarios = mutableListOf<Scenario>()
        (1..3).map { index ->
            scenarios += mockk<Scenario>(name = "scenario-$index") {
                every { name } returns "scenario-$index"
                every { dags } returns listOf(
                    relaxedMockk {
                        every { name } returns "root-underload-$index"
                        every { isRoot } returns true
                        every { isUnderLoad } returns true
                    },
                    relaxedMockk {
                        every { name } returns "root-noload-$index"
                        every { isRoot } returns true
                        every { isUnderLoad } returns false
                    },
                    relaxedMockk {
                        every { name } returns "non-root-underload-$index"
                        every { isRoot } returns false
                        every { isUnderLoad } returns true
                    }
                )
            }
        }
        every { scenarioRegistry.all() } returns scenarios
    }

    @Test
    internal fun `should have nothing assigned by default`() {
        // given
        val store = LocalAssignmentStoreImpl(scenarioRegistry)

        // when
        store.reset()

        // then
        assertThat(store.assignments.isEmpty()).isTrue()
        assertThat(store.hasMinionsAssigned("scenario-1")).isFalse()
        assertThat(store.hasMinionsAssigned("scenario-2")).isFalse()
        assertThat(store.isLocal("scenario-1", "my-minion", "root-underload-1")).isFalse()
        assertThat(store.hasRootUnderLoadLocally("scenario-1", "my-minion")).isFalse()
    }

    @Test
    internal fun `should save the assignments and verify them`() {
        // given
        val store = LocalAssignmentStoreImpl(scenarioRegistry)
        store.reset()

        // when
        store.save(
            "scenario-1", mapOf(
                "my-minion-1" to setOf("root-underload-1", "non-root-underload-1"),
                "my-minion-2" to setOf("non-root-underload-1"),
                "my-minion-3" to setOf("root-noload-1")
            )
        )

        store.save(
            "scenario-2", mapOf(
                "my-minion-4" to setOf("root-underload-2", "non-root-underload-2"),
                "my-minion-5" to setOf("non-root-underload-2"),
                "my-minion-6" to setOf("root-noload-2")
            )
        )

        // then
        assertThat(store.assignments["scenario-1"]).isNotNull().all {
            hasSize(3)
            key("my-minion-1").containsOnly("root-underload-1", "non-root-underload-1")
            key("my-minion-2").containsOnly("non-root-underload-1")
            key("my-minion-3").containsOnly("root-noload-1")
        }
        assertThat(store.assignments["scenario-2"]).isNotNull().all {
            hasSize(3)
            key("my-minion-4").containsOnly("root-underload-2", "non-root-underload-2")
            key("my-minion-5").containsOnly("non-root-underload-2")
            key("my-minion-6").containsOnly("root-noload-2")
        }
        assertThat(store.hasMinionsAssigned("scenario-1")).isTrue()
        assertThat(store.hasMinionsAssigned("scenario-2")).isTrue()
        assertThat(store.hasMinionsAssigned("scenario-3")).isFalse()

        // Verifying all the minions assignments.
        assertThat(store.isLocal("scenario-1", "my-minion-1", "root-underload-1")).isTrue()
        assertThat(store.isLocal("scenario-1", "my-minion-1", "non-root-underload-1")).isTrue()
        assertThat(store.isLocal("scenario-1", "my-minion-1", "root-noload-1")).isFalse()
        assertThat(store.hasRootUnderLoadLocally("scenario-1", "my-minion-1")).isTrue()

        assertThat(store.isLocal("scenario-1", "my-minion-2", "root-underload-1")).isFalse()
        assertThat(store.isLocal("scenario-1", "my-minion-2", "non-root-underload-1")).isTrue()
        assertThat(store.isLocal("scenario-1", "my-minion-2", "root-noload-1")).isFalse()
        assertThat(store.hasRootUnderLoadLocally("scenario-1", "my-minion-2")).isFalse()

        assertThat(store.isLocal("scenario-1", "my-minion-3", "root-underload-1")).isFalse()
        assertThat(store.isLocal("scenario-1", "my-minion-3", "non-root-underload-1")).isFalse()
        assertThat(store.isLocal("scenario-1", "my-minion-3", "root-noload-1")).isTrue()
        assertThat(store.hasRootUnderLoadLocally("scenario-1", "my-minion-3")).isFalse()

        assertThat(store.isLocal("scenario-2", "my-minion-4", "root-underload-2")).isTrue()
        assertThat(store.isLocal("scenario-2", "my-minion-4", "non-root-underload-2")).isTrue()
        assertThat(store.isLocal("scenario-2", "my-minion-4", "root-noload-2")).isFalse()
        assertThat(store.hasRootUnderLoadLocally("scenario-2", "my-minion-4")).isTrue()

        assertThat(store.isLocal("scenario-2", "my-minion-5", "root-underload-2")).isFalse()
        assertThat(store.isLocal("scenario-2", "my-minion-5", "non-root-underload-2")).isTrue()
        assertThat(store.isLocal("scenario-2", "my-minion-5", "root-noload-2")).isFalse()
        assertThat(store.hasRootUnderLoadLocally("scenario-2", "my-minion-5")).isFalse()

        assertThat(store.isLocal("scenario-2", "my-minion-6", "root-underload-2")).isFalse()
        assertThat(store.isLocal("scenario-2", "my-minion-6", "non-root-underload-2")).isFalse()
        assertThat(store.isLocal("scenario-2", "my-minion-6", "root-noload-2")).isTrue()
        assertThat(store.hasRootUnderLoadLocally("scenario-2", "my-minion-6")).isFalse()

        // Searching the minion on the wrong scenario.
        assertThat(store.isLocal("scenario-2", "my-minion-1", "root-underload-1")).isFalse()
        assertThat(store.isLocal("scenario-2", "my-minion-1", "non-root-underload-1")).isFalse()
        assertThat(store.isLocal("scenario-2", "my-minion-1", "root-noload-1")).isFalse()

        // when
        store.reset()

        // then
        assertThat(store.assignments.isEmpty()).isTrue()
        assertThat(store.hasMinionsAssigned("scenario-1")).isFalse()
        assertThat(store.hasMinionsAssigned("scenario-2")).isFalse()
        assertThat(store.isLocal("scenario-1", "my-minion-1", "root-underload-1")).isFalse()
        assertThat(store.hasRootUnderLoadLocally("scenario-1", "my-minion-1")).isFalse()
    }


}