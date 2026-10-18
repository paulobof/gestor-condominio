import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../api/complaintsApi', async (orig) => {
  const actual = await orig<typeof import('../api/complaintsApi')>();
  return {
    ...actual,
    adminQueue: vi.fn(),
    previewBatch: vi.fn(),
    createBatch: vi.fn(),
    listBatches: vi.fn(),
  };
});

import { ComplaintForwardPage } from './ComplaintForwardPage';
import {
  adminQueue,
  createBatch,
  listBatches,
  previewBatch,
  type Complaint,
} from '../api/complaintsApi';
import { toast } from 'sonner';

const mk = (id: string, code: number): Complaint => ({
  id,
  code,
  typeId: 't',
  typeName: 'Manutenção',
  subtypeId: 's',
  subtypeName: 'Pintura',
  locationId: 'l',
  locationName: 'Torre 2',
  locationDetail: null,
  description: 'Parede',
  status: 'READY',
  dueDate: null,
  rejectionReason: null,
  resolutionNote: null,
  createdAt: '2026-10-10T12:00:00Z',
  forwardedAt: null,
  resolvedAt: null,
  editable: false,
  photos: [],
});

const writeText = vi.fn().mockResolvedValue(undefined);

beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(navigator, { clipboard: { writeText } });
  vi.mocked(adminQueue).mockResolvedValue({
    content: [
      { complaint: mk('a', 1), authorName: 'X', unitCode: 'T1-1' },
      { complaint: mk('b', 2), authorName: 'Y', unitCode: 'T1-2' },
    ],
    totalElements: 2,
    totalPages: 1,
    number: 0,
  });
  vi.mocked(listBatches).mockResolvedValue([]);
  vi.mocked(previewBatch).mockResolvedValue({ text: 'Chamados — LOTE', count: 1 });
  vi.mocked(createBatch).mockResolvedValue({
    id: 'b1',
    sentAt: '2026-10-17T12:00:00Z',
    sentByName: 'Admin',
    complaintCount: 1,
    renderedText: 'Chamados — LOTE',
  });
});

function renderPage() {
  return render(
    <MemoryRouter>
      <ComplaintForwardPage />
    </MemoryRouter>
  );
}

describe('ComplaintForwardPage', () => {
  it('lista os prontos, todos marcados por padrão', async () => {
    renderPage();
    expect(adminQueue).toHaveBeenCalledWith(['READY'], 0);
    const boxes = await screen.findAllByRole('checkbox');
    expect(boxes).toHaveLength(2);
    boxes.forEach((b) => expect(b).toBeChecked());
  });

  it('gera o texto só dos marcados, copia e confirma', async () => {
    renderPage();
    const [first] = await screen.findAllByRole('checkbox');
    await userEvent.click(first); // desmarca #1
    await userEvent.click(screen.getByRole('button', { name: 'Gerar texto' }));

    expect(previewBatch).toHaveBeenCalledWith(['b']);
    expect(await screen.findByDisplayValue('Chamados — LOTE')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Copiar' }));
    expect(writeText).toHaveBeenCalledWith('Chamados — LOTE');

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar envio' }));
    await userEvent.click(screen.getByRole('button', { name: /sim, marcar como encaminhados/i }));
    await waitFor(() => expect(createBatch).toHaveBeenCalledWith(['b']));
    expect(toast.success).toHaveBeenCalledWith('1 chamado(s) encaminhado(s).');
  });

  it('conflito ao confirmar mostra a mensagem do backend e recarrega', async () => {
    vi.mocked(createBatch).mockRejectedValue({
      response: {
        data: { message: 'O chamado #2 não está mais pronto para encaminhar. Recarregue.' },
      },
    });
    renderPage();
    await screen.findAllByRole('checkbox');
    await userEvent.click(screen.getByRole('button', { name: 'Gerar texto' }));
    await screen.findByDisplayValue('Chamados — LOTE');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar envio' }));
    await userEvent.click(screen.getByRole('button', { name: /sim, marcar como encaminhados/i }));

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        'O chamado #2 não está mais pronto para encaminhar. Recarregue.'
      )
    );
    expect(adminQueue).toHaveBeenCalledTimes(2);
  });

  it('sem nada marcado não gera texto', async () => {
    renderPage();
    const boxes = await screen.findAllByRole('checkbox');
    for (const b of boxes) await userEvent.click(b);
    expect(screen.getByRole('button', { name: 'Gerar texto' })).toBeDisabled();
  });
});
