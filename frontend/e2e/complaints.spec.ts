import { test, expect } from './support/fixtures';

const CATALOG = {
  types: [
    {
      id: 't1',
      name: 'Manutenção',
      active: true,
      sortOrder: 1,
      subtypes: [{ id: 's1', name: 'Pintura', active: true, sortOrder: 1 }],
    },
  ],
  locations: [{ id: 'l1', name: 'Torre 2', active: true, sortOrder: 1 }],
};

const COMPLAINT = {
  id: 'c1',
  code: 123,
  typeId: 't1',
  typeName: 'Manutenção',
  subtypeId: 's1',
  subtypeName: 'Pintura',
  locationId: 'l1',
  locationName: 'Torre 2',
  locationDetail: 'hall',
  description: 'Parede descascando no hall',
  status: 'OPEN',
  dueDate: null,
  rejectionReason: null,
  resolutionNote: null,
  createdAt: '2026-10-17T12:00:00Z',
  forwardedAt: null,
  resolvedAt: null,
  editable: true,
  photos: [],
  syndicResponse: null,
  syndicResponseAt: null,
};

test.describe('Chamados', () => {
  test.beforeEach(async ({ mock }) => {
    mock.get('/features', { complaints: true });
    mock.get('/complaints/catalog', CATALOG);
  });

  test('morador abre chamado com foto', async ({ page, mock }) => {
    mock.post('/complaints', COMPLAINT);
    mock.post('/complaints/c1/photos', { id: 'p1', ordering: 0, contentType: 'image/png' });
    mock.get('/complaints/c1', COMPLAINT);
    mock.get(/\/complaints\/c1\/photos\/p1\/url$/, { url: 'about:blank' });

    await page.goto('/chamados/novo');
    await page.getByLabel('Tipo', { exact: true }).selectOption('t1');
    await page.getByLabel('Subtipo').selectOption('s1');
    await page.getByLabel('Local', { exact: true }).selectOption('l1');
    await page.getByLabel('Descrição').fill('Parede descascando no hall');
    await page.getByLabel('Adicionar foto').setInputFiles({
      name: 'parede.png',
      mimeType: 'image/png',
      // PNG 1x1
      buffer: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
        'base64'
      ),
    });

    // a compressão é assíncrona: espera a miniatura antes de enviar
    await expect(page.getByAltText('Foto 1')).toBeVisible();

    const created = page.waitForRequest(
      (r) => r.method() === 'POST' && new URL(r.url()).pathname === '/api/complaints'
    );
    const uploaded = page.waitForRequest(
      (r) => r.method() === 'POST' && r.url().endsWith('/api/complaints/c1/photos')
    );
    await page.getByRole('button', { name: 'Abrir chamado' }).click();
    expect((await created).postDataJSON()).toMatchObject({
      typeId: 't1',
      subtypeId: 's1',
      locationId: 'l1',
    });
    await uploaded;
    await expect(page).toHaveURL(/\/chamados\/c1$/);
    await expect(page.getByText('Parede descascando no hall')).toBeVisible();
  });

  test('admin encaminha lote e define prazo; mural mostra o prazo', async ({ page, mock }) => {
    const ready = { ...COMPLAINT, status: 'READY', editable: false };
    mock.get('/admin/complaints', {
      content: [{ complaint: ready, authorName: 'Ana', unitCode: 'T2-51' }],
      totalElements: 1,
      totalPages: 1,
      number: 0,
    });
    mock.get('/admin/complaints/batches', []);
    mock.post('/admin/complaints/batches/preview', {
      text: 'Chamados — HELBOR TRILOGY HOME\nLote de 17/10/2026 (1 chamado)',
      count: 1,
    });
    mock.post('/admin/complaints/batches', {
      id: 'b1',
      sentAt: '2026-10-17T12:00:00Z',
      sentByName: 'Síndico de Teste',
      complaintCount: 1,
      renderedText: 'x',
    });

    await page.goto('/admin/chamados/encaminhar');
    await expect(page.getByRole('checkbox')).toBeChecked();
    await page.getByRole('button', { name: 'Gerar texto' }).click();
    await expect(page.getByLabel('Texto do lote')).toHaveValue(/Lote de 17\/10\/2026/);
    await page.getByRole('button', { name: 'Confirmar envio' }).click();
    const batch = page.waitForRequest(
      (r) => r.method() === 'POST' && r.url().endsWith('/api/admin/complaints/batches')
    );
    await page.getByRole('button', { name: /sim, marcar como encaminhados/i }).click();
    expect((await batch).postDataJSON()).toEqual({ ids: ['c1'] });

    const forwarded = { ...ready, status: 'FORWARDED', forwardedAt: '2026-10-17T12:00:00Z' };
    mock.get('/admin/complaints/c1', {
      complaint: forwarded,
      authorName: 'Ana',
      unitCode: 'T2-51',
      events: [],
    });
    mock.put('/admin/complaints/c1/due-date', {
      complaint: { ...forwarded, dueDate: '2026-11-30' },
      authorName: 'Ana',
      unitCode: 'T2-51',
      events: [],
    });
    await page.goto('/admin/chamados/c1');
    await page.getByLabel('Prazo informado pela síndica').fill('2026-11-30');
    await page.getByRole('button', { name: 'Salvar prazo' }).click();
    await expect(page.getByText('Prazo atual: 30/11/2026')).toBeVisible();

    mock.get(/\/complaints\/board$/, {
      content: [{ ...forwarded, dueDate: '2026-11-30' }],
      totalElements: 1,
      totalPages: 1,
      number: 0,
    });
    await page.goto('/chamados');
    await expect(page.getByText('Prazo: 30/11/2026')).toBeVisible();
  });
});
