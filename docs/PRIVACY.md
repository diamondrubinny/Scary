# SCARY: פרטיות

> **טיוטה טכנית שדורשת בדיקה משפטית לפני פרסום.** המסמך מתאר את מה שהקוד עושה בפועל (גרסה 0.4.0, אוקטובר 2026), ולא טוען להתאמה מלאה לחוק הגנת הפרטיות (כולל תיקון 13) או ל־GDPR.
> הטקסט שמוצג באפליקציה (הגדרות ← אודות ← פרטיות) הוא תקציר של סעיף 4, בארבע שפות, ב־`res/values*/strings.xml` (`privacy_paragraphs`).

## 1. עקרונות

- אין חשבונות, אין שרת SCARY, אין אנליטיקה, אין פרסום ואין SDK של צד שלישי.
- אין עוגיות ואין trackers של SCARY. אפליקציית Android נייטיב, ו־WebView משמש רק לנגן הטריילרים של YouTube (ראו `TRAILER_AUTOPLAY_PLAN.md`):
  - העוגיות בו חסומות, ואחסון הרשת שלו נמחק כשהנגן נסגר.
  - הוא לא נטען לפני שהמשתמש ענה "לנגן טריילרים" בשאלה של הפעם הראשונה, ואפשר לכבות אותו בכל רגע בהגדרות.
  - לכן אין באנר עוגיות. **דורש בדיקה משפטית:** האם השאלה הזו מספיקה כהסכמה לפי ePrivacy (סעיף 5(3)).
  - אם תתווסף בעתיד אנליטיקה, היא תיטען רק אחרי הסכמה מפורשת, עם אפשרות משיכה (ראו `PLAN.md` סעיף 13).
- המידע נשמר מקומית בלבד. `android:allowBackup="false"`, כך שהוא לא עובר לגיבוי של Google.
- הרשת: HTTPS בלבד (`network_security_config`, ללא cleartext). בקשות API נשלחות רק ל־Cinemeta ול־TMDB. תמונות נטענות מכתובות HTTPS שהשירותים מחזירים (בעיקר `images.metahub.space` ו־`image.tmdb.org`).

## 2. Data Map

