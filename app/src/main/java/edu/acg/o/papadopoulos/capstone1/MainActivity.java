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
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
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

/**
 * This application allows users to authenticate themselves to the Qard web app. A MIFARE Classic
 * proximity card must be scanned on the back of the phone and then the user must scan the QR code
 * displayed by the web app in order to register/sign in.
 *
 * @course ITC4918 Software Development Capstone project
 * @semester Spring 2023
 * @author Orestis Papadopoulos
 * @instructor Ioannis Christou, Ph.D.
 * */

public class MainActivity extends AppCompatActivity {

    private NfcAdapter nfcAdapter;
    private WifiManager wifiManager;

    // when the language is changed, the hint on this EditText is the only one which does not change
    // the app must close and open again for the text to change language
    private EditText ip_address;
    private Button btn_open_scanner;
    private TextView txt_view_name;

    private String authentication_url = "";
    private String qr_code_data = "", user_uuid = "", proximity_card_id = "", sign_in_session_uuid = "";
    private String uuid_filename = "user_uuid";
    private String first_name, last_name;

    // must be updated after every time postDataToServer() is called with operation REGISTER_USER or DELETE_USER
    private boolean uuid_file_exists = false, user_is_registered = false;;

    // these dialogs are global because they might need to show/hide multiple times
    private AlertDialog scanCardDialog, pendingAuthenticationDialog;

    // use PendingIntent, and ForegroundDispatch to prevent Activity from reopening if card is scanned while app is open
    // the app can open automatically if a card is scanned; if the app is opened manually, wait for card to be scanned with this Intent
    private PendingIntent cardWasScanned;
    private Tag tag;
    private enum Operation {
        REGISTER_USER,
        SIGN_IN_USER,
        DELETE_USER,
        GET_USERS_NAME
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ip_address = findViewById(R.id.ip_address);
        btn_open_scanner = findViewById(R.id.open_scanner);
        txt_view_name = findViewById(R.id.users_name);
        txt_view_name.setText(R.string.you_are_not_registered);
        nfcAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // 1: check if the phone has NFC; if it does not, close app
        if (nfcAdapter == null) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.nfc_not_supported)
                    .setMessage(R.string.this_application_cannot_function_without_NFC)
                    .setPositiveButton(R.string.ok, (dialog, id) -> {
                        finishAndRemoveTask(); // closes app
                    })
                    .show();

