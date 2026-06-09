import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/faqApi', () => ({ listFaq: vi.fn() }));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { FaqPage } from './FaqPage';
import { listFaq } from '../api/faqApi';
import { useAuth } from '@/features/auth/useAuth';

const listMock = vi.mocked(listFaq);
const useAuthMock = vi.mocked(useAuth);

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

function setUser(authorities: string[] = []) {
  useAuthMock.mockReturnValue({ user: { id: 'u1', authorities } } as never);
}

function renderPage() {
  return render(
    <MemoryRouter>
      <FaqPage />
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  setUser();
});

describe('FaqPage', () => {
  it('renderiza os headings de categoria e as perguntas', async () => {
    listMock.mockResolvedValue([
      faq({ id: 'f1', category: 'Regras', question: 'Posso ter pet?' }),
      faq({ id: 'f2', category: 'Horários', question: 'Que horas abre a piscina?' }),
    ]);
    renderPage();

    expect(await screen.findByText('Regras')).toBeInTheDocument();
    expect(screen.getByText('Horários')).toBeInTheDocument();
    expect(screen.getByText('Posso ter pet?')).toBeInTheDocument();
    expect(screen.getByText('Que horas abre a piscina?')).toBeInTheDocument();
  });

  it('sem FAQ_MANAGE não exibe link "Gerenciar"', async () => {
    setUser([]);
    listMock.mockResolvedValue([faq()]);
    renderPage();

    await screen.findByText('Pergunta padrão?');
    expect(screen.queryByRole('link', { name: /gerenciar/i })).not.toBeInTheDocument();
  });

  it('com FAQ_MANAGE exibe link "Gerenciar" apontando para /faq/gerenciar', async () => {
    setUser(['FAQ_MANAGE']);
    listMock.mockResolvedValue([faq()]);
    renderPage();

    const link = await screen.findByRole('link', { name: /gerenciar/i });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/faq/gerenciar');
  });

  it('mostra estado vazio quando não há FAQs', async () => {
    listMock.mockResolvedValue([]);
    renderPage();

    expect(await screen.findByText('Nenhuma informação publicada ainda.')).toBeInTheDocument();
  });
});
