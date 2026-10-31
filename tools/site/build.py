# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

"""Builds the updated scary.network page from the live page (site.html) and the CI screenshots.

Usage: python3 build.py <live site.html> <shots dir> <phone shots dir> <out index.html> <out og.jpg>
Only the texts in text.py, the features list, the screenshots row, the heroes, the install tip and the meta
descriptions change; the cookie banner, the legal texts (but for two additions) and the script stay as they are.
"""
import base64
import html
import io
import json
import re
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from text import LANGS, T  # noqa: E402
from shots import HERO, SHOTS  # noqa: E402

live, shots_dir, phone_dir, out_html, out_og = sys.argv[1:6]
page = open(live, encoding="utf-8").read()


def jpeg(img, width, quality):
    img = img.convert("RGB")
    if img.width != width:
        img = img.resize((width, round(img.height * width / img.width)), Image.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=quality, optimize=True, progressive=True)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode()


def shot(name):
    return Image.open(f"{shots_dir}/{name}.png")


def remote_composite(lang):
    """The TV's remote screen and the phone page side by side, as on the README."""
    canvas = Image.new("RGB", (1280, 720), (14, 9, 8))
    phone = Image.open(f"{phone_dir}/phone-{lang}.png").convert("RGB")
    ph = 666
    pw = round(phone.width * ph / phone.height)
    phone = phone.resize((pw, ph), Image.LANCZOS)
    frame = Image.new("RGB", (pw + 16, ph + 16), (46, 33, 29))
    frame.paste(phone, (8, 8))
    mask = Image.new("L", frame.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, frame.width - 1, frame.height - 1], radius=30, fill=255)
    px = 1280 - frame.width - 18
    tw = px - 18 - 30 - 16
    th = round(tw * 9 / 16)
    tv = shot(SHOTS["remote"]["tv"]).convert("RGB").resize((tw, th), Image.LANCZOS)
    bezel = Image.new("RGB", (tw + 16, th + 16), (46, 33, 29))
    bezel.paste(tv, (8, 8))
    canvas.paste(bezel, (18, (720 - bezel.height) // 2))
    canvas.paste(frame, (px, (720 - frame.height) // 2), mask)
    return canvas


# ---- The dictionary -------------------------------------------------------------------------------------------
start = page.index("var I18N = ") + len("var I18N = ")
i18n, length = json.JSONDecoder().raw_decode(page[start:])
old = {lang: dict(i18n[lang]) for lang in LANGS}

for lang in LANGS:
    d = i18n[lang]
    o = old[lang]
    d["sub"] = T["sub"][lang]
    d["spec_size"] = o["spec_size"].replace("2.7", "2.9").replace("2,7", "2,9")
    assert "2" in d["spec_size"] and "7" not in d["spec_size"], (lang, d["spec_size"])
    d["badge_new"] = T["badge_new"][lang]
    # Features: new ones and the old ones, in the new order.
    new_features = {
        "f1": ("f1_t", "f1_d"), "f2": ("f2_t", "f2_d"), "f3": ("f3_t", "f3_d"), "f4": ("f4_t", "f4_d"),
        "f6": ("f6_t", "f6_d"), "f10": ("f10_t", "f10_d"), "f11": ("f11_t", "f11_d"),
    }
    moved = {"f5": "f2", "f7": "f3", "f8": "f4", "f9": "f5", "f12": "f9"}
    for key, (t, dsc) in new_features.items():
        d[key + "_t"] = T[t][lang]
        d[key + "_d"] = T[dsc][lang]
    for key, was in moved.items():
        d[key + "_t"] = o[was + "_t"]
        d[key + "_d"] = o[was + "_d"]
    d["f8_d"] = o["f4_d"] + T["f8_d_extra"][lang]
    # Screens.
    for i, s in enumerate(SHOTS["order"], 1):
        spec = SHOTS[s]
        d[f"sc{i}_t"] = T[spec["t"]][lang] if spec["t"] in T else o[spec["t"]]
        d[f"sc{i}_d"] = T[spec["d"]][lang] if spec["d"] in T else o[spec["d"]]
        alt = spec["alt"]
        d[f"sc{i}_alt"] = alt[lang] if isinstance(alt, dict) else (T[alt][lang] if alt in T else o[alt])
    for k in [k for k in d if re.fullmatch(r"s\d+_(t|d|alt)", k)]:
        del d[k]
    # The hero: the day's pick in each language's screenshot.
    for k in ("hero_alt", "hero_caption", "fear_aria", "fear_label"):
        d[k] = HERO[k][lang]
    d["install_tip"] = T["install_tip"][lang]
    d["legal_version"] = T["legal_version"][lang]
    body = o["privacy_body"]
    m = re.search(r'docs/PRIVACY\.md</a>[^<]*', body)
    assert m, lang
    d["privacy_body"] = body[:m.end()] + T["privacy_app_extra"][lang] + body[m.end():]

page = page[:start] + json.dumps(i18n, ensure_ascii=False) + page[start + length:]

# ---- Heroes ---------------------------------------------------------------------------------------------------
heroes = {lang: jpeg(shot(HERO["shot"][lang]), 1280, 74) for lang in LANGS}
page = re.sub(r'var HEROES = \{.*?\};', lambda m: "var HEROES = " + json.dumps(heroes) + ";", page, count=1, flags=re.S)
en = i18n["en"]
page = re.sub(
    r'(<img id="hero-img" src=")[^"]*(" width="1280" height="720" data-i18n-attr="alt:hero_alt" alt=")[^"]*(")',
    lambda m: m.group(1) + heroes["en"] + m.group(2) + html.escape(en["hero_alt"]) + m.group(3), page, count=1)
page = re.sub(r'(<span data-i18n="hero_caption">)[^<]*', lambda m: m.group(1) + html.escape(en["hero_caption"], quote=False), page, count=1)
page, n = re.subn(r'(<span [^>]*data-i18n="fear_label"[^>]*>)[^<]*', lambda m: m.group(1) + en["fear_label"], page, count=1)
assert n == 1, "fear_label"
marks = "".join('<i class="on"></i>' if i < HERO["fear"] else "<i></i>" for i in range(5))
page = re.sub(
    r'(<span class="fear" role="img" data-i18n-attr="aria-label:fear_aria" aria-label=")[^"]*(">)(?:<i[^>]*></i>){5}',
    lambda m: m.group(1) + html.escape(en["fear_aria"]) + m.group(2) + marks, page, count=1)

# ---- Features -------------------------------------------------------------------------------------------------
NEW = {1, 3, 4, 6}
items = []
for i in range(1, 13):
    title = html.escape(en[f"f{i}_t"], quote=False)
    badge = ' <span class="tag-new" data-i18n="badge_new">New</span>' if i in NEW else ""
    items.append(f'        <li><h3><span data-i18n="f{i}_t">{title}</span>{badge}</h3>'
                 f'<p data-i18n="f{i}_d">{html.escape(en[f"f{i}_d"], quote=False)}</p></li>')
page = re.sub(r'(<ul class="features">\n).*?(\n      </ul>)', lambda m: m.group(1) + "\n".join(items) + m.group(2),
              page, count=1, flags=re.S)

# ---- Screens --------------------------------------------------------------------------------------------------
figures = []
for i, s in enumerate(SHOTS["order"], 1):
    img = remote_composite("en") if s == "remote" else shot(SHOTS[s]["shot"])
    src = jpeg(img, 1280, 70)
    figures.append(
        f'        <figure class="shot"><img loading="lazy" src="{src}" width="1280" height="720" '
        f'data-i18n-attr="alt:sc{i}_alt" alt="{html.escape(en[f"sc{i}_alt"])}"><figcaption>'
        f'<b data-i18n="sc{i}_t">{html.escape(en[f"sc{i}_t"], quote=False)}</b>'
        f'<span data-i18n="sc{i}_d">{html.escape(en[f"sc{i}_d"], quote=False)}</span></figcaption></figure>')
page = re.sub(r'(<div class="row"[^>]*>\n).*?(\n      </div>\n      <p class="row-hint")',
              lambda m: m.group(1) + "\n".join(figures) + m.group(2), page, count=1, flags=re.S)

# ---- Install tip, size, meta, styles ---------------------------------------------------------------------------
page = page.replace('<li data-i18n="spec_size">2.7 MB</li>', f'<li data-i18n="spec_size">{en["spec_size"]}</li>', 1)
page = re.sub(r'(<ol class="steps">.*?</ol>)', lambda m: m.group(1) + f'\n        <p class="tip" data-i18n-html="install_tip">{en["install_tip"]}</p>',
              page, count=1, flags=re.S)
page = re.sub(r'(<p class="sub" data-i18n="sub">)[^<]*', lambda m: m.group(1) + html.escape(en["sub"], quote=False), page, count=1)
page = re.sub(r'(<span data-i18n="legal_version">)[^<]*', lambda m: m.group(1) + en["legal_version"], page, count=1)
page = re.sub(r'(<meta name="description" content=")[^"]*', lambda m: m.group(1) + html.escape(
    "SCARY is a free, open-source horror home screen for Android TV: 170 films and 40 series, tonight's pick, moods, "
    "a bucket list, your phone as the remote, and every streaming app in one place."), page, count=1)
page = re.sub(r'(<meta property="og:description" content=")[^"]*', lambda m: m.group(1) + html.escape(
    "SCARY: a free, open-source horror home screen for Android TV. Films and series, a bucket list and a phone remote. "
    "Download the APK, see the screens, support the project."), page, count=1)
CSS = """
/* "New" on a feature: text, so screen readers say it with the title. */
.tag-new { display: inline-block; vertical-align: middle; margin-inline-start: 8px; border: 1px solid var(--candle); color: var(--candle);
  border-radius: 999px; padding: 1px 9px; font: 700 var(--step--1)/1.4 var(--body); letter-spacing: 0; }
.tip { margin-top: 18px; padding: 14px 16px; border-inline-start: 3px solid var(--candle); background: var(--ember); border-radius: 8px; color: var(--ash); }
.tip strong { color: var(--bone); }
/* On a phone, an open document kept the width of its widest table and pushed the page sideways: the column now fits
   the screen (the table scrolls inside its own frame), and long links wrap. */
.docs { grid-template-columns: minmax(0, 1fr); }
.docs details { min-width: 0; }
.doc-body a { overflow-wrap: anywhere; }
"""
page = page.replace("</style>", CSS + "</style>", 1)

open(out_html, "w", encoding="utf-8").write(page)

# The server allows the inline style and script only by hash (Content-Security-Policy): print the new ones.
import hashlib  # noqa: E402
styles = re.findall(r"<style>(.*?)</style>", page, flags=re.S)
scripts = re.findall(r"<script>(.*?)</script>", page, flags=re.S)
assert len(styles) == 1 and len(scripts) == 1, (len(styles), len(scripts))
def csp_hash(text):
    return "'sha256-" + base64.b64encode(hashlib.sha256(text.encode("utf-8")).digest()).decode() + "'"
print("style-src", csp_hash(styles[0]))
print("script-src", csp_hash(scripts[0]))

# ---- og.jpg (1200x630): the English hero on the site's dark background ---------------------------------------
og = Image.new("RGB", (1200, 630), (14, 9, 8))
hero = shot(HERO["shot"]["en"]).convert("RGB").resize((1120, 630), Image.LANCZOS)
og.paste(hero, (40, 0))
og.save(out_og, "JPEG", quality=84, optimize=True, progressive=True)
print("written", out_html, len(page), "bytes")
