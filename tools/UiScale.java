import java.nio.*;import java.nio.file.*;import java.util.*;
// UI scale for client.dll 7.6005 (applied on top of the QoL patch).
// java tools\UiScale.java <client.dll> <scale e.g. 2.0> <out.dll> [<out.patch>]
// The UI lays out at screen/scale (WidgetManager Init/SetScreenSize, the UI's render-device size reads and
// ScreenWidth/Height reads), screen-space DrawPrim vertices are multiplied by scale while WidgetManager::Render
// runs, and mouse coordinates going to the UI (widget events, the cursor widget, tooltip placement) are divided.
// The scale in use is max(1, min(requested, width/800, height/600)), worked out on every UI resize, so the UI never
// lays out smaller than the 800x600 it was made for (the 800x600 loading area stays at 1x).
public class UiScale{
 static final long BASE=0x62000000L;
 static final int CAVE=900;
 // writable scratch in STLPORT_ (the QoL patch uses +e00..+e0b)
 static final long D_FLAG=0x62b27f00L,D_UIW=0x62b27f04L,D_UIH=0x62b27f08L,D_SCALE=0x62b27f0cL,D_INV=0x62b27f10L;
 static final long DEV_TRANSFORM=0x629fa7c4L; // ILTDrawPrim transform type, 1 = screen space
 static final long WM_INIT=0x6242c810L,WM_SETSIZE=0x624278f0L,WM_RENDER=0x624299d0L,WM_POST=0x62427d70L;
 static final long VT_INIT=0x628b98a0L,VT_SETSIZE=0x628b98b0L,VT_RENDER=0x628b993cL,VT_POST=0x628b9950L;
 static final long CURSOR_MOVE=0x620a7a40L,GET_MOUSE=0x6235eb20L;
 // the cursor widget and its mouse-over text are drawn after WidgetManager::Render, in a second BeginRender/EndRender
 // pass, by this function; scale it like the rest of the UI
 static final long CURSOR_DRAW=0x620a7700L;
 static final long[] CURSOR_DRAW_CALLS={0x6215a1e5L,0x621307a4L};
 static final long SET_VIEWPORT_CALL=0x6233c817L; // the only IDirect3DDevice9::SetViewport call

