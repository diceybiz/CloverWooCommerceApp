package com.example.cloverwoocommerceapp;

public class CustomerCTX{

    private Customer customer;

    private WalletBalance walletBalance;


    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public WalletBalance getWalletBalance() {
        return walletBalance;
    }

    public void setWalletBalance(WalletBalance walletBalance) {
        this.walletBalance = walletBalance;
    }

}
