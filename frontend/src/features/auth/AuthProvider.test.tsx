import { act, render, screen, waitFor } from '@testing-library/react';
import { useContext } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('./api/authApi', () => ({
  refresh: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  me: vi.fn(),
}));
vi.mock('@/lib/api', () => ({
  setAccessToken: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

import * as authApi from './api/authApi';
import { AuthContext, AuthProvider } from './AuthProvider';

const user = {
  id: 'u1',
  fullName: 'Ana Silva',
  greetingName: 'Ana',
  email: 'ana@x.com',
  unitId: null,
  isUnitMaster: false,
  roles: [],
  authorities: [],
  mustChangePassword: false,
};

let ctx: React.ContextType<typeof AuthContext> = null;

function Probe() {
  ctx = useContext(AuthContext);
  return <span>{ctx?.user?.greetingName ?? 'sem-usuario'}</span>;
}

beforeEach(() => {
  vi.clearAllMocks();
  ctx = null;
});

describe('AuthProvider.refreshUser', () => {
  it('recarrega o usuário logado via /auth/me', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({ accessToken: 't', user });
    vi.mocked(authApi.me).mockResolvedValue({ ...user, greetingName: 'Aninha' });

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    );
    expect(await screen.findByText('Ana')).toBeInTheDocument();

    await act(async () => {
      await ctx?.refreshUser?.();
    });

    await waitFor(() => expect(screen.getByText('Aninha')).toBeInTheDocument());
    expect(authApi.me).toHaveBeenCalledTimes(1);
  });
});
