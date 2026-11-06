# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

# Which CI screenshot goes where, and the alt texts for what each one shows (CI run of 8d68ec6, 31 October 2026:
# tonight's pick is Black Swan, 2010, fear 3/5, in every language; family night shows Beetlejuice).

HERO = {
    "shot": {"en": "40-en-home", "he": "01-home", "ar": "50-ar-home", "pt": "14-home-pt", "ru": "17-home-ru"},
    "fear": 3,
    "hero_alt": {
        "en": "SCARY's home screen on a TV, in English: Black Swan as tonight's pick, the Movies and Series tabs, buttons to watch it in Stremio, see the trailer or details, and the apps row below.",
        "he": "מסך הבית של SCARY בטלוויזיה, בעברית: Black Swan כסרט של הלילה, הטאבים סרטים וסדרות, כפתורים לצפייה ב־Stremio, לטריילר ולפרטים, ושורת האפליקציות מתחת.",
        "ar": "الشاشة الرئيسية لـ SCARY على التلفاز بالعربية: فيلم Black Swan اختيارًا لليلة، وتبويبا الأفلام والمسلسلات، وأزرار للمشاهدة في Stremio والإعلان والتفاصيل، وصف التطبيقات في الأسفل.",
        "pt": "Tela inicial do SCARY em uma TV, em português: Black Swan como filme da noite, as abas Filmes e Séries, botões para assistir no Stremio, ver o trailer ou os detalhes, e a fileira de apps abaixo.",
        "ru": "Главный экран SCARY на телевизоре, на русском: «Black Swan» как фильм этой ночи, вкладки «Фильмы» и «Сериалы», кнопки «Смотреть в Stremio», «Трейлер» и «Подробнее», ниже ряд приложений.",
    },
    "hero_caption": {
        "en": "Tonight's pick: Black Swan, 2010",
        "he": "הסרט של הלילה: Black Swan, ‏2010",
        "ar": "اختيار الليلة: Black Swan، ‏2010",
        "pt": "O filme da noite: Black Swan, 2010",
        "ru": "Фильм этой ночи: «Black Swan», 2010",
    },
    "fear_aria": {"en": "Fear 3 out of 5", "he": "רמת פחד 3 מתוך 5", "ar": "مستوى الرعب 3 من 5", "pt": "Medo 3 de 5", "ru": "Страх 3 из 5"},
    "fear_label": {"en": "Fear 3/5", "he": "פחד 3/5", "ar": "الرعب 3/5", "pt": "Medo 3/5", "ru": "Страх 3/5"},
}

