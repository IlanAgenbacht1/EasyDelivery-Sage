package com.clone.EasyDelivery.Adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.Utility.AppConstant;
import com.clone.EasyDelivery.Utility.JsonHandler;

import java.util.ArrayList;
import java.util.List;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    private List<String> tripFiles;
    private LayoutInflater inflater;
    private OnItemClickListener listener;
    private Context context;


    public interface OnItemClickListener {
        void onItemClick(String tripNumber);
    }

    // Constructor
    public TripAdapter(Context context, ArrayList<String> tripFiles, OnItemClickListener onItemClickListener) {
        this.tripFiles = tripFiles;
        this.listener = onItemClickListener;
        this.context = context;
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        String tripFile = tripFiles.get(position);

        holder.bind(tripFile, listener);
    }

    @Override
    public int getItemCount() {
        return tripFiles.size();
    }

    // ViewHolder class
    public static class TripViewHolder extends RecyclerView.ViewHolder {
        private TextView tripNumberTextView;
        private TextView textViewDeliveryCount;

        public TripViewHolder(@NonNull View itemView) {
            super(itemView);

            tripNumberTextView = itemView.findViewById(R.id.text_trip_number);
            textViewDeliveryCount = itemView.findViewById(R.id.textViewDeliveryCount);
        }

        public void bind(final String tripName, final TripAdapter.OnItemClickListener listener) {

            Log.i("DeliveryCount", "Binder context: " + itemView.getContext() + " Trip: " + tripName);

            int deliveryCount = 0;

            if (!AppConstant.completedTrips.contains(tripName)) {

                deliveryCount = JsonHandler.returnDeliveryCount(itemView.getContext(), tripName);
            }

            tripNumberTextView.setText(tripName);

            if (deliveryCount > 1) {
                textViewDeliveryCount.setText(deliveryCount + " Deliveries");
            } else {
                textViewDeliveryCount.setText(deliveryCount + " Delivery");
            }

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    listener.onItemClick(tripName);
                }
            });

        }
    }
}
