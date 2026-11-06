package io.github.mungbean2435.bookvoice.tts.system

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice as AndroidVoice
import android.util.Log
import io.github.mungbean2435.bookvoice.tts.ProviderConfig
import io.github.mungbean2435.bookvoice.tts.SynthRequest
import io.github.mungbean2435.bookvoice.tts.SynthResult
import io.github.mungbean2435.bookvoice.tts.TtsCatalog
import io.github.mungbean2435.bookvoice.tts.TtsException
import io.github.mungbean2435.bookvoice.tts.TtsProvider
import io.github.mungbean2435.bookvoice.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

/** 系统 TTS 的可用性快照，设置页用它告诉用户「为什么不能用」。 */
data class SystemTtsStatus(
    val ready: Boolean,
    val engineName: String,
    val voiceCount: Int,
    val chineseVoiceCount: Int,
    val message: String,
    /** 有的引擎只认系统设置里的发音人，应用内 setVoice 会返回成功但声音不变。 */
    val voiceSwitchSupported: Boolean = true
)

/**
 * 设备自带 TTS 引擎。离线音色取决于系统里已安装的引擎/语言包；
 * 系统引擎本身不做流式，这里用 synthesizeToFile 生成 WAV 后走现有缓存/播放链路。
 */
class SystemTtsProvider(context: Context) : TtsProvider {

    private val appContext = context.applicationContext
    private val synthMutex = Mutex()
    private val utteranceSeq = AtomicInteger()
    private val initSeq = AtomicInteger()

    /**
     * TextToSpeech 的初始化是异步的，而且失败后不会自己恢复：
     * 用户去系统设置装好引擎/语音包回来，必须能重新绑定，不能要求重启 App。
     * 因此这里保存「一次初始化尝试」，失败后下次调用会 shutdown 旧实例再建新的。
     */
    private class EngineHandle(
        val engine: TextToSpeech,
        val ready: CompletableDeferred<Boolean>,
        /** 初始化时系统的默认引擎包名，用来发现「用户切了引擎」。 */
        @Volatile var boundEngine: String? = null
    )

    /** 引擎连接在合成前/合成中消失，外层重建一次再试。 */
    private class EngineLost(message: String) : Exception(message)

    private val initLock = Any()
    private var handle: EngineHandle? = null
    @Volatile
    private var lastInitOk: Boolean? = null
    private var lastInitCode: Int? = null

    /**
     * 只认系统设置的引擎（Oplus TTS Accessibility Engine）当前发音人的指纹。
     * 它不走 Secure 的 tts_default_*，所以系统设置里换了发音人后要手动刷新，
     * 否则缓存会一直播旧声音。
     */
    @Volatile
    private var managedVoiceFingerprint: String = ""

    /** 用户从系统设置换完发音人后点「重新检测」：+1 让旧缓存整体失效。 */
    @Volatile
    private var managedCacheGeneration: Int = 0

    override val id: String = "system"
    override val displayName: String = "系统 TTS"
    override val audioFormat: String = "wav"
    override val requiresApiKey: Boolean = false

    override val defaultConfig: ProviderConfig = ProviderConfig(
        baseUrl = "",
        model = "系统引擎",
        voice = ""
    )

    override fun voices(): List<Voice> = listOf(
        Voice("", "系统默认", "跟随系统语言与引擎")
    )

