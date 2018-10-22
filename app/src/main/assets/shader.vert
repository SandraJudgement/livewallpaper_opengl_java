#version 300 es

uniform mat4 uModelMatrix;

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec4 inColor;

out vec4 vColor;

void main() {
    gl_Position = uModelMatrix * vec4(inPosition, 1.0);
    vColor = inColor;
}