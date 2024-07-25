package com.clone.DeliveryApp.Utility;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.Nullable;

import com.clone.DeliveryApp.Database.DeliveryDb;
import com.clone.DeliveryApp.Model.Delivery;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class SyncService extends IntentService {

    private boolean connected;

    BroadcastReceiver receiver;

    DeliveryDb database;

    public SyncService() {
        super("SyncService");
    }

    @Override
    public void onStart(@Nullable Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i("SyncService", "Destroyed");

        if (receiver != null) {

            unregisterReceiver(receiver);
        }

        if (database.isOpen()) {
            database.close();
        }
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

        Log.i("SyncService", "onHandleIntent");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        IntentFilter filter = new IntentFilter();

        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addAction("DeliveryCompleted");
        filter.addAction("DeliveryStarted");
        filter.addAction("TripStarted");
        filter.addAction("TripCompleted");

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String action = intent.getAction();

                switch (action) {

                    case "android.net.conn.CONNECTIVITY_CHANGE":
                        try {

                            Thread thread = new Thread(new Runnable() {
                                @Override
                                public void run() {

                                    connected = ConnectionHelper.isInternetConnected();
                                }
                            });

                            thread.start();
                            thread.join();

                            if (connected) {

                                LocationHelper.getLocation(true);

                                thread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {

                                        DropboxHelper.downloadAllTrips(getApplicationContext(), AppConstant.COMPANY);
                                        
                                        ScheduleHelper.getLocalTrips(getApplicationContext());
                                    }
                                });

                                thread.start();

                            } else {

                                LocationHelper.getLocation(false);
                            }

                        } catch (Exception e) {

                            e.printStackTrace();
                        }

                        Log.i("SyncService", "Connectivity action");
                    break;

                    case "TripStarted":

                        SyncConstant.STARTED_TRIP = AppConstant.TRIP_NAME;

                        Thread thread = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                DropboxHelper.moveFileInProgress();
                            }
                        });

                        thread.start();

                        Log.i("SyncService", "Trip Started");
                    break;

                    case "TripCompleted" :

                        Log.i("SyncService", "Trip Completed");
                    break;

                    case "DeliveryStarted":
                        
                        Log.i("SyncService", "Delivery Started");
                    break;

                    case "DeliveryCompleted":

                        Thread threadDocumnetSync = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                syncCompletedDelivery();
                            }
                        });

                        threadDocumnetSync.start();
                        
                        Log.i("SyncService", "Delivery Completed");
                    break;
                }
            }
        };

        registerReceiver(receiver,filter);

        return START_STICKY;
    }


    private void syncCompletedDelivery() {

        database = new DeliveryDb(getApplicationContext());
        database.open();

        try {

            for (String trip : AppConstant.tripList) {

                //iterate through current trips stored on the device

                SyncConstant.TRIP_NAME = trip;
                AppConstant.TRIP_NAME = trip;

                JSONObject jsonData = JsonHandler.readFile(getApplicationContext());

                SyncConstant.TRIP_ID = jsonData.getString("tripId");

                //check if there are completed deliveries for this trip

                List<String> documents = database.getCompletedDocumentList();

                if (!documents.isEmpty()) {

                    for (String document : documents) {

                        //create delivery json and upload to dropbox

                        SyncConstant.DOCUMENT = document;

                        Delivery delivery = database.getCompletedDocument();

                        delivery = database.getCompletedParcels(delivery);

                        JsonHandler.writeDeliveryFile(getApplicationContext(), delivery);

                        DropboxHelper.uploadCompletedDelivery();
                    }
                }
            }

        } catch (JSONException e) {

            throw new RuntimeException(e);
        }
    }
}