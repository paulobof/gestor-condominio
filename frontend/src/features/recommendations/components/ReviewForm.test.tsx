import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';

import { ReviewForm } from './ReviewForm';

describe('ReviewForm', () => {
  it('desabilita o botão de publicar com menos de 10 caracteres', async () => {
    render(<ReviewForm onSubmit={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: '5 estrelas' }));
    await userEvent.type(screen.getByLabelText('Comentário'), 'curto');

    expect(screen.getByRole('button', { name: /publicar avaliação/i })).toBeDisabled();
  });

  it('habilita o botão assim que o comentário chega a 10 caracteres', async () => {
    render(<ReviewForm onSubmit={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: '5 estrelas' }));
    await userEvent.type(screen.getByLabelText('Comentário'), '1234567890');

    expect(screen.getByRole('button', { name: /publicar avaliação/i })).toBeEnabled();
  });

  it('escolher estrelas altera o valor enviado no onSubmit', async () => {
    const onSubmit = vi.fn();
    render(<ReviewForm onSubmit={onSubmit} />);

    await userEvent.click(screen.getByRole('button', { name: '4 estrelas' }));
    await userEvent.type(
      screen.getByLabelText('Comentário'),
      'Muito bom atendimento, recomendo bastante.'
    );
    await userEvent.click(screen.getByRole('button', { name: /publicar avaliação/i }));

    expect(onSubmit).toHaveBeenCalledWith({
      sentiment: 'LIKE',
      rating: 4,
      text: 'Muito bom atendimento, recomendo bastante.',
    });
  });

  it('onSubmit recebe { sentiment, rating, text } ao escolher não recomendo', async () => {
    const onSubmit = vi.fn();
    render(<ReviewForm onSubmit={onSubmit} />);

    await userEvent.click(screen.getByRole('button', { name: /não recomendo/i }));
    await userEvent.click(screen.getByRole('button', { name: '2 estrelas' }));
    await userEvent.type(
      screen.getByLabelText('Comentário'),
      'Não gostei do atendimento recebido.'
    );
    await userEvent.click(screen.getByRole('button', { name: /publicar avaliação/i }));

    expect(onSubmit).toHaveBeenCalledWith({
      sentiment: 'DISLIKE',
      rating: 2,
      text: 'Não gostei do atendimento recebido.',
    });
  });

  it('sem nota escolhida o botão de publicar fica desabilitado mesmo com texto válido', () => {
    render(<ReviewForm onSubmit={vi.fn()} />);
    expect(screen.getByRole('button', { name: /publicar avaliação/i })).toBeDisabled();
  });

  it('pré-preenche com initialValues (edição) e chama onCancel', async () => {
    const onCancel = vi.fn();
    render(
      <ReviewForm
        onSubmit={vi.fn()}
        onCancel={onCancel}
        submitLabel="Salvar avaliação"
        initialValues={{ sentiment: 'DISLIKE', rating: 3, text: 'Comentário original aqui.' }}
      />
    );

    expect(screen.getByRole('button', { name: '3 estrelas' })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
    expect(screen.getByLabelText('Comentário')).toHaveValue('Comentário original aqui.');
    expect(screen.getByRole('button', { name: 'Salvar avaliação' })).toBeEnabled();

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(onCancel).toHaveBeenCalled();
  });
});
