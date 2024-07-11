package com.clone.DeliveryApp.Activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.DeliveryApp.Adapter.ParcelAdapter;
//import com.clone.DeliveryApp.BuildConfig;
import com.clone.DeliveryApp.Database.DeliveryDb;
import com.clone.DeliveryApp.Model.ItemParcel;
import com.clone.DeliveryApp.Model.Schedule;
import com.clone.DeliveryApp.R;
import com.clone.DeliveryApp.Utility.AppConstant;
import com.clone.DeliveryApp.Utility.LocationHelper;
import com.clone.DeliveryApp.Utility.ScheduleHelper;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputLayout;
import com.kyanogen.signatureview.SignatureView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class Dash extends AppCompatActivity {

    private EditText etDocu, etParcel,etNo;

    private Button btnSign, btnPic, btnSave, btnReset;

    private RecyclerView recyclerView;

    private RelativeLayout rlRv,rl_1;

    private Spinner spinnerDoc;

    private ArrayAdapter<String> spinnerAdapter;

    private ArrayList<String> spinnerList;

    private ArrayList<ItemParcel> listItems;

    private ParcelAdapter adapter;

    private Context context = this;

    private boolean isIncre = false;

    LinearLayoutManager linearLayoutManager;
    View parentLayout;

    private static final String TAG = "Dash";

    Bitmap bitmap;

    int last_entered=0;

    String path;
    private static final String IMAGE_DIRECTORY = "/DeliveryApp";

    private static final String SiGN_DIRECTORY = "/DeliverySignature";
    private static final String PIC_DIRECTORY = "/DeliveryImages";


    private Button btn_next;
    private int imageType = 0;
    private Uri ImagefileUri;
    String currentPicturePath;
    public int img_isthere = 0;
    public static final int REQUEST_CAPTURE = 7;
    File file;
    Uri fileUri;
    String img_URI;

    private boolean isSign=false,isPic=false;

    int arrylist_length=0;
    int current_position=0;
    private Button btnAdd,btnRemove;

    private RelativeLayout rlTick1,rlTick2;
    private TextInputLayout ll_number;

    String signImagePath;

    private EditText enter_num;
    int no_of_parcels=0;

    DeliveryDb database;
    Schedule schedule;

    Button btnFinish;

    public static Activity dashActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash);

        dashActivity = this;

        btn_next = findViewById(R.id.btn_next);

        etDocu = findViewById(R.id.et_docu);
        spinnerDoc = findViewById(R.id.spinnerDocument);

        etNo = findViewById(R.id.et_parcel_no);

       // btnFinish = findViewById(R.id.btn_finish);

        rlTick1 = findViewById(R.id.rl_tick1);
        rlTick2 = findViewById(R.id.rl_tick2);

        enter_num = findViewById(R.id.et_number);
        ll_number= findViewById(R.id.ll_number);

        btnAdd = findViewById(R.id.btn_add);
        btnRemove = findViewById(R.id.btn_minus);
        btnReset = findViewById(R.id.buttonReset);

        rlRv = findViewById(R.id.rl_rv);
        rl_1= findViewById(R.id.rl_1);
        recyclerView = findViewById(R.id.rv);

        parentLayout = findViewById(android.R.id.content);

        //ivPic = findViewById(R.id.iv_pic);

        btnPic = findViewById(R.id.btn_pic);
        btnSign = findViewById(R.id.btn_sign);
        btnSave = findViewById(R.id.btn_delivery);

        spinnerList = new ArrayList<>();
        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spinnerList);
        spinnerDoc.setAdapter(spinnerAdapter);

        listItems = new ArrayList<>();

        database = new DeliveryDb(context).open();

        adapter = new ParcelAdapter(this, listItems, database);

        linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);

        Button defaultBtn = new Button(this);

        btnAdd.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.button)));
        btnRemove.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.button)));

        btnAdd.setEnabled(false);
        btnRemove.setEnabled(false);

        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setAdapter(adapter);

        if (recyclerView.getChildCount() == listItems.size()){

            linearLayoutManager.setStackFromEnd(true);
        }
        else{

            linearLayoutManager.setStackFromEnd(false);
        }

        btn_next.setEnabled(false);

        AppConstant.adapterParcelList.clear();


        etNo.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    // do your stuff here


                }
                return false;
            }
        });


        etNo.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {



            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {



//                listItems.clear();
            }


            @Override
            public void afterTextChanged(Editable s) {

                if (TextUtils.isEmpty(s.toString().trim())) {

                } else {

                    no_of_parcels= Integer.parseInt(s.toString());
                }

                if (!etNo.getText().toString().isEmpty() && (!etNo.getText().toString().equalsIgnoreCase("0"))) {
                    //etNo.setEnabled(false);

                    btnAdd.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.theme)));
                    btnRemove.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.theme)));

                    btnAdd.setEnabled(true);
                    btnRemove.setEnabled(true);

                    if (!isIncre) {

                        try {

                            //listItems.clear();
                            rlRv.setVisibility(View.VISIBLE);

                            int i = no_of_parcels;

                            arrylist_length = i;

                            for (int j = 0; j < i; j++) {

                                listItems.add(new ItemParcel("PARCEL " + (j + 1)));
                                adapter.notifyDataSetChanged();
                            }

                            current_position = 1;
                            last_entered = current_position;
                            ll_number.setHint("Enter Parcel # " + (current_position));
                            btn_next.setEnabled(true);

                        } catch (NumberFormatException e) {

                            rlRv.setVisibility(View.GONE);

                            listItems.clear();
                            adapter.notifyDataSetChanged();
                        }
                    }
                }

            }
        });

        btn_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                boolean duplicate = false;

                rl_1.setVisibility(View.VISIBLE);

                if (!enter_num.getText().toString().isEmpty()){

                    for (String existingParcel : AppConstant.adapterParcelList) {

                        if (enter_num.getText().toString().equals(existingParcel)) {

                            duplicate = true;
                        }
                    }

                    if (current_position<=arrylist_length && !duplicate){

                        if (current_position==arrylist_length) {

                            listItems.get(current_position - 1).setNumber(enter_num.getText().toString());

                            adapter.notifyDataSetChanged();
                            current_position += 1;
                            enter_num.setText("");

                            ll_number.setHint("All Parcel #s Entered");
                            enter_num.setEnabled(false);
                            enter_num.clearFocus();
                            last_entered = current_position;

                            btnSign.setTextColor(getResources().getColor(android.R.color.white));
                            btnSign.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.button_select)));

                        }else {

                            enter_num.setEnabled(true);

                            enter_num.clearFocus();

                            Log.d(TAG, "onClick: " + current_position + "  " + arrylist_length);

                            listItems.get(current_position - 1).setNumber(enter_num.getText().toString());
                            Log.d(TAG, "onClick: " + listItems.get(current_position - 1).getNumber().toString());

                            adapter.notifyDataSetChanged();
                            current_position += 1;

                            ll_number.setHint("Enter Parcel #" + String.valueOf(current_position));

                            enter_num.setText("");

                            last_entered = current_position;
                        }
                    }

                }else {

                    Toast.makeText(context, "Enter Parcel Details", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Log.d(TAG, "onClick: "+current_position);

                if (etNo.getText().toString().length()>0){

                    enter_num.setEnabled(true);
                    ll_number.setHint("Enter Parcel # "+(last_entered));
                    btn_next.setVisibility(View.VISIBLE);

                    //       btnFinish.setVisibility(View.INVISIBLE);
                    enter_num.setText("");
                    enter_num.clearFocus();
                    enter_num.setEnabled(true);
                    isIncre =true;
                    arrylist_length +=1;
                    listItems.add(new ItemParcel("PARCEL " + (current_position)));
                    adapter.notifyDataSetChanged();
                    etNo.setText(String.valueOf(listItems.size()));

                    btnSign.setTextColor(getResources().getColor(R.color.darkgray));
                    btnSign.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.button)));
                }else {

                    String text="Enter no of Parcels";
                    SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                    biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                    Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();

                  //  Toast.makeText(context, "Enter no of Parcels", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (etNo.getText().toString().length()>0 && arrylist_length>1){

                    isIncre =true;
                    Log.d(TAG, "onClick: "+arrylist_length);

                    listItems.remove(listItems.size() - 1);

                    if (listItems.get(0).getNumber()== null){

                        current_position=listItems.size();
                    }else {

                        current_position = listItems.size() + 1;
                    }

                    arrylist_length -=1;
                    adapter.notifyDataSetChanged();
                    etNo.setText(String.valueOf(listItems.size()));
                    Log.d(TAG, "onClick: "+current_position+"  "+arrylist_length);
                    if (listItems.get(listItems.size()-1).getNumber()== null ){

                        ll_number
                            .setHint("Enter Parcel # " + String.valueOf(last_entered));

                        enter_num.setText("");
                        enter_num.setEnabled(true);
                        btnSign.setTextColor(getResources().getColor(R.color.darkgray));
                        btnSign.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.button)));

                    }else{

                        last_entered = listItems.size()+1;
                        ll_number
                            .setHint("All Parcel #s Entered");
                        enter_num.setText("");
                        enter_num.setEnabled(false);
                        btnSign.setTextColor(getResources().getColor(android.R.color.white));
                        btnSign.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.button_select)));
                    }

                }else {

//                    Toast.makeText(context, "Enter no of Parcels", Toast.LENGTH_SHORT).show();
                }

                if (listItems.size()<1){

                    btnSign.setTextColor(getResources().getColor(R.color.black));
                }
            }
        });

        btnSign.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                  if (isDataValid() && listItems.size()>0 && etDocu.getText().toString().length()>0){
                      ViewDialog alert = new ViewDialog();
                      alert.showDialog(Dash.this);
                  }else {

                      String text="Enter All Parcel Details";
                      SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                      biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                      Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();

                      //Toast.makeText(context, "Enter All Parcel Details", Toast.LENGTH_LONG).show();
                  }
            }
        });

        btnPic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (isDataValid() && listItems.size()>0){
                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                        file = null;
                        long tsLong = System.currentTimeMillis() / 1000;

                        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        file = new File(getFilesDir(), "Pic" + tsLong + ".jpg");
                        img_URI = "file:" + file.getAbsolutePath();
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(Dash.this,
                            "com.clone.DeliveryApp" + ".provider",
                            file));

                        startActivityForResult(intent,REQUEST_CAPTURE);
                    }else {
                        file = null;
                        long tsLong = System.currentTimeMillis() / 1000;
                        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        file = new File(getFilesDir(), "Pic" + tsLong + ".jpg");
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(file));
                        img_URI = "" + Uri.fromFile(file);
                        startActivityForResult(intent,REQUEST_CAPTURE);
                    }
                }else {
                    String text="Enter All Parcel Details";
                    SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                    biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                    Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();

                   // Toast.makeText(context, "Enter All Parcel Details", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (validation()){

                    isDataValid();

                    AppConstant.PARCEL_NO = String.valueOf(listItems.size());
                    AppConstant.PIC_PATH = currentPicturePath;
                    AppConstant.SIGN_PATH = path;
                    AppConstant.parcelList = listItems;

                    // Validate location
                    AppConstant.GPS_LOCATION = LocationHelper.returnClosestCoordinate(schedule.getLocation(), context);

                    int validationDistance = 50;

                    if (LocationHelper.isWithinDistance(schedule.getLocation(), validationDistance)) {

                        Toast.makeText(Dash.this, "Location within " + validationDistance + "m of " + schedule.getCustomerName(), Toast.LENGTH_LONG).show();

                        database.close();

                        startActivity(new Intent(Dash.this,Preview.class));

                    } else {

                        AlertDialog alertDialog = new AlertDialog.Builder(Dash.this).create();

                        alertDialog.setTitle("Location Mismatch");

                        alertDialog.setMessage("Your current location is more than " + validationDistance + "m from " + schedule.getCustomerName() + ".");

                        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Continue",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {

                                        dialog.dismiss();

                                        database.close();

                                        startActivity(new Intent(Dash.this,Preview.class));
                                    }
                                });

                        alertDialog.show();
                    }
                }
            }
        });

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                deleteImageFiles();

                recyclerView.removeAllViews();
                AppConstant.adapterParcelList.clear();
                //AppConstant.gpsList.clear();
                listItems.clear();
                adapter.notifyDataSetChanged();

                if (AppConstant.DOCUMENT.equals(AppConstant.SAVED_DOCUMENT)) {

                    AppConstant.SAVED_DOCUMENT = null;
                    AppConstant.SAVED_PARCELS.clear();
                }

                etDocu.setText(AppConstant.DOCUMENT);

                setScheduleData(AppConstant.DOCUMENT);

                etNo.setText(String.valueOf(schedule.getNumberOfParcels()));

                rlTick1.setVisibility(View.INVISIBLE);
                rlTick2.setVisibility(View.INVISIBLE);

                btnSign.setBackgroundColor(getResources().getColor(R.color.button));
                btnSign.setTextColor(getResources().getColor(android.R.color.black));

                btnPic.setBackgroundColor(getResources().getColor(R.color.button));
                btnPic.setTextColor(getResources().getColor(android.R.color.black));

                btnSave.setBackgroundColor(getResources().getColor(R.color.button));
                btnSave.setTextColor(getResources().getColor(R.color.black));

                enter_num.setEnabled(true);
                enter_num.requestFocus();
            }
        });

        spinnerDoc.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                recyclerView.removeAllViews();
                AppConstant.adapterParcelList.clear();
                //AppConstant.gpsList.clear();
                listItems.clear();
                adapter.notifyDataSetChanged();



                enter_num.setEnabled(true);
                enter_num.requestFocus();

                btnSign.setBackgroundColor(getResources().getColor(R.color.button));
                btnSign.setTextColor(getResources().getColor(android.R.color.black));

                btnPic.setBackgroundColor(getResources().getColor(R.color.button));
                btnPic.setTextColor(getResources().getColor(android.R.color.black));

                btnSave.setBackgroundColor(getResources().getColor(R.color.button));
                btnSave.setTextColor(getResources().getColor(R.color.black));

                /*for (String parcel : schedule.getParcelNumbers()) {

                    listItems.get(current_position - 1).setNumber(parcel);

                    current_position += 1;
                }

                adapter.notifyDataSetChanged();*/
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        getAndDisplayData();
    }


    public void getAndDisplayData() {

        if (AppConstant.SAVED_DOCUMENT != null && AppConstant.SAVED_DOCUMENT.equals(AppConstant.DOCUMENT)) {

            etDocu.setText(AppConstant.SAVED_DOCUMENT);
            etDocu.setFocusable(false);
            etDocu.setCursorVisible(false);

            setScheduleData(AppConstant.SAVED_DOCUMENT);

            etNo.setText(String.valueOf(schedule.getNumberOfParcels()));

            listItems.clear();

            for (int i = 0; i < AppConstant.SAVED_PARCELS.size(); i++) {

                listItems.add(AppConstant.SAVED_PARCELS.get(i));
            }

            adapter.notifyDataSetChanged();
        }
        else {

            etDocu.setText(AppConstant.DOCUMENT);
            etDocu.setFocusable(false);
            etDocu.setCursorVisible(false);

            setScheduleData(AppConstant.DOCUMENT);

            etNo.setText(String.valueOf(schedule.getNumberOfParcels()));
        }
    }


    public void setScheduleData(String document) {

        if (!database.isOpen()) {

            database.open();
        }

        schedule = database.getScheduleData(document);
    }


    public boolean isDataValid(){

        for(int i = 0; i < listItems.size(); i++){

            ItemParcel data = listItems.get(i);

            if(data.getNumber() == null || data.getNumber().equalsIgnoreCase("")){

                Log.d(TAG, "isDataValid: "+data.getNumber());
                adapter.notifyItemChanged(i);

                return false;
            }
        }

        return true;
        //do something here as all data is valid
    }


    public boolean isEntry(){

        for(int i = 0; i<listItems.size(); i++){
            ItemParcel data = listItems.get(i);
            if(data.getNumber() == null || data.getNumber().equalsIgnoreCase("") ){

                btnSign.setTextColor(getResources().getColor(R.color.black));

                return false;
            }
        }

        btnSign.setTextColor(getResources().getColor(android.R.color.white));
        btnSign.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.button_select)));

        return true;
        //do something here as all data is valid
    }


    public boolean validation() {

        boolean bool = false;

        try {

            if (etDocu.getText().toString().length() == 0){
                bool = false;
                String text="Enter Document Number";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();

              //  Toast.makeText(context, "Enter Document Number", Toast.LENGTH_LONG).show();
            }
            else if (!(isDataValid() && listItems.size()>0)){

                String text="Enter All Parcel Details";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();

               // Toast.makeText(context, "Enter All Parcel Details", Toast.LENGTH_LONG).show();
            }
            else if (!isPic) {
                //user input empty then set bool false
                bool = false;
                String text="Capture Document Image";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();

               // Toast.makeText(context, "Capture Document Image", Toast.LENGTH_LONG).show();
            }
            else if (!isSign) {
                bool = false;
                //Toast.makeText(context, "Capture Signature", Toast.LENGTH_LONG).show();

                String text="Capture Signature";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();

            }
//            else if (!isDataValid()) {
//                bool = false;
//                Snackbar.make(parentLayout, "enter all parcel details", Snackbar.LENGTH_LONG).show();
//            }
            else {

                String text="To be moved to details screen WIP";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
//                Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();


               // Toast.makeText(context, "To be moved to details screen WIP", Toast.LENGTH_SHORT).show();
                //user input not empty so set bool true
                bool = true;
            }


            List<String> inputParcels = new ArrayList<>();

            for (String parcel : schedule.getParcelNumbers()) {

                for (int i = 0; i < listItems.size(); i++) {

                    String inputParcel = listItems.get(i).getNumber();

                    if (parcel.equals(inputParcel)) {

                        inputParcels.add(listItems.get(i).getNumber());
                    }
                }
            }

            if (inputParcels.size() != listItems.size()) {

                Toast.makeText(this, "Invalid parcel number!", Toast.LENGTH_LONG).show();

                bool = false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            bool = false;
        }

        return bool;
    }


    public class ViewDialog {

        public void showDialog(Activity activity) {

            final BottomSheetDialog dialog = new BottomSheetDialog(Dash.this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            dialog.setCancelable(true);
            dialog.setContentView(R.layout.signature_dialog);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            Button clear, save;
            final SignatureView signatureView;
            RelativeLayout llClose;

            clear = (Button) dialog.findViewById(R.id.clear);
            save = (Button) dialog.findViewById(R.id.save);
            llClose = dialog.findViewById(R.id.llTop);

            signatureView = dialog.findViewById(R.id.signature_view);

            clear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    signatureView.clearCanvas();
                }
            });

            llClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    dialog.dismiss();
                }
            });

            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bitmap = signatureView.getSignatureBitmap();
                    path = saveImage(bitmap);
                    dialog.dismiss();
                    rlTick1.setVisibility(View.VISIBLE);
                    isSign = true;
                    btnPic.setTextColor(getResources().getColor(android.R.color.white));
                    btnPic.setBackgroundColor(getResources().getColor(R.color.button_select));

                }
            });

            try {
                Field behaviorField = dialog.getClass().getDeclaredField("behavior");
                behaviorField.setAccessible(true);
                final BottomSheetBehavior behavior = (BottomSheetBehavior) behaviorField.get(dialog);
                behavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {

                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        if (newState == BottomSheetBehavior.STATE_DRAGGING) {
                            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                        }
                    }

                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                    }
                });
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            dialog.show();
        }
    }


    public String saveImage(Bitmap myBitmap) {

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        myBitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes);

        File wallpaperDirectory = new File(this.getFilesDir() + IMAGE_DIRECTORY + SiGN_DIRECTORY);
        // have the object build the directory structure, if needed.
        if (!wallpaperDirectory.exists()) {
            wallpaperDirectory.mkdirs();
            Log.d("hhhhh", wallpaperDirectory.toString());
        }

        try {
            File f = new File(wallpaperDirectory, Calendar.getInstance()
                    .getTimeInMillis() + ".jpg");
            f.createNewFile();
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(bytes.toByteArray());
            MediaScannerConnection.scanFile(Dash.this,
                    new String[]{f.getPath()},
                    new String[]{"image/jpeg"}, null);
            fo.close();
            Log.d("TAG", "File Saved::--->" + f.getAbsolutePath());

            return f.getAbsolutePath();

        } catch (IOException e1) {
            e1.printStackTrace();
        }

        return "";
    }


    public String compressImage(String imageUri) {

        String filePath = getRealPathFromURI(imageUri);
        Bitmap scaledBitmap = null;

        BitmapFactory.Options options = new BitmapFactory.Options();

//      by setting this field as true, the actual bitmap pixels are not loaded in the memory. Just the bounds are loaded. If
//      you try the use the bitmap here, you will get null.
        options.inJustDecodeBounds = true;
        Bitmap bmp = BitmapFactory.decodeFile(filePath, options);

        int actualHeight = options.outHeight;
        int actualWidth = options.outWidth;

//      max Height and width values of the compressed image is taken as 816x612

        if (actualHeight != 0 && actualWidth != 0) {
            float maxHeight = 816.0f;
            float maxWidth = 612.0f;
            float imgRatio = actualWidth / actualHeight;
            float maxRatio = maxWidth / maxHeight;

//      width and height values are set maintaining the aspect ratio of the image

            if (actualHeight > maxHeight || actualWidth > maxWidth) {
                if (imgRatio < maxRatio) {
                    imgRatio = maxHeight / actualHeight;
                    actualWidth = (int) (imgRatio * actualWidth);
                    actualHeight = (int) maxHeight;
                } else if (imgRatio > maxRatio) {
                    imgRatio = maxWidth / actualWidth;
                    actualHeight = (int) (imgRatio * actualHeight);
                    actualWidth = (int) maxWidth;
                } else {
                    actualHeight = (int) maxHeight;
                    actualWidth = (int) maxWidth;

                }
            }

//      setting inSampleSize value allows to load a scaled down version of the original image

            options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight);

//      inJustDecodeBounds set to false to load the actual bitmap
            options.inJustDecodeBounds = false;

//      this options allow android to claim the bitmap memory if it runs low on memory
            options.inPurgeable = true;
            options.inInputShareable = true;
            options.inTempStorage = new byte[16 * 1024];

            try {
//          load the bitmap from its path
                bmp = BitmapFactory.decodeFile(filePath, options);
            } catch (OutOfMemoryError exception) {
                exception.printStackTrace();

            }
            try {
                scaledBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError exception) {
                exception.printStackTrace();
            }

            float ratioX = actualWidth / (float) options.outWidth;
            float ratioY = actualHeight / (float) options.outHeight;
            float middleX = actualWidth / 2.0f;
            float middleY = actualHeight / 2.0f;

            android.graphics.Matrix scaleMatrix = new android.graphics.Matrix();
            scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

            Canvas canvas = new Canvas(scaledBitmap);
            canvas.setMatrix(scaleMatrix);
            canvas.drawBitmap(bmp, middleX - bmp.getWidth() / 2, middleY - bmp.getHeight() / 2, new Paint(Paint.FILTER_BITMAP_FLAG));

//      check the rotation of the image and display it properly
            ExifInterface exif;
            try {
                exif = new ExifInterface(filePath);

                int orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, 0);
                Log.d("EXIF", "Exif: " + orientation);
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                if (orientation == 6) {
                    matrix.postRotate(90);
                    Log.d("EXIF", "Exif: " + orientation);
                } else if (orientation == 3) {
                    matrix.postRotate(180);
                    Log.d("EXIF", "Exif: " + orientation);
                } else if (orientation == 8) {
                    matrix.postRotate(270);
                    Log.d("EXIF", "Exif: " + orientation);
                }
                scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0,
                        scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix,
                        true);
            } catch (IOException e) {
                e.printStackTrace();
            }

            FileOutputStream out = null;
            String filename = getFilename();

            currentPicturePath = filename;
