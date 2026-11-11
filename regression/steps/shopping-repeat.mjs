import { When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { today, addDays } from '../support/days.mjs';

// Rhythmus aus dem Feature: "trip" (bei jedem Einkauf) oder "<Abstand> <days|weeks|months>"
const repeatFrom = text => {
  if (text === 'trip') return { unit: 'trip' };
  const [interval, unit] = text.split(' ');
  return { interval: Number(interval), unit };
};

async function addItem(world, repeat) {
  world.itemName = 'E2E-' + randomUUID();
  world.itemNames.push(world.itemName);
  const response = await world.api('POST', '/api/shopping', { name: world.itemName, quantity: '1', category: 'vorrat', urgent: false, repeat });
  expect(response.status(), await response.text()).toBe(201);
  world.item = await response.json();
}

async function currentItem(world) {
  return (await (await world.api('GET', '/api/shopping')).json()).find(i => i.id === world.item.id);
}

When('ich per API einen wiederkehrenden Testartikel mit Rhythmus {string} anlege', async function (rhythm) {
  await addItem(this, repeatFrom(rhythm));
});

When('ich per API einen gewöhnlichen Testartikel anlege', async function () {
  await addItem(this, null);
});

// Achtung: "Abgehakte entfernen" gilt für die ganze Liste. Deshalb nur in der Wegwerf-Testumgebung
// (regression.sh) oder einem Testlabor ausführen, nie gegen echte Familiendaten.
When('ich den Testartikel per API abhake und die abgehakten Artikel entferne', async function () {
  const checked = await this.api('PATCH', `/api/shopping/${this.item.id}/checked`, { checked: true });
  expect(checked.status(), await checked.text()).toBe(200);
  const removed = await this.api('DELETE', '/api/shopping/checked');
  expect(removed.status(), await removed.text()).toBe(200);
});

Then('steht der Testartikel wieder offen auf der Liste', async function () {
  const item = await currentItem(this);
  expect(item, 'Artikel muss erhalten bleiben').toBeTruthy();
  expect(item.checked).toBe(false);
  expect(item.returnsOn ?? null).toBeNull();
});

Then('kommt der Testartikel in {int} Tagen wieder', async function (days) {
  const item = await currentItem(this);
  expect(item, 'Artikel muss erhalten bleiben').toBeTruthy();
  expect(item.returnsOn).toBe(addDays(today(), days));
  expect(item.checkedOn).toBe(today());
});

Then('ist der Testartikel von der Liste verschwunden', async function () {
  expect(await currentItem(this)).toBeUndefined();
});
