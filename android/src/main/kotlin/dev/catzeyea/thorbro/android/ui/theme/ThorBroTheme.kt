package dev.catzeyea.thorbro.android.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.catzeyea.thorbro.android.R

// The ThorBro look (same tokens as the PC app). Screens and components take every color, corner,
// shadow and text style from this file, so the look can be changed in one place.
// Reference: docs/design/thorbro-android-design/DESIGN-ANDROID.md

/** Color tokens. */
object ThorColors {
    val Bg = Color(0xFF1A1E1F)
    val Panel = Color(0xFF252A2A)
    val PanelSelected = Color(0xFF2B312E)
    val Ink = Color(0xFFEFE7D2)
    /** Default outline of panels and secondary buttons. */
    val InkLine = Ink.copy(alpha = 0.78f)
    /** Quieter outline (small buttons, the L1/R1 chips in the header). */
    val InkSoft = Ink.copy(alpha = 0.5f)
    /** Divider lines between rows of a panel. */
    val InkHairline = Ink.copy(alpha = 0.2f)
    val Mute = Color(0xFFB4B6AA)
    /** Body copy, a bit brighter than [Mute]. */
    val Body = Color(0xFFC4C6BA)
    val Sage = Color(0xFFA5B98C)
    val SageShadow = Color(0xFF4A5C38)
    val Orange = Color(0xFFF0A94B)
    val OrangeShadow = Color(0xFF7A4A0F)
    val Danger = Color(0xFFFF9C84)
    val Shadow = Color(0xFF0C0F0F)
    /** Text on sage or orange fills. */
    val OnAccent = Color(0xFF1A1E1F)
    /** The focus ring. */
    val Focus = Ink
}

/**
 * The reference design is drawn on a 1280x720 landscape canvas. Sizes from it are "design units" (du).
 * [DesignScale] turns them into dp for the actual window, so the layout keeps its proportions on the
 * Thor (about 768x432 dp at the default density) and on other screens.
 */
object DesignScale {
    const val DESIGN_WIDTH = 1280f
    const val DESIGN_HEIGHT = 720f
    const val MIN = 0.5f
    const val MAX = 1.25f

    /** dp per design unit for a window of [widthDp] x [heightDp]. Pure function, unit-testable. */
    fun forWindow(widthDp: Float, heightDp: Float): Float =
        minOf(widthDp / DESIGN_WIDTH, heightDp / DESIGN_HEIGHT).coerceIn(MIN, MAX)
}

private val LocalDesignScale = staticCompositionLocalOf { 0.6f }

/** A size from the reference design, converted to dp for this window. */
val Number.du: Dp
    @Composable @ReadOnlyComposable
    get() = (toFloat() * LocalDesignScale.current).dp

/** A font size from the reference design, converted to sp (so the user's font size setting still applies). */
val Number.dsp: TextUnit
    @Composable @ReadOnlyComposable
    get() = (toFloat() * LocalDesignScale.current).sp

/** Fixed sizes that must not shrink with the design scale (accessibility minimums, focus ring). */
object ThorDimens {
    /** Minimum height of anything that can be touched or focused. */
    val MinTarget = 48.dp
    /** Minimum height of the main action buttons (Connect to PC, Play on Thor). */
    val MinMainAction = 64.dp
    val Outline = 2.dp
    val FocusRingWidth = 4.dp
    val FocusRingOffset = 4.dp
    /** Room that scrolling containers keep around their content, so focus rings and shadows are not cut off. */
    val ScrollBleed = FocusRingWidth + FocusRingOffset + 4.dp
}

/**
 * Corner radii in design units, in CSS order: top-left, top-right, bottom-right, bottom-left.
 * The design gives every element slightly different corners ("hand-drawn" look).
 *
 * The reference also uses elliptical corners (CSS `a b c d / e f g h`). Compose's RoundedCornerShape
 * only has circular corners, so only the horizontal radii are kept.
 */
@Immutable
data class Corners(val topLeft: Int, val topRight: Int, val bottomRight: Int, val bottomLeft: Int) {
    constructor(all: Int) : this(all, all, all, all)

    @Composable
    @ReadOnlyComposable
    fun shape(): Shape = RoundedCornerShape(
        topStart = topLeft.du,
        topEnd = topRight.du,
        bottomEnd = bottomRight.du,
        bottomStart = bottomLeft.du,
    )
}

/** A hard drop shadow: no blur, just the shape again, offset and filled with [color]. Offsets in design units. */
@Immutable
data class HardShadow(val x: Int, val y: Int, val color: Color = ThorColors.Shadow)

/** Corner sets and shadows used by the components. Values from the reference HTML files. */
object ThorShapes {
    val PcCard = Corners(24, 18, 26, 16)
    val StepCardQuiet = Corners(18, 24, 16, 26)
    val StepCardMain = Corners(26, 18, 24, 16)
    val GamesList = Corners(22, 18, 24, 16)
    val SelectedGameCard = Corners(26, 18, 24, 16)
    val SetupCards = listOf(
        Corners(22, 18, 24, 16),
        Corners(18, 24, 16, 22),
        Corners(24, 16, 20, 22),
        Corners(16, 22, 24, 18),
    )

