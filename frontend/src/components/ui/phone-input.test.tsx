import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, it, expect, vi } from 'vitest';
import { PhoneInput } from './phone-input';

/** Wrapper controlado para exercitar o onChange como num formulário real. */
function Harness({ initial = '', onValue }: { initial?: string; onValue: (v: string) => void }) {
  const [value, setValue] = useState(initial);
  return (
    <PhoneInput
      id="phone"
      value={value}
      onChange={(v) => {
        setValue(v);
        onValue(v);
      }}
    />
  );
}

describe('PhoneInput', () => {
  it('começa com DDI +55 (Brasil)', () => {
    render(<Harness onValue={vi.fn()} />);
    expect(screen.getByLabelText('Código do país (DDI)')).toHaveValue('55');
  });

  it('digitar o número nacional emite dígitos com DDI', async () => {
    const onValue = vi.fn();
    render(<Harness onValue={onValue} />);
    await userEvent.type(document.getElementById('phone') as HTMLInputElement, '11988887777');
    expect(onValue).toHaveBeenLastCalledWith('5511988887777');
  });

  it('aplica a máscara BR no campo', async () => {
    render(<Harness onValue={vi.fn()} />);
    const input = document.getElementById('phone') as HTMLInputElement;
    await userEvent.type(input, '11988887777');
    expect(input).toHaveValue('(11) 98888-7777');
  });

  it('trocar o DDI mantém o número e re-emite com o novo DDI', async () => {
    const onValue = vi.fn();
    render(<Harness onValue={onValue} />);
    const input = document.getElementById('phone') as HTMLInputElement;
    await userEvent.type(input, '912345678');
    await userEvent.selectOptions(screen.getByLabelText('Código do país (DDI)'), '351');
    expect(onValue).toHaveBeenLastCalledWith('351912345678');
  });

  it('carrega um value existente já formatado', () => {
    render(<Harness initial="5511988887777" onValue={vi.fn()} />);
    expect(document.getElementById('phone')).toHaveValue('(11) 98888-7777');
    expect(screen.getByLabelText('Código do país (DDI)')).toHaveValue('55');
  });
});
