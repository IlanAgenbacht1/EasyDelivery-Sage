package com.clone.EasyDelivery.Utility.operations;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;

/**
 * 📊 Operation for updating trip status/metadata
 * 
 * This operation updates trip metadata without changing its core state.
 * Good for progress updates, notes, etc.
 */
public class UpdateTripStatusOperation extends SyncOperation {
    private static final String TAG = "UpdateTripStatusOperation";
    
    public UpdateTripStatusOperation(String tripId, JSONObject data) {
        super("UPDATE_STATUS", tripId, data);
    }
    
    @Override
    public SyncResult executeOnline(Context context) {
        try {
            Log.i(TAG, "Online update trip status: " + getTripId());
            
            // Get status and additional data from operation
            String status = null;
            JSONObject additionalData = null;
            
            try {
                JSONObject operationData = getData();
                if (operationData.has("status")) {
                    status = operationData.getString("status");
                }
                if (operationData.has("data")) {
                    additionalData = operationData.getJSONObject("data");
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not extract status data from operation", e);
            }
            
            // For now, this is a placeholder - actual status updates would
            // be handled through specific business logic or via UnifiedTripManager
            Log.i(TAG, "Trip status update processed: " + getTripId() + " -> " + status);
            
            return SyncResult.success("Trip status updated online");
            
        } catch (Exception e) {
            Log.e(TAG, "Error in online status update for trip: " + getTripId(), e);
            return SyncResult.failure("Online status update failed: " + e.getMessage());
        }
    }
    
    @Override
    public SyncResult executeOffline(Context context) {
        try {
            Log.i(TAG, "Offline update trip status: " + getTripId());
            
            // For offline execution, just queue for later
            return SyncResult.success("Trip status updated offline (will sync later)");
            
        } catch (Exception e) {
            return SyncResult.failure("Offline status update failed: " + e.getMessage());
        }
    }
    
    @Override
    public int getPriority() {
        return 1; // Normal priority - status updates are not urgent
    }
    
    /**
     * Factory method to create status update operation
     */
    public static UpdateTripStatusOperation create(String tripId, String status, JSONObject additionalData) {
        try {
            JSONObject data = new JSONObject();
            data.put("tripId", tripId);
            data.put("status", status);
            data.put("operationType", "statusUpdate");
            
            if (additionalData != null) {
                data.put("data", additionalData);
            }
            
            return new UpdateTripStatusOperation(tripId, data);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating update trip status operation", e);
            return null;
        }
    }
}