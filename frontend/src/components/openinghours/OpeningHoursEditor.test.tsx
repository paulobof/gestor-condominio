import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { OpeningHoursEditor } from './OpeningHoursEditor';
import type { OpeningHoursDto } from './openingHours';

describe('OpeningHoursEditor', () => {
  it('toggling "Aberto 24 horas" ON calls onChange with ([], true)', async () => {
    const onChange = vi.fn();
    render(<OpeningHoursEditor value={[]} is24h={false} onChange={onChange} />);

    const toggle = screen.getByRole('checkbox', { name: /aberto 24 horas/i });
    await userEvent.click(toggle);

    expect(onChange).toHaveBeenCalledWith([], true);
  });

  it('toggling "Aberto 24 horas" OFF calls onChange with (value, false)', async () => {
    const onChange = vi.fn();
    const existingHours: OpeningHoursDto[] = [
      { dayOfWeek: 1, opensAt: '09:00', closesAt: '17:00' },
    ];
    render(<OpeningHoursEditor value={existingHours} is24h={true} onChange={onChange} />);

    const toggle = screen.getByRole('checkbox', { name: /aberto 24 horas/i });
    await userEvent.click(toggle);

    expect(onChange).toHaveBeenCalledWith(existingHours, false);
  });

  it('checking a day "Aberto" and typing opens/closes calls onChange with correct DTO', async () => {
    const onChange = vi.fn();
    render(<OpeningHoursEditor value={[]} is24h={false} onChange={onChange} />);

    // Enable Monday (dayOfWeek=1), label "Segunda"
    const mondayCheckbox = screen.getByRole('checkbox', { name: /segunda/i });
    await userEvent.click(mondayCheckbox);

    // Now the time inputs should be enabled — fill them
    const opensInput = screen.getByLabelText(/abre.*segunda|segunda.*abre/i);
    const closesInput = screen.getByLabelText(/fecha.*segunda|segunda.*fecha/i);

    await userEvent.clear(opensInput);
    await userEvent.type(opensInput, '08:00');
    await userEvent.clear(closesInput);
    await userEvent.type(closesInput, '18:00');

    // Find the last call and check it contains the expected entry
    const calls = onChange.mock.calls;
    const lastCall = calls[calls.length - 1];
    const [hoursArg, is24hArg] = lastCall as [OpeningHoursDto[], boolean];

    expect(is24hArg).toBe(false);
    const mondayEntry = hoursArg.find((h) => h.dayOfWeek === 1);
    expect(mondayEntry).toBeDefined();
    expect(mondayEntry?.opensAt).toBe('08:00');
    expect(mondayEntry?.closesAt).toBe('18:00');
  });
});
