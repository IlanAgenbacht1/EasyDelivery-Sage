package com.clone.DeliveryApp.Adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.DeliveryApp.R;

import java.util.ArrayList;
import java.util.List;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    private List<String> tripFiles;
    private LayoutInflater inflater;
    private OnItemClickListener listener;


    public interface OnItemClickListener {
        void onItemClick(String tripNumber);
    }

    // Constructor
    public TripAdapter(Context context, ArrayList<String> tripFiles, OnItemClickListener onItemClickListener) {
        this.tripFiles = tripFiles;
        this.listener = onItemClickListener;
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

        public TripViewHolder(@NonNull View itemView) {
            super(itemView);

            tripNumberTextView = itemView.findViewById(R.id.text_trip_number);
        }

        public void bind(final String tripName, final TripAdapter.OnItemClickListener listener) {

            tripNumberTextView.setText(tripName);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    listener.onItemClick(tripName);
                }
            });

        }
    }
}
