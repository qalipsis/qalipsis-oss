package io.qalipsis.api.serialization

import kotlinx.serialization.Serializable

/**
 * Class used in the api-processors module for testing of the serialization.
 *
 * @author Eric Jessé
 */
@Serializable
data class SerializablePerson(val name: String, val age: Int)
