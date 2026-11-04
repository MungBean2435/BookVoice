package io.github.mungbean2435.bookvoice.data.parse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeTitlesTest {

    @Test
    fun recognizesChineseVolumeHeaders() {
        assertTrue(VolumeTitles.isVolumeTitle("第一卷"))
        assertTrue(VolumeTitles.isVolumeTitle("第 2 卷 风起"))
        assertTrue(VolumeTitles.isVolumeTitle("第三部：归途"))
        assertTrue(VolumeTitles.isVolumeTitle("卷三"))
        assertTrue(VolumeTitles.isVolumeTitle("Volume 4"))
        assertTrue(VolumeTitles.isVolumeTitle("Part 2 - The Sea"))
    }

    @Test
    fun rejectsChapterTitlesAndLongLines() {
        assertFalse(VolumeTitles.isVolumeTitle("第一章 夜航"))
        assertFalse(VolumeTitles.isVolumeTitle("序章"))
        assertFalse(VolumeTitles.isVolumeTitle("他说，第一卷写得很好。"))
        assertFalse(VolumeTitles.isVolumeTitle("第" + "一".repeat(80) + "卷"))
        assertFalse(VolumeTitles.isVolumeTitle(""))
    }
}
