package com.clone.DeliveryApp.Activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.clone.DeliveryApp.R;
import com.clone.DeliveryApp.Utility.AppConstant;
import com.clone.DeliveryApp.Utility.ScheduleHelper;
import com.clone.DeliveryApp.Utility.SyncService;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SplashLogin extends AppCompatActivity {

    private ImageView ivLogo;

    private RelativeLayout rlLayout;
    private TextInputLayout company, email, driver, vehicle;
    private TextInputEditText etCompany, etEmail, etDriver, etVehicle;
    private Button proceed;
    private ProgressBar loadingIcon;

    int REQUEST_ID_MULTIPLE_PERMISSIONS = 10000;
    boolean isAlradyRequested = false;


    private static final String TAG = "SplashLogin";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        etCompany = findViewById(R.id.et_com);
        etEmail = findViewById(R.id.et_email);
        etDriver =findViewById(R.id.et_driver);
        etVehicle = findViewById(R.id.et_vehicle);
        rlLayout = findViewById(R.id.rl_main);
        proceed = findViewById(R.id.btn_login);
        loadingIcon = findViewById(R.id.progressBar);
        ivLogo = findViewById(R.id.iv_splashLogo);


        Handler handler = new Handler();

        if (!checkAndRequestPermissions()) {

            animateLogo();
        }


        if (GetCompany()!=null && GetCompany().length()>0){

            etCompany.setText(GetCompany());
        }
        else {
            //for testing only

            etCompany.setText("DEV");
            etCompany.setFocusable(false);
            etCompany.setCursorVisible(false);
        }
        if (GetDriver()!=null && GetDriver().length()>0){

            etDriver.setText(GetDriver());
        }
        else {
            //for testing only

            etDriver.setText("DEV");
        }
        if (GetEmail()!=null && GetEmail().length()>0){

            etEmail.setText(GetEmail());
        }
        if (GetVehicle()!=null && GetVehicle().length()>0){

            etVehicle.setText(GetVehicle());
        }
        else {
            //for testing only

            etVehicle.setText("DEV");
        }

        proceed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (validation()){

                    StoreCompany(etCompany.getText().toString());
                    StoreDriver(etDriver.getText().toString());
                    StoreEmail(etEmail.getText().toString());
                    StoreVehicle(etVehicle.getText().toString());

                    etCompany.setFocusable(false);
                    etCompany.setCursorVisible(false);
                    etDriver.setFocusable(false);
                    etDriver.setCursorVisible(false);
                    etEmail.setFocusable(false);
                    etEmail.setCursorVisible(false);
                    etVehicle.setFocusable(false);
                    etVehicle.setCursorVisible(false);

                    proceed.setVisibility(View.GONE);
                    loadingIcon.setVisibility(View.VISIBLE);

                    Toast.makeText(SplashLogin.this, "Downloading Delivery Schedule...", Toast.LENGTH_LONG).show();

                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            AppConstant.tripList = ScheduleHelper.getTrips(etCompany.getText().toString());

                            ScheduleHelper.downloadSchedule(SplashLogin.this, etCompany.getText().toString(), SplashLogin.this);

                            Intent startSyncIntent = new Intent(SplashLogin.this, SyncService.class);
                            startService(startSyncIntent);

                            startActivity(new Intent(SplashLogin.this, DashHeader.class));
                            finish();
                        }
                    });

                    thread.start();
                }
            }
        });


        etEmail.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    // do your stuff here
                    if(!validEmail(etEmail.getText().toString())) {

                        String text="Invalid Email. Please Re-enter";
                        SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                        biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                        Toast.makeText(SplashLogin.this, biggerText, Toast.LENGTH_LONG).show();

                    }
                }
                return false;
            }
        });

    }

