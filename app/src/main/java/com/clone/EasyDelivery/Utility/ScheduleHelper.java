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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

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

                e.printStackTrace();

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
        // 🚀 Use Enhanced Sync for cloud-integrated trip discovery
        Log.i("Trip List", "🚀 Using Enhanced Sync cloud-integrated trip discovery");
        return getENHANCED_SYNCAvailableTrips(context);
    }
    
    /**
     * 🎯 Unified Trip Discovery
     * Uses the unified trip manager for seamless trip discovery
     */
    private static ArrayList<String> getENHANCED_SYNCAvailableTrips(Context context) {
        ArrayList<String> finalTripList = new ArrayList<>();
        
        try {
            Log.i("UnifiedTripList", "🎯 Starting unified trip discovery");
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
                        Log.d("UnifiedTripList", "✅ Added available trip: " + tripId);
                    } else {
                        Log.d("UnifiedTripList", "⚠️ Trip " + tripId + " available but missing locally - will download");
                        // Trip is available but not local - trigger download
                        triggerTripDownload(context, tripId);
                    }
                }
            }
            
            Collections.sort(finalTripList);
            
            long duration = System.currentTimeMillis() - startTime;
            Log.i("UnifiedTripList", "🎆 Unified trip discovery completed in " + duration + "ms - found " + finalTripList.size() + " available trips");
            
            // 🧹 CLEANUP: Remove any local trip files that don't exist anymore
            cleanupStaleLocalTrips(context, finalTripList);
            
        } catch (Exception e) {
            Log.e("UnifiedTripList", "Error in unified trip discovery", e);
        }
        
        return finalTripList;
    }
    
    /**
     * 📶 LEGACY: Original file-based trip discovery
     * This method is preserved for fallback scenarios but should not be used
     * as the primary trip discovery mechanism in Enhanced Sync systems
     */
    private static ArrayList<String> getLegacyTrips(Context context) {
        ArrayList<String> finalTripList = new ArrayList<>();
        try {
            Log.i("Trip List", "📶 LEGACY: Fetching trips from local files only...");

            File tripDir = new File(context.getFilesDir() + "/Trip/");
            if (!tripDir.exists()) {
                Log.w("Trip List", "Trip directory does not exist.");
                return finalTripList;
            }

            // SIMPLIFIED: Just get all valid local trip files
            String[] tripFiles = tripDir.list();
            if (tripFiles == null) {
                Log.w("Trip List", "No files found in trip directory.");
                return finalTripList;
            }

            // Add all valid local trips (skip complex filtering that was removing trips)
            for (String fileName : tripFiles) {
                if (fileName.endsWith(".json") && !fileName.endsWith(".tmp")) {
                    String tripName = fileName.substring(0, fileName.length() - 5);
                    File currentFile = new File(tripDir, fileName);

                    // Only exclude if file is empty or trip is actually completed
                    if (currentFile.length() > 0 && !AppConstant.completedTrips.contains(tripName)) {
                        finalTripList.add(tripName);
                        Log.d("Trip List", "Added local trip: " + tripName);
                    }
                }
            }

            Collections.sort(finalTripList);
            Log.i("Trip List", "📶 LEGACY: Found " + finalTripList.size() + " local trips: " + finalTripList.toString());

        } catch (Exception e) {
            Log.e("Trip List", "Error getting local trips", e);
        }
        return finalTripList;
    }
    
    /**
     * 🚀 Enhanced Sync: Trigger download of a trip from available folder
     */
    private static void triggerTripDownload(Context context, String tripId) {
        Log.i("ENHANCED_SYNCTripList", "📥 Triggering Enhanced Sync download for trip: " + tripId);
        
        // Run download in background thread
        Thread downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Download from Enhanced Sync available folder path
                    downloadTripFromEnhancedSync(context, tripId);
                    Log.i("ENHANCED_SYNCTripList", "✅ Downloaded trip file: " + tripId);
                } catch (Exception e) {
                    Log.e("ENHANCED_SYNCTripList", "Failed to download trip: " + tripId, e);
                }
            }
        });
        downloadThread.start();
    }
    
    /**
     * Download trip file from Enhanced Sync available folder
     */
    private static void downloadTripFromEnhancedSync(Context context, String tripId) throws Exception {
        DbxClientV2 client = DropboxHelper.getClient(context);
        if (client == null) {
            throw new Exception("Dropbox client not available");
        }
        
        // Enhanced Sync path: /Customers/CompanyName/available/TripId.json
        String customerPath = "/Customers/" + AppConstant.COMPANY + "/";
        String availablePath = customerPath + "available/" + tripId + ".json";
        
        File tripDir = new File(context.getFilesDir() + "/Trip/");
        if (!tripDir.exists()) {
            tripDir.mkdirs();
        }
        
        File finalFile = new File(tripDir, tripId + ".json");
        File tempFile = new File(tripDir, tripId + ".json.tmp");
        
        // Download to a temporary file first
        try (OutputStream outputStream = new FileOutputStream(tempFile)) {
            Log.d("ENHANCED_SYNCTripList", "Downloading from: " + availablePath);
            client.files().downloadBuilder(availablePath).download(outputStream);
        }
        
        // Atomically rename the temp file to the final file
        if (tempFile.renameTo(finalFile)) {
            Log.d("ENHANCED_SYNCTripList", "Successfully saved trip file: " + tripId);
        } else {
            // Clean up temp file on failure
            if (tempFile.exists()) {
                tempFile.delete();
            }
            throw new Exception("Failed to rename temporary file for: " + tripId);
        }
    }
    
    /**
     * 🧹 CLEANUP: Remove any local trip files that don't exist in the cloud anymore
     * This ensures local cache is consistent with cloud source of truth
     */
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
            
            Log.i("ENHANCED_SYNCTripList", "🧹 CLEANUP: Checking " + tripFiles.length + " local files against " + cloudTripList.size() + " cloud trips");
            
            int deletedCount = 0;
            for (String fileName : tripFiles) {
                if (fileName.endsWith(".json") && !fileName.endsWith(".tmp")) {
                    String tripName = fileName.substring(0, fileName.length() - 5);
                    
                    // Delete local file if:
                    // 1. Trip doesn't exist in cloud anymore AND
                    // 2. Trip is not in completed list (don't delete completed trip data)
                    if (!cloudTripList.contains(tripName) && !AppConstant.completedTrips.contains(tripName)) {
                        File staleFile = new File(tripDir, fileName);
                        if (staleFile.delete()) {
                            deletedCount++;
                            Log.i("ENHANCED_SYNCTripList", "🗑️ CLEANUP: Deleted stale local file: " + fileName);
                        } else {
                            Log.w("ENHANCED_SYNCTripList", "⚠️ CLEANUP: Failed to delete stale file: " + fileName);
                        }
                    } else if (cloudTripList.contains(tripName)) {
                        Log.d("ENHANCED_SYNCTripList", "✅ CLEANUP: Keeping valid local file: " + fileName);
                    } else {
                        Log.d("ENHANCED_SYNCTripList", "🛡️ CLEANUP: Preserving completed trip file: " + fileName);
                    }
                }
            }
            
            if (deletedCount > 0) {
                Log.i("ENHANCED_SYNCTripList", "✅ CLEANUP: Deleted " + deletedCount + " stale local trip files");
            } else {
                Log.d("ENHANCED_SYNCTripList", "✅ CLEANUP: No stale files found");
            }
            
        } catch (Exception e) {
            Log.w("ENHANCED_SYNCTripList", "Error cleaning up stale local trips", e);
        }
    }


    public static void deleteTripFile(Context context, String tripName) {
        try {

            File file = new File(context.getFilesDir() + "/Trip/", tripName + ".json");

            file.delete();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
    
    /**
     * 🎯 EMERGENCY: Force cleanup of all local trip cache
     * This can be called manually to resolve cache inconsistency issues
     */
    public static void forceCleanupLocalTripCache(Context context) {
        Log.i("UnifiedTripList", "🎯 EMERGENCY: Starting force cleanup of local trip cache");
        
        try {
            // Get fresh list of available trips from unified manager
            UnifiedTripManager tripManager = UnifiedTripManager.getInstance(context);
            List<String> freshTrips = tripManager.getAvailableTrips();
            ArrayList<String> freshCloudTrips = new ArrayList<>(freshTrips);
            
            Log.i("UnifiedTripList", "🎯 EMERGENCY: Found " + freshCloudTrips.size() + " trips available");
            
            // Clean up stale local files
            cleanupStaleLocalTrips(context, freshCloudTrips);
            
            Log.i("UnifiedTripList", "✅ EMERGENCY: Force cleanup completed");
            
        } catch (Exception e) {
            Log.e("UnifiedTripList", "Error during force cleanup", e);
        }
    }

}
