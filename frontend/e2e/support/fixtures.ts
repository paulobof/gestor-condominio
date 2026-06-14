import { test as base, expect, type Page, type Route } from '@playwright/test';

/**
 * Usuário autenticado padrão dos testes. authorities cobre as permissions que
 * gateiam os menus/páginas (ver `user.authorities.includes(...)` no app), então
 * por padrão o síndico de teste enxerga tudo. Use `mock.user({...})` para variar.
 */
export const MOCK_USER = {
  id: 'user-1',
  fullName: 'Síndico de Teste',
  greetingName: 'Síndico',
  email: 'sindico@example.com',
  unitId: 'unit-1',
  isUnitMaster: true,
  roles: ['ROLE_SYNDIC'],
  authorities: [
    'ANNOUNCEMENT_MANAGE',
    'RESIDENT_MANAGE',
    'CLASSIFIED_MODERATE',
    'RECOMMENDATION_MODERATE',
    'USER_MANAGE',
    'USER_VIEW',
    'ROLE_ASSIGN',
    'FAQ_MANAGE',
    'INFO_MANAGE',
    'REGISTRATION_VIEW',
  ],
  mustChangePassword: false,
};

type Handler = {
  method: string;
  match: (pathname: string) => boolean;
  status: number;
  body: unknown;
};

function toMatcher(path: string | RegExp): (pathname: string) => boolean {
  if (path instanceof RegExp) return (p) => path.test(p);
  // string: casa pelo final do pathname (ignora /api e querystring)
  return (p) => p === path || p.endsWith(path);
}

export interface ApiMock {
  /** Registra resposta para um GET. O último registrado vence (override). */
  get(path: string | RegExp, body: unknown, status?: number): void;
  post(path: string | RegExp, body: unknown, status?: number): void;
  put(path: string | RegExp, body: unknown, status?: number): void;
  del(path: string | RegExp, body?: unknown, status?: number): void;
  /** Define a sessão de /auth/refresh (boot). null = não autenticado (401). */
  user(overrides: Partial<typeof MOCK_USER> | null): void;
  /** Define o que /auth/login retorna. null = credenciais inválidas (401). */
  loginAs(overrides: Partial<typeof MOCK_USER> | null): void;
}

async function fulfill(route: Route, status: number, body: unknown) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body ?? {}),
  });
}

/**
 * Fixture `mock`: instala um único interceptor de chamadas à API que despacha
 * por uma tabela de handlers. Determinístico, sem depender da ordem de registro.
 * Já vem com /auth/refresh autenticado; chame mock.user(null) para deslogar.
 */
export const test = base.extend<{ mock: ApiMock }>({
  mock: async ({ page }, provide) => {
    let sessionUser: typeof MOCK_USER | null = MOCK_USER;
    let loginUser: typeof MOCK_USER | null = MOCK_USER;
    const handlers: Handler[] = [];

    const add = (method: string, path: string | RegExp, body: unknown, status: number) =>
      handlers.push({ method, match: toMatcher(path), status, body });

    // Só chamadas reais ao backend (pathname inicia com /api/). NÃO casar módulos
    // do Vite dev como /src/features/auth/api/authApi.ts (contêm "/api/" no meio).
    await page.route(
      (url) => url.pathname.startsWith('/api/'),
      async (route) => {
        const req = route.request();
        const method = req.method();
        const pathname = new URL(req.url()).pathname;

        // auth bootstrap (refresh = sessão; login = credenciais)
        if (/\/auth\/refresh$/.test(pathname)) {
          if (!sessionUser) return fulfill(route, 401, { message: 'unauthorized' });
          return fulfill(route, 200, { accessToken: 'e2e-access-token', user: sessionUser });
        }
        if (/\/auth\/login$/.test(pathname)) {
          if (!loginUser) return fulfill(route, 401, { message: 'invalid credentials' });
          return fulfill(route, 200, { accessToken: 'e2e-access-token', user: loginUser });
        }
        if (/\/auth\/logout$/.test(pathname)) return fulfill(route, 204, {});

        // handlers específicos (do mais recente p/ o mais antigo → override)
        for (let i = handlers.length - 1; i >= 0; i--) {
          const h = handlers[i];
          if (h.method === method && h.match(pathname)) return fulfill(route, h.status, h.body);
        }
        // não mockado → 404 (evita cair no proxy do vite p/ :8080)
        return fulfill(route, 404, { message: `unmocked ${method} ${pathname}` });
      }
    );

    const api: ApiMock = {
      get: (p, b, s = 200) => add('GET', p, b, s),
      post: (p, b, s = 200) => add('POST', p, b, s),
      put: (p, b, s = 200) => add('PUT', p, b, s),
      del: (p, b = {}, s = 200) => add('DELETE', p, b, s),
      user: (o) => {
        sessionUser = o === null ? null : { ...MOCK_USER, ...o };
      },
      loginAs: (o) => {
        loginUser = o === null ? null : { ...MOCK_USER, ...o };
      },
    };

    await provide(api);
  },
});

export { expect };
export type { Page };
