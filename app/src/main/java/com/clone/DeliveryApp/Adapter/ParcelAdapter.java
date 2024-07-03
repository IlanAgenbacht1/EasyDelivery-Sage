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
import androidx.recyclerview.widget.RecyclerView;

public class ParcelAdapter extends RecyclerView.Adapter<ParcelAdapter.ViewHolder> {


    private Context context;
    private ArrayList<ItemParcel> listItems;
    private DeliveryDb database;
    boolean isSubmitClick = false;



    public ParcelAdapter(Context context, ArrayList<ItemParcel> listItems, DeliveryDb database) {

        this.context = context;
        this.listItems = listItems;
        this.database = database;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_parcel, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, final int position) {


        ItemParcel data = listItems.get(holder.getAdapterPosition());

        TextView numberInput = holder.etNumber;

//        holder.ll_number.setHint(listItems.get(position).getHint());
//        holder.etNumber.setImeOptions(0);
        if (listItems.get(position).getNumber()!=null) {

            holder.rl_main.setVisibility(View.VISIBLE);
            holder.etNumber.setText((holder.getAdapterPosition() + 1) + "." + " " + listItems.get(position).getNumber());

            validateParcel(holder, null);
        }
        else {

            holder.rl_main.setVisibility(View.INVISIBLE);
        }

//        if (holder.etNumber.getText().toString()==null) {
//            if(isSubmitClick){
//                holder.etNumber.setError("please enter a valid input");
//            }
//            data.setTextEmpty(true);
//        } else {
//            data.setTextEmpty(false);
//            //do something else
//        }

        holder.iv_edit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
               if (listItems.get(position).getNumber()!=null){

                   final Dialog dialog = new Dialog(context);
                   dialog.setContentView(R.layout.activity_dialog);

                   TextView textViewUser = (TextView) dialog.findViewById(R.id.textView);
                   final EditText editText = (EditText) dialog.findViewById(R.id.edt_comment);
                   Button buttonSubmit = (Button) dialog.findViewById(R.id.buttonSubmit);
                   Button buttonCancel = (Button) dialog.findViewById(R.id.buttonCancel);

                   textViewUser.setText(listItems.get(position).getHint());
                   editText.setText(listItems.get(position).getNumber());


                   buttonSubmit.setOnClickListener(new OnClickListener() {
                       @Override
                       public void onClick(View view) {

                           ItemParcel itemParcel = new ItemParcel();
                           itemParcel.setDocu(listItems.get(position).getDocu());
                           itemParcel.setHint(listItems.get(position).getHint());
                           itemParcel.setNumber(editText.getText().toString());
                           itemParcel.setPic(listItems.get(position).getPic());
                           itemParcel.setSign(listItems.get(position).getSign());
                           itemParcel.setUnit(listItems.get(position).getUnit());

                           listItems.set(position,itemParcel);

                           notifyDataSetChanged();

                           validateParcel(holder, editText);

                           editText.setEnabled(false);
                           dialog.dismiss();

                           hideKeyboard(editText);
                       }
                   });


                   buttonCancel.setOnClickListener(new OnClickListener() {
                       @Override
                       public void onClick(View view) {

                           dialog.dismiss();


                       }
                   });


                   dialog.show();


//                   final AlertDialog.Builder alert = new AlertDialog.Builder(context);
//
//                   alert.setTitle(listItems.get(position).getHint());
//
//                   final EditText input = new EditText(context);
//                   input.setText(listItems.get(position).getNumber());
//                   alert.setView(input);
//
//                   alert.setPositiveButton("Update", new DialogInterface.OnClickListener() {
//                       public void onClick(DialogInterface dialog, int whichButton) {
//                           String value = input.getText().toString();
//                           // Do something with value!
//
//                           ItemParcel itemParcel = new ItemParcel();
//                           itemParcel.setDocu(listItems.get(position).getDocu());
//                           itemParcel.setHint(listItems.get(position).getHint());
//                           itemParcel.setNumber(input.getText().toString());
//                           itemParcel.setPic(listItems.get(position).getPic());
//                           itemParcel.setSign(listItems.get(position).getSign());
//                           itemParcel.setUnit(listItems.get(position).getUnit());
//
//                           listItems.set(position,itemParcel);
//
//                           notifyDataSetChanged();
//                       }
//                   });
//
//                   alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//                       public void onClick(DialogInterface dialog, int whichButton) {
//                           // Canceled.
//                       }
//                   });
//
//                   alert.show();
               }


            }
        });

    }


    public void validateParcel(ViewHolder holder, EditText editText) {

        boolean valid = false;
        boolean duplicate = false;

        if (!database.isOpen()) {

            database.open();
        }

        Schedule schedule = database.getScheduleData(AppConstant.DOCUMENT);

        String parcelInput;

        if (editText == null) {

            parcelInput = holder.etNumber.getText().toString();

        } else {

            parcelInput = editText.getText().toString();
        }

        parcelInput = parcelInput.substring(3);

        for (String parcel : schedule.getParcelNumbers()) {

            if (parcelInput.equals(parcel)) {

                holder.iv_check.setVisibility(View.VISIBLE);
                holder.iv_cross.setVisibility(View.GONE);

                AppConstant.parcelsValid = true;
                valid = true;

                AppConstant.adapterParcelList.add(parcel);
            }
        }

        if (!valid) {

            holder.iv_cross.setVisibility(View.VISIBLE);
            holder.iv_check.setVisibility(View.GONE);

            AppConstant.parcelsValid = false;

            AppConstant.adapterParcelList.add(parcelInput);
        }
    }


    public void hideKeyboard(View view) {
        try {

            InputMethodManager imm = (InputMethodManager) context.getSystemService(context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

        } catch(Exception ignored) {
        }
    }


    @Override
    public int getItemCount() {
        return listItems.size();
    }


    public class ViewHolder extends RecyclerView.ViewHolder {

        private TextView etNumber;
        private TextInputLayout ll_number;
        private ImageView iv_edit, iv_cross, iv_check;
        private RelativeLayout rl_main;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            etNumber = itemView.findViewById(R.id.tv_number);
            ll_number=itemView.findViewById(R.id.ll_number);
            iv_edit=itemView.findViewById(R.id.iv_edit);
            iv_check = itemView.findViewById(R.id.iv_check);
            iv_cross = itemView.findViewById(R.id.iv_cross);
            rl_main=itemView.findViewById(R.id.rl_main);


//            etNumber.addTextChangedListener(new TextWatcher() {
//                @Override
//                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
//
//                }
//
//                @Override
//                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
//
//                    listItems.get(getAdapterPosition()).setNumber(etNumber.getText().toString());
//                    ((Dash) context).isEntry();
//
//                }
//
//                @Override
//                public void afterTextChanged(Editable editable) {
//
//                }
//            });



        }
    }

//    public void setSubmitClick(boolean isTrue){
//        this.isSubmitClick = isTrue;
//    }

}


