# Avaliações das indicações

**Data:** 2026-08-28
**Status:** aprovado (design), pendente plano de implementação
**Pedido do Paulo:** "nas indicações, quero que seja tipo rede social, tenha curtidas e deslike,
comentários com notas de estrelas, com obrigação de comentário, mas somente pode dar like ou
comentar quem está logado e 1 por unidade somente."

## 1. Objetivo

Transformar as indicações num espaço de reputação: cada apartamento pode registrar **uma avaliação**
de um profissional — recomendo/não recomendo, nota de 1 a 5 estrelas e um comentário obrigatório —
e a lista passa a ordenar pelos melhores avaliados.

## 2. O que já existe

- `recommendation_vote` (V35): LIKE/DISLIKE, único por **usuário**.
- `recommendation_comment` (V35): só texto, soft delete.
- `recommendation.like_count` / `dislike_count`: contadores desnormalizados.
- `recommendation.rating` (1-5) e `recommendation.comment`: a nota **do autor da indicação**,
  preenchida no cadastro.

**Dado real (levantado em 2026-08-28):** produção tem **0 indicações, 0 votos, 0 comentários**.
HML tem 1 indicação de teste com nota do autor. Não há migração de dados a fazer — o desenho pode
reestruturar livremente.

## 3. Decisões

| Questão | Decisão |
|---|---|
| Modelo | **Uma avaliação só**: like/dislike + estrelas + comentário, num formulário único |
| Comentário | **Obrigatório**, mínimo 10 caracteres |
| Quem avalia | Autenticado **e com unidade**. Síndico (sem unidade) lê e não avalia |
| Cota | **1 por unidade** por indicação |
| Segundo morador da unidade | Bloqueado; vê "seu apartamento já avaliou, por Ana"; só a autora edita/apaga |
| Nota do autor da indicação | **Vira a 1ª avaliação**; `rating`/`comment` saem da indicação |
| Autor sem unidade (síndico) | Cria a indicação **sem** avaliação |
| Ordenação | Morador do condomínio → melhor score → recência |
| Identidade na tela | **Anônima para todos**, inclusive moradores |
| Identidade no banco | Gravada; visível a quem tem `RECOMMENDATION_MODERATE` |
| Quem indicou | Continua **visível** ("indicado por Ana") |
| Moderação | Oculta (`HIDDEN`) e a vaga do apartamento **continua ocupada** |
| Estrutura | Tabela nova `recommendation_review`; `recommendation_vote` e `recommendation_comment` saem |

### 3.1 Por que a identidade é anônima na tela mas gravada no banco

O anonimato protege **quem avalia depois** — principalmente quem dá nota baixa a um prestador que
atende o prédio inteiro e que vai continuar cruzando com ele. Mas anonimato absoluto deixaria a
moderação cega: o síndico só poderia ocultar um abuso, nunca tratar com a pessoa. A identidade fica
no banco e some apenas da interface pública.

**Consequência aceita:** a avaliação de quem indicou é dedutível (é a mais antiga, e o app mostra
quem indicou). Isso é coerente — quem indica faz um elogio público por vontade própria. O anonimato
existe para quem vem depois.

**Não é anonimato dentro do apartamento:** o segundo morador vê que a Ana avaliou. É informação
entre pessoas que moram juntas, e sem ela ele não entenderia por que não pode avaliar.

## 4. Modelo de dados

### 4.1 Tabela nova (migration `V40`)

```sql
CREATE TABLE recommendation_review (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version           bigint NOT NULL DEFAULT 0,
    recommendation_id uuid NOT NULL REFERENCES recommendation (id) ON DELETE CASCADE,
    unit_id           uuid NOT NULL REFERENCES unit (id) ON DELETE RESTRICT,
    author_user_id    uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    sentiment         varchar(10) NOT NULL,
    rating            smallint NOT NULL,
    text              text NOT NULL,
    status            varchar(10) NOT NULL DEFAULT 'ACTIVE',
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    deleted_at        timestamptz,
    CONSTRAINT chk_review_sentiment CHECK (sentiment IN ('LIKE', 'DISLIKE')),
    CONSTRAINT chk_review_rating    CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_review_status    CHECK (status IN ('ACTIVE', 'HIDDEN')),
    CONSTRAINT chk_review_text      CHECK (length(btrim(text)) >= 10)
);

-- Cota do apartamento. PARCIAL de propósito: apagar a própria avaliação libera a vaga;
-- uma avaliação apenas OCULTA (moderada) continua ocupando, para o abuso não ser reescrito.
CREATE UNIQUE INDEX ux_review_unit ON recommendation_review (recommendation_id, unit_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_review_reco ON recommendation_review (recommendation_id, created_at DESC)
    WHERE deleted_at IS NULL;
```

**`unit_id` é gravado, não derivado do usuário na leitura.** A cota é do apartamento; se a moradora
mudar de unidade, a avaliação continua pertencendo ao apartamento que a fez.

### 4.2 Mudanças em `recommendation`

- **Entram:** `review_count integer NOT NULL DEFAULT 0`, `rating_sum integer NOT NULL DEFAULT 0`.
- **Saem:** `rating`, `comment` (viram a primeira avaliação).
- **Ficam:** `like_count`, `dislike_count`, agora derivados do `sentiment` das avaliações ativas.

Guarda-se a **soma** das notas, não a média: soma é inteiro exato, média em coluna acumula erro de
arredondamento a cada recálculo. A média sai de `rating_sum::numeric / review_count` na leitura.

Avaliação `HIDDEN` **não conta** em nenhum dos quatro contadores.

### 4.3 Ordenação

Score bayesiano, para que poucas notas perfeitas não superem muitas notas ótimas:

```
score = (rating_sum + 3 * 3.5) / (review_count + 3)
```

