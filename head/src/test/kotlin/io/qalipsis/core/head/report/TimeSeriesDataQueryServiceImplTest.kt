package io.qalipsis.core.head.report

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.mockk
import io.qalipsis.api.query.AggregationQueryExecutionContext
import io.qalipsis.api.query.DataRetrievalQueryExecutionContext
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.TimeSeriesAggregationResult
import io.qalipsis.api.report.TimeSeriesDataProvider
import io.qalipsis.api.report.TimeSeriesRecord
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignsInstantsAndDuration
import io.qalipsis.core.head.jdbc.repository.DataSeriesRepository
import io.qalipsis.core.head.report.catadioptre.calculateMinimumAggregationTimeframe
import io.qalipsis.core.head.report.catadioptre.sanitizeAggregationRequest
import io.qalipsis.core.head.report.catadioptre.sanitizeRetrievalRequest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import java.time.Instant

@WithMockk
internal class TimeSeriesDataQueryServiceImplTest {

    @RegisterExtension
    val testDataProvider = TestDispatcherProvider()

    @MockK
    private lateinit var timeSeriesDataProvider: TimeSeriesDataProvider

    @MockK
    private lateinit var dataSeriesRepository: DataSeriesRepository

    @MockK
    private lateinit var campaignRepository: CampaignRepository

    @InjectMockKs
    @SpyK(recordPrivateCalls = true)
    private lateinit var timeSeriesDataQueryService: TimeSeriesDataQueryServiceImpl

    @Test
    internal fun `should not aggregate when there is no data series with prepared query`() = testDataProvider.runTest {
        // given
        coEvery {
            dataSeriesRepository.findAllByTenantAndReferences(
                "my-tenant",
                setOf("ds-1", "ds-2")
            )
        } returns listOf(
            mockk { every { query } returns null }
        )

        // when
        val result = timeSeriesDataQueryService.render("my-tenant", setOf("ds-1", "ds-2"), mockk())

        // then
        assertThat(result).isEmpty()
        coVerifyOnce { dataSeriesRepository.findAllByTenantAndReferences("my-tenant", setOf("ds-1", "ds-2")) }
        confirmVerified(dataSeriesRepository, campaignRepository, timeSeriesDataProvider)
    }

    @Test
    internal fun `should not aggregate when there are data series with prepared query but no campaign`() =
        testDataProvider.runTest {
            // given
            coEvery {
                dataSeriesRepository.findAllByTenantAndReferences(
                    "my-tenant",
                    setOf("ds-1", "ds-2")
                )
            } returns listOf(
                mockk { every { query } returns "my-query" }
            )
            coEvery {
                campaignRepository.findInstantsAndDuration(
                    "my-tenant",
                    setOf("camp-1", "camp-2")
                )
            } returns CampaignsInstantsAndDuration(null, null, null)

            // when
            val result = timeSeriesDataQueryService.render("my-tenant", setOf("ds-1", "ds-2"), mockk {
                every { campaignsReferences } returns setOf("camp-1", "camp-2")
            })

            // then
            assertThat(result).isEmpty()
            coVerifyOrder {
                dataSeriesRepository.findAllByTenantAndReferences("my-tenant", setOf("ds-1", "ds-2"))
                campaignRepository.findInstantsAndDuration("my-tenant", setOf("camp-1", "camp-2"))
            }
            confirmVerified(dataSeriesRepository, campaignRepository, timeSeriesDataProvider)
        }

