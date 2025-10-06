package com.clone.EasyDelivery.Utility.operations;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import com.clone.EasyDelivery.Utility.DropboxHelper;
import com.clone.EasyDelivery.Utility.AppConstant;
// SyncConstant merged into AppConstant

/**
 * 🚀 Operation for starting claimed trips
 * 
 * This operation moves a trip from the 'claimed' folder to the 'in_progress' folder
 * and updates local state to reflect that work has begun.
 */
public class StartTripOperation extends SyncOperation {
    private static final String TAG = "StartTripOperation";
    
    public StartTripOperation(String tripId, JSONObject data) {
        super("START_TRIP", tripId, data);
    }
    
    public static StartTripOperation create(String tripId, JSONObject data) {
        return new StartTripOperation(tripId, data);
    }
    
    @Override
    public SyncResult executeOnline(Context context) {
        try {
            Log.i(TAG, "Online start trip: " + getTripId());
            
            // 🔍 SMART DETECTION: Check if trip was already completed offline
            // This handles mixed connectivity scenarios gracefully
            if (isTripAlreadyCompletedOffline(context)) {
                Log.i(TAG, "🎯 SMART SKIP: Trip " + getTripId() + " was already completed offline, skipping start operation");
                updateLocalStateForStartedTrip(); // Still update local state for consistency
                return SyncResult.success("Trip was already completed offline, start operation skipped");
            }
            
            // 🔍 SMART DETECTION: Check if trip is already completed in Dropbox
            if (DropboxHelper.isTripCompletedInDropbox(context, getTripId())) {
                Log.i(TAG, "🎯 SMART SKIP: Trip " + getTripId() + " already completed in Dropbox, skipping start operation");
                updateLocalStateForStartedTrip(); // Update local state for consistency
                return SyncResult.success("Trip already completed in Dropbox, start operation skipped");
            }
            
            // Proceed with normal start operation
            boolean startSuccess = DropboxHelper.startTripDirectly(context, getTripId());
            
            if (startSuccess) {
                // Update local state
                updateLocalStateForStartedTrip();
                
                Log.i(TAG, "Trip started successfully: " + getTripId());
                return SyncResult.success("Trip started and moved to in_progress folder");
            } else {
                Log.w(TAG, "Trip start failed: " + getTripId());
                return SyncResult.failure("Failed to start trip on Dropbox");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in online start operation for trip: " + getTripId(), e);
            return SyncResult.failure("Online start failed: " + e.getMessage());
        }
    }
    
    @Override
    public SyncResult executeOffline(Context context) {
        try {
            Log.i(TAG, "Offline start trip: " + getTripId());
            
            // Update local state optimistically
            updateLocalStateForStartedTrip();
            
            return SyncResult.success("Trip started offline (will sync later)");
        } catch (Exception e) {
            return SyncResult.failure("Offline start failed: " + e.getMessage());
        }
    }
    
    private void updateLocalStateForStartedTrip() {
        try {
            // Add to in-progress trips list
            if (!AppConstant.inProgressTrips.contains(getTripId())) {
                AppConstant.inProgressTrips.add(getTripId());
                Log.d(TAG, "Added trip to in-progress list: " + getTripId());
            }
            
            // Update sync constants
            if (AppConstant.STARTED_TRIP == null || AppConstant.STARTED_TRIP.isEmpty()) {
                AppConstant.STARTED_TRIP = getTripId();
                Log.d(TAG, "Updated started trip constant: " + getTripId());
            }
            
            // Remove from claimed trips if present
            AppConstant.claimedTrips.remove(getTripId());
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating local state for started trip", e);
        }
    }
    
    @Override
    public int getPriority() {
        return 2; // High priority - user-initiated action
    }
    
    /**
     * 🔍 Check if trip was already completed offline
     * This method detects various offline completion scenarios:
     * 1. Trip in local completed list
     * 2. Trip has all deliveries marked as completed in database
     * 3. Trip completion was synced but start operation got queued
     */
    private boolean isTripAlreadyCompletedOffline(Context context) {
        try {
            String tripId = getTripId();
            
            // Check 1: Is trip in local completed trips list?
            if (AppConstant.completedTrips.contains(tripId)) {
                Log.d(TAG, "🔍 Trip found in local completed list: " + tripId);
                return true;
            }
            
            // Check 2: Check database for completion status
            // This handles cases where trip was completed but not yet added to completed list
            if (isTripFullyCompletedInDatabase(context, tripId)) {
                Log.d(TAG, "🔍 Trip found fully completed in database: " + tripId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking offline completion status", e);
            return false;
        }
    }
    
    /**
     * 📊 Check if trip is fully completed in database
     * This method queries the database to see if all deliveries are marked as completed
     * Uses the existing DeliveryDb.isTripFullyCompleted method which checks:
     * 1. All deliveries are completed AND uploaded
     * 2. All emails are sent
     * 3. Data has been synced (document count matches sync count)
     */
    private boolean isTripFullyCompletedInDatabase(Context context, String tripId) {
        try {
            // Import the database class
            com.clone.EasyDelivery.Database.DeliveryDb database = new com.clone.EasyDelivery.Database.DeliveryDb(context);
            database.open();
            
            try {
                // Use existing comprehensive method to check if trip is fully completed
                boolean tripFullyCompleted = database.isTripFullyCompleted(tripId);
                
                if (tripFullyCompleted) {
                    Log.d(TAG, "📊 Database confirms trip is fully completed and synced: " + tripId);
                    return true;
                }
                
                return false;
                
            } finally {
                database.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking database completion status for trip: " + getTripId(), e);
            // If we can't check, assume not completed to be safe
            return false;
        }
    }
}
