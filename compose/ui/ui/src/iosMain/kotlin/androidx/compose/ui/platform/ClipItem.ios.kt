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

package androidx.compose.ui.platform

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.uikit.utils.cmp_loadString
import platform.Foundation.NSItemProvider
import platform.Foundation.NSItemProviderWritingProtocol
import platform.Foundation.NSString
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * An item of a [ClipEntry], whose plain text representation is either loaded ahead of time or
 * loaded asynchronously, see [ClipItem.loading].
 */
internal class ClipItem private constructor(val itemProvider: NSItemProvider) {
    /** The plain text representation of the item, null if it has none or isn't loaded yet. */
    var plainText: String? = null
        private set

    var isPlainTextLoaded: Boolean = false
        private set

    private var onPlainTextLoaded: MutableList<() -> Unit>? = null

    /** Whether the item has, or may turn out to have, a plain text representation. */
    val hasPlainText: Boolean
        get() =
            plainText != null ||
                (!isPlainTextLoaded &&
                    itemProvider.hasItemConformingToTypeIdentifier(UTTypePlainText.identifier))

    /** Invokes [block] on the main thread once the plain text of this item is loaded. */
    fun whenPlainTextLoaded(block: () -> Unit) {
        if (isPlainTextLoaded) {
            block()
        } else {
            (onPlainTextLoaded ?: mutableListOf<() -> Unit>().also { onPlainTextLoaded = it }).add(
                block
            )
        }
    }

    private fun completeLoading(plainText: String?) {
        this.plainText = plainText
        isPlainTextLoaded = true
        val callbacks = onPlainTextLoaded
        onPlainTextLoaded = null
        callbacks?.forEach { it() }
    }

    companion object {
        fun loaded(itemProvider: NSItemProvider, plainText: String?): ClipItem =
            ClipItem(itemProvider).apply { completeLoading(plainText) }

        /**
         * Creates an item whose plain text is loaded asynchronously. The loading is started right
         * away, because the data of a dropped item can only be requested while the drop is being
         * performed.
         */
        fun loading(itemProvider: NSItemProvider): ClipItem =
            ClipItem(itemProvider).apply {
                itemProvider.cmp_loadString { plainText, _ ->
                    // The item provider calls back on a background queue.
                    dispatch_async(dispatch_get_main_queue()) { completeLoading(plainText) }
                }
            }
    }
}

/**
 * Invokes [block] on the main thread once the plain text of every item of this entry is loaded,
 * right away if it's already loaded. The plain text of a dropped item is loaded asynchronously.
 */
@InternalComposeUiApi
fun ClipEntry.whenPlainTextLoaded(block: () -> Unit) {
    // Checking the flag first avoids reading the items of a pasteboard entry, which may show the
    // paste permission prompt.
    val pendingItems =
        if (mayLoadPlainText) items.filterNot { it.isPlainTextLoaded } else emptyList()
    if (pendingItems.isEmpty()) {
        block()
        return
    }
    var pendingCount = pendingItems.size
    pendingItems.forEach { item ->
        item.whenPlainTextLoaded {
            pendingCount--
            if (pendingCount == 0) block()
        }
    }
}

/**
 * Creates an [NSItemProvider] that provides [text] as an [NSString] object, like the item providers
 * of the text dragged from other applications.
 */
internal fun plainTextItemProvider(text: String): NSItemProvider {
    // NSString conforms to NSItemProviderWriting in a category, which Kotlin doesn't know about.
    @Suppress("CAST_NEVER_SUCCEEDS")
    val writing = (text as NSString) as NSItemProviderWritingProtocol
    return NSItemProvider(`object` = writing)
}
