package io.qalipsis.api.processors

import io.micronaut.context.ApplicationContext

/**
 *
 * @author Eric Jessé
 */
object ServicesLoader {

    /**
     * Load the services passing the application context as parameter.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> loadServices(name: String, context: ApplicationContext): Collection<T> {
        return this.javaClass.classLoader.getResources("META-INF/qalipsis/${name}")
            .toList()
            .flatMap { ServicesFiles.readFile(it.openStream()) }
            .map { loaderClass ->
                try {
                    Class.forName(loaderClass).getConstructor(ApplicationContext::class.java)
                        .newInstance(context) as T
                } catch (e: NoSuchMethodException) {
                    Class.forName(loaderClass).getConstructor().newInstance() as T
                }
            }
    }

    /**
     * Load the profiles defined in the plugins.
     */
    fun loadPlugins(): Collection<String> {
        return this.javaClass.classLoader.getResources("META-INF/qalipsis/plugin")
            .toList()
            .flatMap { ServicesFiles.readFile(it.openStream()) }
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }
}
