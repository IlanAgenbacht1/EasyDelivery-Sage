package com.clone.DeliveryApp.Adapter;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.clone.DeliveryApp.Database.DeliveryDb;
import com.clone.DeliveryApp.Model.ItemParcel;
import com.clone.DeliveryApp.Model.Schedule;
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

            //validateParcel(holder, null);
        }


    }

    @Override
    public int getItemCount() {
        return listItems.size();
    }


    public void validateParcel(int position, String parcelInput) {

        ViewHolder holder = (ViewHolder) recyclerView.findViewHolderForAdapterPosition(position);

        if (!database.isOpen()) {

            database.open();
        }

        Schedule schedule = database.getScheduleData(AppConstant.DOCUMENT);

        for (String parcel : schedule.getParcelNumbers()) {

            if (parcelInput.equals(parcel)) {

                holder.iv_check.setVisibility(View.VISIBLE);

                AppConstant.adapterParcelList.add(parcel);
            }
        }
    }


    public class ViewHolder extends RecyclerView.ViewHolder {

        private TextView etNumber;
        private TextInputLayout ll_number;
        private ImageView iv_edit, iv_cross, iv_check;
        private ConstraintLayout rl_main;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            etNumber = itemView.findViewById(R.id.tv_number);
            ll_number=itemView.findViewById(R.id.ll_number);
            iv_check = itemView.findViewById(R.id.iv_check);
            rl_main=itemView.findViewById(R.id.rl_main);
        }
    }

}


