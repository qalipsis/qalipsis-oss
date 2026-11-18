/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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
package io.qalipsis.core.head.report

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
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
     * Returns a singleton instance of thymeleaf [TemplateEngine] with resolvers for both HTML and plain-text templates.
     *
     * HTML resolver (order 1): resolves `views/[name].html` in [TemplateMode.HTML] — used by [HtmlReportService].
     * Text resolver (order 2): resolves `views/[name].txt` in [TemplateMode.TEXT] — used by [AsciiReportService].
     */
    @Singleton
    @Primary
    fun templateEngine(): TemplateEngine {
        val htmlResolver = ClassLoaderTemplateResolver().apply {
            order = 1
            templateMode = TemplateMode.HTML
            characterEncoding = CHARACTER_ENCODING
            prefix = RESOLVER_PREFIX
            suffix = ".html"
            checkExistence = true
        }
        val textResolver = ClassLoaderTemplateResolver().apply {
            order = 2
            templateMode = TemplateMode.TEXT
            characterEncoding = CHARACTER_ENCODING
            prefix = RESOLVER_PREFIX
            suffix = ".txt"
            checkExistence = true
        }
        return TemplateEngine().apply {
            addTemplateResolver(htmlResolver)
            addTemplateResolver(textResolver)
            addDialect(Java8TimeDialect())
        }
    }

    private companion object {
        const val CHARACTER_ENCODING = "UTF-8"
        const val RESOLVER_PREFIX = "/views/"
    }
}