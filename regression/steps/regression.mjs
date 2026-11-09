import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { login } from '../support/hooks.mjs';
import { baseURL, username, password } from '../support/world.mjs';
const row = world => world.page.locator('[data-item]').filter({ has: world.page.getByText(world.itemName, { exact: true }) });
Given('ich bin als Administrator angemeldet', async function () { await login(this.page); });
Given('ich öffne die Anmeldung', async function () {
  await this.page.goto(baseURL);
  await expect(this.page.getByRole('form', { name: 'Anmelden', exact: true })).toBeVisible();
});
When('ich die Seite neu lade', async function () { await this.page.reload(); });
When('ich mich abmelde', async function () { await this.page.getByRole('button', { name: 'Abmelden', exact: true }).click(); });
Then('sehe ich das Anmeldeformular', async function () { await expect(this.page.getByRole('form', { name: 'Anmelden', exact: true })).toBeVisible(); });
Then('die API verweigert den Zugriff auf mein Profil', async function () { expect((await this.api('GET', '/api/auth/me')).status()).toBe(401); });
When('ich mich mit einem unbekannten Benutzer anmelde', async function () {
  await this.page.getByLabel('Benutzername', { exact: true }).fill('unknown-' + randomUUID());
  await this.page.getByLabel('Passwort', { exact: true }).fill('WrongPassword!');
  await this.page.getByRole('button', { name: 'Anmelden', exact: true }).click();
});
Then('sehe ich eine Anmeldefehlermeldung', async function () { await expect(this.page.getByRole('alert')).toContainText(/Benutzername oder Passwort ist falsch/); });
Then('ist die Anmeldung ohne Zugangsdaten deaktiviert', async function () { await expect(this.page.getByRole('button', { name: 'Anmelden', exact: true })).toBeDisabled(); });
Then('ist ein zweiter Browser nicht angemeldet', async function () {
  const other = await this.context.browser().newContext(); this.contexts.push(other);
  expect((await other.request.get(baseURL + '/api/auth/me')).status()).toBe(401);
  const page = await other.newPage(); await page.goto(baseURL);
  await expect(page.getByRole('form', { name: 'Anmelden', exact: true })).toBeVisible();
});
When('ich den Bereich {string} öffne', async function (name) { await this.page.getByRole('complementary').getByRole('navigation').getByRole('button', { name, exact: true }).click(); });
Then('sehe ich die Seite {string}', async function (title) { await expect(this.page.getByRole('heading', { name: title, exact: true, level: 1 })).toBeVisible(); });
Then('der Bereich {string} ist als aktiv markiert', async function (name) { await expect(this.page.getByRole('complementary').getByRole('navigation').getByRole('button', { name, exact: true })).toHaveAttribute('aria-current', 'page'); });
When('ich die mobile Ansicht verwende', async function () { await this.page.setViewportSize({ width: 390, height: 844 }); });
When('ich das mobile Menü öffne', async function () { await this.page.getByRole('button', { name: 'Menü öffnen', exact: true }).click(); });
When('ich einen neuen Testartikel mit Menge {string} anlege', async function (quantity) {
  this.itemName = 'E2E-' + randomUUID(); this.itemNames.push(this.itemName);
  await this.page.getByRole('textbox', { name: 'Neuer Artikel', exact: true }).fill(this.itemName);
  await this.page.getByRole('textbox', { name: 'Menge', exact: true }).fill(quantity);
  await this.page.getByRole('button', { name: 'Hinzufügen', exact: true }).click();
  await expect(row(this)).toBeVisible();
});
Then('sehe ich den Testartikel mit Menge {string}', async function (quantity) { await expect(row(this)).toBeVisible(); await expect(row(this)).toContainText(quantity); });
When('ich den Testartikel als gekauft markiere', async function () { await row(this).getByRole('button', { name: this.itemName + ' gekauft', exact: true }).click(); });
Then('ist der Testartikel gekauft', async function () { await expect(row(this).getByRole('button', { name: this.itemName + ' nicht gekauft', exact: true })).toHaveAttribute('aria-pressed', 'true'); });
When('ich den Testartikel wieder öffne', async function () { await row(this).getByRole('button', { name: this.itemName + ' nicht gekauft', exact: true }).click(); });
Then('ist der Testartikel offen', async function () { await expect(row(this).getByRole('button', { name: this.itemName + ' gekauft', exact: true })).toHaveAttribute('aria-pressed', 'false'); });
When('ich die Menge des Testartikels auf {string} ändere', async function (quantity) {
  await row(this).getByRole('button', { name: this.itemName + ' bearbeiten', exact: true }).click();
  const editor = this.page.locator('div').filter({ has: this.page.getByRole('textbox', { name: 'Artikel', exact: true }) }).filter({ has: this.page.getByRole('button', { name: 'Speichern', exact: true }) }).last();
  await editor.getByRole('textbox', { name: 'Menge', exact: true }).fill(quantity);
  await editor.getByRole('button', { name: 'Speichern', exact: true }).click();
});
When('ich den Testartikel entferne', async function () { await row(this).getByRole('button', { name: this.itemName + ' entfernen', exact: true }).click(); });
Then('ist der Testartikel nicht mehr vorhanden', async function () { await expect(row(this)).toHaveCount(0); });
Then('kann ich keinen leeren Artikel hinzufügen', async function () { await this.page.getByRole('textbox', { name: 'Neuer Artikel', exact: true }).fill('   '); await expect(this.page.getByRole('button', { name: 'Hinzufügen', exact: true })).toBeDisabled(); });
Given('ein zweiter Administrator betrachtet die Einkaufsliste', async function () {
  const other = await this.context.browser().newContext({ viewport: { width: 1440, height: 900 } }); this.contexts.push(other);
  this.secondPage = await other.newPage();
  const liveReady = this.secondPage.waitForResponse(response => new URL(response.url()).pathname === '/api/live' && response.status() === 200);
  await login(this.secondPage);
  await liveReady;
  await this.secondPage.getByRole('complementary').getByRole('navigation').getByRole('button', { name: 'Einkauf', exact: true }).click();
  await expect(this.secondPage.getByRole('textbox', { name: 'Neuer Artikel', exact: true })).toBeVisible();
});
Then('erscheint der Testartikel im zweiten Browser', async function () { await expect(this.secondPage.locator('[data-item]').filter({ has: this.secondPage.getByText(this.itemName, { exact: true }) })).toBeVisible({ timeout: 15000 }); });
Then('liefert der anonyme API-Aufruf {string} den Status {int}', async function (path, status) { expect((await this.api('GET', path)).status()).toBe(status); });
When('ich einen Artikel ohne CSRF-Token per API anlege', async function () {
  const name = 'E2E-CSRF-' + randomUUID(); this.itemNames.push(name);
  this.response = await this.api('POST', '/api/shopping', { name, category: 'vorrat', quantity: null, urgent: false }, false);
});
When('ich die Ersteinrichtung erneut per API aufrufe', async function () { this.response = await this.api('POST', '/api/auth/setup', { name: 'Duplicate Admin', username, password, color: '#2563EB' }); });
Then('hat die API-Antwort den Status {int}', function (status) { expect(this.response.status()).toBe(status); });
