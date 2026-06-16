import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { IndependentNotice } from './IndependentNotice';

describe('IndependentNotice', () => {
  it('deixa claro que é app independente, sem vínculo com a gestão atual', () => {
    render(<IndependentNotice />);
    const note = screen.getByRole('note');
    expect(note).toHaveTextContent(/independente/i);
    expect(note).toHaveTextContent(/sem v[ií]nculo com a (atual )?gest[ãa]o/i);
  });

  it('identifica a controladora dos dados (WIZOR)', () => {
    render(<IndependentNotice />);
    expect(screen.getByRole('note')).toHaveTextContent(/WIZOR/);
  });
});