| סוג מידע | מקור | מטרה | גישה | אחסון | צד שלישי | שמירה | מחיקה |
|---|---|---|---|---|---|---|---|
| הגדרות (שפה, אפקטים, צלילים, מד פחד, מצב רשת) | המשתמש | תפעול | האפליקציה בלבד | DataStore / SharedPreferences במכשיר | אין | עד שינוי או מחיקה | ניקוי נתונים / הסרה |
| אפליקציות מוצמדות ומוסתרות | המשתמש | סידור המסך | האפליקציה | DataStore | אין | כנ״ל | כנ״ל |
| "נצפו לאחרונה" (מזהי סרטים) | שימוש | שורת "המשך מאיפה שעצרת" | האפליקציה | DataStore | אין | 12 האחרונים בלבד | כנ״ל |
| "הרשימה שלי" (מזהי סרטים) | המשתמש | רשימת צפייה אישית | האפליקציה | DataStore | אין | עד 100, עד הסרה | הסרה מהרשימה / ניקוי נתונים |
| "נצפה" (מזהי סרטים) | המשתמש, או סימון אוטומטי אחרי צפייה | תג "נצפה", והעדפת סרטים שלא נצפו ב"הסרט של הלילה" וב"הפתע אותי" | האפליקציה | DataStore | אין | עד 1,000, עד הסרה | ביטול הסימון בעמוד הסרט / ניקוי נתונים |
| "אהבתי" ו"לא בשבילי" (מזהי סרטים) | המשתמש, בעמוד הסרט | "כי אהבת את X", והוצאת סרטים מ"הסרט של הלילה", "הפתע אותי" ו"כמה זמן יש לך?" | האפליקציה | DataStore | אין | עד 500 בכל רשימה, עד ביטול | לחיצה חוזרת על הכפתור / ניקוי נתונים |
| יום הסימון כנצפה (מזהה סרט ותאריך, בלי שעה) | סימון "נצפה" | "השנה שלך בפחד" (מחושב בטלוויזיה) | האפליקציה | DataStore | אין | כמו רשימת "נצפה" (עד 1,000) | ביטול הסימון / ניקוי נתונים |
| טיימר שינה (שעת הסיום) | המשתמש | ספירה לאחור ועצירת הניגון | האפליקציה; התראה (AlarmManager) במערכת | DataStore | אין | עד שהזמן נגמר, כיבוי או הפעלה מחדש של הטלוויזיה | אוטומטי / "כבוי" בהגדרות |
| כניסות הטלוויזיה (HDMI, אנטנה: שם ומזהה) | המערכת (TvInputManager, בלי הרשאה) | אריחים בשורת האפליקציות | האפליקציה | זיכרון בלבד | אין | לא נשמר | — |
| Bucket list (מזהי סרטים לפי הסדר, והסרט שנפתח ממנה) | המשתמש (מצב בחירה, כפתור בעמוד סרט) | צפייה בסרטים אחד אחרי השני, ושאלה בחזרה ל־SCARY | האפליקציה | DataStore | אין | עד 30 סרטים, עד הסרה או ניקוי | ניקוי הרשימה במסך שלה / ניקוי נתונים |
| סרט שנפתח לצפייה (מזהה ושעת פתיחה) | שימוש | להחליט, כשחוזרים ל־SCARY, אם הסרט נצפה. SCARY לא רואה מה קורה באפליקציה האחרת ומחליטה לפי הזמן בלבד | האפליקציה | DataStore | אין | נמחק בחזרה הבאה ל־SCARY | אוטומטי; אפשר לכבות סימון אוטומטי בהגדרות |
| שלט בטלפון: מפתח חד־פעמי (32 בתים אקראיים) | נוצר בטלוויזיה בכל הפעלה (SecureRandom) | חתימה והצפנה של פקודות בין הטלפון לטלוויזיה | האפליקציה; הדף בטלפון (sessionStorage של הלשונית) | זיכרון בלבד בטלוויזיה; בטלפון רק בלשונית הפתוחה | אין. מוצג בקוד ה־QR ולא נשלח ברשת אף פעם | עד ניתוק, 30 דקות בלי שימוש או 20 פקודות מזויפות | אוטומטי; "ניתוק" / "קוד חדש" |
| שלט בטלפון: פקודות (חצים, טקסט חיפוש, מפתח TMDB) וכתובת ה־IP של הטלפון ברשת הביתית | המשתמש, מהטלפון | שליטה ב־SCARY, חיפוש, הזנת מפתח TMDB | האפליקציה | לא נשמר (טקסט החיפוש ומפתח ה־TMDB משמשים כמו בהקלדה בטלוויזיה) | אין: הכל ברשת הביתית | לא נשמר | — |
| סרטים שנשמרו מהרשת (פרטי סרט ציבוריים) | Cinemeta | שמירה ב"רשימה שלי" וב"נפתחו לאחרונה" גם אחרי שהם יוצאים מהשורות | האפליקציה | `saved_movies.json` במכשיר | אין | עד 150 | ניקוי נתונים |
| טקסט חיפוש | המשתמש | חיפוש במאגר ובאינטרנט | האפליקציה, Cinemeta | בזיכרון בלבד, לא נשמר | Cinemeta (רק כש"מידע מהרשת" פעיל ובשאילתה של 3 תווים ומעלה) | לא נשמר אצלנו; אצל Cinemeta לפי המדיניות שלו | — |
| חיפוש קולי | המשתמש (דיבור) | הפיכת דיבור לשאילתה | שירות הדיבור של המכשיר | SCARY מקבלת רק טקסט, ולא שומרת אותו מעבר לשאילתה הנוכחית | שירות זיהוי הדיבור של הטלוויזיה (בדרך כלל Google) | לפי מדיניות הספק | **דורש בדיקה מול הספק** |
| PIN הורים | המשתמש | נעילת הורים | האפליקציה | hash מסוג PBKDF2-HMAC-SHA256 עם salt אקראי. ה־PIN עצמו לא נשמר | אין | עד כיבוי הנעילה | כיבוי נעילה / ניקוי נתונים |
| מצב נעילה אחרי ניסיונות כושלים | שימוש | הגנה מניחוש | האפליקציה | DataStore | אין | עד הצלחה | אוטומטי |
| מפתח TMDB | המשתמש (אופציונלי) | גישה ל־TMDB | האפליקציה | AES-256-GCM, מפתח ב־Android Keystore | נשלח ל־TMDB בלבד | עד מחיקה | "מחיקת מפתח" בהגדרות |
| מדינה לשכירה ולקנייה | בחירת המשתמש, או הגדרת האזור של המכשיר / מדינת הרשת והסים (בלי הרשאה) | הצגת חנויות במדינה הנכונה | האפליקציה | DataStore (רק אם נבחרה ידנית); הזיהוי האוטומטי לא נשמר | אין: TMDB מחזירה את כל המדינות והסינון נעשה במכשיר | עד שינוי | "אוטומטי" בהגדרות / ניקוי נתונים |
| זמינות לשכירה ולקנייה (נתוני JustWatch דרך TMDB) | TMDB | "לא מצאתם? אפשר לשכור או לקנות" | האפליקציה | בזיכרון בלבד, 40 סרטים אחרונים | TMDB (רק עם מפתח; נשלחים מזהה הסרט והמפתח) | עד 24 שעות או עד סגירת האפליקציה | אוטומטי |
| הגדרות טריילרים (ניגון אוטומטי, קול, האם נענתה השאלה הראשונה) | המשתמש | תפעול | האפליקציה | DataStore | אין | עד שינוי | הגדרות / ניקוי נתונים |
| ניגון טריילר (מזהה הסרטון ב־YouTube) | Cinemeta | טריילר שמתנגן מעצמו ליד סרט | נגן YouTube בתוך SCARY | בזיכרון בלבד; אחסון הרשת של הנגן נמחק בסגירה, עוגיות חסומות | YouTube / Google: מזהה הסרטון, IP, פרטי מכשיר ודפדפן, נתוני ניגון | לא נשמר אצלנו; אצל Google לפי המדיניות שלה | כיבוי "ניגון אוטומטי של טריילרים" בהגדרות. **דורש בדיקה משפטית** |
| מטמון Cinemeta / TMDB | השירותים | הצגת מידע ללא רשת | האפליקציה | קבצי JSON ב־`filesDir` | — | רענון כל 24 שעות | מחיקת מפתח (TMDB) / ניקוי נתונים |
| מטמון תמונות | השירותים | ביצועים | האפליקציה | Coil disk cache | — | עד 128MB, הישנות נמחקות ראשונות | ניקוי מטמון / נתונים |
| זמני קריסה (שעה בלבד, בלי פרטי השגיאה) | האפליקציה | זיהוי קריסות חוזרות ומעבר למצב בטוח | האפליקציה בלבד | SharedPreferences במכשיר | אין, לא נשלח לשום מקום | 3 האחרונים, רק מ־2 הדקות האחרונות; נמחק כשנכנסים למצב בטוח | אוטומטי / ניקוי נתונים |
| רשימת אפליקציות מותקנות | המערכת | הצגה והפעלה | האפליקציה | זיכרון בלבד | אין | לא נשמרת | — |
| כתובת IP | רשת | נדרש טכנית לכל בקשה | ספקי השירות | אצל הספקים | Cinemeta, Metahub, TMDB | לפי מדיניות הספק | **דורש בדיקה מול הספקים** |

