package com.example.orderpayment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderPaymentLifecycleApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderPaymentLifecycleApplication.class, args);
    }
}
