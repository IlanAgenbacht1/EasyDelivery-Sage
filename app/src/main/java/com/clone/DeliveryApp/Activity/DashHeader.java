package com.clone.DeliveryApp.Activity;

import android.content.Intent;
import android.os.Bundle;

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

    private RecyclerView recyclerView;
    private HeaderAdapter adapter;
    private List<Schedule> deliveryList;

    private DeliveryDb database;

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
}