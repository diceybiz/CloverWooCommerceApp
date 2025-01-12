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
    private List<MetaData> metaDatumDAOS;
    private String email;
    private Billing billing;

    public int getId() {
        return id;
    }
    public String getEmail() {
        return email;
    }
    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Billing getBilling(){return billing;}

}