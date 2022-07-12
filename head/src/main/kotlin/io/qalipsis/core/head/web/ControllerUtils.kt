package io.qalipsis.core.head.web

internal object ControllerUtils {

    fun String.asFilters() = this.split(',').mapNotNull { it.trim().takeUnless(String::isNullOrBlank) }
}