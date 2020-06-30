package io.evolue.api.processors

import io.micronaut.context.ApplicationContext

/**
 *
 * @author Eric Jessé
 */
object ServicesLoader {

    /**
     * Load the services.
     */
    fun <T> loadServices(name: String): Collection<T> {
        return this.javaClass.classLoader.getResources("META-INF/evolue/${name}")
            .toList()
            .flatMap { ServicesFiles.readFile(it.openStream()) }
            .map { loaderClass ->
                Class.forName(loaderClass).getConstructor().newInstance() as T
            }
    }

    /**
     * Load the services passing the application context as parameter.
     */
    fun <T> loadServices(name: String, context: ApplicationContext): Collection<T> {
        return this.javaClass.classLoader.getResources("META-INF/evolue/${name}")
            .toList()
            .flatMap { ServicesFiles.readFile(it.openStream()) }
            .map { loaderClass ->
                try {
                    Class.forName(loaderClass).getConstructor(ApplicationContext::class.java)
                        .newInstance(context) as T
                } catch (e: Exception) {
                    Class.forName(loaderClass).getConstructor().newInstance() as T
                }
            }
    }
}
