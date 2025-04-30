    #version 300 es
    precision mediump float;
    // uniform vec4 u_Color; // ไม่ใช้ Uniform ชั่วคราว
    layout(location = 0) out vec4 o_FragColor;

    void main() {
        // **** บังคับเป็นสีม่วงแดงทึบ ****
        o_FragColor = vec4(1.0, 0.0, 1.0, 1.0); // Magenta, Alpha=1.0
    }
    