**מידע רגיש:** אין. אין מידע על קטינים מעבר לעצם השימוש בנעילת הורים, ואין מידע ביומטרי, רפואי או פיננסי.

## 3. ספקים חיצוניים

| ספק | מתי | מה נשלח | מטרה | הסכמה | לבדוק |
|---|---|---|---|---|---|
| Cinemeta (`v3-cinemeta.strem.io`, `cinemeta-catalogs.strem.io`) | כש"מידע מהרשת" פעיל (ברירת מחדל: פעיל): רענון יומי (סרטים וסדרות), חיפוש, וטעינת עמוד נוסף כשגוללים לסוף שורה | מזהי IMDb ציבוריים, טקסט החיפוש (בחיפוש), מספר עמוד בקטלוג, IP, User-Agent | פוסטרים, רקעים, דירוג, שחקנים, שורות פופולריות, חיפוש ברשת | לא נדרשת לדעתנו (אין מידע אישי מעבר ל־IP). **דורש בדיקה משפטית** | תנאי שימוש, זכויות בתמונות, מיקום שרתים |
| מארחי תמונות (בעיקר `images.metahub.space`; כל כתובת HTTPS ש־Cinemeta מחזירה) | כנ״ל | בקשות תמונה, IP | תמונות | כנ״ל | כנ״ל. אפשר לשקול רשימת מארחים מותרים |
| TMDB (`api.themoviedb.org`, `image.tmdb.org`) | רק עם מפתח שהמשתמש הזין | המפתח, מזהי סרטים, שפה, IP (בשורת "בקרוב בקולנוע" גם טווח תאריכים: מחר עד עוד חצי שנה) | שמות ותקצירים בשפה, שורות נוספות | המשתמש בוחר להזין מפתח | תנאי TMDB (אישי/מסחרי), קרדיט חובה מוצג |
| שירות זיהוי הדיבור של הטלוויזיה (בדרך כלל Google) | רק כשהמשתמש לוחץ על המיקרופון בחיפוש | ההקלטה עצמה (SCARY לא ניגשת אליה), שפת הממשק | הפיכת דיבור לטקסט | פעולה יזומה; ההרשאות של השירות עצמו | מדיניות הספק, מיקום עיבוד |
| נגן YouTube מוטמע (`www.youtube.com/iframe_api`, `www.youtube-nocookie.com`, ו־`www.youtube.com` כניסיון אחרון) | רק אחרי "לנגן טריילרים" בשאלה הראשונה; כשהפוקוס נשאר 1.5 שניות על סרט שיש לו טריילר; לא בלי רשת, לא בערב משפחתי, לא בסרט נעול, לא כש"הסרת אנימציות" פעיל | מזהה הסרטון, IP, User-Agent, Referer עם מזהה האפליקציה (נדרש בתנאי YouTube), נתוני ניגון | טריילר ליד הסרט | השאלה בפעם הראשונה (opt-in), וכיבוי בהגדרות. **דורש בדיקה משפטית** | תנאי YouTube API Services ו־Required Minimum Functionality, ‏Google Privacy Policy, ‏DPA / העברה בינלאומית |
| Stremio / YouTube / Google Play | כשהמשתמש בוחר לצפות, טריילר או חנות | Intent עם מזהה סרט או חיפוש | העברה לאפליקציה אחרת | פעולה יזומה | המדיניות שלהם חלה מאותו רגע |
| IMDb (`www.imdb.com`) | רק אם המשתמש סורק את קוד ה־QR "סרקו לשיתוף הסרט" בעמוד סרט, בטלפון שלו | כלום מ־SCARY: הקישור (מזהה IMDb ציבורי) נפתח בטלפון | פרטים, ביקורות ושיתוף בטלפון | פעולה יזומה בטלפון | מדיניות הפרטיות של IMDb חלה מאותו רגע |
| הדפדפן בטלפון של המשתמש (ברשת הביתית בלבד) | רק כשהמשתמש מפעיל "שלט בטלפון" וסורק את הקוד | הדף של השלט (מתוך האפליקציה), ותשובות לפקודות: סטטוס ותוצאות חיפוש (מזהה, שם, שנים) | שליטה מהטלפון | פעולה יזומה | אין ספק חיצוני. HTTP בתוך הרשת הביתית, עם חתימה והצפנה משלנו (לא TLS): **דורש בדיקת אבטחה מקצועית** |
| PayPal (`www.paypal.com`) | רק אם המשתמש סורק את קוד התרומה בטלפון שלו | כלום מ־SCARY: הקישור נפתח בטלפון, לא בטלוויזיה | תרומה (רשות) | פעולה יזומה בטלפון | מדיניות הפרטיות של PayPal חלה מאותו רגע. SCARY לא יודעת מי תרם |
| אפליקציות צפייה מותקנות (Netflix, Apple TV, yes, HOT, סלקום TV ועוד) וחיפוש Google TV | רק כשהמשתמש לוחץ "חיפוש ב־X" בעמוד סרט | שם הסרט המקורי בלבד (ב־YouTube גם השנה) | חיפוש הסרט באפליקציה | פעולה יזומה | המדיניות של כל אפליקציה חלה מאותו רגע. SCARY רק בודקת במכשיר אילו אפליקציות מותקנות, ולא שולחת את הרשימה לשום מקום |

