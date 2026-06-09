import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/contactsApi', () => ({ listContacts: vi.fn() }));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('@/components/openinghours/OpeningHoursDisplay', () => ({
  OpeningHoursDisplay: () => null,
}));

import { ContactsPage } from './ContactsPage';
import { listContacts } from '../api/contactsApi';
import { useAuth } from '@/features/auth/useAuth';

const listMock = vi.mocked(listContacts);
const useAuthMock = vi.mocked(useAuth);

function contact(over: Record<string, unknown> = {}) {
  return {
    id: 'c1',
    name: 'Portaria',
    category: 'Condomínio',
    phone: '+551133334444',
    notes: null,
    is24h: false,
    openingHours: [],
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
      <ContactsPage />
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  setUser();
});

describe('ContactsPage', () => {
  it('renderiza os nomes dos contatos e links tel: clicáveis', async () => {
    listMock.mockResolvedValue([
      contact({ id: 'c1', name: 'Portaria', phone: '+551133334444' }),
      contact({ id: 'c2', name: 'Zelador', phone: '+5511999998888' }),
    ]);
    renderPage();

    expect(await screen.findByText('Portaria')).toBeInTheDocument();
    expect(screen.getByText('Zelador')).toBeInTheDocument();

    const portariaLink = screen.getByRole('link', { name: '+551133334444' });
    expect(portariaLink).toHaveAttribute('href', 'tel:+551133334444');

    const zeladorLink = screen.getByRole('link', { name: '+5511999998888' });
    expect(zeladorLink).toHaveAttribute('href', 'tel:+5511999998888');
  });

  it('sem CONTACT_MANAGE não exibe link "Gerenciar"', async () => {
    setUser([]);
    listMock.mockResolvedValue([contact()]);
    renderPage();

    await screen.findByText('Portaria');
    expect(screen.queryByRole('link', { name: /gerenciar/i })).not.toBeInTheDocument();
  });

  it('com CONTACT_MANAGE exibe link "Gerenciar" apontando para /contatos/gerenciar', async () => {
    setUser(['CONTACT_MANAGE']);
    listMock.mockResolvedValue([contact()]);
    renderPage();

    const link = await screen.findByRole('link', { name: /gerenciar/i });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/contatos/gerenciar');
  });

  it('mostra estado vazio quando não há contatos', async () => {
    listMock.mockResolvedValue([]);
    renderPage();

    expect(await screen.findByText('Nenhum contato cadastrado ainda.')).toBeInTheDocument();
  });
});
