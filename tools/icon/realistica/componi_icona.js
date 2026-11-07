const { chromium } = require('playwright');
const fs = require('fs');
const S = 4096;
const PHOTOS = [
  ['hd2/heic0506a.jpg', 1.15], ['hd2/eso1907a.jpg', 2.3], ['hd2/heic1917a.jpg', 1.35], ['hd2/tyrannosaurus_rex_fossil_7.jpg', 1],
  ['hd2/ocean_wave_4.jpg', 1], ['hd2/northern_lights_1.jpg', 1], ['hd2/volcano_lava_4.jpg', 1], ['hd2/jellyfish_1.jpg', 1],
  ['hd2/castle_2.jpg', 1], ['hd2/heic0515a.jpg', 1.1], ['hd2/whale_0.jpg', 1], ['hd2/uragano_iss.jpg', 1.05], ['hd2/weic2216b.jpg', 1], ['hd2/lightning_storm_4.jpg', 1]];
const C = S / 2, R0 = 0.262 * S, R1 = 0.412 * S, TURNS = 1.12, TH0 = -Math.PI * 0.86;
const at = t => { const th = TH0 + t * TURNS * 2 * Math.PI, r = R0 + t * (R1 - R0); return [C + r * Math.cos(th), C + r * Math.sin(th)]; };
function cards() {
  const n = PHOTOS.length;
  return PHOTOS.map(([src, zoom], i) => { const t = i / (n - 1), [x, y] = at(t), w = 0.07 * S + t * 0.14 * S, h = w * 0.76, rot = 13 * Math.sin(i * 1.9);
    return `<div class="card" style="left:${x - w / 2}px;top:${y - h / 2}px;width:${w}px;height:${h}px;transform:rotate(${rot}deg)"><img src="dl/${src}" style="transform:scale(${zoom})"><div class="gloss"></div></div>`; }).join('');
}
function trailPath(a = -0.14, b = 1.1, steps = 260) { const pts = []; for (let k = 0; k <= steps; k++) { const [x, y] = at(a + k / steps * (b - a)); pts.push(`${x.toFixed(1)},${y.toFixed(1)}`); } return 'M' + pts.join(' L'); }
function dust() { let seed = 11, out = ''; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  for (let i = 0; i < 520; i++) { const t = -0.12 + rnd() * 1.2, [x, y] = at(t), off = (rnd() - .5) * S * 0.05 * (0.4 + t), a = rnd() * Math.PI * 2;
    out += `<circle cx="${(x + Math.cos(a) * off).toFixed(1)}" cy="${(y + Math.sin(a) * off).toFixed(1)}" r="${(0.8 + rnd() * rnd() * 5).toFixed(2)}" fill="${rnd() < .2 ? '#ffe7b0' : rnd() < .5 ? '#dbe7ff' : '#ffffff'}" opacity="${(0.25 + rnd() * .65).toFixed(2)}"/>`; }
  return out; }
