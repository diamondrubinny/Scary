# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

"""Brings site/index.html up to what SCARY does today (19 November 2026, version 0.5.16), in place.

Usage: python3 tools/site/update.py site/index.html

Unlike build.py (which needs the page of 24 October), this works on the page as it is and can be run again: every text
is set to its full value, never added to. It changes the hero's line, the twelve features, the size, the documents'
version, the privacy summary of the app and the meta descriptions. The screenshots, the cookie banner, the styles and
the rest of the script stay as they are. It prints the Content-Security-Policy hashes the server needs.
"""
import base64
import hashlib
import html
import json
import re
import sys

LANGS = ["en", "he", "ar", "pt", "ru"]
path = sys.argv[1]
page = open(path, encoding="utf-8").read()
start = page.index("var I18N = ") + len("var I18N = ")
i18n, length = json.JSONDecoder().raw_decode(page[start:])
old = {lang: dict(i18n[lang]) for lang in LANGS}


def L(en, he, ar, pt, ru):
    return {"en": en, "he": he, "ar": ar, "pt": pt, "ru": ru}


SUB = L(
    "SCARY turns your Android TV into a horror cinema. 450 hand-picked horror films and thrillers and 40 series with real posters, a new film for tonight every evening, rows from the web that never run out, a player for your own files, and Stremio, Google Play and all your apps in one place.",
    "SCARY הופך את ה־Android TV שלכם לאולם קולנוע של אימה: 450 סרטי אימה ומתח ו־40 סדרות שנבחרו ביד, עם פוסטרים אמיתיים, סרט חדש לערב בכל ערב, שורות מהרשת שלא נגמרות, נגן לקבצים שלכם, ו־Stremio, ‏Google Play וכל האפליקציות שלכם במקום אחד.",
    "يحوّل SCARY تلفاز Android TV إلى سينما رعب: ‏450 فيلم رعب وإثارة و40 مسلسلًا مختارة بعناية مع ملصقات حقيقية، وفيلم جديد لكل ليلة، وصفوف من الإنترنت لا تنتهي، ومشغّل لملفاتك، وStremio وGoogle Play وكل تطبيقاتك في مكان واحد.",
    "O SCARY transforma sua Android TV num cinema de terror: 450 filmes de terror e suspense e 40 séries escolhidos a dedo, com pôsteres de verdade, um filme novo para cada noite, fileiras da internet que nunca acabam, um player para os seus arquivos, e Stremio, Google Play e todos os seus apps num só lugar.",
    "SCARY превращает ваш Android TV в кинотеатр ужасов: 450 отобранных вручную фильмов ужасов и триллеров и 40 сериалов с настоящими постерами, новый фильм на каждый вечер, бесконечные подборки из сети, плеер для ваших файлов, а также Stremio, Google Play и все ваши приложения в одном месте.",
)

