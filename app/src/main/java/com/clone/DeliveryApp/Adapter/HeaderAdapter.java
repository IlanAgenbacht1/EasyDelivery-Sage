package com.clone.DeliveryApp.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.DeliveryApp.Model.Schedule;
import com.clone.DeliveryApp.R;

import java.util.List;

import okhttp3.internal.http2.Header;

public class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.CustomerViewHolder> {

    private List<Schedule> documents;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Schedule schedule);
    }

    public HeaderAdapter(List<Schedule> documents, OnItemClickListener listener) {
        this.documents = documents;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CustomerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_header, parent, false);
        return new CustomerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CustomerViewHolder holder, int position) {
        Schedule schedule = documents.get(position);
        holder.bind(schedule, listener);
    }

    @Override
    public int getItemCount() {
        return documents.size();
    }

    public static class CustomerViewHolder extends RecyclerView.ViewHolder {
        private TextView documentNumberTextView;
        private TextView customerNameTextView;
        private TextView customerAddressTextView;

        public CustomerViewHolder(@NonNull View itemView) {
            super(itemView);
            documentNumberTextView = itemView.findViewById(R.id.text_document_number);
            customerNameTextView = itemView.findViewById(R.id.text_customer_name);
            customerAddressTextView = itemView.findViewById(R.id.text_customer_address);
        }

        public void bind(final Schedule schedule, final OnItemClickListener listener) {
            documentNumberTextView.setText(schedule.getDocument());
            customerNameTextView.setText(schedule.getCustomerName());
            customerAddressTextView.setText(schedule.getAddress());

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onItemClick(schedule);
                }
            });
        }
    }
}