## 4. טיוטת מדיניות פרטיות

### עברית

**מדיניות פרטיות – SCARY** (טיוטה, עודכן באוקטובר 2026)

1. **מי אנחנו.** SCARY היא אפליקציית מסך בית (לאנצ׳ר) ל־Android TV, בקוד פתוח: github.com/diamondrubinny/Scary.
2. **מה איננו אוספים.** אין חשבונות, אין פרסומות, אין אנליטיקה ואין עוגיות. אין שרת של SCARY, ושום מידע על השימוש שלכם לא נשלח אלינו.
3. **מה נשמר בטלוויזיה שלכם בלבד.** הגדרות ושפה, אפליקציות מוצמדות ומוסתרות, 12 הסרטים האחרונים שפתחתם, "הרשימה שלי", קוד PIN של נעילת הורים כ־hash עם salt (לא הקוד עצמו), ומטמון של מידע ותמונות על סרטים. הסרת האפליקציה או ניקוי הנתונים שלה מוחקים הכל. SCARY לא נכללת בגיבוי של Android.
   - **גם אלה נשמרים רק בטלוויזיה:** הסרטים שסימנתם "אהבתי" או "לא בשבילי", היום שבו סימנתם כל סרט כנצפה, וה־Bucket list שלכם. מהם נבנים "כי אהבת", "כמה זמן יש לך?" ו"השנה שלך בפחד", והחישוב נעשה בטלוויזיה. טיימר השינה הוא התראה בטלוויזיה שעוצרת את מה שמתנגן.
   - **קוד QR בעמוד סרט:** פותח את עמוד ה־IMDb של הסרט בטלפון שלכם. הטלוויזיה לא שולחת דבר.
   - **שלט בטלפון** (רק כשמפעילים אותו בהגדרות): הטלוויזיה מגישה דף קטן ברשת הביתית, והטלפון פותח אותו בסריקת קוד QR. בקוד יש מפתח חד־פעמי שחותם ומצפין כל פקודה, והוא לא נשלח ברשת אף פעם. הפקודות עוברות ישירות מהטלפון לטלוויזיה ולא נשמרות. הדף לא טוען דבר מהאינטרנט. השלט כבה בניתוק, אחרי 30 דקות בלי שימוש, או כשמגיעות פקודות מזויפות.
