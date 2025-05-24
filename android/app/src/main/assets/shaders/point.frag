#version 300 es
precision mediump float;

uniform vec4 u_Color; // Color of the point

// in vec4 v_Color; // ไม่ต้องถ้าใช้ u_Color โดยตรง

layout(location = 0) out vec4 o_FragColor;

void main() {
    // Check if the fragment is within the circular point
    // vec2 coord = gl_PointCoord - vec2(0.5);
    // if (dot(coord, coord) > 0.25) { // 0.25 for a circle
    //     discard; // Make it a circle instead of a square
    // }
    o_FragColor = u_Color; // Use the uniform color
}