    @Test
    internal fun `should sanitize the context and aggregate data when data series and campaigns are found`() =
        testDataProvider.runTest {
            // given
            coEvery {
                dataSeriesRepository.findAllByTenantAndReferences(
                    "my-tenant",
                    setOf("ds-1", "ds-2")
                )
            } returns listOf(
                mockk {
                    every { reference } returns "ref-1"
                    every { query } returns "my-query-1"
                },
                mockk {
                    every { reference } returns "ref-2"
                    every { query } returns "my-query-2"
                }
            )
            val campaignsInstantsAndDuration = CampaignsInstantsAndDuration(
                minStart = Instant.now() - Duration.ofMinutes(3), maxEnd = Instant.now(), maxDurationSec = 6_000
            )
            coEvery {
                campaignRepository.findInstantsAndDuration(
                    "my-tenant",
                    setOf("camp-1", "camp-2")
                )
            } returns campaignsInstantsAndDuration
            val aggregationResult = mockk<Map<String, List<TimeSeriesAggregationResult>>>()
            coEvery { timeSeriesDataProvider.executeAggregations(any(), any()) } returns aggregationResult
            val providedRequest = mockk<AggregationQueryExecutionRequest> {
                every { campaignsReferences } returns setOf("camp-1", "camp-2")
            }
            val sanitizedAggregationContext = mockk<AggregationQueryExecutionContext>()
            coEvery {
                timeSeriesDataQueryService["sanitizeAggregationRequest"](
                    refEq(providedRequest),
                    refEq(campaignsInstantsAndDuration)
                )
            } returns sanitizedAggregationContext

            // when
            val result = timeSeriesDataQueryService.render("my-tenant", setOf("ds-1", "ds-2"), providedRequest)

            // then
            assertThat(result).isSameAs(aggregationResult)
            coVerifyOrder {
                dataSeriesRepository.findAllByTenantAndReferences("my-tenant", setOf("ds-1", "ds-2"))
                campaignRepository.findInstantsAndDuration("my-tenant", setOf("camp-1", "camp-2"))
                timeSeriesDataQueryService["sanitizeAggregationRequest"](
                    refEq(providedRequest),
                    refEq(campaignsInstantsAndDuration)
                )
                timeSeriesDataProvider.executeAggregations(
                    mapOf("ref-1" to "my-query-1", "ref-2" to "my-query-2"),
                    refEq(sanitizedAggregationContext)
                )
            }
            confirmVerified(dataSeriesRepository, campaignRepository, timeSeriesDataProvider)
        }

    @Test
    internal fun `should replace the start of aggregation context when too early`() {
        // given
        val start = Instant.now() - Duration.ofMinutes(2)
        val end = Instant.now()
        val timeFrame = Duration.ofSeconds(1)
        val request = AggregationQueryExecutionRequest(
            campaignsReferences = setOf("camp-1", "camp-2"),
            scenariosNames = setOf("scen-1", "scen-2"),
            from = start,
            until = end,
            aggregationTimeframe = timeFrame
        )
        val campaignsInstantsAndDuration = CampaignsInstantsAndDuration(
            minStart = start.plusMillis(1), maxEnd = end.plusMillis(1), maxDurationSec = 120
        )

        // when
        val result = timeSeriesDataQueryService.sanitizeAggregationRequest(request, campaignsInstantsAndDuration)

        // then
        assertThat(result).all {
            prop(AggregationQueryExecutionContext::campaignsReferences).containsAll("camp-1", "camp-2")
            prop(AggregationQueryExecutionContext::scenariosNames).containsAll("scen-1", "scen-2")
            prop(AggregationQueryExecutionContext::from).isEqualTo(start.plusMillis(1))
            prop(AggregationQueryExecutionContext::until).isEqualTo(end)
            prop(AggregationQueryExecutionContext::aggregationTimeframe).isEqualTo(timeFrame)
        }
    }

    @Test
    internal fun `should replace the end of aggregation context when too late`() {
        // given
        val start = Instant.now() - Duration.ofMinutes(2)
        val end = Instant.now()
        val timeFrame = Duration.ofSeconds(1)
        val request = AggregationQueryExecutionRequest(
            campaignsReferences = setOf("camp-1", "camp-2"),
            scenariosNames = setOf("scen-1", "scen-2"),
            from = start,
            until = end,
            aggregationTimeframe = timeFrame
        )
        val campaignsInstantsAndDuration = CampaignsInstantsAndDuration(
            minStart = start.minusMillis(1), maxEnd = end.minusMillis(1), maxDurationSec = 120
        )

        // when
        val result = timeSeriesDataQueryService.sanitizeAggregationRequest(request, campaignsInstantsAndDuration)

        // then
        assertThat(result).all {
            prop(AggregationQueryExecutionContext::campaignsReferences).containsAll("camp-1", "camp-2")
            prop(AggregationQueryExecutionContext::scenariosNames).containsAll("scen-1", "scen-2")
            prop(AggregationQueryExecutionContext::from).isEqualTo(start)
            prop(AggregationQueryExecutionContext::until).isEqualTo(end.minusMillis(1))
            prop(AggregationQueryExecutionContext::aggregationTimeframe).isEqualTo(timeFrame)
        }
    }