LIBRARY_T = L("450 films and 40 series", "450 סרטים ו־40 סדרות", "450 فيلمًا و40 مسلسلًا", "450 filmes e 40 séries", "450 фильмов и 40 сериалов")
LIBRARY_D = L(
    "Hand-picked horror and psychological thrillers, each with a fear meter from 1 to 5, and 3,700 more that search finds even without a network. A Series tab brings Hill House, Stranger Things, Dark, Kingdom and more.",
    "סרטי אימה ומתח פסיכולוגי שנבחרו ביד, לכל אחד מד פחד מ־1 עד 5, ועוד 3,700 שהחיפוש מוצא גם בלי רשת. טאב סדרות מביא את Hill House, ‏Stranger Things, ‏Dark, ‏Kingdom ועוד.",
    "أفلام رعب وإثارة نفسية مختارة بعناية، لكل منها مقياس رعب من 1 إلى 5، و3,700 فيلم آخر يجدها البحث حتى بلا شبكة. وتبويب للمسلسلات يضم Hill House وStranger Things وDark وKingdom وغيرها.",
    "Terror e suspense psicológico escolhidos a dedo, cada um com um medidor de medo de 1 a 5, e mais 3.700 que a busca encontra mesmo sem rede. A aba Séries traz Hill House, Stranger Things, Dark, Kingdom e mais.",
    "Отобранные вручную ужасы и психологические триллеры, у каждого шкала страха от 1 до 5, и ещё 3700, которые поиск находит даже без сети. Вкладка «Сериалы»: Hill House, Stranger Things, Dark, Kingdom и другие.",
)
ROWS_D = L(
    "Popular now, new this year, in the spotlight, sci-fi horror, crime and suspense, and year after year back to 1920: rows from the web are read three times a day and keep loading for as long as you scroll. With your own TMDB key come 13 more: just released, hidden gems, and the horror of each decade.",
    "פופולרי עכשיו, חדש השנה, מה שמדברים עליו, אימה ומדע בדיוני, פשע ומתח, ושנה אחרי שנה אחורה עד 1920: השורות מהרשת נקראות שלוש פעמים ביום וממשיכות להיטען כל עוד גוללים. עם מפתח TMDB משלכם מתווספות עוד 13: יצאו עכשיו, פנינים נסתרות ואימה של כל עשור.",
    "الرائج الآن، وجديد هذا العام، وما تحت الأضواء، ورعب الخيال العلمي، والجريمة والتشويق، وعامًا بعد عام رجوعًا إلى 1920: تُقرأ الصفوف من الإنترنت ثلاث مرات يوميًا وتواصل التحميل ما دمت تتصفح. ومع مفتاح TMDB الخاص بك 13 صفًا إضافيًا: صدرت للتو، وجواهر خفية، ورعب كل عقد.",
    "Em alta agora, novos deste ano, em destaque, terror de ficção científica, crime e suspense, e ano após ano até 1920: as fileiras da internet são lidas três vezes ao dia e continuam carregando enquanto você rola. Com a sua chave do TMDB vêm mais 13: acabaram de sair, joias escondidas e o terror de cada década.",
    "Популярное сейчас, новинки года, в центре внимания, фантастические ужасы, криминал и напряжение, и год за годом назад до 1920-го: подборки из сети читаются три раза в день и подгружаются, пока вы листаете. С вашим ключом TMDB добавляются ещё 13: только что вышли, скрытые жемчужины и ужасы каждого десятилетия.",
)
PLAYER_T = L("A player for your own files", "נגן לקבצים שלכם", "مشغّل لملفاتك", "Um player para os seus arquivos", "Плеер для ваших файлов")
PLAYER_D = L(
    "Connect your own Real-Debrid account and SCARY plays the files already in it, and only those: it never looks for sources. Audio and subtitle tracks, subtitles found by the film's name, your series by season and episode, and it goes on from where you stopped.",
    "חברו את חשבון ה־Real-Debrid שלכם, ו־SCARY תנגן את הקבצים שכבר נמצאים בו, ורק אותם: היא לא מחפשת מקורות. רצועות שמע וכתוביות, כתוביות שנמצאות לפי שם הסרט, הסדרות שלכם לפי עונה ופרק, והמשך מהמקום שבו עצרתם.",
    "اربط حسابك على Real-Debrid، وسيشغّل SCARY الملفات الموجودة فيه مسبقًا، وهي فقط: لا يبحث عن مصادر. مسارات الصوت والترجمة، وترجمة تُعثر عليها باسم الفيلم، ومسلسلاتك حسب الموسم والحلقة، والمتابعة من حيث توقفت.",
    "Conecte a sua conta do Real-Debrid e o SCARY reproduz os arquivos que já estão nela, e só eles: nunca procura fontes. Faixas de áudio e de legenda, legendas encontradas pelo nome do filme, suas séries por temporada e episódio, e continua de onde você parou.",
    "Подключите свой аккаунт Real-Debrid, и SCARY будет воспроизводить файлы, которые в нём уже есть, и только их: источники он не ищет. Звуковые дорожки и субтитры, субтитры по названию фильма, ваши сериалы по сезонам и сериям, и продолжение с места остановки.",
)
ACCOUNTS_T = L(
    "Your TMDB and Stremio accounts, in step", "חשבונות ה־TMDB וה־Stremio שלכם, מסונכרנים", "حساباك على TMDB وStremio، متزامنان",
    "Suas contas do TMDB e do Stremio, em sincronia", "Ваши аккаунты TMDB и Stremio — синхронно",
)
# Added after the TMDB account's own text (kept as it is in each language).
STREMIO = L(
    " Connect your Stremio account too, and what you started there waits on the home screen.",
    " אפשר לחבר גם את חשבון ה־Stremio, ומה שהתחלתם לראות שם מחכה במסך הבית.",
    " ويمكنك ربط حسابك على Stremio أيضًا، فيظهر ما بدأت مشاهدته هناك على الشاشة الرئيسية.",
    " Conecte também a sua conta do Stremio, e o que você começou lá espera na tela inicial.",
    " Подключите и аккаунт Stremio, и начатое там будет ждать на главном экране.",
)
ASK_T = L("Ask SCARY ✦", "שאלו את SCARY ✦", "اسأل SCARY ✦", "Pergunte ao SCARY ✦", "Спросите SCARY ✦")
ASK_D = L(
    "Say what you feel like in your own words, \"something slow and Scandinavian\", and get films from the library; or ask for a few spoiler-free words about a film. Optional, and only after you agree: with a free service, or with your own key.",
    "אומרים במילים שלכם מה בא לכם, \"משהו איטי וסקנדינבי\", ומקבלים סרטים מהספרייה; או מבקשים כמה מילים על סרט, בלי ספוילרים. לא חובה, ורק אחרי שהסכמתם: עם שירות חינמי או עם מפתח משלכם.",
    "قل ما تشتهيه بكلماتك، «شيء بطيء وإسكندنافي»، واحصل على أفلام من المكتبة؛ أو اطلب كلمات قليلة عن فيلم بلا حرق للأحداث. اختياري، وبعد موافقتك فقط: بخدمة مجانية أو بمفتاحك الخاص.",
    "Diga o que você quer com as suas palavras, \"algo lento e escandinavo\", e receba filmes da biblioteca; ou peça umas palavras sobre um filme, sem spoilers. Opcional, e só depois que você concordar: com um serviço gratuito ou com a sua própria chave.",
    "Скажите своими словами, чего хочется, «что-нибудь медленное и скандинавское», и получите фильмы из библиотеки; или попросите пару слов о фильме без спойлеров. По желанию и только после вашего согласия: через бесплатный сервис или с вашим ключом.",
)
DEVICES_T = L(
    "TV, tablet and phone, in five languages", "טלוויזיה, טאבלט וטלפון, בחמש שפות", "تلفاز وجهاز لوحي وهاتف، بخمس لغات",
    "TV, tablet e celular, em cinco idiomas", "Телевизор, планшет и телефон, на пяти языках",
)
DEVICES_D = L(
    "Made for the TV and its remote, and it installs on tablets and phones too. English, Hebrew, Arabic, Portuguese and Russian, right to left where it should be, with full TalkBack support. Family night shows only the gentlest films, and leaving it takes your PIN. The official build updates itself.",
    "בנוי לטלוויזיה ולשלט, ומותקן גם על טאבלטים וטלפונים. עברית, ערבית, אנגלית, פורטוגזית ורוסית, מימין לשמאל איפה שצריך, עם תמיכה מלאה ב־TalkBack. ערב משפחתי מציג רק את הסרטים העדינים ביותר, והיציאה ממנו דורשת את קוד ה־PIN. הגרסה הרשמית מתעדכנת מעצמה.",
    "صُمّم للتلفاز وجهاز التحكم، ويُثبَّت على الأجهزة اللوحية والهواتف أيضًا. العربية والعبرية والإنجليزية والبرتغالية والروسية، من اليمين إلى اليسار حيث يلزم، مع دعم كامل لـ TalkBack. السهرة العائلية تعرض أخف الأفلام فقط، والخروج منها يتطلب رمز PIN. والنسخة الرسمية تحدّث نفسها.",
    "Feito para a TV e o controle remoto, e também se instala em tablets e celulares. Português, inglês, hebraico, árabe e russo, da direita para a esquerda onde deve ser, com suporte completo a TalkBack. A noite em família mostra só os filmes mais leves, e para sair dela é preciso o PIN. A versão oficial se atualiza sozinha.",
    "Сделан для телевизора и пульта, но ставится и на планшеты и телефоны. Русский, английский, португальский, иврит и арабский, справа налево там, где нужно, с полной поддержкой TalkBack. Семейный вечер показывает только самые мягкие фильмы, а чтобы выйти из него, нужен PIN-код. Официальная сборка обновляется сама.",
)
LEGAL_VERSION = L(
    "Version 1.3 · Last updated 19 November 2026", "גרסה 1.3 · עודכן לאחרונה ב־19 בנובמבר 2026", "الإصدار 1.3 · آخر تحديث في 19 نوفمبر 2026",
    "Versão 1.3 · Atualizado em 19 de novembro de 2026", "Версия 1.3 · Обновлено 19 ноября 2026 г.",
)
# The app's privacy in short, after the link to docs/PRIVACY.md: the whole rest of that paragraph.
PRIVACY_APP = L(
    ". In short: no SCARY account, no ads and no tracking, and we receive nothing; your settings stay on your device. The phone remote works only on your home network, straight between your phone and your TV, and keeps nothing. What reaches an outside service is yours to turn on or off: film data from Cinemeta (on unless you turn it off), your own TMDB key and account, your own Stremio and Real-Debrid accounts (their keys stay encrypted on the device and go only to that service), subtitles found online (the film's IMDb number goes to a subtitle service; on in the player unless you turn it off), Ask SCARY (your question goes to the AI service you choose, only after you agree) and trailers from YouTube (only after you agree). Each of these services sees your IP address. The official build asks GitHub whether there is a new version.",
    ". בקצרה: בלי חשבון SCARY, בלי פרסומות ובלי מעקב, ואנחנו לא מקבלים דבר; ההגדרות נשארות במכשיר. השלט בטלפון עובד רק ברשת הביתית, ישירות בין הטלפון לטלוויזיה, ולא שומר כלום. מה שמגיע לשירות חיצוני נתון לבחירתכם: מידע על סרטים מ־Cinemeta (פעיל אלא אם כיביתם), מפתח וחשבון TMDB משלכם, חשבונות Stremio ו־Real-Debrid משלכם (המפתחות שלהם נשארים מוצפנים במכשיר ונשלחים רק לאותו שירות), כתוביות מהאינטרנט (מספר ה־IMDb של הסרט נשלח לשירות כתוביות; פעיל בנגן אלא אם כיביתם), \"שאלו את SCARY\" (השאלה נשלחת לשירות ה־AI שבחרתם, רק אחרי שהסכמתם) וטריילרים מ־YouTube (רק אחרי שהסכמתם). כל אחד מהשירותים האלה רואה את כתובת ה־IP שלכם. הגרסה הרשמית בודקת מול GitHub אם יש גרסה חדשה.",
    ". باختصار: بلا حساب SCARY وبلا إعلانات وبلا تتبع، ولا يصلنا شيء؛ وتبقى إعداداتك على جهازك. يعمل جهاز التحكم بالهاتف على شبكتك المنزلية فقط، مباشرة بين هاتفك والتلفاز، ولا يحتفظ بأي شيء. وما يصل إلى خدمة خارجية هو باختيارك: بيانات الأفلام من Cinemeta (مفعّلة ما لم توقفها)، ومفتاحك وحسابك على TMDB، وحساباك على Stremio وReal-Debrid (تبقى مفاتيحها مشفّرة على الجهاز ولا تُرسل إلا إلى تلك الخدمة)، والترجمة من الإنترنت (يُرسَل رقم IMDb للفيلم إلى خدمة ترجمة؛ مفعّلة في المشغّل ما لم توقفها)، و«اسأل SCARY» (يُرسَل سؤالك إلى خدمة الذكاء الاصطناعي التي تختارها، بعد موافقتك فقط)، والإعلانات التشويقية من YouTube (بعد موافقتك فقط). وكل خدمة منها ترى عنوان IP الخاص بك. والنسخة الرسمية تسأل GitHub عن وجود إصدار جديد.",
    ". Em resumo: sem conta do SCARY, sem anúncios e sem rastreamento, e nós não recebemos nada; suas configurações ficam no aparelho. O controle pelo celular funciona só na sua rede de casa, direto entre o celular e a TV, e não guarda nada. O que chega a um serviço de fora é escolha sua: dados de filmes do Cinemeta (ligado, a menos que você desligue), a sua chave e conta do TMDB, as suas contas do Stremio e do Real-Debrid (as chaves ficam criptografadas no aparelho e vão só para esse serviço), legendas da internet (o número do filme no IMDb vai para um serviço de legendas; ligado no player, a menos que você desligue), Pergunte ao SCARY (a sua pergunta vai para o serviço de IA que você escolher, só depois que você concordar) e trailers do YouTube (só depois que você concordar). Cada um desses serviços vê o seu endereço IP. A versão oficial pergunta ao GitHub se há uma versão nova.",
    ". Коротко: без аккаунта SCARY, рекламы и слежки, и мы ничего не получаем; настройки остаются на устройстве. Пульт в телефоне работает только в домашней сети, напрямую между телефоном и телевизором, и ничего не сохраняет. Что попадает во внешний сервис, решаете вы: данные о фильмах из Cinemeta (включено, пока вы не выключите), ваш ключ и аккаунт TMDB, ваши аккаунты Stremio и Real-Debrid (их ключи хранятся на устройстве в зашифрованном виде и отправляются только в этот сервис), субтитры из интернета (номер фильма в IMDb отправляется в сервис субтитров; в плеере включено, пока вы не выключите), «Спросите SCARY» (ваш вопрос уходит в выбранный вами ИИ-сервис, только после вашего согласия) и трейлеры с YouTube (только после вашего согласия). Каждый из этих сервисов видит ваш IP-адрес. Официальная сборка спрашивает GitHub, есть ли новая версия.",
)
META = ("SCARY is a free, open-source horror home screen for Android TV: 450 films and 40 series, tonight's pick, rows that "
        "never run out, a player for your own files, your phone as the remote, and every streaming app in one place.")
