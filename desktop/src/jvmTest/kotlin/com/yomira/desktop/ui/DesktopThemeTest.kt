package com.yomira.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the v5 palette contract from the design brief. The desktop shell must
 * stay on the OLED near-black + violet ramp; a stray legacy accent (the old
 * `#06050C` canvas or `#FF007F` hot pink) would silently break the visual
 * language across every surface, so it is asserted here instead of reviewed by
 * eye on each render.
 */
class DesktopThemeTest {

    private fun hex(color: Color): String =
        "#" + listOf(color.red, color.green, color.blue)
            .joinToString("") { channel ->
                ((channel * 255f).toInt().coerceIn(0, 255)).toString(16).padStart(2, '0')
            }
            .uppercase()

    @Test
    fun `canvas and surfaces use the OLED token ramp`() {
        assertEquals("#090A0E", hex(YomiraColors.Oled))
        assertEquals("#13141C", hex(YomiraColors.Surface))
        assertEquals("#1B1C26", hex(YomiraColors.SurfaceRaised))
        assertEquals("#090A0E", hex(Canvas))
        assertEquals("#13141C", hex(Panel))
    }

    @Test
    fun `accents stay on the violet to lavender ramp`() {
        assertEquals("#9D4EDD", hex(YomiraColors.Purple))
        assertEquals("#C084FC", hex(YomiraColors.Lavender))
        assertEquals("#9D4EDD", hex(Violet))
        assertEquals("#C084FC", hex(Lavender))
    }

    @Test
    fun `legacy hot pink and washed canvas are gone`() {
        val banned = listOf("#FF007F", "#E04BFF", "#06050C", "#050409")
        val palette = listOf(
            YomiraColors.Oled,
            YomiraColors.OledRaised,
            YomiraColors.Surface,
            YomiraColors.SurfaceRaised,
            YomiraColors.SurfaceHighest,
            YomiraColors.Purple,
            YomiraColors.Lavender,
            YomiraColors.Violet,
            YomiraColors.Indigo,
            YomiraColors.ReaderCanvas,
        ).map(::hex)
        banned.forEach { color -> assertEquals(-1, palette.indexOf(color)) }
    }

    @Test
    fun `card geometry is the 16dp poster language`() {
        assertEquals(16.dp, CardCorner)
        assertEquals(20.dp, GridGutter)
        assertEquals(30.dp, GridRowGap)
        assertEquals(32.dp, ShellPadding)
    }
}
