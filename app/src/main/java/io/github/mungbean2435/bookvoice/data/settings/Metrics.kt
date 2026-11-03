package io.github.mungbean2435.bookvoice.data.settings

/** 只存在内存里的最近一次测量值，用于设置页「诊断」。 */
object Metrics {
    @Volatile
    var lastImportMs: Long = 0

    @Volatile
    var lastBreakdown: String = ""

    /** 点书 → 首帧正文可见，毫秒。 */
    @Volatile
    var lastOpenMs: Long = 0

    /** 上次打开的拆分：查询 / 构建 rows / 首帧，毫秒。 */
    @Volatile
    var lastOpenBreakdown: String = ""

    @Volatile
    private var openStartNanos: Long = 0

    /** 点书时记下起点，由 AppRoot 调用。 */
    fun markOpenStart() {
        openStartNanos = System.nanoTime()
    }

    /** 取走起点（一次打开只算一次）；没有起点时返回 0，由播放页自己补。 */
    fun takeOpenStartNanos(): Long {
        val start = openStartNanos
        openStartNanos = 0
        return start
    }
}
