package com.clone.EasyDelivery.Activity;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.EasyDelivery.Activity.TripDash;
import com.clone.EasyDelivery.Adapter.PreviewAdapter;
import com.clone.EasyDelivery.Database.DeliveryDb;
import com.clone.EasyDelivery.Model.ItemParcel;
import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.Utility.AppConstant;
import com.clone.EasyDelivery.Utility.ImageHelper;
import com.clone.EasyDelivery.Utility.ToastLogger;
import com.clone.EasyDelivery.Utility.SecurityManager;
import com.google.gson.Gson;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class Preview extends AppCompatActivity {

    private TextView tvCompany,tvDriver,tvVehicle,tvDate,tvTime,tvDocu,tvParcels,tvComment;
    private Button btnBack,btnConfirm;
    private RecyclerView recyclerView;
    private String date_final,time_final,currentDate;
    private PreviewAdapter adapter;
    private RelativeLayout rl_sign_view,rl_view_parcel;
    private ItemParcel itemParcel;
    String result,result1;
    private Context context;
    ArrayList<String> parcelId;
    ArrayList<String> filePaths;
    String strList;
    private TextView tvPic,tvSign;
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
        tvComment = findViewById(R.id.tv_comment);
        tvParcels = findViewById(R.id.tv_parcel);

        Collections.sort(AppConstant.validatedParcels);

        adapter = new PreviewAdapter(this, AppConstant.validatedParcels);

        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(adapter);

        tvCompany.setText(GetCompany());

        tvDriver.setText(GetDriver());

        tvVehicle.setText(GetVehicle());

        Log.d(TAG, "onCreate:current "+currentDate);

        tvDate.setText(new SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(new Date()));

        tvTime.setText(new SimpleDateFormat("HH:mm", Locale.US).format(new Date())+" Hours");

        tvDocu.setText(AppConstant.DOCUMENT);
        tvComment.setText(AppConstant.COMMENT);
        tvParcels.setText(AppConstant.PARCEL_NO);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        result = AppConstant.SIGN_PATH.substring(AppConstant.SIGN_PATH.lastIndexOf('/') + 1).trim();
        tvSign.setText("View Here");

        // 🔒 SECURE SIGNATURE DECRYPTION: Use SecurityManager instead of legacy method
        byte[] decryptedSignature = null;
        try {
            Log.i("PreviewSecurity", "Starting secure signature decryption for preview");
            
            if (AppConstant.SIGN_PATH == null || AppConstant.SIGN_PATH.trim().isEmpty()) {
                Log.e("PreviewSecurity", "Signature path is null or empty");
                tvSign.setText("No Signature Available");
                rl_sign_view.setEnabled(false);
                return;
            }
            
            // Use SecurityManager for secure decryption
            SecurityManager securityManager = SecurityManager.getInstance(this);
            
            // First try to load the signature as a SecurityPackage (new format)
            SecurityManager.SignaturePackage signaturePackage = ImageHelper.loadEncryptedSignatureWithIntegrity(AppConstant.SIGN_PATH);
            
            if (signaturePackage != null) {
                // New secure format with integrity verification
                Log.i("PreviewSecurity", "Loading signature with integrity verification");
                decryptedSignature = securityManager.decryptSignatureWithIntegrityCheck(signaturePackage);
                
                if (decryptedSignature != null) {
                    Log.i("PreviewSecurity", "Signature decrypted successfully with integrity verification: " + decryptedSignature.length + " bytes");
                } else {
                    Log.e("PreviewSecurity", "Signature integrity verification failed");
                }
            } else {
                // Fallback: Try legacy decryption for backward compatibility
                Log.w("PreviewSecurity", "Attempting legacy signature decryption");
                
                // Read raw encrypted file
                File signatureFile = new File(AppConstant.SIGN_PATH);
                if (signatureFile.exists()) {
                    byte[] encryptedData = new byte[(int) signatureFile.length()];
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(signatureFile)) {
                        fis.read(encryptedData);
                    }
                    
                    // Try SecurityManager decryption first
                    decryptedSignature = securityManager.decryptSignature(encryptedData);
                    
                    if (decryptedSignature != null) {
                        Log.i("PreviewSecurity", "Legacy signature decrypted with SecurityManager: " + decryptedSignature.length + " bytes");
                    } else {
                        // Final fallback: Try old ImageHelper method
                        Log.w("PreviewSecurity", "Trying final fallback with ImageHelper");
                        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
                        String keyString = prefs.getString("signature_key", "");
                        
                        if (!keyString.isEmpty()) {
                            decryptedSignature = ImageHelper.decryptImage(AppConstant.SIGN_PATH, keyString);
                            if (decryptedSignature != null) {
                                Log.w("PreviewSecurity", "Legacy decryption successful - consider re-encrypting with SecurityManager");
                            }
                        } else {
                            Log.e("PreviewSecurity", "No encryption key available for legacy decryption");
                        }
                    }
                } else {
                    Log.e("PreviewSecurity", "Signature file does not exist: " + AppConstant.SIGN_PATH);
                }
            }
            
            // Update UI based on decryption result
            if (decryptedSignature != null) {
                Log.i("PreviewSecurity", "Signature successfully decrypted for preview");
                tvSign.setText("View Signature");
                rl_sign_view.setEnabled(true);
            } else {
                Log.e("PreviewSecurity", "Failed to decrypt signature - all methods failed");
                tvSign.setText("Signature Unavailable");
                rl_sign_view.setEnabled(false);
                
                // Show user-friendly error
                Toast.makeText(this, "Unable to load signature for preview. The signature may be corrupted.", Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Log.e("PreviewSecurity", "Exception during signature decryption: " + e.getMessage(), e);
            tvSign.setText("Signature Error");
            rl_sign_view.setEnabled(false);
            
            // Show user-friendly error
            Toast.makeText(this, "Error loading signature: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

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

                //email();
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

            // 📷 FIX: Keep the full filename with .jpg extension for proper file lookup
            String imageFile = result1; // Keep full filename including .jpg extension
            
            // Remove .jpg extension for database storage (maintain compatibility)
            String imageFileForDb = result1.substring(0, result1.length() - 4);

            String signatureFile = AppConstant.SIGN_PATH.substring(AppConstant.SIGN_PATH.lastIndexOf('/') + 1).trim();

            currentDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US).format(new Date());
            
            Log.i("PreviewDatabase", "Storing image file in database: " + imageFileForDb);
            Log.i("PreviewDatabase", "Full image filename: " + imageFile);
            Log.i("PreviewDatabase", "Signature file: " + signatureFile);

            db.setDocumentCompleted(itemParcel.getDocu(), imageFileForDb, signatureFile, currentDate, this);
            db.updateComment();
            db.createEmailEntry(itemParcel.getDocu(), AppConstant.TRIPID);

            Log.i("SyncService", "Email queued: " + AppConstant.TRIPID + ":" + itemParcel.getDocu());
            
            // 🎯 TRIP COMPLETION: Check if all deliveries for this trip are now completed
            boolean allDeliveriesCompleted = checkAllDeliveriesCompleted(db, AppConstant.TRIPID);
            Log.i("TripCompletion", "All deliveries completed for trip " + AppConstant.TRIPID + ": " + allDeliveriesCompleted);
            
            if (allDeliveriesCompleted) {
                Log.i("TripCompletion", "🎉 TRIP COMPLETED! Adding to completed trips list and broadcasting completion");
                
                // Add trip to completed list if not already there
                if (!AppConstant.completedTrips.contains(AppConstant.TRIPID)) {
                    AppConstant.completedTrips.add(AppConstant.TRIPID);
                    Log.i("TripCompletion", "Added trip " + AppConstant.TRIPID + " to completed trips list");
                }
                
                // Send broadcast to trigger trip completion handling
                Intent tripCompletedIntent = new Intent("TripCompleted");
                tripCompletedIntent.putExtra("tripId", AppConstant.TRIPID);
                sendBroadcast(tripCompletedIntent);
                Log.i("TripCompletion", "✅ Broadcasted TripCompleted for trip: " + AppConstant.TRIPID);
                
                // Show completion confirmation
                showTripCompletionDialog();
            } else {
                Log.i("TripCompletion", "Trip not yet complete - more deliveries remaining");
                
                // Navigate back to DashHeader to continue with remaining deliveries
                startActivity(new Intent(Preview.this, DashHeader.class));
                finishAffinity();
            }

            db.close();
            
            AppConstant.DOCUMENT = "";
            AppConstant.COMMENT = "";
        }

        catch(SQLException e){

            e.printStackTrace();

            ToastLogger.exception(getApplicationContext(), e);
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
                        .append("<p><b>"+"Please find the delivery details for document number: "+tvDocu.getText().toString()+" below:"+"</b></p>")
                        .append("<p><b>"+"1. Company: "+tvCompany.getText().toString()+"</b></p>")
                        .append("<p><b>"+"2. Driver Name: "+tvDriver.getText().toString()+"</b></p>")
                        .append("<p><b>"+"3. Delivery Vehicle: "+tvVehicle.getText().toString()+"</b></p>")
                        .append("<p><b>"+"4. Date of Delivery: "+tvDate.getText().toString()+"</b></p>")
                        .append("<p><b>"+"5. Time of Delivery: "+tvTime.getText().toString()+"</b></p>")
                        .append("<p><b>"+"6. Document Number: "+tvDocu.getText().toString()+"</b></p>")
                        .append("<p><b>"+"7. Number of Parcels: "+tvParcels.getText().toString()+"</b></p>")
                        .append("<p><b>"+"8. Parcel Details: "+"</b></p>")
                        .append("<small><p>"+strList+"</p></small>")
                        .append("<p><b>"+"9. Customer Signature: "+ result +"(See Attached File)"+"</b></p>")
                        .append("<p><b>"+"10. Parcel Photograph: "+ result1 +"(See Attached File)"+"</b></p>")

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
    
    /**
     * 🎯 Check if all deliveries for the given trip are completed
     * @param db Database instance
     * @param tripId Trip ID to check
     * @return true if all deliveries are completed, false otherwise
     */
    private boolean checkAllDeliveriesCompleted(DeliveryDb db, String tripId) {
        try {
            // Get list of incomplete documents for this trip
            List<String> incompleteDocuments = db.getDocumentList(true); // true = incomplete only
            
            Log.i("TripCompletion", "Checking completion for trip: " + tripId);
            Log.i("TripCompletion", "Remaining incomplete documents: " + incompleteDocuments.size() + " - " + incompleteDocuments);
            
            // If no incomplete documents remain, trip is complete
            boolean isComplete = incompleteDocuments.isEmpty();
            
            if (isComplete) {
                Log.i("TripCompletion", "✅ ALL DELIVERIES COMPLETED for trip: " + tripId);
            } else {
                Log.i("TripCompletion", "⏳ Trip " + tripId + " still has " + incompleteDocuments.size() + " pending deliveries");
            }
            
            return isComplete;
            
        } catch (Exception e) {
            Log.e("TripCompletion", "Error checking trip completion for " + tripId, e);
            return false; // Fail safe - assume not complete if we can't check
        }
    }
    
    /**
     * 🎉 Show trip completion confirmation dialog and navigate to trip dashboard
     */
    private void showTripCompletionDialog() {
        try {
            final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this, R.style.AlertDialogStyle);
            
            builder.setTitle("🎉 Trip Completed!");
            builder.setMessage("Congratulations! You have successfully completed all deliveries for trip " + AppConstant.TRIPID + ".\n\nThe trip data is being synchronized with the cloud.");
            
            builder.setPositiveButton("Continue", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    // Navigate to trip dashboard
                    Log.i("TripCompletion", "User acknowledged trip completion - navigating to TripDash");
                    startActivity(new Intent(Preview.this, TripDash.class));
                    finishAffinity();
                }
            });
            
            // Prevent canceling - user must acknowledge
            builder.setCancelable(false);
            
            android.app.AlertDialog dialog = builder.create();
            dialog.show();
            
            Log.i("TripCompletion", "Displayed trip completion confirmation dialog");
            
        } catch (Exception e) {
            Log.e("TripCompletion", "Error showing completion dialog - falling back to direct navigation", e);
            // Fallback: Navigate directly if dialog fails
            startActivity(new Intent(Preview.this, TripDash.class));
            finishAffinity();
        }
    }
}
