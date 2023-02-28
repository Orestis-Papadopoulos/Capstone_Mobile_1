package edu.acg.o.papadopoulos.capstone1;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainActivity extends AppCompatActivity {

    private String uuid, proximity_card_id;
    private Button btn_register_sign_in;
    private final String server_url = "http://192.168.2.59/capstone1/index.php";
    NfcAdapter nfcAdapter;
    enum MessageType {
        NFC_NOT_SUPPORTED,
        ENABLE_NFC
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nfcAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());
        if (nfcAdapter == null) showDialog("NFC Adapter Error", "Your device does not support NFC", this, MessageType.NFC_NOT_SUPPORTED);

        if (!nfcAdapter.isEnabled()) {
            showDialog("NFC is Disabled", "Would you like to turn it on?", this, MessageType.ENABLE_NFC);
        }

        // write uuid to app-specific storage
        // check if file exists
        String[] app_specific_files = getApplicationContext().fileList();
        String uuid_filename = "user_uuid";
        boolean uuid_file_exists = Arrays.asList(app_specific_files).contains(uuid_filename);

        if (!uuid_file_exists) {
            try (FileOutputStream fileOutputStream = this.openFileOutput(uuid_filename, Context.MODE_PRIVATE)) {
                fileOutputStream.write(uuid.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // read file into uuid
            // set btn text to 'sign in'
        }

        btn_register_sign_in = findViewById(R.id.register_sign_in);

        // use the foreground dispatch system (reason: if the app is open, do not reopen the activity)

        try {
            Tag tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
            proximity_card_id = tag.getId().toString();
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    public void notification(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private void openQRCodeScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Volume up to flash on");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(CaptureAct.class);
        barcodeLauncher.launch(options);
    }

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        // it is red, but it works
        uuid = result.getContents().toString();

        if (proximity_card_id.equals("")) {
            // prompt user to scan their card, and then execute the following
        }

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
        }){
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map <String,String> Params = new HashMap<>();
                Params.put("qr_code_uuid", uuid);
                Params.put("proximity_card_id", proximity_card_id);
                return Params;
            }
        };
        Singleton.getInstance(MainActivity.this).addToRequestQueue(stringRequest);
    });

    public void showDialog(String title, String message, Context context, MessageType messageType) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message);

        switch (messageType) {
            case NFC_NOT_SUPPORTED: {
                dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                    public void onClick(DialogInterface dialog, int which) {
                        finishAndRemoveTask();
                    }
                });
            } case ENABLE_NFC: {
                dialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            intent = new Intent(Settings.ACTION_NFC_SETTINGS);
                        } else {
                            intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                        }
                        startActivity(intent);
                    }
                });
                dialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
            }
        }

        dialog.show();
    }
}
