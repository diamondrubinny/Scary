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

function crypt(secret, counter, data) {
  const out = new Uint8Array(data.length);
  for (let block = 0, offset = 0; offset < data.length; block++, offset += 32) {
    const stream = hmac(secret, concat(ascii("ks"), be64(counter), be32(block)));
    for (let i = 0; i < 32 && offset + i < data.length; i++) out[offset + i] = data[offset + i] ^ stream[i];
  }
  return out;
}

const hex = (bytes) => Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
const base64 = (bytes) => btoa(String.fromCharCode(...bytes));

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

// Node (tools/remote-js-check.mjs) checks this file against RemoteProtocol.kt; the browser has no "module".
if (typeof module !== "undefined") module.exports = { sha256, hmac, crypt, seal, hex };

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
    tmdbUnreachable: "TMDB could not be reached from the TV. Try again.", tmdbStorage: "The TV could not store the key safely.",
    credit: "Powered by SCARY · Open source",
  },
  he: {
    title: "השלט של SCARY", connected: "מחובר לטלוויזיה.", connecting: "מתחבר לטלוויזיה…",
    offline: "אין חיבור לטלוויזיה. אותה רשת Wi-Fi? השליטה מהטלפון עדיין פועלת ב־SCARY?",
    noSecret: "סרקו שוב את קוד ה־QR בטלוויזיה: הדף צריך את הסוד שבקוד.",
    badSignature: "הקוד הזה כבר לא בתוקף. סרקו את הקוד החדש בטלוויזיה.",
    notFound: "הכותר הזה כבר לא מוצג בטלוויזיה. חפשו שוב.", notFront: "לא הצלחנו להגיע ל־SCARY בטלוויזיה. נסו שוב.", a11yOff: "כדי לשלוט מהטלפון גם באפליקציות אחרות, מפעילים בטלוויזיה את “SCARY Home button”: ‏SCARY › הגדרות › שלט בטלפון.", oldAndroid: "בטלוויזיה הזו Android ישן מ־13: באפליקציות אחרות הטלפון לוחץ חזרה ובית, מפעיל, משהה ומשנה עוצמה, אבל לא חצים.", busy: "קצת לאט יותר.",
    up: "למעלה", down: "למטה", left: "שמאלה", right: "ימינה", ok: "OK", back: "חזרה", home: "בית",
    play: "ניגון / השהיה", volUp: "הגברה", volDown: "הנמכה", mute: "השתקה",
    searchLabel: "חיפוש סרטים וסדרות", searchButton: "חיפוש בטלוויזיה", openOnTv: "פתיחה בטלוויזיה",
    noResults: "עוד אין תוצאות בספרייה של SCARY; הטלוויזיה מחפשת גם ברשת.", opened: "נפתח בטלוויזיה.",
    tmdbTitle: "מפתח TMDB", tmdbHint: "הדביקו את מפתח ה־API או את ה־Read Access Token של TMDB. הוא עובר מוצפן ונבדק מול TMDB.",
    tmdbLabel: "מדביקים כאן את המפתח", tmdbButton: "שליחה לטלוויזיה", tmdbSaved: "נשמר. השורות של TMDB והשמות בעברית בדרך.",
    tmdbInvalid: "זה לא נראה כמו מפתח של TMDB.", tmdbRejected: "TMDB לא קיבלה את המפתח.",
    tmdbUnreachable: "הטלוויזיה לא הצליחה להגיע ל־TMDB. נסו שוב.", tmdbStorage: "הטלוויזיה לא הצליחה לשמור את המפתח בבטחה.",
    credit: "Powered by SCARY · Open source",
  },
  ar: {
    title: "جهاز تحكم SCARY", connected: "متصل بالتلفاز.", connecting: "جارٍ الاتصال بالتلفاز…",
    offline: "لا يمكن الوصول إلى التلفاز. نفس شبكة Wi-Fi؟ هل ما زال التحكم من الهاتف يعمل في SCARY؟",
    noSecret: "امسح رمز QR على التلفاز مرة أخرى: تحتاج هذه الصفحة إلى السر الموجود في الرمز.",
    badSignature: "لم يعد هذا الرمز صالحًا. امسح الرمز الجديد على التلفاز.",
    notFound: "لم يعد هذا العنوان معروضًا على التلفاز. ابحث مرة أخرى.", notFront: "تعذّر الوصول إلى SCARY على التلفاز. حاول مرة أخرى.", a11yOff: "للتحكم في التطبيقات الأخرى من الهاتف، فعّل “SCARY Home button” على التلفاز: SCARY › الإعدادات › جهاز التحكم بالهاتف.", oldAndroid: "يعمل هذا التلفاز بإصدار Android أقدم من 13: في التطبيقات الأخرى يضغط الهاتف الرجوع والرئيسية ويشغّل ويوقف مؤقتًا ويضبط الصوت، لكن دون الأسهم.", busy: "على مهلك قليلًا.",
    up: "أعلى", down: "أسفل", left: "يسار", right: "يمين", ok: "OK", back: "رجوع", home: "الرئيسية",
    play: "تشغيل / إيقاف مؤقت", volUp: "رفع الصوت", volDown: "خفض الصوت", mute: "كتم",
    searchLabel: "ابحث عن أفلام ومسلسلات", searchButton: "ابحث على التلفاز", openOnTv: "افتح على التلفاز",
    noResults: "لا نتائج في مكتبة SCARY بعد؛ التلفاز يبحث في الويب أيضًا.", opened: "فُتح على التلفاز.",
    tmdbTitle: "مفتاح TMDB", tmdbHint: "الصق مفتاح API أو رمز Read Access الخاص بـ TMDB. ينتقل مشفّرًا ويتحقق منه TMDB.",
    tmdbLabel: "الصق المفتاح هنا", tmdbButton: "أرسل إلى التلفاز", tmdbSaved: "حُفظ. صفوف TMDB والعناوين المترجمة في الطريق.",
    tmdbInvalid: "هذا لا يبدو مفتاح TMDB.", tmdbRejected: "لم يقبل TMDB هذا المفتاح.",
    tmdbUnreachable: "تعذّر على التلفاز الوصول إلى TMDB. حاول مرة أخرى.", tmdbStorage: "تعذّر على التلفاز حفظ المفتاح بأمان.",
    credit: "Powered by SCARY · Open source",
  },
  pt: {
    title: "Controle do SCARY", connected: "Conectado à TV.", connecting: "Conectando à TV…",
    offline: "A TV não responde. Mesma rede Wi-Fi? O controle pelo celular ainda está ligado no SCARY?",
    noSecret: "Escaneie de novo o código QR na TV: esta página precisa do segredo do código.",
    badSignature: "Este código não vale mais. Escaneie o novo código na TV.",
    notFound: "A TV não mostra mais este título. Busque de novo.", notFront: "Não deu para alcançar o SCARY na TV. Tente de novo.", a11yOff: "Para controlar outros apps pelo celular, ative “SCARY Home button” na TV: SCARY › Configurações › Controle pelo celular.", oldAndroid: "Esta TV tem Android anterior ao 13: em outros apps o celular aperta Voltar e Início, reproduz, pausa e ajusta o volume, mas não as setas.", busy: "Mais devagar.",
    up: "Cima", down: "Baixo", left: "Esquerda", right: "Direita", ok: "OK", back: "Voltar", home: "Início",
    play: "Tocar / Pausar", volUp: "Aumentar volume", volDown: "Diminuir volume", mute: "Mudo",
    searchLabel: "Buscar filmes e séries", searchButton: "Buscar na TV", openOnTv: "Abrir na TV",
    noResults: "Nada na biblioteca do SCARY ainda; a TV também busca na web.", opened: "Aberto na TV.",
    tmdbTitle: "Chave do TMDB", tmdbHint: "Cole sua chave de API ou token de leitura do TMDB. Ela viaja criptografada e é verificada pelo TMDB.",
    tmdbLabel: "Cole a chave aqui", tmdbButton: "Enviar para a TV", tmdbSaved: "Salva. As fileiras do TMDB e os títulos traduzidos estão a caminho.",
    tmdbInvalid: "Isso não parece uma chave do TMDB.", tmdbRejected: "O TMDB não aceitou esta chave.",
    tmdbUnreachable: "A TV não conseguiu falar com o TMDB. Tente de novo.", tmdbStorage: "A TV não conseguiu guardar a chave com segurança.",
    credit: "Powered by SCARY · Open source",
  },
  ru: {
    title: "Пульт SCARY", connected: "Подключено к телевизору.", connecting: "Подключение к телевизору…",
    offline: "Телевизор недоступен. Та же сеть Wi-Fi? Управление с телефона в SCARY ещё включено?",
    noSecret: "Отсканируйте QR-код на телевизоре ещё раз: странице нужен секрет из кода.",
    badSignature: "Этот код больше не действует. Отсканируйте новый код на телевизоре.",
    notFound: "Телевизор больше не показывает это название. Поищите снова.", notFront: "Не удалось связаться со SCARY на телевизоре. Попробуйте ещё раз.", a11yOff: "Чтобы управлять с телефона и другими приложениями, включите на телевизоре «SCARY Home button»: SCARY › Настройки › Пульт в телефоне.", oldAndroid: "На этом телевизоре Android старше 13: в других приложениях телефон нажимает «Назад» и «Домой», включает, ставит на паузу и меняет громкость, но не стрелки.", busy: "Помедленнее.",
    up: "Вверх", down: "Вниз", left: "Влево", right: "Вправо", ok: "OK", back: "Назад", home: "Домой",
    play: "Пуск / пауза", volUp: "Громче", volDown: "Тише", mute: "Без звука",
    searchLabel: "Поиск фильмов и сериалов", searchButton: "Искать на телевизоре", openOnTv: "Открыть на телевизоре",
    noResults: "В библиотеке SCARY пока ничего; телевизор ищет и в интернете.", opened: "Открыто на телевизоре.",
    tmdbTitle: "Ключ TMDB", tmdbHint: "Вставьте API-ключ или токен доступа TMDB. Он передаётся в зашифрованном виде и проверяется TMDB.",
    tmdbLabel: "Вставьте ключ сюда", tmdbButton: "Отправить на телевизор", tmdbSaved: "Сохранено. Ряды TMDB и переведённые названия уже в пути.",
    tmdbInvalid: "Это не похоже на ключ TMDB.", tmdbRejected: "TMDB не принял этот ключ.",
    tmdbUnreachable: "Телевизор не смог связаться с TMDB. Попробуйте ещё раз.", tmdbStorage: "Телевизор не смог надёжно сохранить ключ.",
    credit: "Powered by SCARY · Open source",
  },
};
const RTL = new Set(["he", "ar"]);
const STATUS_WORDS = {
  bad_signature: "badSignature", not_front: "notFront", not_found: "notFound", a11y_off: "a11yOff", old_android: "oldAndroid", busy: "busy", off: "offline",
  tmdb_saved: "tmdbSaved", tmdb_invalid: "tmdbInvalid", tmdb_rejected: "tmdbRejected",
  tmdb_unreachable: "tmdbUnreachable", tmdb_storage: "tmdbStorage",
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

  async function send(command, text) {
    try {
      const response = await fetch("/api", { method: "POST", headers: { "Content-Type": "application/json" }, body: seal(secret, counter(), command, text) });
      const reply = await response.json();
      if (reply.s === "ok") say(words.connected);
      else say(words[STATUS_WORDS[reply.s]] || words.offline);
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

  document.getElementById("tmdb-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const field = document.getElementById("tmdb");
    const key = field.value.trim();
    if (!key) return;
    const reply = await send("tmdb", key);
    if (reply && reply.s === "tmdb_saved") field.value = "";
  });

  say(words.connecting);
  send("ping");
}

start();
