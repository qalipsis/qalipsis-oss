package io.qalipsis.core.head.redis.campaign

internal enum class CampaignRedisState {
    FACTORY_DAGS_ASSIGNMENT_STATE,
    MINIONS_ASSIGNMENT_STATE,
    WARMUP_STATE,
    MINIONS_STARTUP_STATE,
    RUNNING_STATE,
    COMPLETION_STATE,
    FAILURE_STATE,
    ABORTING_STATE
}