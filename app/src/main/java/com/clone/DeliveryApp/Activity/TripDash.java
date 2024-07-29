package com.clone.DeliveryApp.Activity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.DeliveryApp.Adapter.TripAdapter;
import com.clone.DeliveryApp.R;
import com.clone.DeliveryApp.Utility.AppConstant;
import com.clone.DeliveryApp.Utility.DropboxHelper;
import com.clone.DeliveryApp.Utility.ScheduleHelper;
import com.clone.DeliveryApp.Utility.SyncConstant;

import java.util.ArrayList;

public class TripDash extends AppCompatActivity {

    TextView title;
    RecyclerView recyclerView;
    TripAdapter adapter;
    ArrayList<String> tripList;
    ProgressBar loadingIcon;
    ImageView logo;

    ConstraintLayout layout;
    boolean layoutAnimated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_dash);

        recyclerView = findViewById(R.id.rv_trip);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setVisibility(View.INVISIBLE);

        layout = findViewById(R.id.trip_dash_main);

        title = findViewById(R.id.tv_tripSelection);

        loadingIcon = findViewById(R.id.progressBarTrip);

        logo = findViewById(R.id.iv_logoTrip);

        layoutAnimated = false;

        ScheduleHelper.getLocalTrips(TripDash.this);

        adapter = new TripAdapter(this, AppConstant.tripList, new TripAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(String tripName) {

                recyclerView.setFocusable(false);

                logo.setVisibility(View.INVISIBLE);
                loadingIcon.setVisibility(View.VISIBLE);

                AppConstant.TRIPID = tripName;

                startTrip();
            }
        });

        recyclerView.setAdapter(adapter);

        loop();
    }


    public void loop() {

        Handler textHandler = new Handler();
        textHandler.post(new Runnable() {
            @Override
            public void run() {

                if (!AppConstant.tripList.isEmpty() && !layoutAnimated) {

                    loadingIcon.setVisibility(View.INVISIBLE);
                    title.setVisibility(View.INVISIBLE);

                    Animation fadeIn = new AlphaAnimation(0, 1);
                    fadeIn.setInterpolator(new DecelerateInterpolator()); //add this
                    fadeIn.setDuration(1000);
                    fadeIn.setStartOffset(250);

                    logo.startAnimation(fadeIn);
                    recyclerView.startAnimation(fadeIn);
                    title.startAnimation(fadeIn);

                    title.setText("SELECT A TRIP :");

                    logo.setVisibility(View.VISIBLE);
                    title.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.VISIBLE);

                    layoutAnimated = true;

                    textHandler.postDelayed(this, 500);

                } else {

                    ScheduleHelper.getLocalTrips(TripDash.this);

                    adapter.notifyDataSetChanged();

                    textHandler.postDelayed(this, 500);
                }
            }
        });
    }


    public void startTrip() {

        SyncConstant.STARTED_TRIP = AppConstant.TRIPID;

        ScheduleHelper.getSchedule(TripDash.this);

        startActivity(new Intent(TripDash.this, DashHeader.class));
        finish();
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