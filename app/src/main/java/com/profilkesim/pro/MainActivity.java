package com.profilkesim.pro;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView ekran = new TextView(this);
        ekran.setText("Profil Kesim Pro\n\nUygulama açıldı!");
        ekran.setTextSize(24);
        ekran.setPadding(40, 80, 40, 40);
        setContentView(ekran);
    }
}
