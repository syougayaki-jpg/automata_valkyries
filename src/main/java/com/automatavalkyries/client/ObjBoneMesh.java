package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * OBJ の面のまま GeckoLib の骨に載せる形（2026-10-21 ユーザー指定「オススメのやりかたで全部かえてみて」。零号機の体）。
 * GeckoLib 4.8.4 は geo の poly_mesh を読むだけで描かないので、tools/lc_objmesh.py が骨ごとに分けた面を
 * assets/&lt;modid&gt;/mesh/&lt;名前&gt;.bin（gzip）に書き、描く側が骨を描くときに同じ骨の変換で一緒に描く（{@link #render}）。
 * 座標は geo の px（GeckoLib と同じく X を返して 1/16 にする）。UV は geo のテクスチャ全体に対する 0..1 なので、箱と同じ絵・光の絵で描ける。
 * 読み込みは最初に描くとき（リソースを読み直したら読み直す）。
 */
public final class ObjBoneMesh {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 1 面の数: 法線 3 ＋ 4 点 ×（x, y, z, u, v） */
    private static final int STRIDE = 3 + 4 * 5;

    private final ResourceLocation location;
    private Map<String, float[]> bones;
    private ResourceManager loadedFrom;
    private final Vector4f pos = new Vector4f();
    private final Vector3f nrm = new Vector3f();

    public ObjBoneMesh(String name) {
        this.location = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "mesh/" + name + ".bin");
    }

    /** 骨の面（無ければ null） */
    float[] quads(String bone) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        if (bones == null || loadedFrom != rm) {
            bones = load(rm);
            loadedFrom = rm;
        }
        return bones.get(bone);
    }

    private Map<String, float[]> load(ResourceManager rm) {
        Map<String, float[]> out = new HashMap<>();
        Optional<Resource> res = rm.getResource(location);
        if (res.isEmpty()) {
            LOGGER.warn("Mesh {} not found", location);
            return out;
        }
        try (InputStream raw = res.get().open(); DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            int version = in.readInt();
            if (magic[0] != 'L' || magic[1] != 'C' || magic[2] != 'M' || magic[3] != 'S' || version != 1)
                throw new IOException("bad header");
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                String name = in.readUTF();
                int count = in.readInt();
                float[] data = new float[count * STRIDE];
                for (int k = 0; k < data.length; k++) data[k] = in.readFloat();
                for (int k = 0; k < count; k++) {                                 // geo → GeckoLib の描く座標（X を返して 1/16）
                    int o = k * STRIDE;
                    data[o] = -data[o];
                    for (int v = 0; v < 4; v++) {
                        int p = o + 3 + v * 5;
                        data[p] = -data[p] / 16.0F;
                        data[p + 1] /= 16.0F;
                        data[p + 2] /= 16.0F;
                    }
                }
                out.put(name, data);
            }
        } catch (IOException ex) {
            LOGGER.error("Failed to load mesh {}", location, ex);
            out.clear();
        }
        return out;
    }

    /** 骨の面を、骨の変換が掛かった pose で描く（GeoRenderer#renderCubesOfBone の後に呼ぶ） */
    public void render(String bone, PoseStack pose, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
        float[] d = quads(bone);
        if (d == null) return;
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        for (int o = 0; o < d.length; o += STRIDE) {
            nm.transform(nrm.set(d[o], d[o + 1], d[o + 2]));
            for (int v = 0; v < 4; v++) {
                int p = o + 3 + v * 5;
                m.transform(pos.set(d[p], d[p + 1], d[p + 2], 1.0F));
                buffer.vertex(pos.x(), pos.y(), pos.z(), r, g, b, a, d[p + 3], d[p + 4], overlay, light, nrm.x(), nrm.y(), nrm.z());
            }
        }
    }
}
