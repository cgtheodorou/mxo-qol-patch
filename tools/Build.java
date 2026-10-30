import java.nio.*;import java.nio.file.*;import java.util.*;
public class Build{
 static ByteBuffer le(byte[] b){return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);}
 public static void main(String[] a)throws Exception{
  byte[] p=Files.readAllBytes(Path.of(a[0]));byte[] orig=Files.readAllBytes(Path.of(a[1]));
  int[] keep=Arrays.stream(a[3].split(",")).mapToInt(Integer::parseInt).toArray();
  ByteBuffer b=le(p);b.position(8);int n=b.getInt();
  int[] off=new int[n];byte[][] ob=new byte[n][],nb=new byte[n][];
  for(int i=0;i<n;i++){off[i]=b.getInt();int len=b.getInt();ob[i]=new byte[len];nb[i]=new byte[len];b.get(ob[i]);b.get(nb[i]);}
  int rs=b.getInt(),re=b.getInt();byte[] modReloc=new byte[re-rs];b.get(modReloc);
  // mod reloc set
  TreeSet<Integer> M=new TreeSet<>();ByteBuffer mr=le(modReloc);int q=0;
  while(q+8<=modReloc.length){int pg=mr.getInt(q),sz=mr.getInt(q+4);if(sz==0)break;for(int k=8;k<sz;k+=2){int e=mr.getShort(q+k)&0xffff;if((e>>12)==3)M.add(pg+(e&0xfff));}q+=sz;}
  // original reloc
  ByteBuffer ob2=le(orig);int pe=ob2.getInt(0x3c);int dd=pe+0x18+0x60+5*8;int rva=ob2.getInt(dd),size=ob2.getInt(dd+4);
  int secTab=pe+0x18+(ob2.getShort(pe+20)&0xffff);int relRaw=-1,relSecHdr=-1,relRawSize=0;
  for(int i=0;i<ob2.getShort(pe+6);i++){int h=secTab+i*40;if(ob2.getInt(h+12)==rva){relRaw=ob2.getInt(h+20);relRawSize=ob2.getInt(h+16);relSecHdr=h;}}
  System.out.printf("reloc rva=%x size=%x raw=%x rawsize=%x hdr=%x%n",rva,size,relRaw,relRawSize,relSecHdr);
  byte[] out=orig.clone();
  boolean[] inKeep=new boolean[orig.length];
  for(int i:keep){for(int k=0;k<ob[i].length;k++){if(orig[off[i]+k]!=ob[i][k])throw new RuntimeException("site "+i+" mismatch");}
    System.arraycopy(nb[i],0,out,off[i],nb[i].length);for(int k=0;k<nb[i].length;k++)inKeep[off[i]+k]=true;}
  // neutralise original relocs overlapping kept ranges
  q=0;int killed=0;
  while(q<size){int pg=ob2.getInt(relRaw+q),sz=ob2.getInt(relRaw+q+4);
    for(int k=8;k<sz;k+=2){int pos=relRaw+q+k;int e=ob2.getShort(pos)&0xffff;if((e>>12)!=3)continue;int r=pg+(e&0xfff);
      boolean hit=false;for(int j=0;j<4;j++)if(r+j<inKeep.length&&inKeep[r+j])hit=true;
      if(hit){int ne=e&0x0fff;out[pos]=(byte)ne;out[pos+1]=(byte)(ne>>8);killed++;System.out.printf("kill reloc %x (entry @%x)%n",r,pos);}}
    q+=sz;}
  // added relocs
  TreeMap<Integer,List<Integer>> add=new TreeMap<>();
  for(int r:M){if(r+4>orig.length)continue;boolean in=true;for(int j=0;j<4;j++)if(!inKeep[r+j])in=false;
    boolean any=false;for(int j=0;j<4;j++)if(inKeep[r+j])any=true;
    if(any&&!in)throw new RuntimeException("partial reloc "+Integer.toHexString(r));
    if(in){add.computeIfAbsent(r&~0xfff,x->new ArrayList<>()).add(r&0xfff);System.out.printf("add reloc %x -> value %08x%n",r,le(out).getInt(r));}}
  int w=relRaw+size;
  for(var en:add.entrySet()){List<Integer> L=en.getValue();int cnt=L.size()+(L.size()%2);int sz=8+cnt*2;
    ByteBuffer ow=le(out);ow.putInt(w,en.getKey());ow.putInt(w+4,sz);int k=w+8;for(int o:L){ow.putShort(k,(short)(0x3000|o));k+=2;}if(L.size()%2==1)ow.putShort(k,(short)0);w+=sz;}
  int newSize=w-relRaw;System.out.printf("killed=%d newSize=%x (max %x)%n",killed,newSize,relRawSize);
  if(newSize>relRawSize)throw new RuntimeException("no room");
  le(out).putInt(dd+4,newSize);if(newSize>ob2.getInt(relSecHdr+8))le(out).putInt(relSecHdr+8,newSize);
  Files.write(Path.of(a[2]),out);
  // emit diff list
  StringBuilder sb=new StringBuilder();int i=0;int runs=0;
  while(i<orig.length){if(orig[i]==out[i]){i++;continue;}int s=i;while(i<orig.length&&(orig[i]!=out[i]||(i+1<orig.length&&orig[i+1]!=out[i+1])||(i+2<orig.length&&orig[i+2]!=out[i+2])))i++;
    sb.append(String.format("%x %s %s%n",s,hx(orig,s,i),hx(out,s,i)));runs++;}
  Files.writeString(Path.of(a[4]),sb.toString());System.out.println("runs="+runs);
 }
 static String hx(byte[] d,int s,int e){StringBuilder x=new StringBuilder();for(int i=s;i<e;i++)x.append(String.format("%02x",d[i]));return x.toString();}
}
