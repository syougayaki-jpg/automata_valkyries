import com.google.gson.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

// GeckoLib 4.8 の変換規則（BakedModelFactory / RenderUtils）を再現した簡易プレビュー
public class GeoPreview {
    static class Bone { String name, parent; double[] pivot; double rx, ry, rz; List<double[][]> quads = new ArrayList<>(); }
    static Map<String, Bone> bones = new LinkedHashMap<>();
    static boolean valkyrie;
    static BufferedImage tex;
    static int TW, TH;

    public static void main(String[] a) throws Exception {
        JsonObject geo = JsonParser.parseReader(new FileReader(a[0])).getAsJsonObject()
            .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        TW = geo.getAsJsonObject("description").get("texture_width").getAsInt();
        TH = geo.getAsJsonObject("description").get("texture_height").getAsInt();
        tex = ImageIO.read(new File(a[1]));
        for (JsonElement e : geo.getAsJsonArray("bones")) {
            JsonObject o = e.getAsJsonObject();
            Bone b = new Bone();
            b.name = o.get("name").getAsString();
            b.parent = o.has("parent") ? o.get("parent").getAsString() : null;
            double[] p = arr(o, "pivot", 0);
            b.pivot = new double[]{-p[0] / 16, p[1] / 16, p[2] / 16};
            double[] r = arr(o, "rotation", 0);
            b.rx = Math.toRadians(-r[0]); b.ry = Math.toRadians(-r[1]); b.rz = Math.toRadians(r[2]);
            if (o.has("cubes")) for (JsonElement ce : o.getAsJsonArray("cubes")) addCube(b, ce.getAsJsonObject());
            bones.put(b.name, b);
        }
        // 任意の引数: [拡大率] [ポーズのカンマ区切り] [向き（度）のカンマ区切り]。兵姫のモデル（識別子に valkyrie）はスカートの無い姿勢で描く
        valkyrie = geo.getAsJsonObject("description").get("identifier").getAsString().contains("valkyrie");
        String[] poses = a.length > 4 ? a[4].split(",") : new String[]{"idle", "walk", "spread", "death"};
        double[] views = a.length > 5 ? Arrays.stream(a[5].split(",")).mapToDouble(Double::parseDouble).toArray() : new double[]{0, 35, 90, 180};
        int s = a.length > 3 ? Integer.parseInt(a[3]) : 7, cw = 30 * s, ch = 40 * s;
        BufferedImage out = new BufferedImage(cw * views.length, ch * poses.length, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(0x9fb8d6)); g.fillRect(0, 0, out.getWidth(), out.getHeight());
        for (int pi = 0; pi < poses.length; pi++) {
            Map<String, double[]> pose = pose(poses[pi]);
            for (int vi = 0; vi < views.length; vi++) {
                render(g, pose, Math.toRadians(views[vi]), vi * cw + cw / 2, pi * ch + ch - 3 * s, s * 16);
                g.setColor(Color.DARK_GRAY); g.drawString(poses[pi] + " / " + (int) views[vi] + "deg", vi * cw + 6, pi * ch + 14);
            }
        }
        ImageIO.write(out, "png", new File(a[2]));
    }

    /** v をピボット p のまわりに回す（X → Y → Z の順に掛ける = GeckoLib の Z・Y・X の行列の積） */
    static void rotateAround(double[] v, double[] p, double rx, double ry, double rz) {
        double x = v[0] - p[0], y = v[1] - p[1], z = v[2] - p[2], t;
        t = y * Math.cos(rx) - z * Math.sin(rx); z = y * Math.sin(rx) + z * Math.cos(rx); y = t;
        t = x * Math.cos(ry) + z * Math.sin(ry); z = -x * Math.sin(ry) + z * Math.cos(ry); x = t;
        t = x * Math.cos(rz) - y * Math.sin(rz); y = x * Math.sin(rz) + y * Math.cos(rz); x = t;
        v[0] = x + p[0]; v[1] = y + p[1]; v[2] = z + p[2];
    }

    static double[] arr(JsonObject o, String k, double def) {
        if (!o.has(k)) return new double[]{def, def, def};
        JsonArray j = o.getAsJsonArray(k); return new double[]{j.get(0).getAsDouble(), j.get(1).getAsDouble(), j.get(2).getAsDouble()};
    }

