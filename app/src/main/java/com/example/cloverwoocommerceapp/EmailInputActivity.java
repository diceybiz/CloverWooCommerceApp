package com.example.cloverwoocommerceapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.clover.sdk.v1.Intents;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.example.cloverwoocommerceapp.models.Customer;
import com.example.cloverwoocommerceapp.models.CustomerCTX;
import com.example.cloverwoocommerceapp.models.Transaction;
import com.example.cloverwoocommerceapp.models.WalletBalance;

/**
 * Dedicated activity for collecting an email
 * and verifying store credit before finalizing a payment.
 */
public class EmailInputActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT = "EXTRA_AMOUNT";
    public static final String EXTRA_ORDER_ID = "EXTRA_ORDER_ID";

    private static final String TAG = "EmailInputActivity";

    private TextView transactionAmountDisplay;
    private AutoCompleteTextView emailInput;
    private TextView balanceDisplay;
    private Button fetchBalanceButton;
    private Button confirmButton;

    private long transactionAmount;
    private String orderId;

    private CustomerCTX customerCTX = new CustomerCTX();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_input);

        // Retrieve extras
        transactionAmount = getIntent().getLongExtra(EXTRA_AMOUNT, 0);
        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);

        // Initialize UI
        transactionAmountDisplay = findViewById(R.id.transaction_amount_display);
        emailInput = findViewById(R.id.email_input);
        balanceDisplay = findViewById(R.id.balance_display);
        fetchBalanceButton = findViewById(R.id.fetch_balance_button);
        confirmButton = findViewById(R.id.confirm_button);

        // Display the transaction amount in dollars and cents
        BigDecimal amountInDollars = BigDecimal.valueOf(transactionAmount)
                .divide(BigDecimal.valueOf(100));
        transactionAmountDisplay.setText("Transaction Amount: $" + amountInDollars);

        // Initially, don't let them confirm
        confirmButton.setEnabled(false);

        // Example: If you have a preload method in your singleton:
        WooCommerceApiSingleton.preloadCustomers(emailInput, 1, 100);

        // Buttons
        fetchBalanceButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter an email", Toast.LENGTH_SHORT).show();
                return;
            }
            fetchCustomerByEmail(email);
        });

        confirmButton.setOnClickListener(v -> finalizePayment());
    }

    private void fetchCustomerByEmail(String email) {
        WooCommerceApi api = WooCommerceApiSingleton.getApi(this);

        Call<List<Customer>> call = api.getCustomerByEmail(email);
        call.enqueue(new Callback<List<Customer>>() {
            @Override
            public void onResponse(Call<List<Customer>> call, Response<List<Customer>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Customer customer = response.body().get(0);
                    if (customer != null) {
                        customerCTX.setCustomer(customer);
                        fetchWalletBalance(customer.getEmail());
                    } else {
                        balanceDisplay.setText("No matching customer found.");
                        confirmButton.setEnabled(false);
                    }
                } else {
                    balanceDisplay.setText("HTTP code: " + response.code() + " (or no customer found)");
                    confirmButton.setEnabled(false);
                }
            }

            @Override
            public void onFailure(Call<List<Customer>> call, Throwable t) {
                balanceDisplay.setText("Failed: " + t.getMessage());
                confirmButton.setEnabled(false);
            }
        });
    }

    private void fetchWalletBalance(String email) {
        WooCommerceApi api = WooCommerceApiSingleton.getApi(this);

        api.getWalletBalance(email).enqueue(new Callback<WalletBalance>() {
            @Override
            public void onResponse(Call<WalletBalance> call, Response<WalletBalance> response) {
                if (response.isSuccessful() && response.body() != null) {
                    WalletBalance walletBalance = response.body();
                    customerCTX.setWalletBalance(walletBalance);

                    BigDecimal currentBal = walletBalance.getBalanceAsBigDecimal();
                    balanceDisplay.setText("Balance: $" + currentBal);

                    // 1) Convert transactionAmount to BigDecimal
                    BigDecimal needed = BigDecimal.valueOf(transactionAmount)
                            .divide(BigDecimal.valueOf(100));

                    // 2) If currentBal >= needed, enable confirm
                    if (currentBal.compareTo(needed) >= 0) {
                        confirmButton.setEnabled(true);
                    } else {
                        confirmButton.setEnabled(false);
                    }
                } else {
                    balanceDisplay.setText("No balance found for user");
                    confirmButton.setEnabled(false);
                }
            }

            @Override
            public void onFailure(Call<WalletBalance> call, Throwable t) {
                balanceDisplay.setText("Error: " + t.getMessage());
                confirmButton.setEnabled(false);
            }
        });
    }

    private void finalizePayment() {
        if (customerCTX.getWalletBalance() == null) {
            Toast.makeText(this, "No balance loaded yet!", Toast.LENGTH_SHORT).show();
            return;
        }

        java.math.BigDecimal currentBal = customerCTX.getWalletBalance().getBalanceAsBigDecimal();
        java.math.BigDecimal needed = new java.math.BigDecimal(transactionAmount).divide(new java.math.BigDecimal("100"));

        // Extra check in case user tries to confirm too soon
        if (currentBal.compareTo(needed) < 0) {
            Toast.makeText(this, "Insufficient store credit!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Actually remove/debit the store credit from the server
        removeStoreCreditFromServer(
                customerCTX.getCustomer().getEmail(),
                needed,
                new Callback<Transaction>() {
                    @Override
                    public void onResponse(Call<Transaction> call, Response<Transaction> response) {
                        if (response.isSuccessful()) {
                            // Payment is good! Return success to Clover
                            Intent data = new Intent();
                            data.putExtra(Intents.EXTRA_AMOUNT, transactionAmount);
                            data.putExtra(Intents.EXTRA_CLIENT_ID,
                                    UUID.randomUUID().toString().replace("-", "").substring(0, 32));
                            data.putExtra(Intents.EXTRA_NOTE, "Store credit payment complete");

                            setResult(RESULT_OK, data);
                            finish();

                        } else {
                            // Show error
                            Toast.makeText(EmailInputActivity.this,
                                    "Failed to remove store credit (HTTP " + response.code() + ")",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Transaction> call, Throwable t) {
                        Toast.makeText(EmailInputActivity.this,
                                "Error removing store credit: " + t.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }
    private void removeStoreCreditFromServer(String email, java.math.BigDecimal amount, Callback<Transaction> callback) {
        // Reuse your "wooCommerceApi.insertNewTransaction(...)" with transactionType=DEBIT
        // or a dedicated "removeCredit" call. Example:

        Transaction transaction = new Transaction(
                amount.toPlainString(),
                "debit",
                "In-Store payment for order:" + orderId,
                email
        );

        Call<Transaction> call = WooCommerceApiSingleton.getApi(this).insertNewTransaction(transaction);
        call.enqueue(callback);
    }

}