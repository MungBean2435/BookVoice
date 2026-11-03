package io.github.mungbean2435.bookvoice.data.backup

import android.content.Context
import android.net.Uri
import io.github.mungbean2435.bookvoice.data.db.Bookmark
import io.github.mungbean2435.bookvoice.data.db.Db
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.data.settings.ThemeMode
import io.github.mungbean2435.bookvoice.data.settings.TtsConfig
import io.github.mungbean2435.bookvoice.data.settings.TtsConfigJson
import io.github.mungbean2435.bookvoice.data.settings.TtsConfigs
import io.github.mungbean2435.bookvoice.tts.ProviderRegistry
import io.github.mungbean2435.bookvoice.tts.Voice
import io.github.mungbean2435.bookvoice.tts.rules.RulePack
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 备份只含「进度 / 书签 / 规则包 / 设置」，不含书文件，也不含 Key。
 * 导入时只恢复本机已有书籍的进度，避免造出一堆打不开的空书。
 */
class BackupManager(private val context: Context) {

    fun export(uri: Uri): String {
        val db = Db.get()
        val root = JSONObject()
        root.put("app", "bookvoice")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val books = JSONArray()
        val marks = JSONArray()
        db.books().forEach { book ->
            books.put(
                JSONObject().apply {
                    put("id", book.id)
                    put("title", book.title)
                    put("lastChapterIdx", book.lastChapterIdx)
                    put("lastBlockIdx", book.lastBlockIdx)
                    put("lastOffsetMs", book.lastOffsetMs)
                }
            )
            db.bookmarks(book.id).forEach { mark ->
                marks.put(
                    JSONObject().apply {
                        put("id", mark.id)
                        put("bookId", mark.bookId)
                        put("chapterIdx", mark.chapterIdx)
                        put("blockIdx", mark.blockIdx)
                        put("offsetMs", mark.offsetMs)
                        put("note", mark.note.orEmpty())
                        put("createdAt", mark.createdAt)
                    }
                )
            }
        }
        root.put("books", books)
        root.put("bookmarks", marks)

        val packs = JSONArray()
        db.rulePacks().forEach { packs.put(JSONObject(it.toJson())) }
        root.put("rulePacks", packs)

        val settings = Settings.state.value
        val active = settings.activeConfigOrNull()
        root.put(
            "settings",
            JSONObject().apply {
                put("activeConfigId", active?.id.orEmpty())
                put("configs", JSONArray().apply {
                    settings.configs.forEach { config ->
                        // 备份不含 Key，只存 id/名称/供应商/端点/选中值/模型与音色库
                        put(TtsConfigJson.encode(config, includeApiKey = false))
                    }
                })
                // 旧版本兼容镜像；同样不含 Key
                put("activeProvider", active?.providerId.orEmpty())
                put("providers", JSONObject().apply {
                    settings.configs.groupBy { it.providerId }.forEach { (providerId, list) ->
                        val first = list.first()
                        put(providerId, JSONObject().apply {
                            put("baseUrl", first.baseUrl)
                            put("model", first.model)
                            put("voice", first.voice)
                        })
                    }
                })
                put("speed", settings.speed.toDouble())
                put("gapSentenceMs", settings.gapSentenceMs)
                put("gapParagraphMs", settings.gapParagraphMs)
                put("cacheLimitMb", settings.cacheLimitMb)
                put("builtinRulesEnabled", settings.builtinRulesEnabled)
                put("fontSizeSp", settings.fontSizeSp)
                put("tapToRead", settings.tapToRead)
                put("themeMode", settings.themeMode.prefValue)
            }
        )

        context.contentResolver.openOutputStream(uri)?.use {
            it.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("无法写入所选文件")

        return "已导出 ${books.length()} 本书的进度、${marks.length()} 个书签"
    }

    fun import(uri: Uri): String {
        val text = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("无法读取文件")
        val root = JSONObject(text)
        // 兼容旧版「听书」导出的备份
        require(root.optString("app") in setOf("bookvoice", "tingshu")) { "这不是书声的备份文件" }

        val db = Db.get()
        var updated = 0
        var skipped = 0
        val books = root.optJSONArray("books") ?: JSONArray()
        for (i in 0 until books.length()) {
            val item = books.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isBlank() || db.book(id) == null) {
                skipped++
                continue
            }
            db.updateProgress(
                id,
                item.optInt("lastChapterIdx"),
                item.optInt("lastBlockIdx"),
                item.optLong("lastOffsetMs")
            )
            updated++
        }

        var importedMarks = 0
        val marks = root.optJSONArray("bookmarks") ?: JSONArray()
        for (i in 0 until marks.length()) {
            val item = marks.optJSONObject(i) ?: continue
            val bookId = item.optString("bookId")
            if (bookId.isBlank() || db.book(bookId) == null) continue
            db.addBookmark(
                Bookmark(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    bookId = bookId,
                    chapterIdx = item.optInt("chapterIdx"),
                    blockIdx = item.optInt("blockIdx"),
                    offsetMs = item.optLong("offsetMs"),
                    note = item.optString("note").takeIf { it.isNotBlank() },
                    createdAt = item.optLong("createdAt", System.currentTimeMillis())
                )
            )
            importedMarks++
        }

        var packs = 0
        val ruleArray = root.optJSONArray("rulePacks") ?: JSONArray()
        for (i in 0 until ruleArray.length()) {
            val item = ruleArray.optJSONObject(i) ?: continue
            runCatching { db.upsertRulePack(RulePack.fromJson(item.toString())) }
                .onSuccess { packs++ }
        }

        root.optJSONObject("settings")?.let { saved ->
            Settings.update { old ->
                var configs = old.configs
                var activeId = old.activeConfigId

                val savedConfigs = saved.optJSONArray("configs")
                if (savedConfigs != null) {
                    configs = mergeConfigs(old.configs, savedConfigs)
                    val savedActive = saved.optString("activeConfigId")
                    if (savedActive.isNotBlank() && configs.any { it.id == savedActive }) {
                        activeId = savedActive
                    }
                } else {
                    saved.optJSONObject("providers")?.let { providers ->
                        configs = mergeLegacyProviders(old.configs, providers)
                        val activeProvider = saved.optString("activeProvider")
                        if (activeProvider.isNotBlank()) {
                            configs.firstOrNull { it.providerId == activeProvider }?.let {
                                activeId = it.id
                            }
                        }
                    }
                }

                old.copy(
                    configs = configs,
                    activeConfigId = activeId,
                    speed = saved.optDouble("speed", old.speed.toDouble()).toFloat(),
                    gapSentenceMs = saved.optInt("gapSentenceMs", old.gapSentenceMs),
                    gapParagraphMs = saved.optInt("gapParagraphMs", old.gapParagraphMs),
                    cacheLimitMb = saved.optInt("cacheLimitMb", old.cacheLimitMb),
                    builtinRulesEnabled = saved.optBoolean(
                        "builtinRulesEnabled",
                        old.builtinRulesEnabled
                    ),
                    fontSizeSp = saved.optInt("fontSizeSp", old.fontSizeSp),
                    tapToRead = saved.optBoolean("tapToRead", old.tapToRead),
                    themeMode = ThemeMode.from(
                        saved.optString("themeMode", old.themeMode.prefValue)
                    )
                )
            }
        }

        val suffix = if (skipped > 0) "（$skipped 本书本机没有，已跳过）" else ""
        return "已恢复 $updated 本书进度、$importedMarks 个书签、$packs 个规则包$suffix"
    }