 static final long[] CURSOR_CALLS={0x62123a9dL},MOUSE_CALLS={0x620996a6L,0x620a4d13L};
 // camera screen-to-ray (`call [reg+0xa0]`, ecx = camera, args float x, y, out) for world picking under the cursor; the
 // cursor and widget positions passed in are UI pixels, so multiply them back to screen pixels
 // render-device viewport rect reads (`call [reg+0xac]`, ecx = device, arg rect) used to project world points into
 // screen pixels for widgets placed over characters: name tags and two other overhead markers; return it in UI pixels
 static final long[] OVERHEAD_VP_CALLS={0x625a87f0L,0x62607d07L,0x62610b88L};
 static final long[] PICK_CALLS={0x6209b31dL,0x6209ea2eL,0x6209eb5dL,0x6209ed47L,0x6215b89cL};
 // the mouse events the input callbacks post ('MMov','MLDn','MLUp','MLDC','MRDn','MRUp','MRDC','MWUp','MWDn'); many other
 // event codes also start with 'M' and carry ids rather than coordinates at +0x14/+0x18
 static final long[] MOUSE_EVENTS={0x766f4d4dL,0x6e444c4dL,0x70554c4dL,0x43444c4dL,0x6e44524dL,0x7055524dL,0x4344524dL,0x7055574dL,0x6e44574dL};
 // render-device GetScreenWidth/Height reads (`call [reg+0x104/0x108]`, ecx = device) in UI code: view manager window
 // placement and clamping, message box, tooltip, cursor drag, atlas. Camera, Bink, screenshots and video mode are left alone.
 static final long[] UI_SIZE_READS={
  0x62008d97L,0x62008dafL,0x62008ddaL,0x62008df2L,0x62008e0cL,0x62008e21L,0x62008e3dL,0x62008e57L,0x620164ffL,0x62016512L,0x6201653dL,
  0x62016550L,0x6201656fL,0x62016584L,0x620165a3L,0x620165b8L,0x62018e31L,0x62018e41L,0x620190ebL,0x62019110L,0x62019141L,0x62019178L,
  0x62019197L,0x620191afL,0x620191e0L,0x62019209L,0x6201921dL,0x6201924bL,0x62019271L,0x6201928aL,0x6201929eL,0x620192aeL,0x62019337L,
  0x62019350L,0x62019373L,0x62019389L,0x620193a6L,0x620193b8L,0x620193d7L,0x620193e9L,0x62019406L,0x62019418L,0x620194a4L,0x620194b4L,
  0x620194c9L,0x620194d7L,0x620194f9L,0x6201950aL,0x6201951fL,0x6201952dL,0x620196ceL,0x620196dcL,0x620196f6L,0x62019704L,0x62019724L,
  0x62019732L,0x6201974aL,0x62019758L,0x6201bc01L,0x6201bc11L,0x6206c05dL,0x6206c06bL,0x6206c08cL,0x6206c09aL,0x6206c0c1L,0x6206c0cfL,
  0x6206c0e9L,0x6206c0f7L,0x6209971fL,0x620a65acL,0x620a65c7L,0x620e7fd0L,0x620e7fe8L,0x6218e12bL,0x6218e143L,0x6218e16bL,0x6218e183L,
  0x6218e199L,0x6218e1aeL,0x6218e1cdL,0x6218e1e2L};
 // every IDirect3DDevice9::DrawPrimitiveUP call in client.dll (all inside the ILTDrawPrim implementation)
 static final long[] DPUP_CALLS={
  0x62331866L,0x62331a64L,0x62331bd5L,0x62331da9L,0x62331f99L,0x62332171L,0x623322f4L,0x623325f0L,0x623326adL,0x62332831L,0x6233296dL,
  0x62332b9aL,0x62332cdcL,0x62332e38L,0x62332ff0L,0x623331d3L,0x62333381L,0x623334e5L,0x6233367aL,0x6233384eL,0x623339e2L,0x62333b48L,
  0x62333ca7L,0x62333e05L,0x62333f4dL,0x623340c2L,0x623342a1L,0x623344b5L,0x623346abL,0x6233482fL,0x623349e5L,0x62334becL,0x62334dcbL,
  0x62334f4bL,0x623350a3L,0x6233526eL,0x623353ceL,0x62335548L,0x6233568cL,0x62335845L,0x6233599eL,0x62335b1bL,0x62335c71L,0x62335e2cL,
  0x62335f88L,0x623360f8L,0x6233623aL,0x623363f3L,0x62336540L,0x62337558L,0x6233775aL,0x62337987L,0x62337b3aL,0x62337d4cL,0x62337f23L,
  0x62338214L,0x623382c9L,0x623385b3L,0x62338708L,0x62338927L,0x62338aa5L};
 static final long[][] CVAR_READS={{0x62019769L,2,D_UIW},{0x62019779L,1,D_UIH},{0x620ae61cL,1,D_UIW}};

 static byte[] img;static ByteBuffer B;
 static int off(long va){return (int)(va-BASE);} // .text/.rdata/STLPORT_ raw == rva below .data; checked in main
 static int u8(long va){return img[off(va)]&0xff;}

