package io.qalipsis.core.head.security.entity

enum class RoleName(name: String) {
    SUPER_ADMINISTRATOR("super-admin"),
    BILLING_ADMINISTRATOR("billing-admin"),
    TENANT_ADMINISTRATOR("tenant-admin"),
    TESTER("tester"),
    REPORTER("reporter")
}