// Rebuilds dist/assets/clubs/ and dist/assets/clubs.json from a checkout of the crest repository.
//
// The previous version read a logo-tree.json from an absolute scratch path that no longer exists,
// so it could not be run. This takes the checkout as an argument instead.
//
//   git clone --depth 1 https://github.com/luukhopman/football-logos /tmp/football-logos
//   node scripts/fetch-assets.mjs /tmp/football-logos
//
// Club badges are trademarks of their clubs. The source repository grants no licence over them;
// see dist/assets/SOURCES.txt. Add or remove leagues by editing LEAGUES below.
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SOURCE = process.argv[2];
if (!SOURCE) {
  console.error('usage: node scripts/fetch-assets.mjs <path to a football-logos checkout>');
  process.exit(2);
}

// Directory names as they appear under logos/ in the source repository.
const LEAGUES = [
  'England - Premier League',
  'Spain - LaLiga',
  'France - Ligue 1',
  'Türkiye - Süper Lig',
  'Italy - Serie A',
  'Germany - Bundesliga',
  'Portugal - Liga Portugal',
  'Netherlands - Eredivisie',
];

const REPO = 'https://raw.githubusercontent.com/luukhopman/football-logos/master';
const safe = name => name.replace(/[^a-zA-Z0-9]/g, '_');

const outDir = path.join(ROOT, 'dist/assets/clubs');
await fs.mkdir(outDir, {recursive: true});

const records = [];
const seen = new Set();
for (const league of LEAGUES) {
  const dir = path.join(SOURCE, 'logos', league);
  let entries;
  try {
    entries = await fs.readdir(dir);
  } catch {
    console.error(`skipped (not in checkout): ${league}`);
    continue;
  }
  let added = 0;
  for (const entry of entries.filter(e => e.endsWith('.png')).sort()) {
    const name = entry.replace(/\.png$/, '');
    // A club promoted between the source's leagues can appear twice; first listing wins.
    if (seen.has(name)) continue;
    seen.add(name);
    const file = `assets/clubs/${safe(name)}.png`;
    await fs.copyFile(path.join(dir, entry), path.join(ROOT, 'dist', file));
    records.push({
      name,
      file,
      source: `${REPO}/${['logos', league, entry].map(encodeURIComponent).join('/')}`,
      league,
    });
    added++;
  }
  console.error(`${String(added).padStart(3)}  ${league}`);
}

records.sort((a, b) => a.name.localeCompare(b.name));
await fs.writeFile(path.join(ROOT, 'dist/assets/clubs.json'), JSON.stringify(records, null, 2) + '\n');
console.error(`\nwrote ${records.length} crests to dist/assets/clubs.json`);
