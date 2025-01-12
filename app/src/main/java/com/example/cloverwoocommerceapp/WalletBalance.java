package com.example.cloverwoocommerceapp;

import java.math.BigDecimal;

public class WalletBalance {
    //only implementing lower level wallet balance endpoint, wallet endpoint is not useful with GET, lots of pointless data
    private String balance;

    public String getBalance() {
        return balance;
    }

    public BigDecimal getBalanceAsBigDecimal(){
        return new BigDecimal(balance);
    }
}
