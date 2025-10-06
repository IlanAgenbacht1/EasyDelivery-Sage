package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

/**
 * ConnectionHelper - Simple network connectivity utilities
 */
public class ConnectionHelper {
    
    private static final String TAG = "ConnectionHelper";
    
    /**
     * Check if device has internet connectivity
     */
    public static boolean isInternetConnected() {
        // For now, we'll assume connected since we don't have context
        // This method should be refactored to accept context parameter
        return true;
    }
    
    /**
     * Check if device has internet connectivity with context
     */
    public static boolean isInternetConnected(Context context) {
        if (context == null) {
            return false;
        }
        
        try {
            ConnectivityManager connectivityManager = 
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                
            if (connectivityManager == null) {
                return false;
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork == null) {
                    return false;
                }
                
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                return capabilities != null && 
                       (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
            } else {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking network connectivity", e);
            return false;
        }
    }
}