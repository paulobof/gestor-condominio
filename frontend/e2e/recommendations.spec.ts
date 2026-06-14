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

    // exact: true para não casar o rodapé global (aria-label "WhatsApp da Wizortech" etc.)
    const instagram = page.getByRole('link', { name: 'Instagram', exact: true });
    await expect(instagram).toHaveAttribute('href', 'https://instagram.com/encanadorze');

    const facebook = page.getByRole('link', { name: 'Facebook', exact: true });
    await expect(facebook).toHaveAttribute('href', 'https://facebook.com/encanadorze');

    const whatsapp = page.getByRole('link', { name: 'WhatsApp', exact: true });
    await expect(whatsapp).toHaveAttribute('href', 'https://wa.me/5511999990000');
  });

  test('admin (sem unidade) cria indicação de morador enviando residentUserId', async ({
    page,
    mock,
  }) => {
    mock.user({ unitId: null, isUnitMaster: false }); // admin: não é morador logado
    mock.post('/recommendations', { ...REC, id: 'new-rec' });
    await page.goto('/indicacoes/nova');

    await page.getByLabel('Serviço').fill('Eletricista do bloco');
    await page.getByRole('checkbox', { name: /é morador/i }).check();
    await page.getByLabel('UUID do morador indicado').fill('11111111-1111-1111-1111-111111111111');

    const createReq = page.waitForRequest(
      (r) => r.method() === 'POST' && r.url().endsWith('/api/recommendations')
    );
    await page.getByRole('button', { name: /criar indicação/i }).click();
    const body = (await createReq).postDataJSON();

    expect(body).toMatchObject({
      isResident: true,
      residentUserId: '11111111-1111-1111-1111-111111111111',
    });
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
    await expect(page.getByRole('link', { name: 'Instagram', exact: true })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'WhatsApp', exact: true })).toHaveCount(0);
  });
});
