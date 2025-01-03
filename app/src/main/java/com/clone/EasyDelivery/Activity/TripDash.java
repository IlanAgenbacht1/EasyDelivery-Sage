package com.clone.EasyDelivery.Activity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.EasyDelivery.Adapter.TripAdapter;
import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.Utility.AppConstant;
import com.clone.EasyDelivery.Utility.ScheduleHelper;
import com.clone.EasyDelivery.Utility.SyncConstant;
import com.clone.EasyDelivery.databinding.ActivityMainBinding;
import com.clone.EasyDelivery.databinding.ActivityTripDashBinding;

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
    int tripCount = 0;

    private @NonNull ActivityTripDashBinding binding;
    private boolean isExpanded = false;

    private Animation fromBottomFabAnim;
    private Animation toBottomFabAnim;
    private Animation rotateClockWiseFabAnim;
    private Animation rotateAntiClockWiseFabAnim;
    private Animation fromBottomBgAnim;
    private Animation toBottomBgAnim;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_dash);

        binding = ActivityTripDashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fromBottomFabAnim = AnimationUtils.loadAnimation(this, R.anim.from_bottom_fab);
        toBottomFabAnim = AnimationUtils.loadAnimation(this, R.anim.to_bottom_fab);
        //rotateClockWiseFabAnim = AnimationUtils.loadAnimation(this, R.anim.rotate_clock_wise);
        //rotateAntiClockWiseFabAnim = AnimationUtils.loadAnimation(this, R.anim.rotate_anti_clock_wise);
        fromBottomBgAnim = AnimationUtils.loadAnimation(this, R.anim.from_bottom_anim);
        toBottomBgAnim = AnimationUtils.loadAnimation(this, R.anim.to_bottom_anim);

        binding.mainFabBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isExpanded) {
                    shrinkFab();
                } else {
                    expandFab();
                }
            }
        });

        binding.fabReturn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                startActivity(new Intent(TripDash.this, ReturnDash.class));

                finish();
            }
        });

        recyclerView = findViewById(R.id.rv_trip);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setVisibility(View.INVISIBLE);

        layout = findViewById(R.id.trip_dash_main);

        title = findViewById(R.id.tv_tripSelection);

        loadingIcon = findViewById(R.id.progressBarTrip);

        logo = findViewById(R.id.iv_logoTrip);

        layoutAnimated = false;

        adapter = new TripAdapter(this, AppConstant.tripList, new TripAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(String tripName) {

                recyclerView.setFocusable(false);

                logo.setVisibility(View.INVISIBLE);
                loadingIcon.setVisibility(View.VISIBLE);

                AppConstant.TRIPID = tripName;

                startTrip(tripName);
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

                    binding.mainFabBtn.startAnimation(fadeIn);
                    //logo.startAnimation(fadeIn);
                    recyclerView.startAnimation(fadeIn);
                    title.startAnimation(fadeIn);

                    title.setText("SELECT TRIP");

                    binding.mainFabBtn.setVisibility(View.VISIBLE);
                    //logo.setVisibility(View.VISIBLE);
                    title.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.VISIBLE);

                    layoutAnimated = true;

                    textHandler.postDelayed(this, 250);

                } else {

                    ScheduleHelper.getLocalTrips(TripDash.this);

                    adapter.notifyDataSetChanged();

                    tripCount++;

                    textHandler.postDelayed(this, 250);
                }
            }
        });
    }


    public void startTrip(String trip) {

        SyncConstant.STARTED_TRIP = AppConstant.TRIPID;

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                ScheduleHelper.getSchedule(TripDash.this, trip);

                startActivity(new Intent(TripDash.this, DashHeader.class));
                finish();
            }
        });

        thread.start();
    }


    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        //super.onBackPressed();

        if (isExpanded) {

            shrinkFab();

        } else {

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



    private void shrinkFab() {
        binding.transparentBg.startAnimation(toBottomBgAnim);
        //binding.mainFabBtn.startAnimation(rotateAntiClockWiseFabAnim);
        //binding.galleryFabBtn.startAnimation(toBottomFabAnim);
        //binding.shareFabBtn.startAnimation(toBottomFabAnim);
        //binding.sendFabBtn.startAnimation(toBottomFabAnim);
        //binding.galleryTv.startAnimation(toBottomFabAnim);
        binding.itemReturn.startAnimation(toBottomFabAnim);
        //binding.sendTv.startAnimation(toBottomFabAnim);

        isExpanded = false;
    }

    private void expandFab() {
        binding.transparentBg.startAnimation(fromBottomBgAnim);
        //binding.mainFabBtn.startAnimation(rotateClockWiseFabAnim);
       // binding.galleryFabBtn.startAnimation(fromBottomFabAnim);
        //binding.shareFabBtn.startAnimation(fromBottomFabAnim);
        //binding.sendFabBtn.startAnimation(fromBottomFabAnim);
        //binding.galleryTv.startAnimation(fromBottomFabAnim);
        binding.itemReturn.startAnimation(fromBottomFabAnim);
        //binding.sendTv.startAnimation(fromBottomFabAnim);

        isExpanded = true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (isExpanded) {
                Rect outRect = new Rect();
                binding.fabConstraint.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    shrinkFab();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}