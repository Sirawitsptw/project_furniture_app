    #version 300 es
    /*
     * Copyright 2017 Google LLC
     * ... (license header) ...
     */

    precision highp float;
    // uniform sampler2D u_Texture; // ไม่ใช้
    // uniform vec4 u_GridControl; // ไม่ใช้

    // in vec3 v_TexCoordAlpha; // ไม่ต้องใช้ Input นี้แล้วก็ได้
    layout(location = 0) out vec4 o_FragColor;

    void main() {
        // **** กำหนดให้เป็นสีฟ้าทึบๆ ไปเลย ****
        o_FragColor = vec4(0.0, 0.5, 1.0, 0.5); // สีฟ้า (R=0, G=0.5, B=1.0) Alpha=0.5 (กึ่งโปร่งใส)
        // ลองปรับ Alpha เป็น 1.0 ถ้าต้องการสีทึบเลย: vec4(0.0, 0.5, 1.0, 1.0)
    }
    