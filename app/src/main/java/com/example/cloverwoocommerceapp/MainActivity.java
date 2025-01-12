package com.example.cloverwoocommerceapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.List;
import java.util.Optional;

public class MainActivity extends AppCompatActivity {

    // WooCommerce API constants,
    //TODO move these to some sort of config, they shouldn't be hardcoded. fuck if i know where yet though
    private static final String wooCommerceURL = "https://dicey.biz/wp-json/wc/v3/";
    private static final String CONSUMER_KEY = "ck_fd49704c7f0abb0d51d8f410fc6aa5a3d0ca10e9";
    private static final String CONSUMER_SECRET = "cs_c15cb676dc137fd0a2d30b8b711f7ff5107e31cb";

    // UI Elements
    private EditText phoneNumberInput, amountInput;
    private Button fetchCustomerButton, addCreditButton, removeCreditButton;
    private TextView currentBalanceView;
    private WooCommerceApi wooCommerceApi;
    private Customer customerCTX;
    private String currentBalance;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        phoneNumberInput = findViewById(R.id.phone_number_input);
        amountInput = findViewById(R.id.amount_edit_text);
        fetchCustomerButton = findViewById(R.id.search_button);
        addCreditButton = findViewById(R.id.add_button);
        removeCreditButton = findViewById(R.id.subtract_button);
        currentBalanceView = findViewById(R.id.result_text_view);

        // Initialize Retrofit for WooCommerce API
        initWooCommerceApi();

        // UI listener functionality
        fetchCustomerButton.setOnClickListener(view -> fetchCustomerByPhoneNumber());
        addCreditButton.setOnClickListener(view -> updateStoreCredit("add"));
        removeCreditButton.setOnClickListener(view -> updateStoreCredit("remove"));
    }
    //before startup, moving loggers to top level possible?
    private void initWooCommerceApi() {
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

        wooCommerceApi = retrofit.create(WooCommerceApi.class);
    }

    private void fetchCustomerByPhoneNumber() {
        String phoneNumber = phoneNumberInput.getText().toString();
        if (phoneNumber.isEmpty()) {
            showToast("Please enter a phone number");
            return;
        }

        Call<List<Customer>> call = wooCommerceApi.getCustomers();
        call.enqueue(new Callback<List<Customer>>() {
            @Override
            public void onResponse(Call<List<Customer>> call, retrofit2.Response<List<Customer>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {

                    Optional<Customer> customer = response.body().stream().filter(c -> c.getBilling().getPhone().equals(phoneNumber)).findFirst();
                    if(customer.isPresent()){
                        customerCTX = customer.get();
                        currentBalance = customer.get().getStoreCreditBalance();
                        currentBalanceView.setText(customer.get().getFirstName()+" "+ customer.get().getLastName() +" Balance: " + (currentBalance != null ? currentBalance : "0"));
                    }else{
                        showToast("Customer not found");
                    }
                } else {
                    showToast("the response was empty");
                }
            }
            @Override
            public void onFailure(Call<List<Customer>> call, Throwable t) {
                showToast("Failed to reach WooCommerce: " + t.getMessage());
            }
        });
    }

    private void updateStoreCredit(String type) {
        if (customerCTX == null) {
            showToast("No customer selected");
            return;
        }

        String amount = amountInput.getText().toString();
        if (amount.isEmpty()) {
            showToast("Please enter an amount");
            return;
        }

        Transaction transaction = new Transaction(amount, type, "Store credit adjustment", String.valueOf(customerCTX.getId()));
        Call<Transaction> call = wooCommerceApi.insertNewTransaction(transaction);
        call.enqueue(new Callback<Transaction>() {
            @Override
            public void onResponse(Call<Transaction> call, retrofit2.Response<Transaction> response) {
                if (response.isSuccessful()) {
                    showToast("Store credit " + type + "ed successfully");
                    fetchCustomerByPhoneNumber(); // Refresh customer data
                } else {
                    showToast("Failed to " + type + " store credit");
                }
            }

            @Override
            public void onFailure(Call<Transaction> call, Throwable t) {
                showToast("Error: " + t.getMessage());
            }
        });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}