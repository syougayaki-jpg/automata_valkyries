package com.automatavalkyries.client.particle;

import com.automatavalkyries.particle.DamageNumberOptions;
import com.automatavalkyries.particle.FxOptions;
import com.automatavalkyries.registry.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

import java.text.NumberFormat;

/**
 * 自作エフェクトの粒子の見た目と動き（クライアントだけ）。どれも自分で光る（暗い所でも明るい）、半透明。
 * <ul>
 *   <li>きらめき: 瞬きながら（絵が切り替わる）すぐ消える</li>
 *   <li>光の玉: 減速しながら薄れる</li>
 *   <li>衝撃波: 地面と平行な輪が広がって薄れる</li>
 *   <li>斬撃: 大きな三日月が一瞬で走って消える</li>
 *   <li>時計の数字: ゆっくり昇り、浮かんで消える</li>
 *   <li>六角の欠片・空間の破片: 回りながら飛び散る</li>
 *   <li>ダメージの数字: 大きな文字が跳ね上がって薄れる</li>
 * </ul>
 */
public final class FxParticles {

    private FxParticles() {}

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SPARK.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Fx(l, x, y, z, dx, dy, dz, o, sprites, Kind.SPARK));
        event.registerSpriteSet(ModParticles.GLOW.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Fx(l, x, y, z, dx, dy, dz, o, sprites, Kind.GLOW));
        event.registerSpriteSet(ModParticles.SHOCKWAVE.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Shockwave(l, x, y, z, o, sprites));
        event.registerSpriteSet(ModParticles.SLASH.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Fx(l, x, y, z, dx, dy, dz, o, sprites, Kind.SLASH));
        event.registerSpriteSet(ModParticles.GLYPH.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Fx(l, x, y, z, dx, dy, dz, o, sprites, Kind.GLYPH));
        event.registerSpriteSet(ModParticles.HEX.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Fx(l, x, y, z, dx, dy, dz, o, sprites, Kind.HEX));
        event.registerSpriteSet(ModParticles.VOID_SHARD.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Fx(l, x, y, z, dx, dy, dz, o, sprites, Kind.SHARD));
        event.registerSpecial(ModParticles.DAMAGE_NUMBER.get(), (o, l, x, y, z, dx, dy, dz) -> new DamageNumber(l, x, y, z, o));
    }

    enum Kind { SPARK, GLOW, SLASH, GLYPH, HEX, SHARD }

    static class Fx extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final Kind kind;
        private final float baseSize, baseAlpha, spin;

        Fx(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, FxOptions o, SpriteSet sprites, Kind kind) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.kind = kind;
            this.xd = dx; this.yd = dy; this.zd = dz;
            this.lifetime = Math.max(1, o.lifetime());
            this.rCol = ((o.color() >> 16) & 255) / 255F;
            this.gCol = ((o.color() >> 8) & 255) / 255F;
            this.bCol = (o.color() & 255) / 255F;
            this.baseSize = o.scale();
            this.quadSize = o.scale();
            this.baseAlpha = kind == Kind.GLOW ? 0.85F : 1F;
            this.alpha = baseAlpha;
            this.hasPhysics = false;
            this.gravity = kind == Kind.SHARD ? 0.04F : 0F;
            this.friction = kind == Kind.GLOW ? 0.9F : kind == Kind.SLASH ? 0.5F : 0.86F;
            this.roll = kind == Kind.SLASH ? o.roll() : random.nextFloat() * Mth.TWO_PI;
            this.oRoll = roll;
            this.spin = kind == Kind.HEX || kind == Kind.SHARD ? (random.nextFloat() - 0.5F) * 0.5F : kind == Kind.GLYPH ? (random.nextFloat() - 0.5F) * 0.06F : 0;
            if (kind == Kind.SPARK) setSpriteFromAge(sprites);
            else pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = (float) age / lifetime;
            oRoll = roll;
            roll += spin;
            switch (kind) {
                case SPARK -> {
                    setSpriteFromAge(sprites);
                    quadSize = baseSize * (t < 0.2F ? 0.5F + t * 2.5F : 1F - (t - 0.2F) * 0.9F);
                }
                case GLOW -> {
                    quadSize = baseSize * (t < 0.15F ? 0.6F + t * 2.7F : 1F - (t - 0.15F) * 0.6F);
                    alpha = baseAlpha * (1 - t * t);
                }
                case SLASH -> {
                    quadSize = baseSize * (0.85F + t * 0.3F);
                    alpha = t < 0.25F ? 1F : 1F - (t - 0.25F) / 0.75F;
                }
                case GLYPH -> {
                    yd += 0.002;
                    alpha = Mth.sin(t * Mth.PI);
                }
                case HEX, SHARD -> alpha = t < 0.6F ? 1F : 1F - (t - 0.6F) / 0.4F;
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 0xF000F0;
        }
    }

    /** 地面と平行に広がる輪（板を寝かせて描く） */
    static class Shockwave extends TextureSheetParticle {
        private final float maxRadius;

        Shockwave(ClientLevel level, double x, double y, double z, FxOptions o, SpriteSet sprites) {
            super(level, x, y, z);
            this.lifetime = Math.max(1, o.lifetime());
            this.rCol = ((o.color() >> 16) & 255) / 255F;
            this.gCol = ((o.color() >> 8) & 255) / 255F;
            this.bCol = (o.color() & 255) / 255F;
            this.maxRadius = o.scale();
            this.hasPhysics = false;
            this.xd = this.yd = this.zd = 0;
            pickSprite(sprites);
        }

        @Override
        public void render(VertexConsumer vc, Camera camera, float partialTick) {
            float t = Math.min(1F, (age + partialTick) / lifetime);
            float radius = maxRadius * (float) Math.pow(t, 0.55);
            float a = (1 - t) * (1 - t);
            Vec3 cam = camera.getPosition();
            float cx = (float) (Mth.lerp(partialTick, xo, x) - cam.x);
            float cy = (float) (Mth.lerp(partialTick, yo, y) - cam.y);
            float cz = (float) (Mth.lerp(partialTick, zo, z) - cam.z);
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            int light = 0xF000F0;
            float[][] corners = {{-radius, -radius}, {radius, -radius}, {radius, radius}, {-radius, radius}};
            float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
            for (int pass = 0; pass < 2; pass++) { // 上からも下からも見える
                for (int k = 0; k < 4; k++) {
                    int i = pass == 0 ? k : 3 - k;
                    vc.vertex(cx + corners[i][0], cy, cz + corners[i][1]).uv(uv[i][0], uv[i][1]).color(rCol, gCol, bCol, a).uv2(light).endVertex();
                }
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** ダメージの数字: 大きな文字が跳ね上がって薄れる（いつも手前に見える） */
    static class DamageNumber extends Particle {
        private final String text;
        private final int color;

        DamageNumber(ClientLevel level, double x, double y, double z, DamageNumberOptions o) {
            super(level, x, y, z);
            this.text = NumberFormat.getIntegerInstance().format(o.value());
            this.color = o.color();
            this.lifetime = 40;
            this.hasPhysics = false;
            this.gravity = 0;
            this.yd = 0.12;
            this.xd = (random.nextDouble() - 0.5) * 0.04;
            this.zd = (random.nextDouble() - 0.5) * 0.04;
        }

        @Override
        public void tick() {
            super.tick();
            yd *= 0.82;
        }

        @Override
        public void render(VertexConsumer ignored, Camera camera, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            float t = (age + partialTick) / lifetime;
            float pop = t < 0.12F ? 0.6F + t / 0.12F * 0.8F : t < 0.2F ? 1.4F - (t - 0.12F) / 0.08F * 0.4F : 1F;
            int alpha = (int) (255 * (t < 0.7F ? 1F : 1F - (t - 0.7F) / 0.3F));
            if (alpha < 8) return;
            Vec3 cam = camera.getPosition();
            PoseStack pose = new PoseStack();
            pose.translate(Mth.lerp(partialTick, xo, x) - cam.x, Mth.lerp(partialTick, yo, y) - cam.y, Mth.lerp(partialTick, zo, z) - cam.z);
            pose.mulPose(camera.rotation());
            float s = 0.045F * pop;
            pose.scale(-s, -s, s);
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            float w = font.width(text) / 2F;
            int argb = (alpha << 24) | (color & 0xFFFFFF);
            int outline = (alpha << 24);
            // 縁取り（黒）を 4 方向にずらして描いてから、本体
            for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                font.drawInBatch(text, -w + d[0], d[1], outline, false, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            }
            font.drawInBatch(text, -w, 0, argb, false, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            buffers.endBatch();
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.CUSTOM;
        }
    }
}
