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
import com.clone.EasyDelivery.Utility.SyncConstant;
import com.clone.EasyDelivery.Utility.ToastLogger;

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

        } catch (Exception e) {

            e.printStackTrace();

            ToastLogger.exception(getApplicationContext(), e);
        }

        if (deliveryList == null || deliveryList.isEmpty()) {

            ScheduleHelper.deleteTripFile(getApplicationContext(), AppConstant.TRIPID);
            AppConstant.completedTrips.add(AppConstant.TRIPID);

            AlertDialog alertDialog = new AlertDialog.Builder(DashHeader.this, R.style.AlertDialogStyle).create();
            alertDialog.setTitle("Deliveries Completed!");
            alertDialog.setMessage("Returning to trip selection...");

            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    startActivity(new Intent(DashHeader.this, TripDash.class));
                    finish();
                }
            });

            alertDialog.show();
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

                        if (!database.tripStarted(AppConstant.TRIPID)) {

                            SyncConstant.STARTED_TRIP = "";
                            AppConstant.TRIPID = "";

                            Intent intent = new Intent("TripNotStarted");
                            sendBroadcast(intent);

                        } else {

                            SyncConstant.STARTED_TRIP = "";
                            AppConstant.TRIPID = "";
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