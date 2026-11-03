package io.github.mungbean2435.bookvoice.ui.player

/**
 * 正文是否应该跟着播放切换到新章节。
 *
 * - 用户主动点读/点了“回到朗读”时（[followPlayback]），播放跳到哪一章就跟到哪一章；
 * - 没有显式跟随时，只有“播放从正在看的这一章连续往下走”才跟。判断依据是
 *   上一拍播放所在的章是否就是当前正文所在章，因此播放跳过空章也没关系；
 *   用户翻到别的章去看时则不会被拽走。
 */
internal fun shouldFollowChapter(
    playingChapter: Int,
    displayedChapter: Int,
    followPlayback: Boolean,
    previousPlayingChapter: Int
): Boolean {
    if (playingChapter < 0) return false
    if (playingChapter == displayedChapter) return false
    if (followPlayback) return true
    return previousPlayingChapter == displayedChapter && playingChapter > displayedChapter
}
