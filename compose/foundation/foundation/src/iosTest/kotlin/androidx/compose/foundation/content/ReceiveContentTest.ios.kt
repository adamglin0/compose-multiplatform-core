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
)

package androidx.compose.foundation.content

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import platform.UIKit.UIPasteboard

class ReceiveContentTest {

    @Test
    fun paste_isReceivedByContentReceiver() = runComposeUiTest {
        val state = TextFieldState()
        var received: TransferableContent? = null
        setContent {
            CompositionLocalProvider(LocalClipboard provides FakeClipboard("hello")) {
                BasicTextField(
                    state = state,
                    modifier =
                        Modifier.testTag(Tag).contentReceiver { content ->
                            received = content
                            content
                        },
                )
            }
        }

        onNodeWithTag(Tag).performSemanticsAction(SemanticsActions.PasteText)
        waitForIdle()

        val content = assertNotNull(received)
        assertEquals(TransferableContent.Source.Clipboard, content.source)
        assertEquals(listOf("public.utf8-plain-text"), content.clipMetadata.typeIdentifiers)
        // The content was not consumed, so the text field inserts it.
        assertEquals("hello", state.text.toString())
    }

    @Test
    fun paste_consumedByContentReceiver_isNotInserted() = runComposeUiTest {
        val state = TextFieldState()
        var receivedText: String? = null
        setContent {
            CompositionLocalProvider(LocalClipboard provides FakeClipboard("hello")) {
                BasicTextField(
                    state = state,
                    modifier =
                        Modifier.testTag(Tag).contentReceiver { content ->
                            receivedText = content.clipEntry.getPlainText()
                            content.consume { true }
                        },
                )
            }
        }

        onNodeWithTag(Tag).performSemanticsAction(SemanticsActions.PasteText)
        waitForIdle()

        assertEquals("hello", receivedText)
        assertEquals("", state.text.toString())
    }

    private class FakeClipboard(text: String) : Clipboard {
        private var clipEntry: ClipEntry? = ClipEntry.withPlainText(text)

        override suspend fun getClipEntry(): ClipEntry? = clipEntry

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            this.clipEntry = clipEntry
        }

        override val nativeClipboard: NativeClipboard
            get() = UIPasteboard.generalPasteboard
    }

    private companion object {
        const val Tag = "textField"
    }
}
