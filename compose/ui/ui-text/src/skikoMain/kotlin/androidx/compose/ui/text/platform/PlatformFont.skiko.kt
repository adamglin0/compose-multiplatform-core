/*
 * Copyright 2021 The Android Open Source Project
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

import org.jetbrains.skia.Typeface as SkTypeface
import androidx.compose.ui.text.Cache
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.ExpireAfterAccessCache
import androidx.compose.ui.text.font.DefaultFontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.GenericFontFamily
import androidx.compose.ui.text.font.LoadedFontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.FontMgrWithFallback
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.TypefaceFontProviderWithFallback

expect sealed class PlatformFont() : Font {
    abstract val identity: String
    abstract val variationSettings: FontVariation.Settings
    internal val cacheKey: String
}

/**
 * A Font that's already installed in the system.
 *
 * @param identity Unique identity for a font. Used internally to distinguish fonts.
 * @param weight The weight of the font. The system uses this to match a font to a font request
 * that is given in a [androidx.compose.ui.text.SpanStyle].
 * @param style The style of the font, normal or italic. The system uses this to match a font to a
 * font request that is given in a [androidx.compose.ui.text.SpanStyle].
 *
 * @see FontFamily
 */
@ExperimentalTextApi
class SystemFont(
    override val identity: String,
    override val weight: FontWeight = FontWeight.Normal,
    override val style: FontStyle = FontStyle.Normal,
    override val variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
) : PlatformFont() {
    override fun toString(): String {
        return "SystemFont(identity='$identity', weight=$weight, style=$style, variationSettings=${variationSettings.settings})"
    }
}

/**
 * Defines a Font using a byte array with loaded font data.
 *
 * @param identity Unique identity for a font. Used internally to distinguish fonts.
 * @param getData should return Byte array with loaded font data.
 * @param weight The weight of the font. The system uses this to match a font to a font request
 * that is given in a [androidx.compose.ui.text.SpanStyle].
 * @param style The style of the font, normal or italic. The system uses this to match a font to a
 * font request that is given in a [androidx.compose.ui.text.SpanStyle].
 *
 * @see FontFamily
 */
class LoadedFont internal constructor(
    override val identity: String,
    internal val getData: () -> ByteArray,
    override val weight: FontWeight,
    override val style: FontStyle,
    override val variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
) : PlatformFont() {
    @ExperimentalTextApi
    override val loadingStrategy: FontLoadingStrategy = FontLoadingStrategy.Blocking

    val data: ByteArray get() = getData()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LoadedFont) return false
        if (identity != other.identity) return false
        if (weight != other.weight) return false
        if (style != other.style) return false
        return variationSettings.settings == other.variationSettings.settings
    }

    override fun hashCode(): Int {
        var result = identity.hashCode()
        result = 31 * result + weight.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + variationSettings.settings.hashCode()
        return result
    }

    override fun toString(): String {
        return "LoadedFont(identity='$identity', weight=$weight, style=$style, variationSettings=${variationSettings.settings})"
    }
}

/**
 * Creates a Font using byte array with loaded font data.
 *
 * @param identity Unique identity for a font. Used internally to distinguish fonts.
 * @param getData should return Byte array with loaded font data.
 * @param weight The weight of the font. The system uses this to match a font to a font request
 * that is given in a [androidx.compose.ui.text.SpanStyle].
 * @param style The style of the font, normal or italic. The system uses this to match a font to a
 * font request that is given in a [androidx.compose.ui.text.SpanStyle].
 *
 * @see FontFamily
 */
fun Font(
    identity: String,
    getData: () -> ByteArray,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
): Font = LoadedFont(identity, getData, weight, style, FontVariation.Settings())

fun Font(
    identity: String,
    getData: () -> ByteArray,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
): Font = LoadedFont(identity, getData, weight, style, variationSettings)

private class SkiaBackedTypeface(
    alias: String?,
    val nativeTypeface: SkTypeface
) : Typeface {
    val alias = alias ?: nativeTypeface.familyName
    override val fontFamily: FontFamily? = null
}

