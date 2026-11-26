/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.factory.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisNoScriptException
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.coroutines.RedisScriptingCoroutinesCommands
import io.qalipsis.core.redis.RedisUtils

/**
 * Lua script loaded from the resource [path], executed by its SHA and loaded again when unknown to Redis, for
 * example after a restart of Redis or on a new node of a cluster.
 *
 * @author Eric Jessé
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
internal class RedisScript(
    private val scriptingCommands: RedisScriptingCoroutinesCommands<String, String>,
    path: String,
) {

    /**
     * Source of the script.
     */
    private val source = RedisUtils.loadScript(path)

    /**
     * SHA of the script once loaded in Redis.
     */
    @Volatile
    private var sha: String? = null

    /**
     * Executes the script on [keys] with [arguments], and returns its result as a list.
     */
    suspend fun execute(keys: List<String>, vararg arguments: String): List<Any?> = try {
        evaluate(sha ?: load(), keys, arguments)
    } catch (e: RedisNoScriptException) {
        evaluate(load(), keys, arguments)
    }

    /**
     * Loads the script into Redis and keeps its SHA.
     */
    private suspend fun load() = scriptingCommands.scriptLoad(source)!!.also { sha = it }

    /**
     * Executes the script identified by [digest].
     */
    private suspend fun evaluate(digest: String, keys: List<String>, arguments: Array<out String>): List<Any?> =
        scriptingCommands.evalsha<List<Any?>>(digest, ScriptOutputType.MULTI, keys.toTypedArray(), *arguments)!!
}
