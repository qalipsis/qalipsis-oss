/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.report.meters

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.qalipsis.core.feedbacks.CampaignMetersFeedback
import io.qalipsis.core.meters.CampaignMeterSnapshot
import io.qalipsis.test.coroutines.TestDispatcherProvider
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@Timeout(20, unit = TimeUnit.SECONDS)
internal abstract class AbstractCampaignMeterFeedbackListenerTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    protected lateinit var feedbackListener: AbstractCampaignMeterFeedbackListener

    protected abstract val verificationDataSource: DataSource

    @BeforeEach
    fun setUp() = testDispatcherProvider.run {
        setUpTest()
    }

    protected abstract suspend fun setUpTest()

    @Test
    internal fun `given empty meters when notify then no row inserted`() = testDispatcherProvider.run {
        // given
        val feedback = CampaignMetersFeedback(meters = emptyList())

        // when
        feedbackListener.notify(feedback)

        // then
        val count = verificationDataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM campaign_meters").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }
        assertThat(count).isEqualTo(0)
    }

    @Test
    internal fun `given single meter with all fields when notify then row inserted with correct values`() =
        testDispatcherProvider.run {
            // given
            val timestampMs = 1_700_000_000_000L
            val snapshot = CampaignMeterSnapshot(
                name = "my-meter",
                tags = """{"env":"prod"}""",
                timestampEpochMs = timestampMs,
                tenant = "tenant-a",
                campaign = "campaign-1",
                scenario = "scenario-x",
                type = "counter",
                count = 42.0,
                value = 3.14,
                sum = 100.5,
                mean = 10.05,
                unit = "ms",
                max = 200.0,
                other = """{"extra":"data"}"""
            )

            // when
            feedbackListener.notify(CampaignMetersFeedback(meters = listOf(snapshot)))

            // then
            verificationDataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT * FROM campaign_meters").use { rs ->
                        rs.next()
                        assertThat(rs.getString("name")).isEqualTo("my-meter")
                        assertThat(rs.getString("tags")).isEqualTo("""{"env":"prod"}""")
                        assertThat(rs.getTimestamp("timestamp"))
                            .isEqualTo(Timestamp.from(Instant.ofEpochMilli(timestampMs)))
                        assertThat(rs.getString("tenant")).isEqualTo("tenant-a")
                        assertThat(rs.getString("campaign")).isEqualTo("campaign-1")
                        assertThat(rs.getString("scenario")).isEqualTo("scenario-x")
                        assertThat(rs.getString("type")).isEqualTo("counter")
                        assertThat(rs.getBigDecimal("count").compareTo(BigDecimal.valueOf(42.0))).isEqualTo(0)
                        assertThat(rs.getBigDecimal("value").compareTo(BigDecimal.valueOf(3.14))).isEqualTo(0)
                        assertThat(rs.getBigDecimal("sum").compareTo(BigDecimal.valueOf(100.5))).isEqualTo(0)
                        assertThat(rs.getBigDecimal("mean").compareTo(BigDecimal.valueOf(10.05))).isEqualTo(0)
                        assertThat(rs.getString("unit")).isEqualTo("ms")
                        assertThat(rs.getBigDecimal("max").compareTo(BigDecimal.valueOf(200.0))).isEqualTo(0)
                        assertThat(rs.getString("other")).isEqualTo("""{"extra":"data"}""")
                    }
                }
            }
        }

    @Test
    internal fun `given snapshot with all null optional fields when notify then null columns stored`() =
        testDispatcherProvider.run {
            // given
            val timestampMs = 1_600_000_000_000L
            val snapshot = CampaignMeterSnapshot(
                name = "minimal-meter",
                timestampEpochMs = timestampMs,
                type = "gauge"
            )

            // when
            feedbackListener.notify(CampaignMetersFeedback(meters = listOf(snapshot)))

            // then
            verificationDataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT * FROM campaign_meters").use { rs ->
                        rs.next()
                        assertThat(rs.getString("name")).isEqualTo("minimal-meter")
                        assertThat(rs.getString("tags")).isNull()
                        assertThat(rs.getTimestamp("timestamp"))
                            .isEqualTo(Timestamp.from(Instant.ofEpochMilli(timestampMs)))
                        assertThat(rs.getString("tenant")).isNull()
                        assertThat(rs.getString("campaign")).isNull()
                        assertThat(rs.getString("scenario")).isNull()
                        assertThat(rs.getString("type")).isEqualTo("gauge")
                        assertThat(rs.getBigDecimal("count")).isNull()
                        assertThat(rs.getBigDecimal("value")).isNull()
                        assertThat(rs.getBigDecimal("sum")).isNull()
                        assertThat(rs.getBigDecimal("mean")).isNull()
                        assertThat(rs.getString("unit")).isNull()
                        assertThat(rs.getBigDecimal("max")).isNull()
                        assertThat(rs.getString("other")).isNull()
                    }
                }
            }
        }

    @Test
    internal fun `given multiple meters when notify then all rows inserted`() = testDispatcherProvider.run {
        // given
        val meters = listOf(
            CampaignMeterSnapshot(name = "meter-1", timestampEpochMs = 1_000_000L, type = "counter"),
            CampaignMeterSnapshot(name = "meter-2", timestampEpochMs = 2_000_000L, type = "gauge"),
            CampaignMeterSnapshot(name = "meter-3", timestampEpochMs = 3_000_000L, type = "timer")
        )

        // when
        feedbackListener.notify(CampaignMetersFeedback(meters = meters))

        // then
        val count = verificationDataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM campaign_meters").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }
        assertThat(count).isEqualTo(3)
    }

    @Test
    internal fun `given snapshot with all fields when bindCampaignMeterSnapshot then all parameters bound correctly`() =
        testDispatcherProvider.run {
            // given
            val timestampMs = 1_700_500_000_000L
            val snapshot = CampaignMeterSnapshot(
                name = "bind-meter",
                tags = """{"zone":"eu"}""",
                timestampEpochMs = timestampMs,
                tenant = "tenant-b",
                campaign = "campaign-bind",
                scenario = "scenario-bind",
                type = "summary",
                count = 7.0,
                value = 1.5,
                sum = 10.5,
                mean = 1.5,
                unit = "s",
                max = 3.0,
                other = """{"p99":"2.8"}"""
            )

            // when — call bindCampaignMeterSnapshot directly to verify parameter binding
            verificationDataSource.connection.use { conn ->
                conn.prepareStatement(INSERT_SQL).use { statement ->
                    feedbackListener.bindCampaignMeterSnapshot(statement, snapshot)
                    statement.executeUpdate()
                }
            }

            // then
            verificationDataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT * FROM campaign_meters WHERE name = 'bind-meter'").use { rs ->
                        rs.next()
                        assertThat(rs.getString("name")).isEqualTo("bind-meter")
                        assertThat(rs.getString("tags")).isEqualTo("""{"zone":"eu"}""")
                        assertThat(rs.getTimestamp("timestamp"))
                            .isEqualTo(Timestamp.from(Instant.ofEpochMilli(timestampMs)))
                        assertThat(rs.getString("tenant")).isEqualTo("tenant-b")
                        assertThat(rs.getString("campaign")).isEqualTo("campaign-bind")
                        assertThat(rs.getString("scenario")).isEqualTo("scenario-bind")
                        assertThat(rs.getString("type")).isEqualTo("summary")
                        assertThat(rs.getBigDecimal("count").compareTo(BigDecimal.valueOf(7.0))).isEqualTo(0)
                        assertThat(rs.getBigDecimal("value").compareTo(BigDecimal.valueOf(1.5))).isEqualTo(0)
                        assertThat(rs.getBigDecimal("sum").compareTo(BigDecimal.valueOf(10.5))).isEqualTo(0)
                        assertThat(rs.getBigDecimal("mean").compareTo(BigDecimal.valueOf(1.5))).isEqualTo(0)
                        assertThat(rs.getString("unit")).isEqualTo("s")
                        assertThat(rs.getBigDecimal("max").compareTo(BigDecimal.valueOf(3.0))).isEqualTo(0)
                        assertThat(rs.getString("other")).isEqualTo("""{"p99":"2.8"}""")
                    }
                }
            }
        }

    protected companion object {
        const val INSERT_SQL =
            "INSERT INTO campaign_meters (name, tags, timestamp, tenant, campaign, scenario, \"type\", \"count\", \"value\", \"sum\", mean, unit, \"max\", other)" +
                    " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
    }
}
