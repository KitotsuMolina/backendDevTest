package com.nunegal.similar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ProductsProperties.class)
public class SimilarProductsApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimilarProductsApplication.class, args);
    }
}
