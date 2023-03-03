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
import android.util.Log;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private NfcAdapter nfcAdapter;
    private WifiManager wifiManager;
    private Button btn_register_sign_in;
    private final String authentication_url = "http://192.168.2.59/capstone/authentication.php";
    private String qr_code_data = "", user_uuid = "", proximity_card_id = "", sign_in_session_uuid = "";
    private final String uuid_filename = "user_uuid";
    private boolean uuid_file_exists = false;

    // this boolean must be updated after every time postDataToServer() is called with operation REGISTER_USER or DELETE_USER
    private boolean user_is_registered = false;
    private AlertDialog scanCardDialog; // might need to show/hide multiple times

    // use PendingIntent, and ForegroundDispatch to prevent Activity from reopening if card is scanned while app is open
    // the app can open auto if card is scanned; if the app is opened manually, wait for card to be scanned with this Intent
    private PendingIntent cardWasScanned;
    private Tag tag;
    private enum Operation {
        REGISTER_USER,
        SIGN_IN_USER,
        DELETE_USER
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_register_sign_in = findViewById(R.id.register_sign_in);
        nfcAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // 1: check if the phone has NFC
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
        uuid_file_exists = Arrays.asList(app_specific_files).contains(uuid_filename);

        // 5: if the user is registered, change btn text
        if (uuid_file_exists) {
            supportInvalidateOptionsMenu(); // calls onPrepareOptionsMenu() below
            btn_register_sign_in.setText(R.string.sign_in);
            try {
                setUserUuidFromFile(uuid_filename);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            user_is_registered = true;
        }

        cardWasScanned = PendingIntent.getActivity(this,0,
                new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),0);

        // notice: the dialog is created, not shown
        scanCardDialog = new AlertDialog.Builder(this)
                .setTitle("Scan Your Card")
                .setMessage("Put your card on the back of your phone")
                .setNegativeButton("Cancel", (dialog, id) -> {})
                .create();

        // try block executes successfully if app opens with scan
        try {
            tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
            // tag.getId() returns byte array; convert to String
            proximity_card_id = new String(tag.getId(), StandardCharsets.UTF_8);
            notification("Card scan caused app to open");
        } catch (Exception e) {
            e.printStackTrace();
        }

        btn_register_sign_in.setOnClickListener(view -> openQRCodeScanner());
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

        // enable the foreground dispatch to wait for card scan (in case the app did not open via scan)
        nfcAdapter.enableForegroundDispatch(this, cardWasScanned,null,null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        nfcAdapter.disableForegroundDispatch(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // for opening the app when an NFC tag is detected

        // called when card is scanned while app is open
        try {
            tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
            proximity_card_id = new String(tag.getId(), StandardCharsets.UTF_8);

            if (!user_is_registered) {
                postDataToServer(user_uuid, proximity_card_id, sign_in_session_uuid, Operation.REGISTER_USER);
                user_is_registered = true;
                btn_register_sign_in.setText(R.string.sign_in);

                try (FileOutputStream fileOutputStream = this.openFileOutput(uuid_filename, Context.MODE_PRIVATE)) {
                    fileOutputStream.write(user_uuid.getBytes());
                    uuid_file_exists = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                supportInvalidateOptionsMenu(); // calls onPrepareOptionsMenu() below

            } else if (!sign_in_session_uuid.equals("") && user_is_registered) {
                Log.d("SIGN IN USER WITH:\n", "user uuid = " + user_uuid + "\ncard id = " + proximity_card_id + "\nsession id = " + sign_in_session_uuid);
                postDataToServer(user_uuid, proximity_card_id, sign_in_session_uuid, Operation.SIGN_IN_USER);
            }

            scanCardDialog.cancel(); // if the dialog has been opened, close it on card scan
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
        switch (item.getItemId()) {
            case R.id.settings_option:
                notification("No Settings yet");
            case R.id.about_option:
                notification("No About yet");
            case R.id.delete_account_option:
                new File(this.getFilesDir(), uuid_filename).delete();
                uuid_file_exists = false;

                Log.d("DELETE USER WITH:", "user uuid = " + user_uuid + "\ncard id = " + proximity_card_id + "\nsession id = " + sign_in_session_uuid);

                postDataToServer(user_uuid, proximity_card_id, sign_in_session_uuid, Operation.DELETE_USER);
                user_is_registered = false;
                btn_register_sign_in.setText(R.string.register);

                proximity_card_id = ""; // reset card id

                supportInvalidateOptionsMenu(); // calls onPrepareOptionsMenu() below
                notification("You selected to delete your account");
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
        options.setPrompt("             Scan the QR code\n\n\n\n\n\n\nPress volume up to turn flash on\n\n\n\n");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(CaptureAct.class);
        barcodeLauncher.launch(options);
    }

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        qr_code_data = result.getContents().toString(); // it is red, but it works

        Operation operation;
        if (user_is_registered) {
            sign_in_session_uuid = qr_code_data;
            operation = Operation.SIGN_IN_USER;
        } else {
            user_uuid = qr_code_data;
            operation = Operation.REGISTER_USER;
        }
        if (!proximity_card_id.equals("")) {
            Log.d(operation.toString(), "user uuid = " + user_uuid + "\ncard id = " + proximity_card_id + "\nsession id = " + sign_in_session_uuid);
            postDataToServer(user_uuid, proximity_card_id, sign_in_session_uuid, operation);
            user_is_registered = true;
        } else scanCardDialog.show();
    });

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void setUserUuidFromFile(String filename) throws FileNotFoundException {

        FileInputStream fileInputStream = getApplicationContext().openFileInput(filename);
        InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
        StringBuilder stringBuilder = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
            String line = reader.readLine();
            stringBuilder.append(line);
            user_uuid = stringBuilder.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void postDataToServer(String user_uuid, String proximity_card_id, String sign_in_session_uuid, Operation operation) {
        StringRequest stringRequest = new StringRequest(Request.Method.POST, authentication_url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                notification("XAMPP server response:\n\n" + response);
            }
        }, error -> {
            notification("onErrorResponse() called");
            error.printStackTrace();
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> Params = new HashMap<>();
                Params.put("user_uuid", user_uuid);
                Params.put("proximity_card_id", proximity_card_id);
                Params.put("sign_in_session_uuid", sign_in_session_uuid);
                Params.put("operation", operation.toString());
                return Params;
            }
        };
        Singleton.getInstance(MainActivity.this).addToRequestQueue(stringRequest);
    }
}
