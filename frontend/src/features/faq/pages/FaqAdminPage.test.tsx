import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/faqApi', () => ({
  listAllFaq: vi.fn(),
  createFaq: vi.fn(),
  updateFaq: vi.fn(),
  setFaqPublished: vi.fn(),
  reorderFaq: vi.fn(),
  deleteFaq: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { FaqAdminPage } from './FaqAdminPage';
import {
  listAllFaq,
  createFaq,
  updateFaq,
  setFaqPublished,
  reorderFaq,
  deleteFaq,
} from '../api/faqApi';
import { toast } from 'sonner';

const listMock = vi.mocked(listAllFaq);
const createMock = vi.mocked(createFaq);
const updateMock = vi.mocked(updateFaq);
const publishMock = vi.mocked(setFaqPublished);
const reorderMock = vi.mocked(reorderFaq);
const deleteMock = vi.mocked(deleteFaq);
const toastSuccess = vi.mocked(toast.success);
const toastError = vi.mocked(toast.error);

function faq(over: Record<string, unknown> = {}) {
  return {
    id: 'f1',
    question: 'Pergunta padrão?',
    answer: 'Resposta padrão.',
    category: 'Geral',
    published: true,
    ordering: 1,
    updatedAt: '2026-06-01T00:00:00Z',
    ...over,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <FaqAdminPage />
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  // Make mutations resolve successfully by default
  createMock.mockResolvedValue(faq() as never);
  updateMock.mockResolvedValue(faq() as never);
  publishMock.mockResolvedValue(faq() as never);
  reorderMock.mockResolvedValue(undefined);
  deleteMock.mockResolvedValue(undefined);
});