/*    @Override
    public void onResume() {
        super.onResume();

        Log.i("OnResume", "onResume called");

        animateLogo();
    }*/


    public boolean validation() {
        boolean bool;

        try {
            if (etCompany.getText().toString().length() < 1) {
                //user input empty then set bool false
                bool = false;

                String text="Enter company name";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(SplashLogin.this, biggerText, Toast.LENGTH_SHORT).show();

                etCompany.requestFocus();

            } else if (etEmail.getText().toString().length() < 1 || !validEmail(etEmail.getText().toString())) {
                bool = false;

                String text="Invalid Email. Please Re-enter";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(SplashLogin.this, biggerText, Toast.LENGTH_SHORT).show();

                etEmail.requestFocus();

            }else if (etDriver.getText().toString().length() < 1) {
                bool = false;

                String text="Enter Driver name";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(SplashLogin.this, biggerText, Toast.LENGTH_SHORT).show();

                etDriver.requestFocus();

            }else if (etVehicle.getText().toString().length() < 1) {
                bool = false;

                String text="Enter vehicle number";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(SplashLogin.this, biggerText, Toast.LENGTH_SHORT).show();

                etVehicle.requestFocus();
            }

            else {

                //user input not empty so set bool true
                bool = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            bool = false;
        }
        return bool;

    }


    public void animateLogo() {

        ivLogo.setVisibility(View.VISIBLE);
        Animation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setInterpolator(new DecelerateInterpolator()); //add this
        fadeIn.setDuration(1000);
        fadeIn.setStartOffset(500);

        Animation slide = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_up);
        slide.setStartOffset(1950);
        slide.setDuration(250);
        slide.setInterpolator(SplashLogin.this, android.R.anim.accelerate_decelerate_interpolator);
        slide.setFillAfter(true);

        AnimationSet set = new AnimationSet(false);
        set.addAnimation(fadeIn);
        set.addAnimation(slide);
        set.setFillAfter(true);

        ivLogo.startAnimation(set);

        rlLayout.setVisibility(View.VISIBLE);
        proceed.setVisibility(View.VISIBLE);

        Animation fadeInLayout = new AlphaAnimation(0, 1);
        fadeInLayout.setInterpolator(new DecelerateInterpolator()); //add this
        fadeInLayout.setDuration(600);
        fadeInLayout.setStartOffset(2225);

        rlLayout.setAnimation(fadeInLayout);
        proceed.setAnimation(fadeInLayout);
    }


    public String GetCompany() {
        SharedPreferences shp = this.getSharedPreferences("COMPANY", MODE_PRIVATE);
        System.out.println("getting Image" + shp.getString("company", ""));
        return shp.getString("company", "");
    }


    public void StoreCompany(String image_url) {
        SharedPreferences.Editor editor = getSharedPreferences("COMPANY", MODE_PRIVATE).edit();
        editor.putString("company", image_url);
        System.out.println("company changed>>>>>>>>>" + image_url);
        editor.commit();
    }



    public String GetDriver() {
        SharedPreferences shp = this.getSharedPreferences("DRIVER", MODE_PRIVATE);
        System.out.println("getting driver" + shp.getString("driver", ""));
        return shp.getString("driver", "");
    }


    public void StoreDriver(String image_url) {
        SharedPreferences.Editor editor = getSharedPreferences("DRIVER", MODE_PRIVATE).edit();
        editor.putString("driver", image_url);
        System.out.println("driver changed>>>>>>>>>" + image_url);
        editor.commit();
    }


    public String GetEmail() {
        SharedPreferences shp = this.getSharedPreferences("EMAIL", MODE_PRIVATE);
        System.out.println("getting email" + shp.getString("email", ""));
        return shp.getString("email", "");
    }


    public void StoreEmail(String image_url) {
        SharedPreferences.Editor editor = getSharedPreferences("EMAIL", MODE_PRIVATE).edit();
        editor.putString("email", image_url);
        System.out.println("email changed>>>>>>>>>" + image_url);
        editor.commit();
    }



    public String GetVehicle() {
        SharedPreferences shp = this.getSharedPreferences("VEHICLE", MODE_PRIVATE);
        System.out.println("getting vehicle" + shp.getString("vehicle", ""));
        return shp.getString("vehicle", "");
    }


    public void StoreVehicle(String image_url) {
        SharedPreferences.Editor editor = getSharedPreferences("VEHICLE", MODE_PRIVATE).edit();
        editor.putString("vehicle", image_url);
        System.out.println("vehicle changed>>>>>>>>>" + image_url);
        editor.commit();
    }


    public String GetApi() {
        SharedPreferences shp = this.getSharedPreferences("API", MODE_PRIVATE);
        System.out.println("getting api" + shp.getString("api", ""));
        return shp.getString("api", "");
    }

    public void StoreApi(String image_url) {
        SharedPreferences.Editor editor = getSharedPreferences("API", MODE_PRIVATE).edit();
        editor.putString("api", image_url);
        System.out.println("api changed>>>>>>>>>" + image_url);
        editor.commit();
    }

    private boolean validEmail(String email) {
        Pattern pattern = Patterns.EMAIL_ADDRESS;
        return pattern.matcher(email).matches();
    }


    private List<String> requiredPermissionsList() {

        int permissionLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        int permissionCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

        List<String> listPermissionsNeeded = new ArrayList<>();

        if (!(Build.VERSION.SDK_INT >= 34)) {
            int permissionWrite = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            int permissionRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

            if (permissionWrite != PackageManager.PERMISSION_GRANTED) {

                listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

            }
            if (permissionRead != PackageManager.PERMISSION_GRANTED) {

                listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);

            }
        }
        if (permissionCamera != PackageManager.PERMISSION_GRANTED) {

            listPermissionsNeeded.add(Manifest.permission.CAMERA);

        }
        if (permissionLocation != PackageManager.PERMISSION_GRANTED) {

            listPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);

        }

        return listPermissionsNeeded;
    }

    private boolean checkAndRequestPermissions() {

        List<String> listPermissionsNeeded = requiredPermissionsList();

        if (!listPermissionsNeeded.isEmpty()) {

            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), REQUEST_ID_MULTIPLE_PERMISSIONS);

            return true;
        }

        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requiredPermissionsList().isEmpty()) {

            animateLogo();
        }
    }
}