4. **אפליקציות מותקנות וכניסות הטלוויזיה.** הרשימות נקראות בטלוויזיה כדי להציג ולהפעיל אותן, ולא יוצאות ממנה.
5. **מידע על סרטים מהרשת** (אפשר לכבות בהגדרות). SCARY פונה ל־Cinemeta, שירות המטא־דאטה הציבורי של Stremio, ושולחת רק מזהי IMDb ציבוריים, ובחיפוש גם את טקסט החיפוש. התמונות נטענות מהכתובות ש־Cinemeta מספקת, בעיקר images.metahub.space. כמו כל אתר, השירותים רואים את כתובת ה־IP של הטלוויזיה.
6. **TMDB** (רק אם הזנתם מפתח). הבקשות נשלחות ל־TMDB עם המפתח, מזהי הסרטים ושפת הממשק. המפתח מוצפן בטלוויזיה ונשלח רק ל־TMDB. מחיקת המפתח מוחקת גם את המטמון. אפשרויות השכירה והקנייה בעמודי הסרטים מגיעות מ־TMDB (נתונים של JustWatch): הבקשה כוללת רק את מזהה הסרט, והמדינה נקבעת בטלוויזיה לפי הגדרת האזור או לפי בחירתכם בהגדרות, ואף פעם לא לפי המיקום שלכם. SCARY לא מבקשת הרשאת מיקום.
7. **חיפוש קולי.** כפתור המיקרופון מעביר את הבקשה לשירות זיהוי הדיבור של הטלוויזיה (בדרך כלל Google). ההקלטה נשלחת לשירות הזה ולא ל־SCARY, ו־SCARY מקבלת רק את הטקסט שזוהה. SCARY לא מבקשת הרשאת מיקרופון.
8. **טריילרים שמתנגנים מעצמם** (רק אחרי שהסכמתם בשאלה של הפעם הראשונה; אפשר לכבות בהגדרות).
   - **הנגן:** הנגן של YouTube, במצב הפרטיות המשופרת (youtube-nocookie.com). אם טריילר לא מתנגן שם, נעשה ניסיון אחרון ב־youtube.com, גם הוא בלי עוגיות.
   - **מה YouTube ‏(Google) מקבלת כשטריילר מתנגן:** את כתובת ה־IP של הטלוויזיה, פרטי המכשיר והדפדפן ואת מזהה הטריילר, לפי מדיניות הפרטיות של Google.
   - **עוגיות ואחסון:** SCARY חוסמת עוגיות בנגן ומוחקת את האחסון שלו כשהוא נסגר.
   - **מתי לא מתנגנים:** בלי רשת או בערב משפחתי.
