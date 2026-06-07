import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/announcementsApi', () => ({ listAnnouncements: vi.fn() }));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { AnnouncementsListPage } from './AnnouncementsListPage';
import { listAnnouncements } from '../api/announcementsApi';
import { useAuth } from '@/features/auth/useAuth';

const listMock = vi.mocked(listAnnouncements);
const useAuthMock = vi.mocked(useAuth);

function page(content: unknown[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0 } as never;
}

function announcement(over: Record<string, unknown> = {}) {
  return {
    id: 'a1',
    title: 'Manutenção da bomba',
    body: 'Água desligada das 9h às 12h.',
    pinned: false,
    publishedAt: '2026-06-06T00:00:00Z',
    authorUserId: 'u1',
    updatedAt: '2026-06-06T00:00:00Z',
    ...over,
  };
}

function setUser(authorities: string[] = []) {
  useAuthMock.mockReturnValue({ user: { id: 'u1', authorities } } as never);
}

function renderPage() {
  return render(
    <MemoryRouter>
      <AnnouncementsListPage />
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  setUser();
});

describe('AnnouncementsListPage', () => {
  it('lista avisos e marca os fixados', async () => {
    listMock.mockResolvedValue(page([announcement({ pinned: true })]));
    renderPage();

    expect(await screen.findByText('Manutenção da bomba')).toBeInTheDocument();
    expect(screen.getByText('Fixado')).toBeInTheDocument();
  });

  it('mostra estado vazio', async () => {
    listMock.mockResolvedValue(page([]));
    renderPage();
    expect(await screen.findByText('Nenhum aviso.')).toBeInTheDocument();
  });

  it('quem tem ANNOUNCEMENT_MANAGE vê "Novo aviso"', async () => {
    setUser(['ANNOUNCEMENT_MANAGE']);
    listMock.mockResolvedValue(page([]));
    renderPage();

    expect(await screen.findByRole('link', { name: /novo aviso/i })).toBeInTheDocument();
  });

  it('sem permissão não vê "Novo aviso"', async () => {
    setUser([]);
    listMock.mockResolvedValue(page([]));
    renderPage();
    await screen.findByText('Nenhum aviso.');

    expect(screen.queryByRole('link', { name: /novo aviso/i })).not.toBeInTheDocument();
  });
});
