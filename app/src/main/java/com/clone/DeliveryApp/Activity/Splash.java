package com.clone.DeliveryApp.Activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.AsyncQueryHandler;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.clone.DeliveryApp.R;
import com.clone.DeliveryApp.Utility.ConnectionHelper;
import com.clone.DeliveryApp.Utility.LocationHelper;

import java.util.ArrayList;
import java.util.List;

public class Splash extends AppCompatActivity {

    private ImageView ivLogo;



    int REQUEST_ID_MULTIPLE_PERMISSIONS = 10000;
    boolean isAlradyRequested = false;


    private static final String TAG = "Splash";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        //LocationHelper.GetLocation(getApplicationContext());
    }


    @Override
    protected void onResume() {
        super.onResume();



        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {

                if (checkAndRequestPermissions()) {

                    isAlradyRequested = false;

                    startActivity(new Intent(Splash.this,Login.class));
                    finish();
                }
            }
        },1000);
    }


    private boolean checkAndRequestPermissions() {
        isAlradyRequested = true;
        if (Build.VERSION.SDK_INT >= 23) {

            int permissionLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
            int permissionCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

            List<String> listPermissionsNeeded = new ArrayList<>();

            if (!(Build.VERSION.SDK_INT >= 34)) {
                int permissionWrite = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                int permissionRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

                if(permissionWrite != PackageManager.PERMISSION_GRANTED){

                    listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

                }
                if (permissionRead != PackageManager.PERMISSION_GRANTED){

                    listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);

                }
            }
            if (permissionCamera != PackageManager.PERMISSION_GRANTED){

                listPermissionsNeeded.add(Manifest.permission.CAMERA);

            }
            if (permissionLocation != PackageManager.PERMISSION_GRANTED) {

                listPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);

            }

            if (!listPermissionsNeeded.isEmpty()) {

                ActivityCompat.requestPermissions(this,
                        listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), REQUEST_ID_MULTIPLE_PERMISSIONS);
                return false;
            }

        } else {

            return true;
        }

        return true;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ID_MULTIPLE_PERMISSIONS) {

            try {
                isAlradyRequested = false;
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }

    }

}
