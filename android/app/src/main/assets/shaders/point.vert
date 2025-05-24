#version 300 es
uniform mat4 u_ModelViewProjection; // MVP matrix for the point
uniform vec4 u_Color;               // Color of the point
uniform float u_PointSize;           // Size of the point

layout(location = 0) in vec3 a_Position; // Vertex position (center of the point)

// varying vec4 v_Color; // ไม่ต้องส่งสีไป fragment ถ้าใช้สีเดียว

void main() {
    gl_Position = u_ModelViewProjection * vec4(a_Position, 1.0);
    gl_PointSize = u_PointSize; // Set the size of the point
    // v_Color = u_Color; // ไม่ต้องถ้า Fragment ใช้ u_Color โดยตรง
}