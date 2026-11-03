package io.github.mungbean2435.bookvoice.tts.minimax

/**
 * MiniMax 的 speech-01 旧音色。speech-2.6 / 2.8 会以 2054 "voice id not exist" 拒绝，
 * 目录里不再提供；用户旧配置里已有的话，UI 会标成「旧版」并提示重选。
 */
private val LEGACY_MINI_MAX_VOICES = setOf(
    "male-qn-qingse",
    "male-qn-jingying",
    "male-qn-badao",
    "male-qn-daxuesheng",
    "female-shaonv",
    "female-yujie",
    "female-chengshu",
    "female-tianmei",
    "male-qn-qingse-jingpin",
    "male-qn-jingying-jingpin",
    "male-qn-badao-jingpin",
    "male-qn-daxuesheng-jingpin",
    "female-shaonv-jingpin",
    "female-yujie-jingpin",
    "female-chengshu-jingpin",
    "female-tianmei-jingpin",
    "clever_boy",
    "cute_boy",
    "lovely_girl",
    "cartoon_pig",
    "bingjiao_didi",
    "junlang_nanyou",
    "chunzhen_xuedi",
    "lengdan_xiongzhang",
    "badao_shaoye",
    "tianxin_xiaoling",
    "qiaopi_mengmei",
    "wumei_yujie",
    "diadia_xuemei",
    "danya_xuejie"
)

fun isLegacyMiniMaxVoice(id: String): Boolean =
    id in LEGACY_MINI_MAX_VOICES || id.startsWith("female-") || id.startsWith("male-qn-")
