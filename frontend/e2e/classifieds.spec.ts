import { test, expect } from './support/fixtures';

const CLASSIFIED = {
  id: 'c-1',
  title: 'Bicicleta aro 29',
  description: 'Seminova, pouco uso.',
  price: 1200,
  status: 'ACTIVE',
  authorUserId: 'user-1',
  createdAt: '2026-06-10T12:00:00Z',
  photos: [],
  contactName: 'Maria Souza',
  contactPhone: '+5511988887777',
};

test.describe('Classificados — contato no detalhe', () => {
  test('exibe nome e telefone de contato com link tel:', async ({ page, mock }) => {
    mock.get('/classifieds/c-1', CLASSIFIED);
    await page.goto('/classificados/c-1');

    await expect(page.getByRole('heading', { name: 'Bicicleta aro 29' })).toBeVisible();
    await expect(page.getByText('Contato')).toBeVisible();
    await expect(page.getByText('Maria Souza')).toBeVisible();

    const tel = page.getByRole('link', { name: '+5511988887777' });
    await expect(tel).toHaveAttribute('href', 'tel:+5511988887777');
  });

  test('sem contato cadastrado não mostra a seção', async ({ page, mock }) => {
    mock.get('/classifieds/c-2', {
      ...CLASSIFIED,
      id: 'c-2',
      contactName: null,
      contactPhone: null,
    });
    await page.goto('/classificados/c-2');

    await expect(page.getByRole('heading', { name: 'Bicicleta aro 29' })).toBeVisible();
    await expect(page.getByText('Contato')).toHaveCount(0);
  });
});
