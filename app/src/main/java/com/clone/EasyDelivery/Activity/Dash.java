package com.clone.EasyDelivery.Activity;

import android.app.ActionBar;
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
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import androidx.core.widget.NestedScrollView;
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


    private TextView textViewTrip, textViewDocument, textViewCustomer, textViewParcelTitle;

    private Button btnSign, btnPic, btnSave, btnReset;

    private Switch barcodeSwitch;

    private ImageView barcodeImage;

    private RecyclerView recyclerView;

    private ConstraintLayout rl_1, header;

    private RelativeLayout rlRv;

    private ArrayList<String> adapterList;

    private ParcelAdapter adapter;

    private Context context = this;

    private EditText commentEditText;

    private boolean animate = true;

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

    private boolean BARCODE, FLAG;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash);

        btn_next = findViewById(R.id.btn_next);
        barcodeSwitch = findViewById(R.id.switch_barcode);
        barcodeImage = findViewById(R.id.iv_barcode);
        textViewDocument = findViewById(R.id.tv_dashDocTitle);
        textViewCustomer = findViewById(R.id.tv_dashCustomer);
        textViewTrip = findViewById(R.id.tv_dashTripTitle);
        rlTick1 = findViewById(R.id.rl_tick1);
        rlTick2 = findViewById(R.id.rl_tick2);
        enter_num = findViewById(R.id.et_number);
        //ll_number= findViewById(R.id.ll_number);
        rlRv = findViewById(R.id.rl_rv);
        rl_1= findViewById(R.id.rl_1);
        recyclerView = findViewById(R.id.rv);
        parentLayout = findViewById(android.R.id.content);
        btnPic = findViewById(R.id.btn_pic);
        btnSign = findViewById(R.id.btn_sign);
        textViewParcelTitle = findViewById(R.id.tv_parcels);
        header = findViewById(R.id.layout_header);

        adapterList = new ArrayList<>();

        database = new DeliveryDb(context).open();

        linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);

        linearLayoutManager.setSmoothScrollbarEnabled(true);

        adapter = new ParcelAdapter(this, adapterList, recyclerView, new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {

                int startIndex = adapterList.get(i).indexOf(" ") + 1; // finds the start index of the parcel number
                String selectedItem = adapterList.get(i).substring(startIndex);

                runDiscrepancyMode(selectedItem, i);

                return false;
            }
        });

        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setAdapter(adapter);

        AppConstant.validatedParcels.clear();
        AppConstant.uiValidatedParcels.clear();
        AppConstant.flaggedParcels.clear();
        AppConstant.discrepancyParcels.clear();

        barcodeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {

                    btn_next.setText("SCAN BARCODE");

                    enter_num.setFocusable(false);

                    BARCODE = true;

                } else {

                    btn_next.setText("NEXT");

                    enter_num.setFocusable(true); // Re-enable focusability
                    enter_num.setFocusableInTouchMode(true); // Ensure touch focus works
                    enter_num.setClickable(true);
                    enter_num.requestFocus();

                    BARCODE = false;
                }

            }
        });

        btn_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (FLAG) {

                    AlertDialog alertDialog = new AlertDialog.Builder(Dash.this, R.style.AlertDialogStyle).create();
                    alertDialog.setTitle("Confirm");
                    alertDialog.setMessage("Are you sure you want to flag the selected parcels?");

                    alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {

                                    AppConstant.flaggedParcels.addAll(AppConstant.discrepancyParcels);
                                    //adding flagged parcels as validated parcels in order to complete delivery. removed later before inserting data into db.
                                    AppConstant.validatedParcels.addAll(AppConstant.discrepancyParcels);
                                    AppConstant.discrepancyParcels.clear();

                                    for (String item : AppConstant.flaggedParcels) {

                                        adapter.notifyItemChanged(adapterList.indexOf(item));
                                    }

                                    Animation fadeIn = new AlphaAnimation(0, 1);
                                    fadeIn.setInterpolator(new DecelerateInterpolator()); //add this
                                    fadeIn.setDuration(300);
                                    fadeIn.setFillAfter(true);

                                    btnPic.setVisibility(View.VISIBLE);
                                    btnSign.setVisibility(View.VISIBLE);
                                    barcodeSwitch.setVisibility(View.VISIBLE);
                                    barcodeImage.setVisibility(View.VISIBLE);

                                    barcodeImage.startAnimation(fadeIn);
                                    barcodeSwitch.startAnimation(fadeIn);
                                    btnPic.startAnimation(fadeIn);
                                    btnSign.startAnimation(fadeIn);

                                    Spannable text = new SpannableString(getSupportActionBar().getTitle());
                                    text.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.gold, null)), 0, text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                                    getSupportActionBar().setTitle(text);

                                    enter_num.setTextColor(getResources().getColor(R.color.gold, null));
                                    enter_num.setHintTextColor(getResources().getColor(R.color.gold, null));
                                    enter_num.setBackground(getDrawable(R.drawable.parcelinput_border));
                                    enter_num.setHint("Enter Parcel");
                                    enter_num.setFocusable(true);
                                    enter_num.setFocusableInTouchMode(true);

                                    textViewParcelTitle.setTextColor(getResources().getColor(R.color.gold, null));

                                    barcodeSwitch.setVisibility(View.VISIBLE);
                                    barcodeImage.setVisibility(View.VISIBLE);

                                    btn_next.setBackgroundColor(getResources().getColor(R.color.gold, null));
                                    btn_next.setText("NEXT");

                                    if (AppConstant.validatedParcels.size() == deliveryData.getNumberOfParcels()) {

                                        barcodeSwitch.setClickable(false);

                                        btn_next.setTextColor(getResources().getColor(R.color.black, null));
                                        btn_next.setText("Complete Delivery");

                                        enter_num.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                                        enter_num.setHint("PARCELS VALID");
                                        enter_num.setFocusable(false);
                                        enter_num.setCursorVisible(false);

                                        BARCODE = false;
                                    }

                                    FLAG = false;
                                    animate = true;

                                    dialog.dismiss();
                                }
                            }
                    );

                    alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                    dialog.dismiss();
                                }
                            }
                    );

                    alertDialog.show();
                }

                if (BARCODE && !FLAG) {

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

                                Log.w("DEBUG", "input pos: " + i);

                                AppConstant.PARCEL_POSITION = i;
                                AppConstant.PARCEL_INPUT = input;

                                AppConstant.validatedParcels.add(adapterList.get(i));
                                AppConstant.uiValidatedParcels.add(adapterList.get(i));

                                adapter.notifyItemChanged(i);

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

                            displayCommentDialog();
                        }
                    }
            );

            alertDialog.show();

        } else {

            displayCommentDialog();
        }
    }


    public void displayCommentDialog() {

        commentEditText = new EditText(this);
        commentEditText.setTextColor(getResources().getColor(R.color.ic_launcher_background));
        commentEditText.setHintTextColor(getResources().getColor(R.color.gold));

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setTitle("Enter Comment")
                .setView(commentEditText)
                .setCancelable(false)
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                        AppConstant.COMMENT = commentEditText.getText().toString();
                    }

                }).create();

        dialog.show();

        commentEditText.requestFocus();
    }


    public void runDiscrepancyMode(String item, int position) {

        if (!AppConstant.validatedParcels.contains(item)) {

            if (AppConstant.discrepancyParcels.contains(item)) {

                AppConstant.discrepancyParcels.remove(item);

                adapter.notifyItemChanged(position);

            } else {

                AppConstant.discrepancyParcels.add(item);

                adapter.notifyItemChanged(position);
            }
        }

        if (!AppConstant.discrepancyParcels.isEmpty()) {

            FLAG = true;

            if (animate) {

                Animation fadeOut = new AlphaAnimation(1, 0);
                fadeOut.setInterpolator(new DecelerateInterpolator()); //add this
                fadeOut.setDuration(300);
                fadeOut.setFillAfter(true);

                Animation fadeIn = new AlphaAnimation(0, 1);
                fadeIn.setInterpolator(new DecelerateInterpolator()); //add this
                fadeIn.setDuration(300);
                fadeIn.setFillAfter(true);

                btnPic.startAnimation(fadeOut);
                btnSign.startAnimation(fadeOut);
                barcodeImage.startAnimation(fadeOut);
                barcodeSwitch.startAnimation(fadeOut);

                btnPic.setVisibility(View.INVISIBLE);
                btnSign.setVisibility(View.INVISIBLE);
                barcodeSwitch.setVisibility(View.INVISIBLE);
                barcodeImage.setVisibility(View.INVISIBLE);

                Spannable text = new SpannableString(getSupportActionBar().getTitle());
                text.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.red, null)), 0, text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                getSupportActionBar().setTitle(text);

                enter_num.setTextColor(getResources().getColor(R.color.red, null));
                enter_num.setHintTextColor(getResources().getColor(R.color.red, null));
                enter_num.setBackground(getDrawable(R.drawable.parcelinputdiscrepancy_border));
                enter_num.setHint("Select Parcels To Flag");
                enter_num.setFocusable(false);

                textViewParcelTitle.setTextColor(getResources().getColor(R.color.red, null));

                btn_next.setBackgroundColor(getResources().getColor(R.color.red, null));
                btn_next.setText("FLAG");

                animate = false;
            }

        } else {

            FLAG = false;

            if (AppConstant.discrepancyParcels.size() < 2) {

                Animation fadeIn = new AlphaAnimation(0, 1);
                fadeIn.setInterpolator(new DecelerateInterpolator()); //add this
                fadeIn.setDuration(300);
                fadeIn.setFillAfter(true);

                btnPic.setVisibility(View.VISIBLE);
                btnSign.setVisibility(View.VISIBLE);
                barcodeSwitch.setVisibility(View.VISIBLE);
                barcodeImage.setVisibility(View.VISIBLE);

                barcodeImage.startAnimation(fadeIn);
                barcodeSwitch.startAnimation(fadeIn);
                btnPic.startAnimation(fadeIn);
                btnSign.startAnimation(fadeIn);

                Spannable text = new SpannableString(getSupportActionBar().getTitle());
                text.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.gold, null)), 0, text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                getSupportActionBar().setTitle(text);

                enter_num.setTextColor(getResources().getColor(R.color.gold, null));
                enter_num.setHintTextColor(getResources().getColor(R.color.gold, null));
                enter_num.setBackground(getDrawable(R.drawable.parcelinput_border));
                enter_num.setHint("Enter Parcel");
                enter_num.setFocusableInTouchMode(true);
                enter_num.setFocusable(true);

                textViewParcelTitle.setTextColor(getResources().getColor(R.color.gold, null));

                barcodeSwitch.setVisibility(View.VISIBLE);
                barcodeImage.setVisibility(View.VISIBLE);

                btn_next.setBackgroundColor(getResources().getColor(R.color.gold, null));
                btn_next.setText("NEXT");

                animate = true;
            }
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

                            AppConstant.validatedParcels.add(adapterList.get(i));
                            AppConstant.uiValidatedParcels.add(adapterList.get(i));

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

        if (database != null && database.isOpen()) {

            database.close();
        }

        Log.i("Debug", "onDestroy Called");
    }

    protected void onResume() {
        super.onResume();

        Log.i("Debug", "onResume Called");
    }

    protected void onPause() {
        super.onPause();

        Log.i("Debug", "onPause Called");
    }
}



















