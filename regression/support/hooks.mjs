import { BeforeAll, AfterAll, Before, After, Status } from '@cucumber/cucumber';
import { chromium, expect } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import { baseURL, username, password } from './world.mjs';
let browser;
export async function login(page, user = username, pass = password) {
  await page.goto(baseURL);
  if ((await page.request.get(baseURL + '/api/auth/me')).status() !== 200) {
    await page.getByLabel('Benutzername', { exact: true }).fill(user);
    await page.getByLabel('Passwort', { exact: true }).fill(pass);
    await page.getByRole('button', { name: 'Anmelden', exact: true }).click();
  }
  await expect(page.getByRole('heading', { name: 'Übersicht', exact: true })).toBeVisible();
}
BeforeAll(async function () {
  await mkdir('reports', { recursive: true });
  browser = await chromium.launch({ headless: process.env.HEADLESS === 'true', channel: process.env.BROWSER_CHANNEL || undefined, slowMo: Number(process.env.SLOW_MO || 80), args: ['--disable-dev-shm-usage'] });
  const context = await browser.newContext();
  try {
    const status = await context.request.get(baseURL + '/api/auth/status');
    expect(status.ok(), 'Anwendung muss vor Testbeginn bereit sein').toBeTruthy();
    if ((await status.json()).setupRequired) {
      const page = await context.newPage();
      await page.goto(baseURL);
      await page.getByLabel('Dein Name', { exact: true }).fill('Regression Admin');
      await page.getByLabel('Benutzername', { exact: true }).fill(username);
      await page.getByLabel('Passwort (mind. 8 Zeichen)', { exact: true }).fill(password);
      await page.getByLabel('Passwort wiederholen', { exact: true }).fill(password);
      await page.getByRole('button', { name: 'Familie einrichten', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'Übersicht', exact: true })).toBeVisible();
    }
  } finally { await context.close(); }
});
Before(async function () {
  this.context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'de-DE', timezoneId: 'Europe/Berlin' });
  this.contexts.push(this.context);
  await this.context.tracing.start({ screenshots: true, snapshots: true, sources: true });
  this.page = await this.context.newPage();
});
After(async function ({ result, pickle }) {
  const slug = pickle.name.replace(/[^a-zA-Z0-9]/g, '_') + '-' + Date.now();
  const failures = [];
  try {
    if (result?.status === Status.FAILED) {
      await this.attach(await this.page.screenshot({ fullPage: true }), 'image/png');
      await this.context.tracing.stop({ path: `reports/${slug}.zip` });
    } else { await this.context.tracing.stop(); }
  } catch (error) { failures.push(error); }
  try {
    if (this.itemNames.length) {
      // Nur die Artikel dieses Szenarios löschen, niemals die gesamte Einkaufsliste.
      await login(this.page);
      const response = await this.api('GET', '/api/shopping');
      expect(response.ok()).toBeTruthy();
      for (const item of await response.json()) {
        if (this.itemNames.includes(item.name)) {
          const deleted = await this.api('DELETE', `/api/shopping/${item.id}`);
          expect(deleted.ok()).toBeTruthy();
        }
      }
    }
  } catch (error) { failures.push(error); }
  try {
    if (this.createdItemIds.length || this.memberIds.length) {
      // Eigener Admin-Kontext: unabhängig von der im Szenario geprüften Rolle.
      const cleanup = await browser.newContext();
      try {
        const page = await cleanup.newPage(); await login(page);
        const token = (await cleanup.cookies()).find(c => c.name === 'XSRF-TOKEN');
        const headers = { 'X-XSRF-TOKEN': decodeURIComponent(token.value) };
        for (const id of this.createdItemIds) {
          const response = await cleanup.request.delete(baseURL + '/api/shopping/' + id, { headers });
          expect([204, 404]).toContain(response.status());
        }
        for (const id of [...this.memberIds].reverse()) {
          const response = await cleanup.request.delete(baseURL + '/api/members/' + id, { headers });
          expect([204, 404]).toContain(response.status());
        }
      } finally { await cleanup.close(); }
    }
  } catch (error) { failures.push(error); }
  finally { for (const context of this.contexts) await context.close(); }
  if (failures.length) throw new AggregateError(failures, 'Artefakte oder Bereinigung fehlgeschlagen');
});
AfterAll(async function () { await browser?.close(); });
