package com.clone.EasyDelivery.Activity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.EasyDelivery.Adapter.HeaderAdapter;
import com.clone.EasyDelivery.Database.DeliveryDb;
import com.clone.EasyDelivery.Model.Delivery;
import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.Utility.AppConstant;
import com.clone.EasyDelivery.Utility.ScheduleHelper;
// SyncConstant merged into AppConstant
import com.clone.EasyDelivery.Utility.ToastLogger;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class DashHeader extends AppCompatActivity {

    RecyclerView recyclerView;
    HeaderAdapter adapter;
    List<Delivery> deliveryList;
    DeliveryDb database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dash_header);

        recyclerView = findViewById(R.id.rvDocument);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        deliveryList = new ArrayList<>();

        database = new DeliveryDb(this);
        database.open();

        try {

            for (String document : AppConstant.documentList) {

                if (ScheduleHelper.documentValid(database, document, true)) {

                    Delivery delivery = database.getDeliveryData(document);

                    deliveryList.add(delivery);
                }
            }

            database.close();

        } catch (Exception e) {

            e.printStackTrace();

            ToastLogger.exception(getApplicationContext(), e);
        }

        if (deliveryList == null || deliveryList.isEmpty()) {
            Log.i("DashHeader", "DeliveryList is empty - trip data should have been loaded by TripDash");
            Log.w("DashHeader", "AppConstant.documentList size: " + AppConstant.documentList.size());
            
            // If somehow the data is not loaded, load it synchronously now
            if (AppConstant.TRIPID != null && !AppConstant.TRIPID.trim().isEmpty()) {
                Log.i("DashHeader", "⚡ INSTANT FALLBACK: Loading trip data synchronously");
                long fallbackStart = System.currentTimeMillis();
                
                ScheduleHelper.getSchedule(this, AppConstant.TRIPID);
                
                // Reload delivery list after schedule loading
                loadDeliveryListFromDatabase();
                
                long fallbackTime = System.currentTimeMillis() - fallbackStart;
                Log.i("DashHeader", "✅ INSTANT FALLBACK: Trip data loaded in " + fallbackTime + "ms");
            }
        }

        adapter = new HeaderAdapter(deliveryList, new HeaderAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Delivery delivery) {

                AppConstant.DOCUMENT = delivery.getDocument();

                startActivity(new Intent(DashHeader.this, Dash.class));

                finish();
            }
        });

        recyclerView.setAdapter(adapter);
        
        Log.i("DashHeader", "✅ INSTANT: Trip " + AppConstant.TRIPID + " ready with " + deliveryList.size() + " deliveries");
        
        // Background trip state verification (non-blocking)
        if (AppConstant.TRIPID != null && !AppConstant.TRIPID.trim().isEmpty()) {
            verifyTripStateInBackground();
        }
    }
    
    /**
     * ⚡ INSTANT: Load delivery list from database synchronously
     */
    private void loadDeliveryListFromDatabase() {
        try {
            deliveryList.clear();
            
            database = new DeliveryDb(this);
            database.open();
            
            for (String document : AppConstant.documentList) {
                if (ScheduleHelper.documentValid(database, document, true)) {
                    Delivery delivery = database.getDeliveryData(document);
                    deliveryList.add(delivery);
                }
            }
            
            database.close();
            
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            
            Log.i("DashHeader", "✅ INSTANT: Loaded " + deliveryList.size() + " deliveries from database");
            
        } catch (Exception e) {
            Log.e("DashHeader", "Error loading delivery list from database", e);
        }
    }
    
    /**
     * 🎯 BACKGROUND: Verify trip state in background (non-blocking)
     */
    private void verifyTripStateInBackground() {
        Thread verifyThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    com.clone.EasyDelivery.Utility.UnifiedTripManager tripManager = 
                        com.clone.EasyDelivery.Utility.UnifiedTripManager.getInstance(DashHeader.this);
                    
                    // The unified trip manager handles all state automatically
                    // Just trigger a sync to ensure everything is up to date
                    tripManager.forceSync();
                    Log.i("DashHeader", "🎯 BACKGROUND: Trip sync completed for: " + AppConstant.TRIPID);
                    
                } catch (Exception e) {
                    Log.e("DashHeader", "Error in background trip state verification", e);
                }
            }
        });
        
        verifyThread.start();
    }
    

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        //super.onBackPressed();

        AlertDialog alertDialog = new AlertDialog.Builder(DashHeader.this, R.style.AlertDialogStyle).create();

        alertDialog.setTitle("Trip Selection");

        alertDialog.setMessage("Return to trip selection screen?");

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

                        database.open();

                        if (!database.tripStarted(AppConstant.TRIPID)) {
                            
                            // 🚀 INSTANT CLEANUP: If user backs out without starting deliveries,
                            // immediately clean up the trip claim for better multi-device UX
                            String tripToCleanup = AppConstant.TRIPID;
                            
                            // 🎯 Use unified trip manager for clean trip release
                            if (tripToCleanup != null && !tripToCleanup.isEmpty()) {
                                Log.i("DashHeader", "🎯 UNIFIED: User backed out, releasing trip: " + tripToCleanup);
                                
                                Thread cleanupThread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            com.clone.EasyDelivery.Utility.UnifiedTripManager tripManager = 
                                                com.clone.EasyDelivery.Utility.UnifiedTripManager.getInstance(DashHeader.this);
                                            
                                            boolean success = tripManager.releaseTrip(tripToCleanup);
                                            
                                            if (success) {
                                                Log.i("DashHeader", "✅ UNIFIED: Trip " + tripToCleanup + " released successfully");
                                            } else {
                                                Log.w("DashHeader", "⚠️ UNIFIED: Trip release had issues (will sync later)");
                                            }
                                        } catch (Exception e) {
                                            Log.e("DashHeader", "Error in trip release", e);
                                        }
                                    }
                                });
                                cleanupThread.start();
                            }

                            AppConstant.STARTED_TRIP = "";
                            AppConstant.TRIPID = "";
                            
                            Log.i("DashHeader", "🎯 Trip cleanup completed via direct UnifiedTripManager call");
                            // Note: Trip release already handled above via tripManager.releaseTrip()

                        } else {

                            AppConstant.STARTED_TRIP = "";
                            AppConstant.TRIPID = "";
                        }

                        if (database != null && database.isOpen()) {

                            database.close();
                        }

                        startActivity(new Intent(DashHeader.this, TripDash.class));
                        finish();
                    }
                });

        alertDialog.show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (database != null && database.isOpen()) {

            database.close();
        }
    }
}
