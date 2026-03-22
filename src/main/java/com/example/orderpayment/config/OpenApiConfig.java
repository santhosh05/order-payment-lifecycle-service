package com.example.orderpayment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI orderPaymentOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Order Payment Lifecycle Service API")
                                .version("v1")
                                .description("APIs for order payment authorization, capture, refund, and reconciliation."));
    }
}
