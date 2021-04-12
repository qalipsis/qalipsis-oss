package io.qalipsis.api.monitoring

import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("monitoring")
internal class MonitoringExportConfiguration : ExportConfigurationProperties()
