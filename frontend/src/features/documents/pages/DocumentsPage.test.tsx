import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../api/documentsApi', () => ({
  listDocuments: vi.fn(),
  uploadDocument: vi.fn(),
  getDocumentBlob: vi.fn(),
  deleteDocument: vi.fn(),
}));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { DocumentsPage } from './DocumentsPage';
import {
  listDocuments,
  uploadDocument,
  getDocumentBlob,
  type DocumentItem,
} from '../api/documentsApi';
import { useAuth } from '@/features/auth/useAuth';

const listMock = vi.mocked(listDocuments);
const uploadMock = vi.mocked(uploadDocument);
const getBlobMock = vi.mocked(getDocumentBlob);
const authMock = vi.mocked(useAuth);

function setUser(authorities: string[]) {
  authMock.mockReturnValue({ user: { authorities } } as never);
}

const doc: DocumentItem = {
  id: 'd1',
  title: 'Regimento Interno 2026',
  type: 'RI',
  filename: 'ri.pdf',
  contentType: 'application/pdf',
  sizeBytes: 1234,
  uploadedByUserId: 'u1',
  createdAt: '2026-06-15T00:00:00Z',
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('DocumentsPage', () => {
  it('lista documentos para qualquer autenticado, sem o formulário de upload', async () => {
    setUser([]); // sem DOCUMENT_MANAGE
    listMock.mockResolvedValue([doc]);

    render(<DocumentsPage />);

    expect(await screen.findByText('Regimento Interno 2026')).toBeInTheDocument();
    expect(screen.getByText('Regimento Interno')).toBeInTheDocument(); // label do tipo
    expect(screen.queryByText('Enviar documento')).not.toBeInTheDocument();
  });

  it('mostra estado vazio', async () => {
    setUser([]);
    listMock.mockResolvedValue([]);

    render(<DocumentsPage />);

    expect(await screen.findByText('Nenhum documento publicado.')).toBeInTheDocument();
  });

  it('exibe o formulário de upload apenas com DOCUMENT_MANAGE', async () => {
    setUser(['DOCUMENT_MANAGE']);
    listMock.mockResolvedValue([]);

    render(<DocumentsPage />);

    expect(await screen.findByText('Enviar documento')).toBeInTheDocument();
  });

  it('envia um documento PDF e recarrega a lista', async () => {
    const user = userEvent.setup();
    setUser(['DOCUMENT_MANAGE']);
    listMock.mockResolvedValue([]);
    uploadMock.mockResolvedValue(doc);

    render(<DocumentsPage />);
    await screen.findByText('Enviar documento');

    await user.type(screen.getByLabelText('Título'), 'Regimento Interno 2026');
    await user.selectOptions(screen.getByLabelText('Tipo'), 'RI');
    const file = new File(['%PDF-1.4'], 'ri.pdf', { type: 'application/pdf' });
    await user.upload(screen.getByLabelText(/Arquivo/), file);
    await user.click(screen.getByRole('button', { name: /Enviar/ }));

    await waitFor(() =>
      expect(uploadMock).toHaveBeenCalledWith('Regimento Interno 2026', 'RI', file)
    );
    // recarrega após enviar (load inicial + após upload)
    await waitFor(() => expect(listMock).toHaveBeenCalledTimes(2));
  });

  it('baixa um documento via blob autenticado (não URL pública)', async () => {
    const user = userEvent.setup();
    setUser([]);
    listMock.mockResolvedValue([doc]);
    const blob = new Blob(['x'], { type: 'application/pdf' });
    getBlobMock.mockResolvedValue(blob);

    const createObjectURL = vi.fn(() => 'blob:fake');
    const revokeObjectURL = vi.fn();
    URL.createObjectURL = createObjectURL as never;
    URL.revokeObjectURL = revokeObjectURL as never;
    const open = vi.fn();
    window.open = open as never;

    render(<DocumentsPage />);
    await screen.findByText('Regimento Interno 2026');

    await user.click(screen.getByRole('button', { name: /Baixar/ }));

    await waitFor(() => expect(getBlobMock).toHaveBeenCalledWith('d1'));
    expect(createObjectURL).toHaveBeenCalledWith(blob);
    expect(open).toHaveBeenCalledWith('blob:fake', '_blank', expect.any(String));
  });
});
