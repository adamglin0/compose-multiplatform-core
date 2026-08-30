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
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.util.fastAny
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

/**
 * Helper function to consume parts of [TransferableContent] on desktop by splitting it to
 * [DataFlavor] parts. Use this function in [contentReceiver] modifier's `onReceive` callback to
 * easily separate remaining parts from incoming [TransferableContent].
 *
 * An AWT [Transferable] carries a single item that is offered in one or more [DataFlavor]s, so the
 * parts that can be consumed here are the representations of that item, not separate items.
 *
 * @param predicate Decides whether to consume or leave the given flavor out. Return true to
 *   indicate that this particular representation was processed here, it shouldn't be passed further
 *   down the content receiver chain. Return false to keep it in the returned [TransferableContent].
 * @return Remaining parts of this [TransferableContent], or null if every flavor was consumed.
 */
@ExperimentalFoundationApi
fun TransferableContent.consume(predicate: (DataFlavor) -> Boolean): TransferableContent? {
    val transferable = clipEntry.asAwtTransferable ?: return this
    val flavors = clipMetadata.dataFlavors
    val remainingFlavors = flavors.filterNot(predicate)

    // Do not allocate anything if nothing was consumed. Returning this very instance is what tells
    // the receive chain that the content was not handled here.
    if (remainingFlavors.size == flavors.size) return this
    if (remainingFlavors.isEmpty()) return null

    val remainingClipEntry = ClipEntry(FilteredTransferable(transferable, remainingFlavors))
    return TransferableContent(
        clipEntry = remainingClipEntry,
        clipMetadata = remainingClipEntry.clipMetadata,
        source = source,
        platformTransferableContent = platformTransferableContent,
    )
}

@ExperimentalFoundationApi
actual fun TransferableContent.hasMediaType(mediaType: MediaType): Boolean =
    clipMetadata.dataFlavors.hasMediaType(mediaType)

/** Returns whether any of these [DataFlavor]s can provide an item with the [mediaType]. */
internal fun List<DataFlavor>.hasMediaType(mediaType: MediaType): Boolean = fastAny {
    it.mediaTypeRepresentation.matchesMediaType(mediaType)
}

internal actual fun ClipEntry.readPlainText(): String? {
    val transferable = asAwtTransferable ?: return null
    return try {
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            transferable.getTransferData(DataFlavor.stringFlavor) as? String
        } else {
            // A text flavor that is not representable as a String, e.g. a "text/html" flavor that
            // is only offered as an InputStream.
            DataFlavor.selectBestTextFlavor(transferable.transferDataFlavors)
                ?.getReaderForText(transferable)
                ?.use { it.readText() }
        }
    } catch (_: UnsupportedFlavorException) {
        // The content is no longer available in the requested flavor.
        null
    } catch (_: IOException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: IllegalArgumentException) {
        // getReaderForText rejects flavors that don't carry text after all.
        null
    }
}

/**
 * The `primaryType/subType` pair that describes this [DataFlavor] as a media type.
 *
 * AWT represents most text as [DataFlavor.stringFlavor], whose MIME type is
 * `application/x-java-serialized-object`. Such flavors are reported as `text/plain` so that they
 * are matched by [MediaType.Text] and [MediaType.PlainText].
 */
private val DataFlavor.mediaTypeRepresentation: String
    get() =
        if (isFlavorTextType() && primaryType != "text") {
            "text/plain"
        } else {
            "$primaryType/$subType"
        }

/**
 * Returns whether this concrete `primaryType/subType` representation is matched by [mediaType],
 * whose sub type, or whole representation, may be the `*` wildcard.
 */
private fun String.matchesMediaType(mediaType: MediaType): Boolean {
    val desired = mediaType.representation
    if (desired == "*/*") return true

    val slashIndex = desired.indexOf('/')
    if (slashIndex <= 0) return false

    return if (desired.length == slashIndex + 2 && desired[slashIndex + 1] == '*') {
        desired.regionMatches(0, this, 0, slashIndex + 1)
    } else {
        desired == this
    }
}

/** A [Transferable] that only offers the [flavors] subset of what [delegate] can provide. */
private class FilteredTransferable(
    private val delegate: Transferable,
    private val flavors: List<DataFlavor>,
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = flavors.toTypedArray()

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in flavors

    override fun getTransferData(flavor: DataFlavor): Any {
        if (flavor !in flavors) throw UnsupportedFlavorException(flavor)
        return delegate.getTransferData(flavor)
    }
}
