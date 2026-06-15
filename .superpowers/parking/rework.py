# -*- coding: utf-8 -*-
"""
Rework da extracao de vagas (HELBOR TRILOGY).
Corrige a classificacao SEM mexer na geometria (coords ficam do floors.json,
ja alinhadas aos PNGs / preview).

Mudancas:
  1. TORRE via CODIGO (T1/T2/T3 -> A/B/C). Fonte = texto OCR 'T{n} {num}', confiavel.
     Antes a torre vinha da COR do box (frgil) -> trocava de torre.
  2. CATEGORIA revalidada: re-le a cor do box no PDF e escolhe a cat (1/2/3)
     SO entre as 3 cores da torre ja conhecida (busca restrita = mais robusta).
  3. MOTOS ganham torre: vaga de carro mais proxima (centro) no mesmo piso.
  4. QA automatico: relatorio de divergencias code-vs-cor, cat alterada,
     motos atribuidas, codes sem cor casada.

Saida: floors.json (atualizado), catalogo.csv, qa_report.txt
"""
import json, csv, os, collections

HERE = os.path.dirname(os.path.abspath(__file__))
PDFS = {
    -1: r"C:\Users\paulo\Downloads\04112019 - GAR SP - HELBOR TRILOGY SBC - AGRUPAMENTO - -1 -REV 05 (1).pdf",
     0: r"C:\Users\paulo\Downloads\04112019 - GAR SP - HELBOR TRILOGY SBC - AGRUPAMENTO - 0 -REV 05 (1).pdf",
     1: r"C:\Users\paulo\Downloads\04112019 - GAR SP - HELBOR TRILOGY SBC - AGRUPAMENTO -  1  -REV 05 (2).pdf",
     2: r"C:\Users\paulo\Downloads\04112019 - GAR SP - HELBOR TRILOGY SBC - AGRUPAMENTO -  2  -REV 05 (1).pdf",
}
# (torre, categoria) -> cor RGB no desenho
CMAP = {('A',1):(0.87,1.0,0.5), ('A',2):(0.65,0.87,0.43), ('A',3):(0.0,0.58,0.29),
        ('B',1):(1.0,0.75,0.0), ('B',2):(1.0,0.25,0.0),  ('B',3):(0.87,0.0,0.0),
        ('C',1):(1.0,0.5,1.0),  ('C',2):(0.5,0.0,1.0),   ('C',3):(0.63,0.36,0.72)}
TOWER_OF = {'1':'A', '2':'B', '3':'C'}

def tower_from_code(code):
    """T1/T2/T3 -> A/B/C ; None para motos/outros."""
    if len(code) >= 2 and code[0] == 'T' and code[1] in TOWER_OF:
        return TOWER_OF[code[1]]
    return None

def center(s):
    return (s['l'] + s['w']/2, s['tp'] + s['h']/2)

# ---------- PART B helper: cor do box por codigo (re-le do PDF) ----------
def colors_by_code():
    """Reproduz o pareamento code->box do extract.py e devolve {code: rgb}."""
    import re, fitz
    out = {}
    def nearest_any(col):
        best, bd = None, 0.12
        for k, c in CMAP.items():
            d = sum((a-b)**2 for a, b in zip(col, c))**.5
            if d < bd:
                bd, best = d, k
        return best
    def C(b):
        return ((b[0]+b[2])/2, (b[1]+b[3])/2)
    for fl, path in PDFS.items():
        if not os.path.exists(path):
            print(f"  [aviso] PDF do piso {fl} ausente -> categoria mantida")
            continue
        pg = fitz.open(path)[0]
        words = pg.get_text("words")
        fills = [(tuple(round(c,2) for c in d['fill']), d['rect'])
                 for d in pg.get_drawings() if d.get('fill') and d.get('rect')]
        carrects = [(nearest_any(col), r) for col, r in fills
                    if nearest_any(col) and 40 <= min(r.width, r.height) <= 75
                    and 88 <= max(r.width, r.height) <= 260]
        exids = {id(w) for w in words if re.match(r'^[123][123]01$', w[4]) and w[1] > 2400}
        seen = set()
        for i, w in enumerate(words[:-1]):
            nxt = words[i+1]
            if re.match(r'^T[123]$', w[4]) and re.match(r'^\d{4}$', nxt[4]) and id(nxt) not in exids:
                code = f"T{w[4][1]} {nxt[4]}"
                if code in seen:
                    continue
                cx, cy = C(nxt)
                inside = [r for cat, r in carrects if r.x0-3 <= cx <= r.x1+3 and r.y0-3 <= cy <= r.y1+3]
                if not inside:
                    cand = sorted(carrects, key=lambda cr: (C(cr[1])[0]-cx)**2 + (C(cr[1])[1]-cy)**2)[:1]
                    if not cand:
                        continue
                    inside = [cand[0][1]]
                r = max(inside, key=lambda rr: rr.width*rr.height)
                # cor real do box r (procura o fill cujo rect == r)
                col = next((c for c, rr in fills if rr == r), None)
                if col:
                    out[(fl, code)] = tuple(col)
                seen.add(code)
    return out

