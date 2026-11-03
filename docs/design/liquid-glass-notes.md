# NeriPlayer 底部液态玻璃笔记

阅读范围：`ui/effect/glass/` 全包（16 个 .kt，3104 行）、`assets/shaders/advanced_glass_region_mask.agsl`、
`component/navigation/CleanFloatingTabBar.kt`、`CleanTabLens.kt`、`component/playback/NeriMiniPlayer.kt`。
路径相对 `app/src/main/java/moe/ouom/neriplayer`。

## 1 结构：玻璃不是浮层自己糊的

底部两件（mini player、tab bar）自己不跑模糊。整页被当成一张图：

1. **快照层**：`AdvancedGlassSceneLayer` 把 background 和 content 各包一层
   `captureAdvancedGlassBackdrop`，在 `graphicsLayer` 上挂 `renderEffect`，
   同时把 `CompositingStrategy` 切成 `Offscreen`。
2. **区域层**：每个 `AdvancedGlassSurface` 只画 tint 色和 1dp 边，
   把自己在窗口里的 bounds、四角半径、opacity 注册进 registry，**上限 32 个**。
3. **Shader 层**：AGSL 用圆角矩形 SDF 逐区域算覆盖率，模糊只在这些区域里显示；
   liquid 开启时，同一个 shader 里再做边缘折射和 rim 高光。

所以底部浮层的「玻璃感」= 底下内容被局部虚化 + 浮层自身一层薄 tint + 一圈 1dp 亮边。

## 2 底部两件的尺寸与玻璃参数

| 角色 | 形状 | 高度 / 边距 | 模糊 | liquid tint（暗/亮） | 非 liquid tint | edge |
|---|---|---|---|---|---|---|
| MiniPlayer | 22dp 连续圆角 | 64dp / 左右 16、下 6 | 24dp | 0.22 / 0.30 | 0.30 / 0.36 | 0.06 / 0.10 |
| BottomNavigation | `CircleShape` 胶囊 | 64dp / 左右 16、下 16 | 28dp | 0.48 / 0.56 | 0.75 | 0.07 / 0.12 |

tab bar 额外：阴影 18dp、0.5dp 描边（暗色 10% 白 / 亮色 70% 白）、
没玻璃时 fallback `surfaceContainerLow`（有自定义背景 0.62，否则 0.9）。
mini player 额外：0.5dp、8% `onSurface` 描边，封面 40dp。

液态开启后 tint 明显变薄（tab bar 0.75 → 0.48/0.56），把可见度让给折射和模糊；
edge 也从 0.12 降到 0.07。

## 3 液态折射怎么算

参数：`refraction 0.42`、`edgeBand 14dp`、`rim 1.6dp`、`rimHighlight 0.16`
（`AdvancedGlassHost.kt:24-27`）。

shader 里每个像素：

1. 用 SDF 距离算边缘权重：`edgeWeight = (1 - depth)²`，越靠边越强。
2. 对 SDF 求梯度得到法线（`roundedRectDistance` 差商）。
3. 采样点向法线反方向偏移：`sample = p - normal * refraction * edgeBand * edgeWeight`，
   于是边缘看起来像把背后内容「吸」进来。
4. 轻微提饱和：`mix(luma, rgb, 1 + 0.12 * lens)`。
5. 加一圈 rim：`(1 - rimDepth)² * rimHighlight * light`，
   `light = 0.55 + 0.45 * dot(normal, (-0.5, -0.86))`，光来自左上。

只有靠近边缘的窄带参与折射，所以中间还是「毛玻璃」，不会整块变成放大镜。

## 4 什么时候才给液态

- `isLiquidGlassEnabled` = 用户开启 && 基础模糊可用 && 渲染管线不是 RegionLocal。
  也就是 **UltraLow / Low 画质只有普通毛玻璃，没有液态**（局部模糊管线不跑 lens）。
- 设备门控：省电模式、`isLowRamDevice`、以及 Dimensity 机型（除非画质选 High）直接不给。
- 后端要求 SDK ≥ 33（AGSL / RuntimeShader）；渲染失败自动 `sessionHealthy = false`，
  整体退回纯色 fallback，不会崩。

## 5 底部特有的两个设计

**5.1 底部两件同时采样背景和内容**
`requiresContentBackdrop` 只对 MiniPlayer 与 BottomNavigation 为 true
（`AdvancedGlassSurface.kt:121-122`）。因为书页/列表会在它们底下滚，
只糊壁纸会露馅，所以内容层也留一份快照一起糊。

