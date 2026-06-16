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
vi.mock('../api/classifiedsApi', () => ({
  createClassified: vi.fn(),
  updateClassified: vi.fn(),
  getClassified: vi.fn(),
  getClassifiedPhotoUrl: vi.fn(),
  uploadClassifiedPhoto: vi.fn(),
  deleteClassifiedPhoto: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { ClassifiedFormPage } from './ClassifiedFormPage';
import { createClassified, uploadClassifiedPhoto } from '../api/classifiedsApi';
import { toast } from 'sonner';

const createMock = vi.mocked(createClassified);
const uploadMock = vi.mocked(uploadClassifiedPhoto);
const toastError = vi.mocked(toast.error);

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/classificados/novo']}>
      <Routes>
        <Route path="/classificados/novo" element={<ClassifiedFormPage />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  createMock.mockResolvedValue({ id: 'new-1' } as never);
  // jsdom não implementa as object URLs usadas no preview de fotos.
  globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview');
  globalThis.URL.revokeObjectURL = vi.fn();
});

describe('ClassifiedFormPage (criação)', () => {
  it('bloqueia submit sem título', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /criar anúncio/i }));

    expect(toastError).toHaveBeenCalledWith('Informe um título.');
    expect(createMock).not.toHaveBeenCalled();
  });

  it('cria anúncio sem foto e navega para o detalhe', async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText('Título'), 'Sofá 3 lugares');
    await userEvent.click(screen.getByRole('button', { name: /criar anúncio/i }));

    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(1));
    expect(createMock).toHaveBeenCalledWith(expect.objectContaining({ title: 'Sofá 3 lugares' }));
    expect(uploadMock).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith('/classificados/new-1', { replace: true })
    );
  });

  it('permite anexar foto na criação e a envia após criar o anúncio', async () => {
    uploadMock.mockResolvedValue({ id: 'p1', ordering: 0, contentType: 'image/png' } as never);
    renderPage();

    await userEvent.type(screen.getByLabelText('Título'), 'Bicicleta');
    const file = new File(['x'], 'foto.png', { type: 'image/png' });
    await userEvent.upload(screen.getByLabelText('Adicionar foto'), file);
    await userEvent.click(screen.getByRole('button', { name: /criar anúncio/i }));

    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(uploadMock).toHaveBeenCalledTimes(1));
    expect(uploadMock).toHaveBeenCalledWith('new-1', expect.any(File));
    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith('/classificados/new-1', { replace: true })
    );
  });
});
