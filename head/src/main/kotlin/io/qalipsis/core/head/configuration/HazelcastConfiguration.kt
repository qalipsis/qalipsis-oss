/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

import com.hazelcast.config.PartitionGroupConfig.MemberGroupType
import com.hazelcast.config.ScheduledExecutorConfig
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.annotation.Nullable
import java.time.Duration
import javax.validation.Valid
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive

/**
 * Configuration for the Hazelcast services.
 */
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD]),
    Requires(notEnv = [ExecutionEnvironments.SINGLE_HEAD])
)
@ConfigurationProperties("hazelcast")
internal interface HazelcastConfiguration {

    @get:Min(2000)
    val port: Int?

    @get:Bindable(defaultValue = "TCP_IP_REDIS")
    val discoveryStrategy: DiscoveryStrategy

    @get:Valid
    @get:Nullable
    val multicast: MulticastDiscovery?

    @get:Valid
    @get:Nullable
    val tcpIp: TcpIpDiscovery?

    @get:Valid
    @get:Nullable
    val kubernetes: KubernetesDiscovery?

    @get:Valid
    val scheduledExecutorService: ScheduledExecutorService

    /**
     * Configuration to create a Hazelcast cluster within Kubernetes.
     *
     * You can use one of service-name,service-label (service-label-name, service-label-value) and
     * pod-label (pod-label-name, pod-label-value) based discovery mechanisms,
     * configuring two of them at once does not make sense.
     *
     * Documentation on [https://github.com/hazelcast/hazelcast-kubernetes/tree/v2.2.3](https://github.com/hazelcast/hazelcast-kubernetes/tree/v2.2.3).
     */
    @ConfigurationProperties("kubernetes")
    interface KubernetesDiscovery {

        /**
         * Kubernetes Namespace where Hazelcast is running; if not specified, the value is taken from the environment
         * variables KUBERNETES_NAMESPACE or OPENSHIFT_BUILD_NAMESPACE. If those are not set,
         * the namespace of the POD will be used (retrieved from /var/run/secrets/kubernetes.io/serviceaccount/namespace).
         */
        @get:Nullable
        val namespace: String?

        /**
         * Service name used to scan only PODs connected to the given service;
         * if not specified, then all PODs in the namespace are checked
         */
        @get:Nullable
        val serviceName: String?

        /**
         * Service label used to tag services that should form the Hazelcast cluster together.
         */
        @get:Nullable
        val serviceLabelName: String?

        /**
         * Service label value used to tag services that should form the Hazelcast cluster together.
         */
        @get:Nullable
        val serviceLabelValue: String?

        /**
         * Pod label used to tag services that should form the Hazelcast cluster together.
         */
        @get:Nullable
        val podLabelName: String?

        /**
         * Pod label value used to tag services that should form the Hazelcast cluster together.
         */
        @get:Nullable
        val podLabelValue: String?

        /**
         * Number of retries in case of issues while connecting to Kubernetes API; defaults to 3.
         */
        @get:Nullable
        @get:Positive
        val kubernetesApiRetries: Int?

        /**
         * URL of Kubernetes Master; https://kubernetes.default.svc by default.
         */
        @get:Nullable
        val kubernetesMaster: String?

        /**
         * API Token to Kubernetes API; if not specified, the value is taken from the file
         * /var/run/secrets/kubernetes.io/serviceaccount/token.
         */
        @get:Nullable
        val apiToken: String?

        /**
         * CA Certificate for Kubernetes API; if not specified, the value is taken from the file
         * /var/run/secrets/kubernetes.io/serviceaccount/ca.crt.
         */
        @get:Nullable
        val caCertificate: String?

        /**
         * Endpoint port of the service; if specified with a value greater than 0, it overrides the default;
         * 0 by default.
         */
        @get:Nullable
        @get:Positive
        val servicePort: Int?

        /**
         * When using DNS Lookup: service DNS (reduired), usually in the form of SERVICE-NAME.NAMESPACE.svc.cluster.local.
         */
        @get:Nullable
        val serviceDns: String?

        /**
         * When using DNS Lookup: custom time (optional) for how long the DNS Lookup is checked.
         */
        @get:Nullable
        @get:PositiveDuration
        val serviceDnsTimeout: Duration?

