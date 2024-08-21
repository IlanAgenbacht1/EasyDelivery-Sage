package com.clone.EasyDelivery.Utility;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.clone.EasyDelivery.Database.DeliveryDb;
import com.clone.EasyDelivery.Model.Delivery;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import jakarta.activation.DataHandler;
import jakarta.mail.Authenticator;
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
    }

    @Override
    public void onCreate() {
        super.onCreate();
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

                threadDownloadTrips.start();
                threadTripStatus.start();
                threadEmail.start();
                threadCompletedData.start();
                threadCompletedTrip.start();

            }
        },0, 20000);



        IntentFilter filter = new IntentFilter();

        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addAction("DeliveryCompleted");
        filter.addAction("DeliveryStarted");
        filter.addAction("TripStarted");
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

                                        DropboxHelper.downloadAllTrips(getApplicationContext());

                                        //ScheduleHelper.getLocalTrips(getApplicationContext());
                                        ScheduleHelper.getLocalTrips(getApplicationContext());
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

                        thread.start();

                        Log.i("SyncService", "Trip Started");

                    break;

                    case "TripCompleted" :

                        Thread threadTripSync = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                //syncCompletedTrip();
                            }
                        });

                        threadTripSync.start();

                        Log.i("SyncService", "Trip Completed");
                    break;

                    case "TripIncomplete":

                        Thread threadIncompleteMove = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                syncTripStatus();
                            }
                        });

                        threadIncompleteMove.start();

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

                        threadDocumentSync.start();

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

            List<String> trips = database.getIncompleteTripSyncList();

            for (String trip : trips) {

                //check if there are completed deliveries for this trip locally

                List<String> documents = database.getCompletedDocumentList(trip);

                if (!documents.isEmpty()) {

                    for (String document : documents) {

                        //create delivery json and upload to dropbox

                        Delivery delivery = database.getCompletedDocument(document, trip);

                        delivery = database.getCompletedParcels(delivery);

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

            DropboxHelper.moveIncompleteTrip(getApplicationContext(), database);
            DropboxHelper.moveTripInProgress();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    private void syncCompletedTrip() {

        openDatabase();

        if (!AppConstant.completedTrips.isEmpty()) {

            for (String completedTrip : AppConstant.completedTrips) {

                DropboxHelper.moveCompletedTrip(completedTrip);

                AppConstant.completedTrips.remove(completedTrip);

                database.deleteUploadedData(completedTrip);

                if (SyncConstant.STARTED_TRIP.equals(completedTrip)) {

                    SyncConstant.STARTED_TRIP = "";
                }

                Log.i("SyncService", completedTrip + " uploaded");
            }
        }
    }


    private void syncEmail() {

        try {

            openDatabase();

            List<Delivery> emailList = database.getAllUnsentEmails();

            for (Delivery queuedEmail : emailList) {

                Delivery data = database.getCompletedDocument(queuedEmail.getDocument(), queuedEmail.getTripId());

                data = database.getCompletedParcels(data);

                if (sendEmail(data)) {

                    database.setEmailSent(queuedEmail.getDocument(), queuedEmail.getTripId());

                    Log.i("SyncService", queuedEmail.getDocument() + " email sent.");
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

            String parcels;

            parcels = TextUtils.join(", ", delivery.getParcelNumbers());
            parcels = parcels.replaceAll("\\s", " ");

            String date = delivery.getTime().substring(0, 10);

            String time = delivery.getTime().substring(delivery.getTime().length() - 8);

            String body = new StringBuilder()

                    .append("<p>" + "Dear Admin," + "</p>")
                    .append("<p>" + "Please find the Delivery Details for Document Number: " + delivery.getDocument() + " below:" + "</p>")
                    .append("<br />")
                    .append("<p><b>" + "1. Company: " + delivery.getCustomerName() + "</b></p>")
                    .append("<p><b>" + "2. Driver Name: " + AppConstant.DRIVER + "</b></p>")
                    .append("<p><b>" + "3. Delivery Vehicle: " + AppConstant.VEHICLE + "</b></p>")
                    .append("<p><b>" + "4. Date of Delivery: " + date + "</b></p>")
                    .append("<p><b>" + "5. Time of Delivery: " + time + "</b></p>")
                    .append("<p><b>" + "6. Document Number: " + delivery.getDocument() + "</b></p>")
                    .append("<p><b>" + "7. Comment: " + delivery.getComment() + "</b></p>")
                    .append("<p><b>" + "8. Number of Parcels: " + delivery.getNumberOfParcels() + "</b></p>")
                    .append("<p><b>" + "9. Parcel Details: " + "</b></p>")
                    .append("<small><p>" + parcels + "</p></small>")
                    .append("<p><b>" + "10. Customer Signature: " + delivery.getSignPath() + " (See Attached File)" + "</b></p>")
                    .append("<p><b>" + "11. Parcel Photograph: " + delivery.getImagePath() + " (See Attached File)" + "</b></p>")
                    .append("<br />")
                    .append("<p>" + "Warm Regards, " + "</p>")
                    .append("<p>" + "EasyDelivery Team" + "</p>")

                    .toString();

            final String username = "dev@easydelivery.biz"; // SMTP username
            final String password = "nnmg ywbr fyud epwo"; // SMTP password

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

            messageBodyPart.setContent(body, "text/html");
            multipart.addBodyPart(messageBodyPart);

            addAttachment(multipart, getApplicationContext().getFilesDir() + "/DeliveryApp/DeliverySignature/" + delivery.getSignPath() + ".jpg");
            addAttachment(multipart, getApplicationContext().getFilesDir() + "/DeliveryApp/DeliveryImage/" + delivery.getImagePath() + ".jpg");

            message.setContent(multipart);

            Transport.send(message);

            return true;

        } catch (Exception e) {

            e.printStackTrace();

            return false;
        }
    }


    private void addAttachment(Multipart multipart, String filePath) {

        try {

            File file = new File(filePath);

            if (file.exists()) {

                MimeBodyPart attachmentBodyPart = new MimeBodyPart();

                try (FileInputStream fis = new FileInputStream(file)) {

                    attachmentBodyPart.setDataHandler(new DataHandler(new ByteArrayDataSource(fis, "image/jpeg")));
                    attachmentBodyPart.setFileName(file.getName());
                    multipart.addBodyPart(attachmentBodyPart);
                }
            }

        } catch(Exception e) {

            e.printStackTrace();
        }
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