def cat_from_color(tower, col):
    """Escolhe cat 1/2/3 SO entre as 3 cores da torre conhecida. Devolve (cat, dist)."""
    best, bd = None, 9.0
    for cat in (1, 2, 3):
        c = CMAP[(tower, cat)]
        d = sum((a-b)**2 for a, b in zip(col, c))**.5
        if d < bd:
            bd, best = d, cat
    return best, round(bd, 3)

# ============================ PIPELINE ============================
def main():
    fp = os.path.join(HERE, 'floors.json')
    data = json.load(open(fp, encoding='utf-8'))

    # re-leitura de cores (pode falhar se PDFs ausentes -> categoria mantida)
    try:
        cols = colors_by_code()
        print(f"cores re-lidas do PDF: {len(cols)} codes")
    except Exception as e:
        cols = {}
        print(f"[aviso] nao consegui re-ler cores ({e}); categoria sera mantida")

    qa = {'tower_changed': [], 'cat_changed': [], 'moto_assigned': [],
          'no_color': [], 'low_conf_cat': []}

    for fl_key in data:
        fl = int(fl_key)
        spots = data[fl_key]['spots']
        cars = [s for s in spots if s['kind'] == 'CAR']

        # 1+2. carros: torre via codigo, categoria revalidada por cor restrita
        for s in cars:
            old_tower = s['tower']
            new_tower = tower_from_code(s['code'])
            if new_tower and new_tower != old_tower:
                qa['tower_changed'].append((fl, s['code'], old_tower, new_tower))
                s['tower'] = new_tower
            t = s['tower']
            col = cols.get((fl, s['code']))
            if col is None:
                qa['no_color'].append((fl, s['code']))
            else:
                new_cat, dist = cat_from_color(t, col)
                if dist > 0.06:
                    qa['low_conf_cat'].append((fl, s['code'], s['cat'], new_cat, dist))
                elif new_cat != s['cat']:
                    qa['cat_changed'].append((fl, s['code'], s['cat'], new_cat))
                    s['cat'] = new_cat

        # 3. motos: torre = carro mais proximo (centro) no mesmo piso
        car_centers = [(center(c), c['tower']) for c in cars if c['tower']]
        for s in spots:
            if s['kind'] == 'MOTO' and car_centers:
                mc = center(s)
                _, t = min(car_centers, key=lambda ct: (ct[0][0]-mc[0])**2 + (ct[0][1]-mc[1])**2)
                if t != s['tower']:
                    qa['moto_assigned'].append((fl, s['code'], t))
                    s['tower'] = t

    # ---- grava floors.json ----
    json.dump(data, open(fp, 'w', encoding='utf-8'), ensure_ascii=False)

    # ---- grava catalogo.csv ----
    rows = [s for fl in data for s in data[fl]['spots']]
    cols_csv = ['floor', 'code', 'tower', 'cat', 'cap', 'pcd', 'kind', 'l', 'tp', 'w', 'h']
    with open(os.path.join(HERE, 'catalogo.csv'), 'w', newline='', encoding='utf-8') as f:
        wr = csv.DictWriter(f, fieldnames=cols_csv, extrasaction='ignore')
        wr.writeheader()
        wr.writerows(rows)

    # ---- QA report ----
    lines = []
    def sec(t, items, fmt):
        lines.append(f"\n## {t}: {len(items)}")
        for it in items[:50]:
            lines.append("  " + fmt(it))
        if len(items) > 50:
            lines.append(f"  ... +{len(items)-50}")
    sec("TORRE corrigida (code != cor antiga)", qa['tower_changed'],
        lambda x: f"piso {x[0]} {x[1]}: {x[2]} -> {x[3]}")
    sec("CATEGORIA corrigida (cor restrita)", qa['cat_changed'],
        lambda x: f"piso {x[0]} {x[1]}: cat {x[2]} -> {x[3]}")
    sec("MOTOS atribuidas a torre", qa['moto_assigned'],
        lambda x: f"piso {x[0]} {x[1]} -> torre {x[2]}")
    sec("CAT baixa confianca (mantida, revisar)", qa['low_conf_cat'],
        lambda x: f"piso {x[0]} {x[1]}: cat {x[2]} (cor sugere {x[3]}, dist {x[4]})")
    sec("Sem cor casada no PDF (cat mantida)", qa['no_color'],
        lambda x: f"piso {x[0]} {x[1]}")

    # distribuicao final
    dist_t = collections.Counter((s['floor'], s['tower']) for fl in data for s in data[fl]['spots'])
    lines.append("\n## DISTRIBUICAO FINAL torre x piso")
    for k in sorted(dist_t, key=lambda x: (x[0], str(x[1]))):
        lines.append(f"  piso {k[0]:>2} torre {k[1]}: {dist_t[k]}")

    report = "\n".join(lines)
    open(os.path.join(HERE, 'qa_report.txt'), 'w', encoding='utf-8').write(report)
    print(report)
    print("\n>> floors.json, catalogo.csv, qa_report.txt atualizados.")

if __name__ == '__main__':
    main()
