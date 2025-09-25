package com.clone.EasyDelivery.Utility;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import com.clone.EasyDelivery.Database.DeliveryDb;
import com.clone.EasyDelivery.Model.Delivery;
import com.clone.EasyDelivery.Model.Return;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.Authenticator;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;


public class SyncService extends IntentService {

    private boolean connected;

    BroadcastReceiver receiver;

    DeliveryDb database;

    public SyncService() {
        super("SyncService");
    }

    @Override
    public void onStart(@Nullable Intent intent, int startId) {
        super.onStart(intent, startId);

        if (database != null && database.isOpen()) {

            database.close();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (database != null && database.isOpen()) {

            database.close();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i("SyncService", "Destroyed");

        if (receiver != null) {

            unregisterReceiver(receiver);
        }

        if (database != null && database.isOpen()) {

            database.close();
        }
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

        Log.i("SyncService", "onHandleIntent");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {

                if (database != null) {

                    if (database.isOpen()) {

                        database.close();
                    }
                }

                Thread threadDownloadTrips = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        DropboxHelper.downloadAllTrips(getApplicationContext());
                    }
                });

                Thread threadCompletedTrip = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        syncCompletedTrip();
                    }
                });

                Thread threadTripStatus = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        syncTripStatus();
                    }
                });

                Thread threadCompletedData = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        syncCompletedData();
                    }
                });

                Thread threadEmail = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        syncEmail();
                    }
                });

                Thread threadReturns = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        syncReturn();
                    }
                });

                threadDownloadTrips.start();
                threadTripStatus.start();
                threadEmail.start();
                threadCompletedData.start();
                threadCompletedTrip.start();
                threadReturns.start();

                try {
                    threadDownloadTrips.join();
                    threadTripStatus.join();
                    threadEmail.join();
                    threadCompletedData.join();
                    threadCompletedTrip.join();
                    threadReturns.join();
                    database.close();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        },0, 20000);

        IntentFilter filter = new IntentFilter();

        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addAction("DeliveryCompleted");
        filter.addAction("DeliveryStarted");
        filter.addAction("TripStarted");
        filter.addAction("TripNotStarted");
        filter.addAction("TripCompleted");
        filter.addAction("TripIncomplete");

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String action = intent.getAction();

                switch (action) {

                    case "android.net.conn.CONNECTIVITY_CHANGE":
                        try {

                            Thread thread = new Thread(new Runnable() {
                                @Override
                                public void run() {

                                    connected = ConnectionHelper.isInternetConnected();
                                }
                            });

                            thread.start();
                            thread.join();

                            if (connected) {

                                Log.i("SyncService", "Connected");

                                LocationHelper.getLocation(true, getApplicationContext());

                                thread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {

                                        //DropboxHelper.downloadAllTrips(getApplicationContext());

                                        //ScheduleHelper.getLocalTrips(getApplicationContext());
                                    }
                                });

                                thread.start();

                            } else {

                                LocationHelper.getLocation(false, getApplicationContext());
                            }

                        } catch (Exception e) {

                            e.printStackTrace();
                        }

                        Log.i("SyncService", "Connectivity action");
                    break;

                    case "TripStarted":

                        Thread thread = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                //DropboxHelper.moveTripInProgress();
                            }
                        });

                        //thread.start();

                        Log.i("SyncService", "Trip Started");

                    break;

                    case "TripNotStarted":

                        Thread thread2 = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                openDatabase();

                                DropboxHelper.moveIncompleteTrip(getApplicationContext(), database);
                            }
                        });

                        thread2.start();

                        Log.i("SyncService", "Trip not started");

                    break;

                    case "TripCompleted" :

                        Thread threadTripSync = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                //syncCompletedTrip();
                            }
                        });

                        //threadTripSync.start();

                        Log.i("SyncService", "Trip Completed");
                    break;

                    case "TripIncomplete":

                        Thread threadIncompleteMove = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                syncTripStatus();
                            }
                        });

                        //threadIncompleteMove.start();

                    break;

                    case "DeliveryStarted":

                        Log.i("SyncService", "Delivery Started");
                    break;

                    case "DeliveryCompleted":

                        Thread threadDocumentSync = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                //syncCompletedData();
                            }
                        });

                        //threadDocumentSync.start();

                        Log.i("SyncService", "Delivery Completed");

                    break;
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            registerReceiver(receiver,filter, Context.RECEIVER_NOT_EXPORTED);

        } else {

            registerReceiver(receiver, filter);
        }

        return START_STICKY;
    }


    private void syncCompletedData() {

        try {

            openDatabase();

            List<String> trips = database.getIncompleteSyncList();

            for (String trip : trips) {

                //check if there are completed deliveries for this trip locally

                List<String> documents = database.getCompletedDocumentList(trip);

                if (!documents.isEmpty()) {

                    for (String document : documents) {

                        //create delivery json and upload to dropbox

                        Delivery delivery = database.getCompletedDocument(document, trip);

                        delivery = database.getCompletedParcels(delivery);
                        delivery = database.getFlaggedParcels(delivery);

                        String filePath = JsonHandler.writeDeliveryFile(getApplicationContext(), delivery);

                        if (DropboxHelper.uploadCompletedDelivery(getApplicationContext(), filePath, trip, document, delivery.getImagePath(), delivery.getSignPath())) {

                            Log.i("SyncService", "Uploaded " + document);

                            File file = new File(filePath);
                            file.delete();

                            ImageHelper.syncDeleteImageFiles(getApplicationContext(), delivery.getImagePath(), delivery.getSignPath());

                            database.setDocumentUploaded(document, trip);
                        }
                    }
                }

                if (database.isDataSynced(trip) && !AppConstant.completedTrips.contains(trip)) {

                    AppConstant.completedTrips.add(trip);
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    private void syncTripStatus() {

        try {

            openDatabase();

            DropboxHelper.updateListInProgressTrips();
            DropboxHelper.moveTripInProgress(null);
            DropboxHelper.moveIncompleteTrip(getApplicationContext(), database);

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    private void syncCompletedTrip() {
        try {

            openDatabase();

            if (!AppConstant.completedTrips.isEmpty()) {

                for (String completedTrip : AppConstant.completedTrips) {

                    DropboxHelper.moveTripInProgress(completedTrip);

                    DropboxHelper.moveCompletedTrip(completedTrip);

                    if (database.isDataSynced(completedTrip)) {

                        AppConstant.completedTrips.remove(completedTrip);

                        database.deleteUploadedData(completedTrip);
                    }

                    if (SyncConstant.STARTED_TRIP.equals(completedTrip)) {

                        SyncConstant.STARTED_TRIP = "";
                    }

                    Log.i("SyncService", completedTrip + " uploaded");
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    private void syncEmail() {
        try {
            openDatabase();
            List<Delivery> emailList = database.getAllUnsentEmails();
            for (Delivery queuedEmail : emailList) {
                Delivery data = database.getCompletedDocument(queuedEmail.getDocument(), queuedEmail.getTripId());
                data = database.getCompletedParcels(data);
                data = database.getFlaggedParcels(data);
                if (sendEmail(data)) {
                    database.setEmailSent(queuedEmail.getDocument(), queuedEmail.getTripId());
                    Log.i("SyncService", queuedEmail.getDocument() + " email sent.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void syncReturn() {
        try {

            openDatabase();

            DropboxHelper.downloadReturnFile(getApplicationContext());

            List<Return> returnsList = database.getReturnsList();

            Log.i("SyncService", "returns size: "+returnsList.size());

            if (!returnsList.isEmpty()) {

                for (Return returnData : returnsList) {

                    File file = JsonHandler.writeReturnFile(getApplicationContext(), returnData);

                    if (DropboxHelper.uploadReturnsFile(getApplicationContext())) {

                        database.deleteReturns(returnData.getItem());

                        Log.i("SyncService", "Return " + returnData.getItem() + " synced.");

                    } else {

                        file.delete();

                        Log.i("SyncService", "Return " + returnData.getItem() + " failed to sync. Return file reset.");
                    }
                }


            }
        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    private boolean sendEmail(Delivery delivery) {
        try {
            String recipient = AppConstant.EMAIL;
            String subject = "ePOD Document Number: " + delivery.getDocument();

            List<String> parcelsList = delivery.getParcelNumbers();
            Collections.sort(parcelsList);
            String parcels = TextUtils.join(", ", parcelsList).replaceAll("\\s", " ");

            String date = delivery.getTime().substring(0, 10);
            String time = delivery.getTime().substring(delivery.getTime().length() - 8);

            // Search for signature files and decrypt
            String signFilename = delivery.getSignPath();
            Log.d("EMAILOUTPUT", "Signature filename from database: " + signFilename);

            byte[] decryptedSignature = null;
            String foundSignaturePath = findSignatureFile(signFilename);

            if (foundSignaturePath != null) {
                try {
                    SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
                    String keyString = prefs.getString("signature_key", "");

                    if (!keyString.isEmpty()) {
                        decryptedSignature = ImageHelper.decryptImage(foundSignaturePath, keyString);
                        Log.d("EMAILOUTPUT", "Signature decrypted successfully from: " + foundSignaturePath);
                    } else {
                        Log.e("EMAILOUTPUT", "Signature key is empty");
                    }
                } catch (Exception e) {
                    Log.e("EMAILOUTPUT", "Failed to decrypt signature: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Create temporary files for images in cache directory
            File signatureFile = null;
            File photoFile = null;

            if (decryptedSignature != null) {
                signatureFile = new File(getCacheDir(), "signature.png");
                try (FileOutputStream fos = new FileOutputStream(signatureFile)) {
                    fos.write(decryptedSignature);
                    Log.d("EMAILOUTPUT", "Signature file created at: " + signatureFile.getAbsolutePath());
                }
            }

            // Copy photo file to cache directory so it can be found by PDF converter
            File originalPhotoFile = new File(getApplicationContext().getFilesDir() + "/DeliveryApp/DeliveryImage/" + delivery.getImagePath() + ".jpg");
            if (originalPhotoFile.exists()) {
                photoFile = new File(getCacheDir(), "photo.jpg");
                try (FileInputStream fis = new FileInputStream(originalPhotoFile);
                     FileOutputStream fos = new FileOutputStream(photoFile)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                    Log.d("EMAILOUTPUT", "Photo file copied to: " + photoFile.getAbsolutePath());
                }
            } else {
                Log.w("EMAILOUTPUT", "Original photo file does not exist: " + originalPhotoFile.getAbsolutePath());
            }

            // Build compact ePOD HTML structure
            StringBuilder bodyBuilder = new StringBuilder();

            // Compact Document Header
            bodyBuilder.append("<div style='text-align: center; margin-bottom: 15px;'>")
                    .append("<h2 style='color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 5px; margin: 0 0 3px 0; font-size: 18px;'>ELECTRONIC PROOF OF DELIVERY</h2>")
                    .append("<p style='color: #7f8c8d; margin: 0; font-size: 12px;'>ePOD Certificate</p>")
                    .append("</div>");

            // Two-column layout for better space utilization
            bodyBuilder.append("<table style='width: 100%; border-collapse: collapse; margin-bottom: 10px;'>")
                    .append("<tr>")
                    // Left column - Document & Delivery Info
                    .append("<td style='width: 50%; vertical-align: top; padding-right: 10px;'>")
                    .append("<div style='border: 1px solid #34495e; padding: 8px; background-color: #f8f9fa;'>")
                    .append("<h4 style='color: #2c3e50; margin: 0 0 5px 0; font-size: 12px; border-bottom: 1px solid #3498db;'>DOCUMENT INFO</h4>")
                    .append("<table style='width: 100%; font-size: 10px;'>")
                    .append("<tr><td style='font-weight: bold; padding: 2px;'>Doc No:</td><td style='padding: 2px;'>" + delivery.getDocument() + "</td></tr>")
                    .append("<tr><td style='font-weight: bold; padding: 2px;'>Date:</td><td style='padding: 2px;'>" + date + "</td></tr>")
                    .append("<tr><td style='font-weight: bold; padding: 2px;'>Time:</td><td style='padding: 2px;'>" + time + "</td></tr>")
                    .append("</table>")
                    .append("</div>")
                    .append("<div style='border: 1px solid #34495e; padding: 8px; background-color: #f8f9fa; margin-top: 5px;'>")
                    .append("<h4 style='color: #2c3e50; margin: 0 0 5px 0; font-size: 12px; border-bottom: 1px solid #3498db;'>DELIVERY DETAILS</h4>")
                    .append("<table style='width: 100%; font-size: 10px;'>")
                    .append("<tr><td style='font-weight: bold; padding: 2px;'>Company:</td><td style='padding: 2px;'>" + delivery.getCustomerName() + "</td></tr>")
                    .append("<tr><td style='font-weight: bold; padding: 2px;'>Driver:</td><td style='padding: 2px;'>" + AppConstant.DRIVER + "</td></tr>")
                    .append("<tr><td style='font-weight: bold; padding: 2px;'>Vehicle:</td><td style='padding: 2px;'>" + AppConstant.VEHICLE + "</td></tr>")
                    .append("</table>")
                    .append("</div>")
                    .append("</td>")
                    // Right column - Parcel Info
                    .append("<td style='width: 50%; vertical-align: top; padding-left: 10px;'>")
                    .append("<div style='border: 1px solid #34495e; padding: 8px; background-color: #f8f9fa;'>")
                    .append("<h4 style='color: #2c3e50; margin: 0 0 5px 0; font-size: 12px; border-bottom: 1px solid #3498db;'>PARCEL INFO</h4>")
                    .append("<table style='width: 100%; font-size: 10px;'>")
                    .append("<tr><td style='font-weight: bold; padding: 2px;'>Count:</td><td style='padding: 2px;'>" + delivery.getNumberOfParcels() + "</td></tr>");

            if (!TextUtils.isEmpty(delivery.getComment())) {
                bodyBuilder.append("<tr><td style='font-weight: bold; padding: 2px; vertical-align: top;'>Notes:</td><td style='padding: 2px; font-size: 9px;'>" + delivery.getComment() + "</td></tr>");
            }

            bodyBuilder.append("<tr><td style='font-weight: bold; padding: 2px; vertical-align: top;'>Items:</td><td style='padding: 2px; font-size: 8px; word-break: break-all;'>" + parcels + "</td></tr>")
                    .append("</table>")
                    .append("</div>");

            // Flagged Items Section (if applicable) - compact version
            if (!delivery.getFlaggedParcelNumbers().isEmpty()) {
                List<String> flaggedParcelsList = delivery.getFlaggedParcelNumbers();
                Collections.sort(flaggedParcelsList);
                String flaggedParcels = TextUtils.join(", ", flaggedParcelsList).replaceAll("\\s", " ");

                bodyBuilder.append("<div style='border: 1px solid #e74c3c; padding: 5px; background-color: #fdf2f2; margin-top: 5px;'>")
                        .append("<h4 style='color: #e74c3c; margin: 0 0 3px 0; font-size: 10px;'>⚠️ FLAGGED ITEMS</h4>")
                        .append("<p style='font-size: 8px; margin: 0; color: #c0392b; word-break: break-all;'>" + flaggedParcels + "</p>")
                        .append("</div>");
            }

            bodyBuilder.append("</td>")
                    .append("</tr>")
                    .append("</table>");

            // Enhanced Evidence Section with better alignment
            bodyBuilder.append("<div style='border: 1px solid #34495e; padding: 8px; background-color: #f8f9fa;'>")
                    .append("<h4 style='color: #2c3e50; margin: 0 0 8px 0; font-size: 12px; border-bottom: 1px solid #3498db; text-align: center;'>DELIVERY EVIDENCE</h4>");

            // Create a flex-like table structure for better alignment
            bodyBuilder.append("<table style='width: 100%; border-collapse: collapse;'>")
                    .append("<tr>")
                    // Signature column with better alignment
                    .append("<td style='width: 50%; vertical-align: middle; text-align: center; padding: 5px; border-right: 1px solid #bdc3c7;'>")
                    .append("<div style='height: 100px; display: flex; flex-direction: column; justify-content: center; align-items: center;'>")
                    .append("<p style='font-weight: bold; margin: 0 0 5px 0; font-size: 10px; color: #34495e;'>Customer Signature</p>");

            if (signatureFile != null) {
                bodyBuilder.append("<div style='border: 2px solid #95a5a6; padding: 5px; background-color: white; border-radius: 3px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); display: inline-block; min-height: 70px; min-width: 160px; display: flex; align-items: center; justify-content: center;'>")
                        .append("<img src='signature.png' style='max-width: 150px; max-height: 60px; width: auto; height: auto; object-fit: contain;' alt='Customer Signature'/>")
                        .append("</div>");
            } else {
                bodyBuilder.append("<div style='border: 2px dashed #bdc3c7; padding: 15px; color: #7f8c8d; background-color: #ecf0f1; font-size: 9px; border-radius: 3px; min-height: 40px; min-width: 160px; display: flex; align-items: center; justify-content: center;'>")
                        .append("No signature captured")
                        .append("</div>");
            }

            bodyBuilder.append("</div>")
                    .append("</td>")

                    // Photo column with better alignment
                    .append("<td style='width: 50%; vertical-align: middle; text-align: center; padding: 5px;'>")
                    .append("<div style='height: 100px; display: flex; flex-direction: column; justify-content: center; align-items: center;'>")
                    .append("<p style='font-weight: bold; margin: 0 0 5px 0; font-size: 10px; color: #34495e;'>Delivery Photo</p>");

            if (photoFile != null) {
                bodyBuilder.append("<div style='border: 2px solid #95a5a6; padding: 5px; background-color: white; border-radius: 3px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); display: inline-block; min-height: 70px; min-width: 160px; display: flex; align-items: center; justify-content: center;'>")
                        .append("<img src='photo.jpg' style='max-width: 150px; max-height: 60px; width: auto; height: auto; object-fit: contain;' alt='Delivery Photo'/>")
                        .append("</div>");
            } else {
                bodyBuilder.append("<div style='border: 2px dashed #bdc3c7; padding: 15px; color: #7f8c8d; background-color: #ecf0f1; font-size: 9px; border-radius: 3px; min-height: 40px; min-width: 160px; display: flex; align-items: center; justify-content: center;'>")
                        .append("No photo available")
                        .append("</div>");
            }

            bodyBuilder.append("</div>")
                    .append("</td>")
                    .append("</tr>")
                    .append("</table>")
                    .append("</div>");

            // Compact Certification Footer
            bodyBuilder.append("<div style='border-top: 2px solid #3498db; padding: 8px 0; margin-top: 10px; text-align: center;'>")
                    .append("<p style='color: #2c3e50; font-weight: bold; font-size: 11px; margin: 0 0 3px 0;'>This ePOD certifies successful delivery completion</p>")
                    .append("<p style='color: #7f8c8d; font-size: 9px; margin: 0 0 2px 0;'>Generated by EasyDelivery System • " + date + " " + time + "</p>")
                    .append("<p style='color: #95a5a6; font-size: 8px; margin: 0;'>Electronically generated - no physical signature required</p>")
                    .append("</div>");

            String body = bodyBuilder.toString();

            // Generate PDF with proper base URI
            File pdfFile = new File(getCacheDir(), "ePOD_" + delivery.getDocument() + ".pdf");
            PdfWriter writer = new PdfWriter(pdfFile);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Set the base URI to the cache directory so images can be found
            ConverterProperties converterProperties = new ConverterProperties();
            converterProperties.setBaseUri(getCacheDir().getAbsolutePath() + "/");

            Log.d("EMAILOUTPUT", "Converting HTML to PDF with base URI: " + getCacheDir().getAbsolutePath());
            Log.d("EMAILOUTPUT", "HTML content: " + body);

            // Convert HTML to PDF
            HtmlConverter.convertToPdf(body, pdfDoc, converterProperties);

            document.close();

            Log.d("EMAILOUTPUT", "PDF generated successfully: " + pdfFile.getAbsolutePath());

            // Email setup
            final String username = "dev@easydelivery.biz";
            final String password = "nnmg ywbr fyud epwo";

            Properties properties = new Properties();
            properties.put("mail.smtp.host", "smtp.gmail.com");
            properties.put("mail.smtp.port", "587");
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");

            Session session = Session.getInstance(properties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("dev@easydelivery.biz"));
            message.addRecipient(MimeMessage.RecipientType.TO, new InternetAddress(recipient));
            message.setSubject(subject);

            Multipart multipart = new MimeMultipart();
            MimeBodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText("Please find the Electronic Proof of Delivery (ePOD) attached for document " + delivery.getDocument() + ".\n\nThis ePOD certificate contains comprehensive delivery information including recipient details, parcel information, and delivery evidence.");
            multipart.addBodyPart(messageBodyPart);

            // Attach PDF
            addAttachment(multipart, pdfFile.getAbsolutePath(), "ePOD_" + delivery.getDocument() + ".pdf");

            message.setContent(multipart);
            Transport.send(message);

            // Clean up temporary files
            if (signatureFile != null && signatureFile.exists()) {
                signatureFile.delete();
            }
            if (photoFile != null && photoFile.exists()) {
                photoFile.delete();
            }
            pdfFile.delete();

            Log.d("EMAILOUTPUT", "Email sent successfully for document: " + delivery.getDocument());
            return true;
        } catch (Exception e) {
            Log.e("EMAILOUTPUT", "Failed to send email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Add this helper method to search for signature files
    private String findSignatureFile(String filename) {
        Log.d("EMAILOUTPUT", "Searching for signature file: " + filename);

        if (filename == null || filename.isEmpty()) {
            Log.e("EMAILOUTPUT", "Filename is null or empty");
            return null;
        }

        // List of potential directories to search
        String[] searchPaths = {
                getApplicationContext().getFilesDir() + "/DeliveryApp/Signature/",
                getApplicationContext().getFilesDir() + "/Signature/",
                getApplicationContext().getFilesDir() + "/",
                getApplicationContext().getCacheDir() + "/",
                getApplicationContext().getExternalFilesDir(null) + "/DeliveryApp/Signature/",
                getApplicationContext().getExternalFilesDir(null) + "/Signature/",
                getApplicationContext().getExternalFilesDir(null) + "/"
        };

        // First, try exact filename match
        for (String basePath : searchPaths) {
            File dir = new File(basePath);
            if (dir.exists() && dir.isDirectory()) {
                File targetFile = new File(dir, filename);
                if (targetFile.exists()) {
                    Log.d("EMAILOUTPUT", "Found signature file at: " + targetFile.getAbsolutePath());
                    return targetFile.getAbsolutePath();
                }
            }
        }

        // If not found, search recursively in all app directories
        Log.d("EMAILOUTPUT", "File not found in standard locations, searching recursively...");

        String foundPath = searchRecursively(getApplicationContext().getFilesDir(), filename);
        if (foundPath != null) {
            return foundPath;
        }

        foundPath = searchRecursively(getApplicationContext().getCacheDir(), filename);
        if (foundPath != null) {
            return foundPath;
        }

        // Also search external files directory if available
        File externalFilesDir = getApplicationContext().getExternalFilesDir(null);
        if (externalFilesDir != null) {
            foundPath = searchRecursively(externalFilesDir, filename);
            if (foundPath != null) {
                return foundPath;
            }
        }

        // If still not found, list all .enc files to see what's available
        Log.d("EMAILOUTPUT", "Signature file not found. Listing all .enc files:");
        listEncryptedFiles(getApplicationContext().getFilesDir());
        listEncryptedFiles(getApplicationContext().getCacheDir());

        return null;
    }

    // Helper method to search recursively
    private String searchRecursively(File directory, String filename) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return null;
        }

        Log.d("EMAILOUTPUT", "Searching in directory: " + directory.getAbsolutePath());

        File[] files = directory.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (file.isFile() && file.getName().equals(filename)) {
                Log.d("EMAILOUTPUT", "Found file: " + file.getAbsolutePath());
                return file.getAbsolutePath();
            } else if (file.isDirectory()) {
                String found = searchRecursively(file, filename);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    // Helper method to list all encrypted files
    private void listEncryptedFiles(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".enc")) {
                Log.d("EMAILOUTPUT", "Found .enc file: " + file.getAbsolutePath());
            } else if (file.isDirectory()) {
                listEncryptedFiles(file);
            }
        }
    }

    private void addAttachment(Multipart multipart, String filePath, String fileName) throws MessagingException {
        MimeBodyPart attachmentBodyPart = new MimeBodyPart();
        DataSource source = new FileDataSource(filePath);
        attachmentBodyPart.setDataHandler(new DataHandler(source));
        attachmentBodyPart.setFileName(fileName);
        multipart.addBodyPart(attachmentBodyPart);
    }

    private void openDatabase() {
        if (database == null) {
            database = new DeliveryDb(getApplicationContext());
            database.open();
        } else {
            database.open();
        }
    }


}