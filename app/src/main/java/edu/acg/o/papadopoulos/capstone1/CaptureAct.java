package edu.acg.o.papadopoulos.capstone1;

import android.content.Intent;

import com.journeyapps.barcodescanner.CaptureActivity;

// used in MainActivity by method "openQRCodeScanner()"
public class CaptureAct extends CaptureActivity {

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        // do this to return to MainActivity, otherwise the app closes
        Intent mainActivityIntent = new Intent(CaptureAct.this, MainActivity.class);
        startActivity(mainActivityIntent);
    }
}
