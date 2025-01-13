package com.example.cloverwoocommerceapp;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import java.util.List;

public interface WooCommerceApi {

    @GET("wc/v3/customers")
    Call<List<Customer>> getCustomerByEmail(@Query("email") String email);

    @GET("wc/v3/customers")
    Call<List<Customer>> getAllCustomers(@Query("page") int page, @Query("per_page") int perPage);

    @GET("wc/v3/wallet/balance")
    Call<WalletBalance> getWalletBalance(@Query("email") String email);

    @POST("wc/v3/wallet/balance")
    Call<WalletBalance> settWalletBalance();

    @POST("wc/v3/wallet")
    Call<Transaction> insertNewTransaction(@Body Transaction transaction);
}