//            imagePath = getFilename();
            try {
                out = new FileOutputStream(filename);

//          write the compressed bitmap at the destination specified by filename.
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            return filename;

        } else {
            return null;
        }
    }


    private String getRealPathFromURI(String contentURI) {

        Uri contentUri = Uri.parse(contentURI);
        Cursor cursor = context.getContentResolver().query(contentUri, null, null, null, null);
        if (cursor == null) {

            return contentUri.getPath();
        }
        else {

            cursor.moveToFirst();
            int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);

            return cursor.getString(index);
        }
    }


    public String getFilename() {

        File file = new File(this.getFilesDir().getPath(), IMAGE_DIRECTORY+PIC_DIRECTORY);

        if (!file.exists()) {
            file.mkdirs();
        }

        String uriSting = (file.getAbsolutePath() + "/" + System.currentTimeMillis() + ".jpg");

        return uriSting;
    }


    public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {

        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        final float totalPixels = width * height;
        final float totalReqPixelsCap = reqWidth * reqHeight * 2;
        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++;
        }

        return inSampleSize;
    }


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        if (resultCode == RESULT_OK && requestCode == REQUEST_CAPTURE ) {

            rlTick2.setVisibility(View.VISIBLE);
            isPic = true;
            btnSave.setTextColor(getResources().getColor(android.R.color.white));
            btnSave.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.button_select)));

            img_isthere = 1;
            imageType = 2;


            //StoreUserImageUp(img_URI);
