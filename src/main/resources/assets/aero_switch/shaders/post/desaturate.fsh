#version 330

uniform sampler2D InSampler;

layout(std140) uniform DesaturateConfig {
    float DesaturateAmount;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    float luma = dot(color.rgb, vec3(0.3, 0.59, 0.11));
    vec3 dimmed = mix(color.rgb, vec3(luma), DesaturateAmount);
    fragColor = vec4(dimmed, color.a);
}
