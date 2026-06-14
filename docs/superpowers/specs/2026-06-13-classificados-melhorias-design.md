# Classificados — Melhorias: múltiplas fotos e dados de contato

**Data:** 2026-06-13
**Status:** RASCUNHO — aguardando respostas das perguntas em aberto.
**Branch de referência:** `feat/gestao-usuarios-v2` (worktree de spec; não há código implementado aqui)

---

## 1. Contexto e motivação

A feature de Classificados (Plano 3A, V15) foi entregue com:
- Até **1 foto por anúncio** inicialmente; o limite de 5 já estava codificado no
  service (`MAX_PHOTOS = 5`) e validado no frontend (`MAX_PHOTOS = 5`), mas
  **o formulário de criação (`ClassifiedFormPage`) aceita fotos somente após salvar
  o anúncio** (fluxo de dois passos: criar → editar → adicionar fotos).
- **Sem campos de contato**: o único dado exposto é `authorUserId` (UUID interno),
  sem nome ou telefone visível para quem lê o anúncio.

O Paulo pediu duas melhorias:
1. Exibir até **5 fotos** de forma clara (o backend já suporta, o frontend já tem o
   guard `MAX_PHOTOS`; o gap é UX — o formulário de criação não guia bem o usuário
   para o upload, e a visualização usa um grid simples sem galeria).
2. Adicionar campos de **nome e telefone do contato**, opcionais, exibidos na página
   de detalhe do anúncio para facilitar que interessados entrem em contato.

Este documento especifica as mudanças necessárias no modelo de dados, na API e na
interface para suportar ambos os pontos.

---

## 2. Estado atual — o que já existe

### 2.1 Backend

| Arquivo | Situação |
|---|---|
| `Classified.java` | Sem campos de contato. Métodos de domínio: `create`, `edit`, `markSold`, `archive`, `reactivate`. |
| `ClassifiedPhoto.java` | Tabela `classified_photo` com `ordering`, soft delete, limite 5 validado em service. |
| `ClassifiedService.java` | `MAX_PHOTO_BYTES = 1_048_576L` (1MB), `MAX_PHOTOS = 5`. Upload fora de transação (correto). Magic-bytes via `MagicBytesValidator`. Presigned GET via `FileStorage`. |
| `ClassifiedController.java` | `POST /{id}/photos` (multipart), `DELETE /{id}/photos/{photoId}`, `GET /{id}/photos/{photoId}/url`. |
| `V15__classifieds.sql` | Tabelas `classified` e `classified_photo`. Sem colunas de contato. |
| `CreateClassifiedRequest.java` | `title`, `description`, `price`. Sem contato. |
| `UpdateClassifiedRequest.java` | `title`, `description`, `price`, `status`. Sem contato. |
| `ClassifiedView.java` | Retorna `authorUserId` (UUID), sem nome/telefone do contato. |
| `ClassifiedPhotoView.java` | `id`, `ordering`, `contentType`. Sem URL inline (URL é endpoint separado). |

**O limite de 5 fotos já está implementado e funcionando.** Não há nada a mudar
na lógica de upload. O trabalho aqui é refinamento de UX e adição de campos.

### 2.2 Frontend

| Arquivo | Situação |
|---|---|
| `ClassifiedFormPage.tsx` | Upload de foto só disponível no modo edição (após criar o anúncio). Grid 2-3 colunas, remoção individual. Compressão via `browser-image-compression` (maxSizeMB 1, maxWidthOrHeight 1920). |
| `ClassifiedDetailPage.tsx` | Grid 2-3 colunas. Sem galeria/lightbox. Sem dados de contato. |
| `ClassifiedsListPage.tsx` | Cards com título e preço. Sem foto de capa. |
| `classifiedsApi.ts` | Tipos `Classified` e `ClassifiedPhoto` sem campos de contato. |

### 2.3 Padrões de upload já estabelecidos no projeto

A lógica de upload segue o mesmo padrão do comprovante de residência e das fotos
de indicações (`recommendation_photo`):

- **Compressão client-side** antes do envio (`browser-image-compression`,
  `maxSizeMB: 1`, `maxWidthOrHeight: 1920`). Fallback para arquivo original se
  a compressão falhar.
