package io.qalipsis.core.feedbacks

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

object FeedbackJsonModule {

    val serializersModule = SerializersModule {
        polymorphic(Feedback::class) {
            subclass(DirectiveFeedback::class)
        }
    }
}

