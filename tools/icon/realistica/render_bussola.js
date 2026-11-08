const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const SZ = +(process.env.SZ || 1600);
  const p = await b.newPage({ viewport: { width: SZ, height: SZ } });
  p.on('console', m => { if (m.type() === 'error') console.log('console:', m.text()); });
  p.on('pageerror', e => console.log('pageerror:', e.message));
  for (const preset of process.argv.slice(3)) {
    await p.goto(`http://127.0.0.1:8765/${preset}.html?s=${SZ}`);
    await p.waitForFunction('window.DONE === true', null, { timeout: 120000 });
    const data = await p.evaluate(() => document.querySelector('canvas').toDataURL('image/png'));
    require('fs').writeFileSync(`${process.argv[2]}/compass_${preset}_${SZ}.png`, Buffer.from(data.split(',')[1], 'base64'));
    const pos = await p.evaluate(() => ({ dia: window.DIA, center: window.CENTER, dias: window.DIAS, size: +new URLSearchParams(location.search).get('s') }));
    require('fs').writeFileSync(`${process.argv[2]}/compass_${preset}_${SZ}.json`, JSON.stringify(pos));
    console.log('ok', preset, JSON.stringify(pos));
  }
  await b.close();
})();
