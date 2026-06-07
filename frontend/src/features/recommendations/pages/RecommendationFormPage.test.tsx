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
vi.mock('../api/recommendationsApi', () => ({
  createRecommendation: vi.fn(),
  updateRecommendation: vi.fn(),
  getRecommendation: vi.fn(),
  getRecommendationPhotoUrl: vi.fn(),
  uploadRecommendationPhoto: vi.fn(),
  deleteRecommendationPhoto: vi.fn(),
}));
vi.mock('../api/tagsApi', () => ({ searchTags: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { RecommendationFormPage } from './RecommendationFormPage';
import { createRecommendation } from '../api/recommendationsApi';
import { searchTags } from '../api/tagsApi';
import { toast } from 'sonner';

const createMock = vi.mocked(createRecommendation);
const searchTagsMock = vi.mocked(searchTags);
const toastError = vi.mocked(toast.error);

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/indicacoes/nova']}>
      <Routes>
        <Route path="/indicacoes/nova" element={<RecommendationFormPage />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  searchTagsMock.mockResolvedValue([]);
  createMock.mockResolvedValue({ id: 'new-1' } as never);
});

describe('RecommendationFormPage (criação)', () => {
  it('bloqueia submit sem nome do serviço', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /criar indicação/i }));

    expect(toastError).toHaveBeenCalledWith('Informe o nome do serviço.');
    expect(createMock).not.toHaveBeenCalled();
  });

  it('cria indicação externa e navega para edição', async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText('Serviço'), 'Encanador Zé');
    await userEvent.click(screen.getByRole('button', { name: /criar indicação/i }));

    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(1));
    expect(createMock).toHaveBeenCalledWith(
      expect.objectContaining({
        serviceName: 'Encanador Zé',
        isResident: false,
        residentUserId: null,
      })
    );
    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith('/indicacoes/new-1/editar', { replace: true })
    );
  });

  it('morador sem UUID bloqueia o submit', async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText('Serviço'), 'Encanador Zé');
    await userEvent.click(screen.getByRole('checkbox', { name: /é morador/i }));
    await userEvent.click(screen.getByRole('button', { name: /criar indicação/i }));

    expect(toastError).toHaveBeenCalledWith('Informe o UUID do morador indicado.');
    expect(createMock).not.toHaveBeenCalled();
  });

  it('morador com UUID cria como isResident com residentUserId', async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText('Serviço'), 'Encanador Zé');
    await userEvent.click(screen.getByRole('checkbox', { name: /é morador/i }));
    await userEvent.type(
      screen.getByLabelText('UUID do morador indicado'),
      '11111111-1111-1111-1111-111111111111'
    );
    await userEvent.click(screen.getByRole('button', { name: /criar indicação/i }));

    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(1));
    expect(createMock).toHaveBeenCalledWith(
      expect.objectContaining({
        isResident: true,
        residentUserId: '11111111-1111-1111-1111-111111111111',
      })
    );
  });
});
