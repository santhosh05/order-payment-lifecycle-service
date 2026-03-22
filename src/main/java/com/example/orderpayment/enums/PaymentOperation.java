package com.example.orderpayment.enums;

public enum PaymentOperation {
    AUTHORIZE("authorize"),
    CAPTURE("capture"),
    REFUND("refund");

    private final String value;

    PaymentOperation(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