OG = ("SCARY: a free, open-source horror home screen for Android TV. Films and series, a bucket list, a phone remote and a "
      "player for your own files. Download the APK, see the screens, support the project.")
SIZE = "4.3"  # build 329: 4,486,566 bytes, 4.28 MiB (the site has always shown MiB)


def feature(en_title_starts):
    """The number of the feature whose English title starts so, in the page as it is now."""
    hits = [int(m.group(1)) for k in old["en"] if (m := re.fullmatch(r"f(\d+)_t", k)) and old["en"][k].startswith(en_title_starts)]
    assert len(hits) == 1, (en_title_starts, hits)
    return hits[0]


rows, remote, bucket, sorted_, accounts = feature("Rows that"), feature("Your phone is"), feature("Bucket list"), feature("Sorted by"), feature("Your TMDB")
moods, where, search = feature("What do you feel"), feature("Where to watch"), feature("Search, or")

for lang in LANGS:
    d, o = i18n[lang], old[lang]
    def keep(n):
        return o[f"f{n}_t"], o[f"f{n}_d"]
    # The TMDB account's own text, without the sentence on Stremio from an earlier run.
    tmdb_d = o[f"f{accounts}_d"].split(STREMIO[lang])[0]
    features = [
        (LIBRARY_T[lang], LIBRARY_D[lang]),
        (o[f"f{rows}_t"], ROWS_D[lang]),
        (PLAYER_T[lang], PLAYER_D[lang]),
        keep(remote), keep(bucket), keep(sorted_),
        (ACCOUNTS_T[lang], tmdb_d + STREMIO[lang]),
        keep(moods), keep(where),
        (ASK_T[lang], ASK_D[lang]),
        keep(search),
        (DEVICES_T[lang], DEVICES_D[lang]),
    ]
    for n, (t, dsc) in enumerate(features, 1):
        d[f"f{n}_t"], d[f"f{n}_d"] = t, dsc
    d["sub"] = SUB[lang]
    d["legal_version"] = LEGAL_VERSION[lang]
    d["spec_size"], n = re.subn(r"\d([.,])\d", lambda m: SIZE.replace(".", m.group(1)), o["spec_size"], count=1)
    assert n == 1, (lang, o["spec_size"])
    body = o["privacy_body"]
    m = re.search(r"docs/PRIVACY\.md</a>", body)
    end = body.index("</p>", m.end())
    d["privacy_body"] = body[:m.end()] + html.escape(PRIVACY_APP[lang], quote=False) + body[end:]

