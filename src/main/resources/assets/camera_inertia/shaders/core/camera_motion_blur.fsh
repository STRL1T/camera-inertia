#version 150

uniform sampler2D DiffuseSampler;

uniform float Intensity;
uniform float Direction;

uniform vec2 InSize;
uniform vec2 OutSize;

uniform float SideStart;
uniform float SideEnd;
uniform float EdgePower;
uniform float BlurPixels;
uniform float ChromaPixels;

in vec2 texCoord;

out vec4 fragColor;

vec4 safeSample(vec2 uv) {
    uv = clamp(uv, vec2(0.001, 0.001), vec2(0.999, 0.999));
    return texture(DiffuseSampler, uv);
}

float getSelectedSideMask(vec2 uv, float side) {
    float sideSign = sign(side);

    if (abs(sideSign) < 0.001) {
        return 0.0;
    }

    float sideValue = 0.0;

    if (sideSign < 0.0) {
        sideValue = clamp(1.0 - uv.x * 2.0, 0.0, 1.0);
    } else {
        sideValue = clamp(uv.x * 2.0 - 1.0, 0.0, 1.0);
    }

    float startValue = clamp(SideStart, 0.0, 1.0);
    float endValue = clamp(SideEnd, startValue + 0.001, 1.0);
    float powerValue = max(EdgePower, 0.001);

    float mask = smoothstep(startValue, endValue, sideValue);
    return pow(mask, powerValue);
}

vec4 motionBlurSample(vec2 uv, vec2 stepDir) {
    vec4 color = vec4(0.0);

    color += safeSample(uv)                  * 0.20;
    color += safeSample(uv - stepDir * 0.12) * 0.17;
    color += safeSample(uv - stepDir * 0.25) * 0.15;
    color += safeSample(uv - stepDir * 0.40) * 0.13;
    color += safeSample(uv - stepDir * 0.58) * 0.11;
    color += safeSample(uv - stepDir * 0.78) * 0.09;
    color += safeSample(uv - stepDir * 1.00) * 0.07;
    color += safeSample(uv - stepDir * 1.24) * 0.05;
    color += safeSample(uv - stepDir * 1.50) * 0.03;

    return color;
}

void main() {
    float intensity = clamp(Intensity, 0.0, 1.0);

    vec4 original = safeSample(texCoord);

    if (intensity <= 0.0001) {
        fragColor = original;
        return;
    }

    float sideSign = sign(Direction);

    if (abs(sideSign) < 0.001) {
        fragColor = original;
        return;
    }

    float screenWidth = max(max(InSize.x, OutSize.x), 1.0);
    float screenHeight = max(max(InSize.y, OutSize.y), 1.0);

    vec2 pixelSize = vec2(1.0 / screenWidth, 1.0 / screenHeight);

    float sideMask = getSelectedSideMask(texCoord, sideSign);

    if (sideMask <= 0.0001) {
        fragColor = original;
        return;
    }

    float localIntensity = intensity * sideMask;

    float radius = localIntensity * max(BlurPixels, 0.0);
    vec2 blurDir = vec2(sideSign * radius * pixelSize.x, 0.0);

    vec2 chromaOffset = vec2(
        sideSign * max(ChromaPixels, 0.0) * pixelSize.x * localIntensity,
        0.0
    );

    vec4 redBlur = motionBlurSample(texCoord + chromaOffset, blurDir);
    vec4 greenBlur = motionBlurSample(texCoord, blurDir);
    vec4 blueBlur = motionBlurSample(texCoord - chromaOffset, blurDir);

    vec3 processed = vec3(
        redBlur.r,
        greenBlur.g,
        blueBlur.b
    );

    vec3 finalColor = mix(original.rgb, processed, sideMask);

    fragColor = vec4(finalColor, original.a);
}