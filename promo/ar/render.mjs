import { chromium } from "playwright";
import http from "node:http"; import fs from "node:fs"; import path from "node:path";
const root = path.dirname(new URL(import.meta.url).pathname);
const types = { ".html": "text/html", ".js": "text/javascript", ".woff2": "font/woff2", ".mjs": "text/javascript" };
const srv = http.createServer((q, r) => { const f = path.join(root, decodeURIComponent(q.url.split("?")[0])); fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); return; } r.writeHead(200, { "content-type": types[path.extname(f)] || "application/octet-stream" }); r.end(d); }); }).listen(0);
const port = srv.address().port;
const [,, outDir, fromS, toS, stepS, workersS] = process.argv;
const frames = []; for (let f = +fromS; f < +toS; f += +(stepS || 1)) frames.push(f);
const workers = +(workersS || 1);
fs.mkdirSync(outDir, { recursive: true });
const browser = await chromium.launch({ args: ["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist"] });
async function worker(k) {
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  page.on("console", m => { if (m.type() === "error" || m.type() === "warning") console.log("[page]", m.text()); });
  page.on("pageerror", e => console.log("[pageerror]", e.message));
  await page.goto(`http://localhost:${port}/promo-ar.html?render`);
  await page.waitForFunction(() => window.READY === true, null, { timeout: 120000 });
  for (let i = k; i < frames.length; i += workers) {
    const f = frames[i];
    const data = await page.evaluate(t => { window.renderAt(t); return document.getElementById("stage").toDataURL("image/png"); }, f / 30);
    fs.writeFileSync(path.join(outDir, `f${String(f).padStart(4, "0")}.png`), Buffer.from(data.split(",")[1], "base64"));
    if (f % 30 === 0) console.log("frame", f);
  }
}
const t0 = Date.now();
await Promise.all(Array.from({ length: workers }, (_, k) => worker(k)));
console.log("done", frames.length, "frames in", (Date.now() - t0) / 1000, "s");
await browser.close(); srv.close();
