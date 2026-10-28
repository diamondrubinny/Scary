// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

// The share page: reads the film from the link, which anyone can write, so every part is checked and shown only as
// text. Nothing is stored and nothing is sent anywhere but the poster request (images.metahub.space).
"use strict";

const WORDS = {
  en: {
    kicker: "Watch tonight", share: "Share", copy: "Copy link", copied: "Link copied.", copyFailed: "Could not copy the link.",
    imdb: "Open on IMDb", poster: "Poster of %s", shareText: "Watch tonight: %s", from: "Shared from SCARY, the horror home screen for Android TV.",
    get: "Get SCARY", error: "This link is broken. Ask for a new one: scan the code on the movie's page in SCARY again.",
  },
  he: {
    kicker: "לצפייה הלילה", share: "שיתוף", copy: "העתקת הקישור", copied: "הקישור הועתק.", copyFailed: "לא הצלחנו להעתיק את הקישור.",
    imdb: "פתיחה ב־IMDb", poster: "הפוסטר של %s", shareText: "לצפייה הלילה: %s", from: "שותף מ־SCARY, מסך הבית של האימה ל־Android TV.",
    get: "להורדת SCARY", error: "הקישור הזה פגום. סרקו שוב את הקוד בעמוד הסרט ב־SCARY.",
  },
  ar: {
    kicker: "للمشاهدة الليلة", share: "مشاركة", copy: "نسخ الرابط", copied: "نُسخ الرابط.", copyFailed: "تعذّر نسخ الرابط.",
    imdb: "فتح على IMDb", poster: "ملصق %s", shareText: "للمشاهدة الليلة: %s", from: "شوركت من SCARY، الشاشة الرئيسية للرعب على Android TV.",
    get: "احصل على SCARY", error: "هذا الرابط معطوب. امسح الرمز في صفحة الفيلم في SCARY مرة أخرى.",
  },
  pt: {
    kicker: "Para ver hoje à noite", share: "Compartilhar", copy: "Copiar link", copied: "Link copiado.", copyFailed: "Não foi possível copiar o link.",
    imdb: "Abrir no IMDb", poster: "Pôster de %s", shareText: "Para ver hoje à noite: %s", from: "Compartilhado do SCARY, a tela inicial de terror para Android TV.",
    get: "Baixar o SCARY", error: "Este link está quebrado. Escaneie de novo o código na página do filme no SCARY.",
  },
  ru: {
    kicker: "Посмотреть сегодня", share: "Поделиться", copy: "Скопировать ссылку", copied: "Ссылка скопирована.", copyFailed: "Не удалось скопировать ссылку.",
    imdb: "Открыть на IMDb", poster: "Постер фильма «%s»", shareText: "Посмотреть сегодня: %s", from: "Отправлено из SCARY, хоррор-экрана для Android TV.",
    get: "Скачать SCARY", error: "Ссылка повреждена. Отсканируйте код на странице фильма в SCARY ещё раз.",
  },
};
const RTL = new Set(["he", "ar"]);
const IMDB_ID = /^tt\d{7,10}$/;
const MAX_TITLE = 80;

function fill(template, value) {
  return template.replace("%s", () => value);
}

function byId(id) {
  return document.getElementById(id);
}

function read(params) {
  const language = Object.prototype.hasOwnProperty.call(WORDS, params.get("l")) ? params.get("l") : "en";
  const id = params.get("i") || "";
  // Control characters out, whitespace folded, length capped: shown as text, never as markup.
  const title = Array.from((params.get("t") || "").replace(/[\u0000-\u001f\u007f]/g, " ").replace(/\s+/g, " ").trim())
    .slice(0, MAX_TITLE).join("");
  const yearText = params.get("y") || "";
  const year = /^\d{4}$/.test(yearText) && +yearText >= 1870 && +yearText <= 2100 ? yearText : "";
  return { language, id: IMDB_ID.test(id) ? id : null, title, year };
}

/** The link to pass on: rebuilt from the checked parts only, so nothing else in the address travels further. */
function canonicalLink(film) {
  const params = new URLSearchParams();
  params.set("i", film.id);
  if (film.title) params.set("t", film.title);
  if (film.year) params.set("y", film.year);
  params.set("l", film.language);
  return location.origin + location.pathname + "?" + params.toString();
}

function show(film) {
  const words = WORDS[film.language];
  document.documentElement.lang = film.language;
  document.documentElement.dir = RTL.has(film.language) ? "rtl" : "ltr";
  byId("kicker").textContent = words.kicker;
  byId("from").textContent = words.from;
  byId("get").textContent = words.get;

  if (!film.id) {
    byId("error").textContent = words.error;
    byId("error").hidden = false;
    return;
  }

  const name = film.title || film.id;
  const label = film.year ? name + " (" + film.year + ")" : name;
  document.title = label + " · SCARY";
  byId("title").textContent = name;
  // A Latin title inside a right-to-left page (or the other way round) keeps its own direction.
  byId("title").dir = "auto";
  byId("year").textContent = film.year;
  byId("year").hidden = !film.year;

  const poster = byId("poster");
  poster.alt = fill(words.poster, name);
  poster.addEventListener("load", () => { poster.hidden = false; });
  poster.addEventListener("error", () => { poster.remove(); });
  poster.src = "https://images.metahub.space/poster/medium/" + film.id + "/img";

  const imdb = byId("imdb");
  imdb.href = "https://www.imdb.com/title/" + film.id + "/";
  imdb.textContent = words.imdb;

  const link = canonicalLink(film);
  const status = byId("status");
  const share = byId("share");
  const copy = byId("copy");
  share.textContent = words.share;
  copy.textContent = words.copy;

  const data = { title: label, text: fill(words.shareText, label), url: link };
  if (navigator.share && (!navigator.canShare || navigator.canShare(data))) {
    share.hidden = false;
    share.addEventListener("click", async () => {
      try {
        await navigator.share(data);
      } catch (error) {
        // Closing the share sheet is not an error worth a word.
        if (error && error.name !== "AbortError") status.textContent = words.copyFailed;
      }
    });
  }
  if (navigator.clipboard && window.isSecureContext) {
    copy.hidden = false;
    copy.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(data.text + "\n" + link);
        status.textContent = words.copied;
      } catch (error) {
        status.textContent = words.copyFailed;
      }
    });
  }
  byId("film").hidden = false;
}

show(read(new URLSearchParams(location.search)));