SHOTS = {
    "order": ["home", "series", "remote", "bucket", "next", "time", "moods", "details", "shiver", "search", "family",
              "screensaver", "privacy"],
    "home": {"shot": "40-en-home", "t": "s_home_t", "d": "s_home_d", "alt": {
        "en": "Home screen with Black Swan as tonight's pick, the Movies and Series tabs at the top, and the apps row below.",
        "he": "מסך הבית עם Black Swan כסרט של הלילה, הטאבים סרטים וסדרות למעלה, ושורת האפליקציות מתחת.",
        "ar": "الشاشة الرئيسية مع Black Swan اختيارًا لليلة، وتبويبا الأفلام والمسلسلات في الأعلى، وصف التطبيقات في الأسفل.",
        "pt": "Tela inicial com Black Swan como filme da noite, as abas Filmes e Séries no alto e a fileira de apps abaixo.",
        "ru": "Главный экран: «Black Swan» как фильм этой ночи, вкладки «Фильмы» и «Сериалы» вверху и ряд приложений внизу."}},
    "series": {"shot": "69-en-series-rows", "t": "s_series_t", "d": "s_series_d", "alt": {
        "en": "The Series tab: Crystal Lake as the pick, and a row of popular horror series with Carrie, From, Yaga and American Horror Story.",
        "he": "טאב הסדרות: Crystal Lake בראש, ושורת סדרות אימה פופולריות עם Carrie, ‏From, ‏Yaga ו־American Horror Story.",
        "ar": "تبويب المسلسلات: Crystal Lake في الأعلى، وصف من مسلسلات الرعب الرائجة مع Carrie وFrom وYaga وAmerican Horror Story.",
        "pt": "A aba Séries: Crystal Lake em destaque e uma fileira de séries de terror em alta, com Carrie, From, Yaga e American Horror Story.",
        "ru": "Вкладка «Сериалы»: «Crystal Lake» вверху и подборка популярных хоррор-сериалов с «Carrie», «From», «Yaga» и «American Horror Story»."}},
    "remote": {"tv": "73-en-phone-remote", "t": "s_remote_t", "d": "s_remote_d", "alt": "s_remote_alt"},
    "bucket": {"shot": "70-en-bucket-list", "t": "s_bucket_t", "d": "s_bucket_d", "alt": {
        "en": "The bucket list: Psycho, Rosemary's Baby and The Exorcist, numbered in order, with Start, Pick more films, and a total time of 6:08.",
        "he": "ה־Bucket list:‏ Psycho, ‏Rosemary's Baby ו־The Exorcist ממוספרים לפי הסדר, עם \"התחלה\", \"בחירת עוד סרטים\" וזמן כולל של 6:08.",
        "ar": "قائمة Bucket list: ‏Psycho وRosemary's Baby وThe Exorcist مرقّمة بالترتيب، مع «ابدأ» و«اختيار أفلام أخرى» ومدة إجمالية 6:08.",
        "pt": "A bucket list: Psycho, Rosemary's Baby e The Exorcist, numerados em ordem, com Começar, Escolher mais filmes e tempo total de 6:08.",
        "ru": "Bucket list: «Psycho», «Rosemary's Baby» и «The Exorcist» по порядку, с кнопками «Начать» и «Выбрать ещё» и общим временем 6:08."}},
    "next": {"shot": "71-en-bucket-prompt", "t": "s_next_t", "d": "s_next_d", "alt": {
        "en": "The question on coming back: Finished Psycho? Next: Rosemary's Baby, with Play Rosemary's Baby and Not now.",
        "he": "השאלה בחזרה: \"סיימת את Psycho?\" הבא: Rosemary's Baby, עם \"לנגן את Rosemary's Baby\" ו\"לא עכשיו\".",
        "ar": "السؤال عند العودة: «هل أنهيت Psycho؟» التالي: Rosemary's Baby، مع «شغّل Rosemary's Baby» و«ليس الآن».",
        "pt": "A pergunta na volta: Terminou Psycho? Próximo: Rosemary's Baby, com Tocar Rosemary's Baby e Agora não.",
        "ru": "Вопрос при возвращении: «Досмотрели Psycho?» Далее: «Rosemary's Baby», с кнопками «Включить Rosemary's Baby» и «Не сейчас»."}},
    "time": {"shot": "72-en-time-double", "t": "s_time_t", "d": "s_time_d", "alt": {
        "en": "How much time do you have? with Double feature chosen, under Black Swan as tonight's pick, and a row of horror films below.",
        "he": "\"כמה זמן יש לך?\" עם \"סרט כפול\" נבחר, מתחת ל־Black Swan כסרט של הלילה, ושורת סרטי אימה מתחת.",
        "ar": "«كم لديك من الوقت؟» مع اختيار «عرض مزدوج»، تحت Black Swan اختيارًا لليلة، وصف من أفلام الرعب في الأسفل.",
        "pt": "Quanto tempo você tem? com Sessão dupla escolhida, abaixo de Black Swan como filme da noite, e uma fileira de filmes de terror.",
        "ru": "«Сколько у вас времени?» с выбранным «Двойным сеансом» под фильмом ночи «Black Swan» и подборкой ужасов ниже."}},
    "moods": {"shot": "55-en-moods", "t": "s1_t", "d": "s_moods_d", "alt": {
        "en": "Mood choices under tonight's pick; Don't sleep tonight is selected, with decades to choose from below.",
        "he": "בחירת מצב רוח מתחת לסרט של הלילה: \"לא לישון הלילה\" נבחר, ומתחת אפשר לבחור עשור.",
        "ar": "خيارات المزاج تحت فيلم الليلة؛ «لا نوم الليلة» مختار، ويمكن اختيار عقد في الأسفل.",
        "pt": "Escolhas de clima abaixo do filme da noite; Sem dormir esta noite está selecionado, com décadas para escolher abaixo.",
        "ru": "Выбор настроения под фильмом ночи; выбрано «Бессонная ночь», ниже можно выбрать десятилетие."}},
    "details": {"shot": "41-en-details", "t": "s2_t", "d": "s_details_d", "alt": {
        "en": "Movie page for Hereditary with its title art, fear 5 of 5, buttons for Stremio, trailer, My list, Mark as watched, Loved it, Not for me and the bucket list, apps to search in, and a QR code to share the film.",
        "he": "עמוד הסרט Hereditary עם לוגו, פחד 5 מתוך 5, כפתורים ל־Stremio, טריילר, הרשימה שלי, סימון כנצפה, אהבתי, לא בשבילי ו־Bucket list, אפליקציות לחיפוש וקוד QR לשיתוף הסרט.",
        "ar": "صفحة فيلم Hereditary مع شعاره، والرعب 5 من 5، وأزرار Stremio والإعلان وقائمتي وشوهد وأحببته وليس لي وBucket list، وتطبيقات للبحث، ورمز QR لمشاركة الفيلم.",
        "pt": "Página do filme Hereditary com o logo, medo 5 de 5, botões para Stremio, trailer, Minha lista, Marcar como assistido, Amei, Não é pra mim e a bucket list, apps para buscar e um código QR para compartilhar o filme.",
        "ru": "Страница фильма «Hereditary» с логотипом, страх 5 из 5, кнопки Stremio, трейлер, «Мой список», «Просмотрено», «Понравилось», «Не моё» и bucket list, приложения для поиска и QR-код, чтобы поделиться."}},
    "shiver": {"shot": "58-en-shiver", "t": "s3_t", "d": "s3_d", "alt": "s3_alt"},
    "search": {"shot": "42-en-search-web", "t": "s6_t", "d": "s6_d", "alt": "s6_alt"},
    "family": {"shot": "56-en-family-night", "t": "s5_t", "d": "s5_d", "alt": {
        "en": "Home screen in family night mode with a badge in the top bar and Beetlejuice as the pick.",
        "he": "מסך הבית במצב ערב משפחתי, עם תג בסרגל העליון ו־Beetlejuice כסרט של הלילה.",
        "ar": "الشاشة الرئيسية في وضع السهرة العائلية مع شارة في الشريط العلوي وBeetlejuice اختيارًا لليلة.",
        "pt": "Tela inicial no modo noite em família, com um selo na barra superior e Beetlejuice como filme da noite.",
        "ru": "Главный экран в режиме семейного вечера: значок в верхней панели и «Beetlejuice» как фильм ночи."}},
    "screensaver": {"shot": "48-en-screensaver", "t": "s7_t", "d": "s7_d", "alt": "s7_alt"},
    "privacy": {"shot": "49-en-settings-about", "t": "s8_t", "d": "s8_d", "alt": "s8_alt"},
}
