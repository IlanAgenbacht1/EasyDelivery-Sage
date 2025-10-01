package com.clone.EasyDelivery.Utility;

import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class ConnectionHelper {

    // Multiple HTTPS endpoints to check for internet connectivity
    private static final String[] CONNECTIVITY_URLS = {
        "https://clients3.google.com/generate_204",
        "https://www.google.com/generate_204",
        "https://connectivitycheck.gstatic.com/generate_204"
    };
    
    public static boolean isInternetConnected() {
        
        // Try multiple endpoints for better reliability
        for (String url : CONNECTIVITY_URLS) {
            if (checkSingleEndpoint(url)) {
                Log.d("Internet", "Internet connectivity confirmed via: " + url);
                return true;
            }
        }
        
        Log.w("Internet", "No internet connectivity detected from any endpoint");
        return false;
    }
    
    private static boolean checkSingleEndpoint(String urlString) {
        try {
            HttpURLConnection urlConnection = (HttpURLConnection) (new URL(urlString).openConnection());
            urlConnection.setRequestProperty("User-Agent", "Android");
            urlConnection.setRequestProperty("Connection", "close");
            urlConnection.setConnectTimeout(5000); // Increased timeout
            urlConnection.setReadTimeout(3000);
            urlConnection.connect();

            boolean connected = (urlConnection.getResponseCode() == 204 && urlConnection.getContentLength() == 0);
            urlConnection.disconnect();
            return connected;

        } catch (IOException e) {
            Log.d("Internet", "Connection check failed for " + urlString + ": " + e.getMessage());
            return false;
        }
    }

}