Uma avaliação 5★ dá 3,88; doze avaliações 4,8★ dão 4,54. Ordem final da lista ACTIVE:
`is_resident DESC, score DESC, created_at DESC`.

O condomínio tem 522 unidades e as indicações estarão na casa das dezenas, então o score é calculado
na query — não precisa de coluna nem de índice dedicado.

## 5. Regras de domínio

1. **Avaliar exige** sessão + `unitId` não nulo. Sem unidade → 403 com código próprio, para o
   frontend explicar em vez de mostrar erro genérico.
2. **Uma por unidade.** A violação da unique é traduzida em erro de domínio, não em 500.
3. **Só o autor edita ou apaga** a própria avaliação. Apagar é soft delete e **libera a vaga**.
4. **Moderação** (`RECOMMENDATION_MODERATE`) muda o status para `HIDDEN`. A linha permanece, a vaga
   **não** é liberada, e os contadores são recalculados.
5. **Criar indicação:** autor com unidade envia nota + comentário e o service cria indicação e
   avaliação **na mesma transação**. Autor sem unidade cria a indicação sem avaliação.
6. **Quem indicou não avalia de novo** — já gastou a cota do apartamento na criação.
7. **Contadores** (`review_count`, `rating_sum`, `like_count`, `dislike_count`) são atualizados em
   toda criação, edição, remoção e moderação, dentro da mesma transação.

## 6. API

| Método | Rota | Autorização |
|---|---|---|
| `GET` | `/api/recommendations/{id}/reviews` | público (leitura de indicações é aberta desde 2026-08-25) |
| `POST` | `/api/recommendations/{id}/reviews` | autenticado + com unidade |
| `PUT` | `/api/recommendations/{id}/reviews/{reviewId}` | autor da avaliação |
| `DELETE` | `/api/recommendations/{id}/reviews/{reviewId}` | autor da avaliação |
| `POST` | `/api/recommendations/{id}/reviews/{reviewId}/hide` | `RECOMMENDATION_MODERATE` |

**Saem:** `POST /{id}/vote`, `GET/POST /{id}/comments`, `DELETE /{id}/comments/{commentId}`.

O `GET` devolve autor como `"Morador do condomínio"` para todos; **apenas** quem tem
`RECOMMENDATION_MODERATE` recebe `authorName` e `unitCode` preenchidos. A decisão de anonimato é
aplicada **no backend**, na montagem do DTO — nunca escondendo no frontend um dado que a API mandou.

## 7. Frontend

- **Detalhe da indicação:** os botões de curtir e o campo de comentário dão lugar a um formulário
  único (recomendo/não recomendo, estrelas, textarea obrigatória). Se o apartamento já avaliou, o
  formulário é substituído por "Seu apartamento já avaliou, por Ana" — com editar/apagar apenas para
  a autora.
- **Lista de avaliações:** estrelas + texto, assinadas "Morador do condomínio", mais recentes
  primeiro.
- **Card da listagem:** média com uma casa decimal e a contagem (`★ 4,3 · 12 avaliações`).
- **Visitante:** lê tudo, e o formulário é substituído pelo mesmo popup de login já usado no resto do
  app (`useLoginPrompt`).
- **Formulário de nova indicação:** nota e comentário passam a ser obrigatórios para quem tem
  unidade, e some para quem não tem.

## 8. Migração

`V40` — próximo número livre (a última é `V39__remove_guest_owner_ownership_proof.sql`).

1. `CREATE TABLE recommendation_review` + índices.
2. `ALTER TABLE recommendation ADD COLUMN review_count`, `rating_sum`.
3. `DROP TABLE recommendation_vote`, `recommendation_comment` — ambas vazias em produção.
4. `ALTER TABLE recommendation DROP COLUMN rating`, `comment`.

**Perda aceita:** a única indicação de HML tem nota do autor e ela se perde no passo 4. É dado de
teste. Produção não tem nenhuma indicação, então lá o efeito é nulo.

**Validar a migration contra dump real antes do push.** `RepositoryPostgresTest` — o único teste que
sobe Postgres de verdade e roda a cadeia Flyway inteira — não executa na máquina de desenvolvimento
(o Testcontainers não conversa com o Docker Desktop 29.3.1). Sem validação manual, a `V40` chegaria
ao deploy sem nunca ter rodado. O procedimento usado na `V39`: restaurar um dump real num container
`postgres:18` descartável e aplicar a migration por cima, com `ON_ERROR_STOP=1`.

## 9. Testes

**Backend**
- Avaliar sem unidade → 403.
- Segunda avaliação do mesmo apartamento (outro morador) → erro de domínio, não 500.
- Comentário com menos de 10 caracteres → 400.
- Nota fora de 1-5 → 400.
- Editar/apagar avaliação de outro → 403.
- Apagar a própria **libera** a vaga; moderar **não** libera.
- Contadores após criar, editar, apagar e ocultar.
- `GET` anônimo não devolve `authorName` nem `unitCode`; com `RECOMMENDATION_MODERATE` devolve.
- Criar indicação com unidade gera indicação + avaliação na mesma transação; sem unidade gera só a
  indicação.
- Ordenação: 12×4,8★ vem antes de 1×5★.

**Frontend**
- Visitante vê avaliações e cai no popup ao tentar avaliar.
- Morador cujo apartamento já avaliou vê a mensagem, não o formulário.
- Autora vê editar/apagar; o vizinho não.
- Card mostra média e contagem.

**E2E:** um morador avalia e a média do card muda.

## 10. Fora de escopo

- Responder a uma avaliação (o profissional não tem conta no app).
- Denúncia de avaliação por morador — a moderação é do síndico.
- Notificação por WhatsApp de nova avaliação.
- Histórico de edições de uma avaliação.
