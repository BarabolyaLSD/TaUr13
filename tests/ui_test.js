const { chromium } = require('playwright-core');
const { spawn } = require('child_process');
const PORT = 8777, URL = `http://127.0.0.1:${PORT}`;
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const srv = process.env.JAVA_CP ? spawn('java', ['-cp', process.env.JAVA_CP, 'ua.urbanist.tools.Main', '--port', String(PORT), '--assets', process.env.ASSETS], { stdio: 'ignore' }) : spawn('python3', ['server.py', '--port', String(PORT)], { cwd: __dirname, stdio: 'ignore' });
  await sleep(1200);
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1243/chrome-linux64/chrome', args: ['--no-sandbox'] });
  const errs = [], shots = [];
  const dev = { viewport: { width: 412, height: 915 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, userAgent: 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36' };
  const names = ['Олег', 'Марія', 'Тарас'];
  const pages = [];
  for (const n of names) {
    const ctx = await browser.newContext(dev); const p = await ctx.newPage();
    p.on('pageerror', e => errs.push(`${n} pageerror: ${e.message}`));
    p.on('console', m => { if (m.type() === 'error') errs.push(`${n} console: ${m.text()}`); });
    p.on('requestfailed', r => { if (!r.url().includes('/api/state')) errs.push(`${n} reqfail ${r.url()}`); });
    await p.goto(URL); await p.waitForSelector('#nm'); pages.push(p);
    await p.fill('#nm', n); await p.click('[data-act=join]');
    await p.waitForSelector('text=Лобі');
  }
  await pages[0].screenshot({ path: '/tmp/ui_lobby.png' });
  // господар додає бота й Сусіда
  await pages[0].click('[data-act=addbot]'); await pages[0].waitForSelector('text=Бот 1');
  await pages[0].selectOption('#thr', '4'); await pages[0].click('[data-act=addnb]'); await pages[0].waitForSelector('text=Сусід');
  await pages[1].waitForSelector('text=Бот 1');                       // інший телефон бачить оновлення
  const nonHostStart = await pages[1].$('[data-act=start]'); if (nonHostStart) errs.push('non-host sees start button');
  await pages[0].click('[data-act=start]');
  for (const p of pages) await p.waitForSelector('.top');
  let over = false, steps = 0, shotEvent = false, shotBuild = false, shotHand = false;
  const t0 = Date.now();
  async function autoplay(p, idx) {
    if (await p.$('#modal .trophy')) return 'over';
    const skip = await p.$('[data-act=skip]');
    if (skip) {                                           // моя подія
      if (!shotEvent && idx === 0) { shotEvent = true; await p.screenshot({ path: '/tmp/ui_event.png', fullPage: true }); }
      const cards = await p.$$('.hand .cd:not(.dim)');
      const tg = await p.$$('.strip .oc[data-act=target]');
      const needDiscard = await p.$('button.dng[data-act=skip]');
      if (needDiscard) { const all = await p.$$('.hand .cd'); await all[0].click(); await p.click('button.dng[data-act=skip]'); return 'discard'; }
      if (cards.length && tg.length && Math.random() < 0.7) {
        await cards[0].click(); await tg[Math.floor(Math.random() * tg.length)].click();
        const play = await p.$('[data-act=play]:not([disabled])'); if (play) { await play.click(); return 'play'; }
      }
      await p.click('[data-act=skip]'); return 'skip';
    }
    const pick = await p.$$('[data-act=pick]:not([disabled])');
    if (pick.length) {
      if (!shotBuild && idx === 1) { shotBuild = true; await p.screenshot({ path: '/tmp/ui_build.png', fullPage: true }); }
      await pick[Math.floor(Math.random() * pick.length)].click();
      await p.click('[data-act=confirm]:not([disabled])'); return 'build';
    }
    const none = await p.$('[data-act=none]'); if (none) { await none.click(); return 'none'; }
    return 'wait';
  }
  while (Date.now() - t0 < 90000) {
    let done = 0;
    for (let i = 0; i < pages.length; i++) {
      try { const r = await autoplay(pages[i], i); if (r === 'over') done++; else steps++; } catch (e) { const m = e.message.split('\n')[0]; if (!/not attached|detached|Target closed|intercepts pointer|not visible|not stable/.test(m)) errs.push(`autoplay ${i}: ${m}`); }
    }
    if (done === pages.length) { over = true; break; }
    await sleep(120);
  }
  // горизонтальний перелив
  for (let i = 0; i < pages.length; i++) {
    const w = await pages[i].evaluate(() => [document.documentElement.scrollWidth, window.innerWidth]);
    if (w[0] > w[1] + 1) errs.push(`horizontal overflow on page ${i}: ${w}`);
  }
  await pages[0].screenshot({ path: '/tmp/ui_over.png' });
  const txt = await pages[0].innerText('#modal');
  console.log('гра завершена:', over, '| дій:', steps, '| час:', ((Date.now() - t0) / 1000).toFixed(1) + 'с');
  console.log('екран завершення:', txt.replace(/\n+/g, ' | ').slice(0, 200));
  // нова гра: господар повертає в лобі
  if (over) { await pages[0].click('[data-act=newgame]'); await pages[1].waitForSelector('text=Лобі', { timeout: 8000 }).catch(() => errs.push('lobby after newgame not shown')); }
  console.log('помилок UI:', errs.length); errs.slice(0, 15).forEach(e => console.log(' -', e));
  await browser.close(); srv.kill();
  process.exit(errs.length || !over ? 1 : 0);
})();
