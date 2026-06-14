# Níveis de importância em Avisos — Design (rascunho)

**Data:** 2026-06-13
**Branch:** a criar (`feat/avisos-importancia`)
**Status:** RASCUNHO — aguarda respostas das perguntas em aberto antes da implementação.

## Contexto

O mural de avisos (`feature/announcement`) exibe todos os cards da mesma forma: sem distinção
visual de urgência. O síndico quer classificar cada aviso por **nível de importância** e ver
cores diferentes por nível — vermelho para urgente, amarelo para importante, azul/verde para
informativo. Hoje o único destaque existente é a cor vermelha da barra lateral do título do
mural na `AnnouncementsListPage` (hardcoded em `--brand-red`).

O sistema não possui campo de prioridade ou cor no `Announcement`. A ordenação é manual via
`position` (V23), independente da importância, e deve permanecer assim.

## Estado atual (file:line)

- `Announcement.java` — campos: `id`, `title`, `body`, `position`, `publishedAt`,
  `authorUserId`, `createdAt`, `updatedAt`, `deletedAt`. Sem campo de prioridade ou cor.
- `AnnouncementView.java` — espelha os mesmos campos; sem prioridade.
- `CreateAnnouncementRequest.java` / `UpdateAnnouncementRequest.java` — só `title` e `body`.
- `AnnouncementService.java:27` — `list` ordena por `position ASC`; nenhuma referência a nível.
- `AnnouncementsListPage.tsx:54` — barra decorativa usa `--brand-red` fixo; sem badge/borda
  por importância.
- `AnnouncementFormPage.tsx` — formulário com `title` e `body` apenas.
- `tokens.css` — variáveis disponíveis: `--brand-red` (350 80% 48%), `--brand-orange`
  (32 90% 50%), `--brand-yellow` (45 93% 52%), `--brand-green` (132 52% 40%),
  `--brand-blue` (205 72% 44%), `--brand-ink` (0 0% 8%).
- Última migration aplicada: `V29__role_guest_general_areas.sql`; próxima livre: `V30`.

## Objetivo

Adicionar um campo `importance` (`HIGH / MEDIUM / LOW`) na entidade `Announcement`, mapeado a
cores reconhecíveis na lista e no detalhe. O nível é definido por quem tem `ANNOUNCEMENT_MANAGE`
(no formulário de criação e edição). A ordenação manual (`position`) permanece como critério
primário e não muda.

**Não-objetivos:** notificações por WhatsApp diferenciadas por nível (escopo separado);
filtragem por nível na lista; push notifications; alteração na paginação ou reordenação.

## Modelo de dados

### Enum proposto: `AnnouncementImportance`

```
HIGH   → "Alta"   → vermelho → --brand-red    (350 80% 48%)
MEDIUM → "Média"  → amarelo  → --brand-yellow (45 93% 52%)
LOW    → "Baixa"  → azul     → --brand-blue   (205 72% 44%)
```

Justificativa da escolha de cores:
- `--brand-red` já é a cor do mural no App/Sidebar; reforça "urgente".
- `--brand-yellow` coincide com `--warning` no sistema; semântica de atenção.
- `--brand-blue` coincide com `--info` no sistema; semântica de informativo.
- `--brand-green` (alternativa para LOW) também é válido; ver perguntas em aberto.
- `--brand-orange` é reservado para Indicações; evitar sobreposição semântica.

### Coluna na tabela `announcement`

Tipo: `varchar(6)` com constraint `CHECK (importance IN ('HIGH','MEDIUM','LOW'))`. Armazenar
como string em vez de tipo `ENUM` nativo do PostgreSQL facilita migrations futuras (sem
`ALTER TYPE`) e é compatível com `@Enumerated(EnumType.STRING)` do JPA.

### Migration `V30__announcement_importance.sql`

```sql
-- flyway:transactional=true

-- Adiciona nível de importância nos avisos do mural.
-- Default MEDIUM para avisos existentes (neutro; pode ser ajustado pelo gestor).

ALTER TABLE announcement
  ADD COLUMN importance varchar(6)
    NOT NULL DEFAULT 'MEDIUM'
    CHECK (importance IN ('HIGH', 'MEDIUM', 'LOW'));

COMMENT ON COLUMN announcement.importance IS
  'Nível de importância: HIGH=vermelho, MEDIUM=amarelo, LOW=azul.';
```

`NOT NULL DEFAULT 'MEDIUM'` é backward-compatible: PostgreSQL aplica o valor a todas as linhas
existentes na mesma instrução DDL. Nenhum contrato de leitura quebra porque `MEDIUM` é o
comportamento "neutro" (amarelo, que já era a cor de destaque usada de forma genérica antes).

## Backend (`feature/announcement`)

### Enum `AnnouncementImportance.java` (novo arquivo)

```java
package br.com.condominio.feature.announcement;

public enum AnnouncementImportance {
  HIGH, MEDIUM, LOW
}
```

