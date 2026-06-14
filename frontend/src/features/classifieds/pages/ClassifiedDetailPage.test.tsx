import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const navigateMock = vi.fn();

vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => navigateMock };
});
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('../api/classifiedsApi', () => ({
  getClassified: vi.fn(),
  getClassifiedPhotoUrl: vi.fn(),
  deleteClassified: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { ClassifiedDetailPage } from './ClassifiedDetailPage';
import { useAuth } from '@/features/auth/useAuth';
import { getClassified, deleteClassified } from '../api/classifiedsApi';

const useAuthMock = vi.mocked(useAuth);
const getMock = vi.mocked(getClassified);
const deleteMock = vi.mocked(deleteClassified);

function classified(over: Record<string, unknown> = {}) {
  return {
    id: 'c1',
    title: 'Sofá 3 lugares',
    description: 'seminovo',
    price: 500,
    status: 'ACTIVE',
    authorUserId: 'u1',
    createdAt: '2026-06-06T00:00:00Z',
    photos: [],
    contactName: null,
    contactPhone: null,
    ...over,
  } as never;
}

function setUser(id: string, authorities: string[] = []) {
  useAuthMock.mockReturnValue({ user: { id, authorities } } as never);
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/classificados/c1']}>
      <Routes>
        <Route path="/classificados/:id" element={<ClassifiedDetailPage />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ClassifiedDetailPage', () => {
  it('renderiza o anúncio', async () => {
    setUser('u9');
    getMock.mockResolvedValue(classified());
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Sofá 3 lugares' })).toBeInTheDocument();
    expect(screen.getByText('Ativo')).toBeInTheDocument();
    expect(screen.getByText('seminovo')).toBeInTheDocument();
  });

  it('mostra "não encontrado" quando a API falha', async () => {
    setUser('u9');
    getMock.mockRejectedValue(new Error('404'));
    renderPage();
    expect(await screen.findByText('Anúncio não encontrado.')).toBeInTheDocument();
  });

  it('autor vê Editar e Excluir', async () => {
    setUser('u1');
    getMock.mockResolvedValue(classified());
    renderPage();
    await screen.findByRole('heading', { name: 'Sofá 3 lugares' });

    expect(screen.getByRole('link', { name: /editar/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /excluir/i })).toBeInTheDocument();
  });

  it('terceiro sem permissão não vê ações', async () => {
    setUser('u9');
    getMock.mockResolvedValue(classified());
    renderPage();
    await screen.findByRole('heading', { name: 'Sofá 3 lugares' });

    expect(screen.queryByRole('link', { name: /editar/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /excluir/i })).not.toBeInTheDocument();
  });

  it('moderador vê ações mesmo sem ser autor', async () => {
    setUser('u9', ['CLASSIFIED_MODERATE']);
    getMock.mockResolvedValue(classified());
    renderPage();
    await screen.findByRole('heading', { name: 'Sofá 3 lugares' });

    expect(screen.getByRole('button', { name: /excluir/i })).toBeInTheDocument();
  });

  it('autor exclui após confirmar e navega de volta', async () => {
    setUser('u1');
    getMock.mockResolvedValue(classified());
    deleteMock.mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();
    await screen.findByRole('heading', { name: 'Sofá 3 lugares' });

    await userEvent.click(screen.getByRole('button', { name: /excluir/i }));

    expect(deleteMock).toHaveBeenCalledWith('c1');
    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith('/classificados', { replace: true })
    );
  });

  it('mostra nome e telefone do anunciante quando presentes', async () => {
    setUser('u9');
    getMock.mockResolvedValue(
      classified({ contactName: 'Ana Costa', contactPhone: '+5511999990000' })
    );
    renderPage();
    await screen.findByRole('heading', { name: 'Sofá 3 lugares' });

    expect(screen.getByText('Ana Costa')).toBeInTheDocument();
    const link = screen.getByRole('link', { name: '+5511999990000' });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', 'tel:+5511999990000');
  });

  it('não mostra bloco de contato quando contactName é null', async () => {
    setUser('u9');
    getMock.mockResolvedValue(classified({ contactName: null, contactPhone: null }));
    renderPage();
    await screen.findByRole('heading', { name: 'Sofá 3 lugares' });

    expect(screen.queryByText('Contato')).not.toBeInTheDocument();
  });

  it('mostra contato sem telefone quando só o nome está presente', async () => {
    setUser('u9');
    getMock.mockResolvedValue(classified({ contactName: 'Sem Fone', contactPhone: null }));
    renderPage();
    await screen.findByRole('heading', { name: 'Sofá 3 lugares' });

    expect(screen.getByText('Sem Fone')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /tel:/i })).not.toBeInTheDocument();
  });
});
