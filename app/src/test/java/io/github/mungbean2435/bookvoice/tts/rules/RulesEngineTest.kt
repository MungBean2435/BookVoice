package io.github.mungbean2435.bookvoice.tts.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesEngineTest {

    @Test
    fun builtinCleansEllipsisAndDashes() {
        val text = "他走了……然后又回来——最后什么也没说。"
        val result = RulesEngine.normalize(text, listOf(RulesEngine.builtin))

        assertTrue(result.startsWith("他走了，"))
        assertTrue(result.contains("，然后又回来，"))
    }

    @Test
    fun builtinConvertsPercentAndTemperature() {
        val text = "湿度 80% ，气温 25℃"
        val result = RulesEngine.normalize(text, listOf(RulesEngine.builtin))

        assertTrue(result.contains("百分之80"))
        assertTrue(result.contains("25摄氏度"))
    }

    @Test
    fun disabledPackIsIgnored() {
        val pack = RulePack(
            id = "custom",
            name = "自定义",
            version = 1,
            enabled = false,
            rules = listOf(TtsRule("literal", "银行", "银航"))
        )

        val result = RulesEngine.normalize("银行", listOf(pack))

        assertEquals("银行", result)
    }

    @Test
    fun enabledPackApplies() {
        val pack = RulePack(
            id = "custom",
            name = "自定义",
            version = 1,
            enabled = true,
            rules = listOf(TtsRule("regex", "(\\d+)岁", "$1 周岁"))
        )

        val result = RulesEngine.normalize("他今年 30岁", listOf(pack))

        assertEquals("他今年 30 周岁", result)
    }

    @Test
    fun invalidRegexDoesNotCrash() {
        val pack = RulePack(
            id = "bad",
            name = "坏规则",
            version = 1,
            enabled = true,
            rules = listOf(TtsRule("regex", "([", "x"))
        )

        val result = RulesEngine.normalize("正常文本", listOf(pack))

        assertEquals("正常文本", result)
    }

    @Test
    fun signatureChangesWhenRulesChange() {
        val a = listOf(RulesEngine.builtin)
        val b = listOf(
            RulesEngine.builtin,
            RulePack("x", "额外", 1, true, listOf(TtsRule("literal", "1", "一")))
        )

        assertNotEquals(RulesEngine.signature(a), RulesEngine.signature(b))
        assertEquals(RulesEngine.signature(a), RulesEngine.signature(listOf(RulesEngine.builtin)))
    }
}
