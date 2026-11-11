import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { request } from './extended.mjs';
import { today, addDays } from '../support/days.mjs';

// --- Hilfen ---------------------------------------------------------------------------------------------------

async function myId(world) {
  const me = await world.api('GET', '/api/auth/me');
  expect(me.ok()).toBeTruthy();
  return (await me.json()).id;
}

const eventInput = (memberIds, overrides = {}) => ({
  title: 'E2E-Termin-' + randomUUID().slice(0, 8),
  departure: null,
  start: today() + 'T10:00:00',
  end: today() + 'T11:00:00',
  memberIds,
  category: 'family',
  location: null, lat: null, lon: null,
  transportMode: 'driving-car', travelTime: null,
  description: null,
  private: false,
  recurrence: null,
  ...overrides,
});

// Termin merken, damit ihn das Aufräumen löscht (Serien ganz, ab dem ersten Termin)
function trackEvent(world, event) {
  world.cleanupPaths.push(`/api/events/${event.id}${event.series ? '?scope=following' : ''}`);
  return event;
}

async function createEvent(world, context, input) {
  const response = await request(context, 'POST', '/api/events', input);
  if (response.status() === 201) trackEvent(world, await response.json());
  return response;
}

async function eventsTitled(world, title) {
  const response = await world.api('GET', '/api/events');
  expect(response.ok()).toBeTruthy();
  return (await response.json()).filter(e => e.title === title).sort((a, b) => a.start.localeCompare(b.start));
}

const dialog = world => world.page.getByRole('dialog');

async function openNewEvent(world) {
  await world.page.getByRole('button', { name: 'Termin hinzufügen', exact: true }).click();
  await expect(dialog(world).getByRole('heading', { name: 'Neuer Termin', exact: true })).toBeVisible();
}

async function fillTimedEvent(world, from, to) {
  world.eventTitle = 'E2E-Termin-' + randomUUID().slice(0, 8);
  await dialog(world).getByLabel('Titel', { exact: true }).fill(world.eventTitle);
  await dialog(world).getByLabel('Beginn', { exact: true }).fill(`${today()}T${from}`);
  await dialog(world).getByLabel('Ende', { exact: true }).fill(`${today()}T${to}`);
}

async function saveForm(world) {
  await dialog(world).getByRole('button', { name: 'Speichern', exact: true }).click();
  await expect(dialog(world)).toHaveCount(0);
  const [event] = await eventsTitled(world, world.eventTitle);
  expect(event, 'Termin muss gespeichert sein').toBeTruthy();
  world.event = trackEvent(world, event);
}

// --- Formular -------------------------------------------------------------------------------------------------

When('ich über das Formular einen Testtermin für heute von {string} bis {string} anlege', async function (from, to) {
  await openNewEvent(this);
  await fillTimedEvent(this, from, to);
  await saveForm(this);
});

When('ich über das Formular einen ganztägigen Testtermin über {int} Tage ab heute anlege', async function (days) {
  await openNewEvent(this);
  this.eventTitle = 'E2E-Ganztags-' + randomUUID().slice(0, 8);
  this.allDayDays = days;
  await dialog(this).getByLabel('Titel', { exact: true }).fill(this.eventTitle);
  await dialog(this).getByRole('checkbox', { name: 'Ganztägig', exact: true }).check();
  await dialog(this).getByLabel('Erster Tag', { exact: true }).fill(today());
  await dialog(this).getByLabel('Letzter Tag', { exact: true }).fill(addDays(today(), days - 1));
  await saveForm(this);
});

Then('sehe ich den Testtermin im Kalender', async function () {
  // Tagesansicht von heute: zeigt alle Termine des Tages (die Monatsansicht nur die ersten drei)
  await this.page.getByRole('button', { name: 'Tag', exact: true }).click();
  await expect(this.page.getByText(this.eventTitle).first()).toBeVisible();
});

Then('liefert die API den Testtermin für heute von {string} bis {string}', async function (from, to) {
  const [event] = await eventsTitled(this, this.eventTitle);
  expect(event.start.slice(0, 16)).toBe(`${today()}T${from}`);
  expect(event.end.slice(0, 16)).toBe(`${today()}T${to}`);
});

Then('speichert die API den Testtermin von 00:00 des ersten Tages bis 00:00 nach dem letzten Tag', async function () {
  const [event] = await eventsTitled(this, this.eventTitle);
  expect(event.start.slice(0, 16)).toBe(`${today()}T00:00`);
  expect(event.end.slice(0, 16)).toBe(`${addDays(today(), this.allDayDays)}T00:00`);
});

