import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import App from './App';
import { AuthContext } from './features/auth/AuthProvider';

const mockAuth = {
  status: 'authenticated' as const,
  user: {
    id: 'u1',
    fullName: 'Paulo Teste',
    greetingName: 'Paulo',
    email: 'paulo@test.com',
    unitId: null,
    isUnitMaster: false,
    roles: ['MANAGER'],
    authorities: ['USER_VIEW'],
    mustChangePassword: false,
  },
  login: vi.fn(),
  logout: vi.fn(),
};

describe('App', () => {
  it('renderiza o nome do condominio', () => {
    render(
      <AuthContext.Provider value={mockAuth}>
        <App />
      </AuthContext.Provider>
    );
    expect(screen.getByText(/helbor trilogy home/i)).toBeInTheDocument();
  });

  it('mostra greeting name do usuario logado', () => {
    render(
      <AuthContext.Provider value={mockAuth}>
        <App />
      </AuthContext.Provider>
    );
    expect(screen.getByText(/paulo/i)).toBeInTheDocument();
  });
});
