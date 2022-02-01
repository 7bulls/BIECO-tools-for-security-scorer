package com.example.mocktool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class MockToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockToolApplication.class, args);
    }

}