 // ---- tiny assembler ----
 static class Asm{long org;java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();java.util.Map<String,Long> lab=new HashMap<>();
  List<Object[]> fix=new ArrayList<>();List<Long> abs=new ArrayList<>();
  Asm(long org){this.org=org;}
  long pc(){return org+b.size();}
  Asm by(int... x){for(int v:x)b.write(v);return this;}
  Asm d32(long v){for(int i=0;i<4;i++)b.write((int)(v>>(8*i)));return this;}
  Asm abs(long v){abs.add(pc());return d32(v);}            // absolute address operand: needs a relocation
  Asm rel(String l){fix.add(new Object[]{pc(),l,4});return d32(0);}
  Asm rel(long target){return d32(target-(pc()+4));}
  Asm rel8(String l){fix.add(new Object[]{pc(),l,1});b.write(0);return this;}
  Asm L(String l){lab.put(l,pc());return this;}
  byte[] done(){byte[] x=b.toByteArray();for(Object[] f:fix){long at=(Long)f[0];long t=lab.get((String)f[1]);int n=(Integer)f[2];long d=t-(at+n);
   if(n==1){if(d<-128||d>127)throw new RuntimeException("rel8 "+f[1]);x[(int)(at-org)]=(byte)d;}else for(int i=0;i<4;i++)x[(int)(at-org)+i]=(byte)(d>>(8*i));}return x;}
  long at(String l){return lab.get(l);}
 }

