package com.example.cloverwoocommerceapp;

import com.google.gson.annotations.SerializedName;

public class Billing {

    //ignoring other billing fields for now
    @SerializedName("phone")
    String phone;

    public String getPhone() {
        return phone;
    }
}
