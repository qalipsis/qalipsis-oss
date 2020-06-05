package io.evolue.core.head.campaign

import io.evolue.core.cross.driving.feedback.FactoryRegistrationFeedback
import io.evolue.core.cross.driving.feedback.FactoryRegistrationFeedbackScenario
import io.evolue.core.cross.driving.feedback.Feedback
import io.evolue.core.cross.driving.feedback.FeedbackConsumer
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.mockk.verifyNever
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.spyk
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CountDownLatch

/**
 * @author Eric Jessé
 */
@ExtendWith(MockKExtension::class)
internal class InMemoryScenariosRepositoryTest {

    @RelaxedMockK
    lateinit var feedbackConsumer: FeedbackConsumer

    @Test
    @Timeout(1)
    internal fun `should save scenarios when factory registers`() {
        // given
        val countDown = CountDownLatch(1)
        val scenarios: List<FactoryRegistrationFeedbackScenario> = listOf(
            relaxedMockk {
                every { id } returns "scen-1"
            },
            relaxedMockk {
                every { id } returns "scen-2"
            }
        )
        coEvery { feedbackConsumer.subscribe() } returns flowOf(FactoryRegistrationFeedback(scenarios)).also {
            countDown.countDown()
        }

        // when
        val inMemoryScenariosRepository = InMemoryScenariosRepository(feedbackConsumer)

        // then
        countDown.await()
        inMemoryScenariosRepository.getAll().containsAll(scenarios)

    }

    @Test
    @Timeout(1)
    internal fun `should not save scenarios when other feedback is received`() {
        // given
        val countDown = CountDownLatch(1)
        coEvery { feedbackConsumer.subscribe() } returns flowOf(relaxedMockk<Feedback>()).also {
            countDown.countDown()
        }

        // when
        val inMemoryScenariosRepository = spyk(InMemoryScenariosRepository(feedbackConsumer))

        // then
        countDown.await()
        verifyNever {
            inMemoryScenariosRepository.saveAll(any())
        }
    }
}
