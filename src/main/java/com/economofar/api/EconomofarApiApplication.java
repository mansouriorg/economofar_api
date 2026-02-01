package com.economofar.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EconomofarApiApplication {

    public static void main(String[] args) {
        // Avec spring-dotenv, pas besoin de configuration ici.
        // La librairie détecte le .env toute seule au démarrage.
        SpringApplication.run(EconomofarApiApplication.class, args);
    }
}