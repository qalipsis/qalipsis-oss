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
package io.qalipsis.core.head.report

import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import org.thymeleaf.TemplateEngine
import org.thymeleaf.extras.java8time.dialect.Java8TimeDialect
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

/**
 * Bean factory that produces an instance of properly configured thymeleaf [TemplateEngine].
 *
 * @author Francisca Eze
 */
@Factory
class TemplateBeanFactory {

    /**
     * Returns a singleton instance of thymeleaf [TemplateEngine].
     */
    @Singleton
    fun templateEngine(): TemplateEngine {
        val templateResolver = ClassLoaderTemplateResolver().apply {
            templateMode = TemplateMode.HTML
            characterEncoding = CHARACTER_ENCODING
            prefix = RESOLVER_PREFIX
            suffix = RESOLVER_SUFFIX
        }
        return TemplateEngine().apply {
            setTemplateResolver(templateResolver)
            addDialect(Java8TimeDialect())
        }
    }

    private companion object {
        const val CHARACTER_ENCODING = "UTF-8"
        const val RESOLVER_PREFIX = "/views/"
        const val RESOLVER_SUFFIX = ".html"
    }
}