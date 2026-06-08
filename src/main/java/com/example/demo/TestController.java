package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/hola")
    public String holaMundo() {
        return "¡El servidor Spring Boot está funcionando localmente!";
    }
}
