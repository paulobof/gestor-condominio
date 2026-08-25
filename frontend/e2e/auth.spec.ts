import { test, expect } from './support/fixtures';

test.describe('Autenticação', () => {
  test('visitante entra direto na home, sem tela de senha', async ({ page, mock }) => {
    mock.user(null); // /auth/refresh falha -> unauthenticated
    await page.goto('/');
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole('link', { name: /criar minha conta/i })).toBeVisible();
    await expect(page.getByText('Entrar no sistema')).toHaveCount(0);
  });

  test('visitante lê o conteúdo do condomínio sem conta', async ({ page, mock }) => {
    mock.user(null);
    await page.goto('/avisos');
    await expect(page).toHaveURL(/\/avisos$/);
    await expect(page.getByRole('link', { name: /entrar/i }).first()).toBeVisible();
  });

  test('visitante em area que exige conta cai no login', async ({ page, mock }) => {
    mock.user(null);
    await page.goto('/minha-unidade/moradores');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByText('Entrar no sistema')).toBeVisible();
  });

  test('login com credenciais válidas leva à home', async ({ page, mock }) => {
    mock.user(null); // boot deslogado → cai no /login
    mock.loginAs({}); // /auth/login responde 200 com o usuário padrão
    await page.goto('/login');

    await page.getByLabel('E-mail', { exact: true }).fill('sindico@example.com');
    await page.getByLabel('Senha', { exact: true }).fill('senha-correta');
    await page.getByRole('button', { name: 'Entrar' }).click();

    await page.waitForURL((url) => url.pathname === '/');
    await expect(page.getByText('Entrar no sistema')).toBeHidden();
  });

  test('credenciais inválidas mostram erro e permanecem no /login', async ({ page, mock }) => {
    mock.user(null);
    mock.loginAs(null); // /auth/login → 401
    await page.goto('/login');

    await page.getByLabel('E-mail', { exact: true }).fill('sindico@example.com');
    await page.getByLabel('Senha', { exact: true }).fill('senha-errada');
    await page.getByRole('button', { name: 'Entrar' }).click();

    await expect(page.getByText(/inválidos|não ativo/i)).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });
});
