import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

import { ProtectedRoute } from './ProtectedRoute';
import { useAuth } from '@/features/auth/useAuth';

const useAuthMock = vi.mocked(useAuth);

function renderAt(path: string, status: 'loading' | 'authenticated' | 'unauthenticated') {
  useAuthMock.mockReturnValue({ status } as never);
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <div>app interno</div>
            </ProtectedRoute>
          }
        />
        <Route
          path="/avisos"
          element={
            <ProtectedRoute>
              <div>avisos</div>
            </ProtectedRoute>
          }
        />
        <Route path="/sobre" element={<div>apresentação</div>} />
        <Route path="/login" element={<div>tela de login</div>} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => vi.clearAllMocks());

describe('ProtectedRoute', () => {
  it('visitante na entrada vê a apresentação, não o login', () => {
    renderAt('/', 'unauthenticated');
    expect(screen.getByText('apresentação')).toBeInTheDocument();
    expect(screen.queryByText('tela de login')).not.toBeInTheDocument();
  });

  it('visitante em rota interna profunda vai para o login', () => {
    renderAt('/avisos', 'unauthenticated');
    expect(screen.getByText('tela de login')).toBeInTheDocument();
  });

  it('autenticado entra direto no app', () => {
    renderAt('/', 'authenticated');
    expect(screen.getByText('app interno')).toBeInTheDocument();
  });
});
