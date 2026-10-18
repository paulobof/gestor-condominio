import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const navigateMock = vi.fn();
vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => navigateMock };
});
vi.mock('browser-image-compression', () => ({
  default: vi.fn((file: File) => Promise.resolve(file)),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../api/complaintsApi', async (orig) => {
  const actual = await orig<typeof import('../api/complaintsApi')>();
  return {
    ...actual,
    getCatalog: vi.fn(),
    getComplaint: vi.fn(),
    createComplaint: vi.fn(),
    updateComplaint: vi.fn(),
    uploadComplaintPhoto: vi.fn(),
    deleteComplaintPhoto: vi.fn(),
    getComplaintPhotoUrl: vi.fn(),
  };
});

import { ComplaintFormPage } from './ComplaintFormPage';
import { createComplaint, getCatalog, uploadComplaintPhoto } from '../api/complaintsApi';
import { toast } from 'sonner';

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

function renderNew() {
  return render(
    <MemoryRouter initialEntries={['/reclamacoes/nova']}>
      <Routes>
        <Route path="/reclamacoes/nova" element={<ComplaintFormPage />} />
      </Routes>
    </MemoryRouter>
  );
}

async function fillValid() {
  await screen.findByRole('option', { name: 'Manutenção' });
  await userEvent.selectOptions(screen.getByLabelText('Tipo'), 't1');
  await userEvent.selectOptions(screen.getByLabelText('Subtipo'), 's1');
  await userEvent.selectOptions(screen.getByLabelText('Local'), 'l1');
  await userEvent.type(screen.getByLabelText(/descrição/i), 'Parede descascando no hall');
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getCatalog).mockResolvedValue(CATALOG);
  vi.mocked(createComplaint).mockResolvedValue({ id: 'c1' } as never);
  vi.mocked(uploadComplaintPhoto).mockResolvedValue({ id: 'p1' } as never);
  globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview');
  globalThis.URL.revokeObjectURL = vi.fn();
});

describe('ComplaintFormPage (abrir)', () => {
  it('bloqueia envio com descrição curta', async () => {
    renderNew();
    await screen.findByRole('option', { name: 'Manutenção' });
    await userEvent.selectOptions(screen.getByLabelText('Tipo'), 't1');
    await userEvent.selectOptions(screen.getByLabelText('Subtipo'), 's1');
    await userEvent.selectOptions(screen.getByLabelText('Local'), 'l1');
    await userEvent.type(screen.getByLabelText(/descrição/i), 'curto');
    await userEvent.click(screen.getByRole('button', { name: /enviar reclamação/i }));

    expect(toast.error).toHaveBeenCalledWith('A descrição precisa ter pelo menos 10 caracteres.');
    expect(createComplaint).not.toHaveBeenCalled();
  });

  it('mostra contador de caracteres', async () => {
    renderNew();
    await fillValid();
    expect(screen.getByText('26/2000')).toBeInTheDocument();
  });

  it('cria, envia fotos pendentes e navega para o detalhe', async () => {
    renderNew();
    await fillValid();
    const file = new File(['x'], 'a.jpg', { type: 'image/jpeg' });
    await userEvent.upload(screen.getByLabelText(/adicionar foto/i), file);
    await userEvent.click(screen.getByRole('button', { name: /enviar reclamação/i }));

    await waitFor(() =>
      expect(createComplaint).toHaveBeenCalledWith({
        typeId: 't1',
        subtypeId: 's1',
        locationId: 'l1',
        locationDetail: null,
        description: 'Parede descascando no hall',
      })
    );
    expect(uploadComplaintPhoto).toHaveBeenCalledWith('c1', file);
    expect(navigateMock).toHaveBeenCalledWith('/reclamacoes/c1', { replace: true });
  });

  it('recusa a 4ª foto', async () => {
    renderNew();
    await screen.findByRole('option', { name: 'Manutenção' });
    const input = screen.getByLabelText(/adicionar foto/i);
    for (let i = 0; i < 3; i++) {
      await userEvent.upload(input, new File(['x'], `${i}.jpg`, { type: 'image/jpeg' }));
    }
    expect(input).toBeDisabled();
    expect(screen.getByText('Limite de 3 fotos atingido.')).toBeInTheDocument();
  });

  it('avisa para não fotografar pessoas ou documentos', async () => {
    renderNew();
    expect(await screen.findByText(/não inclua pessoas/i)).toBeInTheDocument();
  });
});
