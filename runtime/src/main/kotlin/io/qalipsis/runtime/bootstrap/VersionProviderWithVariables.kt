package io.qalipsis.runtime.bootstrap

import picocli.CommandLine
import java.util.Properties

/**
 * Implementation of Picocli [CommandLine.IVersionProvider] to provide QALIPSIS version to the help documentation.
 *
 * @author Eric Jessé
 */
internal class VersionProviderWithVariables : CommandLine.IVersionProvider {

    override fun getVersion(): Array<String> {
        return arrayOf("QALIPSIS version ${executionProperties.getProperty("version")}")
    }

    private companion object {
        val executionProperties = Properties().apply {
            load(VersionProviderWithVariables::class.java.getResourceAsStream("/build.properties"))
        }
    }
}