page = page[:start] + json.dumps(i18n, ensure_ascii=False) + page[start + length:]
en = i18n["en"]

# The page's own (English) text, for before the script runs and for readers without one.
NEW = {3, 7, 10, 12}
items = []
for i in range(1, 13):
    badge = ' <span class="tag-new" data-i18n="badge_new">New</span>' if i in NEW else ""
    items.append(f'        <li><h3><span data-i18n="f{i}_t">{html.escape(en[f"f{i}_t"], quote=False)}</span>{badge}</h3>'
                 f'<p data-i18n="f{i}_d">{html.escape(en[f"f{i}_d"], quote=False)}</p></li>')
page, n = re.subn(r'(<ul class="features">\n).*?(\n      </ul>)', lambda m: m.group(1) + "\n".join(items) + m.group(2), page, count=1, flags=re.S)
assert n == 1, "features"
for pattern, value in (
    (r'(<p class="sub" data-i18n="sub">)[^<]*', html.escape(en["sub"], quote=False)),
    (r'(<span data-i18n="legal_version">)[^<]*', en["legal_version"]),
    (r'(<li data-i18n="spec_size">)[^<]*', en["spec_size"]),
    (r'(<meta name="description" content=")[^"]*', html.escape(META)),
    (r'(<meta property="og:description" content=")[^"]*', html.escape(OG)),
):
    page, n = re.subn(pattern, lambda m, v=value: m.group(1) + v, page, count=1)
    assert n == 1, pattern
# The privacy summary in the page's own markup.
i = page.index('data-i18n-html="privacy_body"')
m = re.compile(r"docs/PRIVACY\.md</a>").search(page, i)
end = page.index("</p>", m.end())
page = page[:m.end()] + html.escape(PRIVACY_APP["en"], quote=False) + page[end:]

open(path, "w", encoding="utf-8").write(page)

styles = re.findall(r"<style>(.*?)</style>", page, flags=re.S)
scripts = re.findall(r"<script>(.*?)</script>", page, flags=re.S)
assert len(styles) == 1 and len(scripts) == 1, (len(styles), len(scripts))
def csp_hash(text):
    return "'sha256-" + base64.b64encode(hashlib.sha256(text.encode("utf-8")).digest()).decode() + "'"
print("style-src", csp_hash(styles[0]))
print("script-src", csp_hash(scripts[0]))
print("written", path, len(page), "bytes")
