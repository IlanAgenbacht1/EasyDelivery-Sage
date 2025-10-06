package com.clone.EasyDelivery.Utility.operations;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import com.clone.EasyDelivery.Utility.DropboxHelper;

/**
 * 🎯 Operation for claiming available trips
 * 
 * This operation moves a trip from the 'available' folder to the 'claimed' folder
 * and associates it with a specific device.
 */
public class ClaimTripOperation extends SyncOperation {
    private static final String TAG = "ClaimTripOperation";
    
    public ClaimTripOperation(String tripId, JSONObject data) {
        super("CLAIM_TRIP", tripId, data);
    }
    
    public static ClaimTripOperation create(String tripId, JSONObject data) {
        return new ClaimTripOperation(tripId, data);
    }
    
    @Override
    public SyncResult executeOnline(Context context) {
        try {
            Log.i(TAG, "Online claim trip: " + getTripId());
            
            // Get device ID from operation data
            String deviceId = null;
            try {
                JSONObject operationData = getData();
                deviceId = operationData.getString("deviceId");
            } catch (Exception e) {
                Log.e(TAG, "Error getting device ID from operation data", e);
                return SyncResult.failure("Missing device ID");
            }
            
            // Perform actual Dropbox trip claiming operation
            boolean claimSuccess = DropboxHelper.claimTripDirectly(context, getTripId(), deviceId);
            
            if (claimSuccess) {
                Log.i(TAG, "Trip claimed successfully: " + getTripId());
                return SyncResult.success("Trip claimed and moved to claimed folder");
            } else {
                Log.w(TAG, "Trip claim failed: " + getTripId());
                return SyncResult.failure("Failed to claim trip on Dropbox");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in online claim operation for trip: " + getTripId(), e);
            return SyncResult.failure("Online claim failed: " + e.getMessage());
        }
    }
    
    @Override
    public SyncResult executeOffline(Context context) {
        try {
            // Update local state immediately for optimistic UI updates
            Log.i(TAG, "Offline claim trip: " + getTripId());
            return SyncResult.success("Trip claimed offline (will sync later)");
        } catch (Exception e) {
            return SyncResult.failure("Offline claim failed: " + e.getMessage());
        }
    }
    
    @Override
    public int getPriority() {
        return 2; // High priority - user-initiated action
    }
}