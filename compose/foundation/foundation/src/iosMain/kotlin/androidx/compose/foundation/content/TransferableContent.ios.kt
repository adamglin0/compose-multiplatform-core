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

@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)

package androidx.compose.foundation.content

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.filterNotItemProviders
import androidx.compose.ui.platform.whenPlainTextLoaded
import androidx.compose.ui.util.fastAny
import platform.Foundation.NSItemProvider
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.UniformTypeIdentifiers.UTTypeText
import platform.UniformTypeIdentifiers.conformsToType

/**
 * Helper function to consume parts of [TransferableContent] on iOS by splitting it to
 * [NSItemProvider] parts, one for each item. Use this function in [contentReceiver] modifier's
 * `onReceive` callback to easily separate remaining parts from incoming [TransferableContent].
 *
 * The content of an item can be loaded from its [NSItemProvider], for example with
 * [NSItemProvider.loadDataRepresentationForTypeIdentifier]. Note that the item providers call back
 * on a background queue. The content of dropped items can only be requested while the drop is being
 * performed, so request it synchronously in `onReceive`. For the same reason the plain text of
 * dropped items, [ClipEntry.getPlainText], is only available asynchronously; it's still inserted by
 * a text field once it's loaded.
 *
 * @param predicate Decides whether to consume or leave the given item out. Return true to indicate
 *   that this particular item was processed here, it shouldn't be passed further down the content
 *   receiver chain. Return false to keep it in the returned [TransferableContent].
 * @return Remaining parts of this [TransferableContent], or null if every item was consumed.
 */
@ExperimentalFoundationApi
fun TransferableContent.consume(predicate: (NSItemProvider) -> Boolean): TransferableContent? {
    val remainingClipEntry = clipEntry.filterNotItemProviders(predicate) ?: return null
    // Returning this very instance is what tells the receive chain that nothing was consumed.
    if (remainingClipEntry === clipEntry) return this

    return TransferableContent(
        clipEntry = remainingClipEntry,
        clipMetadata = remainingClipEntry.clipMetadata,
        source = source,
        platformTransferableContent = platformTransferableContent,
    )
}

/**
 * Returns whether this [TransferableContent] can provide an item with the [mediaType].
 *
 * On iOS the content is described by Uniform Type Identifiers. A concrete MIME type like
 * `text/html` matches the types that conform to the type with this MIME type. A wildcard like
 * [MediaType.Image] matches the types whose preferred MIME type has the same primary type and, for
 * `text`, `image`, `audio` and `video`, the types that conform to the corresponding general type,
 * such as `public.image`.
 */
@ExperimentalFoundationApi
actual fun TransferableContent.hasMediaType(mediaType: MediaType): Boolean =
    clipMetadata.typeIdentifiers.hasMediaType(mediaType)

/** Returns whether any of these Uniform Type Identifiers describes content with the [mediaType]. */
internal fun List<String>.hasMediaType(mediaType: MediaType): Boolean {
    val representation = mediaType.representation
    if (representation == MediaType.All.representation) return isNotEmpty()

    val slashIndex = representation.indexOf('/')
    if (slashIndex <= 0) return false

    val types = mapNotNull { UTType.typeWithIdentifier(it) }
    return if (representation.length == slashIndex + 2 && representation.last() == '*') {
        val primaryType = representation.substring(0, slashIndex + 1)
        val generalType = generalTypeOf(primaryType)
        types.fastAny { type ->
            (generalType != null && type.conformsToType(generalType)) ||
                type.preferredMIMEType?.startsWith(primaryType, ignoreCase = true) == true
        }
    } else {
        val desiredType = UTType.typeWithMIMEType(representation) ?: return false
        types.fastAny { it.conformsToType(desiredType) }
    }
}

/** The general type of the content whose MIME type has the [primaryType], like `image/`. */
private fun generalTypeOf(primaryType: String): UTType? =
    when (primaryType.lowercase()) {
        "text/" -> UTTypeText
        "image/" -> UTTypeImage
        "audio/" -> UTTypeAudio
        "video/" -> UTTypeMovie
        else -> null
    }

internal actual fun ClipEntry.readPlainText(): String? = getPlainText()

internal actual fun ClipEntry.readPlainTextWhenLoaded(onResult: (String?) -> Unit) {
    whenPlainTextLoaded { onResult(getPlainText()) }
}
