package com.clone.EasyDelivery.Utility.operations;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import com.clone.EasyDelivery.Utility.AppConstant;
import com.clone.EasyDelivery.Utility.TripCacheManager;
import com.clone.EasyDelivery.Database.DeliveryDb;
import java.io.File;
import java.util.ArrayList;

/**
 * 🗑️ Operation for removing trips that are no longer available on Dropbox
 * 
 * This operation cleans up local files and cache entries for trips that
 * have been deleted from the Dropbox source.
 */
public class RemoveTripOperation extends SyncOperation {
    private static final String TAG = "RemoveTripOperation";
    
    public RemoveTripOperation(String tripId, JSONObject data) {
        super("REMOVE_TRIP", tripId, data);
    }
    
    @Override
    public SyncResult executeOnline(Context context) {
        try {
            Log.i(TAG, "🗑️ Online remove trip: " + getTripId());
            
            // 🚽 CRITICAL SAFETY CHECK: Protect trips with partial completions
            if (!isSafeToRemove(context)) {
                String reason = "Trip has completed deliveries or is in progress - removal blocked for safety";
                Log.w(TAG, "⚠️ SAFETY BLOCK: " + reason + " (" + getTripId() + ")");
                return SyncResult.failure(reason);
            }
            
            // Clean up database entries FIRST (most critical)
            cleanupDatabaseEntries(context);
            
            // Remove from local cache
            removeFromLocalCache(context);
            
            // Clean up local files
            cleanupLocalFiles(context);
            
            // Remove from in-memory lists
            removeFromMemoryLists();
            
            Log.i(TAG, "✅ Trip removed successfully: " + getTripId());
            return SyncResult.success("Trip completely removed from local storage and database");
            
        } catch (Exception e) {
            Log.e(TAG, "Error in online removal operation for trip: " + getTripId(), e);
            return SyncResult.failure("Online removal failed: " + e.getMessage());
        }
    }
    
    @Override
    public SyncResult executeOffline(Context context) {
        try {
            Log.i(TAG, "🗑️ Offline remove trip: " + getTripId());
            
            // 🚽 CRITICAL SAFETY CHECK: Protect trips with partial completions
            if (!isSafeToRemove(context)) {
                String reason = "Trip has completed deliveries or is in progress - removal blocked for safety";
                Log.w(TAG, "⚠️ SAFETY BLOCK: " + reason + " (" + getTripId() + ")");
                return SyncResult.failure(reason);
            }
            
            // Same comprehensive cleanup for offline mode - this is a local-only operation
            cleanupDatabaseEntries(context);
            removeFromLocalCache(context);
            cleanupLocalFiles(context);
            removeFromMemoryLists();
            
            return SyncResult.success("Trip completely removed offline");
        } catch (Exception e) {
            return SyncResult.failure("Offline removal failed: " + e.getMessage());
        }
    }
    
