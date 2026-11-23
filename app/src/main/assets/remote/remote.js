// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

// SCARY's phone remote. The TV serves this page on the home network; the QR code adds "#s=<secret>&l=<language>",
// which the browser never sends anywhere. Each command is signed with the secret (HMAC-SHA256) and carries a rising
// counter; text (a search, the TMDB key) is encrypted with a keystream from the same secret. Plain HTTP on a home
// network has no Web Crypto, so SHA-256 is computed here. The TV side is RemoteProtocol.kt: the two must agree.
"use strict";

// ---- SHA-256 and HMAC (FIPS 180-4, RFC 2104) -------------------------------------------------------------------
const K = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
]);

function sha256(bytes) {
  const h = new Uint32Array([0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19]);
  const bitLength = bytes.length * 8;
  const padded = new Uint8Array(((bytes.length + 9 + 63) >> 6) << 6);
  padded.set(bytes);
  padded[bytes.length] = 0x80;
  const view = new DataView(padded.buffer);
  view.setUint32(padded.length - 8, Math.floor(bitLength / 0x100000000));
  view.setUint32(padded.length - 4, bitLength >>> 0);
  const w = new Uint32Array(64);
  for (let offset = 0; offset < padded.length; offset += 64) {
    for (let i = 0; i < 16; i++) w[i] = view.getUint32(offset + i * 4);
    for (let i = 16; i < 64; i++) {
      const a = w[i - 15], b = w[i - 2];
      const s0 = ((a >>> 7) | (a << 25)) ^ ((a >>> 18) | (a << 14)) ^ (a >>> 3);
      const s1 = ((b >>> 17) | (b << 15)) ^ ((b >>> 19) | (b << 13)) ^ (b >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
    }
    let [a, b, c, d, e, f, g, hh] = h;
    for (let i = 0; i < 64; i++) {
      const s1 = ((e >>> 6) | (e << 26)) ^ ((e >>> 11) | (e << 21)) ^ ((e >>> 25) | (e << 7));
      const t1 = (hh + s1 + ((e & f) ^ (~e & g)) + K[i] + w[i]) >>> 0;
      const s0 = ((a >>> 2) | (a << 30)) ^ ((a >>> 13) | (a << 19)) ^ ((a >>> 22) | (a << 10));
      const t2 = (s0 + ((a & b) ^ (a & c) ^ (b & c))) >>> 0;
      hh = g; g = f; f = e; e = (d + t1) >>> 0; d = c; c = b; b = a; a = (t1 + t2) >>> 0;
    }
    h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh;
  }
  const out = new Uint8Array(32);
  const outView = new DataView(out.buffer);
  for (let i = 0; i < 8; i++) outView.setUint32(i * 4, h[i]);
  return out;
}

function concat(...parts) {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let offset = 0;
  for (const p of parts) { out.set(p, offset); offset += p.length; }
  return out;
}

function hmac(key, data) {
  const block = new Uint8Array(64);
  block.set(key.length > 64 ? sha256(key) : key);
  const inner = block.map((b) => b ^ 0x36);
  const outer = block.map((b) => b ^ 0x5c);
  return sha256(concat(outer, sha256(concat(inner, data))));
}

const ascii = (text) => new TextEncoder().encode(text);

function be64(n) {
  const out = new Uint8Array(8);
  const view = new DataView(out.buffer);
  view.setUint32(0, Math.floor(n / 0x100000000));
  view.setUint32(4, n >>> 0);
  return out;
}

function be32(n) {
  const out = new Uint8Array(4);
  new DataView(out.buffer).setUint32(0, n);
  return out;
}

function crypt(secret, counter, data, label) {
  const out = new Uint8Array(data.length);
  const prefix = concat(ascii(label || "ks"), be64(counter));
  for (let block = 0, offset = 0; offset < data.length; block++, offset += 32) {
    const stream = hmac(secret, concat(prefix, be32(block)));
    for (let i = 0; i < 32 && offset + i < data.length; i++) out[offset + i] = data[offset + i] ^ stream[i];
  }
  return out;
}

const hex = (bytes) => Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
// In pieces: a backup file is too long to hand to String.fromCharCode at once.
function base64(bytes) {
  let binary = "";
  for (let offset = 0; offset < bytes.length; offset += 0x6000) binary += String.fromCharCode.apply(null, bytes.subarray(offset, offset + 0x6000));
  return btoa(binary);
}

function base64Decode(text) {
  const binary = atob(text);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

function base64UrlDecode(text) {
  const plain = text.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(plain + "=".repeat((4 - (plain.length % 4)) % 4));
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}

/** The request body for a command, as RemoteProtocol.seal builds it. */
function seal(secret, counter, command, text) {
  const cipher = crypt(secret, counter, new TextEncoder().encode(text || ""));
  const tag = hmac(secret, concat(ascii("mac"), be64(counter), ascii(command), new Uint8Array([0]), cipher));
  return JSON.stringify({ n: counter, c: command, x: base64(cipher), t: hex(tag) });
}

/**
 * The text of an answer to command [counter] (the viewer's backup), as RemoteProtocol.sealAnswer made it: checked
 * against its signature first, then decrypted. Null when it was not signed with this secret for this command.
 */
function openAnswer(secret, counter, cipherText, tagHex) {
  let cipher;
  try {
    cipher = base64Decode(String(cipherText));
  } catch (e) {
    return null;
  }
  const expected = hex(hmac(secret, concat(ascii("amac"), be64(counter), cipher)));
  const given = String(tagHex);
  let difference = expected.length ^ given.length;
  for (let i = 0; i < expected.length; i++) difference |= expected.charCodeAt(i) ^ given.charCodeAt(i % (given.length || 1));
  if (difference !== 0) return null;
  return new TextDecoder().decode(crypt(secret, counter, cipher, "ka"));
}

// Node (tools/remote/check-remote-js.cjs) checks this file against RemoteProtocol.kt; the browser has no "module".
if (typeof module !== "undefined") module.exports = { sha256, hmac, crypt, seal, hex, openAnswer };

// ---- The page -------------------------------------------------------------------------------------------------
const WORDS = {
  en: {
    title: "SCARY remote", connected: "Connected to the TV.", connecting: "Connecting to the TV…",
    offline: "The TV cannot be reached. Same Wi-Fi? Is SCARY's phone remote still on?",
    noSecret: "Scan the QR code on the TV again: this page needs the code's secret.",
    badSignature: "This code is no longer valid. Scan the new code on the TV.",
    notFound: "The TV no longer shows this title. Search again.", notFront: "Could not reach SCARY on the TV. Try again.", a11yOff: "To control other apps from the phone, turn on “SCARY Home button” on the TV: SCARY › Settings › Phone remote.", oldAndroid: "This TV runs Android older than 13: in other apps the phone presses Back and Home, plays, pauses and sets the volume, but not the arrows.", busy: "Slow down a little.",
    up: "Up", down: "Down", left: "Left", right: "Right", ok: "OK", back: "Back", home: "Home",
    play: "Play / Pause", volUp: "Volume up", volDown: "Volume down", mute: "Mute",
    searchLabel: "Search films and series", searchButton: "Search on the TV", openOnTv: "Open on the TV",
    noResults: "Nothing in SCARY's library yet; the TV also searches the web.", opened: "Opened on the TV.",
    tmdbTitle: "TMDB key", tmdbHint: "Paste your TMDB API key or read access token. It travels encrypted and is checked by TMDB.",
    tmdbLabel: "Paste the key here", tmdbButton: "Send to the TV", tmdbSaved: "Saved. TMDB rows and titles in your language are on their way.",
    tmdbInvalid: "That does not look like a TMDB key.", tmdbRejected: "TMDB did not accept this key.",
    tmdbUnreachable: "TMDB could not be reached from the TV. Try again.", tmdbStorage: "The TV could not store the key safely.", aiTitle: "AI key", aiHint: "For Ask SCARY with your own AI service. The key travels encrypted, is checked with the service, and stays on the TV.", aiService: "Service", aiLabel: "Paste the key here", aiButton: "Send to the TV", aiSaved: "Saved. Ask SCARY now uses this service.", aiInvalid: "That does not look like a key.", aiRejected: "The service did not accept this key.", aiUnreachable: "The TV could not reach the service. Try again.", aiStorage: "The TV could not store the key safely.",
    vpnTitle: "VPN", vpnHint: "A WireGuard settings file (.conf) from your own VPN provider. It holds a private key: it goes only to your TV, over this encrypted page, and is kept encrypted there. You connect at the TV, in Settings, VPN. SCARY comes with no VPN provider.", vpnLabel: "WireGuard settings file (.conf)", vpnButton: "Send to the TV", vpnReady: "The file is ready to send.", vpnInvalid: "This is not a WireGuard settings file SCARY can use.", vpnFailed: "The TV could not keep the file. Try again.", vpnDone: "Saved on the TV. Connect in Settings, VPN.",
    sourceTitle: "Source", sourceHint: "The address of your own source, to add on the TV: a Torznab indexer (Prowlarr, Jackett) with its API key, or a stream add-on whose address ends with /manifest.json and takes no key. It goes only to your TV, over this encrypted page, and is kept encrypted there. SCARY comes with no source.", sourceAddress: "Address", sourceKey: "API key (a Torznab indexer only)", sourceButton: "Send to the TV", sourceSaved: "Saved on the TV. Check it there: source settings, Check the source.", sourceBadAddress: "The TV did not take this address. It must start with https:// (or http:// at a home-network address in numbers).", sourceBadKey: "The TV did not take this key. A Torznab indexer needs its API key.", sourceFull: "The TV already has the most sources it takes. Remove one there first.", sourceFailed: "The TV could not save the source. Try again.",
    backupTitle: "Backup", backupHint: "Your lists (My list, watched, loved, the bucket list) and a few settings, as a file on this phone. No keys, accounts or PIN are in it. Restoring adds to the TV's lists and removes nothing.", backupSave: "Save a backup on this phone", backupSaved: "The backup file was saved on this phone.", backupFailed: "The TV could not make the backup. Try again.", restoreLabel: "Restore from a backup file", restoreButton: "Restore to the TV", restoreInvalid: "This is not a SCARY backup file.", restoreFailed: "The TV could not restore the file. Try again.", restoreDone: "Restored on the TV.", sumPrefix: "In this file", sumMyList: "My list", sumWatched: "Watched", sumLoved: "Loved it", sumBucket: "Bucket list",
    credit: "Powered by SCARY · Open source",
  },
  he: {
    title: "השלט של SCARY", connected: "מחובר לטלוויזיה.", connecting: "מתחבר לטלוויזיה…",
    offline: "אין חיבור לטלוויזיה. אותה רשת Wi-Fi? השליטה מהטלפון עדיין פועלת ב־SCARY?",
    noSecret: "סרקו שוב את קוד ה־QR בטלוויזיה: הדף צריך את הסוד שבקוד.",
    badSignature: "הקוד הזה כבר לא בתוקף. סרקו את הקוד החדש בטלוויזיה.",
    notFound: "הכותר הזה כבר לא מוצג בטלוויזיה. חפשו שוב.", notFront: "לא הצלחנו להגיע ל־SCARY בטלוויזיה. נסו שוב.", a11yOff: "כדי לשלוט מהטלפון גם באפליקציות אחרות, מפעילים בטלוויזיה את “SCARY: כפתור הבית”: ‏SCARY › הגדרות › שלט בטלפון.", oldAndroid: "בטלוויזיה הזו Android ישן מ־13: באפליקציות אחרות הטלפון לוחץ חזרה ובית, מפעיל, משהה ומשנה עוצמה, אבל לא חצים.", busy: "קצת לאט יותר.",
    up: "למעלה", down: "למטה", left: "שמאלה", right: "ימינה", ok: "OK", back: "חזרה", home: "בית",
    play: "ניגון / השהיה", volUp: "הגברה", volDown: "הנמכה", mute: "השתקה",
    searchLabel: "חיפוש סרטים וסדרות", searchButton: "חיפוש בטלוויזיה", openOnTv: "פתיחה בטלוויזיה",
    noResults: "עוד אין תוצאות בספרייה של SCARY; הטלוויזיה מחפשת גם ברשת.", opened: "נפתח בטלוויזיה.",
    tmdbTitle: "מפתח TMDB", tmdbHint: "הדביקו את מפתח ה־API או את ה־Read Access Token של TMDB. הוא עובר מוצפן ונבדק מול TMDB.",
    tmdbLabel: "מדביקים כאן את המפתח", tmdbButton: "שליחה לטלוויזיה", tmdbSaved: "נשמר. השורות של TMDB והשמות בעברית בדרך.",
    tmdbInvalid: "זה לא נראה כמו מפתח של TMDB.", tmdbRejected: "TMDB לא קיבלה את המפתח.",
    tmdbUnreachable: "הטלוויזיה לא הצליחה להגיע ל־TMDB. נסו שוב.", tmdbStorage: "הטלוויזיה לא הצליחה לשמור את המפתח בבטחה.", aiTitle: "מפתח AI", aiHint: "ל\"שאלו את SCARY\" עם שירות AI משלכם. המפתח עובר מוצפן, נבדק מול השירות ונשמר רק בטלוויזיה.", aiService: "שירות", aiLabel: "מדביקים כאן את המפתח", aiButton: "שליחה לטלוויזיה", aiSaved: "נשמר. \"שאלו את SCARY\" עובד עכשיו עם השירות הזה.", aiInvalid: "זה לא נראה כמו מפתח.", aiRejected: "השירות לא קיבל את המפתח.", aiUnreachable: "הטלוויזיה לא הצליחה להגיע לשירות. נסו שוב.", aiStorage: "הטלוויזיה לא הצליחה לשמור את המפתח בבטחה.",
    vpnTitle: "VPN", vpnHint: "קובץ הגדרות WireGuard (‎.conf) מספק ה־VPN שלכם. יש בו מפתח פרטי: הוא עובר רק לטלוויזיה שלכם, דרך הדף המוצפן הזה, ונשמר בה מוצפן. מתחברים בטלוויזיה, בהגדרות ← VPN. SCARY מגיעה בלי ספק VPN.", vpnLabel: "קובץ הגדרות WireGuard (‎.conf)", vpnButton: "שליחה לטלוויזיה", vpnReady: "הקובץ מוכן לשליחה.", vpnInvalid: "זה לא קובץ הגדרות WireGuard ש־SCARY יכולה להשתמש בו.", vpnFailed: "הטלוויזיה לא הצליחה לשמור את הקובץ. נסו שוב.", vpnDone: "נשמר בטלוויזיה. מתחברים בהגדרות ← VPN.",
    sourceTitle: "מקור", sourceHint: "הכתובת של המקור שלכם, להוספה בטלוויזיה: אינדקסר Torznab (Prowlarr, Jackett) עם מפתח ה־API שלו, או תוסף מקורות שהכתובת שלו מסתיימת ב־‎/manifest.json ואין לו מפתח. היא עוברת רק לטלוויזיה שלכם, דרך הדף המוצפן הזה, ונשמרת בה מוצפנת. SCARY מגיעה בלי מקור.", sourceAddress: "כתובת", sourceKey: "מפתח API (לאינדקסר Torznab בלבד)", sourceButton: "שליחה לטלוויזיה", sourceSaved: "נשמר בטלוויזיה. בדקו אותו שם: הגדרות מקור ← בדיקת המקור.", sourceBadAddress: "הטלוויזיה לא קיבלה את הכתובת. היא צריכה להתחיל ב־https://‎ (או ב־http://‎ בכתובת ביתית במספרים).", sourceBadKey: "הטלוויזיה לא קיבלה את המפתח. אינדקסר Torznab צריך את מפתח ה־API שלו.", sourceFull: "בטלוויזיה כבר יש את מספר המקורות המרבי. הסירו שם אחד קודם.", sourceFailed: "הטלוויזיה לא הצליחה לשמור את המקור. נסו שוב.",
    backupTitle: "גיבוי", backupHint: "הרשימות שלכם (הרשימה שלי, נצפו, אהבתי, רשימת המשאלות) וכמה הגדרות, כקובץ בטלפון הזה. אין בו מפתחות, חשבונות או קוד PIN. שחזור מוסיף לרשימות שבטלוויזיה ולא מוחק דבר.", backupSave: "שמירת גיבוי בטלפון הזה", backupSaved: "קובץ הגיבוי נשמר בטלפון הזה.", backupFailed: "הטלוויזיה לא הצליחה ליצור את הגיבוי. נסו שוב.", restoreLabel: "שחזור מקובץ גיבוי", restoreButton: "שחזור לטלוויזיה", restoreInvalid: "זה לא קובץ גיבוי של SCARY.", restoreFailed: "הטלוויזיה לא הצליחה לשחזר את הקובץ. נסו שוב.", restoreDone: "שוחזר בטלוויזיה.", sumPrefix: "בקובץ הזה", sumMyList: "הרשימה שלי", sumWatched: "נצפו", sumLoved: "אהבתי", sumBucket: "רשימת המשאלות",
    credit: "Powered by SCARY · Open source",
  },
  ar: {
    title: "جهاز تحكم SCARY", connected: "متصل بالتلفاز.", connecting: "جارٍ الاتصال بالتلفاز…",
    offline: "لا يمكن الوصول إلى التلفاز. نفس شبكة Wi-Fi؟ هل ما زال التحكم من الهاتف يعمل في SCARY؟",
    noSecret: "امسح رمز QR على التلفاز مرة أخرى: تحتاج هذه الصفحة إلى السر الموجود في الرمز.",
    badSignature: "لم يعد هذا الرمز صالحًا. امسح الرمز الجديد على التلفاز.",
    notFound: "لم يعد هذا العنوان معروضًا على التلفاز. ابحث مرة أخرى.", notFront: "تعذّر الوصول إلى SCARY على التلفاز. حاول مرة أخرى.", a11yOff: "للتحكم في التطبيقات الأخرى من الهاتف، فعّل “زر الشاشة الرئيسية لـ SCARY” على التلفاز: SCARY › الإعدادات › جهاز التحكم بالهاتف.", oldAndroid: "يعمل هذا التلفاز بإصدار Android أقدم من 13: في التطبيقات الأخرى يضغط الهاتف الرجوع والرئيسية ويشغّل ويوقف مؤقتًا ويضبط الصوت، لكن دون الأسهم.", busy: "على مهلك قليلًا.",
    up: "أعلى", down: "أسفل", left: "يسار", right: "يمين", ok: "OK", back: "رجوع", home: "الرئيسية",
    play: "تشغيل / إيقاف مؤقت", volUp: "رفع الصوت", volDown: "خفض الصوت", mute: "كتم",
    searchLabel: "ابحث عن أفلام ومسلسلات", searchButton: "ابحث على التلفاز", openOnTv: "افتح على التلفاز",
    noResults: "لا نتائج في مكتبة SCARY بعد؛ التلفاز يبحث في الويب أيضًا.", opened: "فُتح على التلفاز.",
    tmdbTitle: "مفتاح TMDB", tmdbHint: "الصق مفتاح API أو رمز Read Access الخاص بـ TMDB. ينتقل مشفّرًا ويتحقق منه TMDB.",
    tmdbLabel: "الصق المفتاح هنا", tmdbButton: "أرسل إلى التلفاز", tmdbSaved: "حُفظ. صفوف TMDB والعناوين المترجمة في الطريق.",
    tmdbInvalid: "هذا لا يبدو مفتاح TMDB.", tmdbRejected: "لم يقبل TMDB هذا المفتاح.",
    tmdbUnreachable: "تعذّر على التلفاز الوصول إلى TMDB. حاول مرة أخرى.", tmdbStorage: "تعذّر على التلفاز حفظ المفتاح بأمان.", aiTitle: "مفتاح الذكاء الاصطناعي", aiHint: "لـ\"اسأل SCARY\" مع خدمة ذكاء اصطناعي خاصة بك. ينتقل المفتاح مشفّرًا ويُتحقق منه لدى الخدمة ويبقى على التلفاز.", aiService: "الخدمة", aiLabel: "الصق المفتاح هنا", aiButton: "أرسل إلى التلفاز", aiSaved: "حُفظ. يستخدم \"اسأل SCARY\" هذه الخدمة الآن.", aiInvalid: "هذا لا يبدو مفتاحًا.", aiRejected: "لم تقبل الخدمة هذا المفتاح.", aiUnreachable: "تعذّر على التلفاز الوصول إلى الخدمة. حاول مرة أخرى.", aiStorage: "تعذّر على التلفاز حفظ المفتاح بأمان.",
    vpnTitle: "VPN", vpnHint: "ملف إعدادات WireGuard (‎.conf) من مزوّد الـVPN الخاص بك. فيه مفتاح خاص: يذهب إلى تلفازك فقط، عبر هذه الصفحة المشفّرة، ويُحفظ هناك مشفّرًا. يتم الاتصال من التلفاز، في الإعدادات ثم VPN. لا يأتي SCARY بمزوّد VPN.", vpnLabel: "ملف إعدادات WireGuard (‎.conf)", vpnButton: "إرسال إلى التلفاز", vpnReady: "الملف جاهز للإرسال.", vpnInvalid: "هذا ليس ملف إعدادات WireGuard يستطيع SCARY استخدامه.", vpnFailed: "تعذّر على التلفاز حفظ الملف. حاول مرة أخرى.", vpnDone: "حُفظ على التلفاز. اتصل من الإعدادات ثم VPN.",
    sourceTitle: "المصدر", sourceHint: "عنوان مصدرك الخاص لإضافته على التلفاز: مفهرس Torznab ‏(Prowlarr، Jackett) مع مفتاح الـAPI، أو إضافة مصادر ينتهي عنوانها بـ ‎/manifest.json ولا تحتاج مفتاحًا. يذهب إلى تلفازك فقط عبر هذه الصفحة المشفّرة، ويُحفظ هناك مشفّرًا. لا يأتي SCARY بأي مصدر.", sourceAddress: "العنوان", sourceKey: "مفتاح API (لمفهرس Torznab فقط)", sourceButton: "إرسال إلى التلفاز", sourceSaved: "حُفظ على التلفاز. تحقق منه هناك: إعدادات المصدر ثم فحص المصدر.", sourceBadAddress: "لم يقبل التلفاز هذا العنوان. يجب أن يبدأ بـ https://‎ (أو http://‎ لعنوان شبكة منزلية بالأرقام).", sourceBadKey: "لم يقبل التلفاز هذا المفتاح. مفهرس Torznab يحتاج مفتاح الـAPI.", sourceFull: "في التلفاز الحد الأقصى من المصادر. أزل واحدًا هناك أولًا.", sourceFailed: "تعذّر على التلفاز حفظ المصدر. حاول مرة أخرى.",
    backupTitle: "نسخة احتياطية", backupHint: "قوائمك (قائمتي، ما شاهدته، ما أحببته، قائمة الأمنيات) وبعض الإعدادات، كملف على هذا الهاتف. لا يحتوي على مفاتيح أو حسابات أو رمز PIN. الاستعادة تضيف إلى قوائم التلفاز ولا تحذف شيئًا.", backupSave: "احفظ نسخة احتياطية على هذا الهاتف", backupSaved: "حُفظ ملف النسخة الاحتياطية على هذا الهاتف.", backupFailed: "تعذّر على التلفاز إنشاء النسخة الاحتياطية. حاول مرة أخرى.", restoreLabel: "الاستعادة من ملف نسخة احتياطية", restoreButton: "استعادة إلى التلفاز", restoreInvalid: "هذا ليس ملف نسخة احتياطية من SCARY.", restoreFailed: "تعذّر على التلفاز استعادة الملف. حاول مرة أخرى.", restoreDone: "تمت الاستعادة على التلفاز.", sumPrefix: "في هذا الملف", sumMyList: "قائمتي", sumWatched: "شوهد", sumLoved: "أحببته", sumBucket: "قائمة الأمنيات",
    credit: "Powered by SCARY · Open source",
  },
  pt: {
    title: "Controle do SCARY", connected: "Conectado à TV.", connecting: "Conectando à TV…",
    offline: "A TV não responde. Mesma rede Wi-Fi? O controle pelo celular ainda está ligado no SCARY?",
    noSecret: "Escaneie de novo o código QR na TV: esta página precisa do segredo do código.",
    badSignature: "Este código não vale mais. Escaneie o novo código na TV.",
    notFound: "A TV não mostra mais este título. Busque de novo.", notFront: "Não deu para alcançar o SCARY na TV. Tente de novo.", a11yOff: "Para controlar outros apps pelo celular, ative “SCARY: botão Home” na TV: SCARY › Configurações › Controle pelo celular.", oldAndroid: "Esta TV tem Android anterior ao 13: em outros apps o celular aperta Voltar e Início, reproduz, pausa e ajusta o volume, mas não as setas.", busy: "Mais devagar.",
    up: "Cima", down: "Baixo", left: "Esquerda", right: "Direita", ok: "OK", back: "Voltar", home: "Início",
    play: "Tocar / Pausar", volUp: "Aumentar volume", volDown: "Diminuir volume", mute: "Mudo",
    searchLabel: "Buscar filmes e séries", searchButton: "Buscar na TV", openOnTv: "Abrir na TV",
    noResults: "Nada na biblioteca do SCARY ainda; a TV também busca na web.", opened: "Aberto na TV.",
    tmdbTitle: "Chave do TMDB", tmdbHint: "Cole sua chave de API ou token de leitura do TMDB. Ela viaja criptografada e é verificada pelo TMDB.",
    tmdbLabel: "Cole a chave aqui", tmdbButton: "Enviar para a TV", tmdbSaved: "Salva. As fileiras do TMDB e os títulos traduzidos estão a caminho.",
    tmdbInvalid: "Isso não parece uma chave do TMDB.", tmdbRejected: "O TMDB não aceitou esta chave.",
    tmdbUnreachable: "A TV não conseguiu falar com o TMDB. Tente de novo.", tmdbStorage: "A TV não conseguiu guardar a chave com segurança.", aiTitle: "Chave de IA", aiHint: "Para o Pergunte ao SCARY com seu próprio serviço de IA. A chave viaja criptografada, é verificada com o serviço e fica na TV.", aiService: "Serviço", aiLabel: "Cole a chave aqui", aiButton: "Enviar para a TV", aiSaved: "Salva. O Pergunte ao SCARY agora usa este serviço.", aiInvalid: "Isso não parece uma chave.", aiRejected: "O serviço não aceitou esta chave.", aiUnreachable: "A TV não conseguiu falar com o serviço. Tente de novo.", aiStorage: "A TV não conseguiu guardar a chave com segurança.",
    vpnTitle: "VPN", vpnHint: "Um arquivo de configuração WireGuard (.conf) do seu provedor de VPN. Ele contém uma chave privada: vai só para a sua TV, por esta página criptografada, e fica guardado lá criptografado. A conexão é feita na TV, em Configurações, VPN. O SCARY não traz provedor de VPN.", vpnLabel: "Arquivo de configuração WireGuard (.conf)", vpnButton: "Enviar para a TV", vpnReady: "O arquivo está pronto para enviar.", vpnInvalid: "Este não é um arquivo de configuração WireGuard que o SCARY possa usar.", vpnFailed: "A TV não conseguiu guardar o arquivo. Tente de novo.", vpnDone: "Salvo na TV. Conecte em Configurações, VPN.",
    sourceTitle: "Fonte", sourceHint: "O endereço da sua própria fonte, para adicionar na TV: um indexador Torznab (Prowlarr, Jackett) com a chave de API, ou um add-on de fontes cujo endereço termina em /manifest.json e não usa chave. Vai só para a sua TV, por esta página criptografada, e fica guardado lá criptografado. O SCARY não traz nenhuma fonte.", sourceAddress: "Endereço", sourceKey: "Chave de API (só para indexador Torznab)", sourceButton: "Enviar para a TV", sourceSaved: "Salvo na TV. Confira lá: configurações da fonte, Verificar a fonte.", sourceBadAddress: "A TV não aceitou este endereço. Ele deve começar com https:// (ou http:// em um endereço da rede doméstica em números).", sourceBadKey: "A TV não aceitou esta chave. Um indexador Torznab precisa da chave de API.", sourceFull: "A TV já tem o máximo de fontes. Remova uma lá primeiro.", sourceFailed: "A TV não conseguiu salvar a fonte. Tente de novo.",
    backupTitle: "Backup", backupHint: "Suas listas (Minha lista, assistidos, adorei, a lista de desejos) e algumas configurações, como um arquivo neste celular. Não há chaves, contas nem PIN nele. Restaurar acrescenta às listas da TV e não remove nada.", backupSave: "Salvar um backup neste celular", backupSaved: "O arquivo de backup foi salvo neste celular.", backupFailed: "A TV não conseguiu criar o backup. Tente de novo.", restoreLabel: "Restaurar de um arquivo de backup", restoreButton: "Restaurar na TV", restoreInvalid: "Este não é um arquivo de backup do SCARY.", restoreFailed: "A TV não conseguiu restaurar o arquivo. Tente de novo.", restoreDone: "Restaurado na TV.", sumPrefix: "Neste arquivo", sumMyList: "Minha lista", sumWatched: "Assistidos", sumLoved: "Adorei", sumBucket: "Lista de desejos",
    credit: "Powered by SCARY · Open source",
  },
  ru: {
    title: "Пульт SCARY", connected: "Подключено к телевизору.", connecting: "Подключение к телевизору…",
    offline: "Телевизор недоступен. Та же сеть Wi-Fi? Управление с телефона в SCARY ещё включено?",
    noSecret: "Отсканируйте QR-код на телевизоре ещё раз: странице нужен секрет из кода.",
    badSignature: "Этот код больше не действует. Отсканируйте новый код на телевизоре.",
    notFound: "Телевизор больше не показывает это название. Поищите снова.", notFront: "Не удалось связаться со SCARY на телевизоре. Попробуйте ещё раз.", a11yOff: "Чтобы управлять с телефона и другими приложениями, включите на телевизоре «SCARY: кнопка «Домой»»: SCARY › Настройки › Пульт в телефоне.", oldAndroid: "На этом телевизоре Android старше 13: в других приложениях телефон нажимает «Назад» и «Домой», включает, ставит на паузу и меняет громкость, но не стрелки.", busy: "Помедленнее.",
    up: "Вверх", down: "Вниз", left: "Влево", right: "Вправо", ok: "OK", back: "Назад", home: "Домой",
    play: "Пуск / пауза", volUp: "Громче", volDown: "Тише", mute: "Без звука",
    searchLabel: "Поиск фильмов и сериалов", searchButton: "Искать на телевизоре", openOnTv: "Открыть на телевизоре",
    noResults: "В библиотеке SCARY пока ничего; телевизор ищет и в интернете.", opened: "Открыто на телевизоре.",
    tmdbTitle: "Ключ TMDB", tmdbHint: "Вставьте API-ключ или токен доступа TMDB. Он передаётся в зашифрованном виде и проверяется TMDB.",
    tmdbLabel: "Вставьте ключ сюда", tmdbButton: "Отправить на телевизор", tmdbSaved: "Сохранено. Ряды TMDB и переведённые названия уже в пути.",
    tmdbInvalid: "Это не похоже на ключ TMDB.", tmdbRejected: "TMDB не принял этот ключ.",
    tmdbUnreachable: "Телевизор не смог связаться с TMDB. Попробуйте ещё раз.", tmdbStorage: "Телевизор не смог надёжно сохранить ключ.", aiTitle: "Ключ ИИ", aiHint: "Для «Спросите SCARY» с вашим собственным сервисом ИИ. Ключ передаётся зашифрованным, проверяется сервисом и хранится только на телевизоре.", aiService: "Сервис", aiLabel: "Вставьте ключ сюда", aiButton: "Отправить на телевизор", aiSaved: "Сохранено. «Спросите SCARY» теперь использует этот сервис.", aiInvalid: "Это не похоже на ключ.", aiRejected: "Сервис не принял этот ключ.", aiUnreachable: "Телевизор не смог связаться с сервисом. Попробуйте ещё раз.", aiStorage: "Телевизор не смог надёжно сохранить ключ.",
    vpnTitle: "VPN", vpnHint: "Файл настроек WireGuard (.conf) от вашего VPN-провайдера. В нём закрытый ключ: он уходит только на ваш телевизор через эту зашифрованную страницу и хранится там в зашифрованном виде. Подключение выполняется на телевизоре: настройки, VPN. В SCARY нет VPN-провайдера.", vpnLabel: "Файл настроек WireGuard (.conf)", vpnButton: "Отправить на телевизор", vpnReady: "Файл готов к отправке.", vpnInvalid: "Это не файл настроек WireGuard, который может использовать SCARY.", vpnFailed: "Телевизор не смог сохранить файл. Попробуйте ещё раз.", vpnDone: "Сохранено на телевизоре. Подключите в настройках, VPN.",
    sourceTitle: "Источник", sourceHint: "Адрес вашего собственного источника для добавления на телевизоре: индексатор Torznab (Prowlarr, Jackett) с его ключом API или дополнение-источник, адрес которого заканчивается на /manifest.json и не требует ключа. Он уходит только на ваш телевизор через эту зашифрованную страницу и хранится там в зашифрованном виде. В SCARY нет источников.", sourceAddress: "Адрес", sourceKey: "Ключ API (только для индексатора Torznab)", sourceButton: "Отправить на телевизор", sourceSaved: "Сохранено на телевизоре. Проверьте там: настройки источника, Проверить источник.", sourceBadAddress: "Телевизор не принял этот адрес. Он должен начинаться с https:// (или http:// для адреса домашней сети в цифрах).", sourceBadKey: "Телевизор не принял этот ключ. Индексатору Torznab нужен его ключ API.", sourceFull: "На телевизоре уже максимум источников. Сначала удалите один там.", sourceFailed: "Телевизор не смог сохранить источник. Попробуйте ещё раз.",
    backupTitle: "Резервная копия", backupHint: "Ваши списки (Мой список, просмотренное, понравившееся, список желаний) и несколько настроек в виде файла на этом телефоне. В нём нет ключей, аккаунтов и PIN-кода. Восстановление добавляет к спискам телевизора и ничего не удаляет.", backupSave: "Сохранить копию на этом телефоне", backupSaved: "Файл резервной копии сохранён на этом телефоне.", backupFailed: "Телевизор не смог создать копию. Попробуйте ещё раз.", restoreLabel: "Восстановить из файла копии", restoreButton: "Восстановить на телевизоре", restoreInvalid: "Это не файл резервной копии SCARY.", restoreFailed: "Телевизор не смог восстановить файл. Попробуйте ещё раз.", restoreDone: "Восстановлено на телевизоре.", sumPrefix: "В этом файле", sumMyList: "Мой список", sumWatched: "Просмотрено", sumLoved: "Понравилось", sumBucket: "Список желаний",
    credit: "Powered by SCARY · Open source",
  },
};
const RTL = new Set(["he", "ar"]);
const STATUS_WORDS = {
  bad_signature: "badSignature", not_front: "notFront", not_found: "notFound", a11y_off: "a11yOff", old_android: "oldAndroid", busy: "busy", off: "offline",
  tmdb_saved: "tmdbSaved", tmdb_invalid: "tmdbInvalid", tmdb_rejected: "tmdbRejected",
  tmdb_unreachable: "tmdbUnreachable", tmdb_storage: "tmdbStorage",
  backup_failed: "backupFailed", restore_ok: "restoreDone", restore_invalid: "restoreInvalid", restore_failed: "restoreFailed",
  vpn_ok: "vpnDone", vpn_invalid: "vpnInvalid", vpn_failed: "vpnFailed",
  source_saved: "sourceSaved", source_bad_address: "sourceBadAddress", source_bad_key: "sourceBadKey", source_full: "sourceFull", source_failed: "sourceFailed",
  ai_saved: "aiSaved", ai_invalid: "aiInvalid", ai_rejected: "aiRejected", ai_unreachable: "aiUnreachable", ai_storage: "aiStorage",
};

function start() {
  if (typeof document === "undefined") return;
  // The secret arrives in the address's "#" part. It is kept for this tab only (a reload), and taken out of the
  // address bar so a shared screenshot or link does not carry it.
  const fragment = new URLSearchParams(location.hash.slice(1));
  let secretText = fragment.get("s") || sessionStorage.getItem("scary.remote.s") || "";
  const language = Object.prototype.hasOwnProperty.call(WORDS, fragment.get("l")) ? fragment.get("l")
    : (sessionStorage.getItem("scary.remote.l") || "en");
  const words = WORDS[language] || WORDS.en;
  try {
    if (fragment.get("s")) {
      sessionStorage.setItem("scary.remote.s", secretText);
      sessionStorage.setItem("scary.remote.l", language);
      history.replaceState(null, "", location.pathname);
    }
  } catch (e) { /* storage blocked: the secret stays in memory for this visit */ }

  document.documentElement.lang = language;
  document.documentElement.dir = RTL.has(language) ? "rtl" : "ltr";
  document.title = words.title;
  for (const node of document.querySelectorAll("[data-w]")) node.textContent = words[node.dataset.w];
  for (const node of document.querySelectorAll("[data-label]")) node.setAttribute("aria-label", words[node.dataset.label]);

  const status = document.getElementById("status");
  const say = (text) => { status.textContent = text; };
  let secret;
  try {
    secret = base64UrlDecode(secretText);
  } catch (e) {
    secret = new Uint8Array(0);
  }
  if (secret.length !== 32) {
    say(words.noSecret);
    document.body.classList.add("unpaired");
    return;
  }

  // A rising counter that survives a reload: the time in microseconds, plus a step within the same millisecond.
  let step = 0;
  const counter = () => Date.now() * 1000 + (step = (step + 1) % 1000);

  // [large]: a backup file to restore goes to its own address, the only one that reads a long body.
  async function send(command, text, large) {
    const n = counter();
    try {
      const response = await fetch(large ? "/api/big" : "/api", { method: "POST", headers: { "Content-Type": "application/json" }, body: seal(secret, n, command, text) });
      const reply = await response.json();
      // The command's counter: an answer that carries the backup is signed for it.
      reply.n = n;
      if (reply.s === "ok") say(words.connected);
      // The backup's own answer says nothing until it is checked and saved.
      else if (reply.s !== "backup") say(words[STATUS_WORDS[reply.s]] || words.offline);
      return reply;
    } catch (e) {
      say(words.offline);
      return null;
    }
  }

  for (const button of document.querySelectorAll("[data-cmd]")) {
    button.addEventListener("click", () => {
      if (navigator.vibrate) navigator.vibrate(15);
      send(button.dataset.cmd);
    });
  }

  const results = document.getElementById("results");
  document.getElementById("search-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const query = document.getElementById("search").value.trim();
    if (!query) return;
    const reply = await send("search", query);
    results.textContent = "";
    if (!reply || reply.s !== "ok") return;
    const found = Array.isArray(reply.r) ? reply.r : [];
    if (found.length === 0) {
      const note = document.createElement("li");
      note.textContent = words.noResults;
      results.appendChild(note);
    }
    for (const item of found) {
      // Titles come from SCARY's library and Cinemeta: shown as text only.
      const row = document.createElement("li");
      const name = document.createElement("span");
      name.textContent = String(item.t || "") + (item.y ? " (" + String(item.y) + ")" : "");
      name.dir = "auto";
      const open = document.createElement("button");
      open.type = "button";
      open.textContent = words.openOnTv;
      open.addEventListener("click", async () => {
        const opened = await send("open", String(item.i || ""));
        if (opened && opened.s === "ok") say(words.opened);
      });
      row.append(name, open);
      results.appendChild(row);
    }
  });

  document.getElementById("ai-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const field = document.getElementById("ai-key");
    const key = field.value.trim();
    if (!key) return;
    // "service\nkey": the TV checks both, then tries the key with that service before keeping it.
    const reply = await send("ai_key", document.getElementById("ai-service").value + "\n" + key);
    if (reply && reply.s === "ai_saved") field.value = "";
  });

  // A source of the viewer's own, added on the TV: "address", or "address key". Neither holds a space.
  document.getElementById("source-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const address = document.getElementById("source-address");
    const key = document.getElementById("source-key");
    const where = address.value.trim();
    const apiKey = key.value.trim();
    if (!where) return;
    if (/\s/.test(where) || /\s/.test(apiKey)) return say(words.sourceBadAddress);
    const reply = await send("source", apiKey ? where + " " + apiKey : where, true);
    if (reply && reply.s === "source_saved") {
      address.value = "";
      key.value = "";
    }
  });

  document.getElementById("tmdb-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const field = document.getElementById("tmdb");
    const key = field.value.trim();
    if (!key) return;
    const reply = await send("tmdb", key);
    if (reply && reply.s === "tmdb_saved") field.value = "";
  });

  // ---- Backup: the viewer's lists as a file on this phone, and back ----
  document.getElementById("backup-save").addEventListener("click", async () => {
    const reply = await send("backup");
    if (!reply || reply.s !== "backup") return;
    // Encrypted and signed by the TV for this very command: anything else is not saved.
    const text = openAnswer(secret, reply.n, reply.b, reply.t);
    if (text === null) {
      say(words.backupFailed);
      return;
    }
    const url = URL.createObjectURL(new Blob([text], { type: "application/json" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = "scary-backup-" + new Date().toISOString().slice(0, 10) + ".json";
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60000);
    say(words.backupSaved);
  });

  const MAX_BACKUP = 120000;
  const fileField = document.getElementById("backup-file");
  const restoreButton = document.getElementById("backup-restore");
  const summary = document.getElementById("backup-summary");
  let chosenBackup = null;
  const count = (value) => (Array.isArray(value) ? value.length : 0);
  fileField.addEventListener("change", async () => {
    chosenBackup = null;
    restoreButton.disabled = true;
    summary.textContent = "";
    const file = fileField.files && fileField.files[0];
    if (!file) return;
    try {
      if (file.size > MAX_BACKUP * 2) throw new Error("too large");
      // Blob.text() where the browser has it, FileReader on older phones.
      const raw = file.text ? await file.text() : await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(reader.error);
        reader.readAsText(file);
      });
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== "object" || parsed.format !== "scary-backup") throw new Error("not a backup");
      // One line, as the TV takes it. The TV checks every part of it again: this page is only the messenger.
      const compact = JSON.stringify(parsed);
      if (new TextEncoder().encode(compact).length > MAX_BACKUP) throw new Error("too large");
      chosenBackup = compact;
      // Numbers only: nothing from the file is shown as text.
      summary.textContent = words.sumPrefix + ": " + [
        words.sumMyList + " " + count(parsed.myList), words.sumWatched + " " + count(parsed.watched),
        words.sumLoved + " " + count(parsed.loved), words.sumBucket + " " + count(parsed.bucket),
      ].join(" · ");
      restoreButton.disabled = false;
    } catch (e) {
      summary.textContent = words.restoreInvalid;
    }
  });
  restoreButton.addEventListener("click", async () => {
    if (!chosenBackup) return;
    restoreButton.disabled = true;
    const reply = await send("restore", chosenBackup, true);
    if (reply && reply.s === "restore_ok") {
      chosenBackup = null;
      fileField.value = "";
      summary.textContent = words.restoreDone;
    } else {
      restoreButton.disabled = false;
    }
  });

  // A WireGuard settings file for the VPN in the TV's Settings. It holds a private key, so nothing from it is shown
  // here, and it leaves this page only sealed, to the TV. The TV reads it strictly: this page is only the messenger.
  const MAX_VPN = 8192;
  const vpnField = document.getElementById("vpn-file");
  const vpnButton = document.getElementById("vpn-send");
  const vpnSummary = document.getElementById("vpn-summary");
  let chosenVpn = null;
  vpnField.addEventListener("change", async () => {
    chosenVpn = null;
    vpnButton.disabled = true;
    vpnSummary.textContent = "";
    const file = vpnField.files && vpnField.files[0];
    if (!file) return;
    try {
      if (file.size > MAX_VPN) throw new Error("too large");
      const raw = file.text ? await file.text() : await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(reader.error);
        reader.readAsText(file);
      });
      if (!/\[Interface\]/i.test(raw) || !/\[Peer\]/i.test(raw)) throw new Error("not a settings file");
      // In base64: a command's text may hold no line break, and the file is made of lines.
      const bytes = new TextEncoder().encode(raw);
      if (bytes.length > MAX_VPN) throw new Error("too large");
      let binary = "";
      for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
      chosenVpn = btoa(binary);
      vpnSummary.textContent = words.vpnReady;
      vpnButton.disabled = false;
    } catch (e) {
      vpnSummary.textContent = words.vpnInvalid;
    }
  });
  vpnButton.addEventListener("click", async () => {
    if (!chosenVpn) return;
    vpnButton.disabled = true;
    const reply = await send("vpn", chosenVpn, true);
    if (reply && reply.s === "vpn_ok") {
      chosenVpn = null;
      vpnField.value = "";
      vpnSummary.textContent = words.vpnDone;
    } else {
      vpnSummary.textContent = reply ? words[STATUS_WORDS[reply.s]] || words.vpnFailed : "";
      vpnButton.disabled = false;
    }
  });

  say(words.connecting);
  send("ping");
}

start();
