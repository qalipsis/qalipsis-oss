package io.qalipsis.api.orchestration.feedbacks

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.qalipsis.core.directives.DirectiveKey
import io.qalipsis.core.feedbacks.DirectiveFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackJsonModule
import io.qalipsis.core.feedbacks.FeedbackStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class FeedbackKindTest {

    private lateinit var json: Json

    @BeforeEach
    fun setUp() {
        json = Json(builderAction = {
            serializersModule = FeedbackJsonModule.serializersModule
        })
    }

    @Test
    fun `should be able to serialize as base class`() {
        val feedback: Feedback = DirectiveFeedback(directiveKey = DirectiveKey(), status = FeedbackStatus.COMPLETED)
        val jsonString = json.encodeToString(feedback)
        val convertedFeedback = json.decodeFromString<Feedback>(jsonString)
        assertThat(convertedFeedback).all {
            prop(Feedback::key).isEqualTo(feedback.key)
        }
    }

    @Test
    fun `should be able to serialize as concrete class`() {
        val feedback = DirectiveFeedback(directiveKey = DirectiveKey(), status = FeedbackStatus.COMPLETED)
        val jsonString = json.encodeToString(feedback)
        val convertedFeedback = json.decodeFromString<DirectiveFeedback>(jsonString)
        assertThat(convertedFeedback).all {
            prop(DirectiveFeedback::key).isEqualTo(feedback.key)
            prop(DirectiveFeedback::error).isEqualTo(feedback.error)
            prop(DirectiveFeedback::status).isEqualTo(FeedbackStatus.COMPLETED)
        }
    }


}