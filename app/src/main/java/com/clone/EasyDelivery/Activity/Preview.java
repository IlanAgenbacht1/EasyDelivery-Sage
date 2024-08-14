package com.clone.EasyDelivery.Activity;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Window;
import android.widget.RelativeLayout;

import android.widget.RelativeLayout.LayoutParams;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.clone.EasyDelivery.Adapter.PreviewAdapter;
//import com.clone.EasyDelivery.BuildConfig;
import com.clone.EasyDelivery.Database.DeliveryDb;
import com.clone.EasyDelivery.Model.ItemParcel;
import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.Utility.AppConstant;
import com.google.gson.Gson;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class Preview extends AppCompatActivity {
    private TextView tvCompany,tvDriver,tvVehicle,tvDate,tvTime,tvDocu,tvParcels;
    private Button btnBack,btnConfirm;
    private RecyclerView recyclerView;
    private String date_final,time_final,currentDate;

    private PreviewAdapter adapter;
     private RelativeLayout rl_sign_view,rl_view_parcel;
    private ItemParcel itemParcel;

    String result,result1;

    private Context context;

//    private ImageView ivSign,ivPic;
    ArrayList<String> parcelId;

    ArrayList<String> filePaths;
    String  strList;

    private TextView tvPic,tvSign;

    String sign, pic;

    private static final String TAG = "Preview";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        recyclerView = findViewById(R.id.rv_parcel);
        rl_sign_view= findViewById(R.id.rl_sign_view);
        rl_view_parcel= findViewById(R.id.rl_view_parcel);
        tvPic = findViewById(R.id.tv_pic);
        tvSign = findViewById(R.id.tv_sign);


        Log.d(TAG, "onCreate: pic"+AppConstant.PIC_PATH);
        Log.d(TAG, "onCreate: sign"+AppConstant.SIGN_PATH);


        btnBack = findViewById(R.id.btn_back);
        btnConfirm = findViewById(R.id.btn_confirm);

        tvCompany = findViewById(R.id.tv_com);
        tvDriver = findViewById(R.id.tv_driver);
        tvVehicle = findViewById(R.id.tv_vehicle);
        tvDate = findViewById(R.id.tv_date);
        tvTime = findViewById(R.id.tv_time);
        tvDocu = findViewById(R.id.tv_docu);
        tvParcels = findViewById(R.id.tv_parcel);


        adapter = new PreviewAdapter(this, AppConstant.validatedParcels);


        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(adapter);


        tvCompany.setText(GetCompany());

        tvDriver.setText(GetDriver());

        tvVehicle.setText(GetVehicle());


        currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime());


        Log.d(TAG, "onCreate:current "+currentDate);


        tvDate.setText(new SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(new Date()));

        tvTime.setText(new SimpleDateFormat("HH:mm", Locale.US).format(new Date())+" Hours");


        tvDocu.setText(AppConstant.DOCUMENT);
        tvParcels.setText(AppConstant.PARCEL_NO);


        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });


        result = AppConstant.SIGN_PATH.substring(AppConstant.SIGN_PATH.lastIndexOf('/') + 1).trim();
        tvSign.setText("View Here");
        result1 = AppConstant.PIC_PATH.substring(AppConstant.PIC_PATH.lastIndexOf('/') + 1).trim();

        tvPic.setText("View Here");


        rl_sign_view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                AppConstant.ZOOM=AppConstant.SIGN_PATH;
                startActivity(new Intent(Preview.this,ZoomView.class));

            }
        });


        rl_view_parcel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                AppConstant.ZOOM=AppConstant.PIC_PATH;
                startActivity(new Intent(Preview.this,ZoomView.class));

            }
        });


        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View view) {

                strList = TextUtils.join(", ",  AppConstant.validatedParcels);
                strList = strList.replaceAll("\\s"," ");
                System.out.println(strList);

                updateDatabase();

                email();
            }
        });
    }


    private void updateDatabase() {

        try{
            DeliveryDb db =new DeliveryDb(Preview.this);

            db.open();

            itemParcel = new ItemParcel();

            itemParcel.setDocu(tvDocu.getText().toString());
            itemParcel.setPic(AppConstant.PIC_PATH);
            itemParcel.setSign(AppConstant.SIGN_PATH);
            itemParcel.setUnit(tvParcels.getText().toString());

            Gson gson = new Gson();

            String parcelString= gson.toJson(parcelId);

            System.out.println("inputString= " + parcelString);

            itemParcel.setParcels(strList);
            itemParcel.setDriver(tvDriver.getText().toString());
            itemParcel.setVehicle(tvVehicle.getText().toString());
            itemParcel.setCompany(tvCompany.getText().toString());
            itemParcel.setTime(currentDate);

            String imageFile = result1.substring(0, result1.length() - 4);

            String signatureFile = result.substring(0, result.length() - 4);

            SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyyyy - HHmmss", Locale.getDefault());

            String date = dateFormat.format(new Date());

            db.setDocumentCompleted(itemParcel.getDocu(), imageFile, signatureFile, date, this);

            db.close();

            startActivity(new Intent(Preview.this, DashHeader.class));

            finishAffinity();

            /*alert.showDialog(Preview.this);
            ViewDialog alert = new ViewDialog();*/
        }

        catch(SQLException e){


            e.printStackTrace();
        }

    }


    public class ViewDialog {

        public void showDialog(Activity activity) {

            final Dialog dialog = new Dialog(Preview.this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setContentView(R.layout.confirm_dialog);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));

            Button save,exit;

            save = (Button) dialog.findViewById(R.id.save);
            exit = (Button) dialog.findViewById(R.id.exit);

            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    AppConstant.DOCUMENT = null;

                    startActivity(new Intent(Preview.this, DashHeader.class));

                    finishAffinity();
                }
            });

            exit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    finishAffinity();
                }
            });

            dialog.show();
            Window window = dialog.getWindow();
            window.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        }
    }


    private void email(){

        final Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE, Uri.parse("mailto:"));
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "ePOD Document Number: "+tvDocu.getText().toString());

        shareIntent.putExtra(Intent.EXTRA_EMAIL  , new String[]{""+GetEmail()});
        shareIntent.putExtra(
                Intent.EXTRA_TEXT,
                Html.fromHtml(new StringBuilder()

                        .append("<p><b>"+"Dear Admin,"+"</b></p>")
                        .append("<p><b>"+"Please find the Delivery Details for Document Number: "+tvDocu.getText().toString()+" below:"+"</b></p>")
                        .append("<p><b>"+"1. Company: "+tvCompany.getText().toString()+"</b></p>")
                        .append("<p><b>"+"2. Driver Name: "+tvDriver.getText().toString()+"</b></p>")
                        .append("<p><b>"+"3. Delivery Vehicle: "+tvVehicle.getText().toString()+"</b></p>")
                        .append("<p><b>"+"4. Date of Delivery: "+tvDate.getText().toString()+"</b></p>")
                        .append("<p><b>"+"5. Time of Delivery: "+tvTime.getText().toString()+"</b></p>")
                        .append("<p><b>"+"6. Document Number: "+tvDocu.getText().toString()+"</b></p>")
                        .append("<p><b>"+"7. Number of Parcels: "+tvParcels.getText().toString()+"</b></p>")
                        .append("<p><b>"+"8. Parcel Details: "+"</b></p>")
                        .append("<small><p>"+strList+"</p></small>")
                        .append("<p><b>"+"9. Customer Signature: "+result +"(See Attached File)"+"</b></p>")
                        .append("<p><b>"+"10. Parcel Photograph: "+result1 +"(See Attached File)"+"</b></p>")

                    .append("<p><b>"+"Warm Regards, "+"</b></p>")
                    .append("<p><b>"+"EasyDelivery Team"+"</b></p>")

                    .toString())
        );

        filePaths = new ArrayList<>();

        filePaths.add(AppConstant.PIC_PATH);
        filePaths.add(AppConstant.SIGN_PATH);
        ArrayList<Uri> uris = new ArrayList<Uri>();
        for (String file : filePaths)
        {

            File file1 =new File(file);
            uris.add( FileProvider.getUriForFile(Objects.requireNonNull(getApplicationContext()),
                    "com.clone.EasyDelivery" + ".provider", file1));
        }
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);


        try{
            startActivity(shareIntent.createChooser(shareIntent, "Send Email"));
        } catch (ActivityNotFoundException ex){
            Toast.makeText(Preview.this, ex.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }

    public String GetCompany() {
        SharedPreferences shp = this.getSharedPreferences("COMPANY", MODE_PRIVATE);
        System.out.println("getting Image" + shp.getString("company", ""));
        return shp.getString("company", "");
    }

    public String GetDriver() {
        SharedPreferences shp = this.getSharedPreferences("DRIVER", MODE_PRIVATE);
        System.out.println("getting driver" + shp.getString("driver", ""));
        return shp.getString("driver", "");
    }

    public String GetEmail() {
        SharedPreferences shp = this.getSharedPreferences("EMAIL", MODE_PRIVATE);
        System.out.println("getting email" + shp.getString("email", ""));
        return shp.getString("email", "");
    }

    public String GetVehicle() {
        SharedPreferences shp = this.getSharedPreferences("VEHICLE", MODE_PRIVATE);
        System.out.println("getting vehicle" + shp.getString("vehicle", ""));
        return shp.getString("vehicle", "");
    }
}
