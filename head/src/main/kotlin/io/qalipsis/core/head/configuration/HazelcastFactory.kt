/*
 * QALIPSIS
 * Copyright (C) 2024 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.configuration

import com.hazelcast.config.Config
import com.hazelcast.config.YamlConfigBuilder
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.HazelcastInstance
import io.lettuce.core.Range
import io.lettuce.core.Range.Boundary
import io.lettuce.core.api.async.RedisSortedSetAsyncCommands
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.configuration.HazelcastConfiguration.DiscoveryStrategy.AUTO
import io.qalipsis.core.head.configuration.HazelcastConfiguration.DiscoveryStrategy.KUBERNETES
import io.qalipsis.core.head.configuration.HazelcastConfiguration.DiscoveryStrategy.MULTICAST
import io.qalipsis.core.head.configuration.HazelcastConfiguration.DiscoveryStrategy.TCP_IP_REDIS
import jakarta.annotation.Nullable
import jakarta.inject.Named
import java.net.InetAddress
import java.time.Duration
import java.time.Instant

@Factory
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD]),
    Requires(notEnv = [ExecutionEnvironments.SINGLE_HEAD])
)
internal class HazelcastFactory(
    @Nullable val redisSetAsyncCommands: RedisSortedSetAsyncCommands<String, String>?,
    @Named(TaskExecutors.SCHEDULED) val taskScheduler: TaskScheduler,
) {

    @Bean(preDestroy = "shutdown")
    fun buildHazelcastInstance(configuration: HazelcastConfiguration): HazelcastInstance {
        val config = YamlConfigBuilder(this::class.java.classLoader.getResourceAsStream("hazelcast-qalipsis.yml"))
            .build().also { hc ->
                hc.clusterName = "qalipsis"
                configureDefaultScheduler(hc, configuration)

                configuration.port?.let { hc.networkConfig.setPort(it) }
                // Disables the auto-detection of the discovery configuration.
                hc.networkConfig.join.autoDetectionConfig.setEnabled(false)
                when (configuration.discoveryStrategy) {
                    MULTICAST -> buildClusterUsingMulticast(configuration, hc)
                    KUBERNETES -> buildClusterUsingKubernetes(configuration, hc)
                    TCP_IP_REDIS -> {
                        requireNotNull(redisSetAsyncCommands) { "A Redis connection is required to create the cluster members registry" }
                        buildClusterUsingTcpIp(configuration, hc)
                    }

                    AUTO -> hc.networkConfig.join.autoDetectionConfig.setEnabled(true)

                    else -> {
                        // No cluster to build, for dev only.
                    }
                }

            }

        return Hazelcast.newHazelcastInstance(config)
    }

    /**
     * Configures the default scheduler.
     */
    private fun configureDefaultScheduler(
        hc: Config,
        configuration: HazelcastConfiguration
    ) {
        hc.getScheduledExecutorConfig("default")
            .setStatisticsEnabled(configuration.scheduledExecutorService.statisticsEnabled)
            .setPoolSize(configuration.scheduledExecutorService.poolSize)
            .setDurability(configuration.scheduledExecutorService.durability)
            .setCapacity(configuration.scheduledExecutorService.capacity)
            .setCapacityPolicy(configuration.scheduledExecutorService.capacityPolicy)
            .also { executorConfig ->
                configuration.scheduledExecutorService.mergePolicy?.let {
                    executorConfig.mergePolicyConfig.setPolicy(it.policy).setBatchSize(it.batchSize)
                }
            }
    }

    /**
     * Builds a Hazelcast cluster using members addresses stored in Redis.
     * This requires a Redis connection.
     */
    private fun buildClusterUsingTcpIp(
        configuration: HazelcastConfiguration,
        hc: Config,
    ) {
        val tcpIpConfiguration =
            requireNotNull(configuration.tcpIp) { "The configuration for TCP IP is required with prefix `hazelcast.tcp-ip`" }
        // List only the members that are recently active.
        val minScore = Instant.now().minus(tcpIpConfiguration.timeout).epochSecond
        val members = redisSetAsyncCommands!!.zrangebyscore(
            CLUSTER_REGISTRY,
            Range.from(Boundary.including(minScore), Boundary.unbounded())
        ).get()
        val localAddress = InetAddress.getLocalHost().hostAddress

        redisSetAsyncCommands.zadd(
            CLUSTER_REGISTRY,
            Instant.now().epochSecond.toDouble(),
            "${localAddress}:${hc.networkConfig.port}",
        )

        taskScheduler.scheduleAtFixedRate(Duration.ZERO, tcpIpConfiguration.timeout.dividedBy(2)) {
            // On a regular basis, sends a heart beat to update the score of the local cluster.
            val now = Instant.now()
            redisSetAsyncCommands.zadd(
                CLUSTER_REGISTRY,
                now.epochSecond.toDouble(),
                "${localAddress}:${hc.networkConfig.port}",
            )
            // And cleans the members that did not send a heartbeat recently.
            val timeout = now - tcpIpConfiguration.timeout
            redisSetAsyncCommands.zremrangebyscore(
                CLUSTER_REGISTRY,
                Range.from(Boundary.unbounded(), Boundary.excluding(timeout.epochSecond))
            )
        }

        hc.networkConfig.join.tcpIpConfig.setEnabled(true)
        tcpIpConfiguration.connectionTimeout?.let { connectionTimeout ->
            hc.networkConfig.join.tcpIpConfig.setConnectionTimeoutSeconds(connectionTimeout.toSeconds().toInt())
        }
        if (members.isNotEmpty()) {
            log.debug { "Connecting to the Hazelcast cluster with members: $members" }
            hc.networkConfig.join.tcpIpConfig.setMembers(members.toList())
        }
    }

    /**
     * Builds a Hazelcast cluster using multicast.
     */
    private fun buildClusterUsingMulticast(
        configuration: HazelcastConfiguration,
        hc: Config,
    ) {
        val multicastConfig = hc.networkConfig.join.multicastConfig
        multicastConfig.setEnabled(true)
        configuration.multicast?.apply {
            group?.let { group ->
                multicastConfig.setMulticastGroup(group)
            }
            port?.let { port ->
                multicastConfig.setMulticastPort(port)
            }
            timeToLive?.let { timeToLive ->
                multicastConfig.setMulticastTimeToLive(timeToLive.toSeconds().toInt())
            }
            timeout?.let { timeout ->
                multicastConfig.setMulticastTimeoutSeconds(timeout.toSeconds().toInt())
            }
            trustedInterfaces?.forEach { trustedInterface ->
                multicastConfig.addTrustedInterface(trustedInterface)
            }
        }

    }

    /**
     * Builds a Hazelcast cluster using multicast.
     */
    private fun buildClusterUsingKubernetes(
        configuration: HazelcastConfiguration,
        hc: Config,
    ) {
        val kubernetesConfig = hc.networkConfig.join.kubernetesConfig
        kubernetesConfig.setEnabled(true)
        configuration.kubernetes?.apply {
            namespace?.let { namespace ->
                kubernetesConfig.setProperty("namespace", namespace)
            }
            serviceName?.let { serviceName ->
                kubernetesConfig.setProperty("service-name", serviceName)
            }
            serviceLabelName?.let { serviceLabelName ->
                kubernetesConfig.setProperty("service-label-name", serviceLabelName)
            }
            serviceLabelValue?.let { serviceLabelValue ->
                kubernetesConfig.setProperty("service-label-value", serviceLabelValue)
            }
            podLabelName?.let { podLabelName ->
                kubernetesConfig.setProperty("pod-label-name", podLabelName)
            }
            podLabelValue?.let { podLabelValue ->
                kubernetesConfig.setProperty("pod-label-value", podLabelValue)
            }
            kubernetesApiRetries?.let { kubernetesApiRetries ->
                kubernetesConfig.setProperty("kubernetes-api-retries", kubernetesApiRetries.toString())
            }
            kubernetesMaster?.let { kubernetesMaster ->
                kubernetesConfig.setProperty("kubernetes-master", kubernetesMaster)
            }
            apiToken?.let { apiToken ->
                kubernetesConfig.setProperty("api-token", apiToken)
            }
            caCertificate?.let { caCertificate ->
                kubernetesConfig.setProperty("ca-certificate", caCertificate)
            }
            servicePort?.let { servicePort ->
                kubernetesConfig.setProperty("service-port", servicePort.toString())
            }
            serviceDns?.let { serviceDns ->
                kubernetesConfig.setProperty("service-dns", serviceDns)
            }
            serviceDnsTimeout?.let { serviceDnsTimeout ->
                kubernetesConfig.setProperty("service-dns-timeout", serviceDnsTimeout.toSeconds().toInt().toString())
            }
            groupType?.let { groupType ->
                hc.partitionGroupConfig.setEnabled(true).setGroupType(groupType)
            }
        }
    }

    private companion object {
        val log = logger()

        const val CLUSTER_REGISTRY = "hazelcast-cluster"
    }

}