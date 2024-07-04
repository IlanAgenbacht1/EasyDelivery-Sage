package com.clone.DeliveryApp.Activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.clone.DeliveryApp.Model.Schedule;
import com.clone.DeliveryApp.R;
import com.clone.DeliveryApp.Utility.AppConstant;
import com.clone.DeliveryApp.Utility.ScheduleHelper;
import com.clone.DeliveryApp.Utility.SyncService;

import java.util.regex.Pattern;

public class Login extends AppCompatActivity {


    private EditText etEmail,etCompany,etDriver,etVehicle;
    private Button btnlogin;
    private ProgressBar loadingIcon;
    private Context context;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        AppConstant.gpsList.clear();

        etEmail = findViewById(R.id.et_email);
        btnlogin = findViewById(R.id.btn_login);
        etCompany = findViewById(R.id.et_com);
        etDriver = findViewById(R.id.et_driver);
        etVehicle = findViewById(R.id.et_vehicle);
        loadingIcon = findViewById(R.id.progressBar);

        loadingIcon.setVisibility(View.GONE);

        if (GetCompany()!=null && GetCompany().length()>0){

            etCompany.setText(GetCompany());
        }
        if (GetDriver()!=null && GetDriver().length()>0){

            etDriver.setText(GetDriver());
        }
        if (GetEmail()!=null && GetEmail().length()>0){

            etEmail.setText(GetEmail());
        }
        if (GetVehicle()!=null && GetVehicle().length()>0){

            etVehicle.setText(GetVehicle());
        }

        btnlogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Toast.makeText(context, "Downloading trip schedule...", Toast.LENGTH_LONG).show();

                if (validation()){

                    StoreCompany(etCompany.getText().toString());
                    StoreDriver(etDriver.getText().toString());
                    StoreEmail(etEmail.getText().toString());
                    StoreVehicle(etVehicle.getText().toString());

                    btnlogin.setVisibility(View.GONE);
                    loadingIcon.setVisibility(View.VISIBLE);

                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {

                            ScheduleHelper.getSchedule(context, GetCompany(), Login.this);

                            startActivity(new Intent(Login.this, DashHeader.class));
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
                        Toast.makeText(Login.this, biggerText, Toast.LENGTH_LONG).show();

                    }
                }
                return false;
            }
        });

        context = this;
        Intent startSyncIntent = new Intent(this, SyncService.class);
        startService(startSyncIntent);
    }


    public boolean validation() {
        boolean bool = false;

        try {
            if (etCompany.getText().toString().length() < 1) {
                //user input empty then set bool false
                bool = false;

                String text="Enter company name";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(Login.this, biggerText, Toast.LENGTH_LONG).show();

            } else if (etEmail.getText().toString().length() < 1 || !validEmail(etEmail.getText().toString())) {
                bool = false;

                String text="Invalid Email. Please Re-enter";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(Login.this, biggerText, Toast.LENGTH_LONG).show();

            }else if (etDriver.getText().toString().length() < 1) {
                bool = false;

                String text="Enter Driver name";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(Login.this, biggerText, Toast.LENGTH_LONG).show();

            }else if (etVehicle.getText().toString().length() < 1) {
                bool = false;

                String text="Enter vehicle number";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(Login.this, biggerText, Toast.LENGTH_LONG).show();
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

}
