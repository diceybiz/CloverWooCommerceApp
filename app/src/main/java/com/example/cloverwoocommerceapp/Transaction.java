package com.example.cloverwoocommerceapp;

public class Transaction {
    private String amount;
    private String type;
    private String note;
    private String email;

    public Transaction(String amount, String type, String note, String email) {
        this.amount = amount;
        this.type = type;
        this.note = note;
        this.email = email;
    }
}