    /**
     * 系统引擎和默认语言变了，缓存就必须失效；否则换引擎后仍会播放旧引擎的 WAV。
     * 这里读 Secure 设置，不触发 TextToSpeech 初始化，任何时机调用都稳定。
     */
    @Suppress("DEPRECATION")
    override fun cacheKeyId(): String {
        val cr = appContext.contentResolver
        fun secure(key: String) = runCatching {
            Settings.Secure.getString(cr, key)
        }.getOrNull().orEmpty()
        return buildString {
            append(id)
            append('|').append(secure(Settings.Secure.TTS_DEFAULT_SYNTH))
            append('|').append(secure(Settings.Secure.TTS_DEFAULT_LANG))
            append('|').append(secure(Settings.Secure.TTS_DEFAULT_COUNTRY))
            append('|').append(secure(Settings.Secure.TTS_DEFAULT_VARIANT))
            if (managedVoiceFingerprint.isNotBlank()) {
                append('|').append(managedVoiceFingerprint)
            }
            if (managedCacheGeneration > 0) {
                append('|').append("gen").append(managedCacheGeneration)
            }
        }
    }

    override fun builtinCatalog(): TtsCatalog = TtsCatalog(
        models = listOf("系统引擎"),
        voices = voices(),
        fromNetwork = false,
        note = "使用设备上已安装的 TTS 引擎；离线音色需先在系统中下载语言包"
    )

    /** 取（必要时创建）一次初始化尝试，并把结果汇合到一个 Deferred。 */
    private fun acquireAttempt(): EngineHandle {
        synchronized(initLock) {
            val current = handle
            if (current != null) {
                if (!current.ready.isCompleted) return current
                if (lastInitOk == true) return current
                // 上一轮初始化失败：释放旧连接，允许装好引擎后直接重试。
                runCatching { current.engine.shutdown() }
                handle = null
            }
            lastInitOk = null
            lastInitCode = null
            val attemptNo = initSeq.incrementAndGet()
            SystemTtsLog.record("初始化", "第 $attemptNo 次创建 TextToSpeech")
            val deferred = CompletableDeferred<Boolean>()
            val created = TextToSpeech(appContext) { status ->
                val ok = status == TextToSpeech.SUCCESS
                SystemTtsLog.record(
                    "初始化",
                    "回调 status=$status（${if (ok) "SUCCESS" else "失败"}）"
                )
                if (!ok) {
                    synchronized(initLock) { lastInitCode = status }
                }
                lastInitOk = ok
                deferred.complete(ok)
            }
            val attempt = EngineHandle(created, deferred)
            handle = attempt
            return attempt
        }
    }

    /**
     * 某个引擎实例失效后立刻丢掉它，下一次 readyEngine 会重新绑定。
     * 只清理同一个实例，避免把别人刚建好的新连接也关掉。
     */
    private fun invalidateEngine(engine: TextToSpeech) {
        synchronized(initLock) {
            val current = handle
            if (current?.engine === engine) {
                SystemTtsLog.record("引擎", "标记失效并释放当前实例")
                runCatching { engine.shutdown() }
                handle = null
                lastInitOk = false
            }
        }
    }

    /** 等引擎初始化；超时/失败返回 null，由调用方给出可读提示。 */
    private suspend fun readyEngine(rebindBudget: Int = 1): TextToSpeech? {
        // 直接持有这一次尝试的实例：初始化回调可能早于 handle 赋值，
        // 从 handle 里再取一次会读到 null 或下一次尝试的对象。
        val started = System.currentTimeMillis()
        val attempt = runCatching { acquireAttempt() }.getOrElse { e ->
            SystemTtsLog.record(
                "初始化",
                "创建异常：${e.javaClass.simpleName}: ${e.message}"
            )
            return null
        }
        val ok = withTimeoutOrNull(INIT_TIMEOUT_MS) { attempt.ready.await() } ?: false
        val elapsed = System.currentTimeMillis() - started
        if (!ok) {
            val code = synchronized(initLock) { lastInitCode }
            SystemTtsLog.record(
                "初始化",
                "未就绪：等待 ${elapsed}ms，lastInitCode=${code ?: "超时"}"
            )
            return null
        }

        // 系统里换了默认引擎时，旧实例还绑在旧引擎上：直接重建，
        // 用户不用重启 App（ROADMAP 3.5.2 的验收项）。
        val defaultEngine = runCatching { attempt.engine.defaultEngine }.getOrNull()
        val bound = attempt.boundEngine
        if (bound == null) {
            attempt.boundEngine = defaultEngine
            SystemTtsLog.record(
                "引擎",
                "就绪：${defaultEngine ?: "默认引擎为空"}，耗时 ${elapsed}ms"
            )
            refreshManagedVoiceFingerprint(attempt.engine)
        } else if (defaultEngine != null && defaultEngine != bound) {
            SystemTtsLog.record("引擎", "默认引擎变化：$bound -> $defaultEngine，重建")
            invalidateEngine(attempt.engine)
            if (rebindBudget > 0) return readyEngine(rebindBudget - 1)
            return null
        }
        return attempt.engine
    }

