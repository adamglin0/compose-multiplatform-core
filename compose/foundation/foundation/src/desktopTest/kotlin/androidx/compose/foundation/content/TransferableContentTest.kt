/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)

package androidx.compose.foundation.content

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TransferableContentTest {

    @Test
    fun mediaTypesAreMimeTypes() {
        assertEquals("text/*", MediaType.Text.representation)
        assertEquals("text/plain", MediaType.PlainText.representation)
        assertEquals("text/html", MediaType.HtmlText.representation)
        assertEquals("image/*", MediaType.Image.representation)
        assertEquals("*/*", MediaType.All.representation)
    }

    @Test
    fun mediaTypesWithSameRepresentationAreEqual() {
        assertEquals(MediaType("text/plain"), MediaType.PlainText)
        assertEquals(MediaType("text/plain").hashCode(), MediaType.PlainText.hashCode())
        assertFalse(MediaType("text/plain") == MediaType.HtmlText)
    }

    @Test
    fun stringContentHasTextMediaTypes() {
        val content = transferableContent(StringSelection("Hello"))

        assertTrue(content.hasMediaType(MediaType.All))
        assertTrue(content.hasMediaType(MediaType.Text))
        assertTrue(content.hasMediaType(MediaType.PlainText))
        assertFalse(content.hasMediaType(MediaType.HtmlText))
        assertFalse(content.hasMediaType(MediaType.Image))
    }

    @Test
    fun htmlContentHasHtmlAndTextMediaTypes() {
        val content = transferableContent(HtmlTransferable("<p>Hello</p>"))

        assertTrue(content.hasMediaType(MediaType.Text))
        assertTrue(content.hasMediaType(MediaType.HtmlText))
        assertFalse(content.hasMediaType(MediaType.PlainText))
    }

    @Test
    fun imageContentHasImageMediaType() {
        val content = transferableContent(ImageTransferable())

        assertTrue(content.hasMediaType(MediaType.All))
        assertTrue(content.hasMediaType(MediaType.Image))
        assertTrue(content.hasMediaType(MediaType("image/x-java-image")))
        assertFalse(content.hasMediaType(MediaType.Text))
    }

    @Test
    fun fileListContentHasFileListMediaType() {
        val content = transferableContent(FileListTransferable())

        assertTrue(content.hasMediaType(MediaType.All))
        assertTrue(content.hasMediaType(MediaType("application/x-java-file-list")))
        assertTrue(content.hasMediaType(MediaType("application/*")))
        assertFalse(content.hasMediaType(MediaType.Text))
    }

    @Test
    fun emptyContentHasNoMediaType() {
        val content = transferableContent(EmptyTransferable)

        assertFalse(content.hasMediaType(MediaType.All))
        assertFalse(content.hasMediaType(MediaType.Text))
    }

    @Test
    fun consumingNothingReturnsTheSameContent() {
        val content = transferableContent(MixedTransferable("Hello"))

        assertSame(content, content.consume { false })
    }

    @Test
    fun consumingEveryFlavorReturnsNull() {
        val content = transferableContent(MixedTransferable("Hello"))

        assertNull(content.consume { true })
    }

    @Test
    fun consumingSomeFlavorsLeavesTheRestOfTheContent() {
        val content = transferableContent(MixedTransferable("Hello"))
        assertTrue(content.hasMediaType(MediaType.Image))

        val remaining = requireNotNull(content.consume { it == DataFlavor.imageFlavor })

        assertFalse(remaining.hasMediaType(MediaType.Image))
        assertTrue(remaining.hasMediaType(MediaType.Text))
        assertEquals("Hello", remaining.clipEntry.readPlainText())
        assertEquals(content.source, remaining.source)
    }

    @Test
    fun consumingFromNonAwtContentReturnsTheSameContent() {
        val clipEntry = ClipEntry(nativeClipEntry = "not a transferable")
        val content =
            TransferableContent(
                clipEntry = clipEntry,
                clipMetadata = clipEntry.clipMetadata,
                source = TransferableContent.Source.DragAndDrop,
            )

        assertSame(content, content.consume { true })
    }

    @Test
    fun readPlainTextReadsStringContent() {
        assertEquals("Hello", clipEntry(StringSelection("Hello")).readPlainText())
    }

    @Test
    fun readPlainTextReadsTextOnlyAvailableAsReader() {
        assertEquals("<p>Hello</p>", clipEntry(HtmlTransferable("<p>Hello</p>")).readPlainText())
    }

    @Test
    fun readPlainTextReturnsNullForNonTextContent() {
        assertNull(clipEntry(ImageTransferable()).readPlainText())
    }

    @Test
    fun readPlainTextReturnsNullForUnreadableContent() {
        assertNull(clipEntry(BrokenTransferable).readPlainText())
    }

    @Test
    fun readPlainTextReturnsNullForNonAwtClipEntry() {
        assertNull(ClipEntry(nativeClipEntry = "not a transferable").readPlainText())
    }

    private fun clipEntry(transferable: Transferable) = ClipEntry(transferable)

    private fun transferableContent(transferable: Transferable): TransferableContent {
        val clipEntry = clipEntry(transferable)
        return TransferableContent(
            clipEntry = clipEntry,
            clipMetadata = clipEntry.clipMetadata,
            source = TransferableContent.Source.DragAndDrop,
        )
    }
}

/** Offers its content as an HTML text that can only be read through a [java.io.Reader]. */
private class HtmlTransferable(private val html: String) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(htmlFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == htmlFlavor

    override fun getTransferData(flavor: DataFlavor): Any =
        if (flavor == htmlFlavor) StringReader(html) else throw UnsupportedFlavorException(flavor)

    private companion object {
        val htmlFlavor = DataFlavor("text/html;class=java.io.Reader", "HTML")
    }
}

/** Offers the same item both as an image and as a text, the way image editors usually do. */
private class MixedTransferable(private val text: String) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> =
        arrayOf(DataFlavor.imageFlavor, DataFlavor.stringFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
        flavor in transferDataFlavors

    override fun getTransferData(flavor: DataFlavor): Any =
        when (flavor) {
            DataFlavor.imageFlavor -> BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            DataFlavor.stringFlavor -> text
            else -> throw UnsupportedFlavorException(flavor)
        }
}

private class ImageTransferable : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
        flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor): Any =
        if (flavor == DataFlavor.imageFlavor) {
            BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        } else {
            throw UnsupportedFlavorException(flavor)
        }
}

private class FileListTransferable : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> =
        arrayOf(DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
        flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor): Any =
        if (flavor == DataFlavor.javaFileListFlavor) {
            emptyList<Any>()
        } else {
            throw UnsupportedFlavorException(flavor)
        }
}

private object EmptyTransferable : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = emptyArray()

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = false

    override fun getTransferData(flavor: DataFlavor): Any = throw UnsupportedFlavorException(flavor)
}

/** Announces text that is no longer available by the time it is read. */
private object BrokenTransferable : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.stringFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
        flavor == DataFlavor.stringFlavor

    override fun getTransferData(flavor: DataFlavor): Any = throw IOException("gone")
}
