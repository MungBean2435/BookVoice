package io.github.mungbean2435.bookvoice.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiquidGlassPolicyTest {

    @Test
    fun sdkBelow31_fallsBackToSolid() {
        assertEquals(GlassTier.Solid, resolveGlassTier(30, true, true, false))
        assertEquals(GlassTier.Solid, resolveGlassTier(26, false, false, false))
    }

    @Test
    fun sdk31And32_areBlurOnly() {
        assertEquals(GlassTier.Blur, resolveGlassTier(31, true, true, false))
        assertEquals(GlassTier.Blur, resolveGlassTier(32, true, true, false))
    }

    @Test
    fun sdk33_liquidOnlyWhenSwitchOnAndAvailable() {
        assertEquals(GlassTier.Liquid, resolveGlassTier(33, true, true, false))
        assertEquals(GlassTier.Blur, resolveGlassTier(33, false, true, false))
        assertEquals(GlassTier.Blur, resolveGlassTier(33, true, false, false))
        assertEquals(GlassTier.Blur, resolveGlassTier(33, true, true, true))
    }

    @Test
    fun blockReasonsAreStable() {
        assertEquals(LiquidGlassBlock.None, resolveLiquidGlassBlock(33, false, false))
        assertEquals(LiquidGlassBlock.UnsupportedSdk, resolveLiquidGlassBlock(32, false, false))
        assertEquals(LiquidGlassBlock.PowerSave, resolveLiquidGlassBlock(33, true, false))
        assertEquals(LiquidGlassBlock.LowRam, resolveLiquidGlassBlock(33, false, true))
        assertNull(liquidGlassBlockReason(LiquidGlassBlock.None))
    }

    @Test
    fun tintMatchesDesignNumbers() {
        assertEquals(0.75f, glassTintAlpha(GlassTier.Blur, true, false), 0.0001f)
        assertEquals(0.48f, glassTintAlpha(GlassTier.Liquid, true, false), 0.0001f)
        assertEquals(0.56f, glassTintAlpha(GlassTier.Liquid, false, false), 0.0001f)
        assertEquals(1f, glassTintAlpha(GlassTier.Solid, false, false), 0.0001f)
        assertEquals(0.62f, glassTintAlpha(GlassTier.Solid, false, true), 0.0001f)
    }

    @Test
    fun refractionWeightOnlyLeavesTheCenterAlone() {
        assertEquals(1f, refractionWeight(0f), 0.0001f)
        assertEquals(0.25f, refractionWeight(0.5f), 0.0001f)
        assertEquals(0f, refractionWeight(1f), 0.0001f)
        assertEquals(0f, refractionWeight(2f), 0.0001f)
    }
}