- **Multipart POST** para o backend (não presigned PUT direto pro MinIO — proibido
  porque furaria o magic-bytes check obrigatório).
- **Magic-bytes server-side**: `MagicBytesValidator.isAcceptedForPhoto` (JPG, PNG,
  WEBP). Tamanho máximo: 1MB. Tipos aceitos: `image/jpeg`, `image/png`,
  `image/webp`.
- **Presigned GET** de TTL curto (`presignedTtlPhotosSeconds`) para leitura.
- **Soft delete** em `classified_photo`.

O padrão de telefone no projeto usa `ValidationPatterns.PHONE`
(`\\+?[0-9]{10,15}`), mesmo regex usado em usuários, indicações e contatos.
`PhoneNumberNormalizer` só é necessário para envio via WhatsApp — aqui o telefone
é apenas exibido, não enviado.

---

## 3. Mudanças propostas

### 3.1 Modelo de dados — V30: adicionar campos de contato

> A tabela `classified_photo` **não precisa de alteração** — ela já existe e já
> suporta até 5 fotos. A única migration necessária é adicionar as colunas de
> contato à tabela `classified`.

**Migration sugerida: `V30__classified_contact.sql`**

```sql
-- flyway:transactional=true

-- Adiciona campos opcionais de contato ao classified.
-- Expand only: nenhuma coluna é removida ou renomeada.
ALTER TABLE classified
    ADD COLUMN contact_name  varchar(120),
    ADD COLUMN contact_phone varchar(20);
```

Ambas as colunas são **nullable** (contato é opcional). Sem CHECK adicional
no telefone — o formato é validado na camada de aplicação.

**Próxima versão livre:** a última migration existente é `V29__role_guest_general_areas.sql`;
logo, a próxima é `V30`.

### 3.2 Domínio — `Classified.java`

Adicionar os dois campos à entidade e expandir o factory method `create` e o
método `edit` para recebê-los:

```java
@Column(name = "contact_name", length = 120)
private String contactName;

@Column(name = "contact_phone", length = 20)
private String contactPhone;
```

`create(authorUserId, title, description, price, contactName, contactPhone)` —
ambos nullable.

`edit(title, description, price, contactName, contactPhone)` — sobrescreve ambos
(null limpa o campo).

### 3.3 DTOs

**`CreateClassifiedRequest`** — adicionar:

```java
@Size(max = 120)
String contactName,

@Pattern(regexp = ValidationPatterns.PHONE)
String contactPhone
```

Ambos opcionais (`@Size` e `@Pattern` não disparam em `null`).

**`UpdateClassifiedRequest`** — mesmas adições.

**`ClassifiedView`** — adicionar `contactName` e `contactPhone` ao record.
Consideração PII: telefone e nome do anunciante **são dados de contato que o
próprio anunciante escolhe expor**; exibição para qualquer usuário logado é
legítima. Nunca logar esses campos (`LogSanitizer` se aparecer em trace de erro).

### 3.4 API — sem novos endpoints

Nenhum endpoint novo. As mudanças são aditivas nos bodies/responses existentes:

| Endpoint | Mudança |
|---|---|
| `POST /api/classifieds` | Aceita `contactName`, `contactPhone` opcionais no body. |
| `PUT /api/classifieds/{id}` | Idem. |
| `GET /api/classifieds/{id}` | Response inclui `contactName`, `contactPhone`. |
| `GET /api/classifieds` | Response inclui `contactName`, `contactPhone` em cada item. |

Os endpoints de foto (`POST /{id}/photos`, `DELETE /{id}/photos/{photoId}`,
`GET /{id}/photos/{photoId}/url`) **não mudam**.

### 3.5 Frontend — mudanças de UX

#### 3.5.1 `classifiedsApi.ts`

Adicionar ao tipo `Classified`:

```typescript
contactName: string | null;
contactPhone: string | null;
```

Adicionar aos bodies de `createClassified` e `updateClassified`:

```typescript
contactName?: string | null;
contactPhone?: string | null;
```

#### 3.5.2 `ClassifiedFormPage.tsx` — formulário de criação/edição

Dois novos campos após "Preço":

```
[Label] Nome do contato (opcional)
[Input] placeholder="Ex.: João Silva"

[Label] Telefone do contato (opcional)
[Input] type="tel" inputMode="tel" placeholder="Ex.: 11999999999"
```

