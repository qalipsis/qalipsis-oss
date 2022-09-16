package io.qalipsis.core.configuration

import kotlinx.serialization.Serializable

@Serializable
data class AbortRunningCampaign(
    val hard: Boolean = true
)