**5.2 选中胶囊会变成拖拽镜片**
平时是 tab 后面一枚内缩 4dp 的小胶囊；拖动时放大成镜片并放大下方图标：
`PressedScale 1.34`、`PressedMagnification 1.2`、`LensEdgeBand 12dp`、
`LensRefraction 4dp`、`LensInnerShadow 8dp`、`LensElevation 14dp`。
跟手速度还会做 squash-and-stretch（沿行进方向拉长、垂直方向压扁），
所以看起来像液体被拖着走。

## 6 摘录

① `AdvancedGlassTokens.kt:20-37`
```kotlin
        AdvancedGlassRole.MiniPlayer -> AdvancedGlassTokens(
            blurRadiusDp = adjustableRadiusDp ?: 24f,
            tintAlpha = when {
                liquid -> if (isDarkTheme) 0.22f else 0.30f
                isDarkTheme -> 0.30f
                else -> 0.36f
            },
            edgeAlpha = if (liquid) 0.06f else 0.10f
        )
        AdvancedGlassRole.BottomNavigation -> AdvancedGlassTokens(
            blurRadiusDp = adjustableRadiusDp ?: 28f,
            tintAlpha = if (liquid) {
                if (isDarkTheme) 0.48f else 0.56f
            } else {
                0.75f
            },
            edgeAlpha = if (liquid) 0.07f else 0.12f
        )
```
② `AdvancedGlassHost.kt:24-27`
```kotlin
private const val LIQUID_GLASS_REFRACTION = 0.42f
private val LIQUID_GLASS_EDGE_BAND = 14.dp
private val LIQUID_GLASS_RIM = 1.6.dp
private const val LIQUID_GLASS_RIM_HIGHLIGHT = 0.16f
```
③ `advanced_glass_region_mask.agsl:61-79`
```glsl
    float edgeBand = max(lensParams.y, 1.0);
    float depth = clamp(-nearestDistance / edgeBand, 0.0, 1.0);
    float edgeWeight = (1.0 - depth) * (1.0 - depth);
    float2 gradient = float2(
        roundedRectDistance(position + float2(1.0, 0.0), bounds, radii) -
            roundedRectDistance(position - float2(1.0, 0.0), bounds, radii),
        roundedRectDistance(position + float2(0.0, 1.0), bounds, radii) -
            roundedRectDistance(position - float2(0.0, 1.0), bounds, radii)
    );
    float gradientLength = length(gradient);
    float2 normal = gradientLength > 0.0001 ? gradient / gradientLength : float2(0.0);
    float2 samplePosition = position - normal * (lensParams.x * edgeBand * edgeWeight * lens);
    half4 color = child.eval(samplePosition);
    half luma = dot(color.rgb, half3(0.2126, 0.7152, 0.0722));
    color.rgb = mix(half3(luma), color.rgb, half(1.0 + 0.12 * lens));
    float rimDepth = clamp(-nearestDistance / max(lensParams.z, 0.5), 0.0, 1.0);
    float rim = (1.0 - rimDepth) * (1.0 - rimDepth);
    float light = 0.55 + 0.45 * clamp(dot(normal, float2(-0.5, -0.86)), -1.0, 1.0);
    color.rgb += half3(rim * lensParams.w * light * lens) * color.a;
```
④ `CleanTabLens.kt:52-58`
```kotlin
private val RestInset = 4.dp
private const val PressedScale = 1.34f
private const val PressedMagnification = 1.2f
private val LensEdgeBand = 12.dp
private val LensRefraction = 4.dp
private val LensInnerShadow = 8.dp
private val LensElevation = 14.dp
```
⑤ `AdvancedGlassController.kt:20-23`
```kotlin
    val isLiquidGlassEnabled: Boolean
        get() = liquidGlassRequested &&
            isBaseBlurEnabled &&
            !advancedBlurQuality.renderProfile().usesRegionLocalRendering
```

## 7 如果要在听书 App 里复刻

1. 先做**无模糊版本**：`surfaceContainerLow` 底 + 0.5dp 亮边 + 16dp 边距，这套已经能看。
2. 再加内容快照：整页 `graphicsLayer` 挂 `RenderEffect.createBlurEffect`，
   `CompositingStrategy.Offscreen`；底部两件同时读背景与内容两份快照。
3. 区域遮罩：把浮层形状转成圆角矩形 SDF，在 shader 或 `clipPath` 里只显示浮层范围内的模糊。
4. 最后加液态：先只做边缘折射 + rim，跑稳 60fps 再加饱和度和镜片拖拽。
5. 降级策略必须有：省电/低内存关液态、低画质只留毛玻璃、失败回落纯色。
   否则在低端机上会整页掉帧。

注意：NeriPlayer 是 GPL-3.0，这份笔记里的 shader 与常量只作行为参考，
真要落地请按数学描述自己重写。
