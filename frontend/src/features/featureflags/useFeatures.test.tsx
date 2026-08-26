import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('axios', () => ({ default: { get: vi.fn() } }));

import axios from 'axios';
import { FeaturesProvider } from './FeaturesProvider';
import { useFeatures } from './useFeatures';

const get = vi.mocked(axios.get);

function Probe() {
  const { enabled, loading } = useFeatures();
  return (
    <ul>
      <li>{loading ? 'carregando' : 'pronto'}</li>
      <li>avisos: {String(enabled('announcements'))}</li>
      <li>vagas: {String(enabled('parkingrental'))}</li>
    </ul>
  );
}

beforeEach(() => vi.clearAllMocks());

describe('useFeatures', () => {
  it('reflete o que o backend diz estar ligado', async () => {
    get.mockResolvedValue({ data: { announcements: true, parkingrental: false } });

    render(
      <FeaturesProvider>
        <Probe />
      </FeaturesProvider>
    );

    await waitFor(() => expect(screen.getByText('pronto')).toBeInTheDocument());
    expect(screen.getByText('avisos: true')).toBeInTheDocument();
    expect(screen.getByText('vagas: false')).toBeInTheDocument();
  });

  it('falha de rede assume tudo ligado — o padrão é rodar', async () => {
    get.mockRejectedValue(new Error('offline'));

    render(
      <FeaturesProvider>
        <Probe />
      </FeaturesProvider>
    );

    await waitFor(() => expect(screen.getByText('pronto')).toBeInTheDocument());
    expect(screen.getByText('avisos: true')).toBeInTheDocument();
    expect(screen.getByText('vagas: true')).toBeInTheDocument();
  });

  it('fora do provider assume tudo ligado (o backend é quem barra de verdade)', () => {
    render(<Probe />);
    expect(screen.getByText('avisos: true')).toBeInTheDocument();
    expect(screen.getByText('vagas: true')).toBeInTheDocument();
  });
});
