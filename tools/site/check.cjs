// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
// Checks the built site in every language: no console errors, no missing texts, no sideways scroll at 360 px.
// Serve site/ on 127.0.0.1:8777 first, then: node tools/site/check.cjs <dir for screenshots>

const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const out = process.argv[2];
  for (const lang of ['en', 'he', 'ar', 'pt', 'ru']) {
    for (const w of [1280, 360]) {
      const p = await b.newPage({ viewport: { width: w, height: 900 } });
      const errs = [];
      p.on('pageerror', e => errs.push(e.message)); p.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
      await p.goto(`http://127.0.0.1:8777/#${lang}`); await p.waitForTimeout(600);
      const r = await p.evaluate(() => {
        const missing = [];
        document.querySelectorAll('[data-i18n],[data-i18n-html]').forEach(el => {
          const k = el.getAttribute('data-i18n') || el.getAttribute('data-i18n-html');
          if (!el.textContent.trim()) missing.push(k);
        });
        document.querySelectorAll('[data-i18n-attr]').forEach(el => el.getAttribute('data-i18n-attr').split(';').forEach(pair => {
          const [a] = pair.split(':'); if (!el.getAttribute(a)) missing.push(pair);
        }));
        const wide = [...document.querySelectorAll('body *')].filter(e => { const r = e.getBoundingClientRect(); return r.width && (r.right > innerWidth + 1 || r.left < -1) && !e.closest('.row'); }).slice(0, 5).map(e => e.tagName + '.' + e.className);
        return { dir: document.documentElement.dir, lang: document.documentElement.lang, sw: document.documentElement.scrollWidth, missing, wide,
          feats: document.querySelectorAll('.features li').length, shots: document.querySelectorAll('.shot').length, news: document.querySelectorAll('.tag-new').length,
          f1: document.querySelector('.features li h3').textContent, sc3: document.querySelector('.shot:nth-child(3) img').alt.slice(0, 60),
          version: document.querySelector('[data-i18n=legal_version]').textContent, size: document.querySelector('[data-i18n=spec_size]').textContent };
      });
      console.log(lang, w, JSON.stringify(r), 'errors:', errs.length ? errs : 'none');
      if (w === 1280 && (lang === 'en' || lang === 'he')) {
        await p.evaluate(() => { const c = document.getElementById('consent'); if (c) c.hidden = true; });
        await p.screenshot({ path: `${out}/site-${lang}-top.png` });
        await p.locator('#features-title').scrollIntoViewIfNeeded(); await p.waitForTimeout(200);
        await p.screenshot({ path: `${out}/site-${lang}-features.png`, fullPage: false });
      }
      if (w === 360 && lang === 'he') { await p.evaluate(() => { document.getElementById('consent').hidden = true; }); await p.screenshot({ path: `${out}/site-he-phone.png`, fullPage: false }); }
      await p.close();
    }
  }
  await b.close();
})();
