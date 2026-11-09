import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { baseURL, username, password } from '../support/world.mjs';
const cases = JSON.parse(await readFile(new URL('../data/input-cases.json', import.meta.url), 'utf8'));
async function authenticate(context, user, pass) {
  await context.request.get(baseURL + '/api/auth/status');
  const csrf = (await context.cookies()).find(c => c.name === 'XSRF-TOKEN');
  const result = await context.request.post(baseURL + '/api/auth/login', {
    form: { username: user, password: pass }, headers: { 'X-XSRF-TOKEN': decodeURIComponent(csrf.value) }
  });
  if (result.status() === 200) await context.request.get(baseURL + '/api/auth/me');
  return result;
}
Given('ich bin für Backendtests über die API als Administrator angemeldet', async function () {
  expect((await authenticate(this.context, username, password)).status()).toBe(200);
});
When('ich die Eingabevariante {string} mit {string} an das Backend sende', async function (id, method) {
  this.inputCase = cases.find(c => c.id === id); expect(this.inputCase).toBeDefined();
  const c = this.inputCase; this.inputMethod = method;
  this.inputPath = c.entity === 'shopping' ? '/api/shopping' : '/api/members';
  const defaults = c.entity === 'shopping'
    ? { name: 'E2E-' + randomUUID(), quantity: '1', category: 'vorrat', urgent: false }
    : { name: 'E2E Unicode', username: 'uv-' + randomUUID().slice(0, 16), password: 'Unicode-default-2026!', color: '#2563EB', role: 'gast', roleFixed: true };
  this.variantRequest = { ...defaults, [c.field]: c.value };
  this.beforeVariants = await (await this.api('GET', this.inputPath)).json();
  if (method === 'PUT') {
    const fixture = await this.api('POST', this.inputPath, defaults);
    expect(fixture.status()).toBe(201); const entity = await fixture.json();
    (c.entity === 'shopping' ? this.createdItemIds : this.memberIds).push(entity.id);
    // Vergleich zweier persistierter Zustände: MongoDB speichert Zeitstempel
    // in Millisekunden, die POST-Antwort kann noch Nanosekunden enthalten.
    this.fixture = (await (await this.api('GET', this.inputPath)).json()).find(e => e.id === entity.id);
    expect(this.fixture).toBeDefined();
    this.response = await this.api('PUT', this.inputPath + '/' + entity.id, this.variantRequest);
  } else {
    this.response = await this.api('POST', this.inputPath, this.variantRequest);
    if (this.response.status() === 201) {
      const entity = await this.response.json();
      (c.entity === 'shopping' ? this.createdItemIds : this.memberIds).push(entity.id);
    }
  }
  await this.attach(JSON.stringify({ id, method, entity: c.entity, field: c.field,
    value: c.field === 'password' ? '[TESTPASSWORT AUSGEBLENDET]' : c.value,
    utf16Units: c.value?.length ?? null, codePoints: c.value == null ? null : [...c.value].length,
    utf8Bytes: c.value == null ? null : Buffer.byteLength(c.value, 'utf8'),
    expectedStatus: c.valid ? (method === 'POST' ? 201 : 200) : 400,
    actualStatus: this.response.status()
  }, null, 2), 'application/json');
});
Then('prüfe ich Validierung und Speicherung dieser Eingabevariante', async function () {
  const c = this.inputCase;
  const expectedStatus = c.valid ? (this.inputMethod === 'POST' ? 201 : 200) : 400;
  expect(this.response.status(), `Fall ${c.id}; Antwort: ${await this.response.text()}`).toBe(expectedStatus);
  const body = await this.response.json();
  const after = await (await this.api('GET', this.inputPath)).json();
  if (!c.valid) {
    expect(body.errors).toHaveProperty(c.field);
    // Keine Teiländerung, kein zusätzliches Konto/Artikel bei abgewiesener Eingabe.
    if (this.fixture) expect(after.find(e => e.id === this.fixture.id)).toEqual(this.fixture);
    const excludeFixture = after.filter(e => e.id !== this.fixture?.id);
    expect(excludeFixture).toEqual(this.beforeVariants);
    return;
  }
  const stored = after.find(e => e.id === body.id); expect(stored).toBeDefined();
  if (c.field !== 'password') {
    expect(body[c.field]).toEqual(c.stored);
    expect(stored[c.field]).toEqual(c.stored);
  } else {
    expect(body).not.toHaveProperty('password'); expect(body).not.toHaveProperty('passwordHash');
    // Erfolgreiche Registrierung reicht nicht: jedes Passwort muss exakt zur Anmeldung taugen.
    const context = await this.context.browser().newContext(); this.contexts.push(context);
    expect((await authenticate(context, this.variantRequest.username, c.value)).status()).toBe(200);
    if (c.value.trim() && c.value.trim() !== c.value) {
      const other = await this.context.browser().newContext(); this.contexts.push(other);
      expect((await authenticate(other, this.variantRequest.username, c.value.trim())).status()).toBe(401);
    }
  }
});
