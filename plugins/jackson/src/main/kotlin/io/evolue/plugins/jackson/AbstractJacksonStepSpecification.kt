package io.evolue.plugins.jackson

import io.evolue.api.steps.AbstractStepSpecification
import io.evolue.api.steps.BroadcastSpecification
import io.evolue.api.steps.LoopableSpecification
import io.evolue.api.steps.SingletonConfiguration
import io.evolue.api.steps.SingletonStepSpecification
import io.evolue.api.steps.SingletonType
import io.evolue.api.steps.UnicastSpecification
import io.evolue.api.steps.datasource.DatasourceRecord
import io.evolue.plugins.jackson.config.SourceConfiguration
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Path
import java.time.Duration

/**
 *
 * @author Eric Jessé
 */
abstract class AbstractJacksonStepSpecification<O : Any?, SELF : AbstractJacksonStepSpecification<O, SELF>> :
    AbstractStepSpecification<Unit, DatasourceRecord<O>, SELF>(),
    JacksonNamespaceStepSpecification<Unit, DatasourceRecord<O>, SELF>,
    SingletonStepSpecification<Unit, DatasourceRecord<O>, SELF>,
    LoopableSpecification, UnicastSpecification, BroadcastSpecification {

    internal val sourceConfiguration = SourceConfiguration()

    override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.BROADCAST)

    override fun loop(idleTimeout: Duration) {
        singletonConfiguration.type = SingletonType.LOOP
        singletonConfiguration.idleTimeout = idleTimeout
        singletonConfiguration.bufferSize = -1
    }

    override fun forwardOnce(bufferSize: Int, idleTimeout: Duration) {
        singletonConfiguration.type = SingletonType.UNICAST
        singletonConfiguration.bufferSize = bufferSize
        singletonConfiguration.idleTimeout = idleTimeout
    }

    override fun broadcast(bufferSize: Int, idleTimeout: Duration) {
        singletonConfiguration.type = SingletonType.BROADCAST
        singletonConfiguration.bufferSize = bufferSize
        singletonConfiguration.idleTimeout = idleTimeout
    }

    /**
     * Reads the CSV data from a plain file on the file system.
     *
     * @param path the path to the file, either absolute or relative to the working directory.
     */
    fun file(path: String): SELF {
        sourceConfiguration.url = Path.of(path).toUri().toURL()
        return this as SELF
    }

    /**
     * Reads the CSV data from a class path resource.
     *
     * @param path the path to the resource, the leading slash is ignored.
     */
    fun classpath(path: String): SELF {
        sourceConfiguration.url =
            this::class.java.classLoader.getResource(if (path.startsWith("/")) path.substring(1) else path)
        return this as SELF
    }

    /**
     * Reads the CSV data from the a URL.
     *
     * @param url the url to access to the CSV resource.
     */
    fun url(url: String): SELF {
        sourceConfiguration.url = URL(url)
        return this as SELF
    }

    /**
     * Sets the charset of the source file. Default is UTF-8.
     *
     * @param encoding the name of the charset
     */
    fun encoding(encoding: String): SELF {
        sourceConfiguration.encoding = Charset.forName(encoding)
        return this as SELF
    }

    /**
     * Sets the charset of the source file. Default is UTF-8.
     *
     * @param encoding the charset to use
     */
    fun encoding(encoding: Charset): SELF {
        sourceConfiguration.encoding = encoding
        return this as SELF
    }
}