    static void addCube(Bone b, JsonObject c) {
        double[] size = arr(c, "size", 0), origin = arr(c, "origin", 0);
        double inf = c.has("inflate") ? c.get("inflate").getAsDouble() / 16 : 0;
        double ox = -(origin[0] + size[0]) / 16, oy = origin[1] / 16, oz = origin[2] / 16;
        double sx = size[0] / 16, sy = size[1] / 16, sz = size[2] / 16;
        double[] blb = {ox - inf, oy - inf, oz - inf}, brb = {ox - inf, oy - inf, oz + sz + inf},
                 tlb = {ox - inf, oy + sy + inf, oz - inf}, trb = {ox - inf, oy + sy + inf, oz + sz + inf},
                 tlf = {ox + sx + inf, oy + sy + inf, oz - inf}, trf = {ox + sx + inf, oy + sy + inf, oz + sz + inf},
                 blf = {ox + sx + inf, oy - inf, oz - inf}, brf = {ox + sx + inf, oy - inf, oz + sz + inf};
        if (c.has("rotation")) { // 箱ごとの回転（Bedrock の cube rotation。骨と同じ向きの取り方で、ピボットのまわりに Z・Y・X の順で掛ける）
            double[] r = arr(c, "rotation", 0), cp = c.has("pivot") ? arr(c, "pivot", 0)
                    : new double[]{origin[0] + size[0] / 2, origin[1] + size[1] / 2, origin[2] + size[2] / 2};
            double[] pv = {-cp[0] / 16, cp[1] / 16, cp[2] / 16};
            double rx = Math.toRadians(-r[0]), ry = Math.toRadians(-r[1]), rz = Math.toRadians(r[2]);
            for (double[] v : new double[][]{blb, brb, tlb, trb, tlf, trf, blf, brf}) rotateAround(v, pv, rx, ry, rz);
        }
        if (c.get("uv").isJsonObject()) { // 面ごとの UV（uv / uv_size）
            JsonObject f = c.getAsJsonObject("uv");
            double[][][] verts = {{trb, tlb, blb, brb}, {tlf, trf, brf, blf}, {tlb, tlf, blf, blb}, {trf, trb, brb, brf}, {trb, trf, tlf, tlb}, {blb, blf, brf, brb}};
            String[] names = {"west", "east", "north", "south", "up", "down"};
            for (int i = 0; i < 6; i++) {
                if (!f.has(names[i])) continue;
                JsonObject fo = f.getAsJsonObject(names[i]);
                JsonArray fu = fo.getAsJsonArray("uv"), fs = fo.getAsJsonArray("uv_size");
                double[][] q = verts[i];
                b.quads.add(new double[][]{q[0], q[1], q[2], q[3], {fu.get(0).getAsDouble(), fu.get(1).getAsDouble(), fs.get(0).getAsDouble(), fs.get(1).getAsDouble()}});
            }
            return;
        }
        JsonArray uvj = c.getAsJsonArray("uv"); double u = uvj.get(0).getAsDouble(), v = uvj.get(1).getAsDouble();
        double fx = Math.floor(size[0]), fy = Math.floor(size[1]), fz = Math.floor(size[2]);
        // {v0,v1,v2,v3, uv{u,v,uSize,vSize}}
        b.quads.add(new double[][]{trb, tlb, blb, brb, {u + fz + fx, v + fz, fz, fy}}); // WEST
        b.quads.add(new double[][]{tlf, trf, brf, blf, {u, v + fz, fz, fy}});           // EAST
        b.quads.add(new double[][]{tlb, tlf, blf, blb, {u + fz, v + fz, fx, fy}});      // NORTH
        b.quads.add(new double[][]{trf, trb, brb, brf, {u + fz + fx + fz, v + fz, fx, fy}}); // SOUTH
        b.quads.add(new double[][]{trb, trf, tlf, tlb, {u + fz, v, fx, fz}});           // UP
        b.quads.add(new double[][]{blb, blf, brf, brb, {u + fz + fx, v + fz, fx, -fz}}); // DOWN
    }

