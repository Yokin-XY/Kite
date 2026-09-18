import { build } from 'esbuild';
import { writeFileSync, mkdirSync } from 'node:fs';

const TARGETS = [
  ['claude', 'providerPresets'],
  ['codex', 'codexProviderPresets'],
  ['gemini', 'geminiProviderPresets'],
  ['grokbuild', 'grokBuildProviderPresets'],
  ['hermes', 'hermesProviderPresets'],
  ['mcode', 'mcodeProviderPresets'],
  ['openclaw', 'openclawProviderPresets'],
  ['opencode', 'opencodeProviderPresets'],
  ['pi', 'piProviderPresets'],
  ['universal', 'universalProviderPresets'],
];

const strip = (v) => {
  if (Array.isArray(v)) return v.map(strip);
  if (v && typeof v === 'object') {
    const out = {};
    for (const [k, val] of Object.entries(v)) out[k] = strip(val);
    return out;
  }
  return v;
};

mkdirSync('out', { recursive: true });
let total = 0;
const summary = [];
for (const [app, exportName] of TARGETS) {
  const entry = `tmp-entry-${app}.mts`;
  writeFileSync(entry, `import data from './src/config/${app === 'grokbuild' ? 'grokBuildProviderPresets' : app + 'ProviderPresets'}';\nexport default data;\n`);
  // 直接 import 命名导出更稳
  writeFileSync(entry, `import { ${exportName} } from './src/config/${app === 'grokbuild' ? 'grokBuildProviderPresets' : app + 'ProviderPresets'}';\nexport default ${exportName};\n`);
  const outfile = `tmp-entry-${app}.mjs`;
  await build({
    entryPoints: [entry],
    bundle: true, format: 'esm', platform: 'node',
    outfile, logLevel: 'error',
    alias: { '@': './src', 'smol-toml': './stubs/smol-toml.ts' },
  });
  const mod = await import(`./${outfile}`);
  const data = mod.default;
  const json = JSON.stringify(strip(data), null, 1);
  writeFileSync(`out/${app}-presets.json`, json);
  const size = Buffer.byteLength(json);
  total += size;
  summary.push(`${app}: ${data.length} presets, ${(size/1024).toFixed(1)} KB`);
  console.log(summary[summary.length-1]);
}
console.log(`TOTAL: ${(total/1024).toFixed(1)} KB`);
