package io.qalipsis.core.head.admin

import java.time.Instant

data class SaveTenantDto (
    val displayName: String
        )

class SaveTenantResponse (
    val displayName: String,
    val reference: String,
    val version: Instant?
        )
