import java.nio.file.*;
public class Hex{public static void main(String[] a)throws Exception{byte[] d=Files.readAllBytes(Path.of(a[0]));
for(int i=1;i<a.length;i+=2){int off=Integer.parseInt(a[i],16),len=Integer.parseInt(a[i+1],16);System.out.printf("== %x%n",off);
StringBuilder s=new StringBuilder();for(int k=0;k<len;k++){if(k%32==0)s.append(String.format("%n%08x: ",off+k));s.append(String.format("%02x",d[off+k]));}System.out.println(s);}}}