    // ElenorModel#setCustomAnimations と同じ計算（頭の向き・キーフレームの揺れは 0）。
    // 値は {rotX, rotY, rotZ, posY}（NaN = geo.json の初期値のまま）
    static Map<String, double[]> pose(String name) {
        double death = name.equals("death") ? 1 : 0, alive = 1 - death;
        double walk = (name.equals("walk") ? 1 : 0) * alive, limbSwing = 0, age = 0;
        double attackArc = (name.equals("spread") ? 1 : 0) * alive;
        double keyframed = name.equals("spread") ? Math.toRadians(50) : 0;
        double N = Double.NaN;
        Map<String, double[]> m = new HashMap<>();
        m.put("root", new double[]{N, N, N, -9.5 * death});
        m.put("body", new double[]{-0.12 * death, N, N, 0});
        m.put("head", new double[]{-0.4 * death, 0, N, 0});
        double legSwing = Math.cos(limbSwing * 0.6662) * 1.4 * walk * 0.5; // 半歩の姿勢
        m.put("right_leg", new double[]{legSwing + 1.45 * death, N, 0.12 * death, 0});
        m.put("left_leg", new double[]{-legSwing + 1.45 * death, N, -0.12 * death, 0});
        double armSwing = Math.cos(limbSwing * 0.6662 + Math.PI) * 0.8 * walk * 0.5;
        double spread = Math.max(0, Math.min(1.3, 0.30 + walk * 0.35 + attackArc * 0.45 + keyframed)) * alive + 1.45 * death;
        double armOut = (0.12 + Math.cos(age * 0.09) * 0.05 + 0.05) * alive + spread * 0.25 + 0.15 * death;
        m.put("right_arm", new double[]{armSwing + attackArc * 1.6 + 0.35 * death, N, armOut, 0});
        m.put("left_arm", new double[]{-armSwing + 0.35 * death, N, -armOut, 0});
        for (int i = 0; i < 8; i++) m.put("skirt_panel_" + i, new double[]{spread, N, N, 0});
        double slope = Math.tan(spread);
        double upper = 0.1 + walk * 0.2;
        double mid = Math.max(0.1, Math.atan((0.2 + 4 * slope) / 6)) + 0.03 + walk * 0.1;
        double tip = Math.max(mid, Math.atan(1.05 * slope) + 0.03) + walk * 0.1;
        if (valkyrie) { // スカートが無いので髪は背中に沿って少しだけ後ろへ、腕は少しだけ開く
            upper = 0.06 + walk * 0.15; mid = upper + 0.04 + walk * 0.08; tip = mid + 0.04 + walk * 0.08;
            m.put("right_arm", new double[]{armSwing + attackArc * 1.6 + 0.35 * death, N, 0.1 + 0.15 * death, 0});
            m.put("left_arm", new double[]{-armSwing + 0.35 * death, N, -0.1 - 0.15 * death, 0});
        }
        m.put("hair_upper", new double[]{-upper, N, N, 0});
        m.put("hair_mid", new double[]{-(mid - upper), N, N, 0});
        m.put("hair_tip", new double[]{-(tip - mid), N, N, 0});
        return m;
    }

    static double[][] matrix(Bone b, Map<String, double[]> pose) {
        double[][] parent = b.parent == null ? identity() : matrix(bones.get(b.parent), pose);
        double rx = b.rx, ry = b.ry, rz = b.rz;
        double[] o = pose.get(b.name);
        if (o != null) { if (!Double.isNaN(o[0])) rx = o[0]; if (!Double.isNaN(o[1])) ry = o[1]; if (!Double.isNaN(o[2])) rz = o[2]; }
        double[] p = b.pivot;
        double posY = (o != null && o.length > 3) ? o[3] / 16 : 0; // GeckoLib: 位置オフセットはピボットより先に適用
        double[][] m = mul(parent, translate(0, posY, 0));
        m = mul(m, translate(p[0], p[1], p[2]));
        m = mul(m, rotZ(rz)); m = mul(m, rotY(ry)); m = mul(m, rotX(rx));
        return mul(m, translate(-p[0], -p[1], -p[2]));
    }

