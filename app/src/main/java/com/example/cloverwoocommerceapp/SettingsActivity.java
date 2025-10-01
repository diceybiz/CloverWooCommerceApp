package com.example.cloverwoocommerceapp;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import android.content.SharedPreferences;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "woocommerce_credentials";
    public static final String KEY_URL = "woocom_url";
    public static final String KEY_CONSUMER_KEY = "consumer_key";
    public static final String KEY_CONSUMER_SECRET = "consumer_secret";

    private EditText editTextUrl, editTextConsumerKey, editTextConsumerSecret;
    private Button buttonSave;
    private SharedPreferences securePrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;

            Log.d("AppVersion", "Version: " + versionName);

            Toast.makeText(this, "App Version: " + versionName, Toast.LENGTH_LONG).show();

            // Or display in a TextView:
            TextView versionView = findViewById(R.id.textViewVersion);
            versionView.setText("Version: " + versionName);

        } catch (Exception e) {
            Log.e("AppVersion", "Failed to get app version", e);
        }


        editTextUrl = findViewById(R.id.editTextUrl);
        editTextConsumerKey = findViewById(R.id.editTextConsumerKey);
        editTextConsumerSecret = findViewById(R.id.editTextConsumerSecret);
        buttonSave = findViewById(R.id.buttonSave);

        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            securePrefs = EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    this,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load secure preferences", Toast.LENGTH_SHORT).show();
        }

        // Pre-fill fields with stored values, if any
        if (securePrefs != null) {
            editTextUrl.setText(securePrefs.getString(KEY_URL, ""));
            editTextConsumerKey.setText(securePrefs.getString(KEY_CONSUMER_KEY, ""));
            editTextConsumerSecret.setText(securePrefs.getString(KEY_CONSUMER_SECRET, ""));
        }

        buttonSave.setOnClickListener(v -> {
            if (securePrefs != null) {
                try{
                    try{
                        WooCommerceApiSingleton.testApi(editTextUrl.getText().toString());
                    }catch (Exception e){
                        Toast.makeText(SettingsActivity.this, "the URL is malformed", Toast.LENGTH_SHORT).show();
                        setResult(Activity.RESULT_CANCELED);
                        finish();
                        return;
                    }

                    securePrefs.edit()
                        .putString(KEY_URL, editTextUrl.getText().toString())
                        .putString(KEY_CONSUMER_KEY, editTextConsumerKey.getText().toString())
                        .putString(KEY_CONSUMER_SECRET, editTextConsumerSecret.getText().toString())
                        .apply();
                        String savedUrl = securePrefs.getString(KEY_URL, "not found");
                        String savedKey = securePrefs.getString(KEY_CONSUMER_KEY, "not found");
                        String savedSecret = securePrefs.getString(KEY_CONSUMER_SECRET, "not found");
                        Log.d("SettingsActivity", "Saved URL: " + savedUrl);
                        Log.d("SettingsActivity", "Saved Consumer Key: " + savedKey);
                        Log.d("SettingsActivity", "Saved Consumer Secret: " + savedSecret);

                        // Reset the API instance so new credentials are used
                        WooCommerceApiSingleton.resetApiInstance();
                        WooCommerceApiSingleton.getApi(this);// this = current Context

                        Toast.makeText(SettingsActivity.this, "Settings saved", Toast.LENGTH_SHORT).show();
                        setResult(Activity.RESULT_OK);

                        finish(); // Optionally finish the activity after saving
                }catch (Exception e){
                    Log.e("SettingsActivity", "Invalid settings input", e);

                    Toast.makeText(SettingsActivity.this, "there was a problem with the settings params, please validate the inputs and save again", Toast.LENGTH_SHORT).show();
                    setResult(Activity.RESULT_CANCELED);
                    finish();
                }
            }
        });
    }
}
