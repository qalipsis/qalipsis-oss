package io.evolue.api.kotlin.components

interface SessionHolder {

    suspend fun open()

    suspend fun maintain()

    suspend fun close()
}