import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

import { ProtectedRoute } from './ProtectedRoute';
import { useAuth } from '@/features/auth/useAuth';
import { LoginPromptContext } from '@/features/auth/LoginPromptProvider';

const useAuthMock = vi.mocked(useAuth);

function renderAt(
  path: string,
  status: 'loading' | 'authenticated' | 'unauthenticated',
  promptLogin = vi.fn()
) {
  useAuthMock.mockReturnValue({ status } as never);
  render(
    <MemoryRouter initialEntries={[path]}>
      <LoginPromptContext.Provider value={{ promptLogin }}>
        <Routes>
          <Route path="/" element={<div>home pública</div>} />
          <Route
            path="/avisos"
            element={
              <ProtectedRoute>
                <div>avisos</div>
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<div>tela de login</div>} />
        </Routes>
      </LoginPromptContext.Provider>
    </MemoryRouter>
  );
  return promptLogin;
}

beforeEach(() => vi.clearAllMocks());

describe('ProtectedRoute', () => {
  it('visitante em área que exige conta volta para a home e o login é pedido no popup', () => {
    const promptLogin = renderAt('/avisos', 'unauthenticated');

    // Nenhuma tela de senha: a pessoa fica no portal e o popup pede a conta por cima.
    expect(screen.getByText('home pública')).toBeInTheDocument();
    expect(screen.queryByText('tela de login')).not.toBeInTheDocument();
    expect(promptLogin).toHaveBeenCalledWith(expect.objectContaining({ destination: '/avisos' }));
  });

  it('autenticado entra direto na área', () => {
    const promptLogin = renderAt('/avisos', 'authenticated');
    expect(screen.getByText('avisos')).toBeInTheDocument();
    expect(promptLogin).not.toHaveBeenCalled();
  });
});
