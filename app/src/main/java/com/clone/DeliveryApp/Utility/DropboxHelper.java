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


    public static void moveFileInProgress() {

        String fromFile = "/Company/" + AppConstant.COMPANY + "/" + SyncConstant.STARTED_TRIP + ".json";

        String toFolder = "/Company/" + AppConstant.COMPANY + "/InProgress/" + SyncConstant.STARTED_TRIP + ".json";

        try {

            getClient().files().moveV2(fromFile, toFolder);

        } catch (RelocationErrorException e) {

            throw new RuntimeException(e);
        } catch (DbxException e) {

            throw new RuntimeException(e);
        }
    }


    public static void uploadCompletedDelivery() {

        try {

            String path = "/Company/" + AppConstant.COMPANY + "/Completed/" + SyncConstant.TRIP_NAME + "/" + SyncConstant.DOCUMENT + "/" + SyncConstant.DOCUMENT + ".json";

            createUploadFolders();

            try(InputStream inputStream = new FileInputStream(new File(SyncConstant.DOCUMENT_FILE_PATH))) {

                getClient().files().uploadBuilder(path).uploadAndFinish(inputStream);
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    public static void createUploadFolders() {

        try {

            boolean tripExists = false;
            boolean documentExists = false;

            String path = "/Company/" + AppConstant.COMPANY + "/Completed/" ;

            ListFolderResult folders = getClient().files().listFolder(path);

            for (int i = 0; i < folders.getEntries().size(); i++) {

                String folderName = folders.getEntries().get(i).getName();

                if (folderName.equals(SyncConstant.TRIP_NAME)) {

                    tripExists = true;
                }
            }

            if (!tripExists) {

                getClient().files().createFolderV2(path + SyncConstant.TRIP_NAME);
            }

            path = path + SyncConstant.TRIP_NAME + "/";

            folders = getClient().files().listFolder(path);

            for (int i = 0; i < folders.getEntries().size(); i++) {

                String folderName = folders.getEntries().get(i).getName();

                if (folderName.equals(SyncConstant.DOCUMENT)) {

                    documentExists = true;
                }
            }

            if (!documentExists) {

                getClient().files().createFolderV2(path + SyncConstant.DOCUMENT);
            }

        } catch(Exception e) {

            e.printStackTrace();
        }
    }

}