    @Test
    internal fun `should replace the timeframe of aggregation context when too low`() {
        // given
        val start = Instant.now() - Duration.ofMinutes(2)
        val end = Instant.now()
        val timeFrame = Duration.ofMillis(1)
        val request = AggregationQueryExecutionRequest(
            campaignsReferences = setOf("camp-1", "camp-2"),
            scenariosNames = setOf("scen-1", "scen-2"),
            from = start,
            until = end,
            aggregationTimeframe = timeFrame
        )
        val campaignsInstantsAndDuration = CampaignsInstantsAndDuration(
            minStart = start.minusMillis(1), maxEnd = end.plusMillis(1), maxDurationSec = 120
        )

        // when
        val result = timeSeriesDataQueryService.sanitizeAggregationRequest(request, campaignsInstantsAndDuration)

        // then
        assertThat(result).all {
            prop(AggregationQueryExecutionContext::campaignsReferences).containsAll("camp-1", "camp-2")
            prop(AggregationQueryExecutionContext::scenariosNames).containsAll("scen-1", "scen-2")
            prop(AggregationQueryExecutionContext::from).isEqualTo(start)
            prop(AggregationQueryExecutionContext::until).isEqualTo(end)
            prop(AggregationQueryExecutionContext::aggregationTimeframe).isEqualTo(Duration.ofSeconds(1))
        }
    }

    @Test
    internal fun `should set values of the aggregation context when missing`() {
        // given
        val start = Instant.now() - Duration.ofMinutes(2)
        val end = Instant.now()
        val request = AggregationQueryExecutionRequest(
            campaignsReferences = setOf("camp-1", "camp-2"),
            scenariosNames = setOf("scen-1", "scen-2"),
            from = null,
            until = null,
            aggregationTimeframe = null
        )
        val campaignsInstantsAndDuration = CampaignsInstantsAndDuration(
            minStart = start, maxEnd = end, maxDurationSec = 120
        )

        // when
        val result = timeSeriesDataQueryService.sanitizeAggregationRequest(request, campaignsInstantsAndDuration)

        // then
        assertThat(result).all {
            prop(AggregationQueryExecutionContext::campaignsReferences).containsAll("camp-1", "camp-2")
            prop(AggregationQueryExecutionContext::scenariosNames).containsAll("scen-1", "scen-2")
            prop(AggregationQueryExecutionContext::from).isEqualTo(start)
            prop(AggregationQueryExecutionContext::until).isEqualTo(end)
            prop(AggregationQueryExecutionContext::aggregationTimeframe).isEqualTo(Duration.ofSeconds(1))
        }
    }

    @Test
    internal fun `should keep the aggregation context when values are within the limits`() {
        // given
        val start = Instant.now() - Duration.ofMinutes(2)
        val end = Instant.now()
        val timeFrame = Duration.ofSeconds(1)
        val request = AggregationQueryExecutionRequest(
            campaignsReferences = setOf("camp-1", "camp-2"),
            scenariosNames = setOf("scen-1", "scen-2"),
            from = start,
            until = end,
            aggregationTimeframe = timeFrame
        )
        val campaignsInstantsAndDuration = CampaignsInstantsAndDuration(
            minStart = start.minusMillis(1), maxEnd = end.plusMillis(1), maxDurationSec = 120
        )

        // when
        val result = timeSeriesDataQueryService.sanitizeAggregationRequest(request, campaignsInstantsAndDuration)

        // then
        assertThat(result).all {
            prop(AggregationQueryExecutionContext::campaignsReferences).containsAll("camp-1", "camp-2")
            prop(AggregationQueryExecutionContext::scenariosNames).containsAll("scen-1", "scen-2")
            prop(AggregationQueryExecutionContext::from).isEqualTo(start)
            prop(AggregationQueryExecutionContext::until).isEqualTo(end)
            prop(AggregationQueryExecutionContext::aggregationTimeframe).isEqualTo(timeFrame)
        }
    }

    @Test
    internal fun `should not retrieve when there is no data series with prepared query`() = testDataProvider.runTest {
        // given
        coEvery {
            dataSeriesRepository.findAllByTenantAndReferences(
                "my-tenant",
                setOf("ds-1", "ds-2")
            )
        } returns listOf(
            mockk { every { query } returns null }
        )

        // when
        val result = timeSeriesDataQueryService.search("my-tenant", setOf("ds-1", "ds-2"), mockk())

        // then
        assertThat(result).isEmpty()
        coVerifyOnce { dataSeriesRepository.findAllByTenantAndReferences("my-tenant", setOf("ds-1", "ds-2")) }
        confirmVerified(dataSeriesRepository, campaignRepository, timeSeriesDataProvider)
    }

