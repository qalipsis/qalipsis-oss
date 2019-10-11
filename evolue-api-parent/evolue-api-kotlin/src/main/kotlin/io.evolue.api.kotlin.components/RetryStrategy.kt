package components

interface RetryStrategy {

    fun canRetry(): Boolean
}