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
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

import { RecommendationFormPage } from './RecommendationFormPage';
import { createRecommendation, uploadRecommendationPhoto } from '../api/recommendationsApi';
import { searchTags } from '../api/tagsApi';
import { toast } from 'sonner';
import { useAuth } from '@/features/auth/useAuth';

const createMock = vi.mocked(createRecommendation);
const uploadMock = vi.mocked(uploadRecommendationPhoto);
const searchTagsMock = vi.mocked(searchTags);
const toastError = vi.mocked(toast.error);
const useAuthMock = vi.mocked(useAuth);

/** Usuário sem unidade (admin ou externo) */
function setUserNoUnit() {
  useAuthMock.mockReturnValue({ user: { id: 'u1', authorities: [], unitId: null } } as never);
}

/** Usuário morador (tem unitId) */
function setUserWithUnit() {
  useAuthMock.mockReturnValue({ user: { id: 'u1', authorities: [], unitId: 'unit-1' } } as never);
}

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
  setUserNoUnit(); // padrão: usuário sem unidade
  // jsdom não implementa as object URLs usadas no preview de fotos.
  globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview');
  globalThis.URL.revokeObjectURL = vi.fn();
});

describe('RecommendationFormPage (criação)', () => {
  it('bloqueia submit sem nome do serviço', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /criar indicação/i }));

    expect(toastError).toHaveBeenCalledWith('Informe o nome do serviço.');
    expect(createMock).not.toHaveBeenCalled();
  });

  it('cria indicação externa sem foto e navega para o detalhe', async () => {
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
    expect(uploadMock).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith('/indicacoes/new-1', { replace: true })
    );
  });

  it('permite anexar foto na criação e a envia após criar a indicação', async () => {
    uploadMock.mockResolvedValue({ id: 'p1', ordering: 0, contentType: 'image/png' } as never);
    renderPage();

    await userEvent.type(screen.getByLabelText('Serviço'), 'Pintor João');
    const file = new File(['x'], 'foto.png', { type: 'image/png' });
    await userEvent.upload(screen.getByLabelText('Adicionar foto'), file);
    await userEvent.click(screen.getByRole('button', { name: /criar indicação/i }));

    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(uploadMock).toHaveBeenCalledTimes(1));
    expect(uploadMock).toHaveBeenCalledWith('new-1', expect.any(File));
    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith('/indicacoes/new-1', { replace: true })
    );
  });

  it('admin: morador sem UUID bloqueia o submit', async () => {
    // Usuário sem unidade vê campo UUID diretamente ao marcar "é morador"
    setUserNoUnit();
    renderPage();
    await userEvent.type(screen.getByLabelText('Serviço'), 'Encanador Zé');
    await userEvent.click(screen.getByRole('checkbox', { name: /é morador/i }));
    await userEvent.click(screen.getByRole('button', { name: /criar indicação/i }));

    expect(toastError).toHaveBeenCalledWith('Informe o UUID do morador indicado.');
    expect(createMock).not.toHaveBeenCalled();
  });

  it('admin: morador com UUID cria como isResident com residentUserId', async () => {
    setUserNoUnit();
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

  it('morador: "esta indicação é minha" envia residentUserId=null', async () => {
    setUserWithUnit();
    renderPage();
    await userEvent.type(screen.getByLabelText('Serviço'), 'Pintor João');
    // Marca "é morador"
    await userEvent.click(screen.getByRole('checkbox', { name: /é morador/i }));
    // O checkbox "esta indicação é minha" deve aparecer e estar marcado por default
    const souEuCheckbox = screen.getByRole('checkbox', {
      name: /esta indicação é minha/i,
    });
    expect(souEuCheckbox).toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: /criar indicação/i }));

    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(1));
    expect(createMock).toHaveBeenCalledWith(
      expect.objectContaining({
        isResident: true,
        residentUserId: null, // "sou eu" → null → backend resolve pelo JWT
      })
    );
  });

  it('envia instagramUrl normalizado a partir de @handle', async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText('Serviço'), 'Pintor João');
    await userEvent.type(screen.getByLabelText('Instagram'), '@joaopintor');
    await userEvent.click(screen.getByRole('button', { name: /criar indicação/i }));

    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(1));
    expect(createMock).toHaveBeenCalledWith(
      expect.objectContaining({
        instagramUrl: 'https://instagram.com/joaopintor',
      })
    );
  });

  it('envia whatsappUrl normalizado a partir de número', async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText('Serviço'), 'Pintor João');
    await userEvent.type(screen.getByLabelText(/WhatsApp/), '+55 11 99999-0000');
    await userEvent.click(screen.getByRole('button', { name: /criar indicação/i }));

    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(1));
    expect(createMock).toHaveBeenCalledWith(
      expect.objectContaining({
        whatsappUrl: 'https://wa.me/5511999990000',
      })
    );
  });
});