9. **אפליקציות אחרות.** צפייה, טריילרים והחנות פותחים את Stremio, YouTube או Google Play, ומאותו רגע חלה מדיניות הפרטיות שלהן.
10. **זכויותיכם.** כל המידע נמצא אצלכם ונמחק בניקוי הנתונים. לשאלות ובקשות: d@rubinny.net או github.com/diamondrubinny/Scary/issues.
11. **שינויים.** שינוי מהותי יופיע במסמך זה עם תאריך עדכון.

### English

**Privacy Policy – SCARY** (draft, updated October 2026)

1. **Who we are.** SCARY is an open-source home screen (launcher) for Android TV: github.com/diamondrubinny/Scary.
2. **What we do not collect.** No accounts, no ads, no analytics, no cookies. There is no SCARY server, and nothing about your use is sent to us.
3. **What stays on your TV only.** Settings and language, pinned and hidden apps, the last 12 movies you opened, your My list, the parental PIN as a salted hash (never the PIN itself), and cached movie information and images. Uninstalling the app or clearing its data deletes everything. SCARY is excluded from Android backup.
   - **Also only on your TV:** the films you mark "Loved it" or "Not for me", the day you mark each film as watched, and your bucket list. They power "Because you loved", "How much time do you have?" and "Your year in fear", worked out on the TV. The sleep timer is an alarm on the TV that pauses what is playing.
   - **The QR code on a film's page** opens its IMDb page on your phone; the TV sends nothing.
   - **Phone remote** (only while you turn it on in settings): the TV serves a small page on your home network, which your phone opens by scanning a QR code. The code holds a one-time key that signs and encrypts every command, and the key is never sent over the network. Commands go straight from the phone to the TV and are not stored. The page loads nothing from the internet. The remote turns off when you disconnect it, after 30 minutes without use, or when forged commands arrive.
