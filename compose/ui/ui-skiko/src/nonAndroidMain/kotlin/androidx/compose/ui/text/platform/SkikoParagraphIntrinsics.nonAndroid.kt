@file:OptIn(InternalComposeUiApi::class)

/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.compose.ui.text.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Range
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.StrongDirectionType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.firstStrongDirectionType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.intl.isRtl
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import kotlin.math.ceil

internal class SkikoParagraphIntrinsics(
    val text: String,
    private val style: TextStyle,
    private val annotations: List<AnnotatedString.Range<out AnnotatedString.Annotation>>,
    private val placeholders: List<Range<Placeholder>>,
    private val density: Density,
    private val fontFamilyResolver: FontFamily.Resolver
) : ParagraphIntrinsics {
    val textDirection = resolveTextDirection(text, style.textDirection, style.localeList)

    //we need to track it reactively to invalidate the UI
    override var hasStaleResolvedFonts: Boolean by mutableStateOf(false)
        private set

    private var layouter: ParagraphLayouter? = newLayouter()

    fun layouter(): ParagraphLayouter {
        val layouter = this.layouter ?: newLayouter()
        this.layouter = null
        return layouter
    }

    private fun newLayouter() = ParagraphLayouter(
        text = text,
        textDirection = textDirection,
        style = style,
        annotations = annotations,
        placeholders = placeholders,
        density = density,
        fontFamilyResolver = fontFamilyResolver,
        onFontStale = { hasStaleResolvedFonts = true }
    )

    /**
     * Creates a [ParagraphLayouter] for a truncated version of the text used to implement
     * [androidx.compose.ui.text.style.TextOverflow.StartEllipsis] and
     * [androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis], which Skia doesn't support
     * natively.
     *
     * The resulting text keeps the range `[0, prefixEnd)` and `[suffixStart, length)` of the
     * original text with [ellipsis] inserted in between. Style annotations and placeholders are
     * remapped to the new offsets; those that fall entirely inside the removed region are dropped.
     *
     * For [StartEllipsis][androidx.compose.ui.text.style.TextOverflow.StartEllipsis] pass
     * `prefixEnd = 0`.
     */
    internal fun ellipsizedLayouter(
        prefixEnd: Int,
        suffixStart: Int,
        ellipsis: String,
    ): ParagraphLayouter {
        val ellipsisLength = ellipsis.length
        val keptText = buildString {
            append(text, 0, prefixEnd)
            append(ellipsis)
            append(text, suffixStart, text.length)
        }

        // Maps an offset in the original text to the corresponding offset in [keptText]. Offsets
        // inside the removed region collapse onto the ellipsis position.
        fun mapOffset(offset: Int): Int = when {
            offset <= prefixEnd -> offset
            offset >= suffixStart -> offset - suffixStart + prefixEnd + ellipsisLength
            else -> prefixEnd + ellipsisLength
        }

        val remappedAnnotations = annotations.mapNotNull { range ->
            val newStart = mapOffset(range.start)
            val newEnd = mapOffset(range.end)
            if (newEnd > newStart) range.copy(start = newStart, end = newEnd) else null
        }

        // A placeholder replaces its whole range with a single box, so it can only be kept if it
        // survives entirely within the visible prefix or suffix.
        val remappedPlaceholders = placeholders.mapNotNull { range ->
            if (range.end <= prefixEnd || range.start >= suffixStart) {
                range.copy(start = mapOffset(range.start), end = mapOffset(range.end))
            } else {
                null
            }
        }

        return ParagraphLayouter(
            text = keptText,
            textDirection = textDirection,
            style = style,
            annotations = remappedAnnotations,
            placeholders = remappedPlaceholders,
            density = density,
            fontFamilyResolver = fontFamilyResolver,
            onFontStale = { hasStaleResolvedFonts = true }
        )
    }

    override var minIntrinsicWidth = 0f
        private set
    override var maxIntrinsicWidth = 0f
        private set

    init {
        val para = layouter!!.layoutParagraph(Float.POSITIVE_INFINITY)
        minIntrinsicWidth = ceil(para.minIntrinsicWidth)
        maxIntrinsicWidth = ceil(para.maxIntrinsicWidth)
    }
}

internal fun resolveTextDirection(
    text: String,
    textDirection: TextDirection? = null,
    localeList: LocaleList? = null
): ResolvedTextDirection {
    return when (textDirection ?: TextDirection.Content) {
        TextDirection.Ltr -> ResolvedTextDirection.Ltr
        TextDirection.Rtl -> ResolvedTextDirection.Rtl
        TextDirection.Content, TextDirection.Unspecified -> {
            contentBasedTextDirection(text) { localeBasedTextDirection(localeList?.firstOrNull()) }
        }
        TextDirection.ContentOrLtr -> contentBasedTextDirection(text) { ResolvedTextDirection.Ltr }
        TextDirection.ContentOrRtl -> contentBasedTextDirection(text) { ResolvedTextDirection.Rtl }
        else -> error("Invalid TextDirection.")
    }
}

/**
 * Determine the paragraph direction by the first strong directional character. If no strong
 * character is found, fallback() will be called.
 *
 * This is the standard Unicode Bidirectional Algorithm (steps P2 and P3).
 * See https://www.unicode.org/reports/tr9/
 */
private fun contentBasedTextDirection(text: String, fallback: () -> ResolvedTextDirection) =
    when (text.firstStrongDirectionType()) {
        StrongDirectionType.Ltr -> ResolvedTextDirection.Ltr
        StrongDirectionType.Rtl -> ResolvedTextDirection.Rtl
        else -> fallback()
    }

private fun localeBasedTextDirection(locale: Locale?) =
    if ((locale ?: Locale.current).isRtl()) {
        ResolvedTextDirection.Rtl
    } else {
        ResolvedTextDirection.Ltr
    }
