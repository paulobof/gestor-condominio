import fitz, re, collections, csv, os, tempfile
PDFS={-1:r"C:\Users\paulo\Downloads\04112019 - GAR SP - HELBOR TRILOGY SBC - AGRUPAMENTO - -1 -REV 05 (1).pdf",
 0:r"C:\Users\paulo\Downloads\04112019 - GAR SP - HELBOR TRILOGY SBC - AGRUPAMENTO - 0 -REV 05 (1).pdf",
 1:r"C:\Users\paulo\Downloads\04112019 - GAR SP - HELBOR TRILOGY SBC - AGRUPAMENTO -  1  -REV 05 (2).pdf",
 2:r"C:\Users\paulo\Downloads\04112019 - GAR SP - HELBOR TRILOGY SBC - AGRUPAMENTO -  2  -REV 05 (1).pdf"}
CMAP={('A',1):(0.87,1.0,0.5),('A',2):(0.65,0.87,0.43),('A',3):(0.0,0.58,0.29),
      ('B',1):(1.0,0.75,0.0),('B',2):(1.0,0.25,0.0),('B',3):(0.87,0.0,0.0),
      ('C',1):(1.0,0.5,1.0),('C',2):(0.5,0.0,1.0),('C',3):(0.63,0.36,0.72)}
COLOR2CAT={v:k for k,v in CMAP.items()}
def nearest_cat(col):
    best=None;bd=0.12
    for c,k in COLOR2CAT.items():
        d=sum((a-b)**2 for a,b in zip(col,c))**.5
        if d<bd: bd=d;best=k
    return best
def C(b): return ((b[0]+b[2])/2,(b[1]+b[3])/2)
AB=re.compile(r'^\d+[AB]$')
allrows=[]; summ={}
for fl,path in PDFS.items():
    pg=fitz.open(path)[0]; words=pg.get_text("words")
    fills=[(tuple(round(c,2) for c in d['fill']),d['rect']) for d in pg.get_drawings() if d.get('fill') and d.get('rect')]
    carrects=[(nearest_cat(col),r) for col,r in fills if nearest_cat(col) and 40<=min(r.width,r.height)<=75 and 88<=max(r.width,r.height)<=260]
    exids={id(w) for w in words if re.match(r'^[123][123]01$',w[4]) and w[1]>2400}  # legend region (y>2400)
    seen=set(); rows=[]
    for i,w in enumerate(words[:-1]):
        nxt=words[i+1]
        if re.match(r'^T[123]$',w[4]) and re.match(r'^\d{4}$',nxt[4]) and id(nxt) not in exids:
            num=nxt[4]; code=f"T{w[4][1]} {num}"
            if code in seen: continue
            cx,cy=C(nxt)
            inside=[(cat,r) for cat,r in carrects if r.x0-3<=cx<=r.x1+3 and r.y0-3<=cy<=r.y1+3]
            if not inside:
                inside=sorted(carrects,key=lambda cr:(C(cr[1])[0]-cx)**2+(C(cr[1])[1]-cy)**2)[:1]
                if not inside: continue
            cat,r=max(inside,key=lambda cr:cr[1].width*cr[1].height); seen.add(code)
            ws=[x[4] for x in words if r.x0<=C(x)[0]<=r.x1 and r.y0<=C(x)[1]<=r.y1]
            ab=sorted(set(t for t in ws if AB.match(t)))
            cap=2 if len(ab)>=2 or max(r.width,r.height)>155 else 1
            rows.append(dict(floor=fl,code=code,tower=cat[0],category=cat[1],capacity=cap,sub=";".join(ab),pcd='PNE' in ws,kind='CAR'))
    for i,w in enumerate(words[:-1]):
        if w[4]=='MOTO' and re.match(r'^\d{2,3}$',words[i+1][4]):
            rows.append(dict(floor=fl,code=f"MOTO {words[i+1][4]}",tower=None,category=None,capacity=1,sub='',pcd=False,kind='MOTO'))
    allrows+=rows
    cars=[r for r in rows if r['kind']=='CAR']
    summ[fl]=dict(car=len(cars),moto=sum(1 for r in rows if r['kind']=='MOTO'),
                  dist=dict(sorted(collections.Counter((r['tower'],r['category'],r['capacity']) for r in cars).items())))
for fl in (-1,0,1,2):
    print(f"PISO {fl}: car={summ[fl]['car']} moto={summ[fl]['moto']}  {summ[fl]['dist']}")
print("\nTOTAL carros:",sum(1 for r in allrows if r['kind']=='CAR'),"| motos:",sum(1 for r in allrows if r['kind']=='MOTO'))
c3=[r for r in allrows if r['category']==3]
print("cat-3 reais (excl. legenda):",len(c3),[r['code'] for r in c3][:20])
cap1cat2=[r for r in allrows if r['category']==2 and r['capacity']==1]
print("cat-2 com capacity 1 (suspeito):",len(cap1cat2),[(r['floor'],r['code'],r['sub']) for r in cap1cat2][:8])
out=os.path.join(tempfile.gettempdir(),'parking_catalogo.csv')
with open(out,'w',newline='',encoding='utf-8') as f:
    wr=csv.DictWriter(f,fieldnames=['floor','code','tower','category','capacity','sub','pcd','kind']); wr.writeheader(); wr.writerows(allrows)
print("CSV:",out,"linhas:",len(allrows))