    val TabActive = Corners(22, 16, 24, 15)
    val TabIdle = Corners(16)
    val ChipLeft = Corners(9, 7, 10, 7)
    val ChipRight = Corners(7, 10, 7, 9)
    val StatusChip = Corners(14, 10, 15, 11)

    val ButtonMain = Corners(22, 26, 20, 28)
    val ButtonSecondary = Corners(16, 13, 17, 12)
    val ButtonSmall = Corners(14, 11, 15, 12)
    val GameRow = Corners(14, 11, 15, 12)

    val Circle: Shape = CircleShape

    val PcCardShadow = HardShadow(5, 6)
    val StepQuietShadow = HardShadow(4, 5)
    val MainCardShadow = HardShadow(6, 6)
    val SetupShadows = listOf(HardShadow(5, 5), HardShadow(4, 6), HardShadow(6, 4), HardShadow(5, 6))
    val OrangeButtonShadow = HardShadow(4, 4, ThorColors.OrangeShadow)
    val ActiveTabShadow = HardShadow(3, 3, ThorColors.OrangeShadow)
    val SageButtonShadow = HardShadow(4, 4, ThorColors.SageShadow)

    /** Tilt of the active tab, in degrees. */
    const val ACTIVE_TAB_ROTATION = -1f
}

/** The only font family of the app, bundled in res/font (no downloadable fonts: the app has no INTERNET permission). */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Text styles, sizes in design units. Line height is optional, as a multiple of the font size. */
object ThorType {
    @Composable
    @ReadOnlyComposable
    fun style(
        size: Int,
        weight: FontWeight = FontWeight.Normal,
        color: Color = Color.Unspecified,
        lineHeight: Float? = null,
        letterSpacingEm: Float = 0f,
    ): TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = weight,
        fontSize = size.dsp,
        color = color,
        lineHeight = lineHeight?.let { (size * it).dsp } ?: TextUnit.Unspecified,
        letterSpacing = letterSpacingEm.em,
    )

    // Named styles shared by several screens.
    @Composable @ReadOnlyComposable fun brand() = style(26, FontWeight.Bold, letterSpacingEm = -0.01f)
    @Composable @ReadOnlyComposable fun label() = style(14, color = ThorColors.Mute)
    @Composable @ReadOnlyComposable fun body() = style(16, color = ThorColors.Body, lineHeight = 1.4f)
    @Composable @ReadOnlyComposable fun small() = style(14, color = ThorColors.Mute, lineHeight = 1.5f)
    @Composable @ReadOnlyComposable fun cardTitle() = style(24, FontWeight.Bold, lineHeight = 1.2f)
    @Composable @ReadOnlyComposable fun bigTitle() = style(34, FontWeight.Bold, lineHeight = 1.1f)
    @Composable @ReadOnlyComposable fun hint() = style(15, color = ThorColors.Mute)
}

private fun thorTypography(): Typography {
    val base = TextStyle(fontFamily = JetBrainsMono, color = ThorColors.Ink)
    val defaults = Typography()
    return Typography(
        displayLarge = defaults.displayLarge.merge(base),
        displayMedium = defaults.displayMedium.merge(base),
        displaySmall = defaults.displaySmall.merge(base),
        headlineLarge = defaults.headlineLarge.merge(base),
        headlineMedium = defaults.headlineMedium.merge(base),
        headlineSmall = defaults.headlineSmall.merge(base),
        titleLarge = defaults.titleLarge.merge(base),
        titleMedium = defaults.titleMedium.merge(base),
        titleSmall = defaults.titleSmall.merge(base),
        bodyLarge = defaults.bodyLarge.merge(base),
        bodyMedium = defaults.bodyMedium.merge(base),
        bodySmall = defaults.bodySmall.merge(base),
        labelLarge = defaults.labelLarge.merge(base),
        labelMedium = defaults.labelMedium.merge(base),
        labelSmall = defaults.labelSmall.merge(base),
    )
}

private fun thorColorScheme() = darkColorScheme(
    background = ThorColors.Bg,
    surface = ThorColors.Panel,
    onBackground = ThorColors.Ink,
    onSurface = ThorColors.Ink,
    primary = ThorColors.Orange,
    onPrimary = ThorColors.OnAccent,
    error = ThorColors.Danger,
)

/**
 * Applies the ThorBro look: dark colors, JetBrains Mono everywhere, and the design scale for this window.
 * Material3 is only used for Text; its typography is set to JetBrains Mono as well so nothing falls back
 * to the system font.
 */
@Composable
fun ThorBroTheme(content: @Composable () -> Unit) {
    BoxWithConstraints {
        val scale = DesignScale.forWindow(maxWidth.value, maxHeight.value)
        // Created once: a new color scheme on every recomposition (e.g. every tab switch) would make
        // MaterialTheme's static composition local change, which recomposes the whole UI.
        val typography = remember { thorTypography() }
        val colors = remember { thorColorScheme() }
        MaterialTheme(colorScheme = colors, typography = typography) {
            CompositionLocalProvider(
                LocalDesignScale provides scale,
                LocalContentColor provides ThorColors.Ink,
            ) {
                ProvideTextStyle(ThorType.style(16, color = ThorColors.Ink), content)
            }
        }
    }
}
