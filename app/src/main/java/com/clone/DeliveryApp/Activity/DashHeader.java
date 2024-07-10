package com.clone.DeliveryApp.Activity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.DeliveryApp.Adapter.HeaderAdapter;
import com.clone.DeliveryApp.Database.DeliveryDb;
import com.clone.DeliveryApp.Model.Schedule;
import com.clone.DeliveryApp.R;
import com.clone.DeliveryApp.Utility.AppConstant;
import com.clone.DeliveryApp.Utility.ScheduleHelper;

import java.util.ArrayList;
import java.util.List;

public class DashHeader extends AppCompatActivity {

    RecyclerView recyclerView;
    HeaderAdapter adapter;
    List<Schedule> deliveryList;
    DeliveryDb database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dash_header);
        /*ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });*/

        recyclerView = findViewById(R.id.rvDocument);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        deliveryList = new ArrayList<>();

        database = new DeliveryDb(this);
        database.open();

        for (String document : AppConstant.documentList) {

            if (ScheduleHelper.documentExists(database, document, true)) {

                Schedule schedule = database.getScheduleData(document);

                deliveryList.add(schedule);
            }
        }

        adapter = new HeaderAdapter(deliveryList, new HeaderAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Schedule schedule) {

                AppConstant.DOCUMENT = schedule.getDocument();

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

        AlertDialog alertDialog = new AlertDialog.Builder(DashHeader.this).create();

        alertDialog.setTitle("Login");

        alertDialog.setMessage("Return to Login Screen?");

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

                        startActivity(new Intent(DashHeader.this, Login.class));
                        finish();
                    }
                });

        alertDialog.show();
    }
}