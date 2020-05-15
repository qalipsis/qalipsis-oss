package io.evolue.api.kotlin.components

interface PaceStrategy {

    suspend fun next()
}