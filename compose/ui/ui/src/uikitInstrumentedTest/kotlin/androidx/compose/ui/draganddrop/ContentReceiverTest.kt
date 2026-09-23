/*
 * Copyright 2026 The Android Open Source Project
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

@file:OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalTestApi::class,
    InternalComposeUiApi::class,
)

package androidx.compose.ui.draganddrop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.ClipItem
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.filterNotItemProviders
import androidx.compose.ui.platform.plainTextItemProvider
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.whenPlainTextLoaded
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGPointMake
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSItemProvider
import platform.Foundation.NSItemProviderReadingProtocol
import platform.Foundation.NSProgress
import platform.Foundation.NSRunLoop
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.UIKit.UIDragItem
import platform.UIKit.UIDragSessionProtocol
import platform.UIKit.UIDropSessionProgressIndicatorStyle
import platform.UIKit.UIDropSessionProtocol
import platform.UIKit.UIPasteboard
import platform.UIKit.UIView
import platform.darwin.NSObject

/**
 * Tests the iOS implementation of `Modifier.contentReceiver`, which spans `compose:ui` and
 * `compose:foundation`. These tests are instrumented tests because matching media types needs the
 * system type database, which isn't available to unit tests.
 */
class ContentReceiverTest {

    @Test
    fun testMediaTypesMatchTypeIdentifiers() = runComposeUiTest {
        val received = pasteIntoTextField(textItem("text"), pngItem())

        assertTrue(received.hasMediaType(MediaType.All))
        assertTrue(received.hasMediaType(MediaType.Text))
        assertTrue(received.hasMediaType(MediaType.PlainText))
        assertTrue(received.hasMediaType(MediaType.Image))
        assertTrue(received.hasMediaType(MediaType("image/png")))
        assertFalse(received.hasMediaType(MediaType.HtmlText))
        assertFalse(received.hasMediaType(MediaType("image/jpeg")))
    }

    @Test
    fun testHtmlMatchesText() = runComposeUiTest {
        val received = pasteIntoTextField(item("public.html"))

        assertTrue(received.hasMediaType(MediaType.Text))
        assertTrue(received.hasMediaType(MediaType.HtmlText))
        assertFalse(received.hasMediaType(MediaType.PlainText))
        assertFalse(received.hasMediaType(MediaType.Image))
    }

    @Test
    fun testWildcardMatchesPreferredMimeType() = runComposeUiTest {
        val received = pasteIntoTextField(item("com.adobe.pdf"))

        assertTrue(received.hasMediaType(MediaType("application/*")))
        assertTrue(received.hasMediaType(MediaType("application/pdf")))
        assertFalse(received.hasMediaType(MediaType("application/zip")))
        assertFalse(received.hasMediaType(MediaType.Text))
    }

    @Test
    fun testTextLeftOverByContentReceiverIsInserted() = runComposeUiTest {
        val state = TextFieldState()
        var consumedImages = 0
        setTextFieldContent(state, textItem("text"), pngItem()) { content ->
            content.consume { itemProvider ->
                itemProvider.hasItemConformingToTypeIdentifier("public.image").also {
                    if (it) consumedImages++
                }
            }
        }

        onNodeWithTag(Tag).performSemanticsAction(SemanticsActions.PasteText)
        waitForIdle()

        assertEquals(1, consumedImages)
        assertEquals("text", state.text.toString())
    }

    @Test
    fun testDropSessionMetadataDoesNotLoadContent() {
        val session = FakeDropSession(listOf(UIDragItem(pngItem().itemProvider)))
        val event = DragAndDropEvent(DropSessionContext(UIView(), session))

        assertEquals(listOf("public.png"), event.clipMetadata.typeIdentifiers)
    }

    @Test
    fun testFilterNotItemProviders() {
        val clipEntry = ClipEntry.withItems(listOf(textItem("a"), pngItem(), textItem("b")))

        assertSame(clipEntry, clipEntry.filterNotItemProviders { false })
        assertNull(clipEntry.filterNotItemProviders { true })

        val images =
            assertNotNull(
                clipEntry.filterNotItemProviders {
                    it.hasItemConformingToTypeIdentifier("public.plain-text")
                }
            )
        assertNull(images.getPlainText())
        assertFalse(images.hasPlainText())
        assertEquals(listOf("public.png"), images.clipMetadata.typeIdentifiers)

        val texts =
            assertNotNull(
                clipEntry.filterNotItemProviders {
                    it.hasItemConformingToTypeIdentifier("public.image")
                }
            )
        assertEquals("a\nb", texts.getPlainText())
        assertEquals(2, texts.itemProviders.size)
    }

