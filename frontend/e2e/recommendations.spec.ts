import { test, expect } from './support/fixtures';

const REC = {
  id: 'rec-1',
  serviceName: 'Encanador Zé',
  professionalName: 'José da Silva',
  phone: null,
  isResident: false,
  residentUserId: null,
  addressLine: null,
  priceRange: null,
  rating: 5,
  comment: 'Resolveu rápido e cobrou justo.',
  recommendedByUserId: 'user-1',
  status: 'ACTIVE',
  createdAt: '2026-06-10T12:00:00Z',
  tags: [],
  openingHours: [],
  photos: [],
  instagramUrl: 'https://instagram.com/encanadorze',
  facebookUrl: 'https://facebook.com/encanadorze',
  whatsappUrl: 'https://wa.me/5511999990000',
  catalogUrl: null,
  ownerUnitId: null,
  ownerUnitCode: null,
};

test.describe('Indicações — links sociais no detalhe', () => {
  test('exibe Instagram, Facebook e WhatsApp com os hrefs corretos', async ({ page, mock }) => {
    mock.get('/recommendations/rec-1', REC);
    await page.goto('/indicacoes/rec-1');

    await expect(page.getByRole('heading', { name: 'Encanador Zé' })).toBeVisible();

    const instagram = page.getByRole('link', { name: 'Instagram' });
    await expect(instagram).toHaveAttribute('href', 'https://instagram.com/encanadorze');

    const facebook = page.getByRole('link', { name: 'Facebook' });
    await expect(facebook).toHaveAttribute('href', 'https://facebook.com/encanadorze');

    const whatsapp = page.getByRole('link', { name: 'WhatsApp' });
    await expect(whatsapp).toHaveAttribute('href', 'https://wa.me/5511999990000');
  });

  test('omite links sociais ausentes', async ({ page, mock }) => {
    mock.get('/recommendations/rec-2', {
      ...REC,
      id: 'rec-2',
      instagramUrl: null,
      facebookUrl: null,
      whatsappUrl: null,
    });
    await page.goto('/indicacoes/rec-2');

    await expect(page.getByRole('heading', { name: 'Encanador Zé' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Instagram' })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'WhatsApp' })).toHaveCount(0);
  });
});