### Entidade `Announcement.java` — alterações

```java
// Novo campo (após "position"):
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 6)
private AnnouncementImportance importance;
```

Método factory atualizado:

```java
public static Announcement create(UUID authorUserId, String title, String body,
                                  int position, AnnouncementImportance importance) {
  Announcement a = new Announcement();
  a.authorUserId = authorUserId;
  a.title        = title;
  a.body         = body;
  a.position     = position;
  a.importance   = importance;
  return a;
}
```

Método `edit` atualizado:

```java
public void edit(String title, String body, AnnouncementImportance importance) {
  this.title      = title;
  this.body       = body;
  this.importance = importance;
}
```

### DTOs — alterações

**`CreateAnnouncementRequest`**

```java
public record CreateAnnouncementRequest(
    @NotBlank @Size(max = 140) String title,
    @NotBlank @Size(max = 5000) String body,
    @NotNull AnnouncementImportance importance) {}
```

**`UpdateAnnouncementRequest`**

```java
public record UpdateAnnouncementRequest(
    @NotBlank @Size(max = 140) String title,
    @NotBlank @Size(max = 5000) String body,
    @NotNull AnnouncementImportance importance) {}
```

**`AnnouncementView`** — adiciona `importance` após `position`:

```java
public record AnnouncementView(
    UUID id, String title, String body, int position,
    AnnouncementImportance importance,
    Instant publishedAt, UUID authorUserId, Instant updatedAt) {

  public static AnnouncementView of(Announcement a) {
    return new AnnouncementView(
        a.getId(), a.getTitle(), a.getBody(), a.getPosition(),
        a.getImportance(),
        a.getPublishedAt(), a.getAuthorUserId(), a.getUpdatedAt());
  }
}
```

### Service `AnnouncementService` — alterações

```java
// create:
Announcement a = Announcement.create(
    authorId, body.title(), body.body(), top, body.importance());

// update:
a.edit(body.title(), body.body(), body.importance());
```

`list` e `getById` não mudam: o campo é serializado automaticamente pelo DTO.

### Controller

Nenhuma mudança de rotas ou autorização. `ANNOUNCEMENT_MANAGE` continua como guarda para escrita.

### Serialização JSON

`AnnouncementImportance` serializa como string (`"HIGH"`, `"MEDIUM"`, `"LOW"`) via
`@Enumerated(EnumType.STRING)`. Se o cliente enviar valor inválido, o Jackson retorna 400 antes
de atingir o controller (`HttpMessageNotReadableException`).

## Frontend (`features/announcements`)

### Paleta de importância — `announcementsApi.ts`

```typescript
export type AnnouncementImportance = 'HIGH' | 'MEDIUM' | 'LOW';

export const IMPORTANCE_LABEL: Record<AnnouncementImportance, string> = {
  HIGH:   'Alta',
  MEDIUM: 'Média',
  LOW:    'Baixa',
};

// HSL raw das vars de tokens.css — usados com hsl() inline
export const IMPORTANCE_HSL: Record<AnnouncementImportance, string> = {
  HIGH:   'var(--brand-red)',
  MEDIUM: 'var(--brand-yellow)',
  LOW:    'var(--brand-blue)',
};
```

Tipo `Announcement` recebe `importance: AnnouncementImportance`.
`AnnouncementBody` recebe `importance: AnnouncementImportance`.

### Lista — `AnnouncementsListPage.tsx`

Cada `Card` recebe borda esquerda colorida por nível (mesmo padrão das cards do `App.tsx`)
e um badge com rótulo de importância:

```tsx
<Card
  className="h-full border-l-4 transition-colors hover:bg-accent"
  style={{ borderLeftColor: `hsl(${IMPORTANCE_HSL[a.importance]})` }}
>
  <CardHeader>
    <CardTitle className="text-base">{a.title}</CardTitle>
    <span
      className="inline-block rounded-full px-2 py-0.5 text-xs font-medium"
      style={{
        color:           `hsl(${IMPORTANCE_HSL[a.importance]})`,
        backgroundColor: `hsl(${IMPORTANCE_HSL[a.importance]} / 0.10)`,
      }}
    >
      {IMPORTANCE_LABEL[a.importance]}
    </span>
  </CardHeader>
  ...
</Card>
```

Observação: exibir o badge apenas para `HIGH` e `LOW` (ocultar `MEDIUM` como padrão silencioso)
é uma alternativa de UX. Ver perguntas em aberto.

### Detalhe — `AnnouncementDetailPage.tsx`

Badge de importância exibido abaixo do `<h1>`, com as mesmas cores e estilo da lista.

### Formulário — `AnnouncementFormPage.tsx`

Novo campo select entre `title` e `body`:

```tsx
const [importance, setImportance] = useState<AnnouncementImportance>('MEDIUM');

// No useEffect de edição:
setImportance(a.importance);

// No form:
<div className="space-y-2">
  <Label htmlFor="importance">Nível de importância</Label>
  <select
    id="importance"
    value={importance}
    onChange={(e) => setImportance(e.target.value as AnnouncementImportance)}
    className="flex w-full rounded-md border border-input bg-background
               px-3 py-2 text-sm ring-offset-background
               focus-visible:outline-none focus-visible:ring-2
               focus-visible:ring-ring focus-visible:ring-offset-2"
  >
    <option value="HIGH">Alta — urgente</option>
    <option value="MEDIUM">Média — atenção</option>
    <option value="LOW">Baixa — informativo</option>
  </select>
</div>
```

No `handleSubmit`, o payload passa a incluir `importance`.
O campo só aparece no formulário (rota protegida por `ANNOUNCEMENT_MANAGE`). Moradores leem
o badge na lista/detalhe sem acesso ao form.

## Ordenação

A ordenação permanece exclusivamente por `position ASC` (ordem manual do V23). O nível de
importância **não afeta a posição** dos avisos na lista. O síndico reordena manualmente com
as setas ↑/↓ se quiser que um aviso de alta importância apareça primeiro.

Razão: introduzir ordenação automática por importância quebraria a semântica da reordenação
manual e causaria deslocamentos inesperados ao alterar o nível de um aviso existente. Se no
futuro o Paulo quiser "urgentes sempre no topo", isso deve ser uma feature separada com
discussão explícita.

## Testes (TDD — escrever antes da implementação)

### Backend

**`AnnouncementTest.java`**
- `create` com cada nível armazena o `importance` correto.
- `edit` altera o `importance`.

**`AnnouncementServiceTest.java`**
- `create(authorId, body)` → `AnnouncementView.importance == body.importance()`.
- `update(id, body)` → `importance` atualizado na view.
- `list` → `importance` presente em cada item da página.

**`AnnouncementControllerWebTest.java`**
- `POST /api/announcements` sem `importance` → 400.
- `POST /api/announcements` com `"importance":"HIGH"` → 201 + body com `"importance":"HIGH"`.
- `POST /api/announcements` com `"importance":"INVALID"` → 400.
- `PUT /api/announcements/{id}` com `"importance":"LOW"` → altera e retorna `"importance":"LOW"`.
- `GET /api/announcements` → cada item do `content` tem campo `importance`.
- `GET /api/announcements/{id}` → campo `importance` presente.

### Frontend

**`announcementsApi.test.ts`**
- Tipo `Announcement` contém `importance`.
- `createAnnouncement` serializa `importance` no corpo da requisição.
- `updateAnnouncement` serializa `importance` no corpo da requisição.

**`AnnouncementsListPage.test.tsx`**
- Card com `importance: 'HIGH'` renderiza badge com texto "Alta".
- Card com `importance: 'LOW'` renderiza badge com texto "Baixa".
- Card com `importance: 'MEDIUM'` renderiza badge com texto "Média" (ou está oculto —
  depende da decisão da pergunta 4).

**`AnnouncementFormPage` (testes existentes ou novos)**
- Campo select `importance` aparece quando `canManage` é verdadeiro.
- Ao editar aviso com `importance: 'HIGH'`, o select inicia em "Alta".
- Submit inclui `importance` no payload.

## Perguntas em aberto

1. **Exatamente 3 níveis?** A proposta usa `HIGH / MEDIUM / LOW`. O Paulo quer um 4º nível
   (ex.: "Crítico" acima de vermelho / laranja) ou os 3 são suficientes para o condomínio?

2. **Rótulos em português?** "Alta / Média / Baixa" versus "Urgente / Importante / Informativo".
   Os rótulos afetam o que o morador lê no badge; os valores do enum permanecem em inglês no
   banco. Qual terminologia o Paulo prefere exibir?

3. **A 3ª cor é azul ou verde?** `--brand-blue` (info, 205 72% 44%) ou `--brand-green`
   (sucesso, 132 52% 40%) para o nível baixo/informativo. Azul tem mais semântica de
   "informação"; verde pode ser lido como "positivo/resolvido". Qual preferência visual?

4. **Badge sempre visível ou só para HIGH e LOW?** Mostrar o badge "Média" em todos os avisos
   padrão gera ruído visual (a maioria pode ser média). Sugestão: ocultar o badge para `MEDIUM`
   e mostrar apenas para `HIGH` e `LOW`. O Paulo concorda, ou quer os 3 níveis sempre visíveis?

5. **Ordenação por importância?** O spec propõe que `importance` não afete a ordem automática
   (posição continua puramente manual). Se o Paulo quiser que avisos `HIGH` sempre apareçam
   antes dos demais, isso requer uma decisão de design separada (conflito com a reordenação
   manual do V23). Confirmar que "só colorir, não reordenar automaticamente" está correto.

6. **Default para avisos existentes:** A migration propõe `DEFAULT 'MEDIUM'` para todas as
   linhas existentes. Isso é adequado, ou o Paulo prefere `LOW` (informativo) como default
   histórico para avisos antigos?
