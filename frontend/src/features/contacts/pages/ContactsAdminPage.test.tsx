import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/contactsApi', () => ({
  listContacts: vi.fn(),
  createContact: vi.fn(),
  updateContact: vi.fn(),
  deleteContact: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('@/components/openinghours/OpeningHoursEditor', () => ({
  OpeningHoursEditor: ({ onChange }: { onChange: (h: unknown[], is24h: boolean) => void }) => (
    <button
      type="button"
      onClick={() => onChange([{ dayOfWeek: 1, opensAt: '08:00', closesAt: '18:00' }], false)}
    >
      set-hours
    </button>
  ),
}));

import { ContactsAdminPage } from './ContactsAdminPage';
import { listContacts, createContact, updateContact, deleteContact } from '../api/contactsApi';
import { toast } from 'sonner';

const listMock = vi.mocked(listContacts);
const createMock = vi.mocked(createContact);
const updateMock = vi.mocked(updateContact);
const deleteMock = vi.mocked(deleteContact);
const toastSuccess = vi.mocked(toast.success);
const toastError = vi.mocked(toast.error);

function contact(over: Record<string, unknown> = {}) {
  return {
    id: 'c1',
    name: 'Portaria',
    category: 'Segurança',
    phone: '11999990000',
    notes: null,
    is24h: false,
    openingHours: [],
    updatedAt: '2026-06-01T00:00:00Z',
    ...over,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <ContactsAdminPage />
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  createMock.mockResolvedValue(contact() as never);
  updateMock.mockResolvedValue(contact() as never);
  deleteMock.mockResolvedValue(undefined);
});

describe('ContactsAdminPage', () => {
  it('chama listContacts na montagem e exibe os contatos', async () => {
    listMock.mockResolvedValue([
      contact({ id: 'c1', name: 'Portaria' }),
      contact({ id: 'c2', name: 'Piscina' }),
    ]);
    renderPage();

    expect(listMock).toHaveBeenCalledOnce();
    expect(await screen.findByText('Portaria')).toBeInTheDocument();
    expect(screen.getByText('Piscina')).toBeInTheDocument();
  });

  it('preenchendo formulário e clicando Salvar chama createContact e recarrega', async () => {
    listMock.mockResolvedValue([]);
    renderPage();

    await screen.findByRole('heading', { name: /gerenciar contatos/i });

    fireEvent.change(screen.getByLabelText(/^nome$/i), {
      target: { value: 'Academia' },
    });
    fireEvent.change(screen.getByLabelText(/^categoria$/i), {
      target: { value: 'Lazer' },
    });
    fireEvent.change(screen.getByLabelText(/^telefone$/i), {
      target: { value: '1133334444' },
    });

    // trigger the OpeningHoursEditor stub
    fireEvent.click(screen.getByRole('button', { name: /set-hours/i }));

    fireEvent.click(screen.getByRole('button', { name: /^salvar$/i }));

    await waitFor(() => {
      expect(createMock).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Academia',
          category: 'Lazer',
          phone: '1133334444',
          openingHours: [{ dayOfWeek: 1, opensAt: '08:00', closesAt: '18:00' }],
          is24h: false,
        })
      );
    });

    await waitFor(() => {
      expect(listMock).toHaveBeenCalledTimes(2);
    });
  });

  it('cada linha exibe botões Editar e Excluir', async () => {
    listMock.mockResolvedValue([contact({ id: 'c1', name: 'Portaria' })]);
    renderPage();

    await screen.findByText('Portaria');

    expect(screen.getByRole('button', { name: /editar/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /excluir/i })).toBeInTheDocument();
  });

  it('Editar preenche o formulário com os dados do item', async () => {
    listMock.mockResolvedValue([
      contact({ id: 'c1', name: 'Portaria', category: 'Segurança', phone: '11900000000' }),
    ]);
    renderPage();

    await screen.findByText('Portaria');
    fireEvent.click(screen.getByRole('button', { name: /editar/i }));

    expect((screen.getByLabelText(/^nome$/i) as HTMLInputElement).value).toBe('Portaria');
    expect((screen.getByLabelText(/^categoria$/i) as HTMLInputElement).value).toBe('Segurança');
    expect((screen.getByLabelText(/^telefone$/i) as HTMLInputElement).value).toBe('11900000000');
  });

  it('após Editar e Salvar chama updateContact com o id correto', async () => {
    listMock.mockResolvedValue([
      contact({ id: 'c1', name: 'Portaria', category: 'Segurança', phone: '11900000000' }),
    ]);
    renderPage();

    await screen.findByText('Portaria');
    fireEvent.click(screen.getByRole('button', { name: /editar/i }));
    fireEvent.click(screen.getByRole('button', { name: /^salvar$/i }));

    await waitFor(() => {
      expect(updateMock).toHaveBeenCalledWith(
        'c1',
        expect.objectContaining({
          name: 'Portaria',
          category: 'Segurança',
          phone: '11900000000',
        })
      );
    });
  });

  it('Excluir chama deleteContact e recarrega', async () => {
    listMock.mockResolvedValue([contact({ id: 'c1', name: 'Portaria' })]);
    renderPage();

    await screen.findByText('Portaria');
    fireEvent.click(screen.getByRole('button', { name: /excluir/i }));

    await waitFor(() => expect(deleteMock).toHaveBeenCalledWith('c1'));
    await waitFor(() => expect(listMock).toHaveBeenCalledTimes(2));
  });

  it('exibe toast.success após criar com sucesso', async () => {
    listMock.mockResolvedValue([]);
    renderPage();

    await screen.findByRole('heading', { name: /gerenciar contatos/i });

    fireEvent.change(screen.getByLabelText(/^nome$/i), {
      target: { value: 'Recepção' },
    });
    fireEvent.change(screen.getByLabelText(/^categoria$/i), {
      target: { value: 'Geral' },
    });
    fireEvent.change(screen.getByLabelText(/^telefone$/i), {
      target: { value: '1100000000' },
    });

    fireEvent.click(screen.getByRole('button', { name: /^salvar$/i }));

    await waitFor(() => expect(toastSuccess).toHaveBeenCalled());
  });

  it('exibe toast.error quando createContact rejeita', async () => {
    listMock.mockResolvedValue([]);
    createMock.mockRejectedValue(new Error('fail'));
    renderPage();

    await screen.findByRole('heading', { name: /gerenciar contatos/i });

    fireEvent.change(screen.getByLabelText(/^nome$/i), {
      target: { value: 'Recepção' },
    });
    fireEvent.change(screen.getByLabelText(/^categoria$/i), {
      target: { value: 'Geral' },
    });
    fireEvent.change(screen.getByLabelText(/^telefone$/i), {
      target: { value: '1100000000' },
    });

    fireEvent.click(screen.getByRole('button', { name: /^salvar$/i }));

    await waitFor(() => expect(toastError).toHaveBeenCalled());
  });
});
