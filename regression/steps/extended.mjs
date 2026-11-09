import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { baseURL } from '../support/world.mjs';
const secret = 'E2E-password-2026!';
const permission = (module, action) => ({ module, action, scope: 'familie' });
const memberInput = (role = 'gast') => ({ name: 'E2E Member', username: 'e2e-' + randomUUID().slice(0, 12), password: secret, color: '#2563EB', role, birthDate: null, roleFixed: true });
const itemInput = () => ({ name: 'E2E-' + randomUUID(), quantity: '1', category: 'vorrat', urgent: false });
async function request(context, method, path, data, headers = {}) {
  const token = (await context.cookies()).find(c => c.name === 'XSRF-TOKEN');
  return context.request.fetch(baseURL + path, { method, data, headers: { ...(token ? { 'X-XSRF-TOKEN': decodeURIComponent(token.value) } : {}), ...headers } });
}
async function track(world, response, type) {
  if (response.status() === 201) {
    const value = await response.json();
    (type === 'member' ? world.memberIds : world.createdItemIds).push(value.id);
  }
  return response;
}
async function createMember(world, input = memberInput()) {
  const response = await track(world, await world.api('POST', '/api/members', input), 'member');
  expect(response.status(), await response.text()).toBe(201);
  return { ...(await response.json()), input };
}
async function session(world, member, pass = member.input.password) {
  const context = await world.context.browser().newContext({ viewport: { width: 1440, height: 900 } });
  world.contexts.push(context);
  await context.request.get(baseURL + '/api/auth/status');
  const token = (await context.cookies()).find(c => c.name === 'XSRF-TOKEN');
  const response = await context.request.post(baseURL + '/api/auth/login', { form: { username: member.input.username, password: pass }, headers: { 'X-XSRF-TOKEN': decodeURIComponent(token.value) } });
  expect(response.status()).toBe(200);
  await context.request.get(baseURL + '/api/auth/me');
  return context;
}
async function addItem(world, context, input = itemInput()) {
  const response = await track(world, await request(context, 'POST', '/api/shopping', input), 'item');
  expect(response.status(), await response.text()).toBe(201);
  return response.json();
}
When('ich einen Artikel mit Feld {string} und Länge {int} per API anlege', async function (field, length) {
  this.response = await track(this, await this.api('POST', '/api/shopping', { ...itemInput(), [field]: 'x'.repeat(length) }), 'item');
});
When('ich ein Mitglied mit Feld {string} und Länge {int} per API anlege', async function (field, length) {
  const value = field === 'username' ? ('e' + randomUUID().replaceAll('-', '')).slice(0, length) : 'x'.repeat(length);
  this.response = await track(this, await this.api('POST', '/api/members', { ...memberInput(), [field]: value }), 'member');
});
When('ich ein Mitglied mit ungültigem Feld {string} und Wert {string} anlege', async function (field, value) {
  this.response = await track(this, await this.api('POST', '/api/members', { ...memberInput(), [field]: value }), 'member');
});
Then('ist der Validierungsstatus {int} für Feld {string}', async function (status, field) {
  expect(this.response.status(), await this.response.text()).toBe(status);
  if (status === 400) expect((await this.response.json()).errors).toHaveProperty(field);
});
Then('akzeptiert die API genau einen weiteren Administrator und verweigert den dritten', async function () {
  const all = await (await this.api('GET', '/api/members')).json();
  expect(all.filter(m => m.role === 'administrator')).toHaveLength(1);
  await createMember(this, memberInput('administrator'));
  const response = await track(this, await this.api('POST', '/api/members', memberInput('administrator')), 'member');
  expect(response.status()).toBe(400); expect((await response.json()).errors).toHaveProperty('role');
});
Then('akzeptiert die API fünf Kinder und verweigert das sechste', async function () {
  const all = await (await this.api('GET', '/api/members')).json();
  expect(all.filter(m => ['kind', 'jugendlicher'].includes(m.role))).toHaveLength(0);
  for (let i = 0; i < 5; i++) await createMember(this, memberInput(i % 2 ? 'jugendlicher' : 'kind'));
  const response = await track(this, await this.api('POST', '/api/members', memberInput('kind')), 'member');
  expect(response.status()).toBe(400); expect((await response.json()).errors).toHaveProperty('role');
});
When('ich ein Kind im Alter von {int} Jahren mit Rollenfixierung {string} anlege', async function (age, fixed) {
  const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Berlin' }).format(new Date());
  const birthDate = String(Number(today.slice(0, 4)) - age) + today.slice(4);
  this.member = await createMember(this, { ...memberInput('kind'), birthDate, roleFixed: fixed === 'ja' });
});
Then('hat das Mitglied die effektive Rolle {string}', function (role) { expect(this.member.effectiveRole).toBe(role); });
Given('ein Testmitglied mit Rolle {string} existiert', async function (role) {
  this.member = await createMember(this, memberInput(role));
  this.actor = await session(this, this.member);
});
When('ich die XSS-Variante {string} als Artikel speichere', async function (variant) {
  const payloads = { img: '<img src=x onerror="window.__e2eXss=1">', script: '<script>window.__e2eXss=1</script>', svg: '<svg onload="window.__e2eXss=1"></svg>' };
  this.payload = payloads[variant]; this.xssItem = await addItem(this, this.context, { ...itemInput(), name: this.payload });
});
Then('wird die Payload auch nach Neuladen als Text ohne Skriptausführung angezeigt', async function () {
  for (let i = 0; i < 2; i++) {
    if (i) await this.page.reload();
    await this.page.getByRole('complementary').getByRole('navigation').getByRole('button', { name: 'Einkauf', exact: true }).click();
    const row = this.page.locator('[data-item]').filter({ has: this.page.getByText(this.payload, { exact: true }) });
    await expect(row).toBeVisible(); await expect(row.locator('img,script,svg[onload]')).toHaveCount(0);
    expect(await this.page.evaluate(() => window.__e2eXss)).toBeUndefined();
  }
});
When('eine neue Browsersitzung die Login-Injection {string} sendet', async function (variant) {
  this.injectionContext = await this.context.browser().newContext(); this.contexts.push(this.injectionContext);
  await this.injectionContext.request.get(baseURL + '/api/auth/status');
  const token = (await this.injectionContext.cookies()).find(c => c.name === 'XSRF-TOKEN');
  const input = { sql: "' OR '1'='1", nosql: '{"$ne":null}', operator: 'username[$ne]=' }[variant];
  this.response = await this.injectionContext.request.post(baseURL + '/api/auth/login', { form: { username: input, password: 'wrong' }, headers: { 'X-XSRF-TOKEN': decodeURIComponent(token.value) } });
});
Then('bleibt die neue Browsersitzung unangemeldet', async function () {
  expect(this.response.status()).toBe(401);
  expect((await this.injectionContext.request.get(baseURL + '/api/auth/me')).status()).toBe(401);
});
When('ich ein Operatorobjekt im Kontofeld {string} sende', async function (field) {
  this.response = await track(this, await this.api('POST', '/api/members', { ...memberInput(), [field]: { $ne: null } }), 'member');
});
Then('wird die Anfrage ohne internen Fehler abgewiesen', async function () {
  expect(this.response.status()).toBe(400);
  expect(await this.response.text()).not.toMatch(/passwordHash|java\.lang\.|org\.springframework|stackTrace/);
});
When('das Testmitglied einen Artikel mit gefälschtem Status und Ersteller anlegt', async function () {
  const me = await (await this.api('GET', '/api/auth/me')).json();
  this.actorItem = await addItem(this, this.actor, { ...itemInput(), status: 'approved', checked: true, createdBy: me.id, checkedBy: me.id });
});
Then('bleibt der Artikel ein ungeprüfter Vorschlag des Testmitglieds', function () {
  expect(this.actorItem).toMatchObject({ status: 'proposed', checked: false, createdBy: this.member.id, checkedBy: null });
});
When('ich mit einem gefälschten CSRF-Token einen Artikel anlege', async function () {
  this.response = await track(this, await request(this.context, 'POST', '/api/shopping', itemInput(), { 'X-XSRF-TOKEN': 'forged-token' }), 'item');
});
When('ich einen MongoDB-Operator als reinen Artikeltext speichere', async function () {
  this.previousItems = await (await this.api('GET', '/api/shopping')).json();
  this.operatorItem = await addItem(this, this.context, { ...itemInput(), name: '{"$where":"return true","$ne":null}' });
});
Then('bleibt der Operator reiner Text und bestehende Artikel bleiben unverändert', async function () {
  const after = await (await this.api('GET', '/api/shopping')).json();
  expect(after.find(i => i.id === this.operatorItem.id).name).toBe(this.operatorItem.name);
  for (const item of this.previousItems) expect(after.find(i => i.id === item.id)).toEqual(item);
  expect(after).toHaveLength(this.previousItems.length + 1);
});
Then('erscheint das Testmitglied in der Familienoberfläche', async function () {
  await this.page.getByRole('complementary').getByRole('navigation').getByRole('button', { name: 'Familie', exact: true }).click();
  await expect(this.page.getByText(this.member.name, { exact: true }).first()).toBeVisible();
});
When('ich Namen und Passwort des Testmitglieds ändere', async function () {
  const input = { ...this.member.input, name: 'E2E Updated Member', password: 'Changed-E2E-2026!' };
  const response = await this.api('PUT', '/api/members/' + this.member.id, input);
  expect(response.status()).toBe(200); expect((await response.json()).name).toBe(input.name);
  this.changedPassword = input.password;
});
Then('funktioniert das neue Passwort und das bisherige wird abgewiesen', async function () {
  this.updatedSession = await session(this, this.member, this.changedPassword);
  const other = await this.context.browser().newContext(); this.contexts.push(other);
  await other.request.get(baseURL + '/api/auth/status');
  const token = (await other.cookies()).find(c => c.name === 'XSRF-TOKEN');
  expect((await other.request.post(baseURL + '/api/auth/login', { form: { username: this.member.username, password: secret }, headers: { 'X-XSRF-TOKEN': decodeURIComponent(token.value) } })).status()).toBe(401);
});
When('ich das Testmitglied lösche', async function () { expect((await this.api('DELETE', '/api/members/' + this.member.id)).status()).toBe(204); });
Then('ist das Konto nicht mehr lesbar und die bestehende Sitzung hat keinen Zugriff', async function () {
  expect((await this.api('GET', '/api/members/' + this.member.id)).status()).toBe(404);
  expect((await request(this.updatedSession, 'GET', '/api/auth/me')).status()).toBe(401);
});
When('ich dessen Benutzername erneut vergebe', async function () { this.response = await track(this, await this.api('POST', '/api/members', { ...memberInput(), username: this.member.username }), 'member'); });
Then('darf das Testmitglied keine Mitglieder anlegen ändern oder löschen', async function () {
  const target = await createMember(this);
  expect((await request(this.actor, 'POST', '/api/members', memberInput())).status()).toBe(403);
  expect((await request(this.actor, 'PUT', '/api/members/' + target.id, { ...target.input, name: 'Forbidden' })).status()).toBe(403);
  expect((await request(this.actor, 'DELETE', '/api/members/' + target.id)).status()).toBe(403);
  expect((await (await this.api('GET', '/api/members/' + target.id)).json()).name).toBe(target.name);
});
When('das Testmitglied einen Artikel anlegt', async function () { this.response = await track(this, await request(this.actor, 'POST', '/api/shopping', itemInput()), 'item'); });
Then('entspricht das Ergebnis dem Einkaufsrecht {string}', async function (result) {
  if (result === 'verboten') expect(this.response.status()).toBe(403);
  else { expect(this.response.status()).toBe(201); expect((await this.response.json()).status).toBe(result); }
});
Given('zwei Testkinder existieren', async function () {
  this.firstChild = await createMember(this, memberInput('kind')); this.secondChild = await createMember(this, memberInput('kind'));
  this.firstSession = await session(this, this.firstChild); this.secondSession = await session(this, this.secondChild);
});
Then('bleiben Vorschläge des ersten Kindes vor dem zweiten verborgen', async function () {
  const item = await addItem(this, this.firstSession);
  const visible = await (await request(this.secondSession, 'GET', '/api/shopping')).json();
  expect(visible.some(i => i.id === item.id)).toBe(false);
  expect((await request(this.secondSession, 'PUT', '/api/shopping/' + item.id, itemInput())).status()).toBe(404);
  expect((await request(this.secondSession, 'DELETE', '/api/shopping/' + item.id)).status()).toBe(404);
  expect((await (await this.api('GET', '/api/shopping')).json()).find(i => i.id === item.id)).toMatchObject({ name: item.name, status: 'proposed' });
});
Then('kann das Kind seinen Vorschlag nicht genehmigen aber der Administrator kann es', async function () {
  const item = await addItem(this, this.actor);
  expect((await request(this.actor, 'POST', '/api/shopping/' + item.id + '/approve')).status()).toBe(403);
  const response = await this.api('POST', '/api/shopping/' + item.id + '/approve');
  expect(response.status()).toBe(200); expect((await response.json()).status).toBe('approved');
});
Then('enthält die Mitgliederantwort keine fremden Zugangsdaten oder Passworthashes', async function () {
  const response = await request(this.actor, 'GET', '/api/members'); expect(response.status()).toBe(200);
  expect(await response.text()).not.toMatch(/passwordHash|\$2[aby]\$|FamilyHub-E2E-2026/);
  for (const member of await response.json()) if (member.id !== this.member.id) {
    expect(member.username).toBeNull(); expect(member.birthDate).toBeNull();
    expect(member.extraPermissions).toBeNull(); expect(member.revokedPermissions).toBeNull();
  }
});
Then('gelten zusätzliche Einkaufsrechte und ihr Entzug auch in der bestehenden Sitzung', async function () {
  const rights = [permission('einkauf', 'ansehen'), permission('einkauf', 'erstellen')];
  expect((await request(this.actor, 'GET', '/api/shopping')).status()).toBe(403);
  expect((await this.api('PUT', '/api/members/' + this.member.id, { ...this.member.input, password: null, extraPermissions: rights })).status()).toBe(200);
  expect((await request(this.actor, 'GET', '/api/shopping')).status()).toBe(200);
  await addItem(this, this.actor);
  expect((await this.api('PUT', '/api/members/' + this.member.id, { ...this.member.input, password: null, extraPermissions: rights, revokedPermissions: rights })).status()).toBe(200);
  expect((await request(this.actor, 'GET', '/api/shopping')).status()).toBe(403);
  expect((await request(this.actor, 'POST', '/api/shopping', itemInput())).status()).toBe(403);
});
Then('darf ein begrenzter Familienverwalter weder sich zum Administrator machen noch fremde Passwörter ändern', async function () {
  const input = { ...this.member.input, password: null, extraPermissions: [permission('familie', 'verwalten')] };
  expect((await this.api('PUT', '/api/members/' + this.member.id, input)).status()).toBe(200);
  expect((await request(this.actor, 'PUT', '/api/members/' + this.member.id, { ...input, role: 'administrator' })).status()).toBe(403);
  expect((await request(this.actor, 'PUT', '/api/members/' + this.member.id, { ...input, extraPermissions: [...input.extraPermissions, permission('system', 'verwalten')] })).status()).toBe(403);
  const target = await createMember(this);
  expect((await request(this.actor, 'PUT', '/api/members/' + target.id, { ...target.input, password: 'ForbiddenPassword!' })).status()).toBe(403);
  const unchanged = await (await this.api('GET', '/api/members/' + this.member.id)).json();
  expect(unchanged.role).toBe('gast'); expect(unchanged.extraPermissions).toEqual(input.extraPermissions);
});
Then('bleiben Löschung und Herabstufung des einzigen Administrators gesperrt', async function () {
  const all = await (await this.api('GET', '/api/members')).json(); expect(all.filter(m => m.role === 'administrator')).toHaveLength(1);
  const admin = all.find(m => m.role === 'administrator');
  expect((await this.api('DELETE', '/api/members/' + admin.id)).status()).toBe(409);
  expect((await this.api('PUT', '/api/members/' + admin.id, { name: admin.name, username: admin.username, color: admin.color, birthDate: admin.birthDate, role: 'gast', password: null, roleFixed: admin.roleFixed })).status()).toBe(409);
  expect((await this.api('GET', '/api/auth/me')).status()).toBe(200);
});
