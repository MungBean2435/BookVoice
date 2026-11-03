package io.github.mungbean2435.bookvoice.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowPolicyTest {

    @Test
    fun idlePlayerNeverFollows() {
        assertFalse(shouldFollowChapter(-1, 3, followPlayback = true, previousPlayingChapter = 2))
        assertFalse(shouldFollowChapter(-1, 0, followPlayback = false, previousPlayingChapter = 0))
    }

    @Test
    fun sameChapterIsNotASwitch() {
        assertFalse(shouldFollowChapter(3, 3, followPlayback = true, previousPlayingChapter = 2))
        assertFalse(shouldFollowChapter(3, 3, followPlayback = false, previousPlayingChapter = 3))
    }

    @Test
    fun naturalAdvanceFollowsEvenWithoutExplicitFollow() {
        assertTrue(shouldFollowChapter(4, 3, followPlayback = false, previousPlayingChapter = 3))
        assertTrue(shouldFollowChapter(1, 0, followPlayback = false, previousPlayingChapter = 0))
    }

    @Test
    fun naturalAdvanceCanSkipEmptyChapters() {
        assertTrue(shouldFollowChapter(5, 3, followPlayback = false, previousPlayingChapter = 3))
    }

    @Test
    fun explicitFollowJumpsAnyDistance() {
        assertTrue(shouldFollowChapter(9, 2, followPlayback = true, previousPlayingChapter = 8))
        assertTrue(shouldFollowChapter(0, 8, followPlayback = true, previousPlayingChapter = 0))
    }

    @Test
    fun withoutExplicitFollowOnlyContinuousPlaybackIsTaken() {
        // 播放早就在别的章，用户正在看第 3 章：继续播不应该把人拽走。
        assertFalse(shouldFollowChapter(5, 3, followPlayback = false, previousPlayingChapter = 4))
        assertFalse(shouldFollowChapter(4, 3, followPlayback = false, previousPlayingChapter = 2))
        assertFalse(shouldFollowChapter(2, 3, followPlayback = false, previousPlayingChapter = 3))
        assertFalse(shouldFollowChapter(0, 3, followPlayback = false, previousPlayingChapter = 0))
    }
}
