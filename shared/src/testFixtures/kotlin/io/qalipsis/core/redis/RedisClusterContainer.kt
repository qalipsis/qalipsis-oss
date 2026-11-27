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

package io.qalipsis.core.redis

import io.lettuce.core.RedisURI
import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.SlotHash
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration

/**
 * Redis Cluster of [MASTERS_COUNT] masters without replica, running in a single container.
 *
 * The nodes announce 127.0.0.1 and are bound to the same ports on the host, so that the cluster topology seen by
 * the clients is reachable from the host.
 */
class RedisClusterContainer(image: String = IMAGE) : FixedHostPortGenericContainer<RedisClusterContainer>(image) {

    init {
        PORTS.forEach { withFixedExposedPort(it, it) }
        withCommand("sh", "-c", STARTUP_SCRIPT)
        waitingFor(Wait.forLogMessage(".*$READY_MESSAGE.*\\n", 1))
        withStartupTimeout(Duration.ofSeconds(90))
    }

    val uris: List<RedisURI>
        get() = PORTS.map { RedisURI.create("redis://127.0.0.1:$it") }

    /**
     * Creates a client of the cluster, refreshing its topology until all the slots are known by the client.
     */
    fun createClient(): RedisClusterClient = RedisClusterClient.create(uris).apply {
        setOptions(
            ClusterClientOptions.builder()
                .topologyRefreshOptions(ClusterTopologyRefreshOptions.builder().enableAllAdaptiveRefreshTriggers().build())
                .build()
        )
        val deadline = System.currentTimeMillis() + TOPOLOGY_TIMEOUT_MS
        while ((0 until SlotHash.SLOT_COUNT).any { partitions.getPartitionBySlot(it) == null }) {
            check(System.currentTimeMillis() < deadline) { "The topology of the Redis cluster is incomplete" }
            Thread.sleep(TOPOLOGY_POLL_MS)
            refreshPartitions()
        }
    }

    companion object {

        const val IMAGE = "redis:7-alpine"

        const val MASTERS_COUNT = 3

        private const val TOPOLOGY_TIMEOUT_MS = 30_000L

        private const val TOPOLOGY_POLL_MS = 200L

        private const val FIRST_PORT = 7100

        val PORTS = (FIRST_PORT until FIRST_PORT + MASTERS_COUNT).toList()

        private const val READY_MESSAGE = "QALIPSIS REDIS CLUSTER READY"

        private val STARTUP_SCRIPT = """
            for port in ${PORTS.joinToString(" ")}; do
              redis-server --port ${'$'}port --cluster-enabled yes --cluster-config-file nodes-${'$'}port.conf \
                --cluster-node-timeout 5000 --appendonly no --save '' --daemonize yes
            done
            sleep 1
            redis-cli --cluster create ${PORTS.joinToString(" ") { "127.0.0.1:$it" }} --cluster-replicas 0 --cluster-yes
            for port in ${PORTS.joinToString(" ")}; do
              until redis-cli -p ${'$'}port cluster info | grep -q 'cluster_state:ok' \
                && redis-cli -p ${'$'}port cluster info | grep -q 'cluster_slots_assigned:16384' \
                && [ "${'$'}(redis-cli -p ${'$'}port cluster nodes | grep -c master)" = "$MASTERS_COUNT" ]; do sleep 0.5; done
            done
            echo "$READY_MESSAGE"
            tail -f /dev/null
        """.trimIndent()
    }
}
