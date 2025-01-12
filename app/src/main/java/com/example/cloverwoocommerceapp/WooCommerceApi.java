package com.example.cloverwoocommerceapp;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import java.util.List;

public interface WooCommerceApi {

    @GET("customers")
    Call<List<Customer>> getCustomers();

    @POST("wallet")
    Call<Transaction> insertNewTransaction(@Body Transaction transaction);
}