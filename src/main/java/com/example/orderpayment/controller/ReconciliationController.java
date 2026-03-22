package com.example.orderpayment.controller;

import com.example.orderpayment.dto.ReconciliationResponse;
import com.example.orderpayment.service.ReconciliationService;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliation")
@Validated
public class ReconciliationController {
    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/daily")
    public ReconciliationResponse getDailyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @NotBlank String restaurantId) {
        return reconciliationService.getDailyReconciliation(date, restaurantId);
    }
}
