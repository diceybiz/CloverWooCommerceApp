package com.example.cloverwoocommerceapp;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Customer {
    private int id;

    @SerializedName("first_name")
    private String firstName;

    @SerializedName("last_name")
    private String lastName;

    @SerializedName("meta_data")
    private List<MetaData> metaData;

    @SerializedName("billing")
    private Billing billing;

    public int getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Billing getBilling(){return billing;}
    public String getStoreCreditBalance() {
        if (metaData != null) {
            for (MetaData meta : metaData) {
                if ("_current_woo_wallet_balance".equals(meta.getKey())) {
                    return meta.getValue();
                }
            }
        }
        return null;
    }
}