**Validação client-side do telefone**: regex `/^\+?[0-9]{10,15}$/` — mesma
do backend. Só valida se o campo não estiver vazio (opcional).

O upload de fotos **não muda** — permanece disponível apenas no modo edição
(após criar o anúncio). O comportamento atual (criar → redirecionar para
`/classificados/:id/editar`) já guia o usuário para adicionar fotos.

**Opcional / melhoria de UX**: exibir uma dica logo após criar o anúncio:
`"Anúncio criado! Adicione até 5 fotos para atrair mais interesse."` — o toast
atual já diz algo parecido (`"Anúncio criado. Adicione fotos, se desejar."`).

#### 3.5.3 `ClassifiedDetailPage.tsx` — visualização

**Galeria de fotos**: em vez do grid simples, propor um layout de galeria com
foto principal grande (primeira foto, `ordering` mais baixo) e thumbnails
das demais abaixo ou ao lado:

```
┌──────────────────────┐
│    foto principal    │   ← foto com menor ordering, aspect 4:3 ou 16:9
└──────────────────────┘
┌────┐ ┌────┐ ┌────┐
│ 2  │ │ 3  │ │ 4  │   ← thumbnails das demais (aspect-square, 80px)
└────┘ └────┘ └────┘
```

Clicar em thumbnail troca a foto principal (sem lightbox obrigatório na primeira
versão — perguntar ao Paulo). A foto "ativa" tem borda destacada.

**Dados de contato**: exibir abaixo da descrição, quando presentes:

```
┌─────────────────────────────┐
│ Contato                     │
│ João Silva                  │
│ (11) 9 9999-9999            │  ← telefone como <a href="tel:...">
└─────────────────────────────┘
```

Exibir sempre para qualquer usuário logado (ver perguntas em aberto sobre
visibilidade).

#### 3.5.4 `ClassifiedsListPage.tsx` — listagem

Exibir a **foto de capa** (primeira foto, se houver) no card da lista:

```
┌─────────────────────────────┐
│ [foto de capa 16:9 ou muted]│
│ Título do anúncio           │
│ R$ 150,00                   │
└─────────────────────────────┘
```

Isso requer buscar a URL da foto de capa. Duas abordagens:

**Opção A (preferida — menor round-trips):** o backend retorna a URL presigned
da primeira foto **inline** no `ClassifiedView`, como um campo `coverPhotoUrl`
gerado no `view()` do service. Custo: N presigned URLs geradas a cada listagem
(uma por anúncio com foto). Para a escala do condomínio (~100 anúncios
ativos max), é aceitável.

**Opção B (sem mudança de backend):** o frontend busca `photoUrl` para a
primeira foto de cada card individualmente — gera N+N requests (listar + N
URLs). Ruim para performance e UX.

**Recomendação:** implementar Opção A, adicionando `coverPhotoUrl: String`
(nullable) ao `ClassifiedView`. O field é gerado no service — se o anúncio
não tiver fotos, `coverPhotoUrl = null` e o card exibe um placeholder com
cor de fundo.

> Nota: se `coverPhotoUrl` for adicionado, o campo tem TTL curto (igual ao
> `presignedTtlPhotosSeconds`). A listagem exibe a URL que expira; o usuário
> que ficar muito tempo na lista verá imagens quebradas. Mitigação: TTL mais
> longo para listagem (ex. 900s), ou aceitar o comportamento e pedir ao usuário
> que recarregue.

---

## 4. Resumo das mudanças por camada

| Camada | Mudança | Impacto |
|---|---|---|
| **DB (V30)** | `ALTER TABLE classified ADD COLUMN contact_name varchar(120), ADD COLUMN contact_phone varchar(20)` | Aditivo, backward-compatible |
| **Entidade** | `contactName`, `contactPhone` em `Classified.java` | Novo campo, factory/edit expandidos |
| **DTOs** | `CreateClassifiedRequest`, `UpdateClassifiedRequest`, `ClassifiedView` | Novo campo, aditivo |
| **Service** | `view()` opcionalmente gera `coverPhotoUrl` | Opcional; ver perguntas em aberto |
| **Frontend — API** | Tipo `Classified`, funções de criação/atualização | Novo campo |
| **Frontend — Form** | Inputs de `contactName`/`contactPhone` | 2 novos inputs opcionais |
| **Frontend — Detail** | Galeria com foto principal + thumbnails + seção de contato | UX nova |
| **Frontend — List** | Foto de capa no card | Depende da decisão sobre `coverPhotoUrl` |