// --- Vorlagen im Formular -------------------------------------------------------------------------------------

When('ich im Formular einen Testtermin am Ort {string} von {string} bis {string} ausfülle', async function (place, from, to) {
  await openNewEvent(this);
  await fillTimedEvent(this, from, to);
  await dialog(this).getByPlaceholder('Ort eingeben ...').fill(place);
  await dialog(this).getByRole('button', { name: 'Übernehmen', exact: true }).click();
  await expect(dialog(this).getByText('📍 ' + place)).toBeVisible();
});

When('ich den Testtermin als Vorlage speichere', async function () {
  this.templateName = 'E2E-Vorlage-' + randomUUID().slice(0, 8);
  await dialog(this).getByRole('button', { name: '☆ Als Vorlage speichern', exact: true }).click();
  await dialog(this).getByLabel('Name der Vorlage', { exact: true }).fill(this.templateName);
  await dialog(this).getByRole('button', { name: 'Speichern', exact: true }).first().click();
});

Then('bestätigt das Formular die gespeicherte Vorlage', async function () {
  await expect(dialog(this).getByRole('status')).toContainText(`Vorlage „${this.templateName}“ gespeichert`);
  const templates = await (await this.api('GET', '/api/event-templates')).json();
  const template = templates.find(t => t.name === this.templateName);
  expect(template, 'Vorlage muss gespeichert sein').toBeTruthy();
  this.cleanupPaths.push('/api/event-templates/' + template.id);
});

When('ich das Formular schließe und einen neuen Termin mit der Vorlage beginne', async function () {
  await dialog(this).getByRole('button', { name: 'Abbrechen', exact: true }).click();
  await expect(dialog(this)).toHaveCount(0);
  await openNewEvent(this);
  await dialog(this).getByLabel('Vorlage (optional)', { exact: true }).selectOption({ label: this.templateName });
});

Then('übernimmt das Formular Titel, Uhrzeit {string} bis {string} und Ort {string}', async function (from, to, place) {
  await expect(dialog(this).getByLabel('Titel', { exact: true })).toHaveValue(this.eventTitle);
  await expect(dialog(this).getByLabel('Beginn', { exact: true })).toHaveValue(new RegExp(`T${from}$`));
  await expect(dialog(this).getByLabel('Ende', { exact: true })).toHaveValue(new RegExp(`T${to}$`));
  await expect(dialog(this).getByText('📍 ' + place)).toBeVisible();
});

// --- API: Prüfungen und Serien --------------------------------------------------------------------------------

When('ich per API einen Testtermin anlege, der vor seinem Beginn endet', async function () {
  this.response = await createEvent(this, this.context,
    eventInput([await myId(this)], { start: today() + 'T12:00:00', end: today() + 'T11:00:00' }));
});

When('ich per API eine wöchentliche Testserie mit {int} Terminen anlege', async function (count) {
  const input = eventInput([await myId(this)], { recurrence: { frequency: 'weekly', interval: 1, count } });
  const response = await createEvent(this, this.context, input);
  expect(response.status(), await response.text()).toBe(201);
  this.eventTitle = input.title;
});

Then('gibt es {int} Termine der Testserie im Abstand von {int} Tagen', async function (count, gap) {
  const events = await eventsTitled(this, this.eventTitle);
  expect(events).toHaveLength(count);
  expect(new Set(events.map(e => e.series.seriesId)).size).toBe(1);
  events.forEach((e, i) => expect(e.start.slice(0, 10)).toBe(addDays(today(), i * gap)));
});

When('ich die Testserie ab dem ersten Termin lösche', async function () {
  const [first] = await eventsTitled(this, this.eventTitle);
  expect((await this.api('DELETE', `/api/events/${first.id}?scope=following`)).status()).toBe(204);
});

When('ich die Testserie ab dem zweiten Termin lösche', async function () {
  const [, second] = await eventsTitled(this, this.eventTitle);
  expect((await this.api('DELETE', `/api/events/${second.id}?scope=following`)).status()).toBe(204);
});

Then('gibt es keinen Termin der Testserie mehr', async function () {
  expect(await eventsTitled(this, this.eventTitle)).toHaveLength(0);
});

Then('gibt es nur noch den ersten Termin der Testserie', async function () {
  const events = await eventsTitled(this, this.eventTitle);
  expect(events).toHaveLength(1);
  expect(events[0].start.slice(0, 10)).toBe(today());
});

