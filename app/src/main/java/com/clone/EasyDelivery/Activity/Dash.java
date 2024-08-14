package com.clone.EasyDelivery.Activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.EasyDelivery.Adapter.ParcelAdapter;
//import com.clone.EasyDelivery.BuildConfig;
import com.clone.EasyDelivery.Database.DeliveryDb;
import com.clone.EasyDelivery.Model.Delivery;
import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.Utility.AppConstant;
import com.clone.EasyDelivery.Utility.ImageHelper;
import com.clone.EasyDelivery.Utility.LocationHelper;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputLayout;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.kyanogen.signatureview.SignatureView;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class Dash extends AppCompatActivity {


    private TextView textViewTrip, textViewDocument, textViewCustomer;

    private Button btnSign, btnPic, btnSave, btnReset;

    private Switch barcodeSwitch;

    private RecyclerView recyclerView;

    private RelativeLayout rlRv,rl_1;

    private Spinner spinnerDoc;

    private ArrayAdapter<String> spinnerAdapter;

    private ArrayList<String> spinnerList;

    private ArrayList<String> adapterList;

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
    String tempPicturePath;
    public int img_isthere = 0;
    public static final int REQUEST_CAPTURE = 7;
    File file;
    String img_URI;

    private boolean isSign=false,isPic=false, deliveryStarted;
    private RelativeLayout rlTick1,rlTick2;
    private TextInputLayout ll_number;
    String signImagePath;
    private EditText enter_num;
    DeliveryDb database;
    Delivery deliveryData;

    int VALIDATION_DISTANCE = 50;

    public static Dash activity;

    private boolean BARCODE;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash);

        btn_next = findViewById(R.id.btn_next);

        barcodeSwitch = findViewById(R.id.switch_barcode);

        textViewDocument = findViewById(R.id.tv_dashDocTitle);
        textViewCustomer = findViewById(R.id.tv_dashCustomer);
        textViewTrip = findViewById(R.id.tv_dashTripTitle);

       // btnFinish = findViewById(R.id.btn_finish);

        rlTick1 = findViewById(R.id.rl_tick1);
        rlTick2 = findViewById(R.id.rl_tick2);

        enter_num = findViewById(R.id.et_number);
        ll_number= findViewById(R.id.ll_number);

        //btnReset = findViewById(R.id.bt);

        rlRv = findViewById(R.id.rl_rv);
        rl_1= findViewById(R.id.rl_1);
        recyclerView = findViewById(R.id.rv);

        parentLayout = findViewById(android.R.id.content);

        btnPic = findViewById(R.id.btn_pic);
        btnSign = findViewById(R.id.btn_sign);

        adapterList = new ArrayList<>();

        database = new DeliveryDb(context).open();

        adapter = new ParcelAdapter(this, adapterList, database, recyclerView);

        linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);

        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setAdapter(adapter);

        /*if (recyclerView.getChildCount() == adapterList.size()){

            linearLayoutManager.setStackFromEnd(true);
        }
        else{

            linearLayoutManager.setStackFromEnd(false);
        }*/

        AppConstant.validatedParcels.clear();

        barcodeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {

                    btn_next.setText("SCAN BARCODE");

                    enter_num.setFocusable(false);

                    BARCODE = true;

                } else {

                    btn_next.setText("NEXT");

                    enter_num.setFocusable(true);

                    BARCODE = false;
                }

            }
        });

        btn_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (BARCODE) {

                    IntentIntegrator integrator = new IntentIntegrator(Dash.this);

                    integrator.setPrompt("Scan Barcode");
                    integrator.setBeepEnabled(true);

                    integrator.initiateScan();
                }

                String input = enter_num.getText().toString();

                if (AppConstant.validatedParcels.size() == deliveryData.getNumberOfParcels()) {

                    if (validation()){

                        AppConstant.PARCEL_NO = String.valueOf(adapterList.size());

                        startActivity(new Intent(Dash.this, Preview.class));
                    }

                } else if (!BARCODE) {

                    rl_1.setVisibility(View.VISIBLE);

                    if (AppConstant.validatedParcels.contains(input)) {

                        Toast.makeText(Dash.this, "Item already entered", Toast.LENGTH_LONG).show();

                    } else {

                        for (int i = 0; i < adapterList.size(); i++) {

                            if (input.equalsIgnoreCase(adapterList.get(i))) {

                                AppConstant.PARCEL_VALIDATION = true;

                                adapter.notifyItemChanged(i);

                                AppConstant.validatedParcels.add(input);

                                if (AppConstant.validatedParcels.size() == deliveryData.getNumberOfParcels()) {

                                    barcodeSwitch.setClickable(false);

                                    btn_next.setTextColor(getResources().getColor(R.color.black, null));
                                    btn_next.setText("Complete Delivery");

                                    enter_num.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                                    enter_num.setHint("PARCELS VALID");
                                    enter_num.setFocusable(false);
                                    enter_num.setCursorVisible(false);
                                }
                            }
                        }
                    }

                    enter_num.setText("");
                }
            }
        });


        btnSign.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (adapterList.size() > 0 && textViewDocument.getText().length() > 0){

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

                if (adapterList.size()>0){

                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){

                        file = null;
                        long tsLong = System.currentTimeMillis() / 1000;

                        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        file = new File(getFilesDir() + IMAGE_DIRECTORY + "/DeliveryImage/");

                        if (!file.exists()) {

                            file.mkdirs();
                        }

                        file = new File(file, tsLong + ".jpg");
                        img_URI = "file:" + file.getAbsolutePath();
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(Dash.this,
                            "com.clone.EasyDelivery" + ".provider",
                            file));

                        startActivityForResult(intent,REQUEST_CAPTURE);

                    }else {

                        file = null;
                        long tsLong = System.currentTimeMillis() / 1000;
                        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        file = new File(getFilesDir() + IMAGE_DIRECTORY + "/DeliveryImage/");

                        if (!file.exists()) {

                            file.mkdirs();
                        }

                        file = new File(file,  tsLong + ".jpg");
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

        getAndDisplayData();
        validateLocation();
    }


    public void getAndDisplayData() {

        textViewDocument.setText(AppConstant.DOCUMENT);

        setScheduleData(AppConstant.DOCUMENT);

        textViewCustomer.setText(deliveryData.getCustomerName());

        textViewTrip.setText(AppConstant.TRIPID);

        adapterList.addAll(deliveryData.getParcelNumbers());

        adapter.notifyDataSetChanged();
    }


    public void setScheduleData(String document) {

        if (!database.isOpen()) {

            database.open();
        }

        deliveryData = database.getDeliveryData(document);
    }


    public void validateLocation() {

        AppConstant.GPS_LOCATION = LocationHelper.returnClosestCoordinate(deliveryData.getLocation(), context);

        if (!LocationHelper.isWithinDistance(deliveryData.getLocation(), VALIDATION_DISTANCE)) {

            AlertDialog alertDialog = new AlertDialog.Builder(Dash.this, R.style.AlertDialogStyle).create();

            alertDialog.setTitle("Location Mismatch");

            alertDialog.setMessage("Your current location is more than " + VALIDATION_DISTANCE + "m from " + deliveryData.getCustomerName() + ". Return to delivery selection?");

            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Return",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {

                            startActivity(new Intent(Dash.this, DashHeader.class));
                            finish();
                        }
                    }
            );

            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Continue Delivery",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            dialog.dismiss();
                        }
                    }
            );

            alertDialog.show();
        }
    }


    public boolean validation() {

        boolean bool = false;

        try {

            if (textViewDocument.getText().length() == 0){
                bool = false;
                String text="Enter Document Number";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();

              //  Toast.makeText(context, "Enter Document Number", Toast.LENGTH_LONG).show();
            }
            else if (!(adapterList.size()>0)){

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

            for (String parcel : deliveryData.getParcelNumbers()) {

                for (int i = 0; i < adapterList.size(); i++) {

                    String inputParcel = adapterList.get(i);

                    if (parcel.equals(inputParcel)) {

                        inputParcels.add(adapterList.get(i));
                    }
                }
            }

            if (inputParcels.size() != adapterList.size()) {

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
                    path = ImageHelper.saveImage(Dash.this, bitmap, IMAGE_DIRECTORY, SiGN_DIRECTORY);
                    AppConstant.SIGN_PATH = path;
                    dialog.dismiss();
                    rlTick1.setVisibility(View.VISIBLE);
                    isSign = true;
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


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (result != null) {

            if (result.getContents() == null) {

                Log.d("Barcode Scanner", "Cancelled scan");

            } else {

                String input = result.getContents();

                rl_1.setVisibility(View.VISIBLE);

                enter_num.setText(input);

                if (AppConstant.validatedParcels.contains(input)) {

                    Toast.makeText(Dash.this, "Item already scanned", Toast.LENGTH_LONG).show();

                } else {

                    for (int i = 0; i < adapterList.size(); i++) {

                        if (input.equalsIgnoreCase(adapterList.get(i))) {

                            AppConstant.PARCEL_VALIDATION = true;

                            adapter.notifyItemChanged(i);

                            AppConstant.validatedParcels.add(input);

                            if (AppConstant.validatedParcels.size() == deliveryData.getNumberOfParcels()) {

                                barcodeSwitch.setChecked(false);
                                barcodeSwitch.setClickable(false);

                                btn_next.setTextColor(getResources().getColor(R.color.black, null));
                                btn_next.setText("Complete Delivery");

                                enter_num.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                                enter_num.setHint("PARCELS VALID");
                                enter_num.setFocusable(false);
                                enter_num.setCursorVisible(false);
                            }
                        }
                    }
                }

                enter_num.setText("");
            }
        }

        if (resultCode == RESULT_OK && requestCode == REQUEST_CAPTURE) {

            rlTick2.setVisibility(View.VISIBLE);
            isPic = true;

            img_isthere = 1;
            imageType = 2;

            String CompressPath = ImageHelper.compressImage(Dash.this, img_URI, IMAGE_DIRECTORY, SiGN_DIRECTORY);

            signImagePath = CompressPath;

            ImagefileUri = Uri.parse(CompressPath);

            file.delete();
        }
    }


    private SpannableStringBuilder enlargedText (String text) {

        SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
        biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);

        return biggerText;
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();

        ImageHelper.deleteImageFiles();

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



















