package io.evolue.api.kotlin.components

interface RampUpStrategy {

    suspend fun next()
}