/**
 * Creates a Font using byte array with loaded font data.
 *
 * @param identity Unique identity for a font. Used internally to distinguish fonts.
 * @param data Byte array with loaded font data.
 * @param weight The weight of the font. The system uses this to match a font to a font request
 * that is given in a [androidx.compose.ui.text.SpanStyle].
 * @param style The style of the font, normal or italic. The system uses this to match a font to a
 * font request that is given in a [androidx.compose.ui.text.SpanStyle].
 *
 * @see FontFamily
 */
fun Font(
    identity: String,
    data: ByteArray,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
): Font = Font(
    identity = identity,
    getData = { data },
    weight = weight,
    style = style,
    variationSettings = FontVariation.Settings(),
)

fun Font(
    identity: String,
    data: ByteArray,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
): Font = Font(
    identity = identity,
    getData = { data },
    weight = weight,
    style = style,
    variationSettings = variationSettings,
)
/**
 * Returns a Compose [Typeface] from Skia [SkTypeface].
 *
 * @param typeface Android Typeface instance
 */
fun Typeface(typeface: SkTypeface, alias: String? = null): Typeface {
    return SkiaBackedTypeface(alias, typeface)
}

@Suppress("DEPRECATION", "OverridingDeprecatedMember")
@Deprecated(
    "Replaced with PlatformFontLoader during the introduction of async fonts, all usages" +
        " should be replaced",
    ReplaceWith("PlatformFontLoader"),
)
class FontLoader : Font.ResourceLoader {

    private val fontCache: FontCache by lazy { FontCache() }
    internal val fontFamilyResolver: FontFamily.Resolver by lazy {
        createFontFamilyResolver(fontCache)
    }

    // TODO: we need to support:
    //  1. font collection (.ttc). Looks like skia currently doesn't have
    //  proper interfaces or they are broken (.makeFromFile(*, 1) always fails)
    //  2. variable fonts. for them we also need to extend definition interfaces to support
    //  custom variation settings
    @Deprecated(
        "Replaced by FontFamily.Resolver, this method should not be called",
        ReplaceWith("FontFamily.Resolver.resolve(font, )"),
    )
    override fun load(font: Font): SkTypeface {
        if (font !is PlatformFont) {
            throw IllegalArgumentException("Unsupported font type: $font")
        }
        return fontCache.load(font).typeface!!
    }
}

class FontLoadResult(val typeface: SkTypeface?, val aliases: List<String>)

internal class FontCache {
    internal val fonts = FontCollection()
    private val fontProvider = TypefaceFontProviderWithFallback()
    private val registered: MutableSet<String> = HashSet()
    private val typefacesCache: Cache<String, SkTypeface> = ExpireAfterAccessCache(
        60_000_000_000 // 1 minute
    )

    init {
        fonts.setDefaultFontManager(FontMgrWithFallback(fontProvider))
        fonts.setAssetFontManager(fontProvider)
    }

    internal fun load(font: PlatformFont): FontLoadResult {
        val typeface = typefacesCache.get(font.cacheKey) {
            loadTypeface(font)
        }
        ensureRegistered(typeface, font.cacheKey)
        return FontLoadResult(typeface, listOf(font.cacheKey))
    }

    internal fun loadPlatformTypes(
        fontFamily: FontFamily,
        fontWeight: FontWeight = FontWeight.Normal,
        fontStyle: FontStyle = FontStyle.Normal
    ): FontLoadResult {
        val aliases = ensureRegistered(fontFamily)
        val style = fontStyle.toSkFontStyle().withWeight(fontWeight.weight)
        return FontLoadResult(fonts.findTypefaces(aliases.toTypedArray(), style).first(), aliases)
    }

    private fun ensureRegistered(typeface: SkTypeface, key: String) {
        if (!registered.contains(key)) {
            fontProvider.registerTypeface(typeface, key)
            registered.add(key)
        }
    }

