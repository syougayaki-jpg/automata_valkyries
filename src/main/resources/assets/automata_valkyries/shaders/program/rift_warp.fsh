#version 150

// 歪曲破界砲の着弾地点の景色の歪み（2026-10-10）: 画面の Center（0〜1）のまわり、半径 Radius（画面の高さに対する割合）を、
// 渦を巻くようにねじり、外へ広がる衝撃の輪で波打たせ、三原色をずらす。Strength（0〜1）で強さ、Phase（0〜1）で時間の進み

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform vec2 InSize;
uniform vec2 Center;
uniform float Radius;
uniform float Strength;
uniform float Phase;

out vec4 fragColor;

void main() {
    float aspect = InSize.x / InSize.y;
    vec2 d = texCoord - Center;
    d.x *= aspect;
    float r = length(d);
    float inside = 1.0 - smoothstep(0.0, Radius * 1.25, r);
    // 渦: 真ん中ほど強くねじる
    float ang = Strength * inside * inside * 2.4;
    float s = sin(ang), c = cos(ang);
    vec2 nd = vec2(c * d.x - s * d.y, s * d.x + c * d.y);
    // 真ん中へ吸い込むように縮める
    nd *= 1.0 - 0.35 * Strength * inside;
    // 外へ広がる衝撃の輪
    float ring = Radius * (0.15 + 1.1 * Phase);
    float band = exp(-pow((r - ring) / (Radius * 0.12), 2.0));
    vec2 dirv = r > 1.0e-4 ? d / r : vec2(0.0);
    nd += dirv * band * 0.035 * Strength * sin((r - ring) * 90.0);
    vec2 uv = Center + vec2(nd.x / aspect, nd.y);
    // 三原色のずれ（歪みの強い所ほど）
    vec2 ca = dirv * (0.004 + 0.01 * band) * Strength * max(inside, band);
    ca.x /= aspect;
    float red = texture(DiffuseSampler, uv + ca).r;
    float green = texture(DiffuseSampler, uv).g;
    float blue = texture(DiffuseSampler, uv - ca).b;
    // 歪みの縁がうっすら紫に光る
    vec3 col = vec3(red, green, blue) + vec3(0.35, 0.1, 0.55) * band * Strength * 0.35;
    fragColor = vec4(col, 1.0);
}
