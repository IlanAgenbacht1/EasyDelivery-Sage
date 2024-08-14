package com.clone.EasyDelivery.Utility;

import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class ConnectionHelper {

    public static boolean isInternetConnected() {

            try {

                HttpURLConnection urlConnection = (HttpURLConnection) (new URL("http://clients3.google.com/generate_204").openConnection());
                urlConnection.setRequestProperty("User-Agent", "Android");
                urlConnection.setRequestProperty("Connection", "close");
                urlConnection.setConnectTimeout(3000);
                urlConnection.connect();

                return (urlConnection.getResponseCode() == 204 && urlConnection.getContentLength() == 0);

            } catch (IOException e) {

                Log.e("Internet", "Error checking internet connection", e);
            }

        return false;
    }

}