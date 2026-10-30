import java.nio.*;import java.nio.file.*;
public class Check{public static void main(String[] a)throws Exception{for(String f:a){byte[] c=Files.readAllBytes(Path.of(f));ByteBuffer b=ByteBuffer.wrap(c).order(ByteOrder.LITTLE_ENDIAN);
 int pe=b.getInt(0x3c);long base=b.getInt(pe+0x34)&0xffffffffL;long soi=b.getInt(pe+0x50);int dd=pe+0x18+0x60+40;int rva=b.getInt(dd),size=b.getInt(dd+4);int raw=0x9e0000;
 int q=0,n=0,bad=0;while(q<size){int pg=b.getInt(raw+q),sz=b.getInt(raw+q+4);for(int k=8;k<sz;k+=2){int e=b.getShort(raw+q+k)&0xffff;if((e>>12)==0)continue;if((e>>12)!=3){System.out.println("type "+(e>>12));continue;}
  int r=pg+(e&0xfff);n++;if(r+4>c.length)continue;long v=b.getInt(r)&0xffffffffL;if(v<base||v>=base+soi){bad++;if(bad<10)System.out.printf("  bad @%x = %08x%n",r,v);}}q+=sz;}
 System.out.printf("%s: relocs=%d outOfImage=%d e04..e08 zero=%b%n",f,n,bad,b.getLong(0x9de000+0xe00)==0&&b.getInt(0x9de000+0xe08)==0);}}}
