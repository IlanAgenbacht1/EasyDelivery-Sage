package com.clone.DeliveryApp.Utility;

import android.content.Context;
import android.util.Log;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DownloadErrorException;
import com.dropbox.core.v2.files.ListFolderResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

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


    public static void downloadAllFiles(Context context, String companyName) {

        try {

            ListFolderResult result = getClient().files().listFolder("/Company/" + companyName + "/");

            for (int i = 0; i < result.getEntries().size(); i++) {

                String resultString = result.getEntries().get(i).getName();

                if (resultString.contains(".json")) {

                    downloadFile(context, companyName, resultString);

                    AppConstant.tripList.add(resultString.substring(0, resultString.length() - 5));
                }
            }

        } catch(Exception e) {

            e.printStackTrace();
        }
    }


    public static void downloadFile(Context context, String companyName, String tripName) {

        try {

            try (OutputStream outputStream = new FileOutputStream(new File(context.getFilesDir(), tripName))) {

                Log.i("Dropbox", "Download starting...");

                getClient().files().downloadBuilder("/Company/" + companyName + "/" + tripName).download(outputStream);

                Log.i("Dropbox", "Download completed.");
            }

        } catch (DownloadErrorException e) {

            e.printStackTrace();

            /*Handler handler = new Handler(Looper.getMainLooper());

            handler.post(new Runnable() {
                @Override
                public void run() {
                    //Toast.makeText(context, "Company name not found.", Toast.LENGTH_LONG).show();
                }
            });*/

        } catch (DbxException | IOException e) {
            e.printStackTrace();
        }
    }

}