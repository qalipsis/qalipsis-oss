package io.qalipsis.core.head.persistence.entity

import java.time.Instant

/**
 * Entity which encapsulates all of the common properties for persistent storage
 *
 * @author rklymenko
 */
interface VersionedEntity : Entity {
    val version: Instant
}