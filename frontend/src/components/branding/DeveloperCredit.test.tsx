import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { DeveloperCredit } from './DeveloperCredit';

describe('DeveloperCredit', () => {
  it('mostra o crédito da Wizortech', () => {
    render(<DeveloperCredit />);
    expect(screen.getByText(/Wizortech/)).toBeInTheDocument();
    expect(screen.getByText(/Desenvolvido por/i)).toBeInTheDocument();
  });

  it('linka para site, e-mail e WhatsApp', () => {
    render(<DeveloperCredit />);

    const site = screen.getByRole('link', { name: /site/i });
    expect(site).toHaveAttribute('href', 'https://wizortech.com.br/');

    const email = screen.getByRole('link', { name: /e-mail/i });
    expect(email).toHaveAttribute('href', 'mailto:contato@wizortech.com.br');

    const whatsapp = screen.getByRole('link', { name: /whatsapp/i });
    expect(whatsapp).toHaveAttribute(
      'href',
      'https://api.whatsapp.com/send/?phone=551145801261&text&type=phone_number&app_absent=0'
    );
  });

  it('abre links externos com rel de segurança', () => {
    render(<DeveloperCredit />);
    for (const name of [/site/i, /whatsapp/i]) {
      const link = screen.getByRole('link', { name });
      expect(link).toHaveAttribute('target', '_blank');
      expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    }
  });
});
