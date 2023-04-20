package edu.acg.o.papadopoulos.capstone1;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Window;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;

// the device I used for testing ran Android 11
// I did not use the SplashScreen API because it starts from Android 12
public class SplashScreenActivity extends AppCompatActivity {

    private Tag tag;
    private String proximity_card_id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // no need to show the ActionBar on splash screen; hide it
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.hide();

        // show this Activity for 5 seconds and then navigate to MainActivity
        new CountDownTimer(4000,1000){
            @Override
            public void onTick(long millisUntilFinished){}

            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onFinish(){
                Intent mainActivityIntent = new Intent(SplashScreenActivity.this, MainActivity.class);

                // if app opened by card scan, get the card's serial number
                tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
                // tag.getId() returns byte array; convert to String like so:
                if (tag != null) proximity_card_id = new String(tag.getId(), StandardCharsets.UTF_8);

                // pass the card's serial number to MainActivity
                Bundle bundle = new Bundle();
                bundle.putString("proximity_card_id", proximity_card_id);
                mainActivityIntent.putExtras(bundle);

                startActivity(mainActivityIntent);

                // add this so that when the back button is pressed in MainActivity,
                // the SplashScreenActivity is not shown
                finish();
            }
        }.start();
    }
}
