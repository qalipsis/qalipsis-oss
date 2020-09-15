package io.evolue.plugins.jackson.config

import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Configuration describing the resource to read to receive the data.
 *
 * @author Eric Jessé
 */
internal data class SourceConfiguration(
        var url: URL? = null,
        var encoding: Charset = StandardCharsets.UTF_8
)