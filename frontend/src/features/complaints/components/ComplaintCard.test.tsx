import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/complaintsApi', async (orig) => {
  const actual = await orig<typeof import('../api/complaintsApi')>();
  return { ...actual, getComplaintPhotoUrl: vi.fn() };
});

import { ComplaintCard } from './ComplaintCard';
import { getComplaintPhotoUrl, type Complaint } from '../api/complaintsApi';

const base: Complaint = {
  id: 'c1',
  code: 123,
  typeId: 't1',
  typeName: 'Manutenção',
  subtypeId: 's1',
  subtypeName: 'Pintura',
  locationId: 'l1',
  locationName: 'Torre 2',
  locationDetail: null,
  description: 'Parede descascando',
  status: 'FORWARDED',
  dueDate: null,
  rejectionReason: null,
  resolutionNote: null,
  syndicResponse: null,
  syndicResponseAt: null,
  createdAt: '2026-10-10T12:00:00Z',
  forwardedAt: '2026-10-17T12:00:00Z',
  resolvedAt: null,
  editable: false,
  photos: [],
};

function renderCard(c: Complaint, showThumbnail = true) {
  return render(
    <MemoryRouter>
      <ul>
        <ComplaintCard c={c} to="/reclamacoes/c1" showThumbnail={showThumbnail} />
      </ul>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getComplaintPhotoUrl).mockResolvedValue('http://minio/thumb.jpg');
});

describe('ComplaintCard', () => {
  it('mostra a miniatura da primeira foto, com alt e lazy loading', async () => {
    renderCard({
      ...base,
      photos: [
        { id: 'p2', ordering: 1, contentType: 'image/jpeg' },
        { id: 'p1', ordering: 0, contentType: 'image/jpeg' },
      ],
    });
    const img = await screen.findByRole('img', { name: 'Foto da reclamação #123' });
    expect(img).toHaveAttribute('src', 'http://minio/thumb.jpg');
    expect(img).toHaveAttribute('loading', 'lazy');
    expect(img).toHaveAttribute('referrerpolicy', 'no-referrer');
    expect(getComplaintPhotoUrl).toHaveBeenCalledTimes(1);
    expect(getComplaintPhotoUrl).toHaveBeenCalledWith('c1', 'p1');
  });

  it('sem fotos não busca URL nem mostra imagem', () => {
    renderCard(base);
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(getComplaintPhotoUrl).not.toHaveBeenCalled();
  });

  it('sem showThumbnail não mostra miniatura', () => {
    renderCard({ ...base, photos: [{ id: 'p1', ordering: 0, contentType: 'image/jpeg' }] }, false);
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(getComplaintPhotoUrl).not.toHaveBeenCalled();
  });

  it('falha ao buscar a URL não quebra o card', async () => {
    vi.mocked(getComplaintPhotoUrl).mockRejectedValue(new Error('403'));
    renderCard({ ...base, photos: [{ id: 'p1', ordering: 0, contentType: 'image/jpeg' }] });
    expect(await screen.findByText('Manutenção › Pintura')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });
});
