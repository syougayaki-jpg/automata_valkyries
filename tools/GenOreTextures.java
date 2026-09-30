import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

// アイテムの仮テクスチャ（16x16）を生成する: 鉱石・インゴット、テスト用の抹殺の棒
public class GenOreTextures {
    // outline, dark, mid, light, highlight
    static final int[][] PALETTES = {
        {0x4a3f2a, 0x9c8a5a, 0xd2bf85, 0xf0e4b8, 0xffffff}, // astrite: 星光の白金
        {0x2b1640, 0x5e3591, 0x8e5fd0, 0xb995f0, 0xeadcff}, // phononium: 振動の紫
        {0x0f2f4a, 0x1f6a9a, 0x3fa2d8, 0x86d2f5, 0xe2f7ff}, // ceruleas: セルリアンブルー
    };
    static final String[] NAMES = {"astrite", "phononium", "ceruleas"};

    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]);
        for (int i = 0; i < NAMES.length; i++) {
            ImageIO.write(ingot(PALETTES[i]), "png", new File(dir, "ingot_" + NAMES[i] + ".png"));
            ImageIO.write(raw(PALETTES[i], 31L * i + 7), "png", new File(dir, "raw_" + NAMES[i] + ".png"));
        }
        ImageIO.write(debugStick(), "png", new File(dir, "debug_kill_stick.png"));
    }

    /** 抹殺の棒: 左下→右上の黒紫の柄と、赤く光る結晶の先端 */
    static BufferedImage debugStick() {
        int[] p = {0x1a0f24, 0x3d2458, 0x6a3fa0, 0xff3b6b, 0xffc2d4};  // 輪郭, 柄(暗), 柄(明), 結晶, 結晶の光
        int[][] g = new int[16][16];
        for (int i = 2; i <= 11; i++) {           // 柄（2px 幅の斜線）
            g[15 - i][i] = 3;
            g[15 - i][i - 1] = 2;
        }
        for (int y = 1; y <= 4; y++)              // 先端の結晶（ひし形）
            for (int x = 11; x <= 14; x++)
                if (Math.abs(x - 12.5) + Math.abs(y - 2.5) <= 2.0) g[y][x] = 4;
        g[2][12] = 5; g[1][12] = 5;               // 結晶のハイライト
        g[12][3] = 4; g[11][4] = 4;               // 柄の途中の赤い帯
        return outline(g, p);
    }

    // 0=透明, 1..5 = palette index+1
    static BufferedImage ingot(int[] p) {
        int[][] g = new int[16][16];
        // 上面（平行四辺形）
        for (int y = 5; y <= 7; y++)
            for (int x = 2 + (8 - y); x <= 11 + (8 - y) - 1; x++) g[y][x] = (y == 5 || x == 2 + (8 - y)) ? 5 : 4;
        // 前面
        for (int y = 8; y <= 11; y++)
            for (int x = 2; x <= 11; x++) g[y][x] = y == 11 ? 2 : (x == 2 ? 4 : 3);
        // 右側面
        for (int y = 5; y <= 11; y++)
            for (int x = 12; x <= 14; x++) {
                int top = 8 - (x - 11);            // 上面の右端に沿った斜辺
                int bottom = 11 - (x - 11) + 1;
                if (y > top && y <= bottom + 1 && y <= 11 && g[y][x] == 0) g[y][x] = 2;
            }
        return outline(g, p);
    }

    static BufferedImage raw(int[] p, long seed) {
        Random rnd = new Random(seed);
        int[][] g = new int[16][16];
        double[][] blobs = {{6.5, 9.5, 3.8}, {10.0, 7.0, 3.3}, {9.5, 11.0, 2.8}, {4.5, 6.5, 2.2}};
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
                for (double[] b : blobs) {
                    double dx = x - b[0], dy = y - b[1];
                    if (dx * dx + dy * dy <= b[2] * b[2]) {
                        // 左上を明るく、右下を暗く
                        double shade = (b[0] - x + b[1] - y) / (b[2] * 2) + (rnd.nextDouble() - 0.5) * 0.6;
                        g[y][x] = shade > 0.55 ? 5 : shade > 0.1 ? 4 : shade > -0.4 ? 3 : 2;
                        break;
                    }
                }
        // 1px 内側に入れて外周線の余白を確保
        int[][] s = new int[16][16];
        for (int y = 1; y < 15; y++) for (int x = 1; x < 15; x++) s[y][x] = g[y][x];
        return outline(s, p);
    }

    static BufferedImage outline(int[][] g, int[] p) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                if (g[y][x] > 0) {
                    img.setRGB(x, y, 0xff000000 | p[g[y][x] - 1]);
                } else if (touches(g, x, y)) {
                    img.setRGB(x, y, 0xff000000 | p[0]);
                }
            }
        return img;
    }

    static boolean touches(int[][] g, int x, int y) {
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : d) {
            int nx = x + o[0], ny = y + o[1];
            if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && g[ny][nx] > 0) return true;
        }
        return false;
    }
}
