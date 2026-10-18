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
  syndicResponse: null,
  syndicResponseAt: null,
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
    await waitFor(() => expect(createBatch).toHaveBeenCalledWith(['b'], 'Chamados — LOTE'));
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

  it('texto desatualizado (BATCH_STALE) avisa, limpa o texto e recarrega', async () => {
    vi.mocked(createBatch).mockRejectedValue({
      response: {
        status: 409,
        data: {
          code: 'BATCH_STALE',
          message:
            'Algum chamado mudou depois que o texto foi gerado. Gere o texto de novo e copie antes de confirmar.',
        },
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
        'Algum chamado mudou depois que o texto foi gerado. Gere o texto de novo e copie antes de confirmar.'
      )
    );
    expect(createBatch).toHaveBeenCalledWith(['a', 'b'], 'Chamados — LOTE');
    await waitFor(() =>
      expect(screen.queryByRole('textbox', { name: 'Texto do lote' })).not.toBeInTheDocument()
    );
  });

  it('sem nada marcado não gera texto', async () => {
    renderPage();
    const boxes = await screen.findAllByRole('checkbox');
    for (const b of boxes) await userEvent.click(b);
    expect(screen.getByRole('button', { name: 'Gerar texto' })).toBeDisabled();
  });

  it('carrega todas as páginas de prontos e mostra o total real', async () => {
    vi.mocked(adminQueue)
      .mockResolvedValueOnce({
        content: [
          { complaint: mk('a', 1), authorName: 'X', unitCode: 'T1-1' },
          { complaint: mk('b', 2), authorName: 'Y', unitCode: 'T1-2' },
        ],
        totalElements: 3,
        totalPages: 2,
        number: 0,
      })
      .mockResolvedValueOnce({
        content: [{ complaint: mk('c', 3), authorName: 'Z', unitCode: 'T1-3' }],
        totalElements: 3,
        totalPages: 2,
        number: 1,
      });
    renderPage();
    expect(await screen.findAllByRole('checkbox')).toHaveLength(3);
    expect(adminQueue).toHaveBeenCalledWith(['READY'], 1);
    expect(screen.getByText(/Prontos para encaminhar \(3\)/)).toBeInTheDocument();
  });
});
