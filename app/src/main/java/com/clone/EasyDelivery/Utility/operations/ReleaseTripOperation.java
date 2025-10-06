package com.clone.EasyDelivery.Utility.operations;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import com.clone.EasyDelivery.Utility.DropboxHelper;
import com.clone.EasyDelivery.Utility.AppConstant;
// SyncConstant merged into AppConstant

/**
 * ↩️ Operation for releasing claimed or in-progress trips back to available
 * 
 * This operation moves a trip back to the 'available' folder and cleans up
 * any local state associated with the trip.
 */
public class ReleaseTripOperation extends SyncOperation {
    private static final String TAG = "ReleaseTripOperation";
    
    public ReleaseTripOperation(String tripId, JSONObject data) {
        super("RELEASE_TRIP", tripId, data);
    }
    
    public static ReleaseTripOperation create(String tripId, JSONObject data) {
        return new ReleaseTripOperation(tripId, data);
    }
    
    @Override
    public SyncResult executeOnline(Context context) {
        try {
            Log.i(TAG, "Online release trip: " + getTripId());
            
            // Get release reason from operation data
            String reason = "Manual release";
            try {
                JSONObject operationData = getData();
                if (operationData.has("reason")) {
                    reason = operationData.getString("reason");
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not extract release reason, using default", e);
            }
            
            // Move trip back to available folder (unclaim)
            boolean releaseSuccess = DropboxHelper.releaseTripDirectly(context, getTripId());
            
            if (releaseSuccess) {
                // Update local state
                updateLocalStateForReleasedTrip();
                
                Log.i(TAG, "Trip released successfully: " + getTripId() + " (reason: " + reason + ")");
                return SyncResult.success("Trip released and moved back to available folder");
            } else {
                Log.w(TAG, "Trip release failed: " + getTripId());
                return SyncResult.failure("Failed to release trip on Dropbox");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in online release operation for trip: " + getTripId(), e);
            return SyncResult.failure("Online release failed: " + e.getMessage());
        }
    }
    
    @Override
    public SyncResult executeOffline(Context context) {
        try {
            Log.i(TAG, "Offline release trip: " + getTripId());
            
            // Update local state optimistically
            updateLocalStateForReleasedTrip();
            
            return SyncResult.success("Trip released offline (will sync later)");
        } catch (Exception e) {
            return SyncResult.failure("Offline release failed: " + e.getMessage());
        }
    }
    
    private void updateLocalStateForReleasedTrip() {
        try {
            // Remove from all local trip lists
            AppConstant.claimedTrips.remove(getTripId());
            AppConstant.inProgressTrips.remove(getTripId());
            AppConstant.completedTrips.remove(getTripId());
            
            // Clear started trip constant if it matches
            if (getTripId().equals(AppConstant.STARTED_TRIP)) {
                AppConstant.STARTED_TRIP = "";
                Log.d(TAG, "Cleared started trip constant for released trip: " + getTripId());
            }
            
            // Clear current trip ID if it matches
            if (getTripId().equals(AppConstant.TRIPID)) {
                AppConstant.TRIPID = "";
                Log.d(TAG, "Cleared current trip ID for released trip: " + getTripId());
            }
            
            Log.d(TAG, "Updated local state for released trip: " + getTripId());
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating local state for released trip", e);
        }
    }
    
    @Override
    public int getPriority() {
        return 1; // Normal priority - cleanup operation
    }
}