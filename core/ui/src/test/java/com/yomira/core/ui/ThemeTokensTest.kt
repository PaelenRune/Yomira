package com.yomira.core.ui

import androidx.compose.ui.graphics.Color
import com.yomira.core.ui.theme.DeepOledBlack
import com.yomira.core.ui.theme.NeonFuchsia
import com.yomira.core.ui.theme.NeonPurple
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTokensTest {
    @Test fun `gallery uses OLED canvas and electric accents`() {
        assertEquals(Color.Black, DeepOledBlack)
        assertEquals(Color(0xFF7B61FF), NeonPurple)
        assertEquals(Color(0xFFFF3366), NeonFuchsia)
    }
}