 public static void main(String[] a)throws Exception{
  img=Files.readAllBytes(Path.of(a[0]));B=ByteBuffer.wrap(img).order(ByteOrder.LITTLE_ENDIAN);
  float scale=Float.parseFloat(a[1]);
  byte[] orig=img.clone();
  // STLPORT_ is rva b27000, raw 9de000
  for(long v=D_FLAG;v<D_INV+4;v++){int fo=(int)(v-0x62b27000L+0x9de000L);if(img[fo]!=0)throw new RuntimeException("scratch not zero @"+Long.toHexString(v));}

  // ---- check sites ----
  List<long[]> getSites=new ArrayList<>(); // {va, 0=width 1=height}
  for(long v:UI_SIZE_READS){int d=B.getInt(off(v)+2);
   if(u8(v)!=0xff||(u8(v+1)&0xf8)!=0x90||(d!=0x104&&d!=0x108))throw new RuntimeException("size read "+Long.toHexString(v));
   getSites.add(new long[]{v,d==0x104?0:1});}
  for(long v:DPUP_CALLS)if(u8(v)!=0xff||(u8(v+1)&0xf8)!=0x90||B.getInt(off(v)+2)!=0x14c)throw new RuntimeException("DrawPrimitiveUP call "+Long.toHexString(v));
  List<Long> dpup=new ArrayList<>();for(long v:DPUP_CALLS)dpup.add(v);

  // ---- cave ----
  long cave=findCave(CAVE);
  System.out.printf("cave at %x%n",cave);
  Asm s=new Asm(cave);
  // the scale in use and its inverse start at 1.0 (written into the file) and are set by "fit"
  for(long v:new long[]{D_SCALE,D_INV})B.putFloat((int)(v-0x62b27000L+0x9de000L),1f);
  s.L("req");s.d32(Float.floatToIntBits(scale));
  s.L("k800");s.d32(Float.floatToIntBits(1f/800));
  s.L("k600");s.d32(Float.floatToIntBits(1f/600));
  // eax = round(eax / scale); keeps ecx/edx
  s.L("div").by(0x50, 0xdb,0x04,0x24, 0xd8,0x0d).abs(D_INV).by(0xdb,0x1c,0x24, 0x58, 0xc3);
  // render-device GetScreenWidth/Height for UI code (ecx = device)
  s.L("getw").by(0x8b,0x41,0x24, 0xe9).rel("div");
  s.L("geth").by(0x8b,0x41,0x28, 0xe9).rel("div");
  // eax = vertex count for (edx = primitive type, eax = primitive count)
  s.L("vcount").by(0x83,0xfa,0x04, 0x75,0x04, 0x8d,0x04,0x40, 0xc3,
                   0x83,0xfa,0x02, 0x75,0x03, 0x01,0xc0, 0xc3,
                   0x83,0xfa,0x03, 0x75,0x02, 0x40, 0xc3,
                   0x83,0xfa,0x05, 0x72,0x03, 0x83,0xc0,0x02, 0xc3);
  // multiply x,y of ebx vertices at esi (stride edi) by st0
  s.L("verts").by(0x85,0xdb, 0x74).rel8("verts_end");
  s.L("verts_loop").by(0xd9,0x06, 0xd8,0xc9, 0xd9,0x1e, 0xd9,0x46,0x04, 0xd8,0xc9, 0xd9,0x5e,0x04, 0x01,0xfe, 0x4b, 0x75).rel8("verts_loop");
  s.L("verts_end").by(0xc3);
  // IDirect3DDevice9::DrawPrimitiveUP(dev, type, count, data, stride) replacement
  s.L("dpup").by(0x80,0x3d).abs(D_FLAG).by(0x00, 0x74).rel8("dpup_pass");
  s.by(0x83,0x3d).abs(DEV_TRANSFORM).by(0x01, 0x75).rel8("dpup_pass");
  s.by(0x53,0x56,0x57);                                   // ebx esi edi | ret dev type count data stride
  s.by(0x8b,0x44,0x24,0x18, 0x8b,0x54,0x24,0x14, 0xe8).rel("vcount");
  s.by(0x89,0xc3, 0x8b,0x74,0x24,0x1c, 0x8b,0x7c,0x24,0x20, 0xd9,0x05).abs(D_SCALE);
  s.by(0xe8).rel("verts");
  s.by(0xdd,0xd8);                                        // fstp st0
  for(int i=0;i<5;i++)s.by(0xff,0x74,0x24,0x20);          // push stride,data,count,type,dev
  s.by(0x8b,0x04,0x24, 0x8b,0x00, 0xff,0x90,0x4c,0x01,0x00,0x00, 0x50); // call [vtbl+0x14c]; push hr
  s.by(0x8b,0x44,0x24,0x1c, 0x8b,0x54,0x24,0x18, 0xe8).rel("vcount");
  s.by(0x89,0xc3, 0x8b,0x74,0x24,0x20, 0x8b,0x7c,0x24,0x24, 0xd9,0x05).abs(D_INV);
  s.by(0xe8).rel("verts");
  s.by(0xdd,0xd8, 0x58, 0x5f,0x5e,0x5b, 0xc2,0x14,0x00);
  s.L("dpup_pass").by(0x8b,0x44,0x24,0x04, 0x8b,0x00, 0xff,0xa0,0x4c,0x01,0x00,0x00);
  // WidgetManager::Render: scale only while the UI draws
  s.L("render").by(0xc6,0x05).abs(D_FLAG).by(0x01, 0xe8).rel(WM_RENDER).by(0xc6,0x05).abs(D_FLAG).by(0x00, 0xc3);
  s.L("curdraw").by(0xc6,0x05).abs(D_FLAG).by(0x01, 0xe8).rel(CURSOR_DRAW).by(0xc6,0x05).abs(D_FLAG).by(0x00, 0xc3);
  // scale = max(1, min(req, w/800, h/600)) for the caller's (w, h) args; sets D_SCALE and D_INV
  s.L("fit").by(0x0f,0xb7,0x44,0x24,0x08, 0x50, 0x0f,0xb7,0x44,0x24,0x10, 0x50); // push w, push h
  s.by(0xdb,0x04,0x24, 0xd8,0x0d).abs(s.at("k600"));        // h/600
  s.by(0xdb,0x44,0x24,0x04, 0xd8,0x0d).abs(s.at("k800"));   // w/800
  s.by(0xdb,0xf1, 0xdb,0xc1, 0xdd,0xd9);                    // fcomi; fcmovnb; fstp st1  -> min
  s.by(0xd9,0x05).abs(s.at("req")).by(0xdb,0xf1, 0xdb,0xc1, 0xdd,0xd9); // min with requested
  s.by(0xd9,0xe8, 0xdb,0xf1, 0xda,0xc1, 0xdd,0xd9);         // fld1; fcomi; fcmovb; fstp st1 -> max with 1
  s.by(0xd9,0x15).abs(D_SCALE).by(0xd9,0xe8, 0xde,0xf1, 0xd9,0x1d).abs(D_INV); // fst scale; 1/scale -> inv
  s.by(0x83,0xc4,0x08, 0xc3);
  // WidgetManager::Init(w, h, ...) / SetScreenSize(w, h): pick the scale, divide the size, remember it for the ScreenWidth/Height reads
  for(String nm:new String[]{"init","setsize"}){
   s.L(nm).by(0xe8).rel("fit").by(0x0f,0xb7,0x44,0x24,0x04, 0xe8).rel("div").by(0x89,0x44,0x24,0x04, 0xa3).abs(D_UIW);
   s.by(0x0f,0xb7,0x44,0x24,0x08, 0xe8).rel("div").by(0x89,0x44,0x24,0x08, 0xa3).abs(D_UIH);
   s.by(0xe9).rel(nm.equals("init")?WM_INIT:WM_SETSIZE);}
  // WidgetManager::PostEvent(ev): mouse events carry x,y at +0x14/+0x18
  s.L("mev");for(long v:MOUSE_EVENTS)s.d32(v);
  s.L("post").by(0x8b,0x54,0x24,0x04, 0x85,0xd2, 0x74).rel8("post_go");
  s.by(0x8b,0x42,0x08, 0x51, 0x6a,MOUSE_EVENTS.length, 0x59);            // eax = type; push ecx; ecx = count
  s.L("post_l").by(0x3b,0x04,0x8d).abs(s.at("mev")-4).by(0x74).rel8("post_m"); // cmp eax,[mev-4+ecx*4]; je
  s.by(0xe2).rel8("post_l").by(0x59, 0xeb).rel8("post_go");               // loop; pop ecx; jmp
  s.L("post_m").by(0x59);
  s.by(0x8b,0x42,0x14, 0xe8).rel("div").by(0x89,0x42,0x14, 0x8b,0x42,0x18, 0xe8).rel("div").by(0x89,0x42,0x18);
  s.L("post_go").by(0xe9).rel(WM_POST);
  // cursor widget follows the mouse (x, y)
  s.L("cursor").by(0x8b,0x44,0x24,0x04, 0xe8).rel("div").by(0x89,0x44,0x24,0x04,
   0x8b,0x44,0x24,0x08, 0xe8).rel("div").by(0x89,0x44,0x24,0x08, 0xe9).rel(CURSOR_MOVE);
  // GetMousePos(&x, &y) for UI placement
  s.L("mouse").by(0xff,0x74,0x24,0x08, 0xff,0x74,0x24,0x08, 0xe8).rel(GET_MOUSE);
  for(int k:new int[]{4,8})s.by(0x8b,0x54,0x24,k, 0x8b,0x02, 0xe8).rel("div").by(0x89,0x02);
  s.by(0xc2,0x08,0x00);
  // eax = round(eax * scale)
  s.L("mul").by(0x50, 0xdb,0x04,0x24, 0xd8,0x0d).abs(D_SCALE).by(0xdb,0x1c,0x24, 0x58, 0xc3);
  // IDirect3DDevice9::SetViewport(dev, vp): lists clip by setting the viewport to their rect in UI pixels, and DrawPrim
  // vertices are relative to it, so scale it too while the UI draws (clamped to the render target)
  s.L("vp").by(0x80,0x3d).abs(D_FLAG).by(0x00, 0x74).rel8("vp_pass");
  s.by(0x56, 0x8b,0x74,0x24,0x0c);                          // esi = vp
  for(int k=0;k<16;k+=4)s.by(0x8b,0x46,k, 0xe8).rel("mul").by(0x89,0x46,k);
  s.by(0x8b,0x15).abs(0x629f84e8L);                        // edx = render device (+0x24/+0x28 screen size)
  for(int k=0;k<2;k++)s.by(0x8b,0x42,0x24+4*k, 0x2b,0x46,4*k, 0x7d,0x02, 0x31,0xc0, 0x39,0x46,8+4*k, 0x76,0x03, 0x89,0x46,8+4*k);
  s.by(0x5e);
  s.L("vp_pass").by(0x8b,0x44,0x24,0x04, 0x8b,0x00, 0xff,0xa0,0xbc,0x00,0x00,0x00);
  // camera screen-to-ray(x, y, out): x, y *= scale, then the real call
  // render device GetViewport(rect) for overhead widget placement: the real call, then rect /= scale
  s.L("ovp").by(0xff,0x74,0x24,0x04, 0x8b,0x01, 0xff,0x90,0xac,0x00,0x00,0x00, 0x8b,0x54,0x24,0x04);
  for(int k=0;k<16;k+=4)s.by(0x8b,0x42,k, 0xe8).rel("div").by(0x89,0x42,k);
  s.by(0xc2,0x04,0x00);
  s.L("pick");for(int k:new int[]{4,8})s.by(0xd9,0x44,0x24,k, 0xd8,0x0d).abs(D_SCALE).by(0xd9,0x5c,0x24,k);
  s.by(0x8b,0x01, 0xff,0xa0,0xa0,0x00,0x00,0x00);
  byte[] code=s.done();
  if(code.length>CAVE)throw new RuntimeException("cave too small");
  System.arraycopy(code,0,img,off(cave),code.length);
  System.out.printf("cave code %d bytes, %d relocations%n",code.length,s.abs.size());

  // ---- sites ----
  for(long[] g:getSites)callSite(g[0],s.at(g[1]==0?"getw":"geth"));
  for(long v:dpup)callSite(v,s.at("dpup"));
  if(u8(SET_VIEWPORT_CALL)!=0xff||(u8(SET_VIEWPORT_CALL+1)&0xf8)!=0x90||B.getInt(off(SET_VIEWPORT_CALL)+2)!=0xbc)throw new RuntimeException("SetViewport call");
  callSite(SET_VIEWPORT_CALL,s.at("vp"));
  for(long v:CURSOR_CALLS){if(u8(v)!=0xe8||v+5+B.getInt(off(v)+1)!=CURSOR_MOVE)throw new RuntimeException("cursor call");B.putInt(off(v)+1,(int)(s.at("cursor")-(v+5)));}
  for(long v:MOUSE_CALLS){if(u8(v)!=0xe8||v+5+B.getInt(off(v)+1)!=GET_MOUSE)throw new RuntimeException("mouse call");B.putInt(off(v)+1,(int)(s.at("mouse")-(v+5)));}
  for(long v:CURSOR_DRAW_CALLS){if(u8(v)!=0xe8||v+5+B.getInt(off(v)+1)!=CURSOR_DRAW)throw new RuntimeException("cursor draw call");B.putInt(off(v)+1,(int)(s.at("curdraw")-(v+5)));}
  for(long v:OVERHEAD_VP_CALLS){if(u8(v)!=0xff||(u8(v+1)&0xf8)!=0x90||B.getInt(off(v)+2)!=0xac)throw new RuntimeException("viewport call");callSite(v,s.at("ovp"));}
  for(long v:PICK_CALLS){if(u8(v)!=0xff||(u8(v+1)&0xf8)!=0x90||B.getInt(off(v)+2)!=0xa0)throw new RuntimeException("pick call");callSite(v,s.at("pick"));}
  for(long[] c:CVAR_READS){int o=off(c[0])+(int)c[1];long want=c[2]==D_UIW?0x629de2acL:0x629de2e8L;if((B.getInt(o)&0xffffffffL)!=want)throw new RuntimeException("cvar read");B.putInt(o,(int)c[2]);}
  long[][] vt={{VT_INIT,WM_INIT,s.at("init")},{VT_SETSIZE,WM_SETSIZE,s.at("setsize")},{VT_RENDER,WM_RENDER,s.at("render")},{VT_POST,WM_POST,s.at("post")}};
  for(long[] v:vt){if((B.getInt(off(v[0]))&0xffffffffL)!=v[1])throw new RuntimeException("vtable "+Long.toHexString(v[0]));B.putInt(off(v[0]),(int)v[2]);}

  addRelocs(s.abs);
  Files.write(Path.of(a[2]),img);
  if(a.length>3){StringBuilder sb=new StringBuilder();int i=0;
   while(i<img.length){if(orig[i]==img[i]){i++;continue;}int b0=i;while(i<img.length&&(orig[i]!=img[i]||(i+1<img.length&&orig[i+1]!=img[i+1])||(i+2<img.length&&orig[i+2]!=img[i+2])))i++;
    sb.append(String.format("%x %s %s%n",b0,hx(orig,b0,i),hx(img,b0,i)));}
   Files.writeString(Path.of(a[3]),sb.toString());}
  System.out.println("ok");
 }

