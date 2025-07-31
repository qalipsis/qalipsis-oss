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
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.head.report.thymeleaf.MediaReplacedElementFactory.Companion.ALT
import io.qalipsis.core.head.report.thymeleaf.MediaReplacedElementFactory.Companion.DEFAULT_ICON_SCALE_HEIGHT
import io.qalipsis.core.head.report.thymeleaf.MediaReplacedElementFactory.Companion.DEFAULT_ICON_SCALE_WIDTH
import io.qalipsis.core.head.report.thymeleaf.MediaReplacedElementFactory.Companion.IMG
import io.qalipsis.core.head.report.thymeleaf.MediaReplacedElementFactory.Companion.PDF_IMAGE
import io.qalipsis.core.head.report.thymeleaf.MediaReplacedElementFactory.Companion.SRC
import io.qalipsis.core.head.report.thymeleaf.MediaReplacedElementFactory.Companion.logger
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.PNGTranscoder
import org.w3c.dom.Element
import org.xhtmlrenderer.extend.ReplacedElement
import org.xhtmlrenderer.extend.ReplacedElementFactory
import org.xhtmlrenderer.extend.UserAgentCallback
import org.xhtmlrenderer.layout.LayoutContext
import org.xhtmlrenderer.pdf.ITextFSImage
import org.xhtmlrenderer.pdf.ITextImageElement
import org.xhtmlrenderer.render.BlockBox
import org.xhtmlrenderer.simple.extend.FormSubmissionListener
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

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
        if (IMG == nodeName && element.getAttribute(ALT).contains(PDF_IMAGE)) {
            val imageString: String = element.getAttribute(SRC)
            try {
                val fsImage = ITextFSImage(Image.getInstance(svgToPng(imageString)))
                if (cssWidth != -1 || cssHeight != -1) {
                    fsImage.scale(cssWidth, cssHeight)
                } else {
                    fsImage.scale(DEFAULT_ICON_SCALE_WIDTH, DEFAULT_ICON_SCALE_HEIGHT)
                }
                return ITextImageElement(fsImage)
            } catch (e: Exception) {
                logger.debug(e) { "There was a problem trying to process the images of the embedded template: ${e.message}" }
            }
        }
        return this.superFactory.createReplacedElement(layoutContext, blockBox, userAgentCallback, cssWidth, cssHeight)
    }

    override fun reset() = superFactory.reset()

    override fun remove(e: Element?) = superFactory.remove(e)

    override fun setFormSubmissionListener(listener: FormSubmissionListener?) =
        superFactory.setFormSubmissionListener(listener)

    /**
     * Converts the svg image to a convenient png byte array format.
     *
     * @param imageSrc the image string content
     */
    private fun svgToPng(imageSrc: String): ByteArray {
        val base64ByteArray = Base64.getDecoder().decode(imageSrc)
        // Set the input source as the SVG data.
        val transcoderInput = ByteArrayInputStream(base64ByteArray).use { TranscoderInput(it) }
        // Create an output stream for the converted image.
        return ByteArrayOutputStream().use { outputStream ->
            // Perform the SVG to PNG conversion.
            PNGTranscoder().transcode(transcoderInput, TranscoderOutput(outputStream))
            // Get the PNG image as a byte array.
            outputStream.toByteArray()
        }
    }

    /**
     * Contains constants used within this class.
     *
     * @property logger custom logger instance
     * @property DEFAULT_ICON_SCALE_WIDTH ideal number of units, to scale the width of the pdf icons to,
     * if not previously specified in the css file. This value gives the best result for the icons, equivalent to the design on the style guide
     * @property DEFAULT_ICON_SCALE_HEIGHT ideal number of units to scale the height of the pdf icons to,
     * if not previously specified in the css file. This value gives the best result for the icons, equivalent to the design on the style guide
     * @property ALT img element attribute that holds alternative text for image tags. Some image elements contain an alt attribute of
     * "pdf-image" to correctly mark out the images that need extra processing before they are rendered in the pdf
     * @property SRC img element attribute that holds the image content that is being rendered
     * @property IMG specifies element tag that needs to be replaced
     * @property PDF_IMAGE attribute name contained in all img elements to be replaced.
     * This determines if an image content should be replaced
     */
    private companion object {

        val logger = logger()

        const val DEFAULT_ICON_SCALE_WIDTH = 285

        const val DEFAULT_ICON_SCALE_HEIGHT = 300

        const val ALT = "alt"

        const val SRC = "src"

        const val IMG = "img"

        const val PDF_IMAGE = "pdf-image"
    }

}