package com.honglian.smartcycling.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 排版尺度。
 *
 * 约定:
 *  - 数值一律使用**表格数字**(`tnum`),避免秒表/速度跳动时整行宽度抖动;
 *  - 普通页面只用这 6 档,不再各处随手写 11.sp / 13.sp / 14.sp / 19.sp。
 *
 * ⚠️ 边界:这 6 档**只覆盖"普通页面"**(历史/地图/设置/配对/离线地图),
 * 它们字号固定,与屏幕尺寸无关。
 *
 * 骑行 HUD 与成绩总结页**故意不用**这套固定档位,而是用**比例字号**:
 *  - [com.honglian.smartcycling.ui.components.SpeedRing] 用 `diameterDp / 3.3f`,
 *    环形随屏幕缩放,数字必须跟着缩放,写死 56sp 会在小屏溢出、大屏显小;
 *  - [com.honglian.smartcycling.ui.screens.RideScreen] 的竖屏仪表盘按
 *    `screenHeightDp < 760` 在 18/20sp、10/11sp 之间切换,把整块 HUD 压进一屏。
 * 因此这里**不提供** HUD 专用字号档位 —— 固定档位无法表达"随容器缩放",
 * 提供一个没人用的档位只会让人以为 HUD 该用它。
 * (历史上曾有 `metric` = 56sp / `metricSmall` = 26sp 两个"骑行 HUD 主数值"档位,
 *  但实际零引用,已删除。)
 */
object AppType {

    val display = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontFeatureSettings = "tnum",
    )

    val title = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    )

    val subtitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )

    val body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    )

    val label = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    val caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
}
