import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const navigateMock = vi.fn();

vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => navigateMock };
});
vi.mock('browser-image-compression', () => ({ default: vi.fn() }));
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
import { createClassified } from '../api/classifiedsApi';
import { toast } from 'sonner';

const createMock = vi.mocked(createClassified);
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
});

describe('ClassifiedFormPage (criação)', () => {
  it('bloqueia submit sem título', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /criar anúncio/i }));

    expect(toastError).toHaveBeenCalledWith('Informe um título.');
    expect(createMock).not.toHaveBeenCalled();
  });

  it('cria anúncio e navega para edição', async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText('Título'), 'Sofá 3 lugares');
    await userEvent.click(screen.getByRole('button', { name: /criar anúncio/i }));

    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(1));
    expect(createMock).toHaveBeenCalledWith(expect.objectContaining({ title: 'Sofá 3 lugares' }));
    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith('/classificados/new-1/editar', { replace: true })
    );
  });
});
