package com.clone.EasyDelivery.Activity;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.util.Base64;
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
import com.kyanogen.signatureview.SignatureView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class Dash extends AppCompatActivity {

    private TextView textViewTrip, textViewDocument, textViewCustomer, textViewParcelTitle;

    private Button btnSign, btnPic, btnSave, btnReset;

    private RecyclerView recyclerView;

    private ConstraintLayout rl_1, header;

    private RelativeLayout rlRv;

    private ArrayList<String> adapterList;

    private ParcelAdapter adapter;

    private Context context = this;

    private EditText commentEditText;

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

    private boolean isSign = false, isPic = false;
    private boolean isQtyValidated = false;
    private RelativeLayout rlTick1,rlTick2;
    private TextInputLayout ll_number;
    String signImagePath;
    private EditText enter_num;
    DeliveryDb database;
    Delivery deliveryData;

    int VALIDATION_DISTANCE = 50;

    public static Dash activity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash);

        btn_next = findViewById(R.id.btn_next);
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

        adapter = new ParcelAdapter(this, adapterList, recyclerView, null);

        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setAdapter(adapter);

        btn_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String inputQtyStr = enter_num.getText().toString();

                if (isQtyValidated) {
                    if (validation()) {
                        AppConstant.PARCEL_NO = String.valueOf(deliveryData.getNumberOfParcels());
                        AppConstant.validatedParcels.clear();
                        AppConstant.validatedParcels.addAll(deliveryData.getParcelNumbers());
                        startActivity(new Intent(Dash.this, Preview.class));
                    }
                } else {
                    if (inputQtyStr.isEmpty()) {
                        Toast.makeText(Dash.this, "Please enter parcel quantity", Toast.LENGTH_LONG).show();
                        return;
                    }

                    int inputQty = 0;
                    try {
                        inputQty = Integer.parseInt(inputQtyStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(Dash.this, "Invalid number format", Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (inputQty > 0) {
                        isQtyValidated = true;

                        deliveryData.setNumberOfParcels(inputQty);

                        btn_next.setTextColor(getResources().getColor(R.color.black, null));
                        btn_next.setText("Complete Delivery");

                        String parcelText = (inputQty == 1) ? inputQty + " item delivered" : inputQty + " items delivered";
                        enter_num.setText(parcelText);
                        enter_num.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                        enter_num.setHint("");
                        enter_num.setFocusable(false);
                        enter_num.setCursorVisible(false);

                        Toast.makeText(Dash.this, "Parcel quantity validated", Toast.LENGTH_SHORT).show();

                    } else {
                        Toast.makeText(Dash.this, "Please enter a valid quantity", Toast.LENGTH_LONG).show();
                    }
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
        textViewParcelTitle.setText("Parcels");
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
                    try {
                        // Capture signature bitmap
                        bitmap = signatureView.getSignatureBitmap();

                        // Generate AES key
                        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                        keyGen.init(256);
                        SecretKey secretKey = keyGen.generateKey();

                        // Initialize cipher
                        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(new byte[16])); // Use secure IV in production

                        // Convert bitmap to byte array
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                        byte[] bitmapData = baos.toByteArray();

                        // Encrypt data
                        byte[] encryptedData = cipher.doFinal(bitmapData);

                        // Save encrypted data to file
                        path = ImageHelper.saveEncryptedImage(Dash.this, encryptedData, IMAGE_DIRECTORY, SiGN_DIRECTORY);
                        AppConstant.SIGN_PATH = path;

                        // Securely store the key (e.g., Android Keystore or secure server)
                        // Example: Save key to SharedPreferences (not secure, use Keystore in production)
                        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
                        prefs.edit().putString("signature_key", Base64.encodeToString(secretKey.getEncoded(), Base64.DEFAULT)).apply();

                        dialog.dismiss();
                        rlTick1.setVisibility(View.VISIBLE);
                        isSign = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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


    // Enhanced method to securely store encryption key
    private void storeEncryptionKey(SecretKey key) {
        try {
            // Store key in SharedPreferences (consider using Android Keystore for better security)
            SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
            String encodedKey = android.util.Base64.encodeToString(key.getEncoded(), android.util.Base64.DEFAULT);
            prefs.edit().putString("signature_key", encodedKey).apply();

            Log.d("SIGNATURE_DEBUG", "Encryption key stored securely");
        } catch (Exception e) {
            Log.e("SIGNATURE_ERROR", "Failed to store encryption key: " + e.getMessage(), e);
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
