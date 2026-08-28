import { test, expect } from './support/fixtures';

test.describe('Autenticação', () => {
  test('visitante entra direto na home, sem tela de senha', async ({ page, mock }) => {
    mock.user(null); // /auth/refresh falha -> unauthenticated
    await page.goto('/');
    await expect(page).toHaveURL(/\/$/);
    // A home nao vende cadastro: mostra o conteudo. Entrar fica no topo; o resto pede quando precisa.
    await expect(page.getByRole('heading', { name: 'HELBOR TRILOGY HOME' })).toBeVisible();
    await expect(page.getByRole('button', { name: /entrar/i }).first()).toBeVisible();
    await expect(page.getByText('Entrar no sistema')).toHaveCount(0);
    await expect(page.getByRole('link', { name: /criar minha conta/i })).toHaveCount(0);
  });

  test('visitante lê o conteúdo aberto sem conta', async ({ page, mock }) => {
    mock.user(null);
    await page.goto('/indicacoes');
    await expect(page).toHaveURL(/\/indicacoes$/);
    await expect(page.getByRole('button', { name: /entrar/i }).first()).toBeVisible();
  });

  test('mural exige conta: o login vem em popup, sem tirar o visitante do portal', async ({
    page,
    mock,
  }) => {
    mock.user(null);
    await page.goto('/avisos');
    // Nao ha desvio para a tela de senha: volta para a home com o popup de entrada por cima.
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByText('Entrar no sistema')).toHaveCount(0);
  });

  test('área da unidade também pede a conta em popup', async ({ page, mock }) => {
    mock.user(null);
    await page.goto('/minha-unidade/moradores');
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole('dialog')).toBeVisible();
  });

  test('o "Entrar" do topo abre o popup, sem sair da página', async ({ page, mock }) => {
    mock.user(null);
    await page.goto('/indicacoes');
    await page
      .getByRole('button', { name: /entrar/i })
      .first()
      .click();

    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page).toHaveURL(/\/indicacoes$/);
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
