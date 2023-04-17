package edu.acg.o.papadopoulos.capstone1;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Window;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

// the device I used for testing ran Android 11
// I did not use the SplashScreen API because it starts from Android 12
public class SplashScreenActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // no need to show the ActionBar on splash screen
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.hide();

        // show this Activity for 5 seconds and then navigate to MainActivity
        new CountDownTimer(5000,1000){
            @Override
            public void onTick(long millisUntilFinished){}

            @Override
            public void onFinish(){
                Intent mainActivityIntent = new Intent(SplashScreenActivity.this, MainActivity.class);
                startActivity(mainActivityIntent);

                // add this so that when the back button is pressed in MainActivity,
                // the SplashScreenActivity is not shown
                finish();
            }
        }.start();
    }
}
