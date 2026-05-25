import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import App from './App';

describe('App', () => {
  it('renderiza o nome do condominio', () => {
    render(<App />);
    expect(screen.getByText(/helbor trilogy home/i)).toBeInTheDocument();
  });

  it('mostra status do backend (placeholder)', () => {
    render(<App />);
    expect(screen.getByTestId('app-status')).toBeInTheDocument();
  });
});
