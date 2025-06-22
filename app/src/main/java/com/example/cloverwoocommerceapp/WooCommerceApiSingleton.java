package com.example.cloverwoocommerceapp;

import android.os.Handler;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import java.io.IOException;
import java.security.GeneralSecurityException;
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

    // Shared Preferences keys and file name (should match those in SettingsActivity)
    public static final String PREFS_NAME = "woocommerce_credentials";
    public static final String KEY_URL = "woocom_url";
    public static final String KEY_CONSUMER_KEY = "consumer_key";
    public static final String KEY_CONSUMER_SECRET = "consumer_secret";

// Default fallback values if none are stored
    private static final String DEFAULT_WOOCOMMERCE_URL = "https://yoursite.com/wp-json/";
    private static final String DEFAULT_CONSUMER_KEY = "";
    private static final String DEFAULT_CONSUMER_SECRET = "";



    private static WooCommerceApi apiInstance;

    // In-memory lists for preloading customers
    private static final List<Customer> tempCustomerList = new ArrayList<>();
    private static final List<String> emailList = new ArrayList<>();


    private WooCommerceApiSingleton() {
        // Private constructor to enforce singleton
    }

    public static WooCommerceApi getApi(Context context) {
        if (apiInstance == null) {
            synchronized (WooCommerceApiSingleton.class) {
                if (apiInstance == null) {
                    // Load the credentials from secure SharedPreferences
                    SharedPreferences securePrefs = null;
                    try {
                        String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                        securePrefs = EncryptedSharedPreferences.create(
                                PREFS_NAME,
                                masterKeyAlias,
                                context,
                                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        );
                    } catch (GeneralSecurityException | IOException e) {
                        e.printStackTrace();
                    }

                    String wooCommerceURL = securePrefs != null
                            ? securePrefs.getString(KEY_URL, DEFAULT_WOOCOMMERCE_URL)
                            : DEFAULT_WOOCOMMERCE_URL;
                    String CONSUMER_KEY = securePrefs != null
                            ? securePrefs.getString(KEY_CONSUMER_KEY, DEFAULT_CONSUMER_KEY)
                            : DEFAULT_CONSUMER_KEY;
                    String CONSUMER_SECRET = securePrefs != null
                            ? securePrefs.getString(KEY_CONSUMER_SECRET, DEFAULT_CONSUMER_SECRET)
                            : DEFAULT_CONSUMER_SECRET;

                    Log.d("WooCommerceApiSingleton", "Using WooCommerce URL: " + wooCommerceURL);
                    Log.d("WooCommerceApiSingleton", "Using Consumer Key: " + CONSUMER_KEY);
                    Log.d("WooCommerceApiSingleton", "Using Consumer Secret: " + CONSUMER_SECRET);



                    // Build the API using Retrofit
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
                                try {
                                    okhttp3.Response response = chain.proceed(request);
                                    Log.i("WooCommerceApi", "<-- " + response.code() + " " + request.url());
                                    return response;
                                } catch (IOException e) {
                                    Log.e("WooCommerceApi", "Network error: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                                    throw e;
                                }
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

    public static void resetApiInstance() {
        apiInstance = null;
        Log.d("WooCommerceApiSingleton", "API instance reset. Will use new credentials on next request.");
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
        getApi(autoCompleteTextView.getContext()).getAllCustomers(page, perPage).enqueue(new Callback<List<Customer>>() {
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