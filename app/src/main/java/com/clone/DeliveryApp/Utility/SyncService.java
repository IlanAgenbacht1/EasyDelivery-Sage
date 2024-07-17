package com.clone.DeliveryApp.Utility;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.SQLException;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.clone.DeliveryApp.Activity.Dash;
import com.clone.DeliveryApp.Database.DeliveryDb;
import com.clone.DeliveryApp.Model.ItemParcel;
import com.clone.DeliveryApp.R;
import com.clone.DeliveryApp.Service.mAsyncTaskGet;
import com.clone.DeliveryApp.WebService.mServiceUrl;
import com.clone.DeliveryApp.WebService.mWebService;

import java.util.ArrayList;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import static android.content.ContentValues.TAG;
import static com.clone.DeliveryApp.Utility.BackgroundService.CONNECTIVITY_CHANGE_ACTION;

public class SyncService extends IntentService {

    private static int FOREGROUND_ID = 1338;
    public Boolean isServiceRunning = false;
    Integer delay;
    private NotificationManager mgr;

    private ArrayList<ItemParcel> listsync;

    private boolean onceCall = false;

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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

//        Toast.makeText(this, "Service Started", Toast.LENGTH_LONG).show();

        IntentFilter filter = new IntentFilter();

        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String action = intent.getAction();

                if (CONNECTIVITY_CHANGE_ACTION.equals(action)) {

                    //check internet connection

                    if (!ConnectionHelper.isConnectedOrConnecting(context)) {

                        if (context != null) {

                            boolean show = false;

                            if (ConnectionHelper.lastNoConnectionTs == -1) {//first time

                                show = true;

                                ConnectionHelper.lastNoConnectionTs = System.currentTimeMillis();

                            } else {

                                if (System.currentTimeMillis() - ConnectionHelper.lastNoConnectionTs > 1000) {

                                    show = true;
                                    ConnectionHelper.lastNoConnectionTs = System.currentTimeMillis();
                                }
                            }

                            if (show && ConnectionHelper.isOnline) {
                                ConnectionHelper.isOnline = false;
                                Log.i("NETWORK123","Connection lost");

                                LocationHelper.GetLocation(getApplicationContext(), false);

                                onceCall=false;

//                                Toast.makeText(context, "No connection", Toast.LENGTH_SHORT).show();
                                //manager.cancelAll();
                            }
                        }
                    } else {
                        Log.i("NETWORK123","Connected");

//                      Toast.makeText(context, "Connected", Toast.LENGTH_SHORT).show();
                        //DropboxHelper.DownloadFile();

                        LocationHelper.GetLocation(getApplicationContext(), true);

                        if (!onceCall) {

                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {

                                    listsync = getDataSqlite();
                                }
                            }, 360000);


                            onceCall=true;
                        }

                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {

                                    if(listsync.size()>0){

                                        Log.i(TAG, "data in db sync called"+listsync.size());

                                        //performSync(listsync);

                                    }else{

                                        Log.i(TAG, "no data in db sync not called"+listsync.size());
                                    }
                                }
                            },600000);


                        }

//                        startSyncThread();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            startMyOwnForeground();
                        else
                            startForeground(1, new Notification());


                        ConnectionHelper.isOnline = true;
                    }
                }
            
        };
        registerReceiver(receiver,filter);

        return START_STICKY;
    }

    public Boolean isWifiConnected() {

        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo wifiInfo= connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        NetworkInfo mobileInfo=connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        if (wifiInfo.isConnected()){

            return wifiInfo.isConnected();
        }
        else{

            return mobileInfo.isConnected();
        }
    }





    @SuppressLint("StaticFieldLeak")
    public ArrayList<ItemParcel> getDataSqlite(){

        if (isWifiConnected()){

            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {

                    DeliveryDb db=new DeliveryDb(SyncService.this);

                    try{
                        db.open();

                        AppConstant.syncList.addAll(db.getSyncData());

                        db.close();
                    }
                    catch(SQLException e) {

                        Toast.makeText(SyncService.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);


                    Log.i(TAG, "onPostExecute: executed");



                }
            }.execute();



        }


        Log.i(TAG, "getDataSqlite: list size from db"+AppConstant.syncList.size());

        return AppConstant.syncList;


    }


    @SuppressLint("StaticFieldLeak")
    public void performSync(ArrayList<ItemParcel> list) {


        Log.i(TAG, "performSync: db list size"+list.size());

            for (int i = 0;i<list.size();i++){


                String mUrl = "";

                String docuId = "";

                mUrl = Uri.parse(mServiceUrl.BaseURL+"Document="+list.get(i).getDocu()+
                        "&User="+list.get(i).getDriver()+"&Vehicle="+list.get(i).getVehicle()+
                        "&Parcels="+list.get(i).getParcels()+"&Company="+list.get(i).getCompany()+
                        "&DeliveryDateTime="+list.get(i).getTime())
                        .buildUpon()
                        .toString();

                docuId = list.get(i).getDocu();

                if (new mWebService().checkInternetConnection(SyncService.this)) {
                    final String finalDocuId = docuId;
                    new mAsyncTaskGet(SyncService.this, mUrl,  new mAsyncTaskGet.AsyncResponse() {
                        @Override
                        public void processFinish(String output) {
                            try {
                                Log.i(TAG, "processFinish: uploaded from db"+finalDocuId);

                                DeliveryDb db=new DeliveryDb(SyncService.this);

                                try{
                                    db.open();

                                    db.deleteEntryAsRow(finalDocuId);

                                    Log.i(TAG, "processFinish: deleted from db"+finalDocuId);

                                    db.close();

                                    Log.i(TAG, "successfully uploaded: "+finalDocuId);
                                }
                                catch(SQLException e) {

                                    Toast.makeText(SyncService.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    }, false).execute();



                }




            }


            list.clear();
            AppConstant.syncList.clear();

            Log.i(TAG, "list cleared after all recent uploads");

    }

    @Override
    protected void onHandleIntent(Intent intent) {
//        WakefulReceiver.completeWakefulIntent(intent);
    }





    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startMyOwnForeground(){
        String NOTIFICATION_CHANNEL_ID = "com.clone.DeliveryApp";
        String channelName = "My Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.mobile_logo)
                .setContentTitle("App is running in background")
                .setPriority(NotificationManager.IMPORTANCE_NONE)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();


        Intent appActivityIntent = new Intent(this, Dash.class);

        PendingIntent contentAppActivityIntent =
                PendingIntent.getActivity(
                        this,  // calling from Activity
                        0,
                        appActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        notificationBuilder.setContentIntent(contentAppActivityIntent);



        startForeground(2, notification);
    }


    public static void DownloadSchedule(Context context, String companyName) {

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                if (ConnectionHelper.isInternetConnected()) {

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {

                            Toast.makeText(context, "Downloading trip schedule...", Toast.LENGTH_LONG).show();
                        }
                    });

                    DropboxHelper.downloadFile(context, companyName, AppConstant.TRIPID);

                } else {

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {

                            Toast.makeText(context, "Connection failed. Continuing offline.", Toast.LENGTH_LONG).show();
                        }
                    });

                    Log.i("Internet", "No internet connection.");
                }
            }
        });

        try {

            thread.start();
            thread.join();

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}