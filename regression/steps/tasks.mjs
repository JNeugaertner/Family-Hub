import { When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { request } from './extended.mjs';
import { today, addDays } from '../support/days.mjs';

// --- Hilfen ---------------------------------------------------------------------------------------------------

async function myId(world) {
  return (await (await world.api('GET', '/api/auth/me')).json()).id;
}

const taskInput = (assigneeId, overrides = {}) => ({
  title: 'E2E-Aufgabe-' + randomUUID().slice(0, 8),
  description: null,
  assigneeId,
  dueDate: today(),
  priority: 'medium',
  category: 'chores',
  points: 0,
  bonus: false,
  repeatable: false,
  repeat: null,
  ...overrides,
});

async function createTask(world, context, input) {
  const response = await request(context, 'POST', '/api/tasks', input);
  if (response.status() === 201) world.cleanupPaths.push('/api/tasks/' + (await response.json()).id);
  return response;
}

async function createOwnTask(world, input) {
  const response = await createTask(world, world.context, input);
  expect(response.status(), await response.text()).toBe(201);
  world.task = await response.json();
  return world.task;
}

async function allTasks(world) {
  const response = await world.api('GET', '/api/tasks');
  expect(response.ok()).toBeTruthy();
  return response.json();
}

// Nächste Runde einer wiederkehrenden Aufgabe; wird zum Aufräumen gemerkt
async function nextRound(world) {
  const next = (await allTasks(world)).find(t => t.previousRound?.taskId === world.task.id);
  if (next) world.cleanupPaths.push('/api/tasks/' + next.id);
  return next;
}

async function balance(world, memberId) {
  const balances = await (await world.api('GET', '/api/points')).json();
  return balances.find(b => b.memberId === memberId)?.points ?? 0;
}

// --- Formular -------------------------------------------------------------------------------------------------

const card = world => world.page.locator('div')
  .filter({ has: world.page.getByRole('heading', { name: world.taskTitle, exact: true }) })
  .filter({ has: world.page.getByRole('button', { name: 'Erledigt', exact: true }) })
  .last();

When('ich über das Formular eine Testaufgabe anlege', async function () {
  this.taskTitle = 'E2E-Aufgabe-' + randomUUID().slice(0, 8);
  await this.page.getByRole('button', { name: 'Neue Aufgabe', exact: true }).click();
  const dialog = this.page.getByRole('dialog');
  await expect(dialog.getByRole('heading', { name: 'Neue Aufgabe', exact: true })).toBeVisible();
  await dialog.getByLabel('Titel', { exact: true }).fill(this.taskTitle);
  await dialog.getByRole('button', { name: 'Speichern', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  this.task = (await allTasks(this)).find(t => t.title === this.taskTitle);
  expect(this.task, 'Aufgabe muss gespeichert sein').toBeTruthy();
  this.cleanupPaths.push('/api/tasks/' + this.task.id);
});

Then('sehe ich die Testaufgabe', async function () {
  await expect(this.page.getByRole('heading', { name: this.taskTitle, exact: true })).toBeVisible();
});

When('ich die Testaufgabe als erledigt markiere', async function () {
  await card(this).hover();
  await card(this).getByRole('button', { name: 'Erledigt', exact: true }).click();
  await expect(card(this).getByRole('button', { name: 'Erledigt', exact: true })).toHaveAttribute('aria-pressed', 'true');
});

Then('hat die Testaufgabe laut API den Status {string}', async function (status) {
  const task = (await allTasks(this)).find(t => t.id === this.task.id);
  expect(task.status).toBe(status);
});

// --- Wiederkehrende Aufgaben ----------------------------------------------------------------------------------

When('ich mir per API eine Testaufgabe gebe, die sich {int} Woche nach Erledigung wiederholt', async function (weeks) {
  await createOwnTask(this, taskInput(await myId(this), { repeat: { interval: weeks, unit: 'weeks', openToAll: false } }));
});

When('ich per API eine Testaufgabe für alle anlege, die sich {int} Tage nach Erledigung wiederholt', async function (days) {
  await createOwnTask(this, taskInput(null, { repeat: { interval: days, unit: 'days', openToAll: true } }));
});

When('ich die Testaufgabe per API abhake', async function () {
  const response = await this.api('PATCH', `/api/tasks/${this.task.id}/status`, { status: 'done' });
  expect(response.status(), await response.text()).toBe(200);
});

Then('gibt es eine neue Runde für mich, fällig in {int} Tagen, mit Verweis auf die erledigte', async function (days) {
  const next = await nextRound(this);
  expect(next, 'neue Runde muss entstanden sein').toBeTruthy();
  expect(next.status).toBe('todo');
  expect(next.assigneeId).toBe(await myId(this));
  expect(next.dueDate).toBe(addDays(today(), days));
  expect(next.previousRound.doneOn).toBe(today());
});

When('ich den Haken der Testaufgabe zurücknehme', async function () {
  const response = await this.api('PATCH', `/api/tasks/${this.task.id}/status`, { status: 'todo' });
  expect(response.status(), await response.text()).toBe(200);
});

Then('gibt es keine neue Runde mehr', async function () {
  expect(await nextRound(this)).toBeUndefined();
});

When('das Testmitglied die offene Runde abhakt', async function () {
  const response = await request(this.actor, 'PATCH', `/api/tasks/${this.task.id}/status`, { status: 'done' });
  expect(response.status(), await response.text()).toBe(200);
});

Then('gehört die erledigte Runde dem Testmitglied', async function () {
  const task = (await allTasks(this)).find(t => t.id === this.task.id);
  expect(task.status).toBe('done');
  expect(task.assigneeId).toBe(this.member.id);
});

Then('die nächste Runde ist wieder für alle offen und in {int} Tagen fällig', async function (days) {
  const next = await nextRound(this);
  expect(next, 'neue Runde muss entstanden sein').toBeTruthy();
  expect(next.assigneeId).toBeNull();
  expect(next.dueDate).toBe(addDays(today(), days));
  expect(next.previousRound.doneBy).toBe(this.member.id);
});

// --- Punkte ---------------------------------------------------------------------------------------------------

When('ich dem Testmitglied eine Testaufgabe mit {int} Punkten gebe', async function (points) {
  await createOwnTask(this, taskInput(this.member.id, { points }));
});

When('das Testmitglied seine Testaufgabe abhakt', async function () {
  const response = await request(this.actor, 'PATCH', `/api/tasks/${this.task.id}/status`, { status: 'done' });
  expect(response.status(), await response.text()).toBe(200);
});

Then('wartet die Testaufgabe auf Bestätigung und das Testmitglied hat {int} Punkte', async function (points) {
  const task = (await allTasks(this)).find(t => t.id === this.task.id);
  expect(task.status).toBe('done');
  expect(await balance(this, this.member.id)).toBe(points);
});

Then('das Testmitglied darf seine Testaufgabe nicht selbst bestätigen', async function () {
  expect((await request(this.actor, 'POST', `/api/tasks/${this.task.id}/confirm`)).status()).toBe(403);
  const status = await request(this.actor, 'PATCH', `/api/tasks/${this.task.id}/status`, { status: 'confirmed' });
  expect(status.status()).toBe(400);
});

When('ich die Testaufgabe bestätige', async function () {
  const response = await this.api('POST', `/api/tasks/${this.task.id}/confirm`);
  expect(response.status(), await response.text()).toBe(200);
  expect((await response.json()).status).toBe('confirmed');
});

async function history(world, memberId) {
  const response = await world.api('GET', '/api/points/history?memberId=' + memberId);
  expect(response.ok()).toBeTruthy();
  return response.json();
}

Then('sind dem Testmitglied {int} Punkte für die Testaufgabe gutgeschrieben', async function (points) {
  const entries = (await history(this, this.member.id)).filter(e => e.taskId === this.task.id);
  expect(entries.map(e => e.amount)).toEqual([points]);
});

// Erfolge vergeben Bonuspunkte (AchievementService), z. B. "Erste Aufgabe" nach der ersten bestätigten Aufgabe
Then('zusätzlich {int} Punkte für den Erfolg {string}, zusammen {int} Punkte', async function (bonus, name, total) {
  const entries = (await history(this, this.member.id)).filter(e => e.reason === 'Erfolg: ' + name);
  expect(entries.map(e => e.amount)).toEqual([bonus]);
  expect(await balance(this, this.member.id)).toBe(total);
});

// --- Rechte ---------------------------------------------------------------------------------------------------

When('das Testmitglied eine Aufgabe für sich anlegt', async function () {
  this.response = await createTask(this, this.actor, taskInput(this.member.id));
});

When('das Testmitglied eine Aufgabe für den Administrator anlegt', async function () {
  this.response = await createTask(this, this.actor, taskInput(await myId(this)));
});

Then('antwortet die API mit Status {int}', async function (status) {
  expect(this.response.status(), await this.response.text()).toBe(status);
});

When('ich eine Bonus-Testaufgabe mit {int} Punkten anlege', async function (points) {
  await createOwnTask(this, taskInput(null, { bonus: true, points, dueDate: null }));
});

When('beide Kinder die Bonus-Testaufgabe gleichzeitig übernehmen wollen', async function () {
  this.claims = await Promise.all([this.firstSession, this.secondSession]
    .map(session => request(session, 'POST', `/api/tasks/${this.task.id}/claim`)));
});

Then('bekommt sie genau eines der beiden', async function () {
  const statuses = this.claims.map(r => r.status());
  expect(statuses.filter(s => s === 200), `Antworten: ${statuses}`).toHaveLength(1);
  // Wer zu spät kommt: 409 (gerade vergeben) oder 404 (für Kinder nicht mehr sichtbar)
  expect([404, 409]).toContain(statuses.find(s => s !== 200));
  const winner = this.claims[statuses.indexOf(200)];
  this.winnerId = (await winner.json()).assigneeId;
  this.loserSession = statuses[0] === 200 ? this.secondSession : this.firstSession;
  expect([this.firstChild.id, this.secondChild.id]).toContain(this.winnerId);
  const task = (await allTasks(this)).find(t => t.id === this.task.id);
  expect(task.assigneeId).toBe(this.winnerId);
  expect(task.status).toBe('inprogress');
});

Then('das andere Kind sieht sie danach nicht mehr', async function () {
  const visible = await (await request(this.loserSession, 'GET', '/api/tasks')).json();
  expect(visible.some(t => t.id === this.task.id)).toBeFalsy();
});
