import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { CatalogSelects } from './CatalogSelects';
import type { Catalog } from '../api/complaintsApi';

const CATALOG: Catalog = {
  types: [
    {
      id: 't1',
      name: 'Manutenção',
      active: true,
      sortOrder: 1,
      subtypes: [{ id: 's1', name: 'Pintura', active: true, sortOrder: 1 }],
    },
    {
      id: 't2',
      name: 'Limpeza',
      active: true,
      sortOrder: 2,
      subtypes: [{ id: 's2', name: 'Garagem', active: true, sortOrder: 1 }],
    },
  ],
  locations: [{ id: 'l1', name: 'Torre 1', active: true, sortOrder: 1 }],
};

describe('CatalogSelects', () => {
  it('mostra só subtipos do tipo escolhido e zera o subtipo ao trocar o tipo', async () => {
    const onChange = vi.fn();
    const { rerender } = render(
      <CatalogSelects
        catalog={CATALOG}
        value={{ typeId: 't1', subtypeId: 's1', locationId: '' }}
        onChange={onChange}
      />
    );
    expect(screen.getByRole('option', { name: 'Pintura' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Garagem' })).not.toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('Tipo'), 't2');
    expect(onChange).toHaveBeenCalledWith({ typeId: 't2', subtypeId: '', locationId: '' });

    rerender(
      <CatalogSelects
        catalog={CATALOG}
        value={{ typeId: 't2', subtypeId: '', locationId: '' }}
        onChange={onChange}
      />
    );
    expect(screen.getByRole('option', { name: 'Garagem' })).toBeInTheDocument();
  });

  it('inclui o item atual inativo via fallback', () => {
    render(
      <CatalogSelects
        catalog={CATALOG}
        value={{ typeId: 'old', subtypeId: 'olds', locationId: 'l1' }}
        onChange={vi.fn()}
        fallback={{ typeId: 'old', typeName: 'Antigo', subtypeId: 'olds', subtypeName: 'Velho' }}
      />
    );
    expect(screen.getByRole('option', { name: 'Antigo (inativo)' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Velho (inativo)' })).toBeInTheDocument();
  });
});
