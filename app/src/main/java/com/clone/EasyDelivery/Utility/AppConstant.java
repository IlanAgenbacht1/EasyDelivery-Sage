package com.clone.EasyDelivery.Utility;


import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.clone.EasyDelivery.Model.ItemParcel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class AppConstant {

    public static String DOCUMENT=" ";
    public static String PARCEL_NO=" ";
    public static String COMPANY = " ";
    public static String SIGN_PATH = " ";
    public static String PIC_PATH = " ";
    public static String ZOOM = " ";
    public static Location GPS_LOCATION;
    public static String TRIPID = "";
    public static String EMAIL = "";
    public static String DRIVER = "";
    public static String VEHICLE = "";
    public static String COMMENT = "";
    
    // Sync constants (moved from SyncConstant) - avoiding duplicates with existing constants
    public static String COMPLETED_TRIP_ID = "";
    public static String STARTED_TRIP = "";
    public static String IN_PROGRESS_TRIP = "";
    public static String TRIP_NAME = "";
    public static String DOCUMENT_FILE_PATH = "";
    // Note: DOCUMENT and TRIP_ID already exist above as DOCUMENT and TRIPID

    public static boolean PARCEL_VALIDATION;
    public static String PARCEL_INPUT = "";
    public static int PARCEL_POSITION;
    public static int tripCount = 0;

    public static ArrayList<Location> gpsList = new ArrayList<>();
    public static ArrayList<String> documentList = new ArrayList<>();
    public static ArrayList<String> validatedParcels = new ArrayList<>();
    public static ArrayList<String> uiValidatedParcels = new ArrayList<>();
    public static ArrayList<String> discrepancyParcels = new ArrayList<>();
    public static ArrayList<String> flaggedParcels = new ArrayList<>();
    public static ArrayList<String> tripList = new ArrayList<>();
    public static ArrayList<String> completedTrips = new ArrayList<>();
    public static List<String> downloadedTrips = new CopyOnWriteArrayList<>();
    public static List<String> inProgressTrips = new CopyOnWriteArrayList<>();
    public static List<String> claimedTrips = new CopyOnWriteArrayList<>();

    public static ArrayList<Integer> removedTripPosList = new ArrayList<>();

    public static String SAVED_DOCUMENT;
    
    // 💾 Offline persistence constants
    private static final String PREFS_NAME = "EasyDeliveryCache";
    private static final String COMPLETED_TRIPS_KEY = "completed_trips";
    private static final String DOWNLOADED_TRIPS_KEY = "downloaded_trips";
    private static final String IN_PROGRESS_TRIPS_KEY = "in_progress_trips";
    
    /**
     * 💾 Save completed trips to persistent storage
     * This ensures completed trips are preserved across app restarts, even when offline
     */
    public static void saveCompletedTripsToStorage(Context context) {
        if (context == null) return;
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            // Convert ArrayList to Set for SharedPreferences
            Set<String> completedSet = new HashSet<>(completedTrips);
            editor.putStringSet(COMPLETED_TRIPS_KEY, completedSet);
            
            // Also save other trip lists for comprehensive caching
            Set<String> downloadedSet = new HashSet<>(downloadedTrips);
            editor.putStringSet(DOWNLOADED_TRIPS_KEY, downloadedSet);
            
            Set<String> inProgressSet = new HashSet<>(inProgressTrips);
            editor.putStringSet(IN_PROGRESS_TRIPS_KEY, inProgressSet);
            
            editor.apply();
            
            Log.d("AppConstant", "💾 Saved " + completedTrips.size() + " completed trips to persistent storage");
            
        } catch (Exception e) {
            Log.e("AppConstant", "Error saving completed trips to storage", e);
        }
    }
    
    /**
     * 📥 Load completed trips from persistent storage
     * This restores completed trips after app restart, ensuring offline data persistence
     */
    public static void loadCompletedTripsFromStorage(Context context) {
        if (context == null) return;
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            // Load completed trips
            Set<String> completedSet = prefs.getStringSet(COMPLETED_TRIPS_KEY, new HashSet<>());
            completedTrips.clear();
            completedTrips.addAll(completedSet);
            
            // Load other trip lists
            Set<String> downloadedSet = prefs.getStringSet(DOWNLOADED_TRIPS_KEY, new HashSet<>());
            downloadedTrips.clear();
            downloadedTrips.addAll(downloadedSet);
            
            Set<String> inProgressSet = prefs.getStringSet(IN_PROGRESS_TRIPS_KEY, new HashSet<>());
            inProgressTrips.clear();
            inProgressTrips.addAll(inProgressSet);
            
            Log.d("AppConstant", "📥 Loaded " + completedTrips.size() + " completed trips from persistent storage");
            
        } catch (Exception e) {
            Log.e("AppConstant", "Error loading completed trips from storage", e);
        }
    }
    
    /**
     * ✅ Mark a trip as completed and persist the change
     * This ensures the completion is saved immediately and survives app restarts
     * 🔧 ENHANCED: Added special handling for problem trips like ORD000009
     */
    public static void markTripCompleted(Context context, String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) return;
        
        try {
            boolean wasAlreadyCompleted = completedTrips.contains(tripId);
            
            if (!wasAlreadyCompleted) {
                completedTrips.add(tripId);
                Log.i("AppConstant", "✅ MARKED COMPLETED: " + tripId);
                
                // 🔧 SPECIAL MONITORING: Extra logging for problematic trips
                if (tripId.equals("ORD000009") || tripId.startsWith("ORD000")) {
                    Log.w("AppConstant", "🔍 PROBLEM TRIP WATCH: Completed " + tripId + " - ensuring persistence");
                    Log.w("AppConstant", "📈 COMPLETION CONTEXT - InProgress: " + inProgressTrips.contains(tripId) + ", Downloaded: " + downloadedTrips.contains(tripId));
                }
            } else {
                Log.d("AppConstant", "🔄 ALREADY COMPLETED: " + tripId + " (no duplicate marking)");
            }
            
            // Remove from other lists if present (with extra logging for problem trips)
            boolean wasInProgress = inProgressTrips.contains(tripId);
            boolean wasDownloaded = downloadedTrips.contains(tripId);
            
            downloadedTrips.remove(tripId);
            inProgressTrips.remove(tripId);
            
            if ((tripId.equals("ORD000009") || tripId.startsWith("ORD000")) && (wasInProgress || wasDownloaded)) {
                Log.i("AppConstant", "🔍 PROBLEM TRIP CLEANUP: Removed " + tripId + " from inProgress(" + wasInProgress + ") and downloaded(" + wasDownloaded + ") lists");
            }
            
            // Persist changes immediately with verification
            saveCompletedTripsToStorage(context);
            
            // 🔧 VERIFICATION: Double-check persistence for problem trips
            if (tripId.equals("ORD000009") || tripId.startsWith("ORD000")) {
                Log.i("AppConstant", "🔍 COMPLETION VERIFICATION: " + tripId + " in completed list: " + completedTrips.contains(tripId));
                Log.i("AppConstant", "📈 CURRENT COMPLETED TRIPS COUNT: " + completedTrips.size());
            }
            
        } catch (Exception e) {
            Log.e("AppConstant", "Error marking trip as completed: " + tripId, e);
            
            // 🚨 CRITICAL ERROR HANDLING: Extra alerting for problem trips
            if (tripId.equals("ORD000009") || tripId.startsWith("ORD000")) {
                Log.e("AppConstant", "🚨 CRITICAL: Failed to mark problem trip as completed: " + tripId);
            }
        }
    }
    
    /**
     * 🧹 Clear all cached trip data (for testing/debugging)
     */
    public static void clearAllTripCache(Context context) {
        if (context == null) return;
        
        try {
            // Clear in-memory lists
            completedTrips.clear();
            downloadedTrips.clear();
            inProgressTrips.clear();
            tripList.clear();
            
            // Clear persistent storage
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            editor.apply();
            
            Log.i("AppConstant", "🧹 Cleared all trip cache data");
            
        } catch (Exception e) {
            Log.e("AppConstant", "Error clearing trip cache", e);
        }
    }

}
