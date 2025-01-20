package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.clone.EasyDelivery.Database.DeliveryDb;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DownloadErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.RelocationErrorException;
import com.dropbox.core.v2.files.WriteMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class DropboxHelper {

    private static String REFRESH_TOKEN = "z5wLDJpEALUAAAAAAAAAAaXxmMgnV5mEjNGWCcU81x0x7TmiG9kQLUU7IlGoTCay";
    private static String APP_KEY = "ma2o6e9amxvvuqk";
    private static String APP_SECRET = "q1t553htqu039lh";

    private static final String CLIENT_PATH = "dropbox/";
    private static final String CUSTOMER_PATH = "/Customers/" + AppConstant.COMPANY + "/";
    private static final String LOCAL_IMAGE_PATH = "/DeliveryApp/DeliveryImage/";
    private static final String LOCAL_SIGNATURE_PATH = "/DeliveryApp/DeliverySignature/";

    private static DbxClientV2 dropboxClient;

    private static DbxClientV2 getClient() {

        if (dropboxClient == null) {

            DbxRequestConfig config = DbxRequestConfig.newBuilder(CLIENT_PATH).build();

            DbxCredential credential = new DbxCredential("", 0L, REFRESH_TOKEN, APP_KEY, APP_SECRET);

            dropboxClient = new DbxClientV2(config, credential);
        }

        return dropboxClient;
    }


    public static void downloadAllTrips(Context context) {

        try {

            ArrayList<String> dropboxTrips = new ArrayList<>();

            ListFolderResult folders = getClient().files().listFolder(CUSTOMER_PATH);

            for (int i = 0; i < folders.getEntries().size(); i++) {

                String resultString = folders.getEntries().get(i).getName();

                Log.i("Dropbox", "Returned file " + resultString);

                if (resultString.contains(".json")) {

                    dropboxTrips.add(resultString.substring(0, resultString.length() - 5));

                    if (!AppConstant.tripList.contains(resultString.substring(0, resultString.length() - 5)) && !AppConstant.completedTrips.contains(resultString.substring(0, resultString.length() - 5))) {

                        downloadFile(context, resultString);
                    }
                }
            }

            for (String trip : dropboxTrips) {

                if (!AppConstant.downloadedTrips.contains(trip)) {

                    AppConstant.downloadedTrips.add(trip);
                }
            }

            ArrayList<String> toRemove = new ArrayList<>();

            for (String trip : AppConstant.downloadedTrips) {

                if (!dropboxTrips.contains(trip)) {

                    toRemove.add(trip);
                }
            }

            AppConstant.downloadedTrips.removeAll(toRemove);

        } catch (DbxException e) {
            e.printStackTrace();
        }
    }


    public static void downloadFile(Context context, String tripName) {

        try {

            File file = new File(context.getFilesDir() + "/Trip/");

            if (!file.exists()) {

                file.mkdirs();
            }

            try (OutputStream outputStream = new FileOutputStream(new File(file.getPath(), tripName))) {

                Log.i("Dropbox", "Download starting...");

                getClient().files().downloadBuilder(CUSTOMER_PATH + tripName).download(outputStream);

                Log.i("Dropbox", "Download completed.");
            }

        } catch (DbxException | IOException e) {
            e.printStackTrace();
        }
    }


    public static void downloadReturnFile(Context context) {

        File file = new File(context.getFilesDir() + "/Return/");

        if (!file.exists()) {

            file.mkdirs();
        }

        try (OutputStream outputStream = new FileOutputStream(new File(file.getPath()))) {

            Log.i("Dropbox", "Download starting...");

            getClient().files().downloadBuilder(CUSTOMER_PATH + "Returns/" + "returns.json").download(outputStream);

            Log.i("Dropbox", "Download completed.");

        } catch (FileNotFoundException e) {


        } catch (DownloadErrorException e) {

            e.printStackTrace();

        } catch (IOException | DbxException e) {

            e.printStackTrace();
        }
    }


    public static boolean uploadReturnsFile(Context context) {
        try {

            try (InputStream inputStream = new FileInputStream(new File(context.getFilesDir() + "/Return/", "returns.json"))) {

                getClient().files().uploadBuilder(CUSTOMER_PATH + "Returns/" + "returns.json").withMode(WriteMode.OVERWRITE).uploadAndFinish(inputStream);

                ToastLogger.message(context, "Uploaded return");
            }

            return true;

        } catch(Exception e) {

            e.printStackTrace();

            ToastLogger.exception(context, e);

            return false;
        }
    }


    public static void moveTripInProgress(String trip) {
        try {

            if (!SyncConstant.STARTED_TRIP.isEmpty()) {

                String fromFile = CUSTOMER_PATH + SyncConstant.STARTED_TRIP + ".json";

                String toFolder = CUSTOMER_PATH + "InProgress/" + SyncConstant.STARTED_TRIP + ".json";

                getClient().files().moveV2(fromFile, toFolder);

                Log.i("SyncService", "Moved " + SyncConstant.STARTED_TRIP + " to InProgress.");

            } else if (trip != null) {

                String fromFile = CUSTOMER_PATH + trip + ".json";

                String toFolder = CUSTOMER_PATH + "InProgress/" + trip + ".json";

                getClient().files().moveV2(fromFile, toFolder);

                Log.i("SyncService", "Moved " + trip + " to InProgress.");
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    public static void moveIncompleteTrip(Context context, DeliveryDb database) {

        try {

            ListFolderResult result = getClient().files().listFolder(CUSTOMER_PATH + "InProgress/");

            if (!result.getEntries().isEmpty()) {

                for (int i = 0; i < result.getEntries().size(); i++) {

                    String item = result.getEntries().get(i).getName();

                    if (!SyncConstant.STARTED_TRIP.equals(item.substring(0, item.length() - 5)) && !AppConstant.completedTrips.contains(item.substring(0, item.length() - 5))) {

                        if (!database.tripStarted(item.substring(0, item.length() - 5))) {

                            String fromFile = CUSTOMER_PATH + "InProgress/" + item;

                            String toFile = CUSTOMER_PATH + item;

                            getClient().files().moveV2(fromFile, toFile);
                        }
                    }
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    public static boolean uploadCompletedDelivery(Context context, String filePath, String tripName, String document, String image, String signature) {

        try {

            String dropboxPath = CUSTOMER_PATH + "Completed/" + tripName + "/" + document;

            String localImage = context.getFilesDir() + LOCAL_IMAGE_PATH + image + ".jpg";

            String localSignature = context.getFilesDir() + LOCAL_SIGNATURE_PATH + signature + ".jpg";

            createUploadFolders(tripName, document);

            try (InputStream inputStream = new FileInputStream(new File(filePath))) {

                getClient().files().uploadBuilder(dropboxPath  + "/" + document + ".json").uploadAndFinish(inputStream);
            }

            if (new File(localImage).exists()) {

                try (InputStream inputStream = new FileInputStream(new File(localImage))) {

                    getClient().files().uploadBuilder(dropboxPath + "/" + image + ".jpg").uploadAndFinish(inputStream);
                }
            }

            if (new File(localSignature).exists()) {

                try (InputStream inputStream = new FileInputStream(new File(localSignature))) {

                    getClient().files().uploadBuilder(dropboxPath + "/" + signature + ".jpg").uploadAndFinish(inputStream);
                }
            }

            return true;

        } catch (Exception e) {

            e.printStackTrace();

            return false;
        }
    }


    public static void createUploadFolders(String tripName, String document) {

        try {

            boolean tripExists = false;

            boolean documentExists = false;

            String path = CUSTOMER_PATH + "Completed/" ;

            ListFolderResult folders = getClient().files().listFolder(path);

            for (int i = 0; i < folders.getEntries().size(); i++) {

                String folderName = folders.getEntries().get(i).getName();

                if (folderName.equals(tripName)) {

                    tripExists = true;
                }
            }

            if (!tripExists) {

                getClient().files().createFolderV2(path + tripName);
            }

            path = path + tripName + "/";

            folders = getClient().files().listFolder(path);

            for (int i = 0; i < folders.getEntries().size(); i++) {

                String folderName = folders.getEntries().get(i).getName();

                if (folderName.equals(document)) {

                    documentExists = true;
                }
            }

            if (!documentExists) {

                getClient().files().createFolderV2(path + document);
            }

        } catch(Exception e) {

            e.printStackTrace();
        }
    }


    public static void moveCompletedTrip(String tripName) {

        try {

            String toPath = CUSTOMER_PATH + "Completed/" + tripName + "/" + tripName + ".json";

            String fromPath = CUSTOMER_PATH + "InProgress/" + tripName + ".json";

            getClient().files().moveV2(fromPath, toPath);

        } catch(Exception e) {

            e.printStackTrace();
        }
    }

}