package com.example.SenaAnalitica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling 
public class SenaAnaliticaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SenaAnaliticaApplication.class, args);
    }
}