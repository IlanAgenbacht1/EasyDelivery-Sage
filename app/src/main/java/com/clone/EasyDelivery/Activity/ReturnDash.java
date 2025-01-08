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
import com.clone.EasyDelivery.Model.Return;
import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.Utility.AppConstant;
import com.clone.EasyDelivery.Utility.DropboxHelper;
import com.clone.EasyDelivery.Utility.ImageHelper;
import com.clone.EasyDelivery.Utility.JsonHandler;
import com.clone.EasyDelivery.Utility.LocationHelper;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputLayout;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.kyanogen.signatureview.SignatureView;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReturnDash extends AppCompatActivity {

    private Context context = this;
    View parentLayout;
    private Button btn_next;
    DeliveryDb database;
    Delivery deliveryData;
    public static ReturnDash activity;

    private EditText editTextItem;
    private EditText editTextQty;
    private EditText editTextCustomer;
    private EditText editTextComment;
    private EditText editTextReference;

    private String date;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_return_dash);

        btn_next = findViewById(R.id.btn_next);
        parentLayout = findViewById(android.R.id.content);

        database = new DeliveryDb(context).open();

        editTextItem = findViewById(R.id.et_item);
        editTextQty = findViewById(R.id.et_qty);
        editTextCustomer = findViewById(R.id.et_customer);
        editTextComment = findViewById(R.id.et_comment);
        editTextReference = findViewById(R.id.et_reference);

        btn_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (validation()) {

                    Return returnData = new Return();

                    returnData.setItem(editTextItem.getText().toString().trim());
                    returnData.setQuantity(editTextQty.getText().toString());
                    returnData.setCustomer(editTextCustomer.getText().toString().trim());
                    returnData.setComment(editTextComment.getText().toString().trim());
                    returnData.setReference(editTextReference.getText().toString().trim());

                    date = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US).format(new Date());
                    returnData.setTime(date);

                    database.createReturnEntry(returnData);

                    startActivity(new Intent(ReturnDash.this, TripDash.class));
                    finish();
                }
            }
        });
    }


    public boolean validation() {

        boolean bool = true;

        try {
            if (editTextItem.getText().length() == 0){
                bool = false;
                String text="Enter Item/Parcel";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();
            } else if (editTextQty.getText().length() == 0){
                bool = false;
                String text="Enter Quantity";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();
            } else if (editTextCustomer.getText().length() == 0){
                bool = false;
                String text="Enter Customer";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();
            } else if (editTextComment.getText().length() == 0){
                bool = false;
                String text="Enter Comment/Reason";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();
            } else if (editTextReference.getText().length() == 0){
                bool = false;
                String text="Enter Reference";
                SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
                Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();
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
            }
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



















