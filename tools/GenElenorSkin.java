import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

// エレノアの GeckoLib 用テクスチャ（128x128、デフォルメ体型・頭 8px）を生成する。
// UV 配置は geo/entity/elenor.geo.json の各キューブの "uv" と一致させること。
public class GenElenorSkin {
    static final Map<Character, Integer> C = Map.ofEntries(
        Map.entry('h', 0xf4effc), Map.entry('H', 0xe3d8f5), Map.entry('M', 0xc2b1e6), Map.entry('N', 0x9a86cc), // 髪（4 階調）
        Map.entry('S', 0xfde4d8), Map.entry('s', 0xf2c9b8), Map.entry('B', 0xf8b4c4), // 肌・頬
        Map.entry('L', 0x3a2450), Map.entry('I', 0xb044d8), Map.entry('E', 0xffffff), Map.entry('e', 0xc9a9c9), // まつ毛・瞳・白目・眉
        Map.entry('D', 0x2e2447), Map.entry('d', 0x1e172f),                          // ドレス
        Map.entry('W', 0xf4f0fa), Map.entry('w', 0xd6cde6),                          // フリル
        Map.entry('R', 0xe040fb), Map.entry('r', 0xa020c0),                          // リボン
        Map.entry('P', 0x8e4fd0), Map.entry('T', 0x6a34a8), Map.entry('J', 0xff7de9), // 差し色・ベルト・宝石
        Map.entry('K', 0x1a1522), Map.entry('k', 0x2c2438), Map.entry('O', 0x3d2c5a)  // ストッキング・靴
    );
    static final BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);

    interface Face { char at(int x, int y, int w, int h); }

    static Face solid(char c) { return (x, y, w, h) -> c; }
    static Face rows(String pattern) { return (x, y, w, h) -> pattern.charAt(y); }
    static Face map(String... rows) { return (x, y, w, h) -> rows[y].charAt(x); }

    /**
     * GeckoLib のボックス UV（サイズ w,h,d / 起点 u,v）。
     * 上段: [d 空き][上 w][下 w]、下段: [東 d][北 w（正面）][西 d][南 w（背面）]。
     * 北・南の面は正面・背面から見たままの向きで描く。
     */
    static void box(int u, int v, int w, int h, int d, Face north, Face south, Face side, Face top, Face bottom) {
        paint(u + d, v, w, d, top);
        paint(u + d + w, v, w, d, bottom);
        paint(u, v + d, d, h, side);
        paint(u + d, v + d, w, h, north);
        paint(u + d + w, v + d, d, h, side);
        paint(u + 2 * d + w, v + d, w, h, south);
    }

    static void paint(int x0, int y0, int w, int h, Face f) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            char c = f.at(x, y, w, h);
            if (c != '.') img.setRGB(x0 + x, y0 + y, 0xff000000 | C.get(c));
        }
    }

    /** 流れる髪（縦筋のハイライトと毛先の影）。tip = true で毛先をギザギザに透過 */
    static final char[] TONE = {'h', 'H', 'M', 'N'};
    static final int[] STRANDS = {1, 0, 1, 2, 1, 2, 1, 1, 0, 2};

    /** 細かい髪: 4 階調の毛束と、毛束の切れ目の影。tip = true で毛先をギザギザに透過 */
    static char hair(int x, int y, int w, int h, boolean tip) {
        if (tip) {
            int cut = (x % 3 == 1) ? 0 : (x % 3 == 0 ? 2 : 1);
            if (y >= h - cut) return '.';
            if (y >= h - cut - 1) return 'N';
        }
        int b = STRANDS[Math.floorMod(x, STRANDS.length)];
        if ((y + Math.floorMod(x * 5, 7)) % 7 == 6) b = Math.min(3, b + 1);
        return TONE[b];
    }

    /** 天使の輪（頭の側面・背面の上から 2〜3 行目にハイライト） */
    static char hairRing(int x, int y, int w, int h) {
        if (y == 2 && x % 2 == 0) return 'h';
        if (y == 3 && x % 3 == 1) return 'h';
        return hair(x, y, w, h, false);
    }

    static final Face HAIR = (x, y, w, h) -> hair(x, y, w, h, false);
    static final Face HAIR_TIP = (x, y, w, h) -> hair(x, y, w, h, true);
    static final Face HAIR_RING = GenElenorSkin::hairRing;

    public static void main(String[] a) throws Exception {
        // ===== 頭 8x8x8 @ (0,0) =====
        box(0, 0, 8, 8, 8,
            map(                             // 顔: 口なし・白目つきの小さな目（瞳の間 2px）・頬の赤み。前髪と目は 1 段下げ
                "HHHHHHHH",
                "HHMHHMHH",
                "HMHHHHMH",
                "NHSHHSHN",
                "NSSSSSSN",
                "NLLSSLLN",
                "NEISSIEN",
                "NBSSSSBN"),
            HAIR_RING, HAIR_RING, HAIR,
            (x, y, w, h) -> y >= h - 3 ? 'H' : 'S');                   // あご（後ろ側は髪）

        // ===== 髪の外殻（大きめの inflate でボリューム）8x8x8 @ (32,0) =====
        box(32, 0, 8, 8, 8,
            map(                             // 前髪の毛束（1 段下げ）。まつ毛の立体パーツは geo の lashes
                "hHHhHHhH",
                "HMHHHHMH",
                "H.H..H.H",
                "........",
                "........",
                "........",
                "........",
                "........"),
            HAIR_RING, HAIR_RING, HAIR, (x, y, w, h) -> '.');

        // ===== 胴 7x10x4 @ (0,16)：ウエスト（ベルト）は上から 8 行目 =====
        box(0, 16, 7, 10, 4,
            map(
                "DWWWWWD",
                "DDWPWDD",
                "DDDPDDD",
                "DDDdDDD",
                "DDDPDDD",
                "DDDdDDD",
                "DDDPDDD",
                "TTTJTTT",
                "DDDDDDD",
                "DDDDDDD"),
            rows("DDDDDDDTDD"), rows("DDDDDDDTDD"), solid('D'), solid('d'));

        // ===== 腕 2x9x2 @ (24,16) / (32,16) =====
        Face arm = rows("DDWSSSPSS");
        box(24, 16, 2, 9, 2, arm, arm, arm, solid('D'), solid('S'));
        box(32, 16, 2, 9, 2, arm, arm, arm, solid('D'), solid('S'));

        // ===== パフスリーブ 4x3x4 @ (40,16) =====
        Face puff = (x, y, w, h) -> y == h - 1 ? ((x % 2 == 0) ? 'W' : 'w') : (y == 0 && x % 2 == 1) ? 'd' : 'D';
        box(40, 16, 4, 3, 4, puff, puff, puff, solid('D'), solid('W'));

        // ===== 脚 3x12x3 @ (0,32) / (12,32)：上 6 行はスカートの下、見える部分は絶対領域＋黒ストッキング =====
        String legRows = "ddddddSPKKKO";
        Face leg = rows(legRows);
        Face legFront = (x, y, w, h) -> (x == 1 && y >= 8 && y <= 10) ? 'k' : legRows.charAt(y);
        box(0, 32, 3, 12, 3, legFront, leg, leg, solid('d'), solid('O'));
        box(12, 32, 3, 12, 3, legFront, leg, leg, solid('d'), solid('O'));

        // ===== 後ろ髪（3 段）幅 11 x 厚さ 3 @ (64,0) / (64,16) / (64,26) =====
        box(64, 0, 11, 12, 3, HAIR, HAIR, HAIR, solid('H'), solid('H'));
        box(64, 16, 11, 6, 3, HAIR, HAIR, HAIR, solid('H'), solid('H'));
        box(64, 26, 11, 6, 3, HAIR_TIP, HAIR_TIP, HAIR_TIP, solid('H'), solid('N'));

        // ===== 横髪 2x12x2 @ (96,0) =====
        Face lock = (x, y, w, h) -> y >= h - 1 ? (x == 0 ? 'N' : '.') : ((x + y) % 4 == 0 ? 'M' : 'H');
        box(96, 0, 2, 12, 2, lock, lock, lock, solid('H'), solid('N'));

        // ===== 頭（後ろ髪）のリボン：黒。結び目 2x3x1 @ (104,0)、輪 6x4x1 @ (104,8) =====
        Face knot = map("kk", "KK", "KK");
        box(104, 0, 2, 3, 1, knot, knot, solid('K'), solid('k'), solid('K'));
        Face loop = map("kkkkkK", "KkKKKK", "KKKKKK", "KKKKKk");  // 上辺の光沢と角の陰影で丸みを出す（透過は正面から穴に見えるので使わない）
        box(104, 8, 6, 4, 1, loop, loop, solid('K'), solid('k'), solid('K'));

        // ===== 立体まつ毛用の黒 2x1 @ (120,16)。geo 側は面ごとの UV で細いキューブに貼る =====
        paint(120, 16, 2, 1, solid('K'));

        // ===== アホ毛 1x4x1 @ (120,0) =====
        box(120, 0, 1, 4, 1, HAIR, HAIR, solid('M'), solid('H'), solid('M'));

        // ===== 胸元のリボン（結び目 1x1x1 @ (104,16)、輪 2x2x1 @ (108,16)） =====
        box(104, 16, 1, 1, 1, solid('J'), solid('J'), solid('r'), solid('r'), solid('r'));
        Face chestLoop = map("RR", "Rr");
        box(108, 16, 2, 2, 1, chestLoop, chestLoop, solid('r'), solid('R'), solid('r'));

        // ===== スカートパネル 6x8x1 @ (64,40)：外側 = ドレス地＋紫ライン＋白フリル、内側 = ペチコート =====
        Face outer = (x, y, w, h) -> y == h - 1 ? ((x % 2 == 0) ? 'W' : 'w') : y == h - 2 ? 'P' : (x == 0 || x == w - 1) ? 'd' : 'D';
        Face inner = (x, y, w, h) -> y == h - 1 ? 'W' : 'w';
        Face edge = (x, y, w, h) -> y == h - 1 ? 'W' : y == h - 2 ? 'P' : 'd';
        box(64, 40, 6, 8, 1, outer, inner, edge, solid('D'), solid('W'));

        ImageIO.write(img, "png", new File(a[0]));
    }
}