---

## 5. Testes

### 5.1 Backend (TDD — testes antes da implementação)

- `ClassifiedServiceTest`: adicionar cenários de `create`/`update` com
  `contactName`/`contactPhone` preenchidos e nulos.
- `ClassifiedServiceTest`: cenário de `contactPhone` com formato inválido
  (`@Pattern` é validado pelo Bean Validation no controller, não no service —
  confirmar que o slice WebMvcTest cobre isso).
- `ClassifiedControllerWebTest`: POST/PUT com `contactPhone` fora do padrão
  → 400 Bad Request.
- `ClassifiedControllerWebTest`: GET `/api/classifieds/{id}` retorna
  `contactName`/`contactPhone`.
- Se `coverPhotoUrl` for adicionado: mock do `FileStorage.presignedGetUrl` no
  `ClassifiedServiceTest` para cenário com e sem fotos.

### 5.2 Frontend

- `ClassifiedFormPage.test.tsx`: inputs de contato presentes, validação do
  telefone com regex.
- `ClassifiedDetailPage.test.tsx`: exibe seção de contato quando campos estão
  presentes; oculta quando nulos; foto principal e thumbnails.
- `classifiedsApi.test.ts`: tipos com `contactName`/`contactPhone`.

---

## 6. Perguntas em aberto

1. **O telefone do contato é o do anunciante (preenchido automaticamente pelo
   sistema a partir do perfil do usuário) ou um campo avulso livre?** Se for
   o do perfil, não precisa de campo extra — basta exibir o telefone do autor.
   Se for avulso, o anunciante pode informar um número diferente do seu (ex.:
   número de um familiar, número comercial). A proposta acima assume campo
   avulso e opcional.

2. **Os dados de contato são visíveis para qualquer usuário logado, ou só para
   o próprio anunciante e moderadores?** Exibir publicamente (para todos os
   logados) é o mais útil para viabilizar o contato, mas expõe o telefone.
   Alternativa: exibir o telefone mascarado parcialmente e revelar só após
   clicar "Ver contato".

3. **A foto de capa na listagem é desejada?** Muda o visual da lista
   significativamente. Requer decisão sobre a abordagem de URL
   (`coverPhotoUrl` inline no backend vs. requests separados no frontend).

4. **A galeria do detalhe precisa de lightbox/zoom?** A proposta prevê troca
   de foto principal ao clicar em thumbnail, sem lightbox. Um lightbox melhora
   muito a UX mas adiciona um componente (ex.: `@radix-ui/react-dialog` já
   disponível via shadcn).

5. **Reordenação de fotos pelo anunciante é necessária?** Hoje a ordem é por
   `ordering` crescente (ordem de upload). Não existe endpoint de reordenação.
   Se o Paulo quiser controlar qual foto aparece como capa, precisaria de um
   endpoint `PATCH /{id}/photos/{photoId}/reorder` (ou drag-and-drop no form).
   Fora de escopo por ora, a menos que confirmado.

6. **As fotos são obrigatórias ou opcionais?** Hoje são opcionais. Manter assim?

7. **Limite de caracteres do `contact_name`**: proposta `varchar(120)`,
   igual ao `title`. Adequado?

8. **O `contact_phone` deve ser formatado/mascarado na UI?** Ex.: exibir
   `(11) 9 9999-9999` em vez de `11999999999`. Requer lib de formatação ou
   regex de display no frontend. O campo no banco guarda o número bruto
   (sem formatação), igualmente ao padrão das outras entidades do projeto.

9. **`coverPhotoUrl` na listagem**: se implementado, qual TTL usar? O padrão
   `presignedTtlPhotosSeconds` (300s) pode causar imagens quebradas em sessões
   longas. Proposta: TTL separado `presignedTtlCoverSeconds` = 900s, ou
   simplesmente aceitar que a listagem expire após 5 min e o usuário recarregue.
