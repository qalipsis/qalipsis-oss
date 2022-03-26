package io.qalipsis.runtime.bootstrap

/**
 * Roles for deployment of a process of QALIPSIS.
 *
 * @property STANDALONE runs all the components in a single JVM
 * @property HEAD only starts the head components
 * @property FACTORY only starts the factory components
 *
 * @author Eric Jessé
 */
enum class DeploymentRole {
    STANDALONE, // Runs head and factory side by side in the same JVM.
    HEAD,
    FACTORY
}