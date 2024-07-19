package com.clone.DeliveryApp.Activity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.DeliveryApp.Adapter.TripAdapter;
import com.clone.DeliveryApp.R;
import com.clone.DeliveryApp.Utility.AppConstant;
import com.clone.DeliveryApp.Utility.DropboxHelper;
import com.clone.DeliveryApp.Utility.ScheduleHelper;
import com.clone.DeliveryApp.Utility.SyncService;

import java.util.ArrayList;

public class TripDash extends AppCompatActivity {

    RecyclerView recyclerView;
    TripAdapter adapter;
    ArrayList<String> tripList;

    ProgressBar loadingIcon;
    ImageView logo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_dash);

        recyclerView = findViewById(R.id.rv_trip);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadingIcon = findViewById(R.id.progressBarTrip);

        logo = findViewById(R.id.iv_logoTrip);

        tripList = new ArrayList<>();

        for (String item : getFilesDir().list()) {

            if (item.contains(".json")) {

                tripList.add(item.substring(0, item.length() - 5));
            }
        }

        adapter = new TripAdapter(this, tripList, new TripAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(String tripName) {

                //recyclerView.setFocusable(false);

                //logo.setVisibility(View.INVISIBLE);
                //loadingIcon.setVisibility(View.VISIBLE);

                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        AppConstant.TRIP_NAME = tripName;

                        ScheduleHelper.getSchedule(TripDash.this, AppConstant.COMPANY, tripName);

                        startActivity(new Intent(TripDash.this, DashHeader.class));
                        finish();
                    }
                });

                thread.start();
            }
        });

        recyclerView.setAdapter(adapter);
    }


    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        //super.onBackPressed();

        AlertDialog alertDialog = new AlertDialog.Builder(TripDash.this, R.style.AlertDialogStyle).create();

        alertDialog.setTitle("Login");

        alertDialog.setMessage("Return to login screen?");

        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "No",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                        dialog.dismiss();
                    }
                });

        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        startActivity(new Intent(TripDash.this, SplashLogin.class));
                        finish();
                    }
                });

        alertDialog.show();
    }
}