import { When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { request } from './extended.mjs';
import { baseURL } from '../support/world.mjs';

const MONTHS = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
const countdown = days => days === 0 ? 'Heute' : days === 1 ? 'Morgen' : `noch ${days} Tage`;

async function upcoming(world, count = 3) {
  const response = await world.api('GET', `/api/holidays/upcoming?count=${count}`);
  expect(response.status()).toBe(200);
  return response.json();
}

Then('zeigt die Übersicht den nächsten Feiertag laut API mit den verbleibenden Tagen', async function () {
  [this.nextHoliday, ...this.laterHolidays] = await upcoming(this);
  const box = this.page.getByRole('region', { name: 'Countdown bis zu den nächsten Feiertagen' });
  await expect(box).toContainText(this.nextHoliday.name);
  await expect(box).toContainText(this.nextHoliday.daysLeft === 0 ? 'Heute!' : String(this.nextHoliday.daysLeft));
});

Then('darunter die beiden Feiertage danach', async function () {
  const box = this.page.getByRole('region', { name: 'Countdown bis zu den nächsten Feiertagen' });
  for (const h of this.laterHolidays) {
    await expect(box.getByRole('listitem').filter({ hasText: h.name })).toContainText(countdown(h.daysLeft));
  }
});

Then('zeigt der Kalender den Countdown zum nächsten Feiertag', async function () {
  [this.nextHoliday] = await upcoming(this, 1);
  await expect(this.page.getByTitle(`Countdown: ${this.nextHoliday.name}`, { exact: false }))
    .toContainText(countdown(this.nextHoliday.daysLeft));
});

When('ich im Monatskalender zum Monat des nächsten Feiertags blättere', async function () {
  const [year, month] = this.nextHoliday.date.split('-').map(Number);
  const heading = `${MONTHS[month - 1]} ${year}`;
  for (let i = 0; i < 13; i++) {
    if (await this.page.getByRole('heading', { name: heading, exact: true }).count()) return;
    await this.page.getByRole('button', { name: 'Nächster Monat', exact: true }).click();
  }
  await expect(this.page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
});

Then('ist der nächste Feiertag an seinem Tag markiert', async function () {
  await expect(this.page.getByTitle(`Feiertag: ${this.nextHoliday.name}`, { exact: true })).toBeVisible();
});

When('ich den Tag des nächsten Feiertags öffne', async function () {
  await this.page.getByTitle(`Feiertag: ${this.nextHoliday.name}`, { exact: true }).click();
});

Then('zeigt die Tagesansicht den Feiertag', async function () {
  await expect(this.page.getByText(`Feiertag: ${this.nextHoliday.name}`, { exact: false })).toBeVisible();
});

Then('liefert die API für 2027 die Feiertage', async function (table) {
  const response = await this.api('GET', '/api/holidays?from=2027-01-01&to=2027-12-31');
  expect(response.status()).toBe(200);
  const holidays = await response.json();
  expect(holidays.map(h => ({ key: h.key, date: h.date }))).toEqual(table.hashes());
});

Then('sieht das Testmitglied die nächsten Feiertage', async function () {
  const response = await request(this.actor, 'GET', '/api/holidays/upcoming?count=6');
  expect(response.status()).toBe(200);
  const holidays = await response.json();
  expect(holidays).toHaveLength(6);
  expect(holidays.map(h => h.daysLeft)).toEqual([...holidays.map(h => h.daysLeft)].sort((a, b) => a - b));
});

Then('ohne Anmeldung liefert die API keine Feiertage', async function () {
  const anonymous = await this.context.browser().newContext();
  this.contexts.push(anonymous);
  expect((await anonymous.request.get(baseURL + '/api/holidays/upcoming')).status()).toBe(401);
});

Then('die API lehnt einen Zeitraum über 400 Tage ab', async function () {
  const response = await this.api('GET', '/api/holidays?from=2027-01-01&to=2028-03-01');
  expect(response.status()).toBe(400);
  expect((await response.json()).errors).toHaveProperty('to');
});
