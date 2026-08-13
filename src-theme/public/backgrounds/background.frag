#version 330

// Iris follow-cursor background ported from the Wallpaper Engine
// (Wallpaper Engine workshop 3781425457) iris_movement shader.
// Samples the artwork texture with a small offset driven by the mouse position,
// so the iris appears to follow the cursor.

layout(std140) uniform ThemeBackgroundData {
    float time;
    vec2 mouse;
    vec2 resolution;
};

in vec2 texCoord;

out vec4 fragColor;

uniform sampler2D InSampler;

void main() {
    // Normalize the cursor to [0,1] with the origin at the top-left.
    vec2 cursor = mouse / resolution;

    // Mirror the original shader: scale to [-1,1], then mirror X.
    vec2 adjusted = (cursor - 0.5) * 2.0;
    adjusted.x *= -1.0;
    adjusted = clamp(adjusted, -vec2(1.0), vec2(1.0));

    // Cursor scale (1.93) * scale multiplier (2.3) * 0.001 from the wallpaper.
    vec2 offset = adjusted * vec2(1.93, 1.93) * vec2(2.3, 2.3) * 0.001;

    // The screen quad texture coordinate is bottom-up, flip V to sample the artwork upright.
    fragColor = texture(InSampler, vec2(texCoord.x, 1.0 - texCoord.y) + offset);
}
