# -*- coding: utf-8 -*-
"""
Gera preview-vagas.html a partir do floors.json (ja corrigido pelo rework.py)
+ os PNGs das plantas. Reconstroi o gerador que havia se perdido.

Contexto simulado do morador: Unidade A-1502, Torre A, vaga DUPLA (cap=2).
Status de cada vaga (assado na geracao):
  - other (cinza): outra torre OU outra categoria/kind  -> nao e da sua cota
  - taken (vermelho): da sua cota mas ja escolhida (ocupacao simulada deterministica)
  - avail (azul): da sua cota e livre
"""
import json, base64, os

HERE = os.path.dirname(os.path.abspath(__file__))
PNG = {-1: 'piso-1.png', 0: 'piso0.png', 1: 'piso1.png', 2: 'piso2.png'}
USER_TOWER = 'A'
USER_CAP = 2  # vaga dupla

def b64(path):
    with open(path, 'rb') as f:
        return base64.b64encode(f.read()).decode()

def status(s):
    """avail | taken | other para o contexto do morador A / dupla."""
    if s['kind'] != 'CAR' or s['tower'] != USER_TOWER or s['cap'] != USER_CAP:
        return 'other'
    # ocupacao simulada deterministica (~35%) pelo numero da vaga (distribui uniforme)
    num = int(''.join(ch for ch in s['code'] if ch.isdigit()) or 0)
    return 'taken' if (num * 7) % 100 < 35 else 'avail'

def main():
    data = json.load(open(os.path.join(HERE, 'floors.json'), encoding='utf-8'))

    n_avail = sum(1 for fl in data for s in data[fl]['spots'] if status(s) == 'avail')
    n_car = sum(1 for fl in data for s in data[fl]['spots'] if s['kind'] == 'CAR')

    head = f"""<!DOCTYPE html><html lang=pt-br><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1"><title>Escolha de vaga</title>
<style>
body{{margin:0;font-family:'Segoe UI',sans-serif;background:#0e0e14;color:#e8e8f0}}
header{{padding:12px 18px;background:#15151f;border-bottom:1px solid #28283c;position:sticky;top:0;z-index:5}}
h1{{font-size:16px;margin:0 0 4px}} .sub{{font-size:12px;color:#a8a8c0}}
.legend{{display:flex;gap:14px;font-size:12px;margin-top:8px;flex-wrap:wrap}} .legend span{{display:flex;align-items:center;gap:6px}} .sw{{width:14px;height:14px;border-radius:3px;display:inline-block}}
.tabs{{margin-top:8px;display:flex;gap:6px}} .tabs button{{background:#1c1c28;border:1px solid #34344a;color:#b9b9d0;padding:6px 14px;border-radius:7px;font-size:13px;cursor:pointer}} .tabs .on{{background:#3b3bd6;border-color:#3b3bd6;color:#fff}}
.wrap{{position:relative;width:100%;max-width:1600px;margin:0 auto}} .wrap img{{width:100%;display:block;filter:grayscale(1) brightness(1.18) contrast(.92)}}
.sp{{position:absolute;box-sizing:border-box;border:1.5px solid rgba(255,255,255,.25);border-radius:2px}}
.sp.avail{{background:rgba(47,111,237,.8);border-color:#2f6fed;cursor:pointer}} .sp.avail:hover{{background:rgba(47,111,237,.95)}}
.sp.taken{{background:rgba(217,54,54,.8);border-color:#d93636}} .sp.other{{background:rgba(120,120,138,.55)}}
.sp.sel{{background:rgba(47,174,90,.92);border-color:#9affc4}}
.bar{{position:fixed;bottom:0;left:0;right:0;background:#15151f;border-top:1px solid #28283c;padding:11px 18px;display:flex;justify-content:space-between;align-items:center;gap:10px}}
.bar b{{color:#fff}} .btn{{background:#2fae5a;color:#06210f;font-weight:700;border:none;padding:10px 20px;border-radius:9px;font-size:14px;cursor:pointer}} .btn:disabled{{background:#2a3b30;color:#5e7a68;cursor:not-allowed}}
</style>
<header>
<h1>Escolha sua vaga &middot; Unidade A-1502 &middot; Torre A &middot; 2 vagas (dupla)</h1>
<div class=sub>Pr&eacute;via com dados reais: {n_car} vagas de carro extra&iacute;das das 4 plantas &middot; {n_avail} dispon&iacute;veis da sua categoria (Torre A, dupla). Torre derivada do c&oacute;digo (T1/T2/T3).</div>
<div class=legend><span><i class="sw" style="background:#2f6fed"></i>Dispon&iacute;vel</span><span><i class="sw" style="background:#d93636"></i>J&aacute; escolhida</span><span><i class="sw" style="background:#5a5a69"></i>Outra torre/categoria</span><span><i class="sw" style="background:#2fae5a"></i>Selecionada</span></div>
<div class=tabs>"""

    floors = ['-1', '0', '1', '2']
    head += "".join(
        f'<button class="{"on" if fl=="-1" else ""}" onclick="tab(\'{fl}\',this)">Piso {fl}</button>'
        for fl in floors) + "</div>\n</header>\n"

    body = ""
    for fl in floors:
        img = b64(os.path.join(HERE, PNG[int(fl)]))
        disp = 'block' if fl == '-1' else 'none'
        body += f'<div class="pan" data-fl="{fl}" style="display:{disp}"><div class="wrap"><img src="data:image/png;base64,{img}">'
        for s in data[fl]['spots']:
            st = status(s)
            body += (f'<div class="sp {st}" style="left:{s["l"]}%;top:{s["tp"]}%;'
                     f'width:{s["w"]}%;height:{s["h"]}%" data-code="{s["code"]}" '
                     f'data-st="{st}" onclick="pick(this)"></div>')
        body += '</div></div>\n'

    footer = """<div class=bar><div id=st>Toque numa vaga <b>azul</b> (em qualquer piso) para escolher sua vaga dupla.</div><button class=btn id=go disabled>Confirmar vaga</button></div>
<script>
var sel=null;
function tab(k,b){document.querySelectorAll('.pan').forEach(p=>p.style.display=p.dataset.fl===k?'block':'none');document.querySelectorAll('.tabs button').forEach(x=>x.classList.remove('on'));b.classList.add('on');}
function pick(el){ if(el.dataset.st!=='avail'&&el!==sel)return;
 if(sel){sel.classList.remove('sel');sel.classList.add('avail');sel.dataset.st='avail';}
 if(sel===el){sel=null;document.getElementById('go').disabled=true;document.getElementById('st').innerHTML='Toque numa vaga <b>azul</b> para escolher.';return;}
 el.classList.remove('avail');el.classList.add('sel');el.dataset.st='sel';sel=el;
 document.getElementById('go').disabled=false;document.getElementById('st').innerHTML='Vaga <b>'+el.dataset.code+'</b> selecionada. Pode confirmar.';}
</script></html>"""

    html = head + body + footer
    out = os.path.join(HERE, 'preview-vagas.html')
    open(out, 'w', encoding='utf-8').write(html)
    print(f"preview-vagas.html gerado: {len(html)//1024} KB, {n_avail} vagas azuis (Torre A dupla), {n_car} carros.")

if __name__ == '__main__':
    main()
