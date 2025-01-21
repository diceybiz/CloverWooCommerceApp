package com.example.cloverwoocommerceapp;

import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import com.example.cloverwoocommerceapp.CustomerCTX;
import com.example.cloverwoocommerceapp.Customer;
import com.example.cloverwoocommerceapp.WooCommerceApi;
import com.example.cloverwoocommerceapp.WalletBalance;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton to manage:
 *  - The WooCommerceApi instance
 *  - Customer preloading (for autocomplete)
 */
public class WooCommerceApiSingleton {

    private static final String wooCommerceURL = "https://dicey.biz/wp-json/";
    private static final String CONSUMER_KEY = "ck_fd49704c7f0abb0d51d8f410fc6aa5a3d0ca10e9";
    private static final String CONSUMER_SECRET = "cs_c15cb676dc137fd0a2d30b8b711f7ff5107e31cb";

    private static WooCommerceApi apiInstance;

    // In-memory lists for preloading customers
    private static final List<Customer> tempCustomerList = new ArrayList<>();
    private static final List<String> emailList = new ArrayList<>();


    private WooCommerceApiSingleton() {
        // Private constructor to enforce singleton
    }

    public static WooCommerceApi getApi() {
        if (apiInstance == null) {
            synchronized (WooCommerceApiSingleton.class) {
                if (apiInstance == null) {
                    // Build API
                    HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                    logging.setLevel(HttpLoggingInterceptor.Level.BODY);

                    OkHttpClient client = new OkHttpClient.Builder()
                            .addInterceptor(chain -> {
                                Request original = chain.request();
                                Request request = original.newBuilder()
                                        .url(original.url().newBuilder()
                                                .addQueryParameter("consumer_key", CONSUMER_KEY)
                                                .addQueryParameter("consumer_secret", CONSUMER_SECRET)
                                                .build())
                                        .build();
                                return chain.proceed(request);
                            })
                            .addInterceptor(logging)
                            .build();

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(wooCommerceURL)
                            .addConverterFactory(GsonConverterFactory.create())
                            .client(client)
                            .build();

                    apiInstance = retrofit.create(WooCommerceApi.class);
                }
            }
        }
        return apiInstance;
    }

    /**
     * Initiates preloading of customers, populates an AutoCompleteTextView with email addresses.
     *
     * @param autoCompleteTextView UI element to attach suggestions to
     * @param page                 which page to start loading from
     * @param perPage              number of items per page
     */
    public static void preloadCustomers(AutoCompleteTextView autoCompleteTextView, int page, int perPage) {
        fetchAllCustomers(autoCompleteTextView, page, perPage);
    }

    private static void fetchAllCustomers(AutoCompleteTextView autoCompleteTextView, int page, int perPage) {
        getApi().getAllCustomers(page, perPage).enqueue(new Callback<List<Customer>>() {
            @Override
            public void onResponse(Call<List<Customer>> call, Response<List<Customer>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    tempCustomerList.addAll(response.body());
                }
                int totalPages = 1;
                if (response.headers().get("X-WP-TotalPages") != null) {
                    totalPages = Integer.parseInt(response.headers().get("X-WP-TotalPages"));
                }

                if (page < totalPages) {
                    new Handler().postDelayed(() -> fetchAllCustomers(autoCompleteTextView, page + 1, perPage), 500);
                } else {
                    // We have all customers from all pages now
                    for (Customer customer : tempCustomerList) {
                        emailList.add(customer.getEmail());
                    }
                    tempCustomerList.clear();
                    setupEmailAutocomplete(autoCompleteTextView);
                }
            }

            @Override
            public void onFailure(Call<List<Customer>> call, Throwable t) {
                Toast.makeText(autoCompleteTextView.getContext(),
                        "Failed to fetch customers: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void setupEmailAutocomplete(AutoCompleteTextView autoCompleteTextView) {
        ArrayAdapter<String> emailAdapter = new ArrayAdapter<>(
                autoCompleteTextView.getContext(),
                android.R.layout.simple_dropdown_item_1line,
                emailList
        );
        autoCompleteTextView.setAdapter(emailAdapter);
        autoCompleteTextView.setThreshold(2); // Start suggesting after 2 characters
    }
}