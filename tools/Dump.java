import java.nio.*;import java.nio.file.*;
public class Dump{public static void main(String[] a)throws Exception{
 byte[] p=Files.readAllBytes(Path.of(a[0]));byte[] c=Files.readAllBytes(Path.of(a[1]));
 ByteBuffer b=ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN);
 b.position(8);int n=b.getInt();int ok=0;
 for(int i=0;i<n;i++){int off=b.getInt(),len=b.getInt();byte[] o=new byte[len],nw=new byte[len];b.get(o);b.get(nw);
  boolean m=off+len<=c.length;if(m)for(int k=0;k<len;k++)if(c[off+k]!=o[k]){m=false;break;}
  if(m)ok++;
  System.out.printf("%3d %08x %6d %s  %s -> %s%n",i,off,len,m?"ok":"XX",hex(o,24),hex(nw,24));}
 System.out.println("end at "+b.position()+" of "+p.length+" ok="+ok+" filelen="+Integer.toHexString(c.length));}
static String hex(byte[] x,int max){StringBuilder s=new StringBuilder();for(int i=0;i<Math.min(x.length,max);i++)s.append(String.format("%02x",x[i]));if(x.length>max)s.append("..");return s.toString();}}
