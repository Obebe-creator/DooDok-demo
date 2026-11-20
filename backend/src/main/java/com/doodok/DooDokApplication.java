package com.doodok;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DooDokApplication {

    public static void main(String[] args) {
        SpringApplication.run(DooDokApplication.class, args);
    }
}
