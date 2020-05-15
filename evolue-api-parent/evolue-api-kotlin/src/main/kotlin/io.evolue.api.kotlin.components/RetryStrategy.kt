package io.evolue.api.kotlin.components

interface RetryStrategy {

    suspend fun canRetry(): Boolean
}