    private fun ensureRegistered(fontFamily: FontFamily): List<String> =
        when (fontFamily) {
            is FontListFontFamily -> {
                val fonts = fontFamily.fonts.filterIsInstance<SystemFont>()
                if (fonts.size == fontFamily.fonts.size) {
                    fonts.map { it.identity }
                } else {
                    // not supported
                    throw IllegalArgumentException(
                        "Don't load FontListFontFamily through ensureRegistered: $fontFamily"
                    )
                }
            }

            is LoadedFontFamily -> {
                val typeface = fontFamily.typeface as SkiaBackedTypeface
                ensureRegistered(typeface.nativeTypeface, typeface.alias)
                listOf(typeface.alias)
            }

            is GenericFontFamily -> fontFamily.aliases
            is DefaultFontFamily -> FontFamily.SansSerif.aliases
            else -> throw IllegalArgumentException("Unknown font family type: $fontFamily")
        }
}

internal enum class Platform {
    Unknown,
    Linux,
    Windows,
    MacOS,
    IOS,
    TvOS,
    WatchOS,
    Android, // use case: a web app running in Chrome Android
}

internal expect fun currentPlatform(): Platform
internal expect fun loadTypeface(font: Font): SkTypeface

internal val GenericFontFamily.aliases
    get() = GenericFontFamiliesMapping[name]
        ?: error("Unknown generic font family $name")

private val GenericFontFamiliesMapping: Map<String, List<String>> by lazy {
    when (currentPlatform()) {
        Platform.Linux ->
            mapOf(
                FontFamily.SansSerif.name to listOf("Noto Sans", "DejaVu Sans", "Arial"),
                FontFamily.Serif.name to listOf("Noto Serif", "DejaVu Serif", "Times New Roman"),
                FontFamily.Monospace.name to listOf("Noto Sans Mono", "DejaVu Sans Mono", "Consolas"),
                // better alternative?
                FontFamily.Cursive.name to listOf("Comic Sans MS")
            )
        Platform.Windows ->
            mapOf(
                // Segoe UI is the Windows system font, so try it first.
                // See https://learn.microsoft.com/en-us/windows/win32/uxguide/vis-fonts
                FontFamily.SansSerif.name to listOf("Segoe UI", "Arial"),
                FontFamily.Serif.name to listOf("Times New Roman"),
                FontFamily.Monospace.name to listOf("Consolas"),
                FontFamily.Cursive.name to listOf("Comic Sans MS")
            )
        Platform.MacOS, Platform.IOS, Platform.TvOS, Platform.WatchOS ->
            mapOf(
                // .AppleSystem* aliases is the only legal way to get default SF and NY fonts.
                FontFamily.SansSerif.name to listOf(".AppleSystemUIFont", "Helvetica Neue", "Helvetica"),
                FontFamily.Serif.name to listOf(".AppleSystemUIFontSerif", "Times", "Times New Roman"),
                FontFamily.Monospace.name to listOf(".AppleSystemUIFontMonospaced", "Menlo", "Courier"),
                // Safari "font-family: cursive" real font names from macOS and iOS.
                FontFamily.Cursive.name to listOf("Apple Chancery", "Snell Roundhand")
            )
        Platform.Android -> // https://m3.material.io/styles/typography/fonts
            mapOf(
                FontFamily.SansSerif.name to listOf("Roboto", "Noto Sans"),
                FontFamily.Serif.name to listOf("Roboto Serif", "Noto Serif"),
                FontFamily.Monospace.name to listOf("Roboto Mono", "Noto Sans Mono"),
                FontFamily.Cursive.name to listOf("Comic Sans MS")
            )
        Platform.Unknown ->
            mapOf(
                FontFamily.SansSerif.name to listOf("Arial"),
                FontFamily.Serif.name to listOf("Times New Roman"),
                FontFamily.Monospace.name to listOf("Consolas"),
                FontFamily.Cursive.name to listOf("Comic Sans MS")
            )
    }
}

internal fun FontVariation.Settings.toSikaFontVariationList(): List<org.jetbrains.skia.FontVariation> {
    return settings.map { setting ->
        org.jetbrains.skia.FontVariation(setting.axisName, setting.toVariationValue(null))
    }
}

/**
 * Bind the font variation settings to the Skia typeface.
 */
internal fun SkTypeface.bindVariantSettings(variationSettings: FontVariation.Settings): SkTypeface {
    if (variationSettings.settings.isEmpty()) return this
    val variations = variationSettings.toSikaFontVariationList()
    return makeClone(variations.toTypedArray())
}