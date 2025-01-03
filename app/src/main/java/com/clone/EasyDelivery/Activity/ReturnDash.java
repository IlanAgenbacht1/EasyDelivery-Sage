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
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.EasyDelivery.Adapter.ParcelAdapter;
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

public class ReturnDash extends AppCompatActivity {


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

    public static ReturnDash activity;

    private boolean BARCODE, FLAG;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_return_dash);

        btn_next = findViewById(R.id.btn_next);
        //ll_number= findViewById(R.id.ll_number);
        parentLayout = findViewById(android.R.id.content);
        header = findViewById(R.id.layout_header);

        adapterList = new ArrayList<>();

        database = new DeliveryDb(context).open();

        AppConstant.validatedParcels.clear();
        AppConstant.uiValidatedParcels.clear();
        AppConstant.flaggedParcels.clear();


        btn_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //validation();
            }
        });

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

                    Toast.makeText(ReturnDash.this, "Item already scanned", Toast.LENGTH_LONG).show();

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

            String CompressPath = ImageHelper.compressImage(ReturnDash.this, img_URI, IMAGE_DIRECTORY, SiGN_DIRECTORY);

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

        startActivity(new Intent(this, TripDash.class));
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



