    /**
     * 🚽 Comprehensive safety check to determine if trip can be safely removed
     * Returns false if the trip has ANY completed deliveries or progress
     */
    private boolean isSafeToRemove(Context context) {
        try {
            DeliveryDb database = new DeliveryDb(context);
            database.open();
            
            try {
                // 🔧 FIXED: Check if this is a cleanup operation for completed trips
                boolean isCompletedTripCleanup = false;
                try {
            JSONObject operationData = getData();
                    if (operationData != null && operationData.has("cleanup_type") && 
                        "completed_trip_cleanup".equals(operationData.getString("cleanup_type"))) {
                        isCompletedTripCleanup = true;
                    }
                } catch (Exception e) {
                    // Ignore JSON parsing errors
                }
                
                // Check 1: If this is completed trip cleanup, allow removal of completed trips
                if (AppConstant.completedTrips.contains(getTripId())) {
                    if (isCompletedTripCleanup) {
                        Log.d(TAG, "🧹 Trip " + getTripId() + " is completed and eligible for cleanup - SAFE to remove");
                        return true;
                    } else {
                        Log.d(TAG, "🚽 Trip " + getTripId() + " is in completedTrips - PROTECTED from removal");
                        return false;
                    }
                }
                
                // Check 2: Is trip currently active/being worked on?
                if (getTripId().equals(AppConstant.TRIPID) || getTripId().equals(AppConstant.STARTED_TRIP)) {
                    Log.d(TAG, "🚽 Trip " + getTripId() + " is currently active - PROTECTED from removal");
                    return false;
                }
                
                // Check 3: Is trip in in-progress list?
                if (AppConstant.inProgressTrips.contains(getTripId())) {
                    Log.d(TAG, "🚽 Trip " + getTripId() + " is in progress - PROTECTED from removal");
                    return false;
                }
                
                // Check 4: Has trip been started (any completed deliveries)?
                if (database.tripStarted(getTripId())) {
                    Log.d(TAG, "🚽 Trip " + getTripId() + " has completed deliveries - PROTECTED from removal");
                    return false;
                }
                
                // Check 5: Does trip have any database entries at all?
                if (database.tripDataExists(getTripId())) {
                    Log.d(TAG, "🚽 Trip " + getTripId() + " has database entries - checking if safe...");
                    
                    // If it has data but hasn't been started, it might be safe
                    // But let's be extra cautious and keep it if it has unsent emails or incomplete uploads
                    if (database.isDataSynced(getTripId())) {
                        Log.d(TAG, "🚽 Trip " + getTripId() + " has unsynced data - PROTECTED from removal");
                        return false;
                    }
                }
                
                // Check 6: Is trip fully completed (all deliveries done and synced)?
                if (database.isTripFullyCompleted(getTripId())) {
                    // ✅ ALLOW REMOVAL: Trip is fully completed and synced
                    // All data is safely stored in Dropbox and emails sent
                    Log.i(TAG, "✅ Trip " + getTripId() + " is fully completed and synced - SAFE to remove for cleanup");
                    return true; // Safe to remove completed trips
                }
                
                // Passed all safety checks - safe to remove
                Log.i(TAG, "✅ Trip " + getTripId() + " passed all safety checks - SAFE to remove");
                return true;
                
            } finally {
                database.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking if trip is safe to remove: " + getTripId(), e);
            // If we can't determine safety, err on the side of caution
            return false;
        }
    }
    
    /**
     * 🗄️ Clean up all database entries for the trip
     * This removes ALL delivery data, parcel data, sync metadata, and email records
     */
    private void cleanupDatabaseEntries(Context context) {
        try {
            DeliveryDb database = new DeliveryDb(context);
            database.open();
            
            try {
                // This removes ALL entries for the trip:
                // - Delivery table (all deliveries for the trip)
                // - Parcel table (all parcels for the trip)
                // - Sync table (sync metadata)
                // - Email table (email send records)
                // - Sync metadata table (enhanced sync tracking)
                database.deleteData(getTripId());
                
                Log.i(TAG, "🗄️ Removed ALL database entries for trip: " + getTripId());
                
            } finally {
                database.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error cleaning up database entries for trip: " + getTripId(), e);
        }
    }
    
    /**
     * Remove trip from local cache
     */
    private void removeFromLocalCache(Context context) {
        try {
            TripCacheManager cacheManager = TripCacheManager.getInstance(context);
            cacheManager.removeTripFromCache(getTripId());
            Log.d(TAG, "Removed trip from cache: " + getTripId());
        } catch (Exception e) {
            Log.w(TAG, "Error removing trip from cache: " + getTripId(), e);
        }
    }
    
    /**
     * Clean up local trip files
     */
    private void cleanupLocalFiles(Context context) {
        try {
            // Remove main trip file
            File tripFile = new File(context.getFilesDir() + "/Trip/", getTripId() + ".json");
            if (tripFile.exists()) {
                boolean deleted = tripFile.delete();
                Log.d(TAG, "Deleted trip file: " + getTripId() + " (success: " + deleted + ")");
            }
            
            // Remove backup file if exists
            File backupFile = new File(context.getFilesDir() + "/Trip/", getTripId() + ".json.backup");
            if (backupFile.exists()) {
                backupFile.delete();
            }
            
            // Remove any return files associated with this trip
            File returnFile = new File(context.getFilesDir() + "/Return/", getTripId() + ".json");
            if (returnFile.exists()) {
                returnFile.delete();
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error cleaning up local files for trip: " + getTripId(), e);
        }
    }
    
    /**
     * Remove trip from in-memory lists
     */
    private void removeFromMemoryLists() {
        try {
            // Remove from downloaded trips list
            if (AppConstant.downloadedTrips.contains(getTripId())) {
                AppConstant.downloadedTrips.remove(getTripId());
                Log.d(TAG, "Removed trip from downloaded list: " + getTripId());
            }
            
            // Remove from claimed trips list
            if (AppConstant.claimedTrips.contains(getTripId())) {
                AppConstant.claimedTrips.remove(getTripId());
                Log.d(TAG, "Removed trip from claimed list: " + getTripId());
            }
            
            // Remove from in-progress trips list
            if (AppConstant.inProgressTrips.contains(getTripId())) {
                AppConstant.inProgressTrips.remove(getTripId());
                Log.d(TAG, "Removed trip from in-progress list: " + getTripId());
            }
            
            // Note: We don't remove from completedTrips as those should remain for record keeping
            
            // Clear active trip if it matches
            if (getTripId().equals(AppConstant.TRIPID)) {
                AppConstant.TRIPID = "";
                Log.d(TAG, "Cleared active trip ID: " + getTripId());
            }
            
            if (getTripId().equals(AppConstant.STARTED_TRIP)) {
                AppConstant.STARTED_TRIP = "";
                Log.d(TAG, "Cleared started trip ID: " + getTripId());
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error removing trip from memory lists: " + getTripId(), e);
        }
    }
    
    @Override
    public int getPriority() {
        return 1; // Low priority - cleanup operations
    }
}