    private fun initFailureMessage(): String {
        val code = synchronized(initLock) { lastInitCode }
        return if (code == null) {
            "系统 TTS 初始化超时（引擎无响应）"
        } else {
            "系统 TTS 初始化失败（错误码 $code）"
        }
    }

    /**
     * 设置页打开时调用：顺带触发一次初始化，并把引擎、音色数量带回来。
     * 失败不抛异常，只把原因放进 message。
     */
    suspend fun status(forceRebind: Boolean = false): SystemTtsStatus {
        if (forceRebind) {
            // 系统设置里换了发音人后，旧 TextToSpeech/旧缓存都可能还停在旧声音。
            // 重建会话并让缓存键整体换代；这是用户点「重新检测」/从设置返回时触发。
            val current = synchronized(initLock) { handle?.engine }
            if (current != null) invalidateEngine(current)
            managedVoiceFingerprint = ""
            managedCacheGeneration += 1
            SystemTtsLog.record("引擎", "强制重建系统引擎，音频缓存进入第 $managedCacheGeneration 代")
        }
        val engine = withContext(Dispatchers.IO) { readyEngine() }
            ?: return SystemTtsStatus(
                ready = false,
                engineName = "",
                voiceCount = 0,
                chineseVoiceCount = 0,
                message = "${initFailureMessage()}；请安装或启用一个系统 TTS 引擎"
            ).also {
                SystemTtsLog.record("状态", "不可用：${it.message}")
            }
        val engineName = runCatching { engine.defaultEngine }.getOrNull().orEmpty()
        val voices = withContext(Dispatchers.IO) {
            runCatching { engine.voices.orEmpty().toList() }.getOrDefault(emptyList())
        }
        val chinese = voices.count { isChineseVoice(it) }
        withContext(Dispatchers.IO) { refreshManagedVoiceFingerprint(engine) }
        val managed = isSystemManagedVoice(engine)
        val installedEngines = withContext(Dispatchers.IO) {
            runCatching {
                engine.engines.orEmpty().map { "${it.name}（${it.label}）" }
            }.getOrDefault(emptyList())
        }
        // Oplus 这类引擎的音色 locale 是 chn/usa，isLanguageAvailable(zh-CN) 却可能失败，
        // 把两个真实返回值一起记下来，才能区分「引擎没中文」和「只是用了非标准 locale」。
        val zhAvailable = runCatching {
            engine.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE)
        }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        val enAvailable = runCatching {
            engine.isLanguageAvailable(Locale.US)
        }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        SystemTtsLog.record(
            "状态",
            "默认引擎=${engineName.ifBlank { "空" }}；已安装 ${installedEngines.size} 个：" +
                installedEngines.joinToString(" | ").take(600) +
                "；isLanguageAvailable zh-CN=$zhAvailable, en-US=$enAvailable"
        )
        val label = engineName.ifBlank { "系统引擎" }
        val message = buildString {
            when {
                voices.isEmpty() ->
                    append("引擎已就绪（$label），但没有返回任何音色；请到系统设置里检查语音包")

                chinese == 0 ->
                    append("引擎已就绪（$label），但没有中文语音；请在系统设置里下载中文语音包")

                else ->
                    append("引擎已就绪：$label · ${voices.size} 个音色（中文 $chinese 个）")
            }
            if (managed) {
                append("；该引擎的发音人由系统设置决定，应用内切换不会生效")
            }
        }
        SystemTtsLog.record(
            "状态",
            buildString {
                append(message)
                if (voices.isNotEmpty()) {
                    append("；示例：")
                    append(
                        voices.take(6).joinToString(" | ") {
                            "${it.name}@${it.locale?.toLanguageTag().orEmpty()}"
                        }
                    )
                }
            }
        )
        return SystemTtsStatus(
            ready = true,
            engineName = engineName,
            voiceCount = voices.size,
            chineseVoiceCount = chinese,
            message = message,
            voiceSwitchSupported = !managed
        )
    }

    override suspend fun fetchCatalog(config: ProviderConfig): TtsCatalog {
        // TextToSpeech 的构造与 getVoices 都是 Binder 调用，别占主线程。
        val engine = withContext(Dispatchers.IO) { readyEngine() }
            ?: return builtinCatalog().copy(
                note = "${initFailureMessage()}；请打开系统 TTS 设置安装或启用引擎"
            ).also {
                SystemTtsLog.record("目录", "读取失败：${it.note}")
            }
        withContext(Dispatchers.IO) { refreshManagedVoiceFingerprint(engine) }
        val engineName = runCatching { engine.defaultEngine }.getOrNull().orEmpty()
        val remote = withContext(Dispatchers.IO) {
            runCatching {
                engine.voices.orEmpty()
                    .filter { it.locale != null }
                    .map { it.toVoice() }
                    .sortedWith(compareBy({ it.hint != "离线" }, { it.label }))
            }
        }.getOrElse {
            return builtinCatalog().copy(
                note = "读取系统音色失败：${it.message?.take(80) ?: "未知错误"}"
            )
        }
        if (remote.isEmpty()) {
            SystemTtsLog.record("目录", "引擎=$engineName，音色列表为空")
            return builtinCatalog().copy(note = "系统引擎没有返回音色，使用默认音色")
        }
        SystemTtsLog.record(
            "目录",
            "引擎=$engineName，读到 ${remote.size} 个音色" +
                "（离线 ${remote.count { it.hint == "离线" }}，" +
                "需联网 ${remote.count { it.hint == "需联网" }}）"
        )
        val note = buildString {
            append("来自系统 TTS 引擎")
            if (engineName.isNotBlank()) append("（").append(engineName).append("）")
            append("；离线音色取决于已下载的语言包")
        }
        return TtsCatalog(
            models = listOf("系统引擎"),
            voices = listOf(Voice("", "系统默认", "跟随系统语言与引擎")) + remote,
            fromNetwork = false,
            note = note
        )
    }

    override suspend fun synthesize(request: SynthRequest): SynthResult {
        val text = request.text.trim()
        if (text.isBlank()) throw TtsException.Empty("没有可合成的文本")
        val started = System.currentTimeMillis()
        SystemTtsLog.record(
            "合成",
            "请求 ${text.length} 字，voice=${request.voiceId.ifBlank { "<系统默认>" }}，" +
                "model=${request.model.ifBlank { "—" }}"
        )
        return synthMutex.withLock {
            var lost: EngineLost? = null
            // 引擎被系统杀掉 / 换引擎后旧连接失效时，重建一次再试；
            // 不支持文件合成的引擎会连续失败两次，最终给出明确提示。
            repeat(2) { attempt ->
                val engine = withContext(Dispatchers.IO) { readyEngine() }
                    ?: throw TtsException.Server(
                        "${initFailureMessage()}；请打开系统 TTS 设置启用引擎或下载语音包"
                    )
                try {
                    return@withLock synthOnce(engine, request.voiceId, text, started)
                } catch (e: EngineLost) {
                    lost = e
                    SystemTtsLog.record(
                        "重试",
                        "第 ${attempt + 1} 次失败：${e.message}；${if (attempt == 0) "重建引擎后重试" else "不再重试"}"
                    )
                    invalidateEngine(engine)
                    if (attempt == 0) return@repeat
                }
            }
            throw TtsException.Server(
                "${lost?.message ?: "系统 TTS 不可用"}；请稍后重试或换一个系统引擎"
            )
        }
    }

    /** 在一次已初始化的引擎上完成「配置音色 + 合成到文件」。 */
    private suspend fun synthOnce(
        engine: TextToSpeech,
        voiceId: String,
        text: String,
        started: Long
    ): SynthResult = withContext(Dispatchers.Main.immediate) {
        // 先设语速/音高，再选音色：部分引擎的 setSpeechRate 会重置 voice。
        // 播放速度由播放器统一处理，合成始终按 1.0，否则会被变速两次。
        runCatching {
            engine.setSpeechRate(1f)
            engine.setPitch(1f)
        }
        val selectedVoice = applyVoice(engine, voiceId, text)
        val file = File.createTempFile("bookvoice-system-", ".wav", appContext.cacheDir)
        try {
            val utteranceId = "bookvoice-${utteranceSeq.incrementAndGet()}"
            val done = CompletableDeferred<Unit>()
            // 每次合成都换 listener，所以回调必须核对 utteranceId：
            // 上一块超时/取消后的迟到回调不能把这一块提前判成完成。
            val listener = object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    SystemTtsLog.record("合成", "onStart $id")
                }

                override fun onDone(id: String?) {
                    SystemTtsLog.record("合成", "onDone $id")
                    if (id == utteranceId) done.complete(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    SystemTtsLog.record("合成", "onError $id（无错误码）")
                    if (id == utteranceId) {
                        done.completeExceptionally(TtsException.Server("系统 TTS 合成失败"))
                    }
                }

                override fun onError(id: String?, errorCode: Int) {
                    SystemTtsLog.record("合成", "onError $id code=$errorCode")
                    if (id == utteranceId) done.completeExceptionally(mapError(errorCode))
                }

                override fun onStop(id: String?, interrupted: Boolean) {
                    SystemTtsLog.record("合成", "onStop $id interrupted=$interrupted")
                    if (id == utteranceId) {
                        done.completeExceptionally(
                            TtsException.Server("系统 TTS 合成被中断")
                        )
                    }
                }
            }
            engine.setOnUtteranceProgressListener(listener)

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                // voiceName 是 TextToSpeech.Engine 的隐藏常量，按 AOSP 的字面值传；
                // 个别 OEM 引擎的 synthesizeToFile 会读 per-request 参数。
                selectedVoice?.let { putString("voiceName", it.name) }
            }
            val result = engine.synthesizeToFile(text, params, file, utteranceId)
            SystemTtsLog.record(
                "合成",
                "synthesizeToFile=$result，utterance=$utteranceId，" +
                    "voice=${selectedVoice?.name ?: "<setLanguage 默认>"}，" +
                    "语速/音高已设 1.0，文本 ${text.length} 字"
            )
            if (result != TextToSpeech.SUCCESS) {
                // 连接已经断了（例如系统切了引擎）时这里会立刻返回 ERROR。
                throw EngineLost("系统 TTS 无法导出音频（$result）")
            }

            try {
                withTimeout(SYNTH_TIMEOUT_MS) { done.await() }
            } catch (e: TimeoutCancellationException) {
                SystemTtsLog.record("合成", "等待 onDone 超时 ${SYNTH_TIMEOUT_MS}ms")
                runCatching { engine.stop() }
                throw TtsException.Server(
                    "系统 TTS 合成超时（${SYNTH_TIMEOUT_MS / 1000} 秒），请稍后重试或换个音色"
                )
            } catch (e: CancellationException) {
                // 用户停止试听/切块：把引擎里排队的那句也停掉，别继续占用引擎。
                runCatching { engine.stop() }
                throw e
            }

            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            SystemTtsLog.record(
                "合成",
                "文件 ${file.length()} 字节，读到 ${bytes.size} 字节，用时 " +
                    "${System.currentTimeMillis() - started}ms"
            )
            if (bytes.isEmpty()) {
                throw TtsException.Empty(
                    "系统 TTS 没有生成音频；可能缺少语音数据，请在系统 TTS 设置里下载语音包"
                )
            }
            SynthResult(
                audio = bytes,
                mime = "audio/wav",
                finishReason = null,
                truncated = false,
                durationMs = System.currentTimeMillis() - started
            )
        } finally {
            file.delete()
        }
    }

    /**
     * 优先用用户选中的音色；否则按正文语言切到中文 / 英文。
     *
     * 顺序很重要：先 setLanguage，再 setVoice。部分引擎（OnePlus 的
     * `com.oplus.ttsaccessibilityengine`）只调 setVoice 会返回 SUCCESS，
     * 但合成仍用默认音色。
     *
     * @return 实际设置的音色；走 setLanguage 时返回 null。
     */
    private fun applyVoice(engine: TextToSpeech, voiceId: String, text: String): AndroidVoice? {
        // OnePlus/OPPO 的 TTS Accessibility Engine 只认系统设置里的发音人：
        // setVoice 会返回 SUCCESS，但合成声音不变。这里不覆盖系统选择，
        // 用户到系统 TTS 设置里换发音人后，下面缓存键会跟着失效。
        if (isSystemManagedVoice(engine)) {
            SystemTtsLog.record(
                "音色",
                "引擎 ${OPLUS_ENGINE} 的发音人由系统设置管理，忽略应用内选择 " +
                    "（${voiceId.ifBlank { "<系统默认>" }}）"
            )
            return null
        }

        if (voiceId.isNotBlank()) {
            val voice = runCatching {
                engine.voices.orEmpty().firstOrNull { it.name == voiceId }
            }.getOrNull()
            if (voice != null) {
                val lang = runCatching { engine.setLanguage(voice.locale) }
                    .getOrDefault(TextToSpeech.ERROR)
                val applied = runCatching { engine.setVoice(voice) }
                    .getOrDefault(TextToSpeech.ERROR)
                val actual = runCatching { engine.voice?.name }.getOrNull()
                SystemTtsLog.record(
                    "音色",
                    "显式音色 $voiceId@${voice.locale?.toLanguageTag().orEmpty()}，" +
                        "setLanguage=$lang，setVoice=$applied，实际=${actual ?: "null"}"
                )
                if (applied == TextToSpeech.SUCCESS) return voice
            } else {
                SystemTtsLog.record("音色", "显式音色 $voiceId 不在引擎音色列表中")
            }
            Log.w("BookVoice", "系统 TTS 音色不可用，回落到按语言选择：$voiceId")
        }

        val hasHan = containsHan(text)
        val preferred = if (hasHan) Locale.SIMPLIFIED_CHINESE else Locale.US
        val language = preferred.getDisplayLanguage(Locale.getDefault())
        val available = runCatching { engine.isLanguageAvailable(preferred) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        SystemTtsLog.record(
            "音色",
            "未指定音色：文本含汉字=$hasHan，isLanguageAvailable(${preferred.toLanguageTag()})=$available"
        )
        when (available) {
            TextToSpeech.LANG_MISSING_DATA -> throw TtsException.Server(
                "系统 TTS 缺少${language}语音数据；请打开系统 TTS 设置下载语音包"
            )

            TextToSpeech.LANG_NOT_SUPPORTED -> {
                // Oplus 等引擎的 Voice.locale 是 chn/usa，isLanguageAvailable(zh-CN)
                // 会误报不支持；改按音色名/语言从引擎列表里挑一个真实音色。
                val picked = pickVoice(engine, hasHan)
                if (picked != null) {
                    val lang = runCatching { engine.setLanguage(picked.locale) }
                        .getOrDefault(TextToSpeech.ERROR)
                    val applied = runCatching { engine.setVoice(picked) }
                        .getOrDefault(TextToSpeech.ERROR)
                    val actual = runCatching { engine.voice?.name }.getOrNull()
                    SystemTtsLog.record(
                        "音色",
                        "语言不可用，改选音色 ${picked.name}@${picked.locale?.toLanguageTag().orEmpty()}，" +
                            "setLanguage=$lang，setVoice=$applied，实际=${actual ?: "null"}"
                    )
                    if (applied == TextToSpeech.SUCCESS) return picked
                }
                if (looksDisconnected(engine)) {
                    throw EngineLost("系统 TTS 引擎连接已断开")
                }
                throw TtsException.Server(
                    "当前系统 TTS 引擎不支持${language}；请在系统 TTS 设置里安装或切换支持${language}的引擎"
                )
            }
        }

        val result = runCatching { engine.setLanguage(preferred) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        SystemTtsLog.record("音色", "setLanguage(${preferred.toLanguageTag()})=$result")
        when (result) {
            TextToSpeech.LANG_MISSING_DATA -> throw TtsException.Server(
                "系统 TTS 缺少${language}语音数据；请打开系统 TTS 设置下载语音包"
            )

            TextToSpeech.LANG_NOT_SUPPORTED -> {
                if (looksDisconnected(engine)) {
                    throw EngineLost("系统 TTS 引擎连接已断开")
                }
                throw TtsException.Server(
                    "当前系统 TTS 引擎不支持${language}；请在系统 TTS 设置里安装或切换支持${language}的引擎"
                )
            }
        }
        return null
    }

    /** 这个引擎是否只认系统设置里的发音人（应用内 setVoice 无效）。 */
    private fun isSystemManagedVoice(engine: TextToSpeech): Boolean =
        runCatching { engine.defaultEngine }.getOrNull() == OPLUS_ENGINE

    /**
     * 只认系统发音人的引擎：把当前发音人记进缓存键。
     * 用户从系统设置换人并回到 App 后，旧 WAV 不会再被复用。
     */
    private fun refreshManagedVoiceFingerprint(engine: TextToSpeech) {
        if (!isSystemManagedVoice(engine)) return
        val voice = runCatching { engine.defaultVoice }.getOrNull() ?: return
        val value = "${voice.name}|${voice.locale?.toLanguageTag().orEmpty()}"
        if (value != managedVoiceFingerprint) {
            managedVoiceFingerprint = value
            SystemTtsLog.record("音色", "系统发音人=$value（缓存键已更新）")
        }
    }

    /** 按正文语言从引擎音色列表里挑一个离线优先、质量高的音色。 */
    private fun pickVoice(engine: TextToSpeech, hasHan: Boolean): AndroidVoice? {
        val voices = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
        return voices
            .filter { if (hasHan) isChineseVoice(it) else isEnglishVoice(it) }
            .sortedWith(
                compareBy<AndroidVoice> { it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality }
            )
            .firstOrNull()
    }

    /**
     * Oplus 引擎用非标准 locale（chn / usa），Java 会把 chn 当成别的语言；
     * 这里同时看 ISO3 代码和音色名，避免「明明有中文音色却报没有」。
     */
    private fun isChineseVoice(voice: AndroidVoice): Boolean {
        val locale = voice.locale
        val lang = locale?.language.orEmpty().lowercase(Locale.ROOT)
        val iso3 = runCatching { locale?.getISO3Language() }
            .getOrNull().orEmpty().lowercase(Locale.ROOT)
        return lang in CHINESE_LANGUAGES ||
            iso3 in CHINESE_ISO3 ||
            voice.name.contains("中") ||
            voice.name.contains("普通话") ||
            voice.name.contains("粤")
    }

    private fun isEnglishVoice(voice: AndroidVoice): Boolean {
        val locale = voice.locale
        val lang = locale?.language.orEmpty().lowercase(Locale.ROOT)
        val iso3 = runCatching { locale?.getISO3Language() }
            .getOrNull().orEmpty().lowercase(Locale.ROOT)
        return lang in ENGLISH_LANGUAGES || iso3 == "eng" || voice.name.contains("英")
    }

    /**
     * 连接断掉时所有 isLanguageAvailable 都会失败、getVoices 也拿不到东西。
     * 只要有一个语言可用，就说明引擎还活着，只是真的不支持目标语言。
     */
    private fun looksDisconnected(engine: TextToSpeech): Boolean {
        val voices = runCatching { engine.voices.orEmpty().toList() }.getOrDefault(emptyList())
        if (voices.isNotEmpty()) return false
        val candidates = listOf(Locale.US, Locale.getDefault())
        return candidates.none { locale ->
            runCatching { engine.isLanguageAvailable(locale) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
        }
    }

    /** 正文里有没有汉字，决定这次合成该用中文还是英文语音。 */
    private fun containsHan(text: String): Boolean = text.any { ch ->
        ch.code in 0x3400..0x4DBF || ch.code in 0x4E00..0x9FFF || ch.code in 0xF900..0xFAFF
    }

    private fun mapError(errorCode: Int): Throwable = when (errorCode) {
        TextToSpeech.ERROR_SERVICE,
        TextToSpeech.ERROR_NOT_INSTALLED_YET ->
            EngineLost("系统 TTS 引擎连接已断开（$errorCode）")

        TextToSpeech.ERROR_NETWORK,
        TextToSpeech.ERROR_NETWORK_TIMEOUT ->
            TtsException.Network("系统 TTS 网络错误（$errorCode）")

        TextToSpeech.ERROR_INVALID_REQUEST ->
            TtsException.TooLong("系统 TTS 拒绝请求（$errorCode）")

        else -> TtsException.Server("系统 TTS 合成失败（$errorCode）")
    }

    private fun AndroidVoice.toVoice(): Voice {
        val locale = this.locale
        // Oplus 引擎的 voice name 本身就是「中文（温柔女声）」这种可读名，
        // 再拼 locale 会变成「Chinook Jargon · 中文（温柔女声）」，直接用它。
        val label = if (name.contains("中") || name.contains("英")) {
            name
        } else {
            val language = when {
                isChineseVoice(this) -> "中文"
                isEnglishVoice(this) -> "英文"
                else -> locale?.getDisplayLanguage(Locale.getDefault()).orEmpty().ifBlank { "系统" }
            }
            val country = locale?.getDisplayCountry(Locale.getDefault()).orEmpty()
            buildString {
                append(language)
                if (country.isNotBlank()) append(" · ").append(country)
                if (name.isNotBlank()) append(" · ").append(name)
            }
        }
        return Voice(
            id = name,
            label = label,
            hint = if (isNetworkConnectionRequired) "需联网" else "离线"
        )
    }

    private companion object {
        /** 首次绑定 / 引擎冷启动可能较慢；超过这个时间按失败提示。 */
        const val INIT_TIMEOUT_MS = 15_000L
        const val SYNTH_TIMEOUT_MS = 120_000L

        /** OnePlus / OPPO ColorOS 16 的 TTS 引擎，只认系统设置里的发音人。 */
        const val OPLUS_ENGINE = "com.oplus.ttsaccessibilityengine"

        /** 正常 ISO 代码 + OEM 常见非标准代码（Oplus 用 chn / usa）。 */
        val CHINESE_LANGUAGES = setOf("zh", "zho", "chn", "cmn")
        val CHINESE_ISO3 = setOf("zho", "chn", "cmn")
        val ENGLISH_LANGUAGES = setOf("en", "eng", "usa")
    }
}
