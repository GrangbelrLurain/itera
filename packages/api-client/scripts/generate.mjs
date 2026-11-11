// Generates src/generated/{admin,public}.ts from ../../contracts/*.openapi.json.
//   node scripts/generate.mjs           write the files
//   node scripts/generate.mjs --check   fail when the committed files are out of date
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';

const here = dirname(fileURLToPath(import.meta.url));
const check = process.argv.includes('--check');
const banner = '// Generated from contracts/<group>.openapi.json by scripts/generate.mjs. Do not edit.\n';

let stale = false;
for (const group of ['admin', 'public']) {
  const source = resolve(here, `../../../contracts/${group}.openapi.json`);
  const target = resolve(here, `../src/generated/${group}.ts`);
  const ast = await openapiTS(pathToFileURL(source));
  const output = `${banner.replace('<group>', group)}${astToString(ast)}`;
  if (check) {
    const current = existsSync(target) ? readFileSync(target, 'utf8') : '';
    if (current.replace(/\r\n/g, '\n') !== output) {
      console.error(`${target} is out of date: run "pnpm --filter @itera/api-client gen"`);
      stale = true;
    }
  } else {
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, output);
    console.log(`generated ${group}.ts`);
  }
}
process.exit(stale ? 1 : 0);