 static void scanDevCalls(long fn,List<long[]> out){
  // walk forward from the function start until the next function's int3 padding; match `call [reg+0x104/0x108]`
  long end=fn;while(!(u8(end)==0xcc&&u8(end+1)==0xcc))end++;
  for(long va=fn;va<end-5;va++){if(u8(va)!=0xff||(u8(va+1)&0xf8)!=0x90||(u8(va+1)&7)==4)continue;
   int d=B.getInt(off(va)+2);if(d!=0x104&&d!=0x108)continue;
   // device pointer loaded shortly before: mov ecx,[0x629f84e8] (8b 0d)
   boolean dev=false;for(long p=va-48;p<va;p++)if(u8(p)==0x8b&&u8(p+1)==0x0d&&B.getInt(off(p)+2)==0x629f84e8)dev=true;
   if(va==0x62019209L)dev=true; // reached by a jump from 62019195, ecx = device loaded at 62019189
   if(!dev)throw new RuntimeException("not a device call at "+Long.toHexString(va));
   out.add(new long[]{va,d==0x104?0:1});}
 }
 static void callSite(long va,long target){img[off(va)]=(byte)0xe8;B.putInt(off(va)+1,(int)(target-(va+5)));img[off(va)+5]=(byte)0x90;}
 static long findCave(int need){
  int best=-1,bl=0;for(int i=0x1000;i<0x868000;){if((img[i]&0xff)!=0xcc){i++;continue;}int j=i;while(j<0x868000&&(img[j]&0xff)==0xcc)j++;
   if(j-i>bl){bl=j-i;best=i;}i=j;}
  if(bl<need+32)throw new RuntimeException("no cave, largest "+bl);
  return BASE+((best+16)&~15);
 }
 static void addRelocs(List<Long> abs){
  int pe=B.getInt(0x3c);int dd=pe+0x18+0x60+5*8;int rva=B.getInt(dd),size=B.getInt(dd+4);
  int nsec=B.getShort(pe+6);int st=pe+24+(B.getShort(pe+20)&0xffff);int raw=-1,rawSize=0,hdr=-1;
  for(int i=0;i<nsec;i++){int h=st+i*40;if(B.getInt(h+12)==rva){raw=B.getInt(h+20);rawSize=B.getInt(h+16);hdr=h;}}
  TreeMap<Long,List<Integer>> pages=new TreeMap<>();for(long v:abs){long r=v-BASE;pages.computeIfAbsent(r&~0xfff,x->new ArrayList<>()).add((int)(r&0xfff));}
  int w=raw+size;
  for(var e:pages.entrySet()){List<Integer> L=e.getValue();int cnt=L.size()+(L.size()%2);int sz=8+cnt*2;
   B.putInt(w,(int)(long)e.getKey());B.putInt(w+4,sz);int k=w+8;for(int o:L){B.putShort(k,(short)(0x3000|o));k+=2;}if(L.size()%2==1)B.putShort(k,(short)0);w+=sz;}
  int newSize=w-raw;if(newSize>rawSize)throw new RuntimeException("no room in .reloc");
  B.putInt(dd+4,newSize);if(newSize>B.getInt(hdr+8))B.putInt(hdr+8,newSize);
  System.out.printf(".reloc %x -> %x (room %x)%n",size,newSize,rawSize);
 }
 static String hx(byte[] d,int s,int e){StringBuilder x=new StringBuilder();for(int i=s;i<e;i++)x.append(String.format("%02x",d[i]));return x.toString();}
}
