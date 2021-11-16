package io.qalipsis.api.coroutines

import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface DispatcherProvider {

    fun default(): CoroutineDispatcher = Dispatchers.Default

    fun io(): CoroutineDispatcher = Dispatchers.IO

    fun unconfined(): CoroutineDispatcher = Dispatchers.Unconfined

}

@Singleton
class DefaultDispatcherProvider : DispatcherProvider