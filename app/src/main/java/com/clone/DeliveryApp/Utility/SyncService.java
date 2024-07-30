package com.clone.DeliveryApp.Utility;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.clone.DeliveryApp.Database.DeliveryDb;
import com.clone.DeliveryApp.Model.Delivery;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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

        if (database != null && database.isOpen()) {

            database.close();
        }
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

        Log.i("SyncService", "onHandleIntent");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {

                Log.i("Timer", "Timer task started");

                syncTripStatus();
                DropboxHelper.downloadAllTrips(getApplicationContext(), AppConstant.COMPANY);
                syncCompletedDelivery();
                syncCompletedTrip();

                Log.i("Timer", "Timer task complete");
            }
        },0, 10000);

        IntentFilter filter = new IntentFilter();

        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addAction("DeliveryCompleted");
        filter.addAction("DeliveryStarted");
        filter.addAction("TripStarted");
        filter.addAction("TripCompleted");
        filter.addAction("TripIncomplete");

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

                                Log.i("SyncService", "Connected");

                                LocationHelper.getLocation(true, getApplicationContext());

                                thread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {

                                        DropboxHelper.downloadAllTrips(getApplicationContext(), AppConstant.COMPANY);

                                        ScheduleHelper.getLocalTrips(getApplicationContext());
                                    }
                                });

                                thread.start();

                            } else {

                                LocationHelper.getLocation(false, getApplicationContext());
                            }

                        } catch (Exception e) {

                            e.printStackTrace();
                        }

                        Log.i("SyncService", "Connectivity action");
                    break;

                    case "TripStarted":

                        Thread thread = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                DropboxHelper.moveTripInProgress();
                            }
                        });

                        thread.start();

                        Log.i("SyncService", "Trip Started");

                    break;

                    case "TripCompleted" :

                        Thread threadTripSync = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                syncCompletedTrip();
                            }
                        });

                        threadTripSync.start();

                        Log.i("SyncService", "Trip Completed");
                    break;

                    case "TripIncomplete":

                        Thread threadIncompleteMove = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                syncTripStatus();
                            }
                        });

                        threadIncompleteMove.start();

                    break;

                    case "DeliveryStarted":

                        Log.i("SyncService", "Delivery Started");
                    break;

                    case "DeliveryCompleted":

                        Thread threadDocumentSync = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                syncCompletedDelivery();
                            }
                        });

                        threadDocumentSync.start();

                        Log.i("SyncService", "Delivery Completed");

                    break;
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            registerReceiver(receiver,filter, Context.RECEIVER_NOT_EXPORTED);
        } else {

            registerReceiver(receiver, filter);
        }

        return START_STICKY;
    }


    private void syncCompletedDelivery() {

        try {

            openDatabase();

            for (String trip : AppConstant.tripList) {

                //iterate through current trips stored on the device

                JSONObject jsonData = JsonHandler.syncReadFile(getApplicationContext(), trip);

                //check if there are completed deliveries for this trip

                List<String> documents = database.getCompletedDocumentList(trip);

                if (!documents.isEmpty()) {

                    for (String document : documents) {

                        //create delivery json and upload to dropbox

                        Delivery delivery = database.getCompletedDocument(document, trip);

                        delivery = database.getCompletedParcels(delivery);

                        String filePath = JsonHandler.writeDeliveryFile(getApplicationContext(), delivery);

                        if (DropboxHelper.uploadCompletedDelivery(getApplicationContext(), filePath, trip, document, delivery.getImagePath(), delivery.getSignPath())) {

                            database.deleteCompletedDocument(document, trip);

                            ImageHelper.syncDeleteImageFiles(getApplicationContext(), delivery.getImagePath(), delivery.getSignPath());
                        }
                    }
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    private void syncCompletedTrip() {

        try {

            openDatabase();

            if (!AppConstant.completedTrips.isEmpty()) {

                for (String trip : AppConstant.completedTrips) {

                    DropboxHelper.moveCompletedTrip(trip);
                }
            }

        } catch(Exception e) {

            e.printStackTrace();
        }
    }


    private void syncTripStatus() {

        try {

            DropboxHelper.moveIncompleteTrip();
            DropboxHelper.moveTripInProgress();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    private void openDatabase() {

        if (database == null) {

            database = new DeliveryDb(getApplicationContext());
            database.open();
        }

        if (!database.isOpen()) {

            database.open();
        }
    }

}