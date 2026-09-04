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
  likeCount: 0,
  dislikeCount: 0,
  reviewCount: 0,
  ratingSum: 0,
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

function reviewsPage(rec: typeof REC) {
  return { content: [rec], totalElements: 1, totalPages: 1, number: 0 };
}

test.describe('Indicações — avaliações', () => {
  test('visitante lê avaliações assinadas "Morador do condomínio" e recebe o popup de login ao tentar avaliar', async ({
    page,
    mock,
  }) => {
    mock.user(null); // visitante: sem sessão
    mock.get('/recommendations/rec-1', REC);
    mock.get('/recommendations/rec-1/reviews', [
      {
        id: 'rv-1',
        sentiment: 'LIKE',
        rating: 5,
        text: 'Atendimento rápido e educado, resolveu no mesmo dia.',
        createdAt: '2026-06-11T10:00:00Z',
        mine: false,
        authorName: null, // avaliação anônima → assinada "Morador do condomínio" na tela
        unitCode: null,
        sameUnit: false,
      },
    ]);

    await page.goto('/indicacoes/rec-1');

    await expect(page.getByText('Morador do condomínio')).toBeVisible();
    await expect(
      page.getByText('Atendimento rápido e educado, resolveu no mesmo dia.')
    ).toBeVisible();

    await page.getByRole('button', { name: 'Avaliar' }).click();

    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByText('Avaliar é para quem tem conta.')).toBeVisible();
  });

  test('morador com unidade avalia e a média do card na listagem muda', async ({ page, mock }) => {
    // mock.user default (MOCK_USER) já tem unidade e isUnitMaster: true.
    const withoutReviews = { ...REC, reviewCount: 0, ratingSum: 0, likeCount: 0 };
    mock.get('/recommendations', reviewsPage(withoutReviews));
    mock.get('/recommendations/rec-1', withoutReviews);
    mock.get('/recommendations/rec-1/reviews', []);

    await page.goto('/indicacoes');
    await expect(page.getByText('Sem avaliações')).toBeVisible();

    await page.getByRole('link', { name: 'Encanador Zé' }).click();
    await expect(page).toHaveURL(/\/indicacoes\/rec-1$/);

    await page.getByRole('button', { name: '5 estrelas' }).click();
    await page.getByLabel('Comentário').fill('Serviço excelente, super recomendo a todos.');

    const newReview = {
      id: 'rv-new',
      sentiment: 'LIKE',
      rating: 5,
      text: 'Serviço excelente, super recomendo a todos.',
      createdAt: '2026-06-12T09:00:00Z',
      mine: true,
      authorName: null,
      unitCode: 'unit-1',
      sameUnit: true,
    };
    mock.post('/recommendations/rec-1/reviews', newReview, 201);
    // overrides o que a página relê logo após o POST e o que a listagem relê ao voltar
    mock.get('/recommendations/rec-1/reviews', [newReview]);
    const withReview = { ...REC, reviewCount: 1, ratingSum: 5, likeCount: 1 };
    mock.get('/recommendations', reviewsPage(withReview));

    await page.getByRole('button', { name: 'Publicar avaliação' }).click();

    await expect(page.getByText(/Seu apartamento já avaliou/)).toBeVisible();

    await page.getByRole('link', { name: 'Voltar' }).click();
    await expect(page).toHaveURL(/\/indicacoes$/);
    await expect(page.getByText('★ 5,0 · 1 avaliação')).toBeVisible();
  });
});
