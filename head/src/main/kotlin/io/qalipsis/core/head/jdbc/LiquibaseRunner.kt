/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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