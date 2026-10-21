package com.firstory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FirstoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(FirstoryApplication.class, args);
    }
}