    record Poly(double depth, Path2D shape, Color color) {}

    static void render(Graphics2D g, Map<String, double[]> pose, double viewYaw, int cx, int cy, double scale) {
        List<Poly> polys = new ArrayList<>();
        double[][] view = rotY(viewYaw);
        for (Bone b : bones.values()) {
            double[][] m = mul(view, matrix(b, pose));
            for (double[][] q : b.quads) {
                double[][] w = new double[4][];
                for (int i = 0; i < 4; i++) w[i] = apply(m, q[i]);
                double[] uv = q[4];
                int nu = (int) Math.abs(uv[2]), nv = (int) Math.abs(uv[3]);
                if (nu == 0 || nv == 0) continue;
                double[] e1 = sub(w[1], w[0]), e2 = sub(w[3], w[0]);
                double[] n = cross(e1, e2); double len = Math.sqrt(dot(n, n)); if (len == 0) continue;
                double light = 0.62 + 0.38 * Math.abs(dot(n, new double[]{0.3, 0.8, -0.5}) / len);
                for (int i = 0; i < nu; i++) for (int j = 0; j < nv; j++) {
                    double tu = uv[0] + uv[2] - (i + 0.5) * uv[2] / nu; // v0 = 右端 (u+uSize)
                    double tv = uv[1] + (j + 0.5) * uv[3] / nv;
                    int px = (int) Math.floor(tu), py = (int) Math.floor(tv);
                    if (px < 0 || py < 0 || px >= TW || py >= TH) continue;
                    int argb = tex.getRGB(px, py);
                    if ((argb >>> 24) == 0) continue;
                    double s0 = (double) i / nu, s1 = (double) (i + 1) / nu, t0 = (double) j / nv, t1 = (double) (j + 1) / nv;
                    double[][] cs = {pt(w[0], e1, e2, s0, t0), pt(w[0], e1, e2, s1, t0), pt(w[0], e1, e2, s1, t1), pt(w[0], e1, e2, s0, t1)};
                    Path2D path = new Path2D.Double(); double depth = 0;
                    for (int k = 0; k < 4; k++) {
                        double sxp = cx - cs[k][0] * scale, syp = cy - cs[k][1] * scale; // 正面から見て右腕が左に来る
                        if (k == 0) path.moveTo(sxp, syp); else path.lineTo(sxp, syp);
                        depth += cs[k][2];
                    }
                    path.closePath();
                    Color c = new Color(argb);
                    polys.add(new Poly(depth / 4, path, new Color((int) (c.getRed() * light), (int) (c.getGreen() * light), (int) (c.getBlue() * light))));
                }
            }
        }
        polys.sort((p1, p2) -> Double.compare(p2.depth, p1.depth)); // 遠い（+Z）方から
        for (Poly p : polys) { g.setColor(p.color); g.fill(p.shape); g.draw(p.shape); }
    }

    static double[] pt(double[] o, double[] e1, double[] e2, double s, double t) { return new double[]{o[0] + e1[0] * s + e2[0] * t, o[1] + e1[1] * s + e2[1] * t, o[2] + e1[2] * s + e2[2] * t}; }
    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[][] identity() { return new double[][]{{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}}; }
    static double[][] translate(double x, double y, double z) { double[][] m = identity(); m[0][3] = x; m[1][3] = y; m[2][3] = z; return m; }
    static double[][] rotX(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[][]{{1, 0, 0, 0}, {0, c, -s, 0}, {0, s, c, 0}, {0, 0, 0, 1}}; }
    static double[][] rotY(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[][]{{c, 0, s, 0}, {0, 1, 0, 0}, {-s, 0, c, 0}, {0, 0, 0, 1}}; }
    static double[][] rotZ(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[][]{{c, -s, 0, 0}, {s, c, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}}; }
    static double[][] mul(double[][] a, double[][] b) { double[][] r = new double[4][4]; for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) for (int k = 0; k < 4; k++) r[i][j] += a[i][k] * b[k][j]; return r; }
    static double[] apply(double[][] m, double[] v) { return new double[]{m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2] + m[0][3], m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2] + m[1][3], m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2] + m[2][3]}; }
}
