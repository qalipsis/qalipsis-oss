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

package io.qalipsis.core.head.jdbc

import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.core.util.StringUtils
import io.micronaut.liquibase.LiquibaseConfigurationProperties
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Singleton
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.postgresql.Driver
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

/**
 * When the DB schema creation is enabled, create or update it when a [ConnectionFactory] is created.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(beans = [DatasourceConfiguration::class, LiquibaseConfigurationProperties::class])
internal class LiquibaseRunner(
    private val configuration: LiquibaseConfigurationProperties,
    private val datasourceConfiguration: DatasourceConfiguration,
) : BeanCreatedEventListener<ConnectionFactory> {

    override fun onCreated(event: BeanCreatedEvent<ConnectionFactory>): ConnectionFactory {
        // List of supported parameters: https://jdbc.postgresql.org/documentation/use/#connection-parameters
        val changeLog = configuration.changeLog
        val properties = Properties()
        properties.putAll(datasourceConfiguration.properties)
        properties["ApplicationName"] = "qalipsis-head-schema-creation"
        properties["user"] = datasourceConfiguration.username
        properties["password"] = datasourceConfiguration.password

        DriverManager
            .getConnection(
                "jdbc:postgresql://${datasourceConfiguration.host}:${datasourceConfiguration.port}/${datasourceConfiguration.database}",
                properties
            )
            .use { connection ->
                val liquibase = Liquibase(changeLog, ClassLoaderResourceAccessor(), createDatabase(connection))
                liquibase.update(Contexts(), LabelExpression())
            }

        return event.bean
    }

    private fun createDatabase(connection: Connection): Database {
        val liquibaseConnection = JdbcConnection(connection)
        val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(liquibaseConnection)
        val defaultSchema = datasourceConfiguration.schema
        if (StringUtils.isNotEmpty(defaultSchema)) {
            if (database.supportsSchemas()) {
                database.defaultSchemaName = defaultSchema
            } else if (database.supportsCatalogs()) {
                database.defaultCatalogName = defaultSchema
            }
        }
        val liquibaseSchema = configuration.liquibaseSchema
        if (StringUtils.isNotEmpty(liquibaseSchema)) {
            if (database.supportsSchemas()) {
                database.liquibaseSchemaName = liquibaseSchema
            } else if (database.supportsCatalogs()) {
                database.liquibaseCatalogName = liquibaseSchema
            }
        }

        return database
    }

    companion object {

        init {
            kotlin.runCatching {
                Driver.register()
            }
        }
    }

}