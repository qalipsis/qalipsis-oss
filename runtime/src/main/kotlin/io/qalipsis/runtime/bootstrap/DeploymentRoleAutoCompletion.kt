package io.qalipsis.runtime.bootstrap

/**
 * Helper class to support autocompletion for the roles in the Picocli command-line.
 *
 * @author Eric Jessé
 */
internal class DeploymentRoleAutoCompletion : Iterable<String> {

    private val values = DeploymentRole.values().map { "$it".lowercase() }

    override fun iterator(): Iterator<String> = values.iterator()

}