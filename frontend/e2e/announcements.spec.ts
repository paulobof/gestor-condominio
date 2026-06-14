import { test, expect } from './support/fixtures';

const PAGE = {
  content: [
    {
      id: 'a-1',
      title: 'Manutenção da piscina',
      body: 'A piscina ficará fechada na segunda.',
      position: 0,
      publishedAt: '2026-06-10T12:00:00Z',
      authorUserId: 'user-1',
      updatedAt: '2026-06-10T12:00:00Z',
      importance: 'HIGH',
    },
    {
      id: 'a-2',
      title: 'Reunião de condôminos',
      body: 'Assembleia no salão de festas.',
      position: 1,
      publishedAt: '2026-06-09T12:00:00Z',
      authorUserId: 'user-1',
      updatedAt: '2026-06-09T12:00:00Z',
      importance: 'MEDIUM',
    },
    {
      id: 'a-3',
      title: 'Novo horário da portaria',
      body: 'Portaria 24h a partir de julho.',
      position: 2,
      publishedAt: '2026-06-08T12:00:00Z',
      authorUserId: 'user-1',
      updatedAt: '2026-06-08T12:00:00Z',
      importance: 'LOW',
    },
  ],
  totalElements: 3,
  totalPages: 1,
  number: 0,
};

test.describe('Avisos — níveis de importância', () => {
  test('lista exibe os badges dos 3 níveis', async ({ page, mock }) => {
    mock.get('/announcements', PAGE);
    await page.goto('/avisos');

    await expect(page.getByRole('heading', { name: /avisos/i })).toBeVisible();
    await expect(page.getByText('Manutenção da piscina')).toBeVisible();

    // badges de importância (IMPORTANCE_LABEL)
    await expect(page.getByText('Urgente')).toBeVisible();
    await expect(page.getByText('Importante')).toBeVisible();
    await expect(page.getByText('Informativo')).toBeVisible();
  });
});
