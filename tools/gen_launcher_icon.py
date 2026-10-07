#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 SmartCycling(智能骑行)的启动图标。

为什么用脚本画,而不是丢一张 PNG?
  图标是**二进制资源**,一旦没有生成过程,后续想调一个色、改一点间距就得重新找设计稿。
  这里把全部几何与配色写成一个 Spec 数据对象,改一个字段就能重新出图,且天然保证:
    - 各密度尺寸精确对应(mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192);
    - 前景严格落在自适应图标的**可见区**内(见下),不会被启动器裁掉;
    - 图形自动居中于可见区(见下),不会出现"整体偏高/偏低"的观感;
    - 抗锯齿一致(超采样后 LANCZOS 缩小),不会出现某个密度下毛边。

设计要点(第一性原理):
  1. **不放文字**。启动器本来就在图标下方显示应用名;图标里的字在 48dp 下必然糊成一团。
     旧图标把"智能骑行 / SMART CYCLING"画进图里 —— 名字本身没错(那确实是 app_name),
     错在**它在这个尺寸下读不出来**,还占掉了主体图形的空间。
     需要带字标的场合(README logo)请用 `tools/gen_logo.py`,那里的尺寸放得下字。
  2. **粗笔画、少细节**。48dp 下 1 个设计单位 = 0.44px,能表达的信息量很小。
     经验阈值:任何两条笔画之间的净空隙 **≥ 4 设计单位**(≈ 1.8px)才可能在 48dp 上分开。
  3. **自行车要"读得出"靠三个信号**:两个轮子 + 中间一个**镂空的三角形** + 鞍座/车把
     明显高于上管。第一版把鞍座、车把和上管画在同一高度,三者糊成一根横杠,于是像卡车。
  4. **速度环只取上半圈**。做成带缺口的整环时,环的两端会垂到车轮下方,看起来像两滴水;
     取上半圈(180°→360°)端点正好停在画面中腰,干净且不干扰车轮。
  5. **外发光要克制**。alpha 越高,48dp 下笔画之间的空隙越容易被光晕填平。
  6. **品牌色**。速度环用青→绿渐变(对应 App 的 BrandCyan #00F0FF / BrandGreen #00FF88),
     底用深蓝(#16263F → #06090F),与骑行 HUD 的深色一脉相承。

自适应图标分层(Android 8.0+ 规范):
  - 画布 108x108dp。系统只保证**中央 66dp 圆**在任何启动器形状下都可见
    (72dp 是"可见区"名义值,但圆形遮罩会切掉四个角,66dp 才是稳妥的落笔范围),
    所以 SAFE_R = 33。本脚本会做**安全区自检**,任何图形外沿超出即报错退出。
  - 背景层:108x108dp 全出血(会被系统裁切)——输出矢量 XML,带线性渐变。
  - 前景层:108x108dp 位图,内容自动居中于可见区。
  - 旧版兜底:mipmap-*/ic_launcher(.round).png,按"中央 72dp 填满图标"的换算合成。

用法:
    python gen_launcher_icon.py --res app/src/main/res
    python gen_launcher_icon.py --res app/src/main/res --preview out/preview.png
"""

import argparse
import dataclasses
import math
import pathlib
import sys

from PIL import Image, ImageDraw, ImageFilter

# ------------------------------------------------------------------ 常量

DESIGN = 108.0                      # 自适应图标画布边长(dp)
CANVAS_CENTER = (DESIGN / 2, DESIGN / 2)
SS = 8                              # 超采样倍数

DENSITIES = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]


# ------------------------------------------------------------------ 设计规格

@dataclasses.dataclass(frozen=True)
class Spec:
    """
    全部几何都在 **108 单位** 的设计空间里表达(即自适应图标的 108dp 画布)。
    坐标原点在画布左上角,x 向右、y 向下。所有坐标最终会被 `centered()` 整体平移,
    因此这里只需写"相对关系正确"的近似值。
    """

    # --- 可见区 ---
    safe_r: float = 33.0        # 中央 66dp 圆的半径(系统保证可见的区域)

    # --- 速度环 ---
    arc_center: tuple = (54.0, 54.0)
    arc_radius: float = 29.6
    arc_width: float = 5.0
    arc_start_deg: float = 180.0   # PIL 角度:0=右,顺时针增大(y 轴向下)
    arc_end_deg: float = 360.0     # 上半圈;两端停在画面中腰
    arc_dots: str = "none"         # none | end | both —— 末端小圆点(仪表指针暗示)
    arc_dot_r: float = 2.6

    # --- 车轮 ---
    # 轮子比车架略粗(4.5 vs 4.2):48dp 下"两个圆圈"是自行车最强的识别信号。
    # 实测对比 4.2 / 4.5 / 4.8 三档:4.2 在 48dp 上偏虚,4.8 在大尺寸下轮圈糊成实心,
    # 4.5 兼顾两者 —— 大尺寸下轮圈留得住孔,48dp 下仍清晰。
    wheel_r: float = 9.1
    wheel_w: float = 4.5
    rear_hub: tuple = (41.0, 63.0)
    front_hub: tuple = (67.0, 63.0)

    # --- 车架骨架(经典菱形:后轴-五通-座管束-头管顶)---
    bb: tuple = (54.0, 62.0)            # 五通(牙盘中心)
    seat_cluster: tuple = (46.5, 45.5)  # 座管束(上管/座管/后上叉交点)
    head_top: tuple = (65.5, 46.5)      # 头管上端

    # --- 座管 + 鞍座(必须明显高于上管,否则三者糊成一根横杠)---
    seat_post_top: tuple = (45.2, 38.2)
    saddle: tuple = ((40.8, 36.6), (49.6, 36.6))
    saddle_w: float = 4.4

    # --- 立管 + 车把 ---
    stem_top: tuple = (66.6, 42.4)
    bar: tuple = ((61.6, 40.6), (70.6, 40.6))
    bar_w: float = 4.0

    # --- 线宽 ---
    frame_w: float = 4.2
    thin_w: float = 3.8

    # --- 配色(与 App 品牌一致)---
    grad_from: tuple = (0x00, 0xE5, 0xFF)   # 亮青(BrandCyan 系)
    grad_to: tuple = (0x00, 0xFF, 0x9D)     # 亮绿(BrandGreen 系)
    bg_from: tuple = (0x16, 0x26, 0x3F)     # 深蓝(偏亮的一端)
    bg_to: tuple = (0x06, 0x09, 0x0F)       # 与 splash_bg 对齐,启动页过渡不跳色
    glow_alpha: int = 30                    # 外发光强度(0-255);过高会在 48dp 糊掉笔画
    glow_blur: float = 2.2                  # 外发光模糊半径(设计单位)

    # -------------------------------------------------------- 几何查询

    def dist(self, p):
        """点 p 到**画布中心**的距离(可见区是画布中心的圆)。"""
        return math.hypot(p[0] - CANVAS_CENTER[0], p[1] - CANVAS_CENTER[1])

    def arc_pt(self, deg):
        rad = math.radians(deg)
        return (self.arc_center[0] + self.arc_radius * math.cos(rad),
                self.arc_center[1] + self.arc_radius * math.sin(rad))

    def _angle_in_sweep(self, deg):
        a0, a1 = self.arc_start_deg, self.arc_end_deg
        return a0 + ((deg - a0) % 360.0) <= a1

    def _arc_key_angles(self):
        """弧上所有"极值角":两个端点 + 落在扫描范围内的 4 个基本方向。"""
        a0, a1 = self.arc_start_deg, self.arc_end_deg
        out = [a0, a1]
        for base in (0, 90, 180, 270, 360, 450, 540, 630):
            if a0 <= base <= a1:
                out.append(float(base))
        return out

    # -------------------------------------------------------- 安全区

    def max_extents(self):
        """
        返回 (描述, 该图形上**离画布中心最远**的点到中心的距离) 列表。

        注意不能一律写成 dist(中心)+r:圆弧只画了一部分,圆心到画布中心的"背离方向"
        未必落在扫描范围内,那种情况下最远点只会在两个端点上。
        """
        items = []

        def circle(desc, c, r, w):
            # PIL 的 outline 向内画 -> 墨迹落在 [r-w, r],最外沿就是 r
            items.append((desc, self.dist(c) + r))

        def seg(desc, p1, p2, w):
            # draw.line 的线宽是**居中**的;点到线段的距离是凸函数,故最远点必在端点
            items.append((desc, max(self.dist(p1), self.dist(p2)) + w / 2.0))

        def arc(desc, w):
            hw = w / 2.0
            rc = self.arc_radius - hw        # 描边中心线(端点圆头补在这里)
            ax, ay = self.arc_center
            away = math.degrees(math.atan2(ay - CANVAS_CENTER[1], ax - CANVAS_CENTER[0])) % 360.0
            if self._angle_in_sweep(away):
                # 弧上存在"背离画布中心"的方向 -> 最远墨迹在该处的**外沿**(半径 r)
                items.append((desc, self.dist(self.arc_center) + self.arc_radius))
            else:
                # 否则最远点只可能落在:外沿的两个端点,或两个端点圆头
                def cap_far(deg):
                    rad = math.radians(deg)
                    c = (self.arc_center[0] + rc * math.cos(rad),
                         self.arc_center[1] + rc * math.sin(rad))
                    return self.dist(c) + hw

                items.append((desc, max(
                    max(self.dist(self.arc_pt(a)) for a in (self.arc_start_deg, self.arc_end_deg)),
                    max(cap_far(a) for a in (self.arc_start_deg, self.arc_end_deg)))))

        arc("速度环", self.arc_width)
        if self.arc_dots in ("end", "both"):
            circle("速度环末端圆点", self.arc_pt(self.arc_end_deg), self.arc_dot_r, 0.0)
        if self.arc_dots == "both":
            circle("速度环起点圆点", self.arc_pt(self.arc_start_deg), self.arc_dot_r, 0.0)

        circle("后轮", self.rear_hub, self.wheel_r, self.wheel_w)
        circle("前轮", self.front_hub, self.wheel_r, self.wheel_w)

        seg("后上叉", self.seat_cluster, self.rear_hub, self.frame_w)
        seg("座管", self.seat_cluster, self.bb, self.frame_w)
        seg("上管", self.seat_cluster, self.head_top, self.frame_w)
        seg("下管", self.bb, self.head_top, self.frame_w)
        seg("前叉", self.head_top, self.front_hub, self.frame_w)
        seg("后下叉", self.bb, self.rear_hub, self.frame_w)

        seg("座管上段", self.seat_cluster, self.seat_post_top, self.thin_w)
        seg("鞍座", self.saddle[0], self.saddle[1], self.saddle_w)
        seg("立管", self.head_top, self.stem_top, self.thin_w)
        seg("车把", self.bar[0], self.bar[1], self.bar_w)
        return items

    def safe_zone_violations(self):
        return [(d, e) for d, e in self.max_extents() if e > self.safe_r]

    # -------------------------------------------------------- 包围盒 / 居中

    def bbox(self):
        """所有图形(含线宽)的包围盒 (x0, y0, x1, y1)。"""
        xs, ys = [], []

        def add(x0, y0, x1, y1):
            xs.extend((x0, x1))
            ys.extend((y0, y1))

        # 弧:描边向内画(见 stroke_arc),墨迹落在半径 [r-w, r] 的环带里,
        # 端点圆头也补在同一条中心线上 -> 极值就是**半径 r 上的路径点**,不需要再外扩
        for a in self._arc_key_angles():
            px, py = self.arc_pt(a)
            add(px, py, px, py)

        for c in (self.rear_hub, self.front_hub):
            add(c[0] - self.wheel_r, c[1] - self.wheel_r,
                c[0] + self.wheel_r, c[1] + self.wheel_r)

        for p1, p2, w in (
                (self.seat_cluster, self.rear_hub, self.frame_w),
                (self.seat_cluster, self.bb, self.frame_w),
                (self.seat_cluster, self.head_top, self.frame_w),
                (self.bb, self.head_top, self.frame_w),
                (self.head_top, self.front_hub, self.frame_w),
                (self.bb, self.rear_hub, self.frame_w),
                (self.seat_cluster, self.seat_post_top, self.thin_w),
                (self.saddle[0], self.saddle[1], self.saddle_w),
                (self.head_top, self.stem_top, self.thin_w),
                (self.bar[0], self.bar[1], self.bar_w),
        ):
            h = w / 2.0
            add(min(p1[0], p2[0]) - h, min(p1[1], p2[1]) - h,
                max(p1[0], p2[0]) + h, max(p1[1], p2[1]) + h)
        return min(xs), min(ys), max(xs), max(ys)

    def shifted(self, dx, dy):
        def s(v):
            return (v[0] + dx, v[1] + dy)

        def s2(v):
            return (s(v[0]), s(v[1]))

        return dataclasses.replace(
            self,
            arc_center=s(self.arc_center),
            rear_hub=s(self.rear_hub), front_hub=s(self.front_hub),
            bb=s(self.bb), seat_cluster=s(self.seat_cluster),
            head_top=s(self.head_top), seat_post_top=s(self.seat_post_top),
            stem_top=s(self.stem_top),
            saddle=s2(self.saddle), bar=s2(self.bar),
        )

    def centered(self):
        """
        平移整份设计,使**图形包围盒的中心**落在画布中心。

        为什么必须做:可见区是画布中心的圆,而自行车天然比速度环"矮",
        包围盒中心会明显偏离画布中心 —— 不修正的话图形整体偏高,底部留一大片空。
        """
        x0, y0, x1, y1 = self.bbox()
        return self.shifted(CANVAS_CENTER[0] - (x0 + x1) / 2.0,
                            CANVAS_CENTER[1] - (y0 + y1) / 2.0)


DEFAULT = Spec()


# ------------------------------------------------------------------ 安全区自检

def check_safe_zone(spec):
    """任何图形外沿超出安全圆 -> 返回问题列表。"""
    return [f"{d}: 外沿 {e:.2f} > 安全半径 {spec.safe_r:.1f}"
            for d, e in spec.safe_zone_violations()]


# ------------------------------------------------------------------ 绘图工具

def _lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient_rgb(size, c_from, c_to):
    """
    从左上到右下的线性渐变。

    性能:母版是 864x864,逐像素在 Python 里跑要几十万次操作。
    这里先做一条宽 2*size-1 的**横向**渐变,再按行切片错位粘贴 ——
    第 y 行取 [y, y+size) 这一段,正好等价于 (x+y) 的对角渐变。
    Python 层循环因此降到 size 次。
    """
    n = 2 * size - 1
    strip = Image.new("RGB", (n, 1))
    px = strip.load()
    for x in range(n):
        px[x, 0] = _lerp(c_from, c_to, x / (n - 1))
    data = strip.tobytes()
    stride = size * 3
    out = bytearray(size * size * 3)
    for y in range(size):
        out[y * stride:(y + 1) * stride] = data[y * 3: y * 3 + stride]
    return Image.frombytes("RGB", (size, size), bytes(out))


def gradient_layer(size, c_from, c_to):
    return gradient_rgb(size, c_from, c_to).convert("RGBA")


def _pt(cx, cy, r, deg):
    rad = math.radians(deg)
    return (cx + r * math.cos(rad), cy + r * math.sin(rad))


def _cap(draw, p, w, fill):
    r = w / 2.0
    draw.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=fill)


def stroke_line(draw, p1, p2, w, fill):
    draw.line([p1, p2], fill=fill, width=max(1, int(round(w))), joint="curve")
    _cap(draw, p1, w, fill)
    _cap(draw, p2, w, fill)


def stroke_arc(draw, cx, cy, r, start, end, w, fill):
    """
    PIL 的 arc 不支持圆头端点,故手动补两个端点圆。

    **必须注意**:PIL 的 outline 是**向内**画的 —— bbox 半径 r、线宽 w 时,
    墨迹实际落在半径 [r-w, r](实测验证过)。
    所以端点圆要补在**描边中心线 r - w/2** 上,而不是路径半径 r 上;
    补在 r 上会让圆头向外鼓出 w/2,在弧的两端各形成一个小球。
    """
    draw.arc([cx - r, cy - r, cx + r, cy + r], start=start, end=end,
             fill=fill, width=max(1, int(round(w))))
    rc = r - w / 2.0
    _cap(draw, _pt(cx, cy, rc, start), w, fill)
    _cap(draw, _pt(cx, cy, rc, end), w, fill)


def stroke_circle(draw, cx, cy, r, w, fill):
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=fill,
                 width=max(1, int(round(w))))


# ------------------------------------------------------------------ 前景

def render_foreground(px, spec=DEFAULT):
    """前景图形(RGBA,透明底)。px 对应 108 设计单位。"""
    s = px / DESIGN
    mask = Image.new("L", (px, px), 0)
    d = ImageDraw.Draw(mask)

    def P(p):
        return (p[0] * s, p[1] * s)

    def L(w):
        return max(1.0, w * s)

    # --- 速度环 ---
    stroke_arc(d, spec.arc_center[0] * s, spec.arc_center[1] * s, spec.arc_radius * s,
               spec.arc_start_deg, spec.arc_end_deg, L(spec.arc_width), 255)

    # --- 车轮 ---
    stroke_circle(d, spec.rear_hub[0] * s, spec.rear_hub[1] * s,
                  spec.wheel_r * s, L(spec.wheel_w), 255)
    stroke_circle(d, spec.front_hub[0] * s, spec.front_hub[1] * s,
                  spec.wheel_r * s, L(spec.wheel_w), 255)

    # --- 车架:经典菱形,6 根线 ---
    stroke_line(d, P(spec.rear_hub), P(spec.seat_cluster), L(spec.frame_w), 255)
    stroke_line(d, P(spec.seat_cluster), P(spec.bb), L(spec.frame_w), 255)
    stroke_line(d, P(spec.seat_cluster), P(spec.head_top), L(spec.frame_w), 255)
    stroke_line(d, P(spec.bb), P(spec.head_top), L(spec.frame_w), 255)
    stroke_line(d, P(spec.head_top), P(spec.front_hub), L(spec.frame_w), 255)
    stroke_line(d, P(spec.bb), P(spec.rear_hub), L(spec.frame_w), 255)
    # --- 座管 / 鞍座 / 立管 / 车把 ---
    stroke_line(d, P(spec.seat_cluster), P(spec.seat_post_top), L(spec.thin_w), 255)
    stroke_line(d, P(spec.saddle[0]), P(spec.saddle[1]), L(spec.saddle_w), 255)
    stroke_line(d, P(spec.head_top), P(spec.stem_top), L(spec.thin_w), 255)
    stroke_line(d, P(spec.bar[0]), P(spec.bar[1]), L(spec.bar_w), 255)

    # --- 渐变上色 ---
    body = gradient_layer(px, spec.grad_from, spec.grad_to)
    body.putalpha(mask)

    # --- 外发光:模糊后压在下层,让深底上的笔画"亮起来" ---
    if spec.glow_alpha > 0:
        glow_mask = mask.filter(ImageFilter.GaussianBlur(radius=max(1.0, spec.glow_blur * s)))
        glow = gradient_layer(px, spec.grad_from, spec.grad_to)
        glow.putalpha(glow_mask.point(lambda v: int(v * spec.glow_alpha / 255)))
        out = Image.alpha_composite(glow, body)
    else:
        out = body

    # --- 末端圆点(叠在最上层,保持锐利)---
    if spec.arc_dots in ("end", "both"):
        dot = Image.new("L", (px, px), 0)
        dd = ImageDraw.Draw(dot)
        for deg in ([spec.arc_end_deg] if spec.arc_dots == "end"
                    else [spec.arc_start_deg, spec.arc_end_deg]):
            c = spec.arc_pt(deg)
            r = spec.arc_dot_r * s
            dd.ellipse([c[0] * s - r, c[1] * s - r, c[0] * s + r, c[1] * s + r], fill=255)
        dot_layer = gradient_layer(px, spec.grad_from, spec.grad_to)
        dot_layer.putalpha(dot)
        out = Image.alpha_composite(out, dot_layer)
    return out


# ------------------------------------------------------------------ 背景

def render_background(px, spec=DEFAULT):
    """深蓝渐变底,全出血。左上略亮,避免大面积死黑。"""
    base = gradient_rgb(px, spec.bg_from, spec.bg_to).convert("RGBA")
    glow = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    cx, cy = px * 0.42, px * 0.34
    for i in range(28, 0, -1):
        t = i / 28.0
        r = px * 0.60 * t
        gd.ellipse([cx - r, cy - r, cx + r, cy + r],
                   fill=(0x2E, 0x5A, 0x8F, int(30 * (1.0 - t) ** 1.4)))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=px * 0.05))
    return Image.alpha_composite(base, glow)


# ------------------------------------------------------------------ 合成

def _shape_mask(size_px, shape):
    mask = Image.new("L", (size_px, size_px), 0)
    md = ImageDraw.Draw(mask)
    if shape == "circle":
        md.ellipse([0, 0, size_px - 1, size_px - 1], fill=255)
    else:
        md.rounded_rectangle([0, 0, size_px - 1, size_px - 1],
                             radius=size_px * 0.22, fill=255)
    return mask


def compose_legacy(fg_master, bg_master, size_px, shape, spec=DEFAULT):
    """
    旧版图标:把 108dp 图层按"中央 72dp 填满图标"的换算合成
    —— 即整张图层放大到 size*108/72 后居中裁切,观感与自适应图标一致。
    """
    scale = DESIGN / (spec.safe_r * 2)
    big = int(round(size_px * scale))
    layer = Image.alpha_composite(
        bg_master.resize((big, big), Image.LANCZOS),
        fg_master.resize((big, big), Image.LANCZOS),
    )
    off = (big - size_px) // 2
    tile = layer.crop((off, off, off + size_px, off + size_px))
    tile.putalpha(_shape_mask(size_px, shape))
    return tile


BG_VECTOR = """<?xml version="1.0" encoding="utf-8"?>
<!--
  自适应图标背景层:108x108dp 全出血,会被系统按启动器形状裁切。
  深蓝线性渐变,终点 #06090F 与 splash_bg 对齐,保证启动页过渡不跳色。
  由 tools/gen_launcher_icon.py 生成;改配色请同步改脚本里的 Spec.bg_from / bg_to。
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0" android:startY="0"
                android:endX="108" android:endY="108"
                android:startColor="#FF16263F"
                android:endColor="#FF06090F" />
        </aapt:attr>
    </path>
</vector>
"""


# ------------------------------------------------------------------ 预览

def build_preview(fg_master, bg_master, path, spec=DEFAULT):
    """拼一张预览:方形遮罩尺寸阶梯 + 圆形遮罩 + 前景原图(带可见区参考圆)。"""
    ladder = [192, 144, 96, 72, 48]
    pad, gap = 18, 16
    row1_h = max(ladder)
    row2_h = 120
    W = pad * 2 + max(sum(ladder) + gap * (len(ladder) - 1), 400)
    H = pad * 3 + row1_h + row2_h

    sheet = Image.new("RGB", (W, H), (0x1B, 0x1F, 0x26))

    x = pad
    for s in ladder:
        tile = compose_legacy(fg_master, bg_master, s, "square", spec)
        sheet.paste(tile, (x, pad + (row1_h - s) // 2), tile)
        x += s + gap

    y2 = pad * 2 + row1_h

    x = pad
    for s in (row2_h, 96, 72):
        tile = compose_legacy(fg_master, bg_master, s, "circle", spec)
        sheet.paste(tile, (x, y2 + (row2_h - s) // 2), tile)
        x += s + gap

    # 右侧:108dp 图层原图 + 可见区参考圆
    guide = Image.alpha_composite(bg_master.resize((row2_h, row2_h), Image.LANCZOS),
                                  fg_master.resize((row2_h, row2_h), Image.LANCZOS))
    gd = ImageDraw.Draw(guide)
    k = row2_h / DESIGN
    for r, col in ((spec.safe_r, (255, 80, 80, 255)), (36.0, (255, 200, 80, 255))):
        gd.ellipse([(CANVAS_CENTER[0] - r) * k, (CANVAS_CENTER[1] - r) * k,
                    (CANVAS_CENTER[0] + r) * k, (CANVAS_CENTER[1] + r) * k],
                   outline=col, width=1)
    sheet.paste(guide.convert("RGB"), (x, y2))

    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)
    return path


# ------------------------------------------------------------------ 主流程

def write_resources(res, fg_master, bg_master, spec=DEFAULT):
    (res / "drawable").mkdir(parents=True, exist_ok=True)
    (res / "drawable" / "ic_launcher_background.xml").write_text(BG_VECTOR, encoding="utf-8")
    print("[写] drawable/ic_launcher_background.xml(渐变矢量)")

    for name, mult in DENSITIES:
        d = res / f"mipmap-{name}"
        d.mkdir(parents=True, exist_ok=True)
        fg_px = int(round(DESIGN * mult))   # 108dp
        ic_px = int(round(48 * mult))       # 48dp

        fg_master.resize((fg_px, fg_px), Image.LANCZOS).save(
            d / "ic_launcher_foreground.png", optimize=True)
        compose_legacy(fg_master, bg_master, ic_px, "square", spec).save(
            d / "ic_launcher.png", optimize=True)
        compose_legacy(fg_master, bg_master, ic_px, "circle", spec).save(
            d / "ic_launcher_round.png", optimize=True)
        print(f"[写] mipmap-{name}: 前景 {fg_px}px / 图标 {ic_px}px")


def main():
    ap = argparse.ArgumentParser(description="生成 SmartCycling 启动图标")
    ap.add_argument("--res", default="app/src/main/res", help="res 目录")
    ap.add_argument("--preview", help="额外输出预览拼图")
    ap.add_argument("--no-center", action="store_true", help="不做自动居中(调试用)")
    ap.add_argument("--skip-check", action="store_true", help="跳过安全区自检(不推荐)")
    args = ap.parse_args()

    spec = DEFAULT if args.no_center else DEFAULT.centered()

    if not args.skip_check:
        bad = check_safe_zone(spec)
        if bad:
            print("安全区自检未通过 —— 下列图形会被启动器裁切:", file=sys.stderr)
            for b in bad:
                print(f"  ✗ {b}", file=sys.stderr)
            print("\n请调小 arc_radius / wheel_r 或收紧线宽后重试。", file=sys.stderr)
            return 1
        desc, ext = max(spec.max_extents(), key=lambda kv: kv[1])
        x0, y0, x1, y1 = spec.bbox()
        print(f"安全区自检通过:最大外沿 {ext:.2f} <= {spec.safe_r:.1f}({desc})")
        print(f"  包围盒 {x1 - x0:.1f} x {y1 - y0:.1f},中心 "
              f"({(x0 + x1) / 2:.2f}, {(y0 + y1) / 2:.2f}),画布中心 {CANVAS_CENTER}")

    res = pathlib.Path(args.res)
    if not res.is_dir():
        print(f"找不到 res 目录: {res}", file=sys.stderr)
        return 1

    master = int(DESIGN * SS)
    print(f"渲染母版 {master}x{master}({SS}x 超采样)…")
    fg_master = render_foreground(master, spec)
    bg_master = render_background(master, spec)

    write_resources(res, fg_master, bg_master, spec)

    if args.preview:
        p = build_preview(fg_master, bg_master, pathlib.Path(args.preview), spec)
        print(f"[写] 预览图 {p}")

    print("\n完成。改几何只需调 Spec 的字段;安全区自检与自动居中会兜住越界和偏心。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
