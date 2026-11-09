import { When, Then, AfterAll } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { baseURL, username, password } from '../support/world.mjs';
const require = createRequire(import.meta.url);
const audits = [];
const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
Then('prüfe ich {string} mit axe auf WCAG A und AA', async function (name) {
  await this.page.evaluate(() => document.fonts.ready);
  await this.page.addScriptTag({ path: require.resolve('axe-core/axe.min.js') });
  const result = await this.page.evaluate(async () => window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a','wcag2aa','wcag21a','wcag21aa','wcag22aa'] } }));
  const filename = 'a11y-' + name.replace(/[^a-z0-9]/gi, '_');
  await mkdir('reports/audit', { recursive: true });
  await writeFile(`reports/audit/${filename}.json`, JSON.stringify(result, null, 2));
  await this.page.screenshot({ path: `reports/audit/${filename}.png`, fullPage: true });
  audits.push({ type: 'axe', name, violations: result.violations, incomplete: result.incomplete.length, passes: result.passes.length });
  await this.attach(JSON.stringify(result.violations, null, 2), 'application/json');
  expect(result.violations.map(v => ({ id: v.id, impact: v.impact, elements: v.nodes.length })), `${name}: axe-Verstöße`).toEqual([]);
});
Then('kann ich mich ausschließlich mit der Tastatur anmelden', async function () {
  const user = this.page.getByLabel('Benutzername', { exact: true });
  await expect(user).toBeFocused();
  await this.page.keyboard.type(username);
  await this.page.keyboard.press('Tab'); await expect(this.page.getByLabel('Passwort', { exact: true })).toBeFocused();
  await this.page.keyboard.type(password);
  await this.page.keyboard.press('Tab'); await expect(this.page.getByRole('button', { name: 'Anmelden', exact: true })).toBeFocused();
  await this.page.keyboard.press('Enter');
  await expect(this.page.getByRole('heading', { name: 'Übersicht', exact: true })).toBeVisible();
  audits.push({ type: 'keyboard', name: 'Anmeldung per Tab und Enter', passed: true });
});
When('ich den Backend-Injection-Versuch {string} ausführe', async function (name) {
  this.auditName = name; this.auditExpected = 201; this.auditRendered = false;
  this.auditInput = { name: 'E2E-' + randomUUID(), quantity: '1', category: 'vorrat', urgent: false };
  let path = '/api/shopping';
  if (name.startsWith('login-')) {
    this.auditContext = await this.context.browser().newContext(); this.contexts.push(this.auditContext);
    await this.auditContext.request.get(baseURL + '/api/auth/status');
    const token = (await this.auditContext.cookies()).find(c => c.name === 'XSRF-TOKEN');
    const options = { headers: { 'X-XSRF-TOKEN': decodeURIComponent(token.value) } };
    if (name === 'login-operator-json') options.data = { username: { $ne: null }, password: { $ne: null } };
    else options.form = name === 'login-operator-form' ? { 'username[$ne]': '', 'password[$ne]': '' } : { username: "' OR 1=1 --", password: 'wrong' };
    this.response = await this.auditContext.request.post(baseURL + '/api/auth/login', options);
    this.auditExpected = 401; return;
  }
  const operator = { 'mongo-ne': { $ne: null }, 'mongo-regex': { $regex: '.*' }, 'mongo-where': { $where: 'return true' } }[name];
  if (operator) { this.auditInput.name = operator; this.auditExpected = 400; }
  if (name === 'xss-name-svg') { this.auditInput.name = '<svg onload="window.__auditXss=1"></svg>'; this.auditRendered = true; }
  if (name === 'xss-quantity-img') { this.auditInput.quantity = '<img src=x onerror=top.x=1>'; this.auditRendered = true; }
  if (name === 'xss-encoded-html') { this.auditInput.name = '&lt;img src=x onerror=window.__auditXss=1&gt;'; this.auditRendered = true; }
  if (name === 'command-literal') this.auditInput.name = '$(printf E2E_COMMAND_MARKER); echo E2E_MARKER';
  if (name === 'template-literal') this.auditInput.name = '{{7*7}} ${7*7} <%= 7*7 %>';
  if (name === 'prototype-properties') this.auditInput = JSON.parse(JSON.stringify(this.auditInput).slice(0, -1) + ',"__proto__":{"isAdmin":true},"constructor":{"prototype":{"isAdmin":true}}}');
  if (['xss-member-name', 'username-crlf'].includes(name)) {
    path = '/api/members';
    this.auditInput = { name: name === 'xss-member-name' ? '<img src=x onerror="window.__auditXss=1">' : 'E2E CRLF', username: name === 'username-crlf' ? 'user\r\nX-E2E: injected' : 'inj-' + randomUUID().slice(0, 12), password: 'Injection-fixture-2026!', color: '#2563EB', role: 'gast', roleFixed: true };
    this.auditExpected = name === 'username-crlf' ? 400 : 201; this.auditRendered = name === 'xss-member-name';
  }
  if (name === 'traversal-member') {
    this.response = await this.api('GET', '/api/members/..%2F..%2Fetc%2Fpasswd'); this.auditExpected = [400, 404, 200]; this.auditTraversal = true; return;
  }
  this.auditPath = path;
  this.auditBefore = await (await this.api('GET', path)).json();
  this.response = await this.api('POST', path, this.auditInput);
  if (this.response.status() === 201) {
    this.auditEntity = await this.response.json();
    (path === '/api/members' ? this.memberIds : this.createdItemIds).push(this.auditEntity.id);
  }
});
Then('hat der Versuch die erwartete sichere Wirkung', async function () {
  const record = { type: 'injection', name: this.auditName, status: this.response.status(), expected: this.auditExpected, passed: false };
  audits.push(record);
  expect(Array.isArray(this.auditExpected) ? this.auditExpected : [this.auditExpected], await this.response.text()).toContain(this.response.status());
  if (this.auditTraversal && this.response.status() === 200) {
    // Nginx normalisiert den Pfad vor der Location-Auswahl. Ein SPA-Fallback
    // ist erlaubt, aber nur, wenn exakt die öffentliche Startseite geliefert wird.
    expect(this.response.headers()['content-type']).toContain('text/html');
    expect(await this.response.text()).toBe(await (await this.api('GET', '/')).text());
    record.note = 'Öffentliche SPA-Startseite; kein Zugriff auf die angefragte Datei.';
  }
  if (this.auditContext) expect((await this.auditContext.request.get(baseURL + '/api/auth/me')).status()).toBe(401);
  if (this.auditPath) {
    const after = await (await this.api('GET', this.auditPath)).json();
    const other = after.filter(e => e.id !== this.auditEntity?.id);
    expect(other).toEqual(this.auditBefore);
    if (this.auditEntity) {
      const stored = after.find(e => e.id === this.auditEntity.id);
      expect(stored.name).toBe(this.auditInput.name);
      expect(stored).not.toHaveProperty('isAdmin'); expect(Object.hasOwn(stored, '__proto__')).toBe(false);
      if (this.auditPath === '/api/shopping') expect(stored.quantity).toBe(this.auditInput.quantity);
      if (this.auditRendered) {
        await this.page.goto(baseURL);
        const area = this.auditPath === '/api/members' ? 'Familie' : 'Einkauf';
        await this.page.getByRole('complementary').getByRole('navigation').getByRole('button', { name: area, exact: true }).click();
        await expect(this.page.getByText(this.auditInput.name, { exact: true }).first()).toBeVisible();
        expect(await this.page.evaluate(() => window.__auditXss)).toBeUndefined();
        expect(await this.page.evaluate(() => window.x)).toBeUndefined();
        expect(await this.page.locator('[onerror*="__auditXss"], [onload*="__auditXss"]').count()).toBe(0);
      }
    }
  }
  record.passed = true;
});
AfterAll(async function () {
  if (!audits.length) return;
  await mkdir('reports/audit', { recursive: true });
  await writeFile('reports/audit/summary.json', JSON.stringify(audits, null, 2));
  const sections = audits.map(r => `<section><h2>${escape(r.name)}</h2>${r.type === 'axe' ? `<p>${r.violations.length} Regelverstöße; ${r.incomplete} Regeln benötigen manuelle Prüfung.</p>` + r.violations.map(v => `<h3>${escape(v.id)} — ${escape(v.impact)}</h3><p>${escape(v.help)}</p><a href="${escape(v.helpUrl)}">Regel erläutert</a><ul>${v.nodes.map(n => `<li><code>${escape(n.target.join(' '))}</code><pre>${escape(n.failureSummary)}</pre></li>`).join('')}</ul>`).join('') : `<p>${r.passed ? 'Bestanden' : 'Fehlgeschlagen'}${r.status ? '; HTTP ' + r.status : ''}</p>`}</section>`).join('');
  await writeFile('reports/audit/index.html', `<!doctype html><html lang="de"><meta charset="utf-8"><title>FamilyHub Audit</title><style>body{font:16px system-ui;max-width:1100px;margin:40px auto;padding:20px}section{border-top:1px solid #ccc;padding:15px 0}pre,code{white-space:pre-wrap;overflow-wrap:anywhere}h3{color:#923}</style><h1>Backend-Injections und Barrierefreiheit</h1><p>axe-core 4.14.0, WCAG 2 A/AA, 2.1 A/AA und 2.2 AA. Automatische Regeln und Tastatur-Anmeldung; kein vollständiges WCAG-Konformitätsurteil.</p>${sections}</html>`);
});