    @Test
    internal fun `should not retrieve when there are data series with prepared query but no campaign`() =
        testDataProvider.runTest {
            // given
            coEvery {
                dataSeriesRepository.findAllByTenantAndReferences(
                    "my-tenant",
                    setOf("ds-1", "ds-2")
                )
            } returns listOf(
                mockk { every { query } returns "my-query" }
            )
            coEvery {
                campaignRepository.findInstantsAndDuration(
                    "my-tenant",
                    setOf("camp-1", "camp-2")
                )
            } returns CampaignsInstantsAndDuration(null, null, null)

            // when
            val result = timeSeriesDataQueryService.search("my-tenant", setOf("ds-1", "ds-2"), mockk {
                every { campaignsReferences } returns setOf("camp-1", "camp-2")
            })

            // then
            assertThat(result).isEmpty()
            coVerifyOrder {
                dataSeriesRepository.findAllByTenantAndReferences("my-tenant", setOf("ds-1", "ds-2"))
                campaignRepository.findInstantsAndDuration("my-tenant", setOf("camp-1", "camp-2"))
            }
            confirmVerified(dataSeriesRepository, campaignRepository, timeSeriesDataProvider)
        }

    @Test
    internal fun `should sanitize the context and retrieve data when data series and campaigns are found`() =
        testDataProvider.runTest {
            // given
            coEvery {
                dataSeriesRepository.findAllByTenantAndReferences(
                    "my-tenant",
                    setOf("ds-1", "ds-2")
                )
            } returns listOf(
                mockk {
                    every { reference } returns "ref-1"
                    every { query } returns "my-query-1"
                },
                mockk {
                    every { reference } returns "ref-2"
                    every { query } returns "my-query-2"
                }
            )
            val campaignsInstantsAndDuration = CampaignsInstantsAndDuration(
                minStart = Instant.now() - Duration.ofMinutes(3), maxEnd = Instant.now(), maxDurationSec = 6_000
            )
            coEvery {
                campaignRepository.findInstantsAndDuration(
                    "my-tenant",
                    setOf("camp-1", "camp-2")
                )
            } returns campaignsInstantsAndDuration
            val retrievalResult = mockk<Map<String, Page<TimeSeriesRecord>>>()
            coEvery { timeSeriesDataProvider.retrieveRecords(any(), any()) } returns retrievalResult
            val providedRequest = mockk<DataRetrievalQueryExecutionRequest> {
                every { campaignsReferences } returns setOf("camp-1", "camp-2")
            }
            val sanitizedRetrievalContext = mockk<DataRetrievalQueryExecutionContext>()
            coEvery {
                timeSeriesDataQueryService["sanitizeRetrievalRequest"](
                    refEq(providedRequest),
                    refEq(campaignsInstantsAndDuration)
                )
            } returns sanitizedRetrievalContext

            // when
            val result = timeSeriesDataQueryService.search("my-tenant", setOf("ds-1", "ds-2"), providedRequest)

            // then
            assertThat(result).isSameAs(retrievalResult)
            coVerifyOrder {
                dataSeriesRepository.findAllByTenantAndReferences("my-tenant", setOf("ds-1", "ds-2"))
                campaignRepository.findInstantsAndDuration("my-tenant", setOf("camp-1", "camp-2"))
                timeSeriesDataQueryService["sanitizeRetrievalRequest"](
                    refEq(providedRequest),
                    refEq(campaignsInstantsAndDuration)
                )
                timeSeriesDataProvider.retrieveRecords(
                    mapOf("ref-1" to "my-query-1", "ref-2" to "my-query-2"),
                    refEq(sanitizedRetrievalContext)
                )
            }
            confirmVerified(dataSeriesRepository, campaignRepository, timeSeriesDataProvider)
        }

