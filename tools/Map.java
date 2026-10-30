import java.nio.*;import java.nio.file.*;import java.util.*;
public class Map{
 static int[][] sec; static long base;
 static long f2va(long f){for(int[] s:sec)if(f>=s[2]&&f<s[2]+s[3])return base+f-s[2]+s[0];return -1;}
 static long va2f(long v){long r=v-base;for(int[] s:sec)if(r>=s[0]&&r<s[0]+Math.max(s[1],s[3]))return r-s[0]+s[2];return -1;}
 public static void main(String[] a)throws Exception{
 byte[] p=Files.readAllBytes(Path.of(a[0]));byte[] c=Files.readAllBytes(Path.of(a[1]));
 ByteBuffer cb=ByteBuffer.wrap(c).order(ByteOrder.LITTLE_ENDIAN);
 int pe=cb.getInt(0x3c);int ns=cb.getShort(pe+6);int osz=cb.getShort(pe+20)&0xffff;base=cb.getInt(pe+24+28)&0xffffffffL;
 sec=new int[ns][];int st=pe+24+osz;
 for(int i=0;i<ns;i++){int o=st+i*40;String nm=new String(c,o,8).trim();sec[i]=new int[]{cb.getInt(o+12),cb.getInt(o+8),cb.getInt(o+20),cb.getInt(o+16)};
  System.out.printf("%s rva=%x vs=%x raw=%x rs=%x%n",nm,sec[i][0],sec[i][1],sec[i][2],sec[i][3]);}
 System.out.printf("base=%x%n",base);
 ByteBuffer b=ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN);b.position(8);int n=b.getInt();
 // apply patch to a copy
 byte[] m=c.clone();List<int[]> sites=new ArrayList<>();
 for(int i=0;i<n;i++){int off=b.getInt(),len=b.getInt();b.position(b.position()+len);byte[] nw=new byte[len];b.get(nw);System.arraycopy(nw,0,m,off,len);sites.add(new int[]{off,len});}
 Files.write(Path.of(a[2]),m);
 ByteBuffer mb=ByteBuffer.wrap(m).order(ByteOrder.LITTLE_ENDIAN);
 for(int i=0;i<n;i++){int off=sites.get(i)[0],len=sites.get(i)[1];StringBuilder sb=new StringBuilder();
  for(int k=0;k<len;k++){int op=m[off+k]&0xff;
   if((op==0xe8||op==0xe9)&&k+5<=len){long tgt=f2va(off+k)+5+mb.getInt(off+k+1);long tf=va2f(tgt);
     // which site contains tf
     int si=-1;for(int j=0;j<n;j++)if(tf>=sites.get(j)[0]&&tf<sites.get(j)[0]+sites.get(j)[1])si=j;
     if(tf>=0&&tf<m.length&&(si>=0||k==0))sb.append(String.format(" %s+%d->%x(f%x%s)",op==0xe8?"call":"jmp",k,tgt,tf,si>=0?" #"+si:""));}
   if(k+4<=len){long v=mb.getInt(off+k)&0xffffffffL;if(v>=base&&v<base+0xc00000)sb.append(String.format(" [%x]",v));}
  }
  System.out.printf("#%d f%x va%x len%d:%s%n",i,off,f2va(off),len,sb);}
}}