When('ich nur den zweiten Termin der Testserie umbenenne', async function () {
  const [, second] = await eventsTitled(this, this.eventTitle);
  this.renamed = this.eventTitle + '-einzeln';
  const response = await this.api('PUT', `/api/events/${second.id}?scope=single`, { ...second, title: this.renamed });
  expect(response.status(), await response.text()).toBe(200);
});

Then('heißt nur der zweite Termin anders und ist als einzeln geändert markiert', async function () {
  const rest = await eventsTitled(this, this.eventTitle);
  expect(rest.map(e => e.start.slice(0, 10))).toEqual([today(), addDays(today(), 14)]);
  rest.forEach(e => expect(e.series.changed).toBe(false));
  const [changed] = await eventsTitled(this, this.renamed);
  expect(changed.series.changed).toBe(true);
  expect(changed.series.seriesId).toBe(rest[0].series.seriesId);
});

// --- API: Rechte ----------------------------------------------------------------------------------------------

When('das Testmitglied einen Termin nur für sich anlegt', async function () {
  this.response = await createEvent(this, this.actor, eventInput([this.member.id]));
});

When('das Testmitglied einen Termin mit dem Administrator anlegt', async function () {
  this.response = await createEvent(this, this.actor, eventInput([this.member.id, await myId(this)]));
});

Then('ist der Termin des Testmitglieds {string}', async function (status) {
  expect(this.response.status(), await this.response.text()).toBe(201);
  this.event = await this.response.json();
  expect(this.event.status).toBe(status);
});

Then('wird der Termin des Testmitglieds mit Status {int} abgewiesen', async function (status) {
  expect(this.response.status(), await this.response.text()).toBe(status);
});

Then('sieht der Administrator den Vorschlag und kann ihn freigeben', async function () {
  const all = await (await this.api('GET', '/api/events')).json();
  expect(all.some(e => e.id === this.event.id && e.status === 'proposed')).toBeTruthy();
  const approved = await this.api('POST', `/api/events/${this.event.id}/approve`);
  expect(approved.status(), await approved.text()).toBe(200);
  expect((await approved.json()).status).toBe('approved');
});

When('ich einen privaten Testtermin nur für mich anlege', async function () {
  const response = await createEvent(this, this.context, eventInput([await myId(this)], { private: true }));
  expect(response.status(), await response.text()).toBe(201);
  this.event = await response.json();
});

Then('sieht das Testmitglied den privaten Testtermin weder in der Liste noch direkt', async function () {
  const list = await request(this.actor, 'GET', '/api/events');
  if (list.ok()) expect((await list.json()).some(e => e.id === this.event.id)).toBeFalsy();
  else expect(list.status()).toBe(403);
  expect([403, 404]).toContain((await request(this.actor, 'GET', `/api/events/${this.event.id}`)).status());
  // Die Antwort darf den Titel auch nicht anderweitig verraten
  expect(await list.text()).not.toContain(this.event.title);
});

When('ich per API eine private Testvorlage anlege', async function () {
  const response = await this.api('POST', '/api/event-templates', {
    name: 'E2E-Privat-' + randomUUID().slice(0, 8), title: 'Geheim', startTime: '09:00', durationMinutes: 60,
    memberIds: [await myId(this)], category: 'family', location: null, lat: null, lon: null,
    transportMode: 'driving-car', travelTime: null, description: null, private: true,
  });
  expect(response.status(), await response.text()).toBe(201);
  this.template = await response.json();
  this.cleanupPaths.push('/api/event-templates/' + this.template.id);
});

Then('fehlt die private Testvorlage in der Vorlagenliste des Testmitglieds', async function () {
  const response = await request(this.actor, 'GET', '/api/event-templates');
  expect(response.status()).toBe(200);
  expect((await response.json()).some(t => t.id === this.template.id)).toBeFalsy();
});

Then('kann das Testmitglied die private Testvorlage weder ändern noch löschen', async function () {
  const changed = await request(this.actor, 'PUT', '/api/event-templates/' + this.template.id, { ...this.template, title: 'Übernommen' });
  expect([403, 404]).toContain(changed.status());
  const deleted = await request(this.actor, 'DELETE', '/api/event-templates/' + this.template.id);
  expect([403, 404]).toContain(deleted.status());
  const mine = await (await this.api('GET', '/api/event-templates')).json();
  expect(mine.find(t => t.id === this.template.id)?.title).toBe('Geheim');
});

Then('verweigert die API dem Testmitglied die Vorlagen mit Status {int}', async function (status) {
  expect((await request(this.actor, 'GET', '/api/event-templates')).status()).toBe(status);
});
