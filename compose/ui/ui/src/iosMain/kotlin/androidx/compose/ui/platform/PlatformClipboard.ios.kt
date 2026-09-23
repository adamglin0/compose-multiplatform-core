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

package androidx.compose.ui.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.util.fastAny
import platform.Foundation.NSItemProvider
import platform.UIKit.UIPasteboard
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.UniformTypeIdentifiers.UTTypeUTF8PlainText
import platform.UniformTypeIdentifiers.conformsToType

actual typealias NativeClipboard = UIPasteboard

private class IosClipboard : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? {
        if (nativeClipboard.numberOfItems() == 0L) return null
        val pasteboard = nativeClipboard
        return ClipEntry().apply {
            getPlainTextLambda = {
                // Don't read the pasteboard again once the items are read, as every read may show
                // the paste permission prompt.
                if (isItemsSnapshotTaken) plainTextOf(items) else pasteboard.string
            }
            hasPlainText = pasteboard.hasStrings
            // Reading the types doesn't access the content, so it doesn't trigger the paste
            // permission prompt, unlike reading the item providers.
            getTypeIdentifiersLambda = {
                pasteboard.pasteboardTypesForItemSet(null)
                    .orEmpty()
                    .flatMap { it as? List<*> ?: emptyList<Any?>() }
                    .filterIsInstance<String>()
                    .distinct()
            }
            getItemsLambda = {
                val itemProviders = pasteboard.itemProviders.filterIsInstance<NSItemProvider>()
                val items = pasteboard.items
                itemProviders.mapIndexed { index, itemProvider ->
                    ClipItem.loaded(itemProvider, (items.getOrNull(index) as? Map<*, *>)?.plainText())
                }
            }
        }
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        if (clipEntry == null) {
            nativeClipboard.items = emptyList<Map<String, Any>>()
        } else {
            nativeClipboard.string = clipEntry.getPlainText()
        }
    }

    /**
     * Provides the [platform.UIKit.UIPasteboard] instance.
     */
    override val nativeClipboard: NativeClipboard
        get() = UIPasteboard.generalPasteboard
}

@Suppress("DEPRECATION")
private class IosClipboardManager : ClipboardManager {
    override fun getText(): AnnotatedString? =
        UIPasteboard.generalPasteboard.string?.let { AnnotatedString(it) }

    override fun setText(annotatedString: AnnotatedString) {
        UIPasteboard.generalPasteboard.string = annotatedString.text
    }

    override fun hasText(): Boolean = !UIPasteboard.generalPasteboard.string.isNullOrEmpty()

    override fun getClip(): ClipEntry? = null

    @Suppress("GetterSetterNames")
    override fun setClip(clipEntry: ClipEntry?) = Unit
}

@Suppress("DEPRECATION")
internal actual fun createPlatformClipboardManager(): ClipboardManager = IosClipboardManager()

internal actual fun createPlatformClipboard(): Clipboard = IosClipboard()

/**
 * A wrapper for [UIPasteboard] items or for the items of a drag and drop session.
 *
 * The plain text representation is available with [getPlainText]. Other representations can be
 * loaded from [itemProviders]. To access or set other data items of the pasteboard, consider using
 * [Clipboard.nativeClipboard].
 */
