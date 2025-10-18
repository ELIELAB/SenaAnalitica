package com.example.SenaAnalitica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// import org.springframework.scheduling.annotation.EnableScheduling; // Linha removida

@SpringBootApplication
// A anotação @EnableScheduling foi removida, 
// pois a sincronização agora é por ApplicationReadyEvent (no SincronizacaoService)
public class SenaAnaliticaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SenaAnaliticaApplication.class, args);
    }
}
