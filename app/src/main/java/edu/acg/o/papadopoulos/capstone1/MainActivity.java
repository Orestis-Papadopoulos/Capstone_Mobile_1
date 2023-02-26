package edu.acg.o.papadopoulos.capstone1;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {

    private TextView proximity_card_id, qr_code_uuid;
    private Button btn_capture_qrCode, btn_connect_to_database;
    private final String server_url = "https://172.29.144.1/capstone1/index.php";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        proximity_card_id = findViewById(R.id.proximity_card_id);
        qr_code_uuid = findViewById(R.id.qr_code_uuid);
        btn_capture_qrCode = findViewById(R.id.capture_qrCode);
        btn_connect_to_database = findViewById(R.id.connect_to_database);

        btn_capture_qrCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanCode();

                // if there is a user with this uuid, add to them the card id, and add the uuid to internal app storage
            }
        });

        btn_connect_to_database.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectToDatabase();
            }
        });

        try {
            Tag tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
            proximity_card_id.append(tag.getId().toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    public void notification(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private void scanCode() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Volume up to flash on");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(CaptureAct.class);
        barcodeLauncher.launch(options);
    }

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        // it is red but it works
        qr_code_uuid.append(result.getContents().toString());
    });

    public void connectToDatabase() {
        StringRequest stringRequest = new StringRequest(server_url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                notification("Error while connecting to database: onErrorResponse() was called");
                error.printStackTrace();
            }
        }){
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map <String,String> Params = new HashMap<>();
                Params.put("qr_code_uuid", qr_code_uuid.getText().toString());
                Params.put("proximity_card_id", proximity_card_id.getText().toString());
                return Params;
            }
        };
        Singleton.getInstance(MainActivity.this).addToRequestQueue(stringRequest);
    }
}
