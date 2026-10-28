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
 *   <li>回る刃: 鉤の歯の輪が高速で回り、歯の絵を交互に替えて流れて見せる</li>
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
        event.registerSpriteSet(ModParticles.SAW.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Fx(l, x, y, z, dx, dy, dz, o, sprites, Kind.SAW));
        event.registerSpriteSet(ModParticles.STAR.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Star(l, x, y, z, dx, dy, dz, o, sprites));
        event.registerSpriteSet(ModParticles.MANDALA.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Mandala(l, x, y, z, o, sprites));
        event.registerSpriteSet(ModParticles.BEAM_RING.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new BeamRing(l, x, y, z, dx, dy, dz, o, sprites));
        event.registerSpriteSet(ModParticles.RIBBON.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Fx(l, x, y, z, dx, dy, dz, o, sprites, Kind.RIBBON));
        event.registerSpriteSet(ModParticles.PETAL.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Fx(l, x, y, z, dx, dy, dz, o, sprites, Kind.PETAL));
        event.registerSpriteSet(ModParticles.BLOOM.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Fx(l, x, y, z, dx, dy, dz, o, sprites, Kind.BLOOM));
        event.registerSpriteSet(ModParticles.LACE.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Shockwave(l, x, y, z, o, sprites));
        event.registerSpriteSet(ModParticles.STREAK.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Fx(l, x, y, z, dx, dy, dz, o, sprites, Kind.STREAK));
        event.registerSpriteSet(ModParticles.CURTAIN.get(), sprites -> (o, l, x, y, z, dx, dy, dz) -> new Fx(l, x, y, z, dx, dy, dz, o, sprites, Kind.CURTAIN));
        event.registerSpecial(ModParticles.DAMAGE_NUMBER.get(), (o, l, x, y, z, dx, dy, dz) -> new DamageNumber(l, x, y, z, o));
    }

    enum Kind { SPARK, GLOW, SLASH, GLYPH, HEX, SHARD, SAW, RIBBON, PETAL, BLOOM, STREAK, CURTAIN }

    static class Fx extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final Kind kind;
        private final float baseSize, baseAlpha, spin;
        private float sway;

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
            this.gravity = kind == Kind.SHARD ? 0.04F : kind == Kind.PETAL ? 0.006F : kind == Kind.RIBBON ? 0.003F : kind == Kind.CURTAIN ? 0.01F : 0F;
            this.friction = kind == Kind.GLOW ? 0.9F : kind == Kind.SLASH || kind == Kind.SAW ? 0.5F : 0.86F;
            this.roll = kind == Kind.SLASH || kind == Kind.STREAK ? o.roll() : kind == Kind.CURTAIN ? 0F : random.nextFloat() * Mth.TWO_PI;
            this.oRoll = roll;
            this.spin = kind == Kind.SAW ? -1.1F : kind == Kind.HEX || kind == Kind.SHARD ? (random.nextFloat() - 0.5F) * 0.5F : kind == Kind.GLYPH ? (random.nextFloat() - 0.5F) * 0.06F
                    : kind == Kind.PETAL ? (random.nextFloat() - 0.5F) * 0.3F : kind == Kind.RIBBON ? (random.nextFloat() - 0.5F) * 0.12F : 0;
            this.sway = random.nextFloat() * Mth.TWO_PI;
            if (kind == Kind.SPARK || kind == Kind.SAW || kind == Kind.BLOOM || kind == Kind.RIBBON) setSpriteFromAge(sprites);
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
                case SAW -> {
                    sprite = sprites.get(age % 2, 1); // 歯の絵を交互に（回りながら歯が流れて見える）
                    quadSize = baseSize * (t < 0.2F ? 0.7F + t * 1.5F : 1F);
                    alpha = t < 0.5F ? 1F : 1F - (t - 0.5F) / 0.5F;
                }
                case HEX, SHARD -> alpha = t < 0.6F ? 1F : 1F - (t - 0.6F) / 0.4F;
                case RIBBON -> { // ねじれのコマを繰り返し、横に揺れながら漂う
                    sprite = sprites.get((age / 3) % 4, 3);
                    sway += 0.25F;
                    xd += Mth.sin(sway) * 0.004;
                    zd += Mth.cos(sway) * 0.004;
                    alpha = t < 0.75F ? 1F : 1F - (t - 0.75F) / 0.25F;
                }
                case PETAL -> { // 揺れながら舞い落ちる
                    sway += 0.18F;
                    xd += Mth.sin(sway) * 0.006;
                    alpha = t < 0.7F ? 1F : 1F - (t - 0.7F) / 0.3F;
                }
                case BLOOM -> { // 蕾から開いて、少し大きくなって消える
                    setSpriteFromAge(sprites);
                    quadSize = baseSize * (0.6F + 0.5F * Math.min(1F, t * 1.6F));
                    alpha = t < 0.6F ? 1F : 1F - (t - 0.6F) / 0.4F;
                }
                case STREAK -> { // 一瞬で伸びて消える
                    quadSize = baseSize * (t < 0.3F ? 0.4F + t * 2F : 1F);
                    alpha = 1F - t * t;
                }
                case CURTAIN -> alpha = t < 0.2F ? t / 0.2F : t < 0.7F ? 1F : 1F - (t - 0.7F) / 0.3F; // 現れて、降りて、消える
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

    /** 星: 光芒の長さが替わる 2 コマを瞬かせ、大きさが脈打ち、ゆっくり回る */
    static class Star extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final float baseSize, phase, spin;

        Star(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, FxOptions o, SpriteSet sprites) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.xd = dx; this.yd = dy; this.zd = dz;
            this.lifetime = Math.max(1, o.lifetime());
            this.rCol = ((o.color() >> 16) & 255) / 255F;
            this.gCol = ((o.color() >> 8) & 255) / 255F;
            this.bCol = (o.color() & 255) / 255F;
            this.baseSize = o.scale();
            this.quadSize = o.scale();
            this.hasPhysics = false;
            this.friction = 0.9F;
            this.gravity = 0;
            this.phase = random.nextFloat() * Mth.TWO_PI;
            this.spin = (random.nextFloat() - 0.5F) * 0.08F;
            this.roll = random.nextFloat() * 0.3F;
            this.oRoll = roll;
            setSprite(sprites.get(0, 1));
        }

        @Override
        public void tick() {
            super.tick();
            float t = (float) age / lifetime;
            oRoll = roll;
            roll += spin;
            setSprite(sprites.get((age / 3) % 2, 1));
            float twinkle = 0.75F + 0.25F * Mth.sin(age * 0.9F + phase);
            float grow = t < 0.15F ? t / 0.15F : 1F;
            quadSize = baseSize * twinkle * grow;
            alpha = t < 0.7F ? 1F : 1F - (t - 0.7F) / 0.3F;
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

    /** 星の陣: 地面と平行（または、こちらを向いて）に広がって回り、終わりに薄れる */
    static class Mandala extends TextureSheetParticle {
        private final float maxRadius;
        private final boolean facing;
        private final float dir;

        Mandala(ClientLevel level, double x, double y, double z, FxOptions o, SpriteSet sprites) {
            super(level, x, y, z);
            this.lifetime = Math.max(1, o.lifetime());
            this.rCol = ((o.color() >> 16) & 255) / 255F;
            this.gCol = ((o.color() >> 8) & 255) / 255F;
            this.bCol = (o.color() & 255) / 255F;
            this.maxRadius = o.scale();
            int mode = Math.round(o.roll());
            this.facing = mode >= 2;
            this.dir = mode % 2 == 0 ? 1F : -1F;
            this.hasPhysics = false;
            this.xd = this.yd = this.zd = 0;
            pickSprite(sprites);
        }

        @Override
        public void render(VertexConsumer vc, Camera camera, float partialTick) {
            float age = this.age + partialTick;
            float t = Math.min(1F, age / lifetime);
            float radius = maxRadius * (t < 0.2F ? (float) Math.sqrt(t / 0.2F) : 1F);
            float a = t < 0.6F ? 1F : 1F - (t - 0.6F) / 0.4F;
            float ang = dir * age * 0.05F;
            Vec3 cam = camera.getPosition();
            float cx = (float) (Mth.lerp(partialTick, xo, x) - cam.x);
            float cy = (float) (Mth.lerp(partialTick, yo, y) - cam.y);
            float cz = (float) (Mth.lerp(partialTick, zo, z) - cam.z);
            org.joml.Vector3f ax, ay;
            if (facing) { // こちらを向く板（カメラの右と上）
                org.joml.Quaternionf q = camera.rotation();
                ax = q.transform(new org.joml.Vector3f(1, 0, 0));
                ay = q.transform(new org.joml.Vector3f(0, 1, 0));
            } else {
                ax = new org.joml.Vector3f(1, 0, 0);
                ay = new org.joml.Vector3f(0, 0, 1);
            }
            float c = Mth.cos(ang) * radius, s = Mth.sin(ang) * radius;
            org.joml.Vector3f u = new org.joml.Vector3f(ax).mul(c).add(new org.joml.Vector3f(ay).mul(s));
            org.joml.Vector3f v = new org.joml.Vector3f(ay).mul(c).sub(new org.joml.Vector3f(ax).mul(s));
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            float[][] k = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
            for (int pass = 0; pass < 2; pass++) { // 表からも裏からも見える
                for (int i = 0; i < 4; i++) {
                    int j = pass == 0 ? i : 3 - i;
                    float px = cx + u.x * k[j][0] + v.x * k[j][1], py = cy + u.y * k[j][0] + v.y * k[j][1], pz = cz + u.z * k[j][0] + v.z * k[j][1];
                    vc.vertex(px, py, pz).uv(uv[j][0], uv[j][1]).color(rCol, gCol, bCol, a).uv2(0xF000F0).endVertex();
                }
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** 光線の輪: 渡された向き（軸）に垂直な面で、半径 scale まで広がりながら回って薄れる */
    static class BeamRing extends TextureSheetParticle {
        private final float maxRadius;
        private final org.joml.Vector3f u, v;

        BeamRing(ClientLevel level, double x, double y, double z, double ax, double ay, double az, FxOptions o, SpriteSet sprites) {
            super(level, x, y, z);
            this.lifetime = Math.max(1, o.lifetime());
            this.rCol = ((o.color() >> 16) & 255) / 255F;
            this.gCol = ((o.color() >> 8) & 255) / 255F;
            this.bCol = (o.color() & 255) / 255F;
            this.maxRadius = o.scale();
            this.hasPhysics = false;
            this.xd = this.yd = this.zd = 0;
            org.joml.Vector3f axis = new org.joml.Vector3f((float) ax, (float) ay, (float) az);
            if (axis.lengthSquared() < 1.0E-6F) axis.set(0, 1, 0);
            axis.normalize();
            org.joml.Vector3f ref = Math.abs(axis.y) > 0.9F ? new org.joml.Vector3f(1, 0, 0) : new org.joml.Vector3f(0, 1, 0);
            this.u = new org.joml.Vector3f(axis).cross(ref).normalize();
            this.v = new org.joml.Vector3f(axis).cross(u).normalize();
            pickSprite(sprites);
        }

        @Override
        public void render(VertexConsumer vc, Camera camera, float partialTick) {
            float age = this.age + partialTick;
            float t = Math.min(1F, age / lifetime);
            float radius = maxRadius * (float) Math.pow(t, 0.45);
            float a = (1 - t) * (1 - t);
            float ang = age * 0.08F;
            Vec3 cam = camera.getPosition();
            float cx = (float) (Mth.lerp(partialTick, xo, x) - cam.x);
            float cy = (float) (Mth.lerp(partialTick, yo, y) - cam.y);
            float cz = (float) (Mth.lerp(partialTick, zo, z) - cam.z);
            float c = Mth.cos(ang) * radius, s = Mth.sin(ang) * radius;
            org.joml.Vector3f a1 = new org.joml.Vector3f(u).mul(c).add(new org.joml.Vector3f(v).mul(s));
            org.joml.Vector3f a2 = new org.joml.Vector3f(v).mul(c).sub(new org.joml.Vector3f(u).mul(s));
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            float[][] k = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < 4; i++) {
                    int j = pass == 0 ? i : 3 - i;
                    vc.vertex(cx + a1.x * k[j][0] + a2.x * k[j][1], cy + a1.y * k[j][0] + a2.y * k[j][1], cz + a1.z * k[j][0] + a2.z * k[j][1])
                            .uv(uv[j][0], uv[j][1]).color(rCol, gCol, bCol, a).uv2(0xF000F0).endVertex();
                }
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
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
    /** 7 セグの数字 0〜9（画面の上が -y の向き。x・y は文字の左上、w・h は大きさ、t は線の太さ、grow は縁取りの太り） */
    static final class Segments {
        private Segments() {}

        /** 数字ごとに点く線（bit 0 上・1 右上・2 右下・3 下・4 左下・5 左上・6 真ん中） */
        private static final int[] MASKS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};

        static void digit(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, float x, float y, float w, float h, float t, int n, int argb, float grow) {
            if (n < 0 || n > 9) return;
            int k = MASKS[n];
            float mid = y + h / 2.0F;
            if ((k & 1) != 0) bar(vc, m, x, y, x + w, y + t, argb, grow);                                   // 上
            if ((k & 2) != 0) bar(vc, m, x + w - t, y, x + w, mid - 0.3F, argb, grow);                      // 右上
            if ((k & 4) != 0) bar(vc, m, x + w - t, mid + 0.3F, x + w, y + h, argb, grow);                  // 右下
            if ((k & 8) != 0) bar(vc, m, x, y + h - t, x + w, y + h, argb, grow);                           // 下
            if ((k & 16) != 0) bar(vc, m, x, mid + 0.3F, x + t, y + h, argb, grow);                         // 左下
            if ((k & 32) != 0) bar(vc, m, x, y, x + t, mid - 0.3F, argb, grow);                             // 左上
            if ((k & 64) != 0) bar(vc, m, x, mid - t / 2, x + w, mid + t / 2, argb, grow);                  // 真ん中
        }

        static void bar(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, float x0, float y0, float x1, float y1, int argb, float grow) {
            int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
            float z = grow > 0 ? 0.01F : 0.0F;                                              // 縁取りは少し奥に
            vc.vertex(m, x0 - grow, y0 - grow, z).color(r, g, b, a).uv2(0xF000F0).endVertex();
            vc.vertex(m, x0 - grow, y1 + grow, z).color(r, g, b, a).uv2(0xF000F0).endVertex();
            vc.vertex(m, x1 + grow, y1 + grow, z).color(r, g, b, a).uv2(0xF000F0).endVertex();
            vc.vertex(m, x1 + grow, y0 - grow, z).color(r, g, b, a).uv2(0xF000F0).endVertex();
        }
    }

    static class DamageNumber extends Particle {
        /**
         * 色の字を縁取りより手前（カメラ側）へ出す量（字の座標で）。字の描き方は半透明の面を距離で並べ替えるので、
         * 同じ深さだと黒い縁取りが色の字の前に来ることがあった（2026-10-29 ユーザー指摘「黒い表記が前にでて見にくい」）。
         * 縦横を (-s, -s, s) で裏返しているので、カメラ側は字の座標の -z（+z にしたら色の字が縁取りの奥に隠れた）
         */
        private static final float FRONT = -0.5F;
        private static final int[][] OUTLINE = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        private final String text;
        private final int color;
        private final boolean segments;

        DamageNumber(ClientLevel level, double x, double y, double z, DamageNumberOptions o) {
            super(level, x, y, z);
            this.segments = o.segments();
            this.text = segments ? Long.toString(Math.max(0L, o.value())) : NumberFormat.getIntegerInstance().format(o.value());
            this.color = o.color() & 0xFFFFFF;
            this.lifetime = 50;
            this.hasPhysics = false;
            this.gravity = 0;
            this.yd = 0.12;
            this.xd = (random.nextDouble() - 0.5) * 0.08; // 続けて当たっても数字が重なりにくいよう、少し散らす
            this.zd = (random.nextDouble() - 0.5) * 0.08;
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
            int alpha = (int) (255 * (t < 0.75F ? 1F : 1F - (t - 0.75F) / 0.25F));
            if (alpha < 8) return;
            Vec3 cam = camera.getPosition();
            double px = Mth.lerp(partialTick, xo, x) - cam.x, py = Mth.lerp(partialTick, yo, y) - cam.y, pz = Mth.lerp(partialTick, zo, z) - cam.z;
            PoseStack pose = new PoseStack();
            pose.translate(px, py, pz);
            pose.mulPose(camera.rotation());
            // 遠くても読めるよう、8 ブロックより遠いと距離に合わせて大きく（最大 3 倍。2026-10-29 ユーザー指定「ダメージ表記を少し見やすく」）
            float far = (float) Mth.clamp(Math.sqrt(px * px + py * py + pz * pz) / 8.0, 1.0, 3.0);
            float s = 0.05F * pop * far;
            pose.scale(-s, -s, s);
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            if (segments) {                                                                 // 7 セグの数字（黒い縁取りの上に色）
                com.mojang.blaze3d.vertex.VertexConsumer vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.textBackgroundSeeThrough());
                org.joml.Matrix4f m = pose.last().pose(), front = new org.joml.Matrix4f(m).translate(0, 0, FRONT);
                float pitch = 8.0F, x0 = -text.length() * pitch / 2.0F + 1.0F;
                for (int i = 0; i < text.length(); i++) Segments.digit(vc, m, x0 + i * pitch, -1.0F, 6.0F, 10.0F, 1.6F, text.charAt(i) - '0', alpha << 24, 1.0F);
                for (int i = 0; i < text.length(); i++) Segments.digit(vc, front, x0 + i * pitch, -1.0F, 6.0F, 10.0F, 1.6F, text.charAt(i) - '0', (alpha << 24) | color, 0.0F);
                buffers.endBatch();
                return;
            }
            float w = font.width(text) / 2F;
            int argb = (alpha << 24) | (color & 0xFFFFFF);
            int outline = (alpha << 24);
            // 縁取り（黒）を斜めも含めた 8 方向にずらして描いてから、本体（明るい背景でも字が埋もれないように）
            for (int[] d : OUTLINE) {
                font.drawInBatch(text, -w + d[0], d[1], outline, false, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            }
            font.drawInBatch(text, -w, 0, argb, false, new org.joml.Matrix4f(pose.last().pose()).translate(0, 0, FRONT), buffers, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            buffers.endBatch();
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.CUSTOM;
        }
    }
}
