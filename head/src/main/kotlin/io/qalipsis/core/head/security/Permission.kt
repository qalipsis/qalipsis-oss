package io.qalipsis.core.head.security

/**
 * Security permission granted to a [User] or [RoleName].
 */
@JvmInline
internal value class Permission(val name: String)

/**
 * All Permission is a [Permission] that allows everything for the user that owns it.
 */
internal val ALL_PERMISSION = Permission("*")