            // this is needed because otherwise I get a null pointer exception
            // every time the nfcAdapter is invoked
            return;
        }

        // 2: prompt user to enable NFC (will open NFC Settings)
        if (!nfcAdapter.isEnabled()) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.nfc_disabled)
                    .setMessage(R.string.this_application_requires_NFC_do_you_want_to_turn_it_on)
                    .setPositiveButton(R.string.yes, (dialog, id) -> {
                        startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                    })
                    .setNegativeButton(R.string.no, (dialog, id) -> {})
                    .show();
        }

        // 3: onResume() called

        // 4: check if app-specific file exists; if it does, the user has been registered
        String[] app_specific_files = getApplicationContext().fileList();
        uuid_file_exists = Arrays.asList(app_specific_files).contains(uuid_filename);

        // 5: if the user is registered, update options menu and set user uuid
        if (uuid_file_exists) {
            user_is_registered = true;
            supportInvalidateOptionsMenu(); // calls onPrepareOptionsMenu() below
            try {
                setUserUuidFromFile(uuid_filename);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            // get the first and last name of user from database
            postDataToServer(user_uuid, proximity_card_id, sign_in_session_uuid, Operation.GET_USERS_NAME);
        }

        // the app can open automatically if a card is scanned; if the app is opened manually, wait for card to be scanned with this Intent
        cardWasScanned = PendingIntent.getActivity(this,0,
                new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),0);

        // the dialogs are created, not shown
        scanCardDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.scan_your_card)
                .setMessage(R.string.put_card)
                .setNegativeButton(R.string.cancel, (dialog, id) -> {})
                .create();

        pendingAuthenticationDialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.pending_authentication)
                .setMessage(R.string.scan_to_delete)
                .setNegativeButton(R.string.cancel, (dialog, id) -> {
                })
                .create();

        try {
            // this condition is important because in case onCreate() is called without a tag
            // having been scanned, then "proximity_card_id" will be assigned to nothing

            if (proximity_card_id.equals("")) {
                // get proximity_card_id from SplashActivity
                Bundle bundle = getIntent().getExtras();
                proximity_card_id = bundle.getString("proximity_card_id");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // button listener QR code scanner
        btn_open_scanner.setOnClickListener(view -> openQRCodeScanner());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 3: prompt user to enable WiFi
        // first NFC is enabled and when the user returns to MainActivity WiFi is checked
        if (nfcAdapter != null && !wifiManager.isWifiEnabled() && nfcAdapter.isEnabled()) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.wifi_disabled)
                    .setMessage(R.string.this_application_requires_an_Internet_connection_do_you_want_to_turn_WiFi_on)
                    .setPositiveButton(R.string.yes, (dialog, id) -> {
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    })
                    .setNegativeButton(R.string.no, (dialog, id) -> {})
                    .show();
        }

        // enable the foreground dispatch to wait for card scan (in case the app did not open via scan)
        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(this, cardWasScanned,null,null);
        }
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
            // handle multiple card scans
            if (proximity_card_id != null) {
                notification(getString(R.string.already_scanned));
                return;
            }
            // convert byte array to String
            tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
            proximity_card_id = new String(tag.getId(), StandardCharsets.UTF_8);

            // card scanned after user selected to delete account
            if (pendingAuthenticationDialog.isShowing()) {
                deleteUserAccount();
                pendingAuthenticationDialog.cancel();
                return;
            }

            if (!user_is_registered && !user_uuid.equals("")) {
                registerUser();
            } else if (user_is_registered && !sign_in_session_uuid.equals("")) {
                postDataToServer(user_uuid, proximity_card_id, sign_in_session_uuid, Operation.SIGN_IN_USER);
            }
            if (scanCardDialog.isShowing()) scanCardDialog.cancel();

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
            // if you don't add the "return true;", all options are clicked when one is clicked
            case R.id.about_option:
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.about)
                        .setMessage(R.string.about_message)
                        .setPositiveButton(R.string.ok, (dialog, id) -> {})
                        .show();
                return true;
            case R.id.see_card_id_option:
                if (proximity_card_id == null) notification(getString(R.string.scan_to_see_id));
                else notification(getString(R.string.your_card_id) + "\t\t" + proximity_card_id);
                return true;
            case R.id.change_language_option:
                startActivity(new Intent(Settings.ACTION_LOCALE_SETTINGS));
                return true;
            case R.id.delete_account_option:
                // do not allow an account to be deleted if the card has not bee scanned
                if (proximity_card_id.equals("")) pendingAuthenticationDialog.show();
                else deleteUserAccount();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // for changes during runtime
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // show the "Delete account" option only if the user is registered (i.e., when there is an account to delete)
        menu.findItem(R.id.delete_account_option).setVisible(uuid_file_exists);
        return false;
    }

    /**
     * Displays a Toast with the specified message.
     * @param message The text to display.
     * */
    public void notification(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    /**
     * Starts the QR code scanner Activity.
     * @source https://github.com/journeyapps/zxing-android-embedded
     * */
    private void openQRCodeScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("             " + getString(R.string.scan_code) + "\n\n\n\n\n\n\n" + getString(R.string.press_volume) + "\n\n\n\n");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(CaptureAct.class);
        barcodeLauncher.launch(options);
    }

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        qr_code_data = result.getContents().toString(); // it is red, but it works

        if (user_is_registered) sign_in_session_uuid = qr_code_data;
        else user_uuid = qr_code_data;

        if (proximity_card_id != null && !proximity_card_id.equals("")) {
            if (user_is_registered) postDataToServer(user_uuid, proximity_card_id, sign_in_session_uuid, Operation.SIGN_IN_USER);
            else registerUser();
        } else scanCardDialog.show();
    });

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    /**
     * Sets the "user_uuid" variable to the value in the passed file.
     * @param filename The name of the file where the user's uuid is stored.
     * */
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

    /**
     * Uses the HTTP POST method to interact with the database.
     * @param user_uuid The user's universally unique id.
     * @param proximity_card_id The serial number of the user's proximity card.
     * @param sign_in_session_uuid The universally unique id of the current sign in session of the user.
     * @param operation The way the app will interact with the database (register/sign in/delete user, or get user's name).
     * @source https://www.c-sharpcorner.com/article/send-data-to-the-remote-database-in-android-application/
     * */
    public void postDataToServer(String user_uuid, String proximity_card_id, String sign_in_session_uuid, Operation operation) {

        // you need the XAMPP web server installed for this
        // the "authentication.php" script must be stored at C:\xampp\htdocs\capstone
        authentication_url = "http://" + ip_address.getText() + "/capstone/authentication.php";

        StringRequest stringRequest = new StringRequest(Request.Method.POST, authentication_url, response -> {
            if (operation == Operation.GET_USERS_NAME) {
                String[] name = response.split(" ");
                first_name = name[0];
                last_name = name[1];
                txt_view_name.setText(getString(R.string.you_are_registered_as)  + "\n\n" + first_name + "\n" + last_name);
            } else notification(getString(R.string.server_response) + "\n\n" + response);
        }, error -> {
            notification(getString(R.string.server_error));
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

    /**
     * Deletes the app-specific file which holds the user's uuid,
     * deletes the user from the database,
     * resets the user's card id,
     * updates the options menu.
     * */
    public void deleteUserAccount() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(getString(R.string.confirm_deletion))
                .setMessage(getString(R.string.are_you_sure))
                .setPositiveButton(R.string.yes, (dialog, id) -> {
                    new File(this.getFilesDir(), uuid_filename).delete();
                    uuid_file_exists = false;

                    postDataToServer(user_uuid, proximity_card_id, sign_in_session_uuid, Operation.DELETE_USER);
                    user_is_registered = false;
                    proximity_card_id = "";
                    txt_view_name.setText(R.string.you_are_not_registered);

                    supportInvalidateOptionsMenu(); // calls onPrepareOptionsMenu()
                    notification(getString(R.string.account_deleted));
                })
                .setNegativeButton(R.string.no, (dialog, id) -> {
                    notification(getString(R.string.deletion_cancelled));
                })
                .show();
    }

    /**
     * Adds the user to the database,
     * creates a file which holds the user's uuid,
     * updates the options menu.
     * */
    public void registerUser() {
        postDataToServer(user_uuid, proximity_card_id, sign_in_session_uuid, Operation.REGISTER_USER);
        user_is_registered = true;

        try (FileOutputStream fileOutputStream = this.openFileOutput(uuid_filename, Context.MODE_PRIVATE)) {
            fileOutputStream.write(user_uuid.getBytes());
            uuid_file_exists = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        supportInvalidateOptionsMenu(); // calls onPrepareOptionsMenu()
        postDataToServer(user_uuid, proximity_card_id, sign_in_session_uuid, Operation.GET_USERS_NAME);
    }
}
