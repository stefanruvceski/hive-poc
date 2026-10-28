import numpy as np, cv2, zxingcpp, sys, segno, reedsolo, string, re
import segno.consts as C
from .qr_geometry import *
from . import qr_geometry as G
RANDOM_TRIALS=40
B64=set((string.ascii_letters+string.digits+'%').encode())
class Collector:
    def __init__(s,L,Lguess=None): s.L=L; s.fmt=None; s.got={}; s.Lguess=Lguess
    def known(s):
        L=s.Lguess or s.length()
        if L is None: return {}
        nd=sum(s.blocks)
        bits='0100'+format(L,'016b')+''.join(format(c,'08b') for c in b'https://suf.purs.gov.rs/v/?vl=')
        kn={}
        for j in range(len(bits)//8): kn[j]=int(bits[8*j:8*j+8],2)
        end=4+16+8*L; t=min(4,nd*8-end); endb=end+t; endb=(endb+7)//8
        # codeword containing end: partial, skip; full padding after
        pad=[0xEC,0x11]
        for j,idx in enumerate(range(endb,nd)): kn[idx]=pad[j%2]
        return kn
    def setup(s,M):
        f,fd=s.L.fmt(M)
        if fd>3: return False
        if s.fmt is None:
            s.fmt=f; ecl=ECL[f>>3]; s.ecl=ecl
            groups=C.ECC[s.L.ver][ecl]; s.blocks=[]
            for g in groups: s.blocks+=[g.num_data]*g.num_blocks
            s.ec=groups[0].num_total-groups[0].num_data
            s.total=sum(s.blocks)+s.ec*len(s.blocks)
            s.BI=[(b,i) for i in range(max(s.blocks)) for b,n in enumerate(s.blocks) if i<n]+[(b,s.blocks[b]+i) for i in range(s.ec) for b in range(len(s.blocks))]
            s.rsc=reedsolo.RSCodec(s.ec)
        return True
    def feed(s,M,conf):
        if not s.setup(M): return
        mf=MASKS[s.fmt&7]; P=s.L.POS
        bits=[int(M[r,c])^int(mf(r,c)) for r,c in P]
        cw=[int(''.join(map(str,bits[i*8:i*8+8])),2) for i in range(s.total)]
        cc=[min(conf[r,c] for r,c in P[i*8:i*8+8]) for i in range(s.total)]
        nb=len(s.blocks); msgs=[[0]*(n+s.ec) for n in s.blocks]; mc=[[0]*(n+s.ec) for n in s.blocks]
        for k,(b,i) in enumerate(s.BI): msgs[b][i]=cw[k]; mc[b][i]=cc[k]
        for k,val in s.known().items():
            b,i=s.seq(k); msgs[b][i]=val; mc[b][i]=1e9
        for b in range(nb):
            G.tick()
            if b in s.got: continue
            order=sorted(range(len(msgs[b])),key=lambda i:mc[b][i])
            order=[i for i in order if mc[b][i]<1e8]
            trials=[order[:e] for e in [0]+list(range(4,s.ec+1,2))]
            if s.Lguess or s.length():
                rng=np.random.default_rng(b)
                pool=order[:min(len(order),3*s.ec)]
                for _ in range(RANDOM_TRIALS):
                    e=int(rng.integers(s.ec-6,s.ec+1)); trials.append(list(rng.choice(pool,size=min(e,len(pool)),replace=False)))
            for er in trials:
                try:
                    d,_,ep=s.rsc.decode(bytes(msgs[b]),erase_pos=[int(x) for x in er])
                    if not s.valid(b,bytes(d)): continue
                    s.got[b]=(bytes(d),len(er)); break
                except Exception: pass
    def seq(s,k):
        for b,n in enumerate(s.blocks):
            if k<n: return b,k
            k-=n
    def length(s):
        if 0 not in s.got: return None
        st=list(s.got[0][0][:3])
        bits=''.join(format(x,'08b') for x in st)
        if bits[:4]!='0100': return None
        return int(bits[4:20],2)
    def valid(s,b,d):
        if b==0:
            bits=''.join(format(x,'08b') for x in d[:40])
            if bits[:4]!='0100': return False
            body=bytes(int(bits[20+8*i:28+8*i],2) for i in range(30))
            if body!=b'https://suf.purs.gov.rs/v/?vl=': return False
            Lh=int(bits[4:20],2)
            if s.Lguess and Lh!=s.Lguess: return False
            if not (600<=Lh<=sum(s.blocks)-3): return False
        L=s.Lguess or s.length() or (int(''.join(format(x,'08b') for x in d[:3])[4:20],2) if b==0 else None)
        if L is None: return False
        nb=len(s.blocks)
        off=sum(s.blocks[:b])
        for i,x in enumerate(d):
            k=off+i
            if 33<=k<L+2 and (x&15) not in (2,3,4,5,6,7): return False
        return True
    def done(s): return s.fmt is not None and len(s.got)==len(s.blocks)
    def result(s):
        out=[bytes(s.rsc.encode(s.got[b][0])) for b in range(len(s.blocks))]
        cw2=[out[b][i] for b,i in s.BI]; bits2=''.join(format(x,'08b') for x in cw2)
        mask=s.fmt&7; mf=MASKS[mask]
        lvl='LMQH'[[C.ERROR_LEVEL_L,C.ERROR_LEVEL_M,C.ERROR_LEVEL_Q,C.ERROR_LEVEL_H].index(s.ecl)]
        M2=np.array(segno.make_qr('x',version=s.L.ver,error=lvl,mask=mask,boost_error=False).matrix,dtype=int)
        for j,(r,c) in enumerate(s.L.POS[:len(bits2)]): M2[r,c]=int(bits2[j])^int(mf(r,c))
        o=np.ones((s.L.N+8,)*2,np.uint8)*255; o[4:-4,4:-4]=(1-M2)*255
        big=cv2.resize(o,None,fx=8,fy=8,interpolation=cv2.INTER_NEAREST)
        r=zxingcpp.read_barcodes(big)
        if not r: return None
        t=r[0].text
        m=re.match(r'^https://suf\.purs\.gov\.rs/v/\?vl=([A-Za-z0-9%]+)$',t)
        return t if m else ('SUSPECT '+t)
def feedall(col,vals):
    for k in (15,9,11,21):
        loc=cv2.blur(vals,(k,k))
        for off in (0,-3,3):
            col.feed((vals<loc+off).astype(int),np.abs(vals-loc-off))
            if col.done(): return True
    return False
def solve2(path,erode=0,versions=None,fpts=None,m=None,scale=1.0,log=print,Lguess=None):
    img=Img(path,erode,scale)
    if fpts is None:
        sc,m,fpts=img.finders(); log(f' finders m={m} score={sc:.2f} {[(round(a),round(b)) for a,b in fpts]}')
    else: fpts=[(x*scale,y*scale) for x,y in fpts]; m=m*scale
    fpts=[img.best('f7',m,x,y,m*1.5)[:2] for x,y in fpts]
    d=np.hypot(fpts[1][0]-fpts[0][0],fpts[1][1]-fpts[0][1]); d2=np.hypot(fpts[2][0]-fpts[0][0],fpts[2][1]-fpts[0][1])
    vest=((d+d2)/2/m+7-17)/4
    if versions is None: versions=sorted(range(max(7,int(vest)-4),min(40,int(vest)+5)),key=lambda v:abs(v-vest))
    cands=[]
    for v in versions:
        G.tick()
        L=Layout(v); N=L.N; mm=(d+d2)/2/(N-7)
        S,D=build(img,L,fpts,mm)
        UV=np.array([(i+.5,j+.5) for j in range(N) for i in range(N)])
        xy=fit(S,D,0.5)(UV).reshape(N,N,2); vals=img.samp(xy); M=(vals<cv2.blur(vals,(15,15))).astype(int)
        vd=L.verdist(M); fd=L.fmt(M)[1]; log(f'  v{v} align {len(S)-3}/{len(L.apos)} verdist={vd} fmtdist={fd}')
        cands.append((vd+fd,v,L,S,D,mm))
    cands.sort(key=lambda c:c[0])
    for score,v,L,S,D,mm in cands[:2]:
        if score>6: continue
        N=L.N; UV=np.array([(i+.5,j+.5) for j in range(N) for i in range(N)])
        col=Collector(L,Lguess)
        f=fit(S,D,0.5); xy=f(UV).reshape(N,N,2)
        if feedall(col,img.samp(xy)): return col.result()
        aS,aD=timing(img,f,N); fS=S+aS; fD=D+aD
        xy=fit(fS,fD,0.5)(UV).reshape(N,N,2)
        if feedall(col,img.samp(xy)): return col.result()
        log(f'  v{v} base blocks {len(col.got)}')
        for it,(P,R) in enumerate([(9,2.5),(7,1.5),(5,1),(11,3),(5,1)]):
            xy=snap(img,xy,N,fS,fD,P,R*mm/7)
            if feedall(col,img.samp(xy)): return col.result()
            log(f'  v{v} snap{it} blocks {len(col.got)}/{len(col.blocks) if col.fmt is not None else "?"}')
    return None
if __name__=='__main__':
    import json
    a=json.loads(sys.argv[1])
    print('RESULT',solve2(**a))
