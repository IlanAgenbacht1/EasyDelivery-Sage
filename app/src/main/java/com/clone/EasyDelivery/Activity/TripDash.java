package com.clone.EasyDelivery.Activity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
import com.clone.EasyDelivery.Utility.ConnectionHelper;
import com.clone.EasyDelivery.Utility.DropboxHelper;
import com.clone.EasyDelivery.Utility.ScheduleHelper;
import com.clone.EasyDelivery.Utility.SyncConstant;
import com.clone.EasyDelivery.Utility.UnifiedTripManager;
import com.clone.EasyDelivery.databinding.ActivityMainBinding;
import com.clone.EasyDelivery.databinding.ActivityTripDashBinding;

import java.util.ArrayList;
import java.util.Collections;

public class TripDash extends AppCompatActivity {

    TextView title;
    RecyclerView recyclerView;
    public static TripAdapter adapter;
    ProgressBar loadingIcon;
    ImageView logo;
    ConstraintLayout layout;
    boolean layoutAnimated;

    private @NonNull ActivityTripDashBinding binding;
    private boolean isExpanded = false;
    private Handler textHandler;
    private Runnable tripUpdateRunnable;
    private Animation fromBottomFabAnim;
    private Animation toBottomFabAnim;
    private Animation fromBottomBgAnim;
    private Animation toBottomBgAnim;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_dash);

        binding = ActivityTripDashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fromBottomFabAnim = AnimationUtils.loadAnimation(this, R.anim.from_bottom_fab);
        toBottomFabAnim = AnimationUtils.loadAnimation(this, R.anim.to_bottom_fab);
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
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        
        // 🎨 SMOOTH ANIMATIONS: Enable built-in RecyclerView animations
        recyclerView.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
        recyclerView.getItemAnimator().setChangeDuration(300);
        recyclerView.getItemAnimator().setMoveDuration(300);
        recyclerView.getItemAnimator().setAddDuration(400);
        recyclerView.getItemAnimator().setRemoveDuration(300);
        
        Log.i("TripDash", "🎨 RecyclerView animations enabled");

        layout = findViewById(R.id.trip_dash_main);

        title = findViewById(R.id.tv_tripSelection);

        loadingIcon = findViewById(R.id.progressBarTrip);

        logo = findViewById(R.id.iv_logoTrip);

        layoutAnimated = false;

        adapter = new TripAdapter(this, new TripAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(String tripName) {

                // 🚀 INSTANT FEEDBACK: Immediately disable interactions and show loading
                recyclerView.setFocusable(false);
                recyclerView.setEnabled(false); // Prevent multiple taps
                
                // Show loading state immediately
                logo.setVisibility(View.INVISIBLE);
                loadingIcon.setVisibility(View.VISIBLE);
                title.setText("STARTING TRIP " + tripName + "...");
                
                Log.i("TripPerformance", "🚀 INSTANT FEEDBACK: UI updated immediately for trip " + tripName);

                AppConstant.TRIPID = tripName;

                startTrip(tripName);
            }
        });

        recyclerView.setAdapter(adapter);

        textHandler = new Handler(Looper.getMainLooper());
        loop();
    }


    public void loop() {
        tripUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                new Thread(() -> {
                    // Background thread: Fetch trips
                    ArrayList<String> newTrips = ScheduleHelper.getLocalTrips(TripDash.this);

                    // UI thread: Update adapter and UI
                    textHandler.post(() -> {
                        adapter.updateTrips(newTrips);

                        if (!newTrips.isEmpty() && !layoutAnimated) {

                            loadingIcon.setVisibility(View.INVISIBLE);
                            title.setVisibility(View.INVISIBLE);

                            Animation fadeIn = new AlphaAnimation(0, 1);
                            fadeIn.setInterpolator(new DecelerateInterpolator()); //add this
                            fadeIn.setDuration(1000);
                            fadeIn.setStartOffset(250);

                            //binding.mainFabBtn.startAnimation(fadeIn);
                            //logo.startAnimation(fadeIn);
                            //recyclerView.startAnimation(fadeIn);
                            title.startAnimation(fadeIn);

                            title.setText("SELECT TRIP");

                            //binding.mainFabBtn.setVisibility(View.VISIBLE);
                            //logo.setVisibility(View.VISIBLE);
                            title.setVisibility(View.VISIBLE);
                            //recyclerView.setVisibility(View.VISIBLE);

                            layoutAnimated = true;

                        }
                        // Reschedule the next run
                        textHandler.postDelayed(this, 5000);
                    });
                }).start();
            }
        };
        // Start the first run
        textHandler.post(tripUpdateRunnable);
    }


    public void startTrip(String trip) {

        SyncConstant.STARTED_TRIP = AppConstant.TRIPID;

        // 🚀 INSTANT DATA LOADING: Load trip data SYNCHRONOUSLY from local JSON first
        Log.i("TripPerformance", "⚡ INSTANT: Loading trip data synchronously from local JSON");
        long dataLoadStart = System.currentTimeMillis();
        
        // Parse schedule data immediately from local JSON file (no Dropbox operations)
        ScheduleHelper.getSchedule(this, AppConstant.TRIPID);
        
        long dataLoadTime = System.currentTimeMillis() - dataLoadStart;
        Log.i("TripPerformance", "✅ INSTANT: Local trip data loaded in " + dataLoadTime + "ms");
        Log.i("TripPerformance", "📄 INSTANT: Loaded " + AppConstant.documentList.size() + " documents for trip " + AppConstant.TRIPID);

        // 🚀 INSTANT NAVIGATION: Navigate immediately with data loaded
        Log.i("TripPerformance", "⚡ INSTANT: Navigating to DashHeader with trip data ready");
        
        textHandler.removeCallbacksAndMessages(null);
        
        // 🚀 UNIFIED: Use new unified trip manager for instant operations
        Thread backgroundClaimThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i("TripPerformance", "🎯 UNIFIED: Claiming and starting trip with unified manager");
                    
                    UnifiedTripManager tripManager = UnifiedTripManager.getInstance(TripDash.this);
                    
                    // Single call handles claim + start, works online or offline
                    boolean success = tripManager.claimAndStartTrip(AppConstant.TRIPID);
                    
                    if (success) {
                        Log.i("TripPerformance", "✅ UNIFIED: Trip claimed and started successfully (sync automatic)");
                    } else {
                        Log.w("TripPerformance", "⚠️ UNIFIED: Trip claim/start failed - but data already loaded for user");
                    }
                    
                } catch (Exception e) {
                    Log.e("TripPerformance", "Error in unified trip operations", e);
                }
            }
        });
        backgroundClaimThread.start();

        // Navigate immediately (UI already has data loaded synchronously)
        startActivity(new Intent(TripDash.this, DashHeader.class));
        finish();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        textHandler.removeCallbacksAndMessages(null);
    }
    
    // Device ID functionality moved to UnifiedTripManager
}
