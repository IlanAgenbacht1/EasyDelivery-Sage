package com.clone.EasyDelivery.Utility.operations;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

import com.clone.EasyDelivery.Utility.DropboxHelper;
import com.clone.EasyDelivery.Utility.AppConstant;
import com.clone.EasyDelivery.Database.DeliveryDb;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListFolderResult;

/**
 * DownloadTripsOperation - Downloads available trips from Dropbox
 * 
 * This operation handles the business logic for discovering and downloading
 * new trip files from the Dropbox available folder, with intelligent filtering
 * to avoid re-downloading completed trips.
 */
public class DownloadTripsOperation extends SyncOperation {
    
    private static final String TAG = "DownloadTripsOperation";
    private static final String CUSTOMER_PATH = "/Customers/" + AppConstant.COMPANY + "/";
    
    public DownloadTripsOperation(String operationId, JSONObject operationData) {
        super("DOWNLOAD_TRIPS", operationId, operationData);
    }
    
    /**
     * Create a new download trips operation
     */
    public static DownloadTripsOperation create() {
        try {
            String operationId = "download_trips_" + System.currentTimeMillis();
            JSONObject operationData = new JSONObject();
            operationData.put("operation_type", "download_trips");
            operationData.put("action", "sync_available_trips");
            
            return new DownloadTripsOperation(operationId, operationData);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to create download trips operation", e);
            return null;
        }
    }
    
    @Override
    public SyncResult executeOnline(Context context) {
        Log.i(TAG, "=== Starting trip download from Dropbox ===");
        
        try {
            ArrayList<String> dropboxTrips = new ArrayList<>();
            
            // Initialize database for trip validation
            DeliveryDb database = null;
            try {
                database = new DeliveryDb(context);
                database.open();
            } catch (Exception e) {
                Log.e(TAG, "Failed to open database for trip validation", e);
                return SyncResult.failure("Database initialization failed: " + e.getMessage());
            }
            
            try {
                // Get Dropbox client
                DbxClientV2 client = DropboxHelper.getClient(context);
                if (client == null) {
                    Log.e(TAG, "Cannot download trips - Dropbox client not available");
                    return SyncResult.failure("Dropbox client not available");
                }

                Log.i(TAG, "Fetching trips from Dropbox available folder...");
                
                // Look in the 'available' folder for trip files
                String availablePath = CUSTOMER_PATH + "available";
                ListFolderResult availableFiles = client.files().listFolder(availablePath);

                if (availableFiles == null || availableFiles.getEntries().isEmpty()) {
                    Log.w(TAG, "No available trip files found on Dropbox");
                    return SyncResult.success("No new trips available");
                }

                Log.i(TAG, "Found " + availableFiles.getEntries().size() + " entries in available folder");
                
                int downloadedCount = 0;
                
                for (int i = 0; i < availableFiles.getEntries().size(); i++) {
                    String fileName = availableFiles.getEntries().get(i).getName();
                    Log.i(TAG, "Processing available trip file: " + fileName);

                    if (fileName.contains(".json")) {
                        String tripId = fileName.substring(0, fileName.length() - 5);
                        dropboxTrips.add(tripId);
                        
                        // Enhanced validation: Check database in addition to in-memory lists
                        boolean shouldDownload = shouldDownloadTrip(database, tripId);
                        
                        Log.d(TAG, "Trip " + tripId + " download decision: " + shouldDownload);
                                
                        if (shouldDownload) {
                            Log.i(TAG, "New trip found on Dropbox, downloading: " + fileName);
                            boolean downloaded = downloadTripFile(context, client, fileName);
                            if (downloaded) {
                                downloadedCount++;
                            }
                        } else {
                            Log.d(TAG, "Skipping download of " + tripId + " - already processed or completed");
                        }
                    }
                }
                
                // Update AppConstant.downloadedTrips to reflect current Dropbox state
                updateDownloadedTripsState(dropboxTrips);
                
                String message = "Downloaded " + downloadedCount + " new trips. Total available: " + dropboxTrips.size();
                Log.i(TAG, message);
                return SyncResult.success(message);
                
            } finally {
                // Close database
                if (database != null && database.isOpen()) {
                    database.close();
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error downloading trips from Dropbox", e);
            return SyncResult.failure("Download failed: " + e.getMessage());
        }
    }
    
    @Override
    public SyncResult executeOffline(Context context) {
        Log.i(TAG, "Download trips operation queued for when online");
        return SyncResult.failure("Operation queued - requires internet connection");
    }
    
    /**
     * Determine if a trip should be downloaded based on current state
     */
    private boolean shouldDownloadTrip(DeliveryDb database, String tripId) {
        try {
            boolean inTripList = AppConstant.tripList.contains(tripId);
            boolean inCompletedList = AppConstant.completedTrips.contains(tripId);
            boolean isFullyCompleted = false;
            
            // Check database to see if trip is fully completed and synced
            if (database != null) {
                try {
                    isFullyCompleted = database.isTripFullyCompleted(tripId);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to check trip completion status in database for " + tripId, e);
                }
            }
            
            // Only download if:
            // 1. Not in trip list AND
            // 2. Not in completed list AND 
            // 3. Not fully completed in database
            boolean shouldDownload = !inTripList && !inCompletedList && !isFullyCompleted;
            
            Log.d(TAG, "Trip " + tripId + " validation - inTripList: " + inTripList + 
                      ", inCompletedList: " + inCompletedList + ", isFullyCompleted: " + isFullyCompleted + 
                      ", shouldDownload: " + shouldDownload);
            
            return shouldDownload;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking if trip should be downloaded: " + tripId, e);
            return false; // Default to not downloading on error
        }
    }
    
    /**
     * Download a specific trip file from Dropbox
     */
    private boolean downloadTripFile(Context context, DbxClientV2 client, String fileName) {
        try {
            DropboxHelper.downloadFile(context, fileName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to download trip file: " + fileName, e);
            return false;
        }
    }
    
    /**
     * Update the AppConstant.downloadedTrips list to reflect current Dropbox state
     */
    private void updateDownloadedTripsState(ArrayList<String> dropboxTrips) {
        try {
            Log.i(TAG, "Updating AppConstant.downloadedTrips. Previous state: " + AppConstant.downloadedTrips.toString());

            // Add any new trips from Dropbox
            for (String trip : dropboxTrips) {
                if (!AppConstant.downloadedTrips.contains(trip)) {
                    AppConstant.downloadedTrips.add(trip);
                }
            }

            // Remove trips that are no longer on Dropbox
            ArrayList<String> toRemove = new ArrayList<>();
            for (String trip : AppConstant.downloadedTrips) {
                if (!dropboxTrips.contains(trip)) {
                    toRemove.add(trip);
                }
            }

            if (!toRemove.isEmpty()) {
                Log.i(TAG, "Removing trips from AppConstant.downloadedTrips: " + toRemove.toString());
                AppConstant.downloadedTrips.removeAll(toRemove);
            }

            Log.i(TAG, "AppConstant.downloadedTrips updated. Current state: " + AppConstant.downloadedTrips.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating downloaded trips state", e);
        }
    }
    
    public String getDescription() {
        return "Download available trips from Dropbox";
    }
}
