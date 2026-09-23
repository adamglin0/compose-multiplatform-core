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

@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)

package androidx.compose.foundation.content

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Matching media types against Uniform Type Identifiers needs the system type database, which isn't
 * available to unit tests, see `ContentReceiverTest` in the UIKit instrumented tests.
 */
class TransferableContentTest {

    @Test
    fun noTypes_matchNothing() {
        assertFalse(emptyList<String>().hasMediaType(MediaType.All))
        assertFalse(emptyList<String>().hasMediaType(MediaType.Text))
    }

    @Test
    fun invalidMediaTypes_matchNothing() {
        val types = listOf("public.utf8-plain-text")

        assertFalse(types.hasMediaType(MediaType("")))
        assertFalse(types.hasMediaType(MediaType("text")))
        assertFalse(types.hasMediaType(MediaType("/plain")))
    }

    @Test
    fun readPlainText() {
        assertEquals("text", ClipEntry.withPlainText("text").readPlainText())
    }

    @Test
    fun consume_everything_returnsNull() {
        val content = ClipEntry.withPlainText("text").toTransferableContent()

        assertNull(content.consume { true })
    }

    @Test
    fun consume_nothing_returnsSameInstance() {
        val content = ClipEntry.withPlainText("text").toTransferableContent()

        assertSame(content, content.consume { false })
    }

    @Test
    fun consume_seesTheItemProviders() {
        val content = ClipEntry.withPlainText("text").toTransferableContent()
        val typeIdentifiers = mutableListOf<Any?>()

        content.consume { itemProvider ->
            typeIdentifiers.addAll(itemProvider.registeredTypeIdentifiers)
            false
        }

        assertTrue("public.utf8-plain-text" in typeIdentifiers, "$typeIdentifiers")
    }

    private fun ClipEntry.toTransferableContent() =
        TransferableContent(
            clipEntry = this,
            clipMetadata = clipMetadata,
            source = TransferableContent.Source.Clipboard,
        )
}
