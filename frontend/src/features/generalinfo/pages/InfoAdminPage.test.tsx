import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/generalInfoApi', () => ({
  listSections: vi.fn(),
  createSection: vi.fn(),
  updateSection: vi.fn(),
  reorderSections: vi.fn(),
  deleteSection: vi.fn(),
}));

// Substitui o editor TipTap por um textarea simples no teste.
vi.mock('@/components/richtext/RichTextEditor', () => ({
  RichTextEditor: ({ value, onChange }: { value: string; onChange: (v: string) => void }) => (
    <textarea aria-label="Conteúdo" value={value} onChange={(e) => onChange(e.target.value)} />
  ),
}));

import { InfoAdminPage } from './InfoAdminPage';
import { listSections, createSection, reorderSections } from '../api/generalInfoApi';

const a = { id: 'a', title: 'Portaria', body: '<p>p</p>', position: 0, updatedAt: '' };
const b = { id: 'b', title: 'Regras', body: '<p>r</p>', position: 1, updatedAt: '' };

function renderPage() {
  return render(
    <MemoryRouter>
      <InfoAdminPage />
    </MemoryRouter>
  );
}

describe('InfoAdminPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listSections).mockResolvedValue([a, b]);
    vi.mocked(createSection).mockResolvedValue({ ...a, id: 'new' });
    vi.mocked(reorderSections).mockResolvedValue(undefined);
  });

  it('cria uma seção chamando createSection', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Portaria');

    await user.type(screen.getByLabelText('Título'), 'Nova seção');
    await user.type(screen.getByLabelText('Conteúdo'), '<p>oi</p>');
    await user.click(screen.getByRole('button', { name: /salvar/i }));

    await waitFor(() =>
      expect(createSection).toHaveBeenCalledWith({ title: 'Nova seção', body: '<p>oi</p>' })
    );
  });

  it('reordena para baixo chamando reorderSections', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Portaria');

    // Botão "descer" da primeira seção troca posição com a segunda.
    await user.click(screen.getAllByRole('button', { name: '↓' })[0]);

    await waitFor(() =>
      expect(reorderSections).toHaveBeenCalledWith([
        { id: 'a', position: 1 },
        { id: 'b', position: 0 },
      ])
    );
  });
});
