package com.clone.EasyDelivery.Utility.operations;

import android.content.Context;
import org.json.JSONObject;

/**
 * 🔄 Base class for all sync operations
 * 
 * This abstract class defines the contract for operations that can be executed
 * both online (immediately) and offline (queued for later sync).
 * 
 * Key principles:
 * - Operations should be idempotent (safe to retry)
 * - Online execution should interact directly with cloud services
 * - Offline execution should update local state optimistically
 * - Operations should be serializable for queue persistence
 */
public abstract class SyncOperation {
    private final String type;
    private final String tripId;
    private final JSONObject data;
    private final long timestamp;
    
    public SyncOperation(String type, String tripId, JSONObject data) {
        this.type = type;
        this.tripId = tripId;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getType() { return type; }
    public String getTripId() { return tripId; }
    public JSONObject getData() { return data; }
    public long getTimestamp() { return timestamp; }
    
    /**
     * Execute operation when online (direct cloud service operation)
     */
    public abstract SyncResult executeOnline(Context context);
    
    /**
     * Execute operation when offline (local database update)
     */
    public abstract SyncResult executeOffline(Context context);
    
    /**
     * Get operation priority (for queue ordering)
     */
    public int getPriority() {
        return 0; // Normal priority by default
    }
    
    /**
     * 📊 Operation result
     */
    public static class SyncResult {
        public final boolean success;
        public final String message;
        public final JSONObject data;
        
        private SyncResult(boolean success, String message, JSONObject data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
        
        public static SyncResult success(String message) {
            return new SyncResult(true, message, null);
        }
        
        public static SyncResult success(String message, JSONObject data) {
            return new SyncResult(true, message, data);
        }
        
        public static SyncResult failure(String message) {
            return new SyncResult(false, message, null);
        }
        
        @Override
        public String toString() {
            return "SyncResult{success=" + success + ", message='" + message + "'}";
        }
    }
}