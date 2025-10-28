package com.example.cloverwoocommerceapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.accounts.Account;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.clover.sdk.util.CloverAccount;
import com.clover.sdk.v1.tender.Tender;
import com.clover.sdk.v1.tender.TenderConnector;
import com.clover.sdk.v1.Intents;
import com.example.cloverwoocommerceapp.models.Customer;
import com.example.cloverwoocommerceapp.models.CustomerCTX;
import com.example.cloverwoocommerceapp.models.Transaction;
import com.example.cloverwoocommerceapp.models.WalletBalance;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // ADDED CODE: TAG constant (for logging in createTenderType)
    private static final String TAG = "MainActivity"; // Or use MainActivity.class.getSimpleName()

    // UI Elements
    private EditText amountInput;
    private AutoCompleteTextView emailAutoComplete;
    private Button fetchCustomerButton, addCreditButton, removeCreditButton;
    private TextView currentBalanceView, resultTextView;
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
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d("mainActivity", "creatOptionsMenu");
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d("mainActivity", "optionsItemSelected");
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            settingsActivityResultLauncher.launch(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final ActivityResultLauncher<Intent> settingsActivityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    WooCommerceApiSingleton.preloadCustomers(emailAutoComplete,1, 100);
                    Log.d("mainActivity", "reran fetch autofill list");
                }
            });

    private void checkPresavedCreds(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            SharedPreferences securePrefs = EncryptedSharedPreferences.create(
                    WooCommerceApiSingleton.PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            String url = securePrefs.getString(SettingsActivity.KEY_URL, null);
            String key = securePrefs.getString(SettingsActivity.KEY_CONSUMER_KEY, null);
            String secret = securePrefs.getString(SettingsActivity.KEY_CONSUMER_SECRET, null);

            try{
                WooCommerceApiSingleton.testApi(url);
            }catch (Exception e){
                Log.e(TAG, "bad url " + e.getMessage());
                securePrefs.edit().remove(SettingsActivity.KEY_URL).apply();
            }

        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error checking secure prefs: " + e.getMessage());
        }
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkPresavedCreds(this);

        Intent intent = getIntent();
        Log.d(TAG, "onCreate: Intent action = " + intent.getAction());
        if ("clover.intent.action.PAY".equals(intent.getAction())) {
            handlePaymentIntent(intent);
        } else if ("com.clover.intent.action.REGISTER_TENDER".equals(intent.getAction())) {
            handleCustomTender(intent);
        }
        createTenderType(this);

        setContentView(R.layout.activity_main);

        // Initialize UI elements
        emailAutoComplete = findViewById(R.id.email_autocomplete);
        amountInput = findViewById(R.id.amount_edit_text);
        fetchCustomerButton = findViewById(R.id.search_button);
        addCreditButton = findViewById(R.id.add_button);
        removeCreditButton = findViewById(R.id.subtract_button);
        currentBalanceView = findViewById(R.id.result_text_view);
        resultTextView = findViewById(R.id.resultTextView);

        // Initialize Retrofit for WooCommerce API
        initWooCommerceApi();

        // UI listener functionality
        fetchCustomerButton.setOnClickListener(view -> fetchCustomerByEmail());
        addCreditButton.setOnClickListener(view -> updateStoreCredit(transactionType.CREDIT));
        removeCreditButton.setOnClickListener(view -> updateStoreCredit(transactionType.DEBIT));
        setAddAndSubtractButtonsEnabled(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if ("clover.intent.action.PAY".equals(intent.getAction())) {
            handlePaymentIntent(intent);
        } else if ("com.clover.intent.action.REGISTER_TENDER".equals(intent.getAction())) {
            handleCustomTender(intent);
        }
    }

        private void handlePaymentIntent(Intent intent) {
        long amount = intent.getLongExtra(Intents.EXTRA_AMOUNT, 0);
        String orderId = intent.getStringExtra(Intents.EXTRA_ORDER_ID);
        com.clover.sdk.v3.base.Tender tender = intent.getParcelableExtra(Intents.EXTRA_TENDER);

        if (amount <= 0) { // Check if the amount is invalid
            Log.e(TAG, "Invalid or missing amount in tender response");
            Toast.makeText(this, "Invalid payment amount. Please try again.", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            //finish();
            return;
        }

        Log.d(TAG, "Processing payment: Amount=" + amount + ", OrderId=" + orderId + ", Tender=" + tender);

        // Example: Display a Toast and send back a success result
        Toast.makeText(this, "Processing payment of $" + (amount / 100.0), Toast.LENGTH_SHORT).show();

        // Instead of finishing, we launch EmailInputActivity:
        Intent i = new Intent(this, EmailInputActivity.class);
        i.putExtra(EmailInputActivity.EXTRA_AMOUNT, amount);
        i.putExtra(EmailInputActivity.EXTRA_ORDER_ID, orderId);
        handlePaymentActivityResultLauncher.launch(i);
    }

    private final ActivityResultLauncher<Intent> handlePaymentActivityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Log.d(TAG, "Activity result received");

                Intent data = result.getData();

                if (result.getResultCode() == RESULT_OK && data != null) {
                    long amount = data.getLongExtra(Intents.EXTRA_AMOUNT, -1);
                    String note = data.getStringExtra(Intents.EXTRA_NOTE);
                    String clientId = data.getStringExtra(Intents.EXTRA_CLIENT_ID);

                    Log.d(TAG, "Store Credit Payment Complete. Amount=" + amount
                            + ", note=" + note + ", clientId=" + clientId);

                    setResult(RESULT_OK, data);
                } else {
                    setResult(RESULT_CANCELED);
                }

                finish();
            });
    //before startup, moving loggers to top level possible?
    private void initWooCommerceApi() {
        // Obtain the WooCommerceApi instance using the singleton,
        // which now reads credentials from secure SharedPreferences.
        wooCommerceApi = WooCommerceApiSingleton.getApi(this);
        WooCommerceApiSingleton.preloadCustomers(emailAutoComplete,1, 100);
    }

    private void setupViews(long amount, String orderId, String merchantId) {
        TextView amountText = findViewById(R.id.text_amount);
        amountText.setText(String.valueOf(amount));

        TextView orderIdText = findViewById(R.id.text_orderid);
        orderIdText.setText(orderId);

        TextView merchantIdText = findViewById(R.id.text_merchantid);
        merchantIdText.setText(merchantId);
    }


    private void handleCustomTender(Intent intent) {
        long amount = intent.getLongExtra(Intents.EXTRA_AMOUNT, 0);
        Log.d(TAG, "Passed Amount: " + amount);
        String orderId = intent.getStringExtra(Intents.EXTRA_ORDER_ID);
        String merchantId = intent.getStringExtra(Intents.EXTRA_MERCHANT_ID);

        // Example UI interaction
        setContentView(R.layout.activity_tender);
        setupViews(amount, orderId, merchantId);

        Button approveButton = findViewById(R.id.acceptButton);
        approveButton.setOnClickListener(view -> {
            Intent result = new Intent();
            result.putExtra(Intents.EXTRA_AMOUNT, amount);
            result.putExtra(Intents.EXTRA_CLIENT_ID, UUID.randomUUID().toString().substring(0, 32));
            result.putExtra(Intents.EXTRA_NOTE, "Transaction approved");
            setResult(RESULT_OK, result);
            finish();
        });

        Button declineButton = findViewById(R.id.declineButton);
        declineButton.setOnClickListener(view -> {
            Intent result = new Intent();
            result.putExtra(Intents.EXTRA_DECLINE_REASON, "Transaction declined by user");
            setResult(RESULT_CANCELED, result);
            finish();
        });
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

    public void setAddAndSubtractButtonsEnabled(boolean enabled) {
        if (enabled) {
            addCreditButton.setEnabled(true);
            removeCreditButton.setEnabled(true);
        } else {
            addCreditButton.setEnabled(false);
            removeCreditButton.setEnabled(false);
        }
    }

    public void setSearchButtonEnabled(boolean enabled){
        fetchCustomerButton.setEnabled(enabled);
    }



    private void fetchCustomerByEmail() {
        customerCTX = new CustomerCTX();
        String email = emailAutoComplete.getText().toString();
        setSearchButtonEnabled(false);
        if (email.isEmpty()) {
            showToast("Please enter a email");
            setSearchButtonEnabled(true);
            return;
        }
        Call<List<Customer>> call = WooCommerceApiSingleton.getApi(this).getCustomerByEmail(email);
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
                        setSearchButtonEnabled(true);
                    }
                } else {
                    currentBalanceView.setText("HTTP code: " + response.code());
                    showToast("the response was empty");
                    setSearchButtonEnabled(true);
                }
            }

            @Override
            public void onFailure(Call<List<Customer>> call, Throwable t) {
                showToast("Failed to reach WooCommerce: " + t.getMessage());
                setSearchButtonEnabled(true);
            }

        });
    }

    private void getWalletBalanceData() {
        if (customerCTX.getCustomer() == null) {
            showToast("No customer is selected, please try again");
            return;
        }
        Call<WalletBalance> call = WooCommerceApiSingleton.getApi(this).getWalletBalance(customerCTX.getCustomer().getEmail());

        call.enqueue(new Callback<WalletBalance>() {
            @Override
            public void onResponse(Call<WalletBalance> call, Response<WalletBalance> response) {
                if (response.isSuccessful() && response.body() != null) {
                    customerCTX.setWalletBalance(response.body());
                    currentBalanceView.setText("Customer: " + customerCTX.getCustomer().getFirstName() + " "
                            + customerCTX.getCustomer().getLastName() + " | Balance: "
                            + customerCTX.getWalletBalance().getBalanceAsBigDecimal());
                    setAllButtonsEnabled(true);
                } else {
                    currentBalanceView.setText("there is an error with the balance for this user");
                    showToast("balance not found");
                    setSearchButtonEnabled(true);
                }
            }

            @Override
            public void onFailure(Call<WalletBalance> call, Throwable t) {
                showToast("Failed to reach WooCommerce: " + t.getMessage());
                setSearchButtonEnabled(true);
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

        Transaction transaction = new Transaction(amount, type.typeValue, "Store credit adjustment", customerCTX.getCustomer().getEmail());
        Call<Transaction> call = WooCommerceApiSingleton.getApi(this).insertNewTransaction(transaction);
        call.enqueue(new Callback<Transaction>() {
            @Override
            public void onResponse(Call<Transaction> call, Response<Transaction> response) {
                if (response.isSuccessful()) {
                    showToast("Store credit " + type.typeValue + "ed successfully");
                    getWalletBalanceData();
                    setAllButtonsEnabled(true);
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

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("WooDebug", "MainActivity resumed - resetting Woo API instance");
        WooCommerceApiSingleton.resetApiInstance();
    }

    // ADDED CODE: The createTenderType method
    private void createTenderType(final Context context) {
        Log.d(TAG, "createTenderType() called");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            TenderConnector tenderConnector = null;
            Tender tender = null;
            Exception error = null;

            Account cloverAcc = CloverAccount.getAccount(context);
            Log.d("TENDER", "Clover Account = " + cloverAcc);

            if (cloverAcc == null) {
                error = new Exception("Clover account not found");
            } else {
                tenderConnector = new TenderConnector(context, cloverAcc, null);
                tenderConnector.connect();

                try {
                    Log.d("TENDER", "About to call checkAndCreateTender...");
                    tender = tenderConnector.checkAndCreateTender(
                            "Dicey Credits",
                            context.getPackageName(),
                            true,
                            false
                    );
                } catch (Exception e) {
                    error = e;
                }
            }

            // Switch back to main thread
            Tender finalTender = tender;
            TenderConnector finalConnector = tenderConnector;
            Exception finalError = error;

            handler.post(() -> {
                if (finalConnector != null) {
                    finalConnector.disconnect();
                    Log.d("TENDER", "TenderConnector disconnected");
                }

                if (finalError != null) {
                    Log.e("TENDER", "Error: ", finalError);
                    Toast.makeText(context, "Error: " + finalError.getMessage(), Toast.LENGTH_SHORT).show();
                } else if (finalTender != null) {
                    Log.d("TENDER", "Tender verified/created: " + finalTender.getId());
                    Toast.makeText(context, "Tender configured: " + finalTender.getLabel(), Toast.LENGTH_SHORT).show();
                } else {
                    Log.e("TENDER", "Unknown error occurred");
                }
            });
        });
    }
}