4. **Installed apps and the TV's inputs.** The lists are read on the TV to show and open them, and never leave it.
5. **Online movie data** (can be turned off in settings). SCARY contacts Cinemeta, Stremio's public metadata service, sending only public IMDb IDs, plus the search text when you search; images load from the addresses it supplies, mostly images.metahub.space. Like any website, these services see your TV's IP address.
6. **TMDB** (only if you enter a key). Requests go to TMDB with your key, movie IDs and the interface language. The key is encrypted on the TV and sent only to TMDB. Deleting the key also deletes the cache. Rent and buy offers on movie pages come from TMDB (data by JustWatch): the request carries only the movie's ID, and your country is picked on the TV from its region setting, or chosen by you in settings, never from your location. SCARY asks for no location permission.
7. **Voice search.** The microphone button hands the request to the TV's speech recognizer (usually Google). The recording goes to that service, not to SCARY; SCARY only receives the recognized text and asks for no microphone permission.
8. **Trailers that play by themselves** (only once you agree when first asked; you can turn them off in settings).
   - **The player:** YouTube's player, in its privacy-enhanced mode (youtube-nocookie.com); when a trailer will not play there, a last try uses youtube.com, also without cookies.
   - **What YouTube (Google) receives when a trailer plays:** your TV's IP address, device and browser details and the trailer's ID, under Google's privacy policy.
   - **Cookies and storage:** SCARY blocks cookies in the player and deletes its storage when it closes.
   - **When none play:** offline or in family night.
9. **Other apps.** Watching, trailers and the store open Stremio, YouTube or Google Play; their privacy policies apply from then on.
10. **Your rights.** All data is on your device and is erased when you clear the app's data. Questions and requests: d@rubinny.net or github.com/diamondrubinny/Scary/issues.
11. **Changes.** Material changes will appear here with a new date.

## 5. נושאים לבדיקה משפטית

- האם SCARY היא "מאגר מידע" לפי חוק הגנת הפרטיות (סביר שלא, כי אין מידע אצל המפתח). **דורש בדיקה משפטית.**
- האם שליחת IP לספקים מחוץ לישראל או לאיחוד האירופי דורשת גילוי או בסיס חוקי נוסף לפי GDPR (אינטרס לגיטימי / ביצוע השירות). **דורש בדיקה משפטית.**
- תנאי השימוש של Cinemeta ושל TMDB, וזכויות בתמונות. **דורש בדיקה משפטית.**
- פרסום ב־Google Play: טופס Data safety, הצהרת `<queries>` / גישה לרשימת אפליקציות, ומדיניות לאנצ׳רים. **דורש אימות מול מדיניות Google Play.**
- תנאי שימוש: טיוטה ב־`TERMS.md`. דורשת בדיקה משפטית.
- שלט בטלפון: שרת HTTP ברשת הביתית, עם פרוטוקול חתימה והצפנה משלנו במקום TLS (פירוט ומודל איומים ב־`PHONE_REMOTE_PLAN.md`). **דורש בדיקת חדירות / סקירת קריפטוגרפיה מקצועית לפני הפצה רחבה.**
- טריילרים שמתנגנים מעצמם (נגן YouTube מוטמע):
  - האם השאלה בפעם הראשונה ("לנגן טריילרים" / "לכבות") היא הסכמה מספיקה לפי ePrivacy/GDPR ולפי תיקון 13, כולל לגבי אחסון הרשת שהנגן משתמש בו עד שהוא נסגר. **דורש בדיקה משפטית.**
  - עמידה בתנאי YouTube API Services וב־Required Minimum Functionality (Referer עם מזהה האפליקציה, גודל מינימלי, בלי שכבות מעל הנגן, נגן אוטומטי אחד). **דורש אימות מחדש מול התיעוד הרשמי לפני כל שחרור.**
