import { World, setWorldConstructor, setDefaultTimeout } from '@cucumber/cucumber';
export const baseURL = process.env.BASE_URL || 'http://127.0.0.1:8081';
export const username = process.env.E2E_USER || 'regressionadmin';
export const password = process.env.E2E_PASSWORD || 'FamilyHub-E2E-2026!';
export class FamilyWorld extends World {
  constructor(options) { super(options); this.contexts = []; this.itemNames = []; this.createdItemIds = []; this.memberIds = []; }
  async api(method, path, data, csrf = true) {
    const token = (await this.context.cookies()).find(c => c.name === 'XSRF-TOKEN');
    return this.context.request.fetch(baseURL + path, {
      method, data, headers: csrf && token ? { 'X-XSRF-TOKEN': decodeURIComponent(token.value) } : {}
    });
  }
}
setWorldConstructor(FamilyWorld);
setDefaultTimeout(30000);
