package io.github.mungbean2435.bookvoice.tts.rules

import org.json.JSONArray
import org.json.JSONObject

data class TtsRule(
    val type: String,   // "literal" | "regex"
    val from: String,
    val to: String,
    val note: String = ""
)

data class RulePack(
    val id: String,
    val name: String,
    val version: Int,
    val enabled: Boolean,
    val rules: List<TtsRule>
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("version", version)
        put("rules", JSONArray().apply {
            rules.forEach { rule ->
                put(
                    JSONObject().apply {
                        put("type", rule.type)
                        put("from", rule.from)
                        put("to", rule.to)
                        put("note", rule.note)
                    }
                )
            }
        })
    }.toString(2)

    companion object {
        fun fromJson(text: String): RulePack {
            val json = JSONObject(text)
            val rules = ArrayList<TtsRule>()
            json.optJSONArray("rules")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val from = item.optString("from")
                    if (from.isEmpty()) continue
                    rules.add(
                        TtsRule(
                            type = item.optString("type", "literal"),
                            from = from,
                            to = item.optString("to"),
                            note = item.optString("note")
                        )
                    )
                }
            }
            return RulePack(
                id = json.optString("id").ifBlank { "imported" },
                name = json.optString("name").ifBlank { "导入的规则" },
                version = json.optInt("version", 1),
                enabled = true,
                rules = rules
            )
        }
    }
}
