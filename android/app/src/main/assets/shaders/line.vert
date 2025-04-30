    #version 300 es
    uniform mat4 u_ModelViewProjection;
    layout(location = 0) in vec3 a_Position;

    void main() {
        gl_Position = u_ModelViewProjection * vec4(a_Position, 1.0);
        // **** เพิ่มบรรทัดนี้ กำหนดขนาดจุดให้ใหญ่ๆ ****
        gl_PointSize = 20.0;
    }
    