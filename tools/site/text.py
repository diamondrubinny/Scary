# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

# New and changed texts for scary.network, in the five languages. Keys not listed keep their current text.
# Screens whose content depends on the day's pick get their alt text in build.py, from what the CI shot shows.

LANGS = ["en", "he", "ar", "pt", "ru"]

T = {
"sub": {
 "en": "SCARY turns your Android TV into a horror cinema. 170 hand-picked horror films and thrillers and 40 series with real posters, a new film for tonight every evening, rows from the web that never run out, and Stremio, Google Play and all your apps in one place.",
 "he": "SCARY הופך את ה־Android TV שלכם לאולם קולנוע של אימה: 170 סרטי אימה ומתח ו־40 סדרות שנבחרו ביד, עם פוסטרים אמיתיים, סרט חדש לערב בכל ערב, שורות מהרשת שלא נגמרות, ו־Stremio, ‏Google Play וכל האפליקציות שלכם במקום אחד.",
 "ar": "يحوّل SCARY تلفاز Android TV إلى سينما رعب: ‏170 فيلم رعب وإثارة و40 مسلسلًا مختارة بعناية مع ملصقات حقيقية، وفيلم جديد لكل ليلة، وصفوف من الإنترنت لا تنتهي، وStremio وGoogle Play وكل تطبيقاتك في مكان واحد.",
 "pt": "O SCARY transforma sua Android TV num cinema de terror: 170 filmes de terror e suspense e 40 séries escolhidos a dedo, com pôsteres de verdade, um filme novo para cada noite, fileiras da internet que nunca acabam, e Stremio, Google Play e todos os seus apps num só lugar.",
 "ru": "SCARY превращает ваш Android TV в кинотеатр ужасов: 170 отобранных вручную фильмов ужасов и триллеров и 40 сериалов с настоящими постерами, новый фильм на каждый вечер, бесконечные подборки из сети, а также Stremio, Google Play и все ваши приложения в одном месте.",
},
"badge_new": {"en": "New", "he": "חדש", "ar": "جديد", "pt": "Novo", "ru": "Новое"},
# Features, in their new order.
"f1_t": {"en": "170 films and 40 series", "he": "170 סרטים ו־40 סדרות", "ar": "170 فيلمًا و40 مسلسلًا", "pt": "170 filmes e 40 séries", "ru": "170 фильмов и 40 сериалов"},
"f1_d": {
 "en": "Hand-picked horror and psychological thrillers, each with a fear meter from 1 to 5. A Series tab brings Hill House, Stranger Things, Dark, Kingdom and more.",
 "he": "סרטי אימה ומתח פסיכולוגי שנבחרו ביד, לכל אחד מד פחד מ־1 עד 5. טאב הסדרות מביא את Hill House, ‏Stranger Things, ‏Dark, ‏Kingdom ועוד.",
 "ar": "أفلام رعب وإثارة نفسية مختارة بعناية، لكل منها مقياس رعب من 1 إلى 5. ويضم تبويب المسلسلات Hill House وStranger Things وDark وKingdom وغيرها.",
 "pt": "Terror e suspense psicológico escolhidos a dedo, cada um com um medidor de medo de 1 a 5. A aba Séries traz Hill House, Stranger Things, Dark, Kingdom e mais.",
 "ru": "Отобранные вручную ужасы и психологические триллеры, у каждого шкала страха от 1 до 5. Во вкладке «Сериалы» — «Призраки дома на холме», «Очень странные дела», «Тьма», «Королевство» и другие.",
},
"f2_t": {"en": "Rows that never run out", "he": "שורות שלא נגמרות", "ar": "صفوف لا تنتهي", "pt": "Fileiras que nunca acabam", "ru": "Подборки без конца"},
"f2_d": {
 "en": "Popular now, new this year, sci-fi horror, crime and suspense, and year after year back to 1920: rows from the web keep loading for as long as you scroll.",
 "he": "פופולרי עכשיו, חדש השנה, אימה ומדע בדיוני, פשע ומתח, ושנה אחרי שנה אחורה עד 1920: השורות מהרשת ממשיכות להיטען כל עוד גוללים.",
 "ar": "الرائج الآن، وجديد هذا العام، ورعب الخيال العلمي، والجريمة والتشويق، وعامًا بعد عام حتى 1920: تواصل الصفوف من الإنترنت التحميل ما دمت تتصفح.",
 "pt": "Em alta, novos deste ano, terror de ficção científica, crime e suspense, e ano a ano até 1920: as fileiras da internet continuam carregando enquanto você rolar.",
 "ru": "Популярное сейчас, новинки года, фантастические ужасы, криминал и напряжение, и год за годом до 1920-го: подборки из сети подгружаются, пока вы листаете.",
},
"f3_t": {"en": "Your phone is the remote", "he": "הטלפון הוא השלט", "ar": "هاتفك هو جهاز التحكم", "pt": "O celular vira controle", "ru": "Телефон вместо пульта"},
"f3_d": {
 "en": "Scan the code on the TV and your phone's browser takes over: arrows, search, and pasting a TMDB key in a second. Turn on SCARY's accessibility service and it controls Stremio and other apps too. Home Wi-Fi only, every command signed and encrypted, nothing to install.",
 "he": "סורקים את הקוד בטלוויזיה, והדפדפן בטלפון לוקח פיקוד: חצים, חיפוש, והדבקת מפתח TMDB בשנייה. עם שירות הנגישות של SCARY הוא שולט גם ב־Stremio ובאפליקציות אחרות. רק ברשת הביתית, כל פקודה חתומה ומוצפנת, בלי להתקין כלום.",
 "ar": "امسح الرمز على التلفاز فيتولى متصفح هاتفك التحكم: الأسهم والبحث ولصق مفتاح TMDB في ثانية. ومع خدمة إمكانية الوصول في SCARY يتحكم أيضًا في Stremio والتطبيقات الأخرى. على شبكتك المنزلية فقط، وكل أمر موقَّع ومشفَّر، ولا حاجة لتثبيت أي شيء.",
 "pt": "Escaneie o código na TV e o navegador do celular assume: setas, busca e colar a chave do TMDB num segundo. Com o serviço de acessibilidade do SCARY, ele controla também o Stremio e outros apps. Só na rede de casa, cada comando assinado e criptografado, nada para instalar.",
 "ru": "Отсканируйте код на телевизоре, и браузер телефона берёт управление: стрелки, поиск и вставка ключа TMDB за секунду. Со службой специальных возможностей SCARY он управляет и Stremio, и другими приложениями. Только в домашней сети, каждая команда подписана и зашифрована, ничего устанавливать не нужно.",
},
"f4_t": {"en": "Bucket list", "he": "Bucket list", "ar": "Bucket list", "pt": "Bucket list", "ru": "Bucket list"},
"f4_d": {
 "en": "Pick a few films, press Start, and they play one after another: each time you come back from Stremio, SCARY offers the next one.",
 "he": "בוחרים כמה סרטים, לוחצים \"התחלה\", והם מתנגנים אחד אחרי השני: בכל חזרה מ־Stremio,‏ SCARY מציעה את הבא.",
 "ar": "اختر بعض الأفلام واضغط «ابدأ»، فتُعرض واحدًا تلو الآخر: في كل عودة من Stremio يعرض عليك SCARY الفيلم التالي.",
 "pt": "Escolha alguns filmes, aperte Começar e eles tocam um depois do outro: cada vez que você volta do Stremio, o SCARY oferece o próximo.",
 "ru": "Выберите несколько фильмов, нажмите «Начать», и они пойдут один за другим: каждый раз, когда вы возвращаетесь из Stremio, SCARY предлагает следующий.",
},
"f6_t": {"en": "How much time do you have?", "he": "כמה זמן יש לך?", "ar": "كم لديك من الوقت؟", "pt": "Quanto tempo você tem?", "ru": "Сколько у вас времени?"},
"f6_d": {
 "en": "Up to 90 minutes, an evening, a double feature or a marathon: SCARY lines up the films and numbers them in the order to watch.",
 "he": "עד 90 דקות, ערב, סרט כפול או מרתון: SCARY מסדרת את הסרטים וממספרת אותם לפי סדר הצפייה.",
 "ar": "حتى 90 دقيقة، أو أمسية، أو عرض مزدوج، أو ماراثون: يرتب SCARY الأفلام ويرقّمها حسب ترتيب المشاهدة.",
 "pt": "Até 90 minutos, uma noite, sessão dupla ou maratona: o SCARY monta a programação e numera os filmes na ordem de assistir.",
 "ru": "До 90 минут, вечер, двойной сеанс или марафон: SCARY подбирает фильмы и нумерует их в порядке просмотра.",
},
"f8_d_extra": {
 "en": " A QR code on every film page opens it on your phone, ready to share.",
 "he": " קוד QR בכל עמוד סרט פותח אותו בטלפון, מוכן לשיתוף.",
 "ar": " ورمز QR في كل صفحة فيلم يفتحه على هاتفك، جاهزًا للمشاركة.",
 "pt": " Um código QR em cada página de filme abre o filme no celular, pronto para compartilhar.",
 "ru": " QR-код на странице каждого фильма открывает его на телефоне, чтобы поделиться.",
},
"f10_t": {"en": "My list, Watched and Loved it", "he": "הרשימה שלי, נצפה ואהבתי", "ar": "قائمتي وشوهد وأحببته", "pt": "Minha lista, Assistido e Amei", "ru": "Мой список, «Просмотрено» и «Понравилось»"},
"f10_d": {
 "en": "Hold OK on a poster to keep it. SCARY marks a film as watched when you come back from it, \"Loved it\" brings more like it, \"Not for me\" keeps it out of suggestions, and \"Your year in fear\" sums it all up.",
 "he": "לחיצה ארוכה על OK בפוסטר שומרת אותו. SCARY מסמנת סרט כנצפה כשחוזרים ממנו, \"אהבתי\" מביא עוד כמוהו, \"לא בשבילי\" מוציא אותו מההצעות, ו\"השנה שלך בפחד\" מסכמת הכל.",
 "ar": "اضغط OK مطولًا على ملصق لحفظه. يضع SCARY علامة «شوهد» على الفيلم عند عودتك منه، و«أحببته» يجلب المزيد مثله، و«ليس لي» يبعده عن الاقتراحات، و«عامك في الخوف» يلخّص كل شيء.",
 "pt": "Segure OK num pôster para guardá-lo. O SCARY marca o filme como assistido quando você volta dele, \"Amei\" traz mais parecidos, \"Não é pra mim\" tira o filme das sugestões, e \"Seu ano no medo\" resume tudo.",
 "ru": "Удерживайте OK на постере, чтобы сохранить его. SCARY отмечает фильм просмотренным, когда вы возвращаетесь, «Понравилось» приносит похожие, «Не моё» убирает фильм из подсказок, а «Ваш год страха» подводит итоги.",
},
"f11_t": {"en": "Search, or be surprised", "he": "חיפוש, או הפתעה", "ar": "ابحث، أو دعه يفاجئك", "pt": "Busque, ou se surpreenda", "ru": "Поиск или сюрприз"},
"f11_d": {
 "en": "By text or voice, in the library and across the web (horror, thriller and mystery only). Or \"Surprise me\": a random film at the fear level you pick, preferring ones you haven't seen.",
 "he": "בהקלדה או בקול, במאגר וגם ברשת (רק אימה, מתח ומסתורין). או \"הפתע אותי\": סרט אקראי ברמת הפחד שבחרתם, עדיף כזה שעוד לא ראיתם.",
 "ar": "بالكتابة أو بالصوت، في المكتبة وعبر الإنترنت (رعب وإثارة وغموض فقط). أو «فاجئني»: فيلم عشوائي بمستوى الرعب الذي تختاره، مع تفضيل ما لم تشاهده.",
 "pt": "Por texto ou voz, na biblioteca e na internet (só terror, suspense e mistério). Ou \"Surpreenda-me\": um filme aleatório no nível de medo que você escolher, de preferência um que você não viu.",
 "ru": "Текстом или голосом, в библиотеке и в сети (только ужасы, триллеры и детективы). Или «Удиви меня»: случайный фильм выбранного уровня страха, лучше из тех, что вы не видели.",
},
# Install: a tip after the three steps.
"install_tip": {
 "en": "Tip: once SCARY is on the TV, open <strong>Settings → Phone remote</strong> and scan the code. Your phone becomes the remote, and pasting a TMDB key takes a second.",
 "he": "טיפ: אחרי ש־SCARY על הטלוויזיה, פותחים <strong>הגדרות ← שלט בטלפון</strong> וסורקים את הקוד. הטלפון הופך לשלט, והדבקת מפתח TMDB לוקחת שנייה.",
 "ar": "نصيحة: بعد تثبيت SCARY على التلفاز، افتح <strong>الإعدادات ← جهاز التحكم بالهاتف</strong> وامسح الرمز. يصبح هاتفك جهاز التحكم، ولصق مفتاح TMDB يستغرق ثانية.",
 "pt": "Dica: com o SCARY na TV, abra <strong>Configurações → Controle pelo celular</strong> e escaneie o código. O celular vira controle, e colar a chave do TMDB leva um segundo.",
 "ru": "Совет: когда SCARY уже на телевизоре, откройте <strong>Настройки → Пульт в телефоне</strong> и отсканируйте код. Телефон станет пультом, а вставка ключа TMDB займёт секунду.",
},
"legal_version": {
 "en": "Version 1.1 · Last updated 31 October 2026",
 "he": "גרסה 1.1 · עודכן לאחרונה ב־31 באוקטובר 2026",
 "ar": "الإصدار 1.1 · آخر تحديث في 31 أكتوبر 2026",
 "pt": "Versão 1.1 · Atualizado em 31 de outubro de 2026",
 "ru": "Версия 1.1 · Обновлено 31 октября 2026 г.",
},
"privacy_app_extra": {
 "en": " The phone remote works only on your home network, straight between your phone and your TV, and keeps nothing.",
 "he": " השלט בטלפון עובד רק ברשת הביתית, ישירות בין הטלפון לטלוויזיה, ולא שומר כלום.",
 "ar": " يعمل جهاز التحكم بالهاتف على شبكتك المنزلية فقط، مباشرة بين هاتفك والتلفاز، ولا يحتفظ بأي شيء.",
 "pt": " O controle pelo celular funciona só na sua rede de casa, direto entre o celular e a TV, e não guarda nada.",
 "ru": " Пульт в телефоне работает только в домашней сети, напрямую между телефоном и телевизором, и ничего не сохраняет.",
},
# Screens (captions; alt texts in build.py where they depend on the shot).
"s_home_t": {"en": "Tonight's pick", "he": "הסרט של הלילה", "ar": "فيلم الليلة", "pt": "O filme da noite", "ru": "Фильм на ночь"},
"s_home_d": {"en": "A new film every night, and tabs for films and series.", "he": "סרט חדש בכל ערב, וטאבים לסרטים ולסדרות.", "ar": "فيلم جديد كل ليلة، وتبويبان للأفلام والمسلسلات.", "pt": "Um filme novo toda noite, e abas para filmes e séries.", "ru": "Новый фильм каждый вечер и вкладки для фильмов и сериалов."},
"s_series_t": {"en": "Horror series", "he": "סדרות אימה", "ar": "مسلسلات الرعب", "pt": "Séries de terror", "ru": "Сериалы ужасов"},
"s_series_d": {"en": "40 hand-picked series, and rows of more.", "he": "40 סדרות שנבחרו ביד, ושורות של עוד.", "ar": "40 مسلسلًا مختارًا، وصفوف للمزيد.", "pt": "40 séries escolhidas a dedo, e fileiras com mais.", "ru": "40 отобранных сериалов и подборки с другими."},
"s_remote_t": {"en": "Your phone is the remote", "he": "הטלפון הוא השלט", "ar": "هاتفك هو جهاز التحكم", "pt": "O celular vira controle", "ru": "Телефон вместо пульта"},
"s_remote_d": {"en": "Scan the code, and the phone controls the TV.", "he": "סורקים את הקוד, והטלפון שולט בטלוויזיה.", "ar": "امسح الرمز، فيتحكم الهاتف في التلفاز.", "pt": "Escaneie o código, e o celular controla a TV.", "ru": "Отсканируйте код, и телефон управляет телевизором."},
"s_remote_alt": {
 "en": "The phone remote: the TV shows a QR code and how to use it; next to it, the phone's remote page with arrows, OK, Back, Home, volume, and a search for shining with The Shining and Doctor Sleep, each with Open on the TV.",
 "he": "השלט בטלפון: בטלוויזיה קוד QR והסבר איך משתמשים, ולידה דף השלט בטלפון עם חצים, OK, חזרה, בית, עוצמת קול, וחיפוש shining עם The Shining ו־Doctor Sleep, ולכל אחד \"פתיחה בטלוויזיה\".",
 "ar": "جهاز التحكم بالهاتف: يعرض التلفاز رمز QR وطريقة الاستخدام، وبجانبه صفحة جهاز التحكم في الهاتف مع الأسهم وOK والرجوع والرئيسية والصوت، وبحث عن shining مع The Shining وDoctor Sleep، ولكل منهما «افتح على التلفاز».",
 "pt": "O controle pelo celular: a TV mostra um código QR e como usar; ao lado, a página do controle no celular com setas, OK, Voltar, Início, volume, e uma busca por shining com The Shining e Doctor Sleep, cada um com Abrir na TV.",
 "ru": "Пульт в телефоне: на телевизоре QR-код и инструкция; рядом страница пульта в телефоне со стрелками, OK, «Назад», «Домой», громкостью и поиском shining с The Shining и Doctor Sleep, у каждого кнопка «Открыть на ТВ».",
},
"s_bucket_t": {"en": "Bucket list", "he": "Bucket list", "ar": "Bucket list", "pt": "Bucket list", "ru": "Bucket list"},
"s_bucket_d": {"en": "Films in order, one after another.", "he": "סרטים לפי הסדר, אחד אחרי השני.", "ar": "أفلام بالترتيب، واحدًا تلو الآخر.", "pt": "Filmes em ordem, um depois do outro.", "ru": "Фильмы по порядку, один за другим."},
"s_next_t": {"en": "The next one?", "he": "הבא בתור?", "ar": "التالي؟", "pt": "O próximo?", "ru": "Следующий?"},
"s_next_d": {"en": "Back from Stremio, SCARY offers the next film.", "he": "חוזרים מ־Stremio, ו־SCARY מציעה את הסרט הבא.", "ar": "عند العودة من Stremio، يعرض SCARY الفيلم التالي.", "pt": "Ao voltar do Stremio, o SCARY oferece o próximo filme.", "ru": "Вернувшись из Stremio, SCARY предлагает следующий фильм."},
"s_time_t": {"en": "How much time do you have?", "he": "כמה זמן יש לך?", "ar": "كم لديك من الوقت؟", "pt": "Quanto tempo você tem?", "ru": "Сколько у вас времени?"},
"s_time_d": {"en": "A lineup numbered in watching order.", "he": "סרטים ממוספרים לפי סדר הצפייה.", "ar": "أفلام مرقّمة حسب ترتيب المشاهدة.", "pt": "Uma programação numerada na ordem de assistir.", "ru": "Подборка, пронумерованная по порядку просмотра."},
"s_moods_d": {"en": "Eight moods, from mind games to no sleep tonight.", "he": "שמונה מצבי רוח, ממשחקי מוח ועד לא לישון הלילה.", "ar": "ثمانية أمزجة، من ألعاب العقل حتى لا نوم الليلة.", "pt": "Oito climas, de jogos mentais a sem dormir esta noite.", "ru": "Восемь настроений: от игр разума до бессонной ночи."},
"s_details_d": {"en": "Fear meter, Loved it, a search in your apps and a code to share.", "he": "מד פחד, אהבתי, חיפוש באפליקציות שלכם וקוד לשיתוף.", "ar": "مقياس الرعب، وأحببته، وبحث في تطبيقاتك، ورمز للمشاركة.", "pt": "Medidor de medo, Amei, busca nos seus apps e um código para compartilhar.", "ru": "Шкала страха, «Понравилось», поиск в ваших приложениях и код, чтобы поделиться."},
}