    /** 新备份：按 id 合并；本地已有的 Key 保留，其余字段和模型/音色库取并集。 */
    private fun mergeConfigs(local: List<TtsConfig>, saved: JSONArray): List<TtsConfig> {
        val out = local.toMutableList()
        for (i in 0 until saved.length()) {
            val item = saved.optJSONObject(i) ?: continue
            val providerId = item.optString("providerId")
            if (providerId.isBlank()) continue
            val imported = TtsConfigJson.decode(item)
            val id = imported.id.ifBlank { UUID.randomUUID().toString() }
            val knownVoices = ProviderRegistry.get(providerId)?.voices().orEmpty()
            val index = out.indexOfFirst { it.id == id }
            if (index >= 0) {
                val existing = out[index]
                out[index] = TtsConfigs.normalizeLibrary(
                    existing.copy(
                        name = imported.name.ifBlank { existing.name },
                        providerId = providerId,
                        baseUrl = imported.baseUrl.ifBlank { existing.baseUrl },
                        model = imported.model.ifBlank { existing.model },
                        voice = imported.voice.ifBlank { existing.voice },
                        models = (existing.models + imported.models).distinct(),
                        voices = mergeVoices(existing.voices, imported.voices)
                    ),
                    knownVoices
                )
            } else {
                out.add(
                    TtsConfigs.normalizeLibrary(
                        imported.copy(id = id, apiKey = ""),
                        knownVoices
                    )
                )
            }
        }
        return out
    }

    private fun mergeVoices(local: List<Voice>, saved: List<Voice>): List<Voice> {
        val out = LinkedHashMap<String, Voice>()
        local.forEach { voice -> if (voice.id.isNotBlank()) out.putIfAbsent(voice.id, voice) }
        saved.forEach { voice ->
            if (voice.id.isBlank()) return@forEach
            val existing = out[voice.id]
            if (existing == null) {
                out[voice.id] = voice
            } else {
                out[voice.id] = existing.copy(
                    label = voice.label.ifBlank { existing.label },
                    hint = voice.hint.ifBlank { existing.hint }
                )
            }
        }
        return out.values.toList()
    }

    /** 2.0.x 旧备份：只有 providers（每家一份），更新该供应商的第一条配置，没有就新建。 */
    private fun mergeLegacyProviders(local: List<TtsConfig>, saved: JSONObject): List<TtsConfig> {
        val out = local.toMutableList()
        saved.keys().forEach { providerId ->
            val item = saved.optJSONObject(providerId) ?: return@forEach
            val index = out.indexOfFirst { it.providerId == providerId }
            if (index >= 0) {
                val existing = out[index]
                out[index] = existing.copy(
                    baseUrl = item.optString("baseUrl", existing.baseUrl),
                    model = item.optString("model", existing.model),
                    voice = item.optString("voice", existing.voice)
                )
            } else {
                val provider = ProviderRegistry.get(providerId) ?: return@forEach
                out.add(
                    TtsConfig(
                        id = providerId,
                        name = provider.displayName,
                        providerId = providerId,
                        baseUrl = item.optString("baseUrl", provider.defaultConfig.baseUrl),
                        model = item.optString("model", provider.defaultConfig.model),
                        voice = item.optString("voice", provider.defaultConfig.voice)
                    )
                )
            }
        }
        return out
    }

    fun importRulePack(uri: Uri): String {
        val text = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("无法读取文件")
        val pack = RulePack.fromJson(text)
        require(pack.rules.isNotEmpty()) { "规则包里没有可用规则" }
        Db.get().upsertRulePack(pack)
        return "已导入「${pack.name}」：${pack.rules.size} 条规则"
    }
}
