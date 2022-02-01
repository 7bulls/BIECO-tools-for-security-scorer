package com.example.mocktool.beans;

import com.example.mocktool.services.AppRegistryService;
import com.google.gson.Gson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class BeansConfigurator {
    @Bean
    @Scope("singleton")
    public AppRegistryService commonRegistry() {
        return new AppRegistryService();
    }

    @Bean
    public Gson gson() {
        return new Gson();
    }
}
