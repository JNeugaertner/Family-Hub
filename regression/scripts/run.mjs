import { spawn } from 'node:child_process';
import { mkdir, open, unlink } from 'node:fs/promises';
await mkdir('reports', { recursive: true });
let lock;
try { lock = await open('reports/.running', 'wx'); }
catch { console.error('Ein Testlauf ist bereits aktiv.'); process.exit(2); }
let child;
try {
  child = spawn(process.execPath, ['node_modules/@cucumber/cucumber/bin/cucumber.js', '--config', 'cucumber.mjs', ...process.argv.slice(2)], { stdio: 'inherit', env: process.env });
  const code = await new Promise((resolve, reject) => { child.on('error', reject); child.on('exit', resolve); });
  process.exitCode = code ?? 1;
} finally { await lock.close(); await unlink('reports/.running'); }
