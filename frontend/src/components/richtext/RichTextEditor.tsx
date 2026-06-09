import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Link from '@tiptap/extension-link';
import { useEffect } from 'react';
import { Bold, Italic, List, ListOrdered, Link as LinkIcon } from 'lucide-react';

interface Props {
  value: string;
  onChange: (html: string) => void;
}

const BTN =
  'inline-flex h-9 min-w-9 items-center justify-center rounded-md border border-border ' +
  'text-sm hover:bg-accent data-[active=true]:bg-accent data-[active=true]:text-accent-foreground';

/** Editor rich text mínimo (negrito, itálico, listas, link). Emite HTML em onChange. */
export function RichTextEditor({ value, onChange }: Props) {
  const editor = useEditor({
    extensions: [StarterKit, Link.configure({ openOnClick: false })],
    content: value,
    onUpdate: ({ editor }) => onChange(editor.getHTML()),
    editorProps: {
      attributes: {
        class:
          'prose prose-sm max-w-none min-h-[140px] rounded-md border border-input bg-background ' +
          'px-3 py-2 focus:outline-none focus-visible:ring-2 focus-visible:ring-ring',
      },
    },
  });

  // Sincroniza quando o valor externo muda (ex.: abrir outro item para editar).
  useEffect(() => {
    if (editor && value !== editor.getHTML()) {
      editor.commands.setContent(value, { emitUpdate: false });
    }
  }, [value, editor]);

  if (!editor) return null;

  const setLink = () => {
    const url = window.prompt('URL do link (https://, tel:, mailto:)') ?? '';
    if (url === '') {
      editor.chain().focus().unsetLink().run();
    } else {
      editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
    }
  };

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1" role="toolbar" aria-label="Formatação">
        <button
          type="button"
          aria-label="Negrito"
          className={BTN}
          data-active={editor.isActive('bold')}
          onClick={() => editor.chain().focus().toggleBold().run()}
        >
          <Bold className="h-4 w-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label="Itálico"
          className={BTN}
          data-active={editor.isActive('italic')}
          onClick={() => editor.chain().focus().toggleItalic().run()}
        >
          <Italic className="h-4 w-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label="Lista com marcadores"
          className={BTN}
          data-active={editor.isActive('bulletList')}
          onClick={() => editor.chain().focus().toggleBulletList().run()}
        >
          <List className="h-4 w-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label="Lista numerada"
          className={BTN}
          data-active={editor.isActive('orderedList')}
          onClick={() => editor.chain().focus().toggleOrderedList().run()}
        >
          <ListOrdered className="h-4 w-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label="Link"
          className={BTN}
          data-active={editor.isActive('link')}
          onClick={setLink}
        >
          <LinkIcon className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
      <EditorContent editor={editor} />
    </div>
  );
}
