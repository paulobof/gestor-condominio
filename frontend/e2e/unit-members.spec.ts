import { test, expect } from './support/fixtures';

const MEMBERS = [
  {
    id: 'm-1',
    fullName: 'João Silva',
    greetingName: 'João',
    email: 'joao@example.com',
    phone: '11999990000',
    status: 'ACTIVE',
  },
  {
    id: 'm-2',
    fullName: 'Ana Costa',
    greetingName: 'Ana',
    email: 'ana@example.com',
    phone: '11888880000',
    status: 'ACTIVE',
  },
];

test.describe('Moradores da minha unidade', () => {
  test('master (RESIDENT_MANAGE) vê a lista de moradores', async ({ page, mock }) => {
    mock.get('/units/me/members', MEMBERS);
    await page.goto('/minha-unidade/moradores');

    await expect(page.getByRole('heading', { name: /moradores da minha unidade/i })).toBeVisible();
    await expect(page.getByText('João Silva')).toBeVisible();
    await expect(page.getByText('Ana Costa')).toBeVisible();
    await expect(page.getByRole('button', { name: /adicionar morador/i })).toBeVisible();
  });
});