actual class ClipEntry internal constructor() {

    /**
     * Describes the types of the content of this entry. Reading it doesn't access the content
     * itself, so it doesn't trigger the pasteboard permission prompt.
     */
    actual val clipMetadata: ClipMetadata
        get() = ClipMetadata(getTypeIdentifiersLambda())

    internal var getPlainTextLambda: () -> String? = { null }
    internal var hasPlainText: Boolean = false
    internal var getTypeIdentifiersLambda: () -> List<String> = { emptyList() }
    internal var getItemsLambda: () -> List<ClipItem> = { emptyList() }

    private val itemsSnapshot = lazy { getItemsLambda() }

    /** The items of this entry, read once so that they represent a single snapshot. */
    internal val items: List<ClipItem> by itemsSnapshot

    internal val isItemsSnapshotTaken: Boolean
        get() = itemsSnapshot.isInitialized()

    /**
     * Whether the plain text of some items may still be loading, which is only the case for the
     * items of a drop, see [ClipItem.loading].
     */
    internal var mayLoadPlainText: Boolean = false

    @ExperimentalComposeUiApi
    fun getPlainText(): String? = getPlainTextLambda.invoke()

    @ExperimentalComposeUiApi
    fun hasPlainText(): Boolean {
        return hasPlainText
    }

    /**
     * The [NSItemProvider]s of the items of this entry, one for each item. They can be used to
     * load representations of the content other than plain text.
     *
     * For an entry obtained from the pasteboard, reading this property accesses the content of the
     * pasteboard, which may show the paste permission prompt to the user. For an entry obtained
     * from a drop, their content can only be loaded while the drop is being performed, that is,
     * synchronously in `onReceive` of `Modifier.contentReceiver`.
     */
    @ExperimentalComposeUiApi
    val itemProviders: List<NSItemProvider>
        get() = items.map { it.itemProvider }

    companion object {
        @ExperimentalComposeUiApi
        fun withPlainText(text: String): ClipEntry = ClipEntry().apply {
            getPlainTextLambda = { text }
            hasPlainText = true
            getTypeIdentifiersLambda = { listOf(UTTypeUTF8PlainText.identifier) }
            getItemsLambda = {
                listOf(ClipItem.loaded(plainTextItemProvider(text), text))
            }
        }

        /**
         * Creates a [ClipEntry] from [items]. Its plain text consists of the plain text of the
         * items that is loaded so far, see [whenPlainTextLoaded].
         */
        internal fun withItems(items: List<ClipItem>): ClipEntry = ClipEntry().apply {
            getPlainTextLambda = { plainTextOf(items) }
            hasPlainText = items.fastAny { it.hasPlainText }
            getTypeIdentifiersLambda = { items.map { it.itemProvider }.typeIdentifiers() }
            getItemsLambda = { items }
            mayLoadPlainText = items.fastAny { !it.isPlainTextLoaded }
        }
    }
}

/** Joins the plain text of [items] with a new line, like Android does for multiple items. */
private fun plainTextOf(items: List<ClipItem>): String? =
    items.mapNotNull { it.plainText }.takeIf { it.isNotEmpty() }?.joinToString("\n")

/** The distinct type identifiers of the representations these item providers can provide. */
internal fun List<NSItemProvider>.typeIdentifiers(): List<String> =
    flatMap { it.registeredTypeIdentifiers }.filterIsInstance<String>().distinct()

/**
 * Returns a [ClipEntry] without the items whose [NSItemProvider] matches [predicate], this very
 * instance if no item matches, or null if every item matches.
 */
@InternalComposeUiApi
fun ClipEntry.filterNotItemProviders(predicate: (NSItemProvider) -> Boolean): ClipEntry? {
    val remainingItems = items.filterNot { predicate(it.itemProvider) }
    return when {
        remainingItems.size == items.size -> this
        remainingItems.isEmpty() -> null
        else -> ClipEntry.withItems(remainingItems)
    }
}

/**
 * Returns the plain text representation of a [UIPasteboard] item, which maps the type identifiers
 * of its representations to their values.
 */
private fun Map<*, *>.plainText(): String? =
    entries.firstNotNullOfOrNull { (typeIdentifier, value) ->
        value as? String ?: return@firstNotNullOfOrNull null
        val type = (typeIdentifier as? String)?.let { UTType.typeWithIdentifier(it) }
        value.takeIf { type?.conformsToType(UTTypePlainText) == true }
    }

/**
 * Describes the content of a [ClipEntry] without giving access to the content itself.
 *
 * On iOS the content is described by the Uniform Type Identifiers of its representations, such as
 * `public.utf8-plain-text` or `public.png`.
 *
 * @property typeIdentifiers The distinct Uniform Type Identifiers of the representations of the
 *   described content.
 */
actual class ClipMetadata internal constructor(
    @property:ExperimentalComposeUiApi val typeIdentifiers: List<String>
)
