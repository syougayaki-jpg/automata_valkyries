import java.io.*;
import java.util.zip.GZIPOutputStream;

// 5x5x5 の空ストラクチャ（GameTest 用テンプレート）を NBT で書き出す
public class GenEmpty {
    public static void main(String[] a) throws Exception {
        try (DataOutputStream o = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(a[0])))) {
            o.writeByte(10); o.writeUTF("");                 // root compound
            o.writeByte(3); o.writeUTF("DataVersion"); o.writeInt(3465);
            o.writeByte(9); o.writeUTF("size"); o.writeByte(3); o.writeInt(3);
            o.writeInt(5); o.writeInt(5); o.writeInt(5);
            o.writeByte(9); o.writeUTF("palette"); o.writeByte(10); o.writeInt(1);
            o.writeByte(8); o.writeUTF("Name"); o.writeUTF("minecraft:air"); o.writeByte(0);
            o.writeByte(9); o.writeUTF("blocks"); o.writeByte(10); o.writeInt(0);
            o.writeByte(9); o.writeUTF("entities"); o.writeByte(10); o.writeInt(0);
            o.writeByte(0);                                    // end root
        }
    }
}
