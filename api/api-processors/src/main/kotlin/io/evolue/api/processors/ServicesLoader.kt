package io.evolue.api.processors

/**
 *
 * @author Eric Jessé
 */
object ServicesLoader {

    /**
     * Load the services.
     */
    fun <T> loadServices(name: String, vararg args: Any?): Collection<T> {
        return this.javaClass.classLoader.getResources("META-INF/evolue/${name}")
            .toList()
            .flatMap { ServicesFiles.readFile(it.openStream()) }
            .map { loaderClass ->
                Class.forName(loaderClass).getConstructor().newInstance() as T
            }
    }
}