    @Test
    fun testClipEntryFromDropSessionLoadsPlainText() {
        val session =
            FakeDropSession(
                listOf(
                    UIDragItem(ClipEntry.withPlainText("dropped").itemProviders.single()),
                    UIDragItem(pngItem().itemProvider),
                )
            )
        val event = DragAndDropEvent(DropSessionContext(UIView(), session))

        val clipEntry = event.toClipEntry()
        // The text is still being loaded, but it's known that there is text.
        assertTrue(clipEntry.hasPlainText())
        assertEquals(2, clipEntry.itemProviders.size)

        var isLoaded = false
        clipEntry.whenPlainTextLoaded { isLoaded = true }
        val deadline = TimeSource.Monotonic.markNow() + 5.seconds
        while (!isLoaded && deadline.hasNotPassedNow()) {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
        }

        assertTrue(isLoaded)
        assertEquals("dropped", clipEntry.getPlainText())
        val typeIdentifiers = clipEntry.clipMetadata.typeIdentifiers
        assertTrue("public.utf8-plain-text" in typeIdentifiers, "$typeIdentifiers")
        assertTrue("public.png" in typeIdentifiers, "$typeIdentifiers")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.pasteIntoTextField(
        vararg items: ClipItem
    ): TransferableContent {
        var received: TransferableContent? = null
        setTextFieldContent(TextFieldState(), *items) { content ->
            received = content
            content
        }
        onNodeWithTag(Tag).performSemanticsAction(SemanticsActions.PasteText)
        waitForIdle()
        return assertNotNull(received)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setTextFieldContent(
        state: TextFieldState,
        vararg items: ClipItem,
        onReceive: (TransferableContent) -> TransferableContent?,
    ) {
        val clipboard = FakeClipboard(ClipEntry.withItems(items.toList()))
        setContent {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                BasicTextField(
                    state = state,
                    modifier = Modifier.testTag(Tag).contentReceiver(onReceive),
                )
            }
        }
    }

    private fun textItem(text: String): ClipItem =
        ClipItem.loaded(plainTextItemProvider(text), text)

    private fun pngItem(): ClipItem = item("public.png")

    private fun item(typeIdentifier: String): ClipItem {
        @Suppress("CAST_NEVER_SUCCEEDS")
        val data: NSData = ("data" as NSString).dataUsingEncoding(NSUTF8StringEncoding)!!
        return ClipItem.loaded(NSItemProvider(item = data, typeIdentifier = typeIdentifier), null)
    }

    private class FakeClipboard(private var clipEntry: ClipEntry?) : Clipboard {
        override suspend fun getClipEntry(): ClipEntry? = clipEntry

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            this.clipEntry = clipEntry
        }

        override val nativeClipboard: NativeClipboard
            get() = UIPasteboard.generalPasteboard
    }

    @OptIn(ExperimentalForeignApi::class)
    private class FakeDropSession(private val dragItems: List<UIDragItem>) :
        NSObject(), UIDropSessionProtocol {
        private var style =
            UIDropSessionProgressIndicatorStyle.UIDropSessionProgressIndicatorStyleDefault

        override fun items(): List<*> = dragItems

        override fun progress(): NSProgress = NSProgress()

        override fun locationInView(view: UIView): CValue<CGPoint> = CGPointMake(0.0, 0.0)

        override fun allowsMoveOperation(): Boolean = false

        override fun isRestrictedToDraggingApplication(): Boolean = false

        override fun hasItemsConformingToTypeIdentifiers(typeIdentifiers: List<*>): Boolean = false

        override fun canLoadObjectsOfClass(aClass: NSItemProviderReadingProtocol): Boolean = false

        override fun localDragSession(): UIDragSessionProtocol? = null

        override fun progressIndicatorStyle(): UIDropSessionProgressIndicatorStyle = style

        override fun setProgressIndicatorStyle(
            progressIndicatorStyle: UIDropSessionProgressIndicatorStyle
        ) {
            style = progressIndicatorStyle
        }

        override fun loadObjectsOfClass(
            aClass: NSItemProviderReadingProtocol,
            completion: (List<*>?) -> Unit,
        ): NSProgress = NSProgress()
    }

    private companion object {
        const val Tag = "textField"
    }
}
