import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';

vi.mock('@/components/UnitSelector', () => ({
  UnitSelector: ({ onChange }: { onChange: (c: string, h: boolean) => void }) => (
    <button onClick={() => onChange('602C', false)}>pick-unit</button>
  ),
}));
vi.mock('@/components/ProofUploader', () => ({
  ProofUploader: ({ onChange }: { onChange: (f: File) => void }) => (
    <button onClick={() => onChange(new File(['x'], 'p.jpg', { type: 'image/jpeg' }))}>
      pick-proof
    </button>
  ),
}));
vi.mock('../api/unitClaimsApi', () => ({ createUnitClaim: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { RegisterExtraUnitPage } from './RegisterExtraUnitPage';
import { createUnitClaim } from '../api/unitClaimsApi';

const createMock = vi.mocked(createUnitClaim);

describe('RegisterExtraUnitPage', () => {
  it('botão fica desabilitado sem unidade/comprovante', () => {
    render(<RegisterExtraUnitPage />);
    expect(screen.getByRole('button', { name: /enviar pedido/i })).toBeDisabled();
  });

  it('submete o pedido de unidade extra via FormData e mostra estado pendente', async () => {
    const user = userEvent.setup();
    createMock.mockResolvedValue({} as never);
    render(<RegisterExtraUnitPage />);

    await user.click(screen.getByRole('button', { name: 'pick-unit' }));
    await user.click(screen.getByRole('button', { name: 'pick-proof' }));
    await user.click(screen.getByRole('button', { name: /enviar pedido/i }));

    expect(createMock).toHaveBeenCalledTimes(1);
    const fd = createMock.mock.calls[0][0] as FormData;
    expect(fd.get('unitCode')).toBe('602C');
    expect(fd.get('proof')).toBeInstanceOf(File);
    expect(await screen.findByText(/pendente de aprovação/i)).toBeInTheDocument();
  });
});
