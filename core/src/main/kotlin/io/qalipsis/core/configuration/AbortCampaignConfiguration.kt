package io.qalipsis.core.configuration

import kotlinx.serialization.Serializable

@Serializable
data class AbortCampaignConfiguration(
    val hard: Boolean = true
)