#!/usr/bin/env node
// TypeScript'e kademeli gecis sirasinda (bkz. proje plani) katman sirasini
// zorlar: bir .ts/.tsx dosyasi hala .js/.jsx olan bir dosyayi import ederse
// bunu raporlar.
//
// Neden gerekli: allowJs:true + checkJs:false altinda TS, donusturulmemis
// bir .jsx dosyasindan (orn. destructured, tipsiz React props) sizan "any"
// icin HICBIR derleme hatasi vermiyor -- checkJs:false o dosyanin kendi
// icindeki noImplicitAny uyarisini bastiriyor, tuketen .tsx tarafinda da
// hata olusmuyor cunku orada eksik bir tip bildirimi yok, zaten "any" olan
// bir sey kullaniliyor. Bu sizinti derleyicide GORUNMEZ ve kaynak kodunda
// literal "any" kelimesi de gecmedigi icin grep'le de yakalanamaz. Tek
// gercek savunma: donusturulen her dosyanin importlarinin gercekten .ts/.tsx
// dosyalarina cozumlendigini diskte fiilen kontrol etmek.
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, dirname, resolve, extname } from "node:path";

const SRC_ROOT = resolve(process.cwd(), "src");
const IMPORT_RE = /(?:from|import)\s*["'](\.[^"']+)["']/g;
const RESOLVABLE_EXTS = ["", ".ts", ".tsx", ".js", ".jsx"];
const NON_MODULE_EXTS = new Set([".css", ".png", ".jpg", ".jpeg", ".svg", ".json"]);

function walk(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory()) out.push(...walk(full));
    else if (/\.(ts|tsx)$/.test(entry)) out.push(full);
  }
  return out;
}

function resolveImport(fromFile, spec) {
  if (NON_MODULE_EXTS.has(extname(spec))) return null; // CSS/resim -- modul degil
  const base = resolve(dirname(fromFile), spec);
  for (const ext of RESOLVABLE_EXTS) {
    const candidate = base + ext;
    try {
      if (statSync(candidate).isFile()) return candidate;
    } catch {
      /* yok, sonrakini dene */
    }
  }
  return null;
}

const tsFiles = walk(SRC_ROOT);
let violations = 0;

for (const file of tsFiles) {
  const content = readFileSync(file, "utf-8");
  for (const match of content.matchAll(IMPORT_RE)) {
    const spec = match[1];
    const resolved = resolveImport(file, spec);
    if (resolved && /\.(js|jsx)$/.test(resolved)) {
      console.error(`VIOLATION: ${file} -> "${spec}" hala donusturulmemis (${resolved})`);
      violations++;
    }
  }
}

if (violations > 0) {
  console.error(`\n${violations} sinir ihlali bulundu -- katman sirasi bozulmus olabilir.`);
  process.exit(1);
} else {
  console.log(`OK: ${tsFiles.length} .ts/.tsx dosyasi tarandi, sinir ihlali yok.`);
  process.exit(0);
}
