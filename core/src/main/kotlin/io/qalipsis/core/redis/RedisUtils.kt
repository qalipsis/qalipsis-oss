package io.qalipsis.core.redis

object RedisUtils {

    /**
     * Creates a convenient prefix for the Redis keys, considering the tenant.
     */
    @JvmStatic
    fun buildKeysPrefixForTenant(tenant: String): String {
        return if (tenant.isNotBlank()) {
            "$tenant:"
        } else {
            ""
        }
    }

    /**
     * Loads a LUA script from the classpath or returns an exception if it does not exist.
     */
    @JvmStatic
    fun loadScript(name: String): ByteArray {
        val resource =
            requireNotNull(this::class.java.getResourceAsStream(name)) { "Redis script $name cannot be found" }

        // Removes the blank lines and comments to optimize the compilation.
        return resource.bufferedReader(Charsets.UTF_8).readLines()
            .filterNot { it.isBlank() || it.trimStart().startsWith("--") }
            .joinToString("\n").encodeToByteArray()
    }
}