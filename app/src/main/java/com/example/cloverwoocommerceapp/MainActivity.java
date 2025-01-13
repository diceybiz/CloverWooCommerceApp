package com.example.cloverwoocommerceapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MainActivity extends AppCompatActivity {

    // WooCommerce API constants,
    //TODO move these to some sort of config, they shouldn't be hardcoded. fuck if i know where yet though
    private static final String wooCommerceURL = "https://dicey.biz/wp-json/";
    private static final String CONSUMER_KEY = "ck_fd49704c7f0abb0d51d8f410fc6aa5a3d0ca10e9";
    private static final String CONSUMER_SECRET = "cs_c15cb676dc137fd0a2d30b8b711f7ff5107e31cb";

    // UI Elements
    private EditText amountInput;
    private AutoCompleteTextView emailAutoComplete;
    private Button fetchCustomerButton, addCreditButton, removeCreditButton;
    private TextView currentBalanceView;
    private WooCommerceApi wooCommerceApi;
    private CustomerCTX customerCTX = new CustomerCTX();
    private final List<Customer> tempCustomerList = new ArrayList<>();
    private final List<String> emailList = new ArrayList<>();

    private enum transactionType {
        DEBIT("debit"),
        CREDIT("credit");
        public final String typeValue;

        transactionType(String typeValue) {
            this.typeValue = typeValue;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if ("com.clover.intent.action.REGISTER_TENDER".equals(intent.getAction())) {
            handleCustomTender(intent);
        }
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        emailAutoComplete = findViewById(R.id.email_autocomplete);
        amountInput = findViewById(R.id.amount_edit_text);
        fetchCustomerButton = findViewById(R.id.search_button);
        addCreditButton = findViewById(R.id.add_button);
        removeCreditButton = findViewById(R.id.subtract_button);
        currentBalanceView = findViewById(R.id.result_text_view);

        // Initialize Retrofit for WooCommerce API
        initWooCommerceApi();
        // UI listener functionality
        fetchCustomerButton.setOnClickListener(view -> fetchCustomerByEmail());
        addCreditButton.setOnClickListener(view -> updateStoreCredit(transactionType.CREDIT));
        removeCreditButton.setOnClickListener(view -> updateStoreCredit(transactionType.DEBIT));
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
        fetchAllCustomers(1, 100);
    }


    private void handleCustomTender(Intent intent) {
        // Get the transaction amount (if any)
        long amount = intent.getLongExtra("clover.intent.extra.AMOUNT", 0);

        // Example: Display a Toast message
        Toast.makeText(this, "Custom tender launched. Amount: " + amount, Toast.LENGTH_SHORT).show();

        // Send the result back to Clover Register
        Intent result = new Intent();
        result.putExtra("clover.intent.extra.RESULT_TENDER", "Store Credit Processed");
        setResult(RESULT_OK, result);
        finish(); // Close the activity
    }

    public void setAllButtonsEnabled(boolean enabled) {
        if (enabled) {
            fetchCustomerButton.setEnabled(true);
            addCreditButton.setEnabled(true);
            removeCreditButton.setEnabled(true);
        } else {
            fetchCustomerButton.setEnabled(false);
            addCreditButton.setEnabled(false);
            removeCreditButton.setEnabled(false);
        }
    }

    private void fetchAllCustomers(int page, int perPage) {
        Call<List<Customer>> call = wooCommerceApi.getAllCustomers(page, perPage);
        call.enqueue(new Callback<List<Customer>>() {
            @Override
            public void onResponse(Call<List<Customer>> call, Response<List<Customer>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    tempCustomerList.addAll(response.body());
                }
                int totalPages = Integer.parseInt(response.headers().get("X-WP-TotalPages") != null ? response.headers().get("X-WP-TotalPages") : "1");
                if (page < totalPages) {
                    new Handler().postDelayed(() -> fetchAllCustomers(page + 1, perPage), 500);
                } else {
                    for (Customer customer : tempCustomerList) {
                        emailList.add(customer.getEmail());
                    }
                    tempCustomerList.clear();
                    setupEmailSearch();
                }
            }

            @Override
            public void onFailure(Call<List<Customer>> call, Throwable t) {
            }
        });
    }

    private void setupEmailSearch() {
        ArrayAdapter<String> emailAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, emailList);
        emailAutoComplete.setAdapter(emailAdapter);
        emailAutoComplete.setThreshold(2);
    }

    private void fetchCustomerByEmail() {
        customerCTX = new CustomerCTX();
        setAllButtonsEnabled(false);
        String email = emailAutoComplete.getText().toString();
        if (email.isEmpty()) {
            showToast("Please enter a email");
            setAllButtonsEnabled(true);
            return;
        }
        Call<List<Customer>> call = wooCommerceApi.getCustomerByEmail(email);
        call.enqueue(new Callback<List<Customer>>() {
            @Override
            public void onResponse(Call<List<Customer>> call, Response<List<Customer>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Customer customer = response.body().get(0);
                    if (customer != null) {
                        customerCTX.setCustomer(customer);
                        getWalletBalanceData();
                    } else {
                        currentBalanceView.setText("please enter a valid customer phone number");
                        showToast("Customer not found");
                        setAllButtonsEnabled(true);
                    }
                } else {
                    currentBalanceView.setText("HTTP code: " + response.code());
                    showToast("the response was empty");
                    setAllButtonsEnabled(true);
                }
            }

            @Override
            public void onFailure(Call<List<Customer>> call, Throwable t) {
                showToast("Failed to reach WooCommerce: " + t.getMessage());
                setAllButtonsEnabled(true);
            }
        });
    }

    private void getWalletBalanceData() {
        if (customerCTX.getCustomer() == null) {
            showToast("No customer is selected, please try again");
            return;
        }
        Call<WalletBalance> call = wooCommerceApi.getWalletBalance(customerCTX.getCustomer().getEmail());
        call.enqueue(new Callback<WalletBalance>() {
            @Override
            public void onResponse(Call<WalletBalance> call, Response<WalletBalance> response) {
                setAllButtonsEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    customerCTX.setWalletBalance(response.body());
                    currentBalanceView.setText("Customer: " + customerCTX.getCustomer().getFirstName() + " " + customerCTX.getCustomer().getLastName() +
                            " | Balance: " + customerCTX.getWalletBalance().getBalanceAsBigDecimal());
                } else {
                    currentBalanceView.setText("there is an error with the balance for this user");
                    showToast("balance not found");
                    setAllButtonsEnabled(true);
                }
            }

            @Override
            public void onFailure(Call<WalletBalance> call, Throwable t) {
                showToast("Failed to reach WooCommerce: " + t.getMessage());
                setAllButtonsEnabled(true);
            }
        });
    }

    private void updateStoreCredit(transactionType type) {
        setAllButtonsEnabled(false);
        if (customerCTX == null) {
            showToast("there was an issue using this customer, please try to search again");
            setAllButtonsEnabled(true);
            return;
        }

        String amount = amountInput.getText().toString();
        if (amount.isEmpty()) {
            showToast("Please enter an amount");
            setAllButtonsEnabled(true);
            return;
        }

        //TODO this call might be able to be changed to a POST to wallet/balance endpoint.
        // after doing the new balance calc locally, just POST new balance
        Transaction transaction = new Transaction(amount, type.typeValue, "Store credit adjustment", customerCTX.getCustomer().getEmail());
        Call<Transaction> call = wooCommerceApi.insertNewTransaction(transaction);
        call.enqueue(new Callback<Transaction>() {
            @Override
            public void onResponse(Call<Transaction> call, Response<Transaction> response) {
                //TODO this will return 200 even if the call fails due to an incorrect type value which is dumb, possibly caused due to retrofitting.
                // figure out a way to reach the {"response":"error"} that comes later in the response outside the response envelope for better error handling
                if (response.isSuccessful()) {
                    showToast("Store credit " + type.typeValue + "ed successfully");
                    getWalletBalanceData();
                } else {
                    showToast("Failed to " + type + " store credit");
                    setAllButtonsEnabled(true);
                }
            }

            @Override
            public void onFailure(Call<Transaction> call, Throwable t) {
                showToast("Error: " + t.getMessage());
                setAllButtonsEnabled(true);
            }
        });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}