    @Test
    internal fun `should replace the start of retrieval context when too early`() {
        // given
        val start = Instant.now() - Duration.ofMinutes(2)
        val end = Instant.now()
        val timeFrame = Duration.ofSeconds(1)
        val request = DataRetrievalQueryExecutionRequest(
            campaignsReferences = setOf("camp-1", "camp-2"),
            scenariosNames = setOf("scen-1", "scen-2"),
            from = start,
            until = end,
            aggregationTimeframe = timeFrame,
            page = 8,
            size = 165
        )
        val campaignsInstantsAndDuration = CampaignsInstantsAndDuration(
            minStart = start.plusMillis(1), maxEnd = end.plusMillis(1), maxDurationSec = 120
        )

        // when
        val result = timeSeriesDataQueryService.sanitizeRetrievalRequest(request, campaignsInstantsAndDuration)

        // then
        assertThat(result).all {
            prop(DataRetrievalQueryExecutionContext::campaignsReferences).containsAll("camp-1", "camp-2")
            prop(DataRetrievalQueryExecutionContext::scenariosNames).containsAll("scen-1", "scen-2")
            prop(DataRetrievalQueryExecutionContext::from).isEqualTo(start.plusMillis(1))
            prop(DataRetrievalQueryExecutionContext::until).isEqualTo(end)
            prop(DataRetrievalQueryExecutionContext::aggregationTimeframe).isEqualTo(timeFrame)
            prop(DataRetrievalQueryExecutionContext::page).isEqualTo(8)
            prop(DataRetrievalQueryExecutionContext::size).isEqualTo(165)
        }
    }

    @Test
    internal fun `should replace the end of retrieval context when too late`() {
        // given
        val start = Instant.now() - Duration.ofMinutes(2)
        val end = Instant.now()
        val timeFrame = Duration.ofSeconds(1)
        val request = DataRetrievalQueryExecutionRequest(
            campaignsReferences = setOf("camp-1", "camp-2"),
            scenariosNames = setOf("scen-1", "scen-2"),
            from = start,
            until = end,
            aggregationTimeframe = timeFrame,
            page = 8,
            size = 165
        )
        val campaignsInstantsAndDuration = CampaignsInstantsAndDuration(
            minStart = start.minusMillis(1), maxEnd = end.minusMillis(1), maxDurationSec = 120
        )

        // when
        val result = timeSeriesDataQueryService.sanitizeRetrievalRequest(request, campaignsInstantsAndDuration)

        // then
        assertThat(result).all {
            prop(DataRetrievalQueryExecutionContext::campaignsReferences).containsAll("camp-1", "camp-2")
            prop(DataRetrievalQueryExecutionContext::scenariosNames).containsAll("scen-1", "scen-2")
            prop(DataRetrievalQueryExecutionContext::from).isEqualTo(start)
            prop(DataRetrievalQueryExecutionContext::until).isEqualTo(end.minusMillis(1))
            prop(DataRetrievalQueryExecutionContext::aggregationTimeframe).isEqualTo(timeFrame)
            prop(DataRetrievalQueryExecutionContext::page).isEqualTo(8)
            prop(DataRetrievalQueryExecutionContext::size).isEqualTo(165)
        }
    }

    @Test
    internal fun `should replace the timeframe of retrieval context when too low`() {
        // given
        val start = Instant.now() - Duration.ofMinutes(2)
        val end = Instant.now()
        val timeFrame = Duration.ofMillis(1)
        val request = DataRetrievalQueryExecutionRequest(
            campaignsReferences = setOf("camp-1", "camp-2"),
            scenariosNames = setOf("scen-1", "scen-2"),
            from = start,
            until = end,
            aggregationTimeframe = timeFrame,
            page = 8,
            size = 165
        )
        val campaignsInstantsAndDuration = CampaignsInstantsAndDuration(
            minStart = start.minusMillis(1), maxEnd = end.plusMillis(1), maxDurationSec = 120
        )

        // when
        val result = timeSeriesDataQueryService.sanitizeRetrievalRequest(request, campaignsInstantsAndDuration)

        // then
        assertThat(result).all {
            prop(DataRetrievalQueryExecutionContext::campaignsReferences).containsAll("camp-1", "camp-2")
            prop(DataRetrievalQueryExecutionContext::scenariosNames).containsAll("scen-1", "scen-2")
            prop(DataRetrievalQueryExecutionContext::from).isEqualTo(start)
            prop(DataRetrievalQueryExecutionContext::until).isEqualTo(end)
            prop(DataRetrievalQueryExecutionContext::aggregationTimeframe).isEqualTo(Duration.ofSeconds(1))
            prop(DataRetrievalQueryExecutionContext::page).isEqualTo(8)
            prop(DataRetrievalQueryExecutionContext::size).isEqualTo(165)
        }
    }

