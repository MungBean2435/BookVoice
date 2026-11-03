package io.github.mungbean2435.bookvoice.tts.rules

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 合成前的文本规整。这是唯一能治「省略号不念、数字读错、术语读错」的位置——
 * 供应商都不给发音控制，只能改输入。
 *
 * 只在送 TTS 的副本上改，正文显示不受影响。
 */
object RulesEngine {

    private val regexCache = ConcurrentHashMap<String, Regex>()

    /** 内置规则：只做保守清理，不改写语义。 */
    val builtin: RulePack = RulePack(
        id = "builtin",
        name = "内置清理",
        version = 1,
        enabled = true,
        rules = listOf(
            TtsRule("literal", "……", "，", "省略号不念"),
            TtsRule("literal", "——", "，", "破折号不念"),
            TtsRule("literal", "···", "，", "间隔号"),
            TtsRule("literal", "。。", "。", "重复句号"),
            TtsRule("literal", "，，", "，", "重复逗号"),
            TtsRule("regex", "(\\d+)\\s*%", "百分之$1", "百分号"),
            TtsRule("regex", "(\\d+)\\s*℃", "$1摄氏度", "摄氏度"),
            TtsRule("regex", "(\\d+)\\s*km", "$1公里", "公里"),
            TtsRule("regex", "(\\d+)\\s*kg", "$1公斤", "公斤"),
            TtsRule("regex", "\\s+", " ", "空白归一")
        )
    )

    fun normalize(text: String, packs: List<RulePack>): String {
        var out = text
        packs.filter { it.enabled }.forEach { pack ->
            pack.rules.forEach { rule ->
                out = try {
                    if (rule.type == "regex") {
                        regexOf(rule.from).replace(out, rule.to)
                    } else {
                        out.replace(rule.from, rule.to)
                    }
                } catch (_: Exception) {
                    out
                }
            }
        }
        return out
    }

    /** 规则变了，缓存 key 就要变，否则会播到旧规则生成的音频。 */
    fun signature(packs: List<RulePack>): String {
        val raw = packs.filter { it.enabled }
            .joinToString("|") { pack ->
                pack.id + ":" + pack.rules.joinToString(";") { "${it.type}>${it.from}>${it.to}" }
            }
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (byte in digest.take(4)) sb.append("%02x".format(byte))
        return sb.toString()
    }

    private fun regexOf(pattern: String): Regex =
        regexCache.getOrPut(pattern) { Regex(pattern) }
}
