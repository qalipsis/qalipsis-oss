package io.qalipsis.core.head.utils

/**
 * @author Joël Valère
 */
object SqlFilterUtils {

    /**
     * Convert the search filters from a user-friendly to the SQL syntax.
     */
    fun Collection<String>.formatsFilters() =
        this.map { it.replace('*', '%').replace('?', '_') }.map { "%${it.trim()}%" }
}