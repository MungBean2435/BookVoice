package io.github.mungbean2435.bookvoice.ui.nav

import java.util.concurrent.TimeUnit

/**
 * 转场闸门：一次返回动作发生后的一小段时间内，后续返回只吞掉、不再 pop。
 *
 * 目的是让「转场进行中再按返回」只处理一次，避免连点箭头 / 连按系统返回把
 * 设置首页也一起弹掉（更严重时会把 tab 容器弹成空白）。
 *
 * 时间源可注入，单测里用假时钟验证；主线程调用，无需加锁。
 */
class BackGate(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private var lastHandledAt = Long.MIN_VALUE / 2

    /** 返回 true 表示这次可以处理；false 表示正处于上一次转场的窗口内。 */
    fun tryHandle(): Boolean {
        val now = clock()
        if (now - lastHandledAt < windowMs) return false
        lastHandledAt = now
        return true
    }

    companion object {
        /** 比页面转场（300ms 进 / 160ms 退）略宽一点。 */
        val DEFAULT_WINDOW_MS: Long = TimeUnit.MILLISECONDS.toMillis(350)
    }
}
