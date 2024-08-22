package com.clone.EasyDelivery.Adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;

import com.clone.EasyDelivery.Database.DeliveryDb;
import com.clone.EasyDelivery.Model.Delivery;
import com.clone.EasyDelivery.R;

import com.clone.EasyDelivery.Utility.AppConstant;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

public class ParcelAdapter extends RecyclerView.Adapter<ParcelAdapter.ViewHolder> {

    private Context context;
    private ArrayList<String> listItems;
    private RecyclerView recyclerView;
    private AdapterView.OnItemLongClickListener listener;


    public ParcelAdapter(Context context, ArrayList<String> listItems, RecyclerView recyclerView, AdapterView.OnItemLongClickListener listener) {

        this.context = context;
        this.listItems = listItems;
        this.recyclerView = recyclerView;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_parcel, parent, false);

        return new ViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, final int position) {

        /*if (listItems.get(position) != null) {

            holder.rl_main.setVisibility(View.VISIBLE);
            holder.etNumber.setText((holder.getAdapterPosition() + 1) + "." + " " + listItems.get(position));
        }

        try {

            if (AppConstant.validatedParcels.contains(holder.etNumber.getText().toString().substring(3))) {


                holder.iv_check.setVisibility(View.VISIBLE);

            } else if (AppConstant.validatedParcels.contains(holder.etNumber.getText().toString().substring(4))) {

                holder.iv_check.setVisibility(View.VISIBLE);

            } else if (AppConstant.validatedParcels.contains(holder.etNumber.getText().toString().substring(5))) {

                holder.iv_check.setVisibility(View.VISIBLE);

            } else {

                holder.iv_check.setVisibility(View.INVISIBLE);
            }

        } catch (Exception e) {

            e.printStackTrace();
        }*/

        if (listItems.get(position) != null) {

            holder.rl_main.setVisibility(View.VISIBLE);
            String itemText = (holder.getAdapterPosition() + 1) + "." + " " + listItems.get(position);
            holder.etNumber.setText(itemText);

            int startIndex = itemText.indexOf(" ") + 1; // finds the start index of the parcel number
            String parcelNumber = itemText.substring(startIndex);

            holder.iv_check.setVisibility(AppConstant.validatedParcels.contains(parcelNumber) ? View.VISIBLE : View.INVISIBLE);
            holder.rl_main.setForeground(AppConstant.discrepancyParcels.contains(parcelNumber) ? context.getDrawable(R.drawable.parcel_discrepancy_border) : null);
        }
    }


    @Override
    public int getItemCount() {
        return listItems.size();
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {

        private TextView etNumber;
        private ImageView iv_check;
        private ConstraintLayout rl_main;

        public ViewHolder(@NonNull View itemView, final AdapterView.OnItemLongClickListener listener) {
            super(itemView);

            etNumber = itemView.findViewById(R.id.tv_number);
            iv_check = itemView.findViewById(R.id.iv_check);
            rl_main=itemView.findViewById(R.id.rl_main);

            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (listener != null) {
                        return listener.onItemLongClick(null, v, getAdapterPosition(), v.getId());
                    }
                    return false;
                }
            });

        }
    }
}


