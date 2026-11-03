package io.github.mungbean2435.bookvoice.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(matchesSettingsQuery("", "TTS 配置", "MiniMax", "语音"))
        assertTrue(matchesSettingsQuery("   ", "TTS 配置", null))
    }

    @Test
    fun matchesTitleSubtitleAndKeywords() {
        assertTrue(matchesSettingsQuery("TTS", "TTS 配置", "MiniMax · 未填 Key", "语音 音色"))
        assertTrue(matchesSettingsQuery("音色", "TTS 配置", "MiniMax", "语音 音色"))
        assertTrue(matchesSettingsQuery("缓存", "存储与缓存", "已用 407 MB", "清理"))
        assertTrue(matchesSettingsQuery("清理", "存储与缓存", "已用 407 MB", "清理"))
    }

    @Test
    fun isCaseInsensitiveAndTrimsSpaces() {
        assertTrue(matchesSettingsQuery("  tts  ", "TTS 配置", null, ""))
        assertTrue(matchesSettingsQuery("JSON", "导入备份", "从 JSON 恢复", ""))
    }

    @Test
    fun noMatchReturnsFalse() {
        assertFalse(matchesSettingsQuery("不存在的设置", "播放设置", "语速 1.00×", "播放"))
        assertFalse(matchesSettingsQuery("蓝牙", "关于书声", "版本 2.5.0", "版本 许可"))
    }
}
