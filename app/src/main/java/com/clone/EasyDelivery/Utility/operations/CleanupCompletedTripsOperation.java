package com.clone.EasyDelivery.Utility.operations;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import com.clone.EasyDelivery.Utility.AppConstant;
import com.clone.EasyDelivery.Utility.TripCacheManager;
import com.clone.EasyDelivery.Database.DeliveryDb;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 🧹 Operation for cleaning up fully completed and synced trips
 * 
 * This operation removes trips that are:
 * - Fully completed (all deliveries done)
 * - Fully synced (all data uploaded to Dropbox)
 * - All emails sent successfully
 * 
 * This helps maintain optimal app performance by removing old data
 * that's safely stored server-side.
 */
public class CleanupCompletedTripsOperation extends SyncOperation {
    private static final String TAG = "CleanupCompletedTripsOperation";
    
    // Age threshold for cleanup (default: 7 days)
    private static final long DEFAULT_AGE_THRESHOLD_MS = 7 * 24 * 60 * 60 * 1000L;
    
    public CleanupCompletedTripsOperation(JSONObject data) {
        super("CLEANUP_COMPLETED_TRIPS", null, data);
    }
    
    public static CleanupCompletedTripsOperation create() {
        JSONObject data = new JSONObject();
        try {
            data.put("operation", "cleanup_completed_trips");
            data.put("age_threshold_days", 7);
            data.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            Log.w(TAG, "Error creating operation data", e);
        }
        return new CleanupCompletedTripsOperation(data);
    }
    
    @Override
    public SyncResult executeOnline(Context context) {
        return performCleanup(context, "online");
    }
    
    @Override
    public SyncResult executeOffline(Context context) {
        return performCleanup(context, "offline");
    }
    
    private SyncResult performCleanup(Context context, String mode) {
        try {
            Log.i(TAG, "🧹 Starting cleanup of completed trips (" + mode + " mode)");
            
            List<String> candidateTrips = findCompletedTripsForCleanup(context);
            
            if (candidateTrips.isEmpty()) {
                Log.i(TAG, "✨ No completed trips found for cleanup");
                return SyncResult.success("No trips require cleanup");
            }
            
            int removedCount = 0;
            int protectedCount = 0;
            
            for (String tripId : candidateTrips) {
                if (cleanupCompletedTrip(context, tripId)) {
                    removedCount++;
                    Log.i(TAG, "🗑️ Cleaned up completed trip: " + tripId);
                } else {
                    protectedCount++;
                    Log.d(TAG, "🛡️ Trip protected from cleanup: " + tripId);
                }
            }
            
            // Update completedTrips list to remove cleaned up trips
            updateCompletedTripsList(context);
            
            String result = String.format("Cleanup complete: %d trips removed, %d protected", 
                removedCount, protectedCount);
            Log.i(TAG, "✅ " + result);
            
            return SyncResult.success(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error during completed trips cleanup", e);
            return SyncResult.failure("Cleanup failed: " + e.getMessage());
        }
    }
    
    /**
     * Find trips that are candidates for cleanup
     */
    private List<String> findCompletedTripsForCleanup(Context context) {
        List<String> candidates = new ArrayList<>();
        
        try {
            DeliveryDb database = new DeliveryDb(context);
            database.open();
            
            try {
                // Get all fully completed trips from database
                List<String> fullyCompletedTrips = database.getAllFullyCompletedTrips();
                
                for (String tripId : fullyCompletedTrips) {
                    // Additional age check - only clean up trips older than threshold
                    if (isTripOldEnoughForCleanup(context, tripId)) {
                        candidates.add(tripId);
                    }
                }
                
                Log.i(TAG, "🔍 Found " + candidates.size() + " trips eligible for cleanup out of " + 
                      fullyCompletedTrips.size() + " completed trips");
                
            } finally {
                database.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error finding completed trips for cleanup", e);
        }
        
        return candidates;
    }
    
    /**
     * Check if trip is old enough to be safely cleaned up
     */
    private boolean isTripOldEnoughForCleanup(Context context, String tripId) {
        try {
            // Check file modification time as a proxy for trip completion age
            File tripFile = new File(context.getFilesDir() + "/Trip/", tripId + ".json");
            if (tripFile.exists()) {
                long fileAge = System.currentTimeMillis() - tripFile.lastModified();
                return fileAge > DEFAULT_AGE_THRESHOLD_MS;
            }
            
            // If no file exists but trip is in database, it's old enough
            return true;
            
        } catch (Exception e) {
            Log.w(TAG, "Error checking trip age for: " + tripId, e);
            return false; // Conservative approach
        }
    }
    
    /**
     * Clean up a specific completed trip
     */
    private boolean cleanupCompletedTrip(Context context, String tripId) {
        try {
            // Use RemoveTripOperation for consistent cleanup
            JSONObject removalData = new JSONObject();
            removalData.put("tripId", tripId);
            removalData.put("reason", "Automated cleanup of completed trip");
            removalData.put("cleanup_type", "completed_trip_cleanup");
            
            RemoveTripOperation removalOp = new RemoveTripOperation(tripId, removalData);
            SyncResult result = removalOp.executeOnline(context);
            
            return result.success;
            
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up completed trip: " + tripId, e);
            return false;
        }
    }
    
    /**
     * Update the completedTrips list after cleanup
     */
    private void updateCompletedTripsList(Context context) {
        try {
            DeliveryDb database = new DeliveryDb(context);
            database.open();
            
            try {
                // Refresh the completedTrips list from database
                List<String> currentCompletedTrips = database.getAllFullyCompletedTrips();
                
                // Update in-memory list
                AppConstant.completedTrips.clear();
                AppConstant.completedTrips.addAll(currentCompletedTrips);
                
                Log.d(TAG, "🔄 Updated completedTrips list: " + currentCompletedTrips.size() + " trips remain");
                
            } finally {
                database.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating completedTrips list after cleanup", e);
        }
    }
    
    @Override
    public int getPriority() {
        return 1; // Low priority - maintenance operation
    }
}