package ru.domvporyadke;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DomVPoryadkeApplication {
    public static void main(String[] args) {
        SpringApplication.run(DomVPoryadkeApplication.class, args);
    }
}