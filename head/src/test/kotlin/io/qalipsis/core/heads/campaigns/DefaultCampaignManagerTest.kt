package io.qalipsis.core.heads.campaigns

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.key
import assertk.assertions.prop
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
import io.qalipsis.api.orchestration.feedbacks.FeedbackConsumer
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.directives.CampaignStartDirective
import io.qalipsis.core.directives.MinionsCreationDirectiveReference
import io.qalipsis.core.directives.MinionsCreationPreparationDirective
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.feedbacks.CampaignStartedForDagFeedback
import io.qalipsis.core.heads.campaigns.catadioptre.receivedMinionsCreationPreparationFeedback
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import org.junit.jupiter.api.Assertions
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
    lateinit var feedbackConsumer: FeedbackConsumer

    @RelaxedMockK
    lateinit var scenarioRepository: ScenarioSummaryRepository

    @RelaxedMockK
    lateinit var directiveProducer: DirectiveProducer

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    @Timeout(3)
    internal fun `should start minions creation with same minions total when campaign a starts and minionsCountPerScenario is specified`() =
        testCoroutineDispatcher.runTest {
            // given
            val campaignManager = DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer, coroutineScope = this
            )
            val scenario1 = ScenarioSummary("scen-1", 4, listOf())
            val scenario2 = ScenarioSummary("scen-2", 17, listOf())
            every { scenarioRepository.getAll(any()) } returns listOf(scenario1, scenario2)
            val directives = mutableListOf<MinionsCreationPreparationDirective>()
            coEvery { directiveProducer.publish(capture(directives)) } answers {}
            val onCriticalFailure: (String) -> Unit = { _ -> println("Do something") }

            // when
            campaignManager.start(
                "my-campaign",
                scenarios = listOf("scen-1", "scen-2"),
                onCriticalFailure = onCriticalFailure
            )

            // then
            coVerifyOrder {
                scenarioRepository.getAll(eq(listOf("scen-1", "scen-2")))
                directiveProducer.publish(any())
                directiveProducer.publish(any())
            }
            assertThat(directives).all {
                hasSize(2)
                each {
                    it.prop(MinionsCreationPreparationDirective::value).isEqualTo(10)
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
    internal fun `should start minions creation with same minions total when campaign a starts and minionsCountFactor is specified`() =
        testCoroutineDispatcher.runTest {
            // given
            val campaignManager = DefaultCampaignManager(
                minionsCountFactor = 2.0, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer, coroutineScope = this
            )
            val scenario1 = ScenarioSummary("scen-1", 4, listOf())
            val scenario2 = ScenarioSummary("scen-2", 17, listOf())
            every { scenarioRepository.getAll(any()) } returns listOf(scenario1, scenario2)
            val directives = mutableListOf<MinionsCreationPreparationDirective>()
            coEvery { directiveProducer.publish(capture(directives)) } answers {}
            val onCriticalFailure: (String) -> Unit = { _ -> println("Do something") }

            // when
            campaignManager.start(
                "my-campaign",
                scenarios = listOf("scen-1", "scen-2"),
                onCriticalFailure = onCriticalFailure
            )

            // then
            coVerifyOrder {
                scenarioRepository.getAll(eq(listOf("scen-1", "scen-2")))
                directiveProducer.publish(any())
                directiveProducer.publish(any())
            }
            assertThat(directives.sortedBy(MinionsCreationPreparationDirective::scenarioId)).all {
                hasSize(2)
                index(0).all {
                    prop(MinionsCreationPreparationDirective::campaignId).isEqualTo("my-campaign")
                    prop(MinionsCreationPreparationDirective::scenarioId).isEqualTo("scen-1")
                    prop(MinionsCreationPreparationDirective::value).isEqualTo(8)
                }
                index(1).all {
                    prop(MinionsCreationPreparationDirective::campaignId).isEqualTo("my-campaign")
                    prop(MinionsCreationPreparationDirective::scenarioId).isEqualTo("scen-2")
                    prop(MinionsCreationPreparationDirective::value).isEqualTo(34)
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
            10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
            directiveProducer = directiveProducer, coroutineScope = this
        )

        // when
        Assertions.assertTrue(campaignManager.accept(MinionsCreationDirectiveReference("", "", "", "")))
    }

    @Test
    @Timeout(3)
    internal fun `should not accept other directives`() = testCoroutineDispatcher.runTest {
        // given
        val campaignManager = DefaultCampaignManager(
            10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
            directiveProducer = directiveProducer, coroutineScope = this
        )

        // when
        Assertions.assertTrue(campaignManager.accept(MinionsCreationDirectiveReference("", "", "", "")))
    }

    @Test
    @Timeout(3)
    internal fun `should store the directives`() = testCoroutineDispatcher.runTest {
        // given
        val campaignManager = DefaultCampaignManager(
            10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
            directiveProducer = directiveProducer, coroutineScope = this
        )
        val directive = MinionsCreationDirectiveReference("directive", "", "", "")

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
            val directive = MinionsCreationPreparationDirective("directive", "", 123)
            val campaignManager = spyk(
                DefaultCampaignManager(
                    10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                    directiveProducer = directiveProducer, coroutineScope = this
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
            val directive = MinionsCreationPreparationDirective("directive", "", 123)
            val campaignManager = spyk(
                DefaultCampaignManager(
                    10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                    directiveProducer = directiveProducer, coroutineScope = this
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
                    10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                    directiveProducer = directiveProducer, coroutineScope = this
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
            val directive = MinionsCreationDirectiveReference("directive", "", "", "")
            val campaignManager = spyk(
                DefaultCampaignManager(
                    10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                    directiveProducer = directiveProducer, coroutineScope = this
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
            val directive = MinionsCreationDirectiveReference("directive", "", "", "")
            val campaignManager = spyk(
                DefaultCampaignManager(
                    10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                    directiveProducer = directiveProducer, coroutineScope = this
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
                    10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                    directiveProducer = directiveProducer, coroutineScope = this
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
            val directive = MinionsCreationPreparationDirective("directive", "", 123)
            val campaignManager = DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer, coroutineScope = this
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
            val directive = MinionsCreationDirectiveReference("directive", "", "", "")
            val campaignManager = DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer, coroutineScope = this
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
            10, speedFactor = 2.87, startOffsetMs = 876, feedbackConsumer = feedbackConsumer,
            scenarioRepository = scenarioRepository,
            directiveProducer = directiveProducer, coroutineScope = this
        )

        val directive1 = MinionsCreationDirectiveReference("directive", "camp-1", "scen-1", "dag-1")
        val directive2 = MinionsCreationDirectiveReference("directive", "camp-1", "scen-1", "dag-2")
        val directive3 = MinionsCreationDirectiveReference("directive", "camp-1", "scen-2", "dag-1")
        val directive4 = MinionsCreationDirectiveReference("directive", "camp-1", "scen-2", "dag-2")

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
            10, speedFactor = 2.87, startOffsetMs = 876, feedbackConsumer = feedbackConsumer,
            scenarioRepository = scenarioRepository,
            directiveProducer = directiveProducer, coroutineScope = this
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
        campaignManager.processFeedBack(feedback4)

        assertThat(publishedDirectives).all {
            hasSize(2)
            any {
                it.isInstanceOf(MinionsRampUpPreparationDirective::class)
                it.prop(MinionsRampUpPreparationDirective::campaignId).isEqualTo("camp-1")
                it.prop(MinionsRampUpPreparationDirective::scenarioId).isEqualTo("scen-1")
                it.prop(MinionsRampUpPreparationDirective::startOffsetMs).isEqualTo(876L)
                it.prop(MinionsRampUpPreparationDirective::speedFactor).isEqualTo(2.87)
            }
            any {
                it.isInstanceOf(MinionsRampUpPreparationDirective::class)
                it.prop(MinionsRampUpPreparationDirective::campaignId).isEqualTo("camp-1")
                it.prop(MinionsRampUpPreparationDirective::scenarioId).isEqualTo("scen-2")
                it.prop(MinionsRampUpPreparationDirective::startOffsetMs).isEqualTo(876L)
                it.prop(MinionsRampUpPreparationDirective::speedFactor).isEqualTo(2.87)
            }
        }

        val directivesInProgress =
            campaignManager.getProperty<Map<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                "directivesInProgress"
            )
        Assertions.assertEquals(2, directivesInProgress.size)
    }


}