    @Test
    internal fun `should set values of the retrieval context when missing`() {
        // given
        val start = Instant.now() - Duration.ofMinutes(2)
        val end = Instant.now()
        val request = DataRetrievalQueryExecutionRequest(
            campaignsReferences = setOf("camp-1", "camp-2"),
            scenariosNames = setOf("scen-1", "scen-2"),
            from = start,
            until = end,
            aggregationTimeframe = null,
            page = 8,
            size = 165
        )
        val campaignsInstantsAndDuration = CampaignsInstantsAndDuration(
            minStart = start.minusMillis(1), maxEnd = end.plusMillis(1), maxDurationSec = 120
        )

        // when
        val result = timeSeriesDataQueryService.sanitizeRetrievalRequest(request, campaignsInstantsAndDuration)

        // then
        assertThat(result).all {
            prop(DataRetrievalQueryExecutionContext::campaignsReferences).containsAll("camp-1", "camp-2")
            prop(DataRetrievalQueryExecutionContext::scenariosNames).containsAll("scen-1", "scen-2")
            prop(DataRetrievalQueryExecutionContext::from).isEqualTo(start)
            prop(DataRetrievalQueryExecutionContext::until).isEqualTo(end)
            prop(DataRetrievalQueryExecutionContext::aggregationTimeframe).isEqualTo(Duration.ofSeconds(1))
            prop(DataRetrievalQueryExecutionContext::page).isEqualTo(8)
            prop(DataRetrievalQueryExecutionContext::size).isEqualTo(165)
        }
    }

    @Test
    internal fun `should keep the retrieval context when values are within the limits`() {
        // given
        val start = Instant.now() - Duration.ofMinutes(2)
        val end = Instant.now()
        val timeFrame = Duration.ofSeconds(1)
        val request = DataRetrievalQueryExecutionRequest(
            campaignsReferences = setOf("camp-1", "camp-2"),
            scenariosNames = setOf("scen-1", "scen-2"),
            from = start,
            until = end,
            aggregationTimeframe = timeFrame,
            page = 8,
            size = 165
        )
        val campaignsInstantsAndDuration = CampaignsInstantsAndDuration(
            minStart = start.minusMillis(1), maxEnd = end.plusMillis(1), maxDurationSec = 120
        )

        // when
        val result = timeSeriesDataQueryService.sanitizeRetrievalRequest(request, campaignsInstantsAndDuration)

        // then
        assertThat(result).all {
            prop(DataRetrievalQueryExecutionContext::campaignsReferences).containsAll("camp-1", "camp-2")
            prop(DataRetrievalQueryExecutionContext::scenariosNames).containsAll("scen-1", "scen-2")
            prop(DataRetrievalQueryExecutionContext::from).isEqualTo(start)
            prop(DataRetrievalQueryExecutionContext::until).isEqualTo(end)
            prop(DataRetrievalQueryExecutionContext::aggregationTimeframe).isEqualTo(timeFrame)
            prop(DataRetrievalQueryExecutionContext::page).isEqualTo(8)
            prop(DataRetrievalQueryExecutionContext::size).isEqualTo(165)
        }
    }

    @ParameterizedTest(name = "should calculate the minimal aggregation stage: ${ParameterizedTest.ARGUMENTS_PLACEHOLDER}")
    @CsvSource(
        "PT0.5S,PT1S",
        "PT400S,PT1S",
        "PT401S,PT5S",
        "PT2000S,PT5S",
        "PT2001S,PT10S",
        "PT4000S,PT10S",
        "PT4001S,PT30S",
        "PT200M,PT30S",
        "PT201M,PT1M",
        "PT400M,PT1M",
        "PT400M1S,PT5M",
        "PT2000M,PT5M",
        "PT2000M1S,PT10M",
        "PT4000M,PT10M",
        "PT4000M1S,PT30M",
        "PT200H,PT30M",
        "PT200H1M,PT1H",
        "PT400H,PT1H",
        "PT400H1S,PT2H",
        "PT800H,PT2H",
        "PT800H1S,PT4H",
        "PT1600H,PT4H",
        "PT1600H1S,PT6H",
        "P100D,PT6H",
        "P100DT1S,PT12H",
        "P200D,PT12H",
        "P200DT1S,P1D",
        "P400D,P1D",
        "P400DT1S,P2D",
        "P800D,P2D",
        "P800DT1S,P7D",
        "P40000D,P7D"
    )
    internal fun `should calculate the minimal aggregation stage`(
        maxCampaignDuration: Duration,
        expectedTimeframe: Duration
    ) {
        assertThat(timeSeriesDataQueryService.calculateMinimumAggregationTimeframe(maxCampaignDuration)).isEqualTo(
            expectedTimeframe
        )
    }
}