const pos = JSON.parse(fs.readFileSync('out/compass_real_3200.json'));
const CW = 0.5566 * S, k = CW / pos.size, cl = C - pos.center[0] * k, ct = C - pos.center[1] * k;
const flare = (x, y, size, op) => `<svg class="flare" style="left:${cl + x * k}px;top:${ct + y * k}px;width:${size}px;height:${size}px;opacity:${op}" viewBox="-100 -100 200 200"><circle r="34" fill="url(#fg)"/><path d="M0,-92 L3,-3 L92,0 L3,3 L0,92 L-3,3 L-92,0 L-3,-3 Z" fill="white" opacity=".7"/><path d="M-60,-60 L1.5,-1.5 L60,-60 L1.5,1.5 L60,60 L-1.5,1.5 L-60,60 L-1.5,-1.5 Z" fill="white" opacity=".25"/></svg>`;
const html = `<!doctype html><html><head><meta charset=utf-8><style>
html,body{margin:0;background:transparent}
.icon{position:relative;width:${S}px;height:${S}px;border-radius:${process.env.SQUARE ? 0 : 23}%;overflow:hidden;background:#02030a}
.bg{position:absolute;inset:-6%;background:url(dl/hd/heic0206a_large.jpg) 40% 30%/cover;transform:rotate(180deg);filter:brightness(.86) saturate(1.5) contrast(1.1) hue-rotate(-6deg)}
.neb{position:absolute;mix-blend-mode:screen;filter:blur(${S * 0.002}px) saturate(1.2)}
.neb1{left:-8%;top:-10%;width:70%;height:70%;background:url(dl/hd2/heic0601a.jpg) 40% 35%/cover;opacity:.30;-webkit-mask-image:radial-gradient(closest-side, rgba(0,0,0,1), rgba(0,0,0,0))}
.neb2{right:-12%;top:-12%;width:62%;height:62%;background:url(dl/hd2/heic0601a.jpg) 70% 60%/cover;opacity:.2;transform:rotate(140deg);-webkit-mask-image:radial-gradient(closest-side, rgba(0,0,0,1), rgba(0,0,0,0))}
.neb3{left:-14%;bottom:-16%;width:60%;height:60%;background:url(dl/hd2/heic0515a.jpg) center/cover;opacity:.16;-webkit-mask-image:radial-gradient(closest-side, rgba(0,0,0,1), rgba(0,0,0,0))}
.vig{position:absolute;inset:0;background:radial-gradient(circle at 50% 50%, rgba(0,0,0,0) 45%, rgba(0,0,0,.42) 100%)}
.halo{position:absolute;left:50%;top:50%;width:${0.66 * S}px;height:${0.66 * S}px;margin:-${0.33 * S}px;border-radius:50%;background:radial-gradient(circle, rgba(170,200,255,.16), rgba(170,200,255,.05) 45%, rgba(0,0,0,0) 70%)}
svg.layer{position:absolute;inset:0}
.card{position:absolute;box-sizing:border-box;border:solid ${S * 0.0042}px #F4F6FA;border-radius:${S * 0.008}px;overflow:hidden;background:#000;
  box-shadow:0 ${S * 0.008}px ${S * 0.022}px rgba(0,0,0,.7), 0 0 0 ${S * 0.0011}px rgba(226,184,97,.9), 0 0 ${S * 0.014}px rgba(170,190,255,.22)}
.card img{width:100%;height:100%;object-fit:cover;display:block}
.gloss{position:absolute;inset:0;background:linear-gradient(155deg, rgba(255,255,255,.2) 0%, rgba(255,255,255,0) 36%)}
.compass{position:absolute;left:${cl}px;top:${ct}px;width:${CW}px;filter:drop-shadow(0 ${S * 0.02}px ${S * 0.03}px rgba(0,0,0,.8))}
.flare{position:absolute;transform:translate(-50%,-50%);mix-blend-mode:screen}
.grain{position:absolute;inset:0;opacity:.07;mix-blend-mode:overlay}
</style></head><body><div class="icon">
<div class="bg"></div><div class="neb neb1"></div><div class="neb neb2"></div><div class="neb neb3"></div><div class="vig"></div><div class="halo"></div>
<svg class="layer" width="${S}" height="${S}" viewBox="0 0 ${S} ${S}"><defs>
  <filter id="nebA" x="-10%" y="-10%" width="120%" height="120%"><feTurbulence type="fractalNoise" baseFrequency="0.0035" numOctaves="5" seed="4" result="n"/>
    <feDisplacementMap in="SourceGraphic" in2="n" scale="${S * 0.045}" xChannelSelector="R" yChannelSelector="G" result="d"/><feGaussianBlur in="d" stdDeviation="${S * 0.007}"/></filter>
  <filter id="nebB" x="-10%" y="-10%" width="120%" height="120%"><feTurbulence type="fractalNoise" baseFrequency="0.009" numOctaves="4" seed="9" result="n"/>
    <feDisplacementMap in="SourceGraphic" in2="n" scale="${S * 0.018}" xChannelSelector="R" yChannelSelector="G" result="d"/><feGaussianBlur in="d" stdDeviation="${S * 0.0022}"/></filter>
  <radialGradient id="endfade" gradientUnits="userSpaceOnUse" cx="${at(1.1)[0]}" cy="${at(1.1)[1]}" r="${S * 0.17}"><stop offset="0" stop-color="black"/><stop offset=".25" stop-color="black"/><stop offset="1" stop-color="white"/></radialGradient>
  <mask id="fade" maskUnits="userSpaceOnUse" x="0" y="0" width="${S}" height="${S}"><rect width="${S}" height="${S}" fill="url(#endfade)"/></mask>
  <filter id="soft"><feGaussianBlur stdDeviation="${S * 0.0007}"/></filter>
  <linearGradient id="ng" gradientUnits="userSpaceOnUse" x1="${S * .1}" y1="${S * .1}" x2="${S * .9}" y2="${S * .9}">
    <stop offset="0" stop-color="#f5b8ff"/><stop offset=".35" stop-color="#a99bff"/><stop offset=".65" stop-color="#78d9ff"/><stop offset="1" stop-color="#ffd99a"/></linearGradient>
</defs>
  <g mask="url(#fade)"><path d="${trailPath()}" fill="none" stroke="url(#ng)" stroke-width="${S * 0.034}" opacity=".26" filter="url(#nebA)" stroke-linecap="round"/>
  <path d="${trailPath()}" fill="none" stroke="url(#ng)" stroke-width="${S * 0.011}" opacity=".42" filter="url(#nebB)" stroke-linecap="round"/>
  <path d="${trailPath()}" fill="none" stroke="#fff6e2" stroke-width="${S * 0.0013}" opacity=".55" filter="url(#soft)"/></g>
  ${dust()}
</svg>
${cards()}
<img class="compass" src="out/compass_real_3200_dof.png">
<svg width="0" height="0" style="position:absolute"><defs><radialGradient id="fg"><stop offset="0" stop-color="#fff" stop-opacity="1"/><stop offset=".3" stop-color="#e0ecff" stop-opacity=".45"/><stop offset="1" stop-color="#9cc3ff" stop-opacity="0"/></radialGradient></defs></svg>

${pos.dias.map(([x, y], i) => i === 2
  ? `<div style="position:absolute;left:${cl + x * k}px;top:${ct + y * k}px;width:${S * 0.04}px;height:${S * 0.04}px;transform:translate(-50%,-50%);border-radius:50%;mix-blend-mode:screen;background:radial-gradient(circle, rgba(150,180,255,.38), rgba(90,120,240,.12) 45%, rgba(0,0,0,0) 70%)"></div>`
  : flare(x, y, S * 0.026, .65)).join('')}
<svg class="grain" width="${S}" height="${S}"><filter id="gr"><feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" seed="5"/><feColorMatrix type="saturate" values="0"/></filter><rect width="100%" height="100%" filter="url(#gr)"/></svg>
</div></body></html>`;
(async () => {
  fs.writeFileSync('v_real.html', html);
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: S, height: S } });
  await p.goto('http://127.0.0.1:8766/v_real.html', { timeout: 180000 });
  await p.waitForLoadState('networkidle');
  await p.evaluate(() => Promise.all([...document.images].map(i => i.decode().catch(() => {}))));
  await p.waitForTimeout(1500);
  await p.screenshot({ path: process.env.SQUARE ? 'out/icona_reale_quadrata_4096.png' : 'out/icona_reale_4096.png', omitBackground: true, timeout: 240000 });
  await b.close(); console.log('ok');
})();
