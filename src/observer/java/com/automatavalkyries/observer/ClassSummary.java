package com.automatavalkyries.observer;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * クラスファイルの要約（メソッドごとのコードの指紋と、参照しているメソッド・フィールド）。
 * 監視エージェントは Minecraft・ASM に依存できない（システムクラスローダーで読まれる）ので、必要な分だけ自前で読む。
 */
final class ClassSummary {

    /** 1 メソッド。{@code trivialReturn} はコードが「すぐ return するだけ」（再変換で中身を潰された形） */
    record MethodInfo(long codeCrc, int codeLength, boolean trivialReturn) {}

    final String name;
    /** "名前記述子" → 要約（コードの無い abstract / native は入れない） */
    final Map<String, MethodInfo> methods = new LinkedHashMap<>();
    /** 参照しているメソッド・フィールド（"所有クラス.名前"） */
    final Set<String> refs = new LinkedHashSet<>();

    private ClassSummary(String name) {
        this.name = name;
    }

    static ClassSummary parse(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 0xCAFEBABE) throw new IOException("Not a class file");
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        Object[] pool = new Object[count];
        int[][] links = new int[count][];
        int[] tags = new int[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            tags[i] = tag;
            switch (tag) {
                case 1 -> pool[i] = in.readUTF();
                case 3, 4 -> in.readInt();
                case 5, 6 -> {
                    in.readLong();
                    i++;
                }
                case 7, 8, 16, 19, 20 -> links[i] = new int[]{in.readUnsignedShort()};
                case 9, 10, 11, 12, 17, 18 -> links[i] = new int[]{in.readUnsignedShort(), in.readUnsignedShort()};
                case 15 -> {
                    in.readUnsignedByte();
                    in.readUnsignedShort();
                }
                default -> throw new IOException("Unknown constant tag " + tag);
            }
        }
        in.readUnsignedShort();
        ClassSummary summary = new ClassSummary(className(pool, links, in.readUnsignedShort()));
        in.readUnsignedShort();
        int interfaces = in.readUnsignedShort();
        for (int i = 0; i < interfaces; i++) in.readUnsignedShort();
        int fields = in.readUnsignedShort();
        for (int i = 0; i < fields; i++) {
            in.readUnsignedShort();
            in.readUnsignedShort();
            in.readUnsignedShort();
            skipAttributes(in);
        }
        int methods = in.readUnsignedShort();
        for (int i = 0; i < methods; i++) {
            in.readUnsignedShort();
            String name = (String) pool[in.readUnsignedShort()];
            String descriptor = (String) pool[in.readUnsignedShort()];
            int attributes = in.readUnsignedShort();
            for (int a = 0; a < attributes; a++) {
                String attribute = (String) pool[in.readUnsignedShort()];
                int length = in.readInt();
                byte[] data = new byte[length];
                in.readFully(data);
                if ("Code".equals(attribute)) summary.methods.put(name + descriptor, codeInfo(data));
            }
        }
        for (int i = 1; i < count; i++) {
            if (tags[i] == 9 || tags[i] == 10 || tags[i] == 11) {
                String owner = className(pool, links, links[i][0]);
                int[] nameAndType = links[links[i][1]];
                summary.refs.add(owner + "." + pool[nameAndType[0]]);
            }
        }
        return summary;
    }

    private static String className(Object[] pool, int[][] links, int index) {
        return (String) pool[links[index][0]];
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        int attributes = in.readUnsignedShort();
        for (int a = 0; a < attributes; a++) {
            in.readUnsignedShort();
            in.skipNBytes(in.readInt());
        }
    }

    /** Code 属性: max_stack(2) max_locals(2) code_length(4) code... */
    private static MethodInfo codeInfo(byte[] data) {
        int length = ((data[4] & 0xFF) << 24) | ((data[5] & 0xFF) << 16) | ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        CRC32 crc = new CRC32();
        crc.update(data, 8, length);
        int last = length > 0 ? data[8 + length - 1] & 0xFF : -1;
        // return（0xb1）、または定数 1 つを積んで xreturn（0xac〜0xb0）だけのコード
        boolean trivial = length > 0 && length <= 2 && last >= 0xAC && last <= 0xB1;
        return new MethodInfo(crc.getValue(), length, trivial);
    }
}
