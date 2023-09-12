/*
 * QALIPSIS
 * Copyright (C) 2023 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.report.thymeleaf

import com.lowagie.text.Image
import io.qalipsis.core.head.web.handler.MediaReplacementException
import org.apache.commons.io.IOUtils
import org.w3c.dom.Element
import org.xhtmlrenderer.extend.ReplacedElement
import org.xhtmlrenderer.extend.ReplacedElementFactory
import org.xhtmlrenderer.extend.UserAgentCallback
import org.xhtmlrenderer.layout.LayoutContext
import org.xhtmlrenderer.pdf.ITextFSImage
import org.xhtmlrenderer.pdf.ITextImageElement
import org.xhtmlrenderer.render.BlockBox
import org.xhtmlrenderer.simple.extend.FormSubmissionListener
import java.io.FileInputStream


/**
 * Custom implementation of [ReplacedElementFactory] that defines the rendering process for image elements.
 *
 * @author Francisca Eze
 */
internal class MediaReplacedElementFactory(private val superFactory: ReplacedElementFactory) : ReplacedElementFactory {

    override fun createReplacedElement(
        layoutContext: LayoutContext?, blockBox: BlockBox,
        userAgentCallback: UserAgentCallback?, cssWidth: Int, cssHeight: Int,
    ): ReplacedElement? {
        val element: Element = blockBox.element
        val nodeName = element.nodeName
        // Replace any <img class="img" /> with the binary data of `image.svg` into the PDF.
        if ("img" == nodeName) {
            //Returns the image src.
            val imagePath: String = element.getAttribute("src")
            try {
                val fsImage = ITextFSImage(Image.getInstance(IOUtils.toByteArray(FileInputStream(imagePath))))
                if (cssWidth != -1 || cssHeight != -1) {
                    fsImage.scale(cssWidth, cssHeight)
                } else {
                    fsImage.scale(2000, 1000)
                }
                return ITextImageElement(fsImage)
            } catch (e: Exception) {
                throw MediaReplacementException("There was a problem trying to process the images of the embedded template: $e")
            }
        }
        return this.superFactory.createReplacedElement(layoutContext, blockBox, userAgentCallback, cssWidth, cssHeight)
    }

    override fun reset() = superFactory.reset()

    override fun remove(e: Element?) = superFactory.remove(e)

    override fun setFormSubmissionListener(listener: FormSubmissionListener?) =
        superFactory.setFormSubmissionListener(listener)
}