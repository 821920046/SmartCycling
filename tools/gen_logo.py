#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成仓库展示用的项目 logo(docs/logo.png)。

为什么单独写一个,而不是直接放大启动图标?
  两者**用途不同**:
    - 启动图标是 48dp 的方形徽标 —— **不能放字**(放了下必然糊),只有 mark。
    - README logo 是在网页上按 200px 宽展示的**组合标**(mark + 字标),
      这个尺寸下字标是清晰的,而且需要它来告诉访客"这是什么项目"。
  但两者必须共用**同一个 mark 与同一套品牌配色**,否则仓库和 App 看起来像两个产品。

关键约束:**字标内容从 App 资源里读**,不在这里写死。
  `app/src/main/res/values/strings.xml` 的 `app_name` 是唯一事实来源 ——
  这样文档里的名字不可能和 App 里显示的名字不一致。

用法:
    python gen_logo.py --res app/src/main/res --out docs/logo.png
    python gen_logo.py --res app/src/main/res --out docs/logo.png --social docs/social_preview.png
"""

import argparse
import importlib.util
import pathlib
import re
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parents[1]

# 画布(与原 logo 一致,避免 README 版式跳动)
SIZE = 1024

# 字标配色:在深蓝底上要亮,同时带品牌色倾向
WORD_FROM = (0xF2, 0xFC, 0xFF)   # 近白偏青
WORD_TO = (0x66, 0xF2, 0xC6)     # 浅品牌绿
RULE_COLOR = (0x3A, 0x6E, 0x8F)

FONT_CANDIDATES = ("msyhbd.ttc", "simhei.ttf", "Dengb.ttf", "NotoSansSC-VF.ttf")


# ------------------------------------------------------------------ 加载

def load_gen():
    spec = importlib.util.spec_from_file_location(
        "gen_launcher_icon", ROOT / "tools" / "gen_launcher_icon.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def find_font():
    for name in FONT_CANDIDATES:
        p = pathlib.Path("C:/Windows/Fonts") / name
        if p.exists():
            return p
    raise SystemExit(f"找不到中文字体,试过:{FONT_CANDIDATES}")


def read_app_name(res: pathlib.Path) -> str:
    """从 App 资源里取应用名 —— 唯一事实来源,避免文档与 App 名字不一致。"""
    p = res / "values" / "strings.xml"
    m = re.search(r'<string name="app_name">([^<]+)</string>',
                  p.read_text(encoding="utf-8"))
    if not m:
        raise SystemExit(f"在 {p} 里找不到 app_name")
    return m.group(1).strip()


# ------------------------------------------------------------------ 绘图工具

def gradient_wh(w, h, c_from, c_to):
    """任意宽高的左上→右下线性渐变(同样用"横向长条 + 按行错位切片"避免 O(w*h) 的 Python 循环)。"""
    n = w + h - 1
    strip = Image.new("RGB", (n, 1))
    px = strip.load()
    for i in range(n):
        t = i / (n - 1)
        px[i, 0] = tuple(round(c_from[j] + (c_to[j] - c_from[j]) * t) for j in range(3))
    data = strip.tobytes()
    stride = w * 3
    out = bytearray(w * h * 3)
    for y in range(h):
        out[y * stride:(y + 1) * stride] = data[y * 3: y * 3 + stride]
    return Image.frombytes("RGB", (w, h), bytes(out)).convert("RGBA")


def render_mark(gen, spec, target_w, target_h):
    """把启动图标的前景 mark 高分辨率渲染后,按包围盒裁掉空白再等比缩放到目标框。"""
    px = 2048
    fg = gen.render_foreground(px, spec)
    k = px / gen.DESIGN
    x0, y0, x1, y1 = spec.bbox()
    pad = int(6 * k)          # 给外发光留余量
    box = (max(0, int(x0 * k) - pad), max(0, int(y0 * k) - pad),
           min(px, int(x1 * k) + pad), min(px, int(y1 * k) + pad))
    crop = fg.crop(box)
    r = min(target_w / crop.width, target_h / crop.height)
    return crop.resize((max(1, round(crop.width * r)), max(1, round(crop.height * r))),
                       Image.LANCZOS)


def text_rgba(text, font, tracking, shear, c_from, c_to):
    """把一行字画成带渐变填充的 RGBA 图层;tracking 为字距,shear 为斜切量(制造速度感)。"""
    widths = [font.getlength(ch) for ch in text]
    w = sum(widths) + tracking * (len(text) - 1)
    asc, desc = font.getmetrics()
    pad = int((asc + desc) * 0.45)

    mask = Image.new("L", (int(w) + pad * 2, asc + desc + pad * 2), 0)
    d = ImageDraw.Draw(mask)
    x = float(pad)
    for ch, cw in zip(text, widths):
        d.text((x, pad + asc), ch, font=font, fill=255, anchor="ls")
        x += cw + tracking

    if shear:
        cy = mask.height / 2.0
        mask = mask.transform(mask.size, Image.AFFINE,
                              (1, shear, -shear * cy, 0, 1, 0), resample=Image.BICUBIC)

    layer = gradient_wh(mask.width, mask.height, c_from, c_to)
    layer.putalpha(mask)
    return layer


# ------------------------------------------------------------------ 主构图

def build_logo(gen, spec, app_name, latin_name, font_path):
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    canvas = Image.alpha_composite(canvas, gen.render_background(SIZE, spec))

    # --- mark ---
    mark = render_mark(gen, spec, target_w=636, target_h=520)
    canvas.alpha_composite(mark, ((SIZE - mark.width) // 2, 62))

    # --- 字标 ---
    f_word = ImageFont.truetype(str(font_path), 170, index=0)
    word = text_rgba(app_name, f_word, tracking=6, shear=0.10,
                     c_from=WORD_FROM, c_to=WORD_TO)
    wy = 700 - word.height // 2
    canvas.alpha_composite(word, ((SIZE - word.width) // 2, wy))

    # --- 副标(拉丁名)+ 两侧横线 ---
    f_lat = ImageFont.truetype(str(font_path), 44, index=0)
    lat = text_rgba(latin_name, f_lat, tracking=15, shear=0.0,
                    c_from=(0x8A, 0xD8, 0xC4), c_to=(0x4F, 0xC8, 0xA8))
    ly = 862 - lat.height // 2
    lx = (SIZE - lat.width) // 2
    canvas.alpha_composite(lat, (lx, ly))

    d = ImageDraw.Draw(canvas)
    ry = ly + lat.height // 2
    for x0, x1 in ((150, lx - 34), (lx + lat.width + 34, SIZE - 150)):
        if x1 > x0:
            d.line([(x0, ry), (x1, ry)], fill=RULE_COLOR + (255,), width=3)

    return canvas


def build_social(gen, spec, app_name, latin_name, font_path, w=1280, h=640):
    """GitHub 社交预览图(推荐 1280x640):mark 在左、字标在右。"""
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    canvas = Image.alpha_composite(canvas, gen.render_background(max(w, h), spec)
                                   .resize((w, h), Image.LANCZOS).convert("RGBA"))

    mark = render_mark(gen, spec, target_w=340, target_h=340)
    canvas.alpha_composite(mark, (96, (h - mark.height) // 2))

    f_word = ImageFont.truetype(str(font_path), 132, index=0)
    word = text_rgba(app_name, f_word, tracking=5, shear=0.10,
                     c_from=WORD_FROM, c_to=WORD_TO)
    f_lat = ImageFont.truetype(str(font_path), 40, index=0)
    lat = text_rgba(latin_name, f_lat, tracking=14, shear=0.0,
                    c_from=(0x8A, 0xD8, 0xC4), c_to=(0x4F, 0xC8, 0xA8))

    tx = 96 + mark.width + 72
    block_h = word.height + 26 + lat.height
    ty = (h - block_h) // 2
    canvas.alpha_composite(word, (tx, ty))
    canvas.alpha_composite(lat, (tx + 6, ty + word.height + 26))
    return canvas


def main():
    ap = argparse.ArgumentParser(description="生成项目 logo")
    ap.add_argument("--res", default="app/src/main/res")
    ap.add_argument("--out", default="docs/logo.png")
    ap.add_argument("--social", help="额外输出 GitHub 社交预览图(1280x640)")
    ap.add_argument("--latin", default="SMART CYCLING")
    args = ap.parse_args()

    gen = load_gen()
    spec = gen.DEFAULT.centered()
    font_path = find_font()
    app_name = read_app_name(pathlib.Path(args.res))
    print(f"应用名(取自 strings.xml): {app_name}")
    print(f"字体: {font_path.name}")

    logo = build_logo(gen, spec, app_name, args.latin, font_path)
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    logo.save(out)
    print(f"[写] {out}  {logo.size[0]}x{logo.size[1]}")

    if args.social:
        s = build_social(gen, spec, app_name, args.latin, font_path)
        sp = pathlib.Path(args.social)
        sp.parent.mkdir(parents=True, exist_ok=True)
        s.save(sp)
        print(f"[写] {sp}  {s.size[0]}x{s.size[1]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
