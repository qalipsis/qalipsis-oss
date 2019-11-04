package io.evolue.api.kotlin.components

interface Request<out Response> {

    suspend fun execute(): Response
}