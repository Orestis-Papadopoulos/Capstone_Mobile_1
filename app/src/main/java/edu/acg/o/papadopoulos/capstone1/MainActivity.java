package edu.acg.o.papadopoulos.capstone1;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private NfcAdapter nfcAdapter;
    private WifiManager wifiManager;
    private Button btn_register_sign_in;
    private final String server_url = "http://192.168.2.59/capstone/register_user.php";
    private String qr_code_data = "", uuid = "", proximity_card_id = "", session_id = "";
    final String uuid_file = "user_uuid";
    boolean uuid_file_exists = false;
    private AlertDialog scanCardDialog; // might need to show/hide multiple times

    // use PendingIntent, and ForegroundDispatch to prevent Activity from reopening if card is scanned while app is open

    // the app can open auto if card is scanned
    // if the app is opened manually, wait for card to be scanned with this Intent
    private PendingIntent cardWasScanned;
    private Tag tag;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_register_sign_in = findViewById(R.id.register_sign_in);
        nfcAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // 1: check if the phone has NFC at all
        if (nfcAdapter == null) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.nfc_not_supported)
                    .setMessage(R.string.this_application_cannot_function_without_NFC)
                    .setPositiveButton(R.string.ok, (dialog, id) -> {
                        finishAndRemoveTask(); // closes app
                    })
                    .show();
        }

        // 2: prompt user to enable NFC
        if (!nfcAdapter.isEnabled()) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.nfc_disabled)
                    .setMessage(R.string.this_application_requires_NFC_do_you_want_to_turn_it_on)
                    .setPositiveButton(R.string.yes, (dialog, id) -> {
                        startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                    })
                    .setNegativeButton(R.string.no, (dialog, id) -> {
                    })
                    .show();
        }

        // 3: onResume() called

        // 4: check if app-specific file exists; if it does, the user has been registered
        String[] app_specific_files = getApplicationContext().fileList();
        uuid_file_exists = Arrays.asList(app_specific_files).contains(uuid_file);

        // 5: if the user is registered, change btn text
        if (uuid_file_exists) {
            supportInvalidateOptionsMenu();
            btn_register_sign_in.setText(R.string.sign_in);
        }

        cardWasScanned = PendingIntent.getActivity(this,0,
                new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),0);

        scanCardDialog = new AlertDialog.Builder(this)
                .setTitle("Scan Your Card")
                .setMessage("Put your card on the back of your phone")
                .setNegativeButton("Cancel", (dialog, id) -> {})
                .create();

        // try block executes successfully if app opens with scan
        try {
            tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
            proximity_card_id = tag.getId().toString();
            notification("Card scan caused app to open");
        } catch (Exception e) {
            e.printStackTrace();
        }

        btn_register_sign_in.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openQRCodeScanner();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 3: prompt user to enable WiFi
        // first NFC is enabled and when the user returns to MainActivity WiFi is checked
        if (!wifiManager.isWifiEnabled() && nfcAdapter.isEnabled()) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.wifi_disabled)
                    .setMessage(R.string.this_application_requires_an_Internet_connection_do_you_want_to_turn_WiFi_on)
                    .setPositiveButton(R.string.yes, (dialog, id) -> {
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    })
                    .setNegativeButton(R.string.no, (dialog, id) -> {
                    })
                    .show();
        }

        // enable the foreground dispatch to wait for card scan
        nfcAdapter.enableForegroundDispatch(this, cardWasScanned,null,null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        nfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // for opening the app when an NFC tag is detected
        setIntent(intent);

        // called when card is scanned while app is open
        try {
            tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
            proximity_card_id = tag.getId().toString();
            if (!uuid.equals("")) updateCardInDatabase();
            scanCardDialog.cancel();
            notification("Card scanned while app open");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.settings_option:
                notification("No Settings yet");
            case R.id.about_option:
                notification("No About yet");
            case R.id.delete_account_option:
                if (uuid_file_exists) {
                    File file = new File(this.getFilesDir(), uuid_file);
                    file.delete();
                    uuid_file_exists = false;
                    btn_register_sign_in.setText(R.string.register);
                    // update options menu >> hide "Delete account" option
                    supportInvalidateOptionsMenu();
                    notification("uuid deleted from app-specific storage");
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // for changes during runtime
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.delete_account_option).setVisible(uuid_file_exists);
        return false;
    }

    public void notification(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private void openQRCodeScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Press volume up to turn flash on\n\n\n\n\n\n\n\n");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(CaptureAct.class);
        barcodeLauncher.launch(options);
    }

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        // it is red, but it works
        qr_code_data = result.getContents().toString();

        if (uuid_file_exists) {
            // the user is registered, so the QR code corresponds to the sign in session id
            session_id = qr_code_data;
            return;
        } else {
            // the user is not registered, so the QR code corresponds to uuid
            uuid = qr_code_data;
            if (!proximity_card_id.equals("")) updateCardInDatabase();
            else {
                scanCardDialog.show();
            }
        }
    });

    public void updateCardInDatabase() {
        // POST uuid and proximity card id to XAMPP server
        // there, a PHP script will add to the user with uuid the proximity card id
        StringRequest stringRequest = new StringRequest(Request.Method.POST, server_url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                notification("XAMPP server response:\n" + response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                notification("onErrorResponse() called");
                error.printStackTrace();
            }
        }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> Params = new HashMap<>();
                Params.put("qr_code_uuid", uuid);
                Params.put("proximity_card_id", proximity_card_id);
                return Params;
            }
        };
        Singleton.getInstance(MainActivity.this).addToRequestQueue(stringRequest);

        // registration is complete
        btn_register_sign_in.setText(R.string.sign_in);

        try (FileOutputStream fileOutputStream = this.openFileOutput(uuid_file, Context.MODE_PRIVATE)) {
            fileOutputStream.write(uuid.getBytes());
            uuid_file_exists = true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        // update options menu >> make "Delete account" option visible
        supportInvalidateOptionsMenu();
    }
}
