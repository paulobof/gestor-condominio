import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { IndependentNotice } from './IndependentNotice';

describe('IndependentNotice', () => {
  it('diz que é aplicativo independente, sem vínculo oficial com quem administra', () => {
    render(<IndependentNotice />);
    const note = screen.getByRole('note');
    expect(note).toHaveTextContent(/independente/i);
    expect(note).toHaveTextContent(/sem v[ií]nculo oficial/i);
    expect(note).toHaveTextContent(/administra[çc][ãa]o/i);
    expect(note).toHaveTextContent(/s[íi]ndico/i);
    expect(note).toHaveTextContent(/conselho/i);
  });

  it('não usa tom de alerta — nada de ícone de aviso nem fundo âmbar', () => {
    const { container } = render(<IndependentNotice />);
    expect(container.querySelector('svg')).toBeNull();
    expect(screen.getByRole('note').className).not.toMatch(/amber/);
  });

  it('não repete a controladora dos dados — isso vive na política de privacidade', () => {
    render(<IndependentNotice />);
    expect(screen.getByRole('note')).not.toHaveTextContent(/WIZOR/i);
  });
});