describe('FaqAdminPage', () => {
  it('chama listAllFaq na montagem e exibe as perguntas', async () => {
    listMock.mockResolvedValue([
      faq({ id: 'f1', question: 'Posso ter pet?', published: true }),
      faq({ id: 'f2', question: 'Horário da piscina?', published: false }),
    ]);
    renderPage();

    expect(listMock).toHaveBeenCalledOnce();
    expect(await screen.findByText('Posso ter pet?')).toBeInTheDocument();
    expect(screen.getByText('Horário da piscina?')).toBeInTheDocument();
  });

  it('exibe badge de rascunho para item não publicado', async () => {
    listMock.mockResolvedValue([faq({ id: 'f1', question: 'Posso ter pet?', published: false })]);
    renderPage();

    await screen.findByText('Posso ter pet?');
    expect(screen.getByText(/rascunho/i)).toBeInTheDocument();
  });

  it('o formulário tem campos pergunta, resposta, categoria e toggle publicado', async () => {
    listMock.mockResolvedValue([]);
    renderPage();

    await screen.findByRole('heading', { name: /gerenciar faq/i });

    expect(screen.getByLabelText(/pergunta/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/resposta/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/categoria/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/publicado/i)).toBeInTheDocument();
  });

  it('preenchendo o formulário e clicando "Salvar" chama createFaq com os dados e recarrega', async () => {
    listMock.mockResolvedValue([]);
    renderPage();

    await screen.findByRole('heading', { name: /gerenciar faq/i });

    fireEvent.change(screen.getByLabelText(/pergunta/i), {
      target: { value: 'Nova pergunta?' },
    });
    fireEvent.change(screen.getByLabelText(/resposta/i), {
      target: { value: 'Nova resposta.' },
    });
    fireEvent.change(screen.getByLabelText(/categoria/i), {
      target: { value: 'Regras' },
    });
    fireEvent.click(screen.getByLabelText(/publicado/i));

    fireEvent.click(screen.getByRole('button', { name: /salvar/i }));

    await waitFor(() => {
      expect(createMock).toHaveBeenCalledWith({
        question: 'Nova pergunta?',
        answer: 'Nova resposta.',
        category: 'Regras',
        published: true,
      });
    });

    // reloads after create
    await waitFor(() => {
      expect(listMock).toHaveBeenCalledTimes(2);
    });
  });

  it('exibe toast.success após criar com sucesso', async () => {
    listMock.mockResolvedValue([]);
    renderPage();

    await screen.findByRole('heading', { name: /gerenciar faq/i });

    fireEvent.change(screen.getByLabelText(/pergunta/i), {
      target: { value: 'Pergunta?' },
    });
    fireEvent.change(screen.getByLabelText(/resposta/i), {
      target: { value: 'Resposta.' },
    });
    fireEvent.change(screen.getByLabelText(/categoria/i), {
      target: { value: 'Geral' },
    });

    fireEvent.click(screen.getByRole('button', { name: /salvar/i }));

    await waitFor(() => expect(toastSuccess).toHaveBeenCalled());
  });

  it('exibe toast.error quando createFaq rejeita', async () => {
    listMock.mockResolvedValue([]);
    createMock.mockRejectedValue(new Error('fail'));
    renderPage();

    await screen.findByRole('heading', { name: /gerenciar faq/i });

    fireEvent.change(screen.getByLabelText(/pergunta/i), {
      target: { value: 'Pergunta?' },
    });
    fireEvent.change(screen.getByLabelText(/resposta/i), {
      target: { value: 'Resposta.' },
    });
    fireEvent.change(screen.getByLabelText(/categoria/i), {
      target: { value: 'Geral' },
    });

    fireEvent.click(screen.getByRole('button', { name: /salvar/i }));

    await waitFor(() => expect(toastError).toHaveBeenCalled());
  });

  it('cada linha exibe botões Editar, Excluir, Publicar/Despublicar, ↑ e ↓', async () => {
    listMock.mockResolvedValue([faq({ id: 'f1', question: 'Posso ter pet?', published: true })]);
    renderPage();

    await screen.findByText('Posso ter pet?');

    expect(screen.getByRole('button', { name: /editar/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /excluir/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /despublicar/i })).toBeInTheDocument();
  });

  it('Editar preenche o formulário com os dados do item', async () => {
    listMock.mockResolvedValue([
      faq({
        id: 'f1',
        question: 'Posso ter pet?',
        answer: 'Sim.',
        category: 'Regras',
        published: true,
      }),
    ]);
    renderPage();

    await screen.findByText('Posso ter pet?');
    fireEvent.click(screen.getByRole('button', { name: /editar/i }));

    expect((screen.getByLabelText(/pergunta/i) as HTMLInputElement).value).toBe('Posso ter pet?');
    expect((screen.getByLabelText(/resposta/i) as HTMLTextAreaElement).value).toBe('Sim.');
    expect((screen.getByLabelText(/categoria/i) as HTMLInputElement).value).toBe('Regras');
  });

  it('após Editar e Salvar chama updateFaq com o id correto', async () => {
    listMock.mockResolvedValue([
      faq({
        id: 'f1',
        question: 'Posso ter pet?',
        answer: 'Sim.',
        category: 'Regras',
        published: true,
      }),
    ]);
    renderPage();

    await screen.findByText('Posso ter pet?');
    fireEvent.click(screen.getByRole('button', { name: /editar/i }));
    fireEvent.click(screen.getByRole('button', { name: /salvar/i }));

    await waitFor(() => {
      expect(updateMock).toHaveBeenCalledWith('f1', {
        question: 'Posso ter pet?',
        answer: 'Sim.',
        category: 'Regras',
        published: true,
      });
    });
  });

  it('Excluir chama deleteFaq e recarrega', async () => {
    listMock.mockResolvedValue([faq({ id: 'f1', question: 'Posso ter pet?' })]);
    renderPage();

    await screen.findByText('Posso ter pet?');
    fireEvent.click(screen.getByRole('button', { name: /excluir/i }));

    await waitFor(() => expect(deleteMock).toHaveBeenCalledWith('f1'));
    await waitFor(() => expect(listMock).toHaveBeenCalledTimes(2));
  });

  it('Publicar/Despublicar chama setFaqPublished com o valor invertido', async () => {
    listMock.mockResolvedValue([faq({ id: 'f1', published: true })]);
    renderPage();

    await screen.findByText('Pergunta padrão?');
    fireEvent.click(screen.getByRole('button', { name: /despublicar/i }));

    await waitFor(() => expect(publishMock).toHaveBeenCalledWith('f1', false));
  });

  it('Publicar chama setFaqPublished(id, true) para item não publicado', async () => {
    listMock.mockResolvedValue([faq({ id: 'f1', published: false })]);
    renderPage();

    await screen.findByText('Pergunta padrão?');
    fireEvent.click(screen.getByRole('button', { name: /^publicar$/i }));

    await waitFor(() => expect(publishMock).toHaveBeenCalledWith('f1', true));
  });

  it('↑ e ↓ chamam reorderFaq trocando ordering de itens adjacentes na mesma categoria', async () => {
    listMock.mockResolvedValue([
      faq({ id: 'f1', question: 'Primeira?', category: 'Geral', ordering: 1 }),
      faq({ id: 'f2', question: 'Segunda?', category: 'Geral', ordering: 2 }),
    ]);
    renderPage();

    await screen.findByText('Primeira?');

    // ↓ no primeiro item da categoria (f1) deve trocar com f2
    const downButtons = screen.getAllByRole('button', { name: /↓/ });
    fireEvent.click(downButtons[0]);

    await waitFor(() => {
      expect(reorderMock).toHaveBeenCalledWith([
        { id: 'f1', ordering: 2 },
        { id: 'f2', ordering: 1 },
      ]);
    });
  });

  it('↑ está desabilitado no primeiro item da categoria', async () => {
    listMock.mockResolvedValue([
      faq({ id: 'f1', question: 'Única?', category: 'Geral', ordering: 1 }),
    ]);
    renderPage();

    await screen.findByText('Única?');

    const upButtons = screen.getAllByRole('button', { name: /↑/ });
    expect(upButtons[0]).toBeDisabled();
  });

  it('↓ está desabilitado no último item da categoria', async () => {
    listMock.mockResolvedValue([
      faq({ id: 'f1', question: 'Única?', category: 'Geral', ordering: 1 }),
    ]);
    renderPage();

    await screen.findByText('Única?');

    const downButtons = screen.getAllByRole('button', { name: /↓/ });
    expect(downButtons[0]).toBeDisabled();
  });
});
