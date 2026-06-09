import { describe, it, expect } from 'vitest';
import { isOpenNow, type OpeningHoursDto } from './openingHours';

const monday: OpeningHoursDto = {
  dayOfWeek: 1, // Monday
  opensAt: '08:00',
  closesAt: '18:00',
};

const hours = [monday];

describe('isOpenNow', () => {
  it('returns true when current time is inside the range', () => {
    expect(isOpenNow(hours, false, { dayOfWeek: 1, minutes: 12 * 60 })).toBe(true);
  });

  it('returns true exactly at opensAt', () => {
    expect(isOpenNow(hours, false, { dayOfWeek: 1, minutes: 8 * 60 })).toBe(true);
  });

  it('returns false exactly at closesAt (exclusive upper bound)', () => {
    expect(isOpenNow(hours, false, { dayOfWeek: 1, minutes: 18 * 60 })).toBe(false);
  });

  it('returns false before opensAt', () => {
    expect(isOpenNow(hours, false, { dayOfWeek: 1, minutes: 7 * 60 + 59 })).toBe(false);
  });

  it('returns false after closesAt', () => {
    expect(isOpenNow(hours, false, { dayOfWeek: 1, minutes: 18 * 60 + 1 })).toBe(false);
  });

  it('returns false when the day is not present in the hours array', () => {
    // Sunday (0) not in array
    expect(isOpenNow(hours, false, { dayOfWeek: 0, minutes: 12 * 60 })).toBe(false);
  });

  it('returns true when is24h is true regardless of time or day', () => {
    expect(isOpenNow([], true, { dayOfWeek: 0, minutes: 0 })).toBe(true);
    expect(isOpenNow([], true, { dayOfWeek: 6, minutes: 23 * 60 + 59 })).toBe(true);
    // Even with hours that would say closed
    expect(isOpenNow(hours, true, { dayOfWeek: 0, minutes: 0 })).toBe(true);
  });

  it('returns false when day entry exists but opensAt/closesAt are null', () => {
    const noTime: OpeningHoursDto[] = [{ dayOfWeek: 1, opensAt: null, closesAt: null }];
    expect(isOpenNow(noTime, false, { dayOfWeek: 1, minutes: 12 * 60 })).toBe(false);
  });
});
