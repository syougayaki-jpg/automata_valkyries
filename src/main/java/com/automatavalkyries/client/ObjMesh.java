package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 三角形のメッシュ（Wavefront OBJ）を読み、エンティティの描画で使う（GeckoLib は箱しか描けないので、メッシュのモデル用。
 * 事象観測体アカシック・プローブ。2026-10-25）。
 * <p>
 * 読むのは頂点・UV・法線と面（三角形か四角形。多角形は扇に分ける）だけで、マテリアルは見ない（テクスチャは描画の側で決める）。
 * UV の縦は OBJ（下が 0）からマイクラ（上が 0）へ反転する。エンティティの描画は四角形ごとなので、三角形は最後の頂点を 2 度出して四角形にする。
 * 読み込みはリソースの読み直しのとき（裏のスレッド）にまとめて行い、描画では作っておいた配列を流すだけ
 * </p>
 */
public final class ObjMesh {

    /** 頂点ごと: 位置 3・UV 2・法線 3。4 頂点で 1 つの四角形 */
    private final float[] data;
    private final int vertices;

    private ObjMesh(float[] data) {
        this.data = data;
        this.vertices = data.length / 8;
    }

    private static final Map<ResourceLocation, ObjMesh> MESHES = new ConcurrentHashMap<>();
    private static final List<ResourceLocation> WANTED = new ArrayList<>();

    /** 読み直しのときに読むメッシュを登録する（クライアントの初期化のとき） */
    public static void want(ResourceLocation location) {
        if (!WANTED.contains(location)) WANTED.add(location);
    }

    /** 読み込み済みのメッシュ（無ければ null。読めなかった・まだ読み直していない） */
    public static ObjMesh get(ResourceLocation location) {
        return MESHES.get(location);
    }

    /** 描く。color は ARGB */
    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        int a = color >>> 24, r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        float[] d = data;
        for (int i = 0, o = 0; i < vertices; i++, o += 8) {
            consumer.vertex(pose, d[o], d[o + 1], d[o + 2]).color(r, g, b, a).uv(d[o + 3], d[o + 4]).overlayCoords(overlay).uv2(light)
                    .normal(normal, d[o + 5], d[o + 6], d[o + 7]).endVertex();
        }
    }

    static ObjMesh parse(BufferedReader reader) throws java.io.IOException {
        List<float[]> positions = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        FloatList out = new FloatList();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.length() < 2) continue;
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "v" -> positions.add(new float[] {Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])});
                case "vt" -> uvs.add(new float[] {Float.parseFloat(p[1]), 1.0F - Float.parseFloat(p[2])});
                case "vn" -> normals.add(new float[] {Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])});
                case "f" -> {
                    int n = p.length - 1;
                    if (n < 3) continue;
                    int[][] corners = new int[n][];
                    for (int i = 0; i < n; i++) corners[i] = corner(p[i + 1], positions.size(), uvs.size(), normals.size());
                    if (n == 4) {
                        for (int i = 0; i < 4; i++) put(out, corners[i], positions, uvs, normals);
                        continue;
                    }
                    for (int i = 1; i + 1 < n; i++) { // 扇に分けた三角形を、最後の頂点を 2 度出して四角形に
                        put(out, corners[0], positions, uvs, normals);
                        put(out, corners[i], positions, uvs, normals);
                        put(out, corners[i + 1], positions, uvs, normals);
                        put(out, corners[i + 1], positions, uvs, normals);
                    }
                }
                default -> {}
            }
        }
        return new ObjMesh(out.toArray());
    }

    /** 面の角（v/vt/vn）を 0 始まりの番号へ（負の番号は後ろから） */
    private static int[] corner(String token, int nv, int nt, int nn) {
        String[] parts = token.split("/", -1);
        return new int[] {index(parts, 0, nv), index(parts, 1, nt), index(parts, 2, nn)};
    }

    private static int index(String[] parts, int i, int size) {
        if (parts.length <= i || parts[i].isEmpty()) return -1;
        int value = Integer.parseInt(parts[i]);
        return value < 0 ? size + value : value - 1;
    }

    private static void put(FloatList out, int[] c, List<float[]> positions, List<float[]> uvs, List<float[]> normals) {
        float[] v = positions.get(c[0]);
        float[] t = c[1] >= 0 ? uvs.get(c[1]) : new float[] {0, 0};
        float[] n = c[2] >= 0 ? normals.get(c[2]) : new float[] {0, 1, 0};
        out.add(v[0], v[1], v[2], t[0], t[1], n[0], n[1], n[2]);
    }

    private static final class FloatList {
        private float[] values = new float[1 << 16];
        private int size;

        void add(float... more) {
            if (size + more.length > values.length) values = java.util.Arrays.copyOf(values, Math.max(values.length * 2, size + more.length));
            System.arraycopy(more, 0, values, size, more.length);
            size += more.length;
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(values, size);
        }
    }

    /** リソースの読み直しで、登録されたメッシュを読み直す */
    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, ObjMesh>> {
        @Override
        protected Map<ResourceLocation, ObjMesh> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, ObjMesh> loaded = new java.util.HashMap<>();
            for (ResourceLocation location : WANTED) {
                var resource = manager.getResource(location);
                if (resource.isEmpty()) {
                    AutomataValkyries.LOGGER.warn("[client] Mesh {} was not found", location);
                    continue;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8))) {
                    ObjMesh mesh = parse(reader);
                    loaded.put(location, mesh);
                    AutomataValkyries.LOGGER.info("[client] Loaded mesh {} ({} quads)", location, mesh.vertices / 4);
                } catch (Exception e) {
                    AutomataValkyries.LOGGER.warn("[client] Could not load mesh {}: {}", location, e.toString());
                }
            }
            return loaded;
        }

        @Override
        protected void apply(Map<ResourceLocation, ObjMesh> loaded, ResourceManager manager, ProfilerFiller profiler) {
            MESHES.clear();
            MESHES.putAll(loaded);
        }
    }
}
