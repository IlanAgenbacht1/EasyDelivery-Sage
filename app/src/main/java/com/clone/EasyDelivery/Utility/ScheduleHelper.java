package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.clone.EasyDelivery.Activity.TripDash;
import com.clone.EasyDelivery.Database.DeliveryDb;
import com.clone.EasyDelivery.Model.Delivery;
import com.clone.EasyDelivery.Utility.UnifiedTripManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import com.dropbox.core.v2.DbxClientV2;

import com.dropbox.core.v2.DbxClientV2;

public class ScheduleHelper {

    private static int documentQty;

    static boolean internetConnected;

    public static void getSchedule(Context context, String trip) {

        parseAndInsertScheduleData(context, trip);
    }

    private static void parseAndInsertScheduleData(Context context, String trip) {

        Delivery delivery = new Delivery();

        JSONObject jsonData = JsonHandler.readFile(context, trip);

        documentQty = 0;

        if (jsonData != null) {

            try {

                AppConstant.documentList.clear();

                //Continue parsing and inserting data

                JSONArray stops = jsonData.getJSONArray("stops");

                for (int i = 0; i < stops.length(); i++) {

                    JSONObject stop = stops.getJSONObject(i);
                    String documentNumber = stop.getString("documentNumber");
                    String orderNumber = stop.optString("orderNumber", ""); // Use optString to handle missing field gracefully
                    AppConstant.documentList.add(documentNumber);

                    JSONObject customer = stop.getJSONObject("customer");
                    String customerName = customer.getString("name");
                    String customerContactName = customer.getString("contactName");
                    String customerContact = customer.getString("contactNumber");

                    String address = stop.getString("address");

                    JSONObject gpsLocation = stop.getJSONObject("gpsLocation");
                    double latitude = gpsLocation.getDouble("latitude");
                    double longitude = gpsLocation.getDouble("longitude");

                    Location location = new Location("");
                    location.setLongitude(longitude);
                    location.setLatitude(latitude);

                    int numParcels = stop.getInt("numParcels");

                    JSONArray parcelNumbers = stop.getJSONArray("parcelNumbers");

                    List<String> parcelList = new ArrayList<>();

                    for (int j = 0; j < parcelNumbers.length(); j++) {

                        String parcelNumber = parcelNumbers.getString(j);

                        parcelList.add(parcelNumber);
                    }

                    delivery.setDocument(documentNumber);
                    delivery.setOrderNumber(orderNumber);
                    delivery.setTripId(AppConstant.TRIPID);
                    delivery.setCustomerName(customerName);
                    delivery.setAddress(address);
                    delivery.setContactName(customerContactName);
                    delivery.setContactNumber(customerContact);
                    delivery.setLocation(location);
                    delivery.setNumberOfParcels(numParcels);
                    delivery.setCompleted(false);
                    delivery.setUploaded(false);
                    delivery.setParcelNumbers(parcelList);

                    insertScheduleData(context, delivery);

                    documentQty++;
                }

                DeliveryDb database = new DeliveryDb(context);

                database.open();

                database.createSyncEntry(AppConstant.TRIPID, documentQty);

                database.close();

            } catch (Exception e) {
                Log.e("ScheduleHelper", "Error parsing and inserting schedule data: " + e.getMessage());
                //ToastLogger.exception(context, e);
            }
        }
    }


    private static void insertScheduleData(Context context, Delivery delivery) {
        try {
            DeliveryDb database = new DeliveryDb(context);

            database.open();

            //Check if document exists first.

            if (!documentValid(database, delivery.getDocument(), false)) {

                database.createScheduleEntry(delivery);

                Log.i("Document Table", "Document inserted.");

                for (String parcel : delivery.getParcelNumbers()) {

                    database.createParcelEntry(parcel, delivery.getDocument(), delivery.getTripId());

                    Log.i("Parcel Table", "Parcel inserted.");
                }
            }

            database.close();

        } catch (Exception e) {

            throw new RuntimeException(e);
        }
    }


    public static boolean documentValid(DeliveryDb database, String document, boolean isIncompleteDocument) {

        List<String> documentList = database.getDocumentList(isIncompleteDocument);

        for (int i = 0; i < documentList.size(); i++) {

            if (document.equals(documentList.get(i))) {

                Log.i("Document Table", "Document " + document + " already exists.");

                return true;
            }
        }

        return false;
    }


    public static ArrayList<String> getLocalTrips(Context context) {
        // Use Enhanced Sync for cloud-integrated trip discovery
        Log.i("Trip List", "Using Enhanced Sync cloud-integrated trip discovery");
        return getENHANCED_SYNCAvailableTrips(context);
    }
    
