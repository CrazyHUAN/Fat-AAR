package com.huan.a_fataar;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.huan.modellocal_code.ModelLocal;
import com.huan.modelproject.ModelProject;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tvShow = findViewById(R.id.am_tv_show);

        String strProj = ModelProject.getString();
        String strLocal = ModelLocal.getString();


    }
}
