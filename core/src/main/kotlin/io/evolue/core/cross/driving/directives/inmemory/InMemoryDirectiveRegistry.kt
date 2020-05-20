package io.evolue.core.cross.driving.directives.inmemory

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import io.evolue.core.cross.driving.directives.Directive
import io.evolue.core.cross.driving.directives.DirectiveKey
import io.evolue.core.cross.driving.directives.DirectiveRegistry
import io.evolue.core.cross.driving.directives.ListDirective
import io.evolue.core.cross.driving.directives.ListDirectiveReference
import io.evolue.core.cross.driving.directives.QueueDirective
import io.evolue.core.cross.driving.directives.QueueDirectiveReference
import io.evolue.core.cross.driving.directives.SingleUseDirective
import io.evolue.core.cross.driving.directives.SingleUseDirectiveReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Implementation of [DirectiveRegistry] hosting the [Directive]s into memory, used for deployments
 * where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
internal class InMemoryDirectiveRegistry : DirectiveRegistry {

    /**
     * Cache of real [QueueDirective]s accessible by the keys of their references.
     */
    private val queueDirectives: Cache<DirectiveKey, QueueDirective<*, *>> =
        Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build()

    /**
     * Cache of real [ListDirective]s accessible by the keys of their references.
     */
    private val listDirectives: Cache<DirectiveKey, ListDirective<*, *>> =
        Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build()

    /**
     * Cache of real [SingleUseDirective]s accessible by the keys of their references.
     */
    private val singleUseDirectives: Cache<DirectiveKey, SingleUseDirective<*, *>> =
        Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build()

    /**
     * Map of mutexes to access the synchronized directives. There are evicted quite fast to avoid useless memory usage.
     */
    private val mutexesCache: LoadingCache<DirectiveKey, Mutex> =
        Caffeine.newBuilder().expireAfterAccess(10, TimeUnit.SECONDS).build { key -> Mutex(false) }

    override suspend fun save(key: DirectiveKey, directive: Directive) {
        when (directive) {
            is QueueDirective<*, *> -> {
                queueDirectives.put(key, directive)
            }
            is ListDirective<*, *> -> {
                listDirectives.put(key, directive)
            }
            is SingleUseDirective<*, *> -> {
                singleUseDirectives.put(key, directive)
            }
        }
    }

    override suspend fun <T> pop(reference: QueueDirectiveReference<T>): T? {
        return mutexesCache[reference.key]!!.withLock {
            queueDirectives.getIfPresent(reference.key)?.queue?.let {
                if (it.size == 1) {
                    queueDirectives.invalidate(reference.key)
                }
                it.removeFirst() as T?
            }
        }
    }

    override suspend fun <T> list(reference: ListDirectiveReference<T>): List<T> {
        return listDirectives.getIfPresent(reference.key)?.set as List<T>? ?: emptyList()
    }

    override suspend fun <T> read(reference: SingleUseDirectiveReference<T>): T? {
        return mutexesCache[reference.key]!!.withLock {
            singleUseDirectives.getIfPresent(reference.key)?.let {
                singleUseDirectives.invalidate(reference.key)
                it.value as T?
            }
        }
    }

}