    /**
     * Unified Trip Discovery
     * Uses the unified trip manager for seamless trip discovery
     */
    private static ArrayList<String> getENHANCED_SYNCAvailableTrips(Context context) {
        ArrayList<String> finalTripList = new ArrayList<>();
        
        try {
            Log.i("UnifiedTripList", "Starting unified trip discovery");
            long startTime = System.currentTimeMillis();
            
            // Use UnifiedTripManager to get available trips
            UnifiedTripManager tripManager = UnifiedTripManager.getInstance(context);
            List<String> availableTrips = tripManager.getAvailableTrips();
            
            for (String tripId : availableTrips) {
                // Filter out completed trips
                if (!AppConstant.completedTrips.contains(tripId)) {
                    // Check if we have valid local data for this trip
                    File tripFile = new File(context.getFilesDir() + "/Trip/", tripId + ".json");
                    
                    if (tripFile.exists() && tripFile.length() > 0) {
                        finalTripList.add(tripId);
                        Log.d("UnifiedTripList", "Added available trip: " + tripId);
                    } else {
                        Log.d("UnifiedTripList", "Trip " + tripId + " available but missing locally - will download");
                        // Trip is available but not local - trigger download
                        triggerTripDownload(context, tripId);
                    }
                }
            }
            
            Collections.sort(finalTripList);
            
            long duration = System.currentTimeMillis() - startTime;
            Log.i("UnifiedTripList", "Unified trip discovery completed in " + duration + "ms - found " + finalTripList.size() + " available trips");
            
            // CLEANUP: Remove any local trip files that don't exist in Dropbox anymore  
            // Use the availableTrips we already got from Dropbox (don't make another network call!)
            simpleCleanupStaleTrips(context, availableTrips);
            
        } catch (Exception e) {
            Log.e("UnifiedTripList", "Error in unified trip discovery", e);
        }
        
        return finalTripList;
    }
    
    /**
     * LEGACY: Original file-based trip discovery
     * This method is preserved for fallback scenarios but should not be used
     * as the primary trip discovery mechanism in Enhanced Sync systems
     */
    private static ArrayList<String> getLegacyTrips(Context context) {
        ArrayList<String> finalTripList = new ArrayList<>();
        try {
            File tripDir = new File(context.getFilesDir() + "/Trip/");
            if (!tripDir.exists()) {
                return finalTripList;
            }

            String[] tripFiles = tripDir.list();
            if (tripFiles == null) {
                return finalTripList;
            }

            for (String fileName : tripFiles) {
                if (fileName.endsWith(".json") && !fileName.endsWith(".tmp")) {
                    String tripName = fileName.substring(0, fileName.length() - 5);
                    File currentFile = new File(tripDir, fileName);

                    if (currentFile.length() > 0 && !AppConstant.completedTrips.contains(tripName)) {
                        finalTripList.add(tripName);
                    }
                }
            }

            Collections.sort(finalTripList);
        } catch (Exception e) {
            Log.e("Trip List", "Error getting local trips", e);
        }
        return finalTripList;
    }
    
    /**
     * CONSOLIDATED: Trigger trip download via UnifiedTripManager
     * This eliminates race conditions by using the centralized download system
     */
    private static void triggerTripDownload(Context context, String tripId) {
        try {
            Log.i("ENHANCED_SYNCTripList", "Delegating download to UnifiedTripManager for: " + tripId);
            
            // Use UnifiedTripManager's synchronized download system
            UnifiedTripManager tripManager = UnifiedTripManager.getInstance(context);
            
            // The UnifiedTripManager will handle the download in the background
            // with proper synchronization and race condition protection
            tripManager.getAvailableTrips(); // This will trigger download if needed
            
        } catch (Exception e) {
            Log.w("ENHANCED_SYNCTripList", "Download delegation failed for " + tripId + ": " + e.getMessage());
        }
    }
    
    private static void cleanupStaleLocalTrips(Context context, ArrayList<String> cloudTripList) {
        try {
            File tripDir = new File(context.getFilesDir() + "/Trip/");
            if (!tripDir.exists()) {
                return;
            }
            
            String[] tripFiles = tripDir.list();
            if (tripFiles == null) {
                return;
            }
            
            int deletedCount = 0;
            for (String fileName : tripFiles) {
                if (fileName.endsWith(".json") && !fileName.endsWith(".tmp")) {
                    String tripName = fileName.substring(0, fileName.length() - 5);
                    
                    // Simple rule: If trip no longer exists in Dropbox, delete it
                    // BUT leave in_progress trips alone (they're being worked on)
                    if (!cloudTripList.contains(tripName) && 
                        !AppConstant.inProgressTrips.contains(tripName)) {
                        
                        File staleFile = new File(tripDir, fileName);
                        if (staleFile.delete()) {
                            deletedCount++;
                            Log.i("TripCleanup", "Deleted stale trip: " + tripName);
                        }
                    }
                }
            }
            
            if (deletedCount > 0) {
                Log.i("TripCleanup", "Cleaned up " + deletedCount + " stale trips");
            }
            
        } catch (Exception e) {
            Log.w("TripCleanup", "Error cleaning up stale trips", e);
        }
    }


    public static void deleteTripFile(Context context, String tripName) {
        try {

            File file = new File(context.getFilesDir() + "/Trip/", tripName + ".json");

            file.delete();

        } catch (Exception e) {
            Log.w("ScheduleHelper", "Error deleting trip file " + tripName + ": " + e.getMessage());
        }
    }
    
    /**
     * EMERGENCY: Force cleanup of all local trip cache
     * This can be called manually to resolve cache inconsistency issues
     */
    public static void forceCleanupLocalTripCache(Context context) {
        Log.i("UnifiedTripList", "EMERGENCY: Starting force cleanup of local trip cache");
        
        try {
            // Get fresh list of available trips from unified manager
            UnifiedTripManager tripManager = UnifiedTripManager.getInstance(context);
            List<String> freshTrips = tripManager.getAvailableTrips();
            ArrayList<String> freshCloudTrips = new ArrayList<>(freshTrips);
            
            Log.i("UnifiedTripList", "EMERGENCY: Found " + freshCloudTrips.size() + " trips available");
            
            // Clean up stale local files
            cleanupStaleLocalTrips(context, freshCloudTrips);
            
            Log.i("UnifiedTripList", "EMERGENCY: Force cleanup completed");
            
        } catch (Exception e) {
            Log.e("UnifiedTripList", "Error during force cleanup", e);
        }
    }
    
    /**
     * SIMPLE: Use the Dropbox trips we already fetched (no extra network call!)
     */
    private static void simpleCleanupStaleTrips(Context context, List<String> dropboxTrips) {
        try {
            Log.i("TripCleanup", "=== CLEANUP DEBUG ===");
            Log.i("TripCleanup", "Dropbox trips: " + dropboxTrips);
            Log.i("TripCleanup", "InProgress trips: " + AppConstant.inProgressTrips);
            
            File tripDir = new File(context.getFilesDir() + "/Trip/");
            if (!tripDir.exists()) {
                Log.i("TripCleanup", "Trip directory doesn't exist");
                return;
            }
            
            String[] localFiles = tripDir.list();
            if (localFiles == null) {
                Log.i("TripCleanup", "No local files found");
                return;
            }
            
            Log.i("TripCleanup", "Found " + localFiles.length + " local files");
            
            int deletedCount = 0;
            for (String fileName : localFiles) {
                Log.d("TripCleanup", "Checking file: " + fileName);
                
                if (fileName.endsWith(".json") && !fileName.endsWith(".tmp")) {
                    String tripName = fileName.substring(0, fileName.length() - 5);
                    Log.i("TripCleanup", "Local trip: " + tripName);
                    
                    boolean inDropbox = dropboxTrips.contains(tripName);
                    boolean inProgress = AppConstant.inProgressTrips.contains(tripName);
                    
                    Log.i("TripCleanup", "Trip " + tripName + ": inDropbox=" + inDropbox + ", inProgress=" + inProgress);
                    
                    // Delete if: Not in Dropbox AND not in_progress
                    if (!inDropbox && !inProgress) {
                        Log.i("TripCleanup", "ATTEMPTING TO DELETE: " + tripName);
                        
                        File staleFile = new File(tripDir, fileName);
                        if (staleFile.delete()) {
                            deletedCount++;
                            Log.i("TripCleanup", "SUCCESS: DELETED stale trip: " + tripName);
                        } else {
                            Log.e("TripCleanup", "FAILED to delete: " + tripName);
                        }
                    } else {
                        Log.i("TripCleanup", "KEEPING trip: " + tripName + " (reason: inDropbox=" + inDropbox + ", inProgress=" + inProgress + ")");
                    }
                } else {
                    Log.d("TripCleanup", "Skipping non-json file: " + fileName);
                }
            }
            
            Log.i("TripCleanup", "=== CLEANUP RESULT: Deleted " + deletedCount + " stale trips ===");
            
        } catch (Exception e) {
            Log.e("TripCleanup", "Error in cleanup", e);
        }
    }

}