//
//
//
//            ViewDialog2 alert = new ViewDialog2();
//            alert.showDialog(Dash.this);
//
//            ivPic.setImageBitmap(BitmapFactory.decodeFile(compressImage(img_URI)));
            String CompressPath = compressImage(img_URI);

            signImagePath = CompressPath;

//            picBite=getBytes(BitmapFactory.decodeFile(CompressPath));

            ImagefileUri = Uri.parse(CompressPath);
        }
    }

    public void deleteImageFiles() {
        if (currentPicturePath != null) {

            File pictureFile = new File(currentPicturePath);

            if (pictureFile.exists()) {

                pictureFile.delete();
            }
        }

        if (path != null) {

            File signFile = new File(path);

            if (signFile.exists()) {

                signFile.delete();
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        deleteImageFiles();

        String parcelNumber = listItems.get(0).getNumber();

        if (parcelNumber != null) {

            AppConstant.SAVED_DOCUMENT = AppConstant.DOCUMENT;
            AppConstant.SAVED_PARCELS = listItems;
        }

        startActivity(new Intent(this, DashHeader.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.i("Debug", "onDestroy Called");
    }

    protected void onResume() {
        super.onResume();

        Log.i("Debug", "onResume Called");
    }

    protected void onPause() {
        super.onPause();

        Log.i("Debug", "onPause Called");

        database.close();
    }
}



