        /**
         * Strategy to use to ensure high-availability.
         *
         * When using ZONE_AWARE configuration, backups are created in the other availability zone. This feature is available only for the Kubernetes API mode.
         * Note: Your Kubernetes cluster must orchestrate Hazelcast Member PODs equally between the availability zones, otherwise Zone Aware feature may not work correctly.
         *
         * When using NODE_AWARE configuration, backups are created in the other Kubernetes nodes. This feature is available only for the Kubernetes API mode.
         * Note: Your Kubernetes cluster must orchestrate Hazelcast Member PODs equally between the nodes, otherwise Node Aware feature may not work correctly.
         */
        @get:Nullable
        val groupType: MemberGroupType?

    }

    /**
     * Configuration to create a Hazelcast cluster by IP addresses and host names, stored in the Redis DB.
     */
    @ConfigurationProperties("tcp-ip")
    interface TcpIpDiscovery {

        /**
         * Defines the connection timeout in seconds. This is the maximum amount of time Hazelcast is going to try to
         * connect to a well known member before giving up. Setting it to a too low value could mean that a
         * member is not able to connect to a cluster.
         * Setting it to a too high value means that member startup could slow down because of longer timeouts,
         * for example when a well known member is not up. Increasing this value is recommended if you have many
         * IPs listed and the members cannot properly build up the cluster. Its default value is 5 seconds.
         */
        @get:PositiveDuration
        @get:Nullable
        val connectionTimeout: Duration?

        /**
         * Timeout after which a member that did not send any heartbeat is ignored by new members joining the cluster.
         */
        @get:PositiveDuration
        @get:Bindable(defaultValue = "PT1M")
        val timeout: Duration

    }

    @ConfigurationProperties("multicast")
    interface MulticastDiscovery {

        /**
         * The multicast group IP address. Specify it when you want to create clusters within the same network.
         * Values can be between 224.0.0.0 and 239.255.255.255. Its default value is 224.2.2.3
         */
        @get:Nullable
        val group: String?

        /**
         * The multicast socket port that the Hazelcast member listens to and sends discovery messages through.
         * Its default value is 54327.
         */
        @get:Nullable
        @get:Min(2000)
        val port: Int?

        /**
         * Time-to-live value for multicast packets sent out to control the scope of multicasts. See more information,
         * see The [http://www.tldp.org/HOWTO/Multicast-HOWTO-2.html](Linux Documentation Project).
         */
        @get:PositiveDuration
        val timeToLive: Duration?

        /**
         * Only when the members are starting up, this timeout (in seconds) specifies the period during which a member
         * waits for a multicast response from another member. For example, if you set it as 60 seconds,
         * each member waits for 60 seconds until a leader member is selected. Its default value is 2 seconds.
         */
        @get:Nullable
        @get:PositiveDuration
        val timeout: Duration?

        /**
         * Includes IP addresses of trusted members. When a member wants to join to the cluster, its join request
         * is rejected if it is not a trusted member.
         * You can give an IP addresses range using the wildcard (*) on the last digit of IP address,
         * e.g., 192.168.1.* or 192.168.1.100-110.
         */
        @get:Nullable
        @get:Bindable(defaultValue = "")
        val trustedInterfaces: Set<@NotBlank String>?

    }

    @ConfigurationProperties("scheduled-executor-service")
    interface ScheduledExecutorService {

        @get:Bindable(defaultValue = "false")
        val statisticsEnabled: Boolean

        @get:Min(1)
        @get:Bindable(defaultValue = "16")
        val poolSize: Int

        @get:Min(1)
        @get:Bindable(defaultValue = "1")
        val durability: Int

        @get:Min(1)
        @get:Bindable(defaultValue = "100")
        val capacity: Int

        @get:Bindable(defaultValue = "PER_NODE")
        val capacityPolicy: ScheduledExecutorConfig.CapacityPolicy

        @get:Nullable
        val mergePolicy: MergePolicyConfig?

        @ConfigurationProperties("merge-policy")
        interface MergePolicyConfig {

            @get:NotBlank
            @get:Bindable(defaultValue = "PutIfAbsentMergePolicy")
            val policy: String

            @get:Min(1)
            @get:Bindable(defaultValue = "100")
            val batchSize: Int
        }
    }

    enum class DiscoveryStrategy {
        /**
         * Does not try to create a cluster, for dev or single-node only.
         */
        NONE,

        /**
         * Uses auto-discovery mode.
         */
        AUTO,

        /**
         * Uses multicast mode.
         */
        MULTICAST,

        /**
         * Uses a list of members stored in a Redis DB.
         */
        TCP_IP_REDIS,

        /**
         * Uses the Kubernetes API.
         */
        KUBERNETES
    }
}