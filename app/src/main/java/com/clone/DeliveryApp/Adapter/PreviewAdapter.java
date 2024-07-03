package com.clone.DeliveryApp.Adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.clone.DeliveryApp.Model.ItemParcel;
import com.clone.DeliveryApp.R;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class PreviewAdapter extends RecyclerView.Adapter<PreviewAdapter.ViewHolder> {


    private Context context;
    private ArrayList<ItemParcel> listItems;

    public PreviewAdapter(Context context, ArrayList<ItemParcel> listItems) {
        this.context = context;
        this.listItems = listItems;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_preview,parent,false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {


        holder.tvTitle.setText(""+String.valueOf(position+1)+": ");
        holder.tvNumber.setText(listItems.get(position).getNumber());

    }

    @Override
    public int getItemCount() {
        return listItems.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {


        private TextView tvTitle,tvNumber;


        public ViewHolder(@NonNull View itemView) {
            super(itemView);


            tvNumber = itemView.findViewById(R.id.tv_parcel);
            tvTitle = itemView.findViewById(R.id.tv_title);
        }
    }
}
