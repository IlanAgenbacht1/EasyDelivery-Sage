package com.clone.EasyDelivery.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.EasyDelivery.Model.Delivery;
import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.Utility.AppConstant;

import java.util.List;

public class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.CustomerViewHolder> {

    private List<Delivery> documents;
    private OnItemClickListener listener;
    private ImageView imageViewExclamation;

    public interface OnItemClickListener {
        void onItemClick(Delivery delivery);
    }

    public HeaderAdapter(List<Delivery> documents, OnItemClickListener listener) {
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
        Delivery delivery = documents.get(position);
        holder.bind(delivery, listener);
    }

    @Override
    public int getItemCount() {
        return documents.size();
    }

    public static class CustomerViewHolder extends RecyclerView.ViewHolder {
        private TextView documentNumberTextView;
        private TextView customerNameTextView;
        private TextView customerAddressTextView;
        private ImageView imageViewExclamation;

        public CustomerViewHolder(@NonNull View itemView) {
            super(itemView);
            documentNumberTextView = itemView.findViewById(R.id.text_document_number);
            customerNameTextView = itemView.findViewById(R.id.text_customer_name);
            customerAddressTextView = itemView.findViewById(R.id.text_customer_address);
            imageViewExclamation = itemView.findViewById(R.id.iv_exclamation);
        }

        public void bind(final Delivery delivery, final OnItemClickListener listener) {
            documentNumberTextView.setText(delivery.getDocument());
            customerNameTextView.setText(delivery.getCustomerName());
            customerAddressTextView.setText(delivery.getAddress());

            if (AppConstant.SAVED_DOCUMENT != null && AppConstant.SAVED_DOCUMENT.equals(delivery.getDocument())) {

                itemView.setBackgroundResource(R.drawable.dash_border_red);
            }
            else {

                itemView.setBackgroundResource(R.drawable.dash_border);
            }

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    listener.onItemClick(delivery);
                }
            });
        }
    }
}