package com.clone.DeliveryApp.Utility;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DownloadErrorException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DropboxHelper {

    private static String REFRESH_TOKEN = "uTGEl3_OaeEAAAAAAAAAAZVbfJWNL2fdJPvT7lyhrfHjTLGQP9UtIKVotmqrT_96";
    private static String APP_KEY = "s901k3tmrktbc58";
    private static String APP_SECRET = "ddef3k5ox2xsams";
    private static DbxClientV2 dropboxClient;


    private static DbxClientV2 GetClient() {

        if (dropboxClient == null) {

            DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/Apps/Granite ePOD").build();

            DbxCredential credential = new DbxCredential("", 0L, REFRESH_TOKEN, APP_KEY, APP_SECRET);

            dropboxClient = new DbxClientV2(config, credential);
        }

        return dropboxClient;
    }


    public static void DownloadFile(Context context, String companyName) {

        try {

            File file = new File(context.getFilesDir(), "trip.json");

            if (!file.exists()) {

                file.mkdirs();
            }

            try (OutputStream outputStream = new FileOutputStream(new File(file.getPath(), "trip.json"))) {

                Log.i("Dropbox", "Download starting...");

                GetClient().files().downloadBuilder("/Company/" + companyName + "/trip.json" ).download(outputStream);

                Log.i("Dropbox", "Download completed.");

                Handler handler = new Handler(Looper.getMainLooper());

                handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "Delivery Schedule Downloaded", Toast.LENGTH_LONG).show();
                        }
                });
            }

        } catch (DownloadErrorException e) {

            e.printStackTrace();

            Handler handler = new Handler(Looper.getMainLooper());

            handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "Company name not found.", Toast.LENGTH_LONG).show();
                }
            });

        } catch (DbxException | IOException e) {
            e.printStackTrace();
        }
    }

}