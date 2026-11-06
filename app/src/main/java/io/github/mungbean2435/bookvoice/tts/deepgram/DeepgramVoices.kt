package io.github.mungbean2435.bookvoice.tts.deepgram

import io.github.mungbean2435.bookvoice.tts.Voice
import org.json.JSONObject

/**
 * Deepgram 的内置音色目录。
 *
 * Deepgram 的 `model` 查询参数本身就包含音色和语言（`flux-{voice}-{lang}` /
 * `aura-{voice}-{lang}`），所以这里把完整 model 字符串当作音色 ID；
 * 配置里的 `model` 字段只用来显示引擎系列（Flux / Aura）。
 */

internal const val DEEPGRAM_MODEL_FLUX = "Flux TTS"
internal const val DEEPGRAM_MODEL_AURA2 = "Aura 2"
internal const val DEEPGRAM_MODEL_AURA = "Aura"

/** 默认音色：Flux 中文女声。 */
internal const val DEEPGRAM_DEFAULT_VOICE = "flux-wenjing-zh"

internal val DEEPGRAM_MODELS = listOf(
    DEEPGRAM_MODEL_FLUX,
    DEEPGRAM_MODEL_AURA2,
    DEEPGRAM_MODEL_AURA
)

/**
 * 中文优先：Flux 的普通话 / 台湾国语 / 粤语音色排在最前，
 * 后面是官方 featured 的英文 Flux 与常用 Aura 音色。
 * 在线目录会补齐其余语言和音色。
 */
internal val DEEPGRAM_BUILTIN_VOICES: List<Voice> = listOf(
    // Flux · 普通话（Early Access，没有文本规整）
    Voice("flux-wenjing-zh", "文静 Wenjing", "中文 · 女声"),
    Voice("flux-lijuan-zh", "丽娟 Lijuan", "中文 · 女声"),
    Voice("flux-shufen-zh", "淑芬 Shufen", "中文 · 女声"),
    Voice("flux-zhen-zh", "珍 Zhen", "中文 · 女声"),
    Voice("flux-bo-zh", "博 Bo", "中文 · 男声"),
    Voice("flux-haoran-zh", "浩然 Haoran", "中文 · 男声"),
    Voice("flux-jian-zh", "健 Jian", "中文 · 男声"),
    Voice("flux-jie-zh", "杰 Jie", "中文 · 男声"),
    Voice("flux-wenbo-zh", "文博 Wenbo", "中文 · 男声"),
    // Flux · 台湾国语
    Voice("flux-chiaying-zh", "佳颖 Chiaying", "中文 · 台湾 · 女声"),
    Voice("flux-yawen-zh", "雅文 Yawen", "中文 · 台湾 · 女声"),
    Voice("flux-yuting-zh", "雨婷 Yuting", "中文 · 台湾 · 女声"),
    Voice("flux-kuanyu-zh", "冠宇 Kuanyu", "中文 · 台湾 · 男声"),
    // Flux · 粤语
    Voice("flux-manlok-zh", "文洛 Manlok", "中文 · 粤语 · 男声"),

    // Flux · 英文 featured
    Voice("flux-hannah-en", "汉娜 Hannah", "英文 · 女声"),
    Voice("flux-kit-en", "基特 Kit", "英文 · 男声"),
    Voice("flux-alexis-en", "亚历克西斯 Alexis", "英文 · 女声"),
    Voice("flux-cliff-en", "克利夫 Cliff", "英文 · 男声"),
    Voice("flux-sienna-en", "西耶娜 Sienna", "英文 · 女声"),
    Voice("flux-cole-en", "科尔 Cole", "英文 · 男声"),
    Voice("flux-brooke-en", "布鲁克 Brooke", "英文 · 女声"),
    Voice("flux-colin-en", "科林 Colin", "英文 · 男声"),
    Voice("flux-gemma-en", "杰玛 Gemma", "英文 · 女声"),
    Voice("flux-haley-en", "海莉 Haley", "英文 · 女声"),
    Voice("flux-heather-en", "希瑟 Heather", "英文 · 女声"),
    Voice("flux-miles-en", "迈尔斯 Miles", "英文 · 男声"),
    Voice("flux-sean-en", "肖恩 Sean", "英文 · 男声"),

    // Aura-2 · 英文 featured
    Voice("aura-2-thalia-en", "塔莉亚 Thalia", "英文 · 女声"),
    Voice("aura-2-andromeda-en", "安德洛墨达 Andromeda", "英文 · 女声"),
    Voice("aura-2-helena-en", "海伦娜 Helena", "英文 · 女声"),
    Voice("aura-2-apollo-en", "阿波罗 Apollo", "英文 · 男声"),
    Voice("aura-2-arcas-en", "阿卡斯 Arcas", "英文 · 男声"),
    Voice("aura-2-aries-en", "阿里斯 Aries", "英文 · 男声"),

    // Aura-1 · 英文
    Voice("aura-asteria-en", "阿斯特里亚 Asteria", "英文 · 女声"),
    Voice("aura-luna-en", "露娜 Luna", "英文 · 女声"),
    Voice("aura-stella-en", "斯特拉 Stella", "英文 · 女声"),
    Voice("aura-athena-en", "雅典娜 Athena", "英文 · 女声"),
    Voice("aura-hera-en", "赫拉 Hera", "英文 · 女声"),
    Voice("aura-orion-en", "俄里翁 Orion", "英文 · 男声"),
    Voice("aura-arcas-en", "阿卡斯 Arcas", "英文 · 男声"),
    Voice("aura-perseus-en", "珀尔修斯 Perseus", "英文 · 男声"),
    Voice("aura-angus-en", "安格斯 Angus", "英文 · 爱尔兰 · 男声"),
    Voice("aura-orpheus-en", "俄耳甫斯 Orpheus", "英文 · 男声"),
    Voice("aura-helios-en", "赫利俄斯 Helios", "英文 · 男声"),
    Voice("aura-zeus-en", "宙斯 Zeus", "英文 · 男声")
)

private val DEEPGRAM_BUILTIN_BY_ID: Map<String, Voice> =
    DEEPGRAM_BUILTIN_VOICES.associateBy { it.id }

internal fun deepgramIsModel(id: String): Boolean {
    val value = id.trim()
    return value.startsWith("flux-") || value.startsWith("aura-")
}

/** `/v1/models` 里的 tts 数组：只取 Flux / Aura 的 canonical_name。 */
internal fun parseDeepgramTtsModels(body: String): List<String> = runCatching {
    val tts = JSONObject(body).optJSONArray("tts") ?: return@runCatching emptyList()
    (0 until tts.length()).mapNotNull { index ->
        val item = tts.optJSONObject(index) ?: return@mapNotNull null
        val name = item.optString("canonical_name").trim().ifBlank {
            item.optString("name").trim()
        }
        name.takeIf { deepgramIsModel(it) }
    }.distinct()
}.getOrDefault(emptyList())

/** 把在线目录里的 model id 变成可读的音色卡；内置目录里有的直接复用。 */
internal fun deepgramVoiceFor(id: String): Voice =
    DEEPGRAM_BUILTIN_BY_ID[id] ?: Voice(
        id = id,
        label = deepgramPrettyName(id),
        hint = deepgramLanguageHint(id)
    )

private fun deepgramPrettyName(id: String): String {
    val core = id
        .removePrefix("flux-")
        .removePrefix("aura-2-")
        .removePrefix("aura-")
        .substringBeforeLast('-')
        .replace('-', ' ')
    return core.replaceFirstChar { it.uppercase() }.ifBlank { id }
}

private fun deepgramLanguageHint(id: String): String {
    val language = id.substringAfterLast('-', "")
    return when (language) {
        "zh" -> "中文"
        "en" -> "英文"
        "ja" -> "日文"
        "es" -> "西班牙语"
        "de" -> "德语"
        "fr" -> "法语"
        "nl" -> "荷兰语"
        "it" -> "意大利语"
        "pt" -> "葡萄牙语"
        "ru" -> "俄语"
        "hi" -> "印地语"
        "ko" -> "韩语"
        else -> language.uppercase().ifBlank { "多语种" }
    }
}
