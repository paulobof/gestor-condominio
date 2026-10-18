import { useCallback, useEffect, useId, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft, Pencil } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  adminCatalog,
  backendMessage,
  createLocation,
  createSubtype,
  createType,
  updateLocation,
  updateSubtype,
  updateType,
  type Catalog,
  type CatalogItem,
  type CatalogItemBody,
} from '../api/complaintsApi';

const nextOrder = (items: CatalogItem[]) =>
  items.reduce((max, i) => Math.max(max, i.sortOrder), 0) + 1;

/** Linha de um item: renomear inline e ativar/desativar. */
function ItemRow({
  item,
  onSave,
}: {
  item: CatalogItem;
  onSave: (body: CatalogItemBody) => Promise<void>;
}) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(item.name);

  return (
    <li className="flex min-h-[44px] flex-wrap items-center gap-2">
      {editing ? (
        <>
          <Input
            aria-label={`Novo nome de ${item.name}`}
            value={name}
            maxLength={80}
            onChange={(e) => setName(e.target.value)}
            className="max-w-xs"
          />
          <Button
            className="min-h-[44px]"
            onClick={async () => {
              await onSave({ name: name.trim(), sortOrder: item.sortOrder, active: item.active });
              setEditing(false);
            }}
            disabled={!name.trim()}
          >
            Salvar
          </Button>
        </>
      ) : (
        <>
          <span className={item.active ? '' : 'text-muted-foreground line-through'}>
            {item.name}
          </span>
          <Button
            variant="ghost"
            className="min-h-[44px]"
            aria-label={`Renomear ${item.name}`}
            onClick={() => setEditing(true)}
          >
            <Pencil aria-hidden="true" />
          </Button>
        </>
      )}
      <Button
        variant="outline"
        className="min-h-[44px]"
        aria-label={`${item.active ? 'Desativar' : 'Ativar'} ${item.name}`}
        onClick={() => onSave({ name: item.name, sortOrder: item.sortOrder, active: !item.active })}
      >
        {item.active ? 'Desativar' : 'Ativar'}
      </Button>
    </li>
  );
}

/** Campo + botão para adicionar um item. */
function AddItem({
  label,
  button,
  onAdd,
}: {
  label: string;
  button: string;
  onAdd: (name: string) => Promise<boolean>;
}) {
  const [name, setName] = useState('');
  const id = useId(); // "Novo subtipo" se repete por tipo: id precisa ser único
  return (
    <div className="flex flex-wrap items-end gap-2">
      <div className="space-y-1">
        <Label htmlFor={id}>{label}</Label>
        <Input id={id} value={name} maxLength={80} onChange={(e) => setName(e.target.value)} />
      </div>
      <Button
        className="min-h-[44px]"
        disabled={!name.trim()}
        onClick={async () => {
          if (await onAdd(name.trim())) setName('');
        }}
      >
        {button}
      </Button>
    </div>
  );
}

export function ComplaintCatalogPage() {
  const [catalog, setCatalog] = useState<Catalog | null>(null);

  const load = useCallback(() => {
    adminCatalog()
      .then(setCatalog)
      .catch(() => toast.error('Erro ao carregar o catálogo.'));
  }, []);

  useEffect(load, [load]);

  /** Executa, recarrega e devolve se deu certo. */
  const run = async (fn: () => Promise<unknown>) => {
    try {
      await fn();
      toast.success('Catálogo atualizado.');
      load();
      return true;
    } catch (err) {
      toast.error(backendMessage(err, 'Não foi possível salvar.'));
      return false;
    }
  };

  if (!catalog) return <main className="mx-auto max-w-3xl p-4">Carregando…</main>;

  return (
    <main className="mx-auto max-w-3xl space-y-4 p-4">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to="/admin/chamados">
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>
      <h1 className="text-2xl font-semibold">Catálogo de chamados</h1>
      <p className="text-sm text-muted-foreground">
        Itens desativados somem do formulário, mas chamados antigos continuam com eles.
      </p>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Tipos e subtipos</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          {catalog.types.map((t) => (
            <fieldset key={t.id} aria-label={t.name} className="space-y-2 rounded-md border p-3">
              <ul>
                <ItemRow
                  item={t}
                  onSave={(body) => run(() => updateType(t.id, body)).then(() => {})}
                />
              </ul>
              <ul className="ml-4 space-y-1 border-l pl-4">
                {t.subtypes.map((s) => (
                  <ItemRow
                    key={s.id}
                    item={s}
                    onSave={(body) => run(() => updateSubtype(s.id, body)).then(() => {})}
                  />
                ))}
              </ul>
              <AddItem
                label="Novo subtipo"
                button="Adicionar subtipo"
                onAdd={(name) =>
                  run(() =>
                    createSubtype(t.id, { name, sortOrder: nextOrder(t.subtypes), active: true })
                  )
                }
              />
            </fieldset>
          ))}
          <AddItem
            label="Novo tipo"
            button="Adicionar tipo"
            onAdd={(name) =>
              run(() => createType({ name, sortOrder: nextOrder(catalog.types), active: true }))
            }
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Locais</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <ul className="space-y-1">
            {catalog.locations.map((l) => (
              <ItemRow
                key={l.id}
                item={l}
                onSave={(body) => run(() => updateLocation(l.id, body)).then(() => {})}
              />
            ))}
          </ul>
          <AddItem
            label="Novo local"
            button="Adicionar local"
            onAdd={(name) =>
              run(() =>
                createLocation({ name, sortOrder: nextOrder(catalog.locations), active: true })
              )
            }
          />
        </CardContent>
      </Card>
    </main>
  );
}
