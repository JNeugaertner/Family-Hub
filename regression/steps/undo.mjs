import { When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { request } from './extended.mjs';

// Der Hinweis unten mit „Rückgängig“ (frontend/src/undo/UndoContext.tsx)
const notice = world => world.page.getByRole('status').filter({ has: world.page.getByRole('button', { name: 'Hinweis schließen', exact: true }) });

Then('der Hinweis bietet {string} an', async function (label) {
  await expect(notice(this).getByRole('button', { name: label, exact: true })).toBeVisible();
});

When('ich im Hinweis auf {string} klicke', async function (label) {
  await notice(this).getByRole('button', { name: label, exact: true }).click();
});

Then('meldet der Hinweis {string}', async function (text) {
  await expect(notice(this)).toContainText(text);
});

When('ich den Testtermin im Kalender öffne und lösche', async function () {
  await this.page.getByRole('button', { name: 'Tag', exact: true }).click();
  await this.page.getByText(this.eventTitle).first().click();
  const dialog = this.page.getByRole('dialog');
  await expect(dialog.getByRole('heading', { name: 'Termin bearbeiten', exact: true })).toBeVisible();
  await dialog.getByRole('button', { name: 'Löschen', exact: true }).click();
  await dialog.getByRole('button', { name: 'Wirklich löschen?', exact: true }).click();
  await expect(dialog).toHaveCount(0);
});

Then('ist der Testtermin laut API gelöscht', async function () {
  expect((await this.api('GET', `/api/events/${this.event.id}`)).status()).toBe(404);
});

When('ich die Testaufgabe über das Formular lösche', async function () {
  await this.page.getByRole('button', { name: `${this.taskTitle} bearbeiten`, exact: true }).click();
  const dialog = this.page.getByRole('dialog');
  await dialog.getByRole('button', { name: 'Löschen', exact: true }).click();
  await dialog.getByRole('button', { name: 'Wirklich löschen?', exact: true }).click();
  await expect(dialog).toHaveCount(0);
});

Then('ist die Testaufgabe laut API gelöscht', async function () {
  expect((await this.api('GET', `/api/tasks/${this.task.id}`)).status()).toBe(404);
});

When('ich per API einen Testartikel lösche und mir die Marke merke', async function () {
  this.itemName = 'E2E-' + randomUUID();
  this.itemNames.push(this.itemName);
  const created = await this.api('POST', '/api/shopping', { name: this.itemName, quantity: '1', category: 'vorrat', urgent: false });
  expect(created.status()).toBe(201);
  this.item = await created.json();
  const deleted = await this.api('DELETE', `/api/shopping/${this.item.id}`);
  expect(deleted.status()).toBe(204);
  this.undoToken = deleted.headers()['x-undo-token'];
  expect(this.undoToken, 'Löschung muss eine Marke liefern').toBeTruthy();
});

Then('kann das Testmitglied die Löschung nicht rückgängig machen', async function () {
  expect((await request(this.actor, 'POST', `/api/shopping/undo/${this.undoToken}`)).status()).toBe(404);
});

Then('ich kann sie genau einmal rückgängig machen', async function () {
  const first = await this.api('POST', `/api/shopping/undo/${this.undoToken}`);
  expect(first.status(), await first.text()).toBe(200);
  expect((await this.api('POST', `/api/shopping/undo/${this.undoToken}`)).status()).toBe(404);
  const items = await (await this.api('GET', '/api/shopping')).json();
  expect(items.filter(i => i.id === this.item.id)).toHaveLength(1);
});
