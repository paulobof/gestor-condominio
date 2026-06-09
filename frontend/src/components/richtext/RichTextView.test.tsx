import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { RichTextView } from './RichTextView';

describe('RichTextView', () => {
  it('renderiza formatação básica', () => {
    render(<RichTextView html="<p><strong>Portaria</strong> 24h</p>" />);
    expect(screen.getByText('Portaria').tagName).toBe('STRONG');
  });

  it('remove script perigoso', () => {
    const { container } = render(<RichTextView html={'<p>ok</p><script>window.x=1</script>'} />);
    expect(container.querySelector('script')).toBeNull();
    expect(container.textContent).toContain('ok');
  });

  it('remove handlers inline', () => {
    const { container } = render(<RichTextView html={'<p onclick="x()">t</p>'} />);
    expect(container.querySelector('p')?.getAttribute('onclick')).toBeNull();
  });
});
