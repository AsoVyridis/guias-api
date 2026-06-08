package com.example.demo;

import lombok.Data;

@Data // Esto genera los getters, setters y constructores gracias a Lombok
public class Guia {
    private String id;
    private String transportista;
    private String fecha; // Formato sugerido: AAAAMMDD (ej: 20260608)
    private String contenido;
}
