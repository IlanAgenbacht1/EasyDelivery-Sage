package com.clone.DeliveryApp.Adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.clone.DeliveryApp.Database.DeliveryDb;
import com.clone.DeliveryApp.Model.Delivery;
import com.clone.DeliveryApp.R;

import com.clone.DeliveryApp.Utility.AppConstant;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

public class ParcelAdapter extends RecyclerView.Adapter<ParcelAdapter.ViewHolder> {

    private Context context;
    private ArrayList<String> listItems;
    private DeliveryDb database;
    boolean isSubmitClick = false;
    private RecyclerView recyclerView;


    public ParcelAdapter(Context context, ArrayList<String> listItems, DeliveryDb database, RecyclerView recyclerView) {

        this.context = context;
        this.listItems = listItems;
        this.database = database;
        this.recyclerView = recyclerView;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_parcel, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, final int position) {

        if (listItems.get(position) != null) {

            holder.rl_main.setVisibility(View.VISIBLE);
            holder.etNumber.setText((holder.getAdapterPosition() + 1) + "." + " " + listItems.get(position));
        }

        if (AppConstant.PARCEL_VALIDATION) {

            holder.iv_check.setVisibility(View.VISIBLE);

            AppConstant.PARCEL_VALIDATION = false;
        }
    }

    @Override
    public int getItemCount() {
        return listItems.size();
    }


    public boolean validParcel(int position, String parcelInput, Delivery delivery) {

        boolean valid = false;



        return valid;
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {

        private TextView etNumber;
        private ImageView iv_check;
        private ConstraintLayout rl_main;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            etNumber = itemView.findViewById(R.id.tv_number);
            iv_check = itemView.findViewById(R.id.iv_check);
            rl_main=itemView.findViewById(R.id.rl_main);
        }
    }
}


