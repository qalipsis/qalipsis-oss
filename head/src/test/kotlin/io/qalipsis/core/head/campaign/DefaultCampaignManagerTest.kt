package io.qalipsis.core.head.campaign

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.key
import assertk.assertions.prop
import cool.graph.cuid.Cuid
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.aerisconsulting.catadioptre.getProperty
import io.aerisconsulting.catadioptre.setProperty
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.orchestration.directives.DirectiveKey
import io.qalipsis.api.orchestration.directives.DirectiveProducer
import io.qalipsis.api.orchestration.feedbacks.DirectiveFeedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.directives.CampaignStartDirective
import io.qalipsis.core.directives.MinionsCreationDirectiveReference
import io.qalipsis.core.directives.MinionsCreationPreparationDirective
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.feedbacks.CampaignStartedForDagFeedback
import io.qalipsis.core.feedbacks.FeedbackHeadChannel
import io.qalipsis.core.head.campaign.catadioptre.currentCampaignConfiguration
import io.qalipsis.core.head.campaign.catadioptre.receivedMinionsCreationPreparationFeedback
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.lang.TestIdGenerator
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.TimeUnit

/**
 * @author Eric Jessé
 */
@WithMockk
internal class DefaultCampaignManagerTest {

    @RelaxedMockK
    lateinit var feedbackHeadChannel: FeedbackHeadChannel

    @RelaxedMockK
    lateinit var factoryService: FactoryService

    @RelaxedMockK
    lateinit var directiveProducer: DirectiveProducer

    @RelaxedMockK
    lateinit var headConfiguration: HeadConfiguration

    private val idGenerator = TestIdGenerator

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    private val configuration = DataCampaignConfiguration(
        id = "my-campaign",
        minionsCountPerScenario = 123123,
        minionsFactor = 1.87,
        speedFactor = 54.87,
        startOffsetMs = 12367,
        scenarios = listOf("scen-1", "scen-2"),
    )

    @BeforeEach
    fun setup() {
        every { headConfiguration.broadcastChannel } returns "broadcast-channel"
    }

    @Test
    @Timeout(3)
    internal fun `should start minions creation with the specified configuration`() =
        testCoroutineDispatcher.runTest {
            // given
            val campaignManager = DefaultCampaignManager(
                feedbackHeadChannel = feedbackHeadChannel, factoryService = factoryService,
                directiveProducer = directiveProducer, coroutineScope = this, headConfiguration, idGenerator
            )
            val scenario1 = ScenarioSummary("scen-1", 4, listOf())
            val scenario2 = ScenarioSummary("scen-2", 17, listOf())
            coEvery { factoryService.getAllScenarios(any()) } returns listOf(scenario1, scenario2)
            val directives = mutableListOf<MinionsCreationPreparationDirective>()
            coEvery { directiveProducer.publish(capture(directives)) } answers {}
            val onCriticalFailure: (String) -> Unit = { _ -> println("Do something") }

            // when
            campaignManager.start(
                configuration,
                onCriticalFailure = onCriticalFailure
            )

            // then
            coVerifyOrder {
                factoryService.getAllScenarios(eq(listOf("scen-1", "scen-2")))
                directiveProducer.publish(any())
                directiveProducer.publish(any())
            }
            assertThat(campaignManager.currentCampaignConfiguration()).isSameAs(configuration)
            assertThat(directives).all {
                hasSize(2)
                each {
                    it.prop(MinionsCreationPreparationDirective::value).isEqualTo(123123)
                }
                any {
                    it.all {
                        prop(MinionsCreationPreparationDirective::campaignId).isEqualTo("my-campaign")
                        prop(MinionsCreationPreparationDirective::scenarioId).isEqualTo("scen-1")
                    }
                }
                any {
                    it.all {
                        prop(MinionsCreationPreparationDirective::campaignId).isEqualTo("my-campaign")
                        prop(MinionsCreationPreparationDirective::scenarioId).isEqualTo("scen-2")
                    }
                }
            }
            val scenarios = campaignManager.getProperty<Map<ScenarioId, ScenarioSummary>>("scenarios")
            assertThat(scenarios).all {
                key("scen-1").isSameAs(scenario1)
                key("scen-2").isSameAs(scenario2)
            }
            val usedOnCriticalFailure = campaignManager.getProperty<(String) -> Unit>("onCriticalFailure")
            assertThat(usedOnCriticalFailure).isSameAs(onCriticalFailure)
        }

    @Test
    @Timeout(3)
    internal fun `should accept MinionsCreationDirectiveReference`() = testCoroutineDispatcher.runTest {
        // given
        val campaignManager = DefaultCampaignManager(
            feedbackHeadChannel = feedbackHeadChannel, factoryService = factoryService,
            directiveProducer = directiveProducer, coroutineScope = this, headConfiguration, idGenerator
        )

        // when
        Assertions.assertTrue(campaignManager.accept(MinionsCreationDirectiveReference("", "", "", "", channel = "broadcast")))
    }

    @Test
    @Timeout(3)
    internal fun `should not accept other directives`() = testCoroutineDispatcher.runTest {
        // given
        val campaignManager = DefaultCampaignManager(
            feedbackHeadChannel = feedbackHeadChannel, factoryService = factoryService,
            directiveProducer = directiveProducer, coroutineScope = this, headConfiguration, idGenerator
        )

        // when
        Assertions.assertTrue(campaignManager.accept(MinionsCreationDirectiveReference("", "", "", "", channel = "broadcast")))
    }

    @Test
    @Timeout(3)
    internal fun `should store the directives`() = testCoroutineDispatcher.runTest {
        // given
        val campaignManager = DefaultCampaignManager(
            feedbackHeadChannel = feedbackHeadChannel, factoryService = factoryService,
            directiveProducer = directiveProducer, coroutineScope = this, headConfiguration, idGenerator
        )
        val directive = MinionsCreationDirectiveReference("directive", "", "", "", channel = "broadcast")

        // when
        campaignManager.process(directive)

        val directivesInProgress =
            campaignManager.getProperty<MutableMap<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                "directivesInProgress"
            )
        Assertions.assertSame(directive, directivesInProgress["directive"]!!.directive)
    }

    @Test
    @Timeout(3)
    internal fun `should process MinionsCreationPreparationDirective in progress when the directive exists`() =
        testCoroutineDispatcher.runTest {
            // given
            val countDownLatch = SuspendedCountLatch(1)
            val directive = MinionsCreationPreparationDirective("directive", "", 123, channel = "broadcast", key = TestIdGenerator.short())
            val campaignManager = spyk(
                DefaultCampaignManager(
                    feedbackHeadChannel = feedbackHeadChannel, factoryService = factoryService,
                    directiveProducer = directiveProducer, coroutineScope = this, headConfiguration, idGenerator
                ), recordPrivateCalls = true
            ) {
                coEvery {
                    receivedMinionsCreationPreparationFeedback(any(), any())
                } coAnswers { countDownLatch.decrement() }
            }
            val directivesInProgress =
                campaignManager.getProperty<MutableMap<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                    "directivesInProgress"
                )
            directivesInProgress[directive.key] = DefaultCampaignManager.DirectiveInProgress(directive)
            val directiveFeedback = DirectiveFeedback("", directive.key, FeedbackStatus.IN_PROGRESS)

            // when
            campaignManager.coInvokeInvisible<Void>("processDirectiveFeedback", directiveFeedback)

            // then
            countDownLatch.await()
            coVerifyOnce {
                campaignManager.receivedMinionsCreationPreparationFeedback(refEq(directiveFeedback), refEq(directive))
            }
            // The directive is still in the cache.
            Assertions.assertTrue(directivesInProgress.containsKey(directive.key))
        }

    @Test
    @Timeout(3)
    internal fun `should process done MinionsCreationPreparationDirective when the directive exists`() =
        testCoroutineDispatcher.runTest {
            // given
            val countDownLatch = SuspendedCountLatch(1)
            val directive = MinionsCreationPreparationDirective("directive", "", 123, channel = "broadcast", key = TestIdGenerator.short())
            val campaignManager = spyk(
                DefaultCampaignManager(
                    feedbackHeadChannel = feedbackHeadChannel, factoryService = factoryService,
                    directiveProducer = directiveProducer, coroutineScope = this, headConfiguration, idGenerator
                ), recordPrivateCalls = true
            ) {
                coEvery {
                    receivedMinionsCreationPreparationFeedback(any(), any())
                } coAnswers { countDownLatch.decrement() }
            }
            val directivesInProgress =
                campaignManager.getProperty<MutableMap<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                    "directivesInProgress"
                )
            directivesInProgress[directive.key] = DefaultCampaignManager.DirectiveInProgress(directive)
            val directiveFeedback = DirectiveFeedback("", directive.key, FeedbackStatus.COMPLETED)

            // when
            campaignManager.coInvokeInvisible<Unit>("processDirectiveFeedback", directiveFeedback)

            // then
            countDownLatch.await()
            coVerifyOnce {
                campaignManager.receivedMinionsCreationPreparationFeedback(refEq(directiveFeedback), refEq(directive))
            }
        }

    @Test
    @Timeout(3)
    internal fun `should ignore MinionsCreationPreparationDirective when the directive does not exist`() =
        testCoroutineDispatcher.runTest {
            // given
            val countDownLatch = SuspendedCountLatch(1)
            val campaignManager = spyk(
                DefaultCampaignManager(
                    feedbackHeadChannel = feedbackHeadChannel, factoryService = factoryService,
                    directiveProducer = directiveProducer, coroutineScope = this, headConfiguration, idGenerator
                )
            ) {
                coEvery {
                    receivedMinionsCreationPreparationFeedback(any(), any())
                } coAnswers { countDownLatch.decrement(1) }
            }
            val directiveFeedback = DirectiveFeedback("", "my-directive", FeedbackStatus.COMPLETED)

            // when
            campaignManager.coInvokeInvisible<Void>("processDirectiveFeedback", directiveFeedback)

            // then
            countDownLatch.await(50, TimeUnit.MILLISECONDS)
            coVerifyNever {
                campaignManager.receivedMinionsCreationPreparationFeedback(any(), any())
            }
        }

    @Test
    @Timeout(3)
    internal fun `should process MinionsCreationDirectiveReference in progress when the directive exists`() =
        testCoroutineDispatcher.runTest {
            // given
            val countDownLatch = SuspendedCountLatch(1)
            val directive = MinionsCreationDirectiveReference("directive", "", "", "", channel = "broadcast")
            val campaignManager = spyk(
                DefaultCampaignManager(
                    feedbackHeadChannel = feedbackHeadChannel, factoryService = factoryService,
                    directiveProducer = directiveProducer, coroutineScope = this, headConfiguration, idGenerator
                ), recordPrivateCalls = true
            )
            coEvery {
                campaignManager["receiveMinionsCreationDirectiveFeedback"](
                    any<DirectiveFeedback>(),
                    any<MinionsCreationDirectiveReference>()
                )
            } coAnswers { countDownLatch.decrement() }
            val directivesInProgress =
                campaignManager.getProperty<MutableMap<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                    "directivesInProgress"
                )
            directivesInProgress[directive.key] = DefaultCampaignManager.DirectiveInProgress(directive)
            val directiveFeedback = DirectiveFeedback("", directive.key, FeedbackStatus.IN_PROGRESS)

            // when
            campaignManager.coInvokeInvisible<Unit>("processDirectiveFeedback", directiveFeedback)

            // then
            countDownLatch.await()
            coVerifyOnce {
                campaignManager["receiveMinionsCreationDirectiveFeedback"](refEq(directiveFeedback), refEq(directive))
            }
            // The directive is still in the cache.
            Assertions.assertTrue(directivesInProgress.containsKey(directive.key))
        }

    @Test
    @Timeout(3)
    internal fun `should process done MinionsCreationDirectiveReference when the directive exists`() =
        testCoroutineDispatcher.runTest {
            // given
            val countDownLatch = SuspendedCountLatch(1)
            val directive = MinionsCreationDirectiveReference("directive", "", "", "", channel = "broadcast")
            val campaignManager = spyk(
                DefaultCampaignManager(
                    feedbackHeadChannel = feedbackHeadChannel, factoryService = factoryService,
                    directiveProducer = directiveProducer, coroutineScope = this, headConfiguration, idGenerator
                ), recordPrivateCalls = true
            )
            coEvery {
                campaignManager["receiveMinionsCreationDirectiveFeedback"](
                    any<DirectiveFeedback>(),
                    any<MinionsCreationDirectiveReference>()
                )
            } coAnswers { countDownLatch.decrement() }

            campaignManager.getProperty<MutableMap<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                "directivesInProgress"
            )[directive.key] = DefaultCampaignManager.DirectiveInProgress(directive)

            val directiveFeedback = DirectiveFeedback("", directive.key, FeedbackStatus.COMPLETED)

            // when
            campaignManager.coInvokeInvisible<Unit>("processDirectiveFeedback", directiveFeedback)

            // then
            countDownLatch.await()
            coVerifyOnce {
                campaignManager["receiveMinionsCreationDirectiveFeedback"](refEq(directiveFeedback), refEq(directive))
            }
        }

    @Test
    @Timeout(3)
    internal fun `should ignore MinionsCreationDirectiveReference when the directive does not exist`() =
        testCoroutineDispatcher.runTest {
            // given
            val countDownLatch = SuspendedCountLatch(1)
            val campaignManager = spyk(
                DefaultCampaignManager(
                    feedbackHeadChannel = feedbackHeadChannel, factoryService = factoryService,
                    directiveProducer = directiveProducer, coroutineScope = this, headConfiguration, idGenerator
                )
            ) {
                val manager = this
                coEvery {
                    manager["receiveMinionsCreationDirectiveFeedback"](
                        any<DirectiveFeedback>(),
                        any<MinionsCreationDirectiveReference>()
                    )
                } coAnswers { countDownLatch.decrement() }
            }
            val directiveFeedback = DirectiveFeedback("", "my-directive", FeedbackStatus.COMPLETED)

            // when
            campaignManager.coInvokeInvisible<Void>("processDirectiveFeedback", directiveFeedback)

            // then
            countDownLatch.await(50, TimeUnit.MILLISECONDS)
            coVerifyNever {
                campaignManager["receiveMinionsCreationDirectiveFeedback"](
                    any<DirectiveFeedback>(),
                    any<MinionsCreationDirectiveReference>()
                )
            }
        }

    @Test
    @Timeout(3)
    internal fun `should process failed feedback for MinionsCreationPreparationDirective`() =
        testCoroutineDispatcher.runTest {
            // given
            val countDownLatch = SuspendedCountLatch(1)
            val onCriticalFailure: (String) -> Unit = { _ -> countDownLatch.blockingDecrement() }
            val directive = MinionsCreationPreparationDirective("directive", "", 123, channel = "broadcast", key = TestIdGenerator.short())
            val campaignManager = DefaultCampaignManager(
                feedbackHeadChannel = feedbackHeadChannel, factoryService = factoryService,
                directiveProducer = directiveProducer, coroutineScope = this, headConfiguration, idGenerator
            )

            campaignManager.setProperty("onCriticalFailure", onCriticalFailure)
            val directiveFeedback = DirectiveFeedback("", directive.key, FeedbackStatus.FAILED)

            // when
            campaignManager.receivedMinionsCreationPreparationFeedback(directiveFeedback, directive)

            // then
            // Since the count down latch was decremented, the conCriticalFailure operation was called.
            countDownLatch.await()
        }

    @Test
    @Timeout(3)
    internal fun `should process failed feedback for MinionsCreationDirectiveReference`() =
        testCoroutineDispatcher.runTest {
            // given
            val countDownLatch = SuspendedCountLatch(1)
            val onCriticalFailure: (String) -> Unit = { _ -> countDownLatch.blockingDecrement() }
            val directive = MinionsCreationDirectiveReference("directive", "", "", "", channel = "broadcast")
            val campaignManager = DefaultCampaignManager(
                feedbackHeadChannel = feedbackHeadChannel, factoryService = factoryService,
                directiveProducer = directiveProducer, coroutineScope = this, headConfiguration, idGenerator
            )

            campaignManager.setProperty("onCriticalFailure", onCriticalFailure)
            val directiveFeedback = DirectiveFeedback("", directive.key, FeedbackStatus.FAILED)

            // when
            campaignManager.coInvokeInvisible<Void>(
                "receiveMinionsCreationDirectiveFeedback",
                directiveFeedback,
                directive
            )

            // then
            // Since the count down latch was decremented, the conCriticalFailure operation was called.
            countDownLatch.await()
        }

    @Test
    @Timeout(3)
    internal fun `should trigger campaign start when all the scenarios are ready`() = testCoroutineDispatcher.runTest {
        // given
        val campaignManager = DefaultCampaignManager(
            feedbackHeadChannel = feedbackHeadChannel, factoryService = factoryService,
            directiveProducer = directiveProducer, coroutineScope = this, headConfiguration, idGenerator
        )

        val directive1 = MinionsCreationDirectiveReference("directive", "camp-1", "scen-1", "dag-1", channel = "broadcast")
        val directive2 = MinionsCreationDirectiveReference("directive", "camp-1", "scen-1", "dag-2", channel = "broadcast")
        val directive3 = MinionsCreationDirectiveReference("directive", "camp-1", "scen-2", "dag-1", channel = "broadcast")
        val directive4 = MinionsCreationDirectiveReference("directive", "camp-1", "scen-2", "dag-2", channel = "broadcast")

        val scenario1 =
            ScenarioSummary(
                "scen-1",
                4,
                listOf(DirectedAcyclicGraphSummary("dag-1"), DirectedAcyclicGraphSummary("dag-2"))
            )
        val scenario2 =
            ScenarioSummary(
                "scen-2",
                17,
                listOf(DirectedAcyclicGraphSummary("dag-1"), DirectedAcyclicGraphSummary("dag-2"))
            )
        val scenarios = campaignManager.getProperty<MutableMap<ScenarioId, ScenarioSummary>>("scenarios")
        scenarios[scenario1.id] = scenario1
        scenarios[scenario2.id] = scenario2
        val publishedDirectives = mutableListOf<CampaignStartDirective>()
        coEvery { directiveProducer.publish(capture(publishedDirectives)) } answers {}
        val readyDagsByScenario =
            campaignManager.getProperty<MutableMap<ScenarioId, MutableCollection<DirectedAcyclicGraphId>>>(
                "readyDagsByScenario"
            )
        readyDagsByScenario["scen-1"] = concurrentSet()
        readyDagsByScenario["scen-2"] = concurrentSet()

        // when
        campaignManager.coInvokeInvisible<Void>(
            "receiveMinionsCreationDirectiveFeedback",
            DirectiveFeedback("", "", FeedbackStatus.COMPLETED),
            directive1
        )
        campaignManager.coInvokeInvisible<Void>(
            "receiveMinionsCreationDirectiveFeedback",
            DirectiveFeedback("", "", FeedbackStatus.COMPLETED),
            directive2
        )
        campaignManager.coInvokeInvisible<Void>(
            "receiveMinionsCreationDirectiveFeedback",
            DirectiveFeedback("", "", FeedbackStatus.COMPLETED),
            directive3
        )

        // then
        val readyScenarios = campaignManager.getProperty<Set<ScenarioId>>("readyScenarios")

        // Only one scenario is ready.
        Assertions.assertEquals(setOf("scen-1"), readyScenarios)

        // when
        campaignManager.coInvokeInvisible<Void>(
            "receiveMinionsCreationDirectiveFeedback",
            DirectiveFeedback("", "", FeedbackStatus.COMPLETED),
            directive4
        )

        assertThat(publishedDirectives).all {
            hasSize(2)
            any {
                it.isInstanceOf(CampaignStartDirective::class).all {
                    prop(CampaignStartDirective::campaignId).isEqualTo("camp-1")
                    prop(CampaignStartDirective::scenarioId).isEqualTo("scen-1")
                }
            }
            any {
                it.isInstanceOf(CampaignStartDirective::class).all {
                    prop(CampaignStartDirective::campaignId).isEqualTo("camp-1")
                    prop(CampaignStartDirective::scenarioId).isEqualTo("scen-2")
                }
            }
        }

        val directivesInProgress =
            campaignManager.getProperty<Map<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                "directivesInProgress"
            )
        Assertions.assertEquals(2, directivesInProgress.size)
    }

    @Test
    @Timeout(3)
    internal fun `should trigger ramp-up when all the DAGs are started`() = testCoroutineDispatcher.runTest {
        // given
        val campaignManager = DefaultCampaignManager(
            feedbackHeadChannel = feedbackHeadChannel, factoryService = factoryService,
            directiveProducer = directiveProducer, coroutineScope = this, headConfiguration, idGenerator
        )

        val feedback1 = CampaignStartedForDagFeedback("camp-1", "scen-1", "dag-1", FeedbackStatus.COMPLETED)
        val feedback2 = CampaignStartedForDagFeedback("camp-1", "scen-1", "dag-2", FeedbackStatus.COMPLETED)
        val feedback3 = CampaignStartedForDagFeedback("camp-1", "scen-2", "dag-1", FeedbackStatus.COMPLETED)
        val feedback4 = CampaignStartedForDagFeedback("camp-1", "scen-2", "dag-2", FeedbackStatus.COMPLETED)

        val scenario1 =
            ScenarioSummary(
                "scen-1",
                4,
                listOf(DirectedAcyclicGraphSummary("dag-1"), DirectedAcyclicGraphSummary("dag-2"))
            )
        val scenario2 =
            ScenarioSummary(
                "scen-2",
                17,
                listOf(DirectedAcyclicGraphSummary("dag-1"), DirectedAcyclicGraphSummary("dag-2"))
            )
        val startedDagsByScenario =
            campaignManager.getProperty<MutableMap<ScenarioId, MutableCollection<DirectedAcyclicGraphId>>>(
                "startedDagsByScenario"
            )
        startedDagsByScenario["scen-1"] = concurrentSet()
        startedDagsByScenario["scen-2"] = concurrentSet()

        val scenarios = campaignManager.getProperty<MutableMap<ScenarioId, ScenarioSummary>>("scenarios")
        scenarios[scenario1.id] = scenario1
        scenarios[scenario2.id] = scenario2
        val publishedDirectives = mutableListOf<MinionsRampUpPreparationDirective>()
        coEvery { directiveProducer.publish(capture(publishedDirectives)) } answers {}

        // when
        campaignManager.processFeedBack(feedback1)
        campaignManager.processFeedBack(feedback2)
        campaignManager.processFeedBack(feedback3)

        // then
        val startedScenarios = campaignManager.getProperty<Set<ScenarioId>>("startedScenarios")

        // Only one scenario is ready.
        Assertions.assertEquals(setOf("scen-1"), startedScenarios)

        // when
        campaignManager.currentCampaignConfiguration(configuration)
        campaignManager.processFeedBack(feedback4)

        assertThat(publishedDirectives).all {
            hasSize(2)
            any {
                it.isInstanceOf(MinionsRampUpPreparationDirective::class)
                it.prop(MinionsRampUpPreparationDirective::campaignId).isEqualTo("camp-1")
                it.prop(MinionsRampUpPreparationDirective::scenarioId).isEqualTo("scen-1")
                it.prop(MinionsRampUpPreparationDirective::startOffsetMs).isEqualTo(12367L)
                it.prop(MinionsRampUpPreparationDirective::speedFactor).isEqualTo(54.87)
            }
            any {
                it.isInstanceOf(MinionsRampUpPreparationDirective::class)
                it.prop(MinionsRampUpPreparationDirective::campaignId).isEqualTo("camp-1")
                it.prop(MinionsRampUpPreparationDirective::scenarioId).isEqualTo("scen-2")
                it.prop(MinionsRampUpPreparationDirective::startOffsetMs).isEqualTo(12367L)
                it.prop(MinionsRampUpPreparationDirective::speedFactor).isEqualTo(54.87)
            }
        }

        val directivesInProgress =
            campaignManager.getProperty<Map<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                "directivesInProgress"
            )
        Assertions.assertEquals(2, directivesInProgress.size)
    }


}
