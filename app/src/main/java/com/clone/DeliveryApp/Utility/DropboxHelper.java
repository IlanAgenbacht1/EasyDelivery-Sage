package com.clone.DeliveryApp.Utility;

import android.content.Context;
import android.util.Log;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.RelocationErrorException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DropboxHelper {

    private static String REFRESH_TOKEN = "uTGEl3_OaeEAAAAAAAAAAZVbfJWNL2fdJPvT7lyhrfHjTLGQP9UtIKVotmqrT_96";
    private static String APP_KEY = "s901k3tmrktbc58";
    private static String APP_SECRET = "ddef3k5ox2xsams";
    private static DbxClientV2 dropboxClient;


    private static DbxClientV2 getClient() {

        if (dropboxClient == null) {

            DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/Apps/EasyDelivery").build();

            DbxCredential credential = new DbxCredential("", 0L, REFRESH_TOKEN, APP_KEY, APP_SECRET);

            dropboxClient = new DbxClientV2(config, credential);
        }

        return dropboxClient;
    }


    public static void downloadAllTrips(Context context, String companyName) {

        try {

            ListFolderResult folders = getClient().files().listFolder("/Company/" + companyName + "/");

            for (int i = 0; i < folders.getEntries().size(); i++) {

                String resultString = folders.getEntries().get(i).getName();

                if (resultString.contains(".json") && !AppConstant.tripList.contains(resultString.substring(0, resultString.length() - 5))) {

                    downloadFile(context, companyName, resultString);
                }
            }
        } catch (DbxException e) {
            e.printStackTrace();
        }
    }


    public static void downloadFile(Context context, String companyName, String tripName) {

        try {

            File file = new File(context.getFilesDir() + "/Trip/");

            if (!file.exists()) {

                file.mkdirs();
            }

            try (OutputStream outputStream = new FileOutputStream(new File(file.getPath(), tripName))) {

                Log.i("Dropbox", "Download starting...");

                getClient().files().downloadBuilder("/Company/" + companyName + "/" + tripName).download(outputStream);

                Log.i("Dropbox", "Download completed.");
            }

        } catch (DbxException | IOException e) {
            e.printStackTrace();
        }
    }


    public static void moveTripInProgress() {

        try {

            if (!SyncConstant.STARTED_TRIP.isEmpty()) {

                String fromFile = "/Company/" + AppConstant.COMPANY + "/" + SyncConstant.STARTED_TRIP + ".json";

                String toFolder = "/Company/" + AppConstant.COMPANY + "/InProgress/" + SyncConstant.STARTED_TRIP+ ".json";

                getClient().files().moveV2(fromFile, toFolder);
            }

        } catch (RelocationErrorException e) {

            throw new RuntimeException(e);

        } catch (DbxException e) {

            throw new RuntimeException(e);
        }
    }


    public static void moveIncompleteTrip() {

        try {

            ListFolderResult result = getClient().files().listFolder("/Company/" + AppConstant.COMPANY + "/InProgress/");

            if (!result.getEntries().isEmpty()) {

                for (int i = 0; i < result.getEntries().size(); i++) {

                    String item = result.getEntries().get(i).getName();

                    if (!SyncConstant.STARTED_TRIP.equals(item.substring(0, item.length() - 5))) {

                        String fromFile = "/Company/" + AppConstant.COMPANY + "/InProgress/" + item;

                        String toFile = "/Company/" + AppConstant.COMPANY + "/" + item;

                        getClient().files().moveV2(fromFile, toFile);
                    }
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    public static boolean uploadCompletedDelivery(Context context, String filePath, String tripName, String document, String image, String signature) {

        try {

            String dropboxPath = "/Company/" + AppConstant.COMPANY + "/Completed/" + tripName + "/" + document;

            String localImage = context.getFilesDir() + "/DeliveryApp/DeliveryImage/" + image + ".jpg";

            String localSignature = context.getFilesDir() + "/DeliveryApp/DeliverySignature/" + signature + ".jpg";

            createUploadFolders(tripName, document);

            try (InputStream inputStream = new FileInputStream(new File(filePath))) {

                getClient().files().uploadBuilder(dropboxPath  + "/" + document + ".json").uploadAndFinish(inputStream);
            }

            try (InputStream inputStream = new FileInputStream(new File(localImage))) {

                getClient().files().uploadBuilder(dropboxPath + "/" + image + ".jpg").uploadAndFinish(inputStream);
            }

            try (InputStream inputStream = new FileInputStream(new File(localSignature))) {

                getClient().files().uploadBuilder(dropboxPath + "/" + signature + ".jpg").uploadAndFinish(inputStream);
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

            String path = "/Company/" + AppConstant.COMPANY + "/Completed/" ;

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

            String toPath = "/Company/" + AppConstant.COMPANY + "/Completed/" + tripName + ".json";

            String fromPath = "/Company/" + AppConstant.COMPANY + "/InProgress/" + tripName + ".json";

            getClient().files().moveV2(fromPath, toPath);

        } catch(Exception e) {

            e.printStackTrace();
        }
    }

}