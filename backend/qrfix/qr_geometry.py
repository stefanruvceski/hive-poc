import numpy as np, cv2, zxingcpp, sys, glob, segno, reedsolo, json, time

# Time budget: every long loop calls tick(); past DEADLINE it raises Timeout (the serverless function has a hard limit).
DEADLINE = float('inf')
class Timeout(Exception): pass
def tick():
    if time.time() > DEADLINE: raise Timeout()
import segno.consts as C
from PIL import Image
from scipy.interpolate import RBFInterpolator
from scipy.ndimage import map_coordinates

MASKS=[lambda r,c:(r+c)%2==0, lambda r,c:r%2==0, lambda r,c:c%3==0, lambda r,c:(r+c)%3==0,
       lambda r,c:(r//2+c//3)%2==0, lambda r,c:(r*c)%2+(r*c)%3==0, lambda r,c:((r*c)%2+(r*c)%3)%2==0,
       lambda r,c:((r+c)%2+(r*c)%3)%2==0]
ECL={1:C.ERROR_LEVEL_L,0:C.ERROR_LEVEL_M,3:C.ERROR_LEVEL_Q,2:C.ERROR_LEVEL_H}
def bch_fmt(d):
    g=0b10100110111; v=d<<10
    for i in range(14,9,-1):
        if v>>i&1: v^=g<<(i-10)
    return ((d<<10)|v)^0b101010000010010
FMT={bch_fmt(d):d for d in range(32)}
def vbch(v):
    g=0b1111100100101; r=v<<12
    for i in range(17,11,-1):
        if r>>i&1: r^=g<<(i-12)
    return (v<<12)|r
pc=lambda x:bin(x).count('1')

class Layout:
    def __init__(s,ver):
        s.ver=ver; N=s.N=17+4*ver; al=s.al=list(C.ALIGNMENT_POS[ver-2]) if ver>1 else []
        F=np.zeros((N,N),bool)
        F[:9,:9]=F[:9,N-8:]=F[N-8:,:9]=True; F[6,:]=F[:,6]=True
        s.apos=[]
        for a in al:
            for b in al:
                if (a<9 and b<9) or (a<9 and b>N-9) or (a>N-9 and b<9): continue
                F[a-2:a+3,b-2:b+3]=True; s.apos.append((b+.5,a+.5))  # (u=col,v=row)
        if ver>=7: F[:6,N-11:N-8]=F[N-11:N-8,:6]=True
        s.F=F; pos=[]; c=N-1; up=True
        while c>0:
            if c==6: c-=1
            for r in (range(N-1,-1,-1) if up else range(N)):
                for cc in (c,c-1):
                    if not F[r,cc]: pos.append((r,cc))
            up=not up; c-=2
        s.POS=pos
    def fmt(s,M):
        N=s.N
        b1=[M[8,i] for i in (0,1,2,3,4,5,7,8)]+[M[i,8] for i in (7,5,4,3,2,1,0)]
        b2=[M[N-1-i,8] for i in range(7)]+[M[8,N-8+i] for i in range(8)]
        best=None
        for b in (b1,b2):
            x=int(''.join(map(str,b)),2); c=min(FMT,key=lambda c:pc(c^x)); d=pc(c^x)
            if best is None or d<best[1]: best=(FMT[c],d)
        return best
    def verdist(s,M):
        if s.ver<7: return 0
        N=s.N
        a=sum(int(M[i//3,N-11+i%3])<<i for i in range(18)); b=sum(int(M[N-11+i%3,i//3])<<i for i in range(18))
        return min(pc(vbch(s.ver)^a),pc(vbch(s.ver)^b))
    def decode(s,M):
        f,fd=s.fmt(M)
        if fd>3: return None,'fmt'
        ecl=ECL[f>>3]; mask=f&7
        groups=C.ECC[s.ver][ecl]
        blocks=[]; 
        for g in groups: blocks+= [g.num_data]*g.num_blocks
        ec=groups[0].num_total-groups[0].num_data
        total=sum(blocks)+ec*len(blocks)
        mf=MASKS[mask]
        bits=[int(M[r,c])^int(mf(r,c)) for r,c in s.POS]
        cw=[int(''.join(map(str,bits[i*8:i*8+8])),2) for i in range(total)]
        BI=[(b,i) for i in range(max(blocks)) for b,n in enumerate(blocks) if i<n]+[(b,blocks[b]+i) for i in range(ec) for b in range(len(blocks))]
        msgs=[[0]*(n+ec) for n in blocks]
        for k,(b,i) in enumerate(BI): msgs[b][i]=cw[k]
        rsc=reedsolo.RSCodec(ec); rep=[]; out=[]
        for b in range(len(blocks)):
            try: d,_,ep=rsc.decode(bytes(msgs[b])); rep.append(len(ep)); out.append(bytes(rsc.encode(d)))
            except Exception: rep.append(None); out.append(None)
        if None in rep: return None,rep
        cw2=[out[b][i] for b,i in BI]
        bits2=''.join(format(x,'08b') for x in cw2)
        ref=np.array(segno.make_qr('x',version=s.ver,error='LMQH'[[C.ERROR_LEVEL_L,C.ERROR_LEVEL_M,C.ERROR_LEVEL_Q,C.ERROR_LEVEL_H].index(ecl)],mask=mask,boost_error=False).matrix,dtype=int)
        M2=ref.copy()
        for j,(r,c) in enumerate(s.POS[:len(bits2)]): M2[r,c]=int(bits2[j])^int(mf(r,c))
        o=np.ones((s.N+8,)*2,np.uint8)*255; o[4:-4,4:-4]=(1-M2)*255
        big=cv2.resize(o,None,fx=8,fy=8,interpolation=cv2.INTER_NEAREST)
        r=zxingcpp.read_barcodes(big)
        return (r[0].text if r else None),rep

def pat(kind,m):
    if kind=='f': p=np.ones((9,9))*255; p[1:8,1:8]=0; p[2:7,2:7]=255; p[3:6,3:6]=0
    elif kind=='f7': p=np.zeros((7,7)); p[1:6,1:6]=255; p[2:5,2:5]=0
    else: p=np.ones((7,7))*255; p[1:6,1:6]=0; p[2:5,2:5]=255; p[3,3]=0
    s=p.shape[0]; z=max(3,round(s*m)); return cv2.resize(p.astype(np.float32),(z,z),interpolation=cv2.INTER_AREA)

class Img:
    def __init__(s,path,erode=0,scale=1.0):
        g=np.array(Image.open(path).convert('L'))
        if scale!=1: g=cv2.resize(g,None,fx=scale,fy=scale,interpolation=cv2.INTER_CUBIC)
        if erode: g=cv2.erode(g,np.ones((1,erode),np.uint8))
        s.g=g.astype(np.float32); s.blur=cv2.GaussianBlur(s.g,(3,3),0.8)
    def best(s,kind,m,x,y,R):
        t=pat(kind,m); h=t.shape[0]/2; H,W=s.g.shape
        x0=int(max(x-h-R,0)); y0=int(max(y-h-R,0)); x1=int(min(x+h+R,W)); y1=int(min(y+h+R,H))
        win=s.g[y0:y1,x0:x1]
        if win.shape[0]<t.shape[0] or win.shape[1]<t.shape[1]: return x,y,-1
        r=cv2.matchTemplate(win,t,cv2.TM_CCOEFF_NORMED); _,v,_,l=cv2.minMaxLoc(r)
        return x0+l[0]+h, y0+l[1]+h, v
    def samp(s,p): return map_coordinates(s.blur,[p[...,1],p[...,0]],order=1)
    def finders(s):
        H,W=s.g.shape; best=None
        for m in np.arange(3,16,0.5):
            tick()
            t=pat('f7',m)
            if t.shape[0]*4>min(H,W): break
            r=cv2.matchTemplate(s.g,t,cv2.TM_CCOEFF_NORMED); h=t.shape[0]/2
            pts=[];sc=0
            for (qx0,qx1,qy0,qy1) in ((0,W/2,0,H/2),(W/2,W,0,H/2),(0,W/2,H/2,H)):
                sub=r[int(qy0):int(qy1)-t.shape[0]+1,int(qx0):int(qx1)-t.shape[0]+1]
                _,v,_,l=cv2.minMaxLoc(sub); pts.append((qx0+l[0]+h,qy0+l[1]+h)); sc+=v
            if best is None or sc>best[0]: best=(sc,m,pts)
        return best

def fit(S,D,sm=0.5):
    return RBFInterpolator(np.array(S,float),np.array(D,float),kernel='thin_plate_spline',smoothing=sm,degree=1)

def build(img,L,fpts,m):
    N=L.N
    S=[(3.5,3.5),(N-3.5,3.5),(3.5,N-3.5)]; D=list(fpts)
    A,_=cv2.estimateAffine2D(np.float32(S),np.float32(D))
    found={}
    order=sorted(L.apos,key=lambda t:min(np.hypot(t[0]-a,t[1]-b) for a,b in S))
    for t in order:
        tick()
        SS=S+list(found); DD=D+[found[k] for k in found]
        if len(SS)>=6: px,py=fit(SS,DD,1.0)([t])[0]
        else: px,py=A@[t[0],t[1],1]
        X,Y,v=img.best('a',m,px,py,2.2*m)
        if v>0.3: found[t]=(X,Y)
    # outlier rejection
    for _ in range(3):
        keys=list(found); bad=[]
        for k in keys:
            SS=S+[x for x in keys if x!=k]; DD=D+[found[x] for x in keys if x!=k]
            if len(SS)<6: continue
            p=fit(SS,DD,1.0)([k])[0]
            if np.hypot(*(p-np.array(found[k])))>1.2*m: bad.append(k)
        if not bad: break
        worst=max(bad,key=lambda k:0); del found[bad[0]]
    # re-search missing near prediction
    SS=S+list(found); DD=D+[found[k] for k in found]; f=fit(SS,DD,1.0)
    for t in L.apos:
        if t in found: continue
        px,py=f([t])[0]; X,Y,v=img.best('a',m,px,py,1.0*m)
        if v>0.25: found[t]=(X,Y)
    return S+list(found), D+[found[k] for k in found]

def timing(img,f,N):
    aS=[];aD=[]
    for c in range(10,N-10,3):
        tick()
        for line in ('row','col'):
            idx=list(range(c-3,c+4))
            uv=np.array([(i+.5,6.5) for i in idx] if line=='row' else [(6.5,i+.5) for i in idx])
            exp=np.array([1 if i%2 else -1 for i in idx],float)
            base=f(uv); bb=(-9,0,0)
            for dx in np.arange(-3,3.1,.5):
                for dy in np.arange(-3,3.1,.5):
                    s=img.samp(base+[dx,dy]); s=s-s.mean()
                    sc=(s*exp).sum()/(np.linalg.norm(s)*np.linalg.norm(exp)+1e-6)
                    if sc>bb[0]: bb=(sc,dx,dy)
            if bb[0]>0.85:
                ctr=(c+.5,6.5) if line=='row' else (6.5,c+.5)
                aS.append(ctr); aD.append(tuple(f([ctr])[0]+[bb[1],bb[2]]))
    return aS,aD

def snap(img,xy,N,fS,fD,P,R,step=2):
    UV=np.array([(i+.5,j+.5) for j in range(N) for i in range(N)])
    Sx=[];Dx=[]
    for j in range(0,N-P+1,step):
        tick()
        for i in range(0,N-P+1,step):
            patch=xy[j:j+P,i:i+P]; bb=(-1,0,0)
            for dx in np.arange(-R,R+.01,.5):
                for dy in np.arange(-R,R+.01,.5):
                    v=img.samp(patch+[dx,dy]); sc=np.abs(v-np.median(v)).mean()
                    if sc>bb[0]: bb=(sc,dx,dy)
            c=P//2; Sx.append((i+c+.5,j+c+.5)); Dx.append(patch[c,c]+[bb[1],bb[2]])
    ff=RBFInterpolator(np.vstack([np.array(Sx),np.array(fS)]),np.vstack([np.array(Dx),np.array(fD)]),kernel='thin_plate_spline',smoothing=2,degree=1)
    return ff(UV).reshape(N,N,2)

def tryvals(L,vals):
    best=None
    for k in (15,9,11,21):
        loc=cv2.blur(vals,(k,k))
        for off in (0,-3,3):
            M=(vals<loc+off).astype(int)
            t,rep=L.decode(M)
            if t: return t,rep
            if isinstance(rep,list):
                n=sum(1 for r in rep if r is not None)
                if best is None or n>best: best=n
    return None,best

def solve(path,erode=0,versions=None,fpts=None,m=None,scale=1.0,log=print):
    img=Img(path,erode,scale)
    if fpts is None:
        sc,m,fpts=img.finders(); log(f' finders m={m} score={sc:.2f} {[(round(a),round(b)) for a,b in fpts]}')
    fpts=[img.best('f7',m,x,y,m*1.5)[:2] for x,y in fpts]
    d=np.hypot(fpts[1][0]-fpts[0][0],fpts[1][1]-fpts[0][1]); d2=np.hypot(fpts[2][0]-fpts[0][0],fpts[2][1]-fpts[0][1])
    Nest=(d+d2)/2/m+7; vest=(Nest-17)/4
    if versions is None: versions=sorted(range(max(7,int(vest)-4),min(40,int(vest)+5)),key=lambda v:abs(v-vest))
    cands=[]
    for v in versions:
        L=Layout(v); N=L.N; mm=(d+d2)/2/(N-7)
        S,D=build(img,L,fpts,mm)
        f=fit(S,D,0.5); UV=np.array([(i+.5,j+.5) for j in range(N) for i in range(N)])
        xy=f(UV).reshape(N,N,2); vals=img.samp(xy); loc=cv2.blur(vals,(15,15)); M=(vals<loc).astype(int)
        vd=L.verdist(M); fd=L.fmt(M)[1]
        log(f'  v{v} N={N} align {len(S)-3}/{len(L.apos)} verdist={vd} fmtdist={fd}')
        cands.append((vd+fd,v,L,S,D,mm))
    cands.sort(key=lambda c:c[0])
    for score,v,L,S,D,mm in cands[:2]:
        if score>6: continue
        N=L.N; UV=np.array([(i+.5,j+.5) for j in range(N) for i in range(N)])
        f=fit(S,D,0.5); aS,aD=timing(img,f,N); fS=S+aS; fD=D+aD
        f=fit(fS,fD,0.5); xy=f(UV).reshape(N,N,2)
        t,info=tryvals(L,img.samp(xy)); log(f'  v{v} base ok-blocks={info}')
        if t: return t
        for it,(P,R) in enumerate([(9,2.5),(7,1.5),(5,1)]):
            xy=snap(img,xy,N,fS,fD,P,R*mm/7)
            t,info=tryvals(L,img.samp(xy)); log(f'  v{v} snap{it} ok-blocks={info}')
            if t: return t
    return None

if __name__=='__main__':
    path=sys.argv[1]; erode=int(sys.argv[2]) if len(sys.argv)>2 else 0
    t=solve(path,erode)
    print('RESULT', t)
