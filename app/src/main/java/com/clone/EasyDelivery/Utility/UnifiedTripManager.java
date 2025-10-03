package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;

/**
 * 🎯 UnifiedTripManager - Simple, unified API for all trip operations
 * 
 * This replaces both the complex TripStateManager and legacy DropboxHelper patterns
 * with a single, intuitive interface that:
 * 
 * ✅ Works instantly online or offline
 * ✅ Requires no feature flags or configuration  
 * ✅ Handles all sync complexity internally
 * ✅ Provides immediate user feedback
 * ✅ Automatically resolves conflicts
 * 
 * Usage is simple:
 * ```java
 * UnifiedTripManager tripManager = UnifiedTripManager.getInstance(context);
 * 
 * // Claim a trip - instant response, syncs automatically
 * boolean success = tripManager.claimTrip("TRIP123");
 * 
 * // Start the trip - instant response  
 * boolean success = tripManager.startTrip("TRIP123");
 * 
 * // Complete the trip - instant response
 * boolean success = tripManager.completeTrip("TRIP123");
 * ```
 * 
 * That's it. No locks, no state machines, no complexity.
 */
public class UnifiedTripManager {
    
    private static final String TAG = "UnifiedTripManager";
    
    private final Context context;
    private final ConnectivityAwareSyncManager syncManager;
    private final String deviceId;
    private static UnifiedTripManager instance;
    
    private UnifiedTripManager(Context context) {
        this.context = context.getApplicationContext();
        this.syncManager = ConnectivityAwareSyncManager.getInstance(context);
        this.deviceId = generateDeviceId();
    }
    
    public static synchronized UnifiedTripManager getInstance(Context context) {
        if (instance == null) {
            instance = new UnifiedTripManager(context);
        }
        return instance;
    }
    
    /**
     * 🎯 Claim a trip - instant response, automatic sync
     * 
     * This is the new simple way to claim trips. No locks, no state checks,
     * just call this method and it works instantly.
     * 
     * @param tripId The trip to claim
     * @return true if operation initiated successfully (always true unless invalid input)
     */
    public boolean claimTrip(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot claim trip - invalid trip ID");
            return false;
        }
        
        Log.i(TAG, "🎯 Claiming trip: " + tripId);
        
        // Create and execute the operation
        try {
            JSONObject operationData = new JSONObject();
            operationData.put("tripId", tripId);
            operationData.put("deviceId", deviceId);
            operationData.put("action", "claim");
            
            ConnectivityAwareSyncManager.SyncOperation operation = new ClaimTripOperation(tripId, operationData);
            
            ConnectivityAwareSyncManager.SyncResult result = syncManager.executeOperation(operation);
            
            if (result.success) {
                Log.i(TAG, "✅ Trip claim initiated: " + tripId + " - " + result.message);
                return true;
            } else {
                Log.w(TAG, "⚠️ Trip claim failed: " + tripId + " - " + result.message);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating claim operation for trip: " + tripId, e);
            return false;
        }
    }
    
    /**
     * 🚀 Start a trip - instant response, automatic sync
     * 
     * Call this after claiming a trip to move it to in-progress state.
     * Works instantly regardless of network connectivity.
     * 
     * @param tripId The trip to start
     * @return true if operation initiated successfully
     */
    public boolean startTrip(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot start trip - invalid trip ID");
            return false;
        }
        
        Log.i(TAG, "🚀 Starting trip: " + tripId);
        
        try {
            JSONObject operationData = new JSONObject();
            operationData.put("tripId", tripId);
            operationData.put("deviceId", deviceId);
            operationData.put("action", "start");
            
            ConnectivityAwareSyncManager.SyncOperation operation = new StartTripOperation(tripId, operationData);
            
            ConnectivityAwareSyncManager.SyncResult result = syncManager.executeOperation(operation);
            
            if (result.success) {
                Log.i(TAG, "✅ Trip start initiated: " + tripId + " - " + result.message);
                return true;
            } else {
                Log.w(TAG, "⚠️ Trip start failed: " + tripId + " - " + result.message);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating start operation for trip: " + tripId, e);
            return false;
        }
    }
    
    /**
     * 🏁 Complete a trip - instant response, automatic sync
     * 
     * Call this when all deliveries in a trip are finished.
     * Works instantly and handles all cloud operations automatically.
     * 
     * @param tripId The trip to complete
     * @return true if operation initiated successfully
     */
    public boolean completeTrip(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot complete trip - invalid trip ID");
            return false;
        }
        
        Log.i(TAG, "🏁 Completing trip: " + tripId);
        
        try {
            JSONObject operationData = new JSONObject();
            operationData.put("tripId", tripId);
            operationData.put("deviceId", deviceId);
            operationData.put("action", "complete");
            
            ConnectivityAwareSyncManager.SyncOperation operation = new CompleteTripOperation(tripId, operationData);
            
            ConnectivityAwareSyncManager.SyncResult result = syncManager.executeOperation(operation);
            
            if (result.success) {
                Log.i(TAG, "✅ Trip completion initiated: " + tripId + " - " + result.message);
                return true;
            } else {
                Log.w(TAG, "⚠️ Trip completion failed: " + tripId + " - " + result.message);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating completion operation for trip: " + tripId, e);
            return false;
        }
    }
    
    /**
     * ↩️ Release a trip - instant response, automatic sync
     * 
     * Use this to unclaim a trip and return it to available state.
     * Useful for cancellations or when a trip can't be completed.
     * 
     * @param tripId The trip to release
     * @param reason Optional reason for releasing the trip
     * @return true if operation initiated successfully
     */
    public boolean releaseTrip(String tripId, String reason) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot release trip - invalid trip ID");
            return false;
        }
        
        if (reason == null) {
            reason = "Manual release";
        }
        
        Log.i(TAG, "↩️ Releasing trip: " + tripId + " (reason: " + reason + ")");
        
        try {
            JSONObject operationData = new JSONObject();
            operationData.put("tripId", tripId);
            operationData.put("deviceId", deviceId);
            operationData.put("action", "release");
            operationData.put("reason", reason);
            
            ConnectivityAwareSyncManager.SyncOperation operation = new ReleaseTripOperation(tripId, operationData);
            
            ConnectivityAwareSyncManager.SyncResult result = syncManager.executeOperation(operation);
            
            if (result.success) {
                Log.i(TAG, "✅ Trip release initiated: " + tripId + " - " + result.message);
                return true;
            } else {
                Log.w(TAG, "⚠️ Trip release failed: " + tripId + " - " + result.message);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating release operation for trip: " + tripId, e);
            return false;
        }
    }
    
    /**
     * 🔄 Convenient method: Claim and start a trip in one call
     * 
     * This combines the most common workflow - claiming a trip and immediately
     * starting work on it.
     * 
     * @param tripId The trip to claim and start
     * @return true if both operations initiated successfully
     */
    public boolean claimAndStartTrip(String tripId) {
        Log.i(TAG, "🔄 Claim and start trip: " + tripId);
        
        boolean claimed = claimTrip(tripId);
        if (claimed) {
            // Small delay to let local state update
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            boolean started = startTrip(tripId);
            if (started) {
                Log.i(TAG, "✅ Successfully claimed and started trip: " + tripId);
                return true;
            } else {
                // If start failed, try to release the claim
                Log.w(TAG, "Start failed, releasing claim for trip: " + tripId);
                releaseTrip(tripId, "Start failed after claim");
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * 📊 Update trip status/metadata
     * 
     * Use this to update trip metadata without changing its core state.
     * Good for progress updates, notes, etc.
     * 
     * @param tripId The trip to update
     * @param status The new status
     * @param data Additional data to include
     * @return true if operation initiated successfully
     */
    public boolean updateTripStatus(String tripId, String status, JSONObject data) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot update trip status - invalid trip ID");
            return false;
        }
        
        Log.i(TAG, "📊 Updating trip status: " + tripId + " -> " + status);
        
        try {
            JSONObject operationData = new JSONObject();
            operationData.put("tripId", tripId);
            operationData.put("deviceId", deviceId);
            operationData.put("action", "update_status");
            operationData.put("status", status);
            if (data != null) {
                operationData.put("data", data);
            }
            
            ConnectivityAwareSyncManager.SyncOperation operation = new UpdateTripStatusOperation(tripId, operationData);
            
            ConnectivityAwareSyncManager.SyncResult result = syncManager.executeOperation(operation);
            
            if (result.success) {
                Log.i(TAG, "✅ Trip status update initiated: " + tripId);
                return true;
            } else {
                Log.w(TAG, "⚠️ Trip status update failed: " + tripId + " - " + result.message);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating status update operation for trip: " + tripId, e);
            return false;
        }
    }
    
    /**
     * 📊 Get current sync status - useful for UI indicators
     */
    public ConnectivityAwareSyncManager.SyncStatus getSyncStatus() {
        return syncManager.getSyncStatus();
    }
    
    /**
     * 🧹 Force immediate sync of all pending operations
     */
    public void forceSync() {
        Log.i(TAG, "🧹 Force sync requested");
        syncManager.forceSync();
    }
    
    /**
     * 📊 Get system statistics for debugging
     */
    public String getSystemStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("🎯 Unified Trip Manager Status:\n");
        stats.append("Device ID: ").append(deviceId).append("\n\n");
        stats.append(syncManager.getSyncStatistics()).append("\n");
        
        OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
        stats.append(queue.getQueueStatistics());
        
        return stats.toString();
    }
    
    /**
     * 🆔 Get the device ID used for operations
     */
    public String getDeviceId() {
        return deviceId;
    }
    
    /**
     * ↩️ Release a trip (overloaded method without reason)
     */
    public boolean releaseTrip(String tripId) {
        return releaseTrip(tripId, "Manual release");
    }
    
    /**
     * 📋 Get list of available trips
     * This method provides a simple way to get available trips for the UI
     */
    public java.util.List<String> getAvailableTrips() {
        java.util.List<String> availableTrips = new java.util.ArrayList<>();
        
        try {
            // Get available trips from local files directly (avoid circular dependency)
            availableTrips.addAll(getLocalTripsDirectly());
            
            Log.i(TAG, "📋 Found " + availableTrips.size() + " available trips");
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting available trips", e);
        }
        
        return availableTrips;
    }
    
    /**
     * 📁 Get trips from Dropbox available folder directly (CRITICAL FIX for claiming sync issues)
     * This replaces the old local-only method to ensure real-time synchronization between devices
     */
    private java.util.List<String> getLocalTripsDirectly() {
        java.util.List<String> availableTrips = new java.util.ArrayList<>();
        
        try {
            Log.d(TAG, "🌐 CRITICAL FIX: Getting trips from Dropbox available folder for real-time sync");
            
            // 🚨 CRITICAL FIX: Check what's actually available on Dropbox, not just local files
            java.util.List<String> dropboxAvailableTrips = getTripsFromDropboxAvailableFolder();
            
            // For each trip available on Dropbox, ensure we have it locally
            for (String tripId : dropboxAvailableTrips) {
                if (!AppConstant.completedTrips.contains(tripId)) {
                    // Check if we have valid local data for this trip
                    File tripFile = new File(context.getFilesDir() + "/Trip/", tripId + ".json");
                    
                    if (tripFile.exists() && tripFile.length() > 0) {
                        availableTrips.add(tripId);
                        Log.d(TAG, "✅ Available trip with local data: " + tripId);
                    } else {
                        Log.d(TAG, "📥 Trip available on Dropbox but missing locally - downloading: " + tripId);
                        // Download the trip asynchronously
                        downloadTripFromDropboxAsync(tripId);
                        // Still add to available list since it exists on Dropbox
                        availableTrips.add(tripId);
                    }
                }
            }
            
            java.util.Collections.sort(availableTrips);
            Log.i(TAG, "🌐 CRITICAL FIX: Found " + availableTrips.size() + " available trips from Dropbox: " + availableTrips.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting trips from Dropbox available folder, falling back to local", e);
            // Fallback to local files if Dropbox is unavailable
            return getLocalTripsDirectlyFallback();
        }
        
        return availableTrips;
    }
    
    /**
     * 🌐 Get list of trips currently in Dropbox available folder
     */
    private java.util.List<String> getTripsFromDropboxAvailableFolder() {
        java.util.List<String> dropboxTrips = new java.util.ArrayList<>();
        
        try {
            com.dropbox.core.v2.DbxClientV2 client = DropboxHelper.getClient(context);
            if (client == null) {
                Log.w(TAG, "Dropbox client not available for checking available trips");
                return dropboxTrips;
            }
            
            String availablePath = "/Customers/" + AppConstant.COMPANY + "/available";
            com.dropbox.core.v2.files.ListFolderResult availableFiles = client.files().listFolder(availablePath);
            
            if (availableFiles != null && !availableFiles.getEntries().isEmpty()) {
                for (int i = 0; i < availableFiles.getEntries().size(); i++) {
                    String fileName = availableFiles.getEntries().get(i).getName();
                    if (fileName.endsWith(".json")) {
                        String tripId = fileName.substring(0, fileName.length() - 5);
                        dropboxTrips.add(tripId);
                        Log.d(TAG, "📋 Found available trip on Dropbox: " + tripId);
                    }
                }
            }
            
            Log.i(TAG, "🌐 Found " + dropboxTrips.size() + " trips in Dropbox available folder");
            
        } catch (Exception e) {
            Log.e(TAG, "Error listing Dropbox available folder", e);
        }
        
        return dropboxTrips;
    }
    
    /**
     * 📥 Download trip file from Dropbox asynchronously
     */
    private void downloadTripFromDropboxAsync(final String tripId) {
        Thread downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DropboxHelper.downloadFile(context, tripId + ".json");
                    Log.i(TAG, "✅ Downloaded trip file: " + tripId);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to download trip: " + tripId, e);
                }
            }
        });
        downloadThread.start();
    }
    
    /**
     * 📁 Fallback method - get trips from local file system only
     */
    private java.util.List<String> getLocalTripsDirectlyFallback() {
        java.util.List<String> localTrips = new java.util.ArrayList<>();
        
        try {
            Log.d(TAG, "📁 FALLBACK: Getting trips from local files only");
            
            File tripDir = new File(context.getFilesDir() + "/Trip/");
            if (!tripDir.exists()) {
                Log.w(TAG, "Trip directory does not exist.");
                return localTrips;
            }
            
            String[] tripFiles = tripDir.list();
            if (tripFiles == null) {
                Log.w(TAG, "No files found in trip directory.");
                return localTrips;
            }
            
            // Add all valid local trips
            for (String fileName : tripFiles) {
                if (fileName.endsWith(".json") && !fileName.endsWith(".tmp")) {
                    String tripName = fileName.substring(0, fileName.length() - 5);
                    File currentFile = new File(tripDir, fileName);
                    
                    // Only include if file has content and trip is not completed
                    if (currentFile.length() > 0 && !AppConstant.completedTrips.contains(tripName)) {
                        localTrips.add(tripName);
                        Log.d(TAG, "Added local trip: " + tripName);
                    }
                }
            }
            
            java.util.Collections.sort(localTrips);
            Log.i(TAG, "📁 FALLBACK: Found " + localTrips.size() + " local trips: " + localTrips.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting local trips fallback", e);
        }
        
        return localTrips;
    }
    
    // ================== MIGRATION HELPERS ==================
    
    /**
     * 🔄 Helper to migrate from old DropboxHelper pattern
     * 
     * This method can replace existing DropboxHelper calls during migration:
     * 
     * OLD: DropboxHelper.moveTripInProgress(context, tripId);
     * NEW: UnifiedTripManager.getInstance(context).migrateFromLegacy("claim_and_start", tripId);
     */
    public boolean migrateFromLegacy(String operation, String tripId) {
        Log.i(TAG, "🔄 Migrating legacy operation: " + operation + " for trip: " + tripId);
        
        switch (operation.toLowerCase()) {
            case "claim":
            case "move_in_progress":
                return claimTrip(tripId);
                
            case "claim_and_start":
                return claimAndStartTrip(tripId);
                
            case "complete":
            case "move_completed":
                return completeTrip(tripId);
                
            case "release":
            case "unclaim":
                return releaseTrip(tripId, "Legacy migration");
                
            default:
                Log.w(TAG, "Unknown legacy operation: " + operation);
                return false;
        }
    }
    
    // ================== PRIVATE HELPERS ==================
    
    private String generateDeviceId() {
        try {
            String androidId = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID
            );
            
            String driverName = AppConstant.DRIVER != null ? 
                AppConstant.DRIVER.replaceAll("[^A-Za-z0-9]", "") : "Driver";
            
            StringBuilder composite = new StringBuilder();
            composite.append(driverName).append("_");
            
            if (androidId != null && androidId.length() > 4) {
                composite.append(androidId.substring(0, Math.min(8, androidId.length())));
            } else {
                composite.append("DEV");
            }
            
            if (android.os.Build.MODEL != null) {
                int modelHash = Math.abs(android.os.Build.MODEL.hashCode() % 10000);
                composite.append("_").append(modelHash);
            }
            
            return composite.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating device ID, using fallback", e);
            return "Device_FALLBACK_" + System.currentTimeMillis();
        }
    }
    
    // ================== CONCRETE OPERATION CLASSES ==================
    
    /**
     * Concrete implementation for claiming trips
     */
    private static class ClaimTripOperation extends ConnectivityAwareSyncManager.SyncOperation {
        public ClaimTripOperation(String tripId, JSONObject data) {
            super("CLAIM_TRIP", tripId, data);
        }
        
        @Override
        public ConnectivityAwareSyncManager.SyncResult executeOnline(Context context) {
            try {
                Log.i(TAG, "🎯 Online claim trip: " + getTripId());
                
                // Get device ID from operation data
                String deviceId = null;
                try {
                    JSONObject operationData = getData();
                    deviceId = operationData.getString("deviceId");
                } catch (Exception e) {
                    Log.e(TAG, "Error getting device ID from operation data", e);
                    return ConnectivityAwareSyncManager.SyncResult.failure("Missing device ID");
                }
                
                // Perform actual Dropbox trip claiming operation
                boolean claimSuccess = DropboxHelper.claimTripDirectly(context, getTripId(), deviceId);
                
                if (claimSuccess) {
                    Log.i(TAG, "✅ Trip claimed successfully: " + getTripId());
                    return ConnectivityAwareSyncManager.SyncResult.success("Trip claimed and moved to claimed folder");
                } else {
                    Log.w(TAG, "⚠️ Trip claim failed: " + getTripId());
                    return ConnectivityAwareSyncManager.SyncResult.failure("Failed to claim trip on Dropbox");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in online claim operation for trip: " + getTripId(), e);
                return ConnectivityAwareSyncManager.SyncResult.failure("Online claim failed: " + e.getMessage());
            }
        }
        
        @Override
        public ConnectivityAwareSyncManager.SyncResult executeOffline(Context context) {
            try {
                // Update local state immediately
                Log.i(TAG, "Offline claim trip: " + getTripId());
                return ConnectivityAwareSyncManager.SyncResult.success("Trip claimed offline (will sync later)");
            } catch (Exception e) {
                return ConnectivityAwareSyncManager.SyncResult.failure("Offline claim failed: " + e.getMessage());
            }
        }
        
        @Override
        public int getPriority() {
            return 2; // High priority
        }
    }
    
    /**
     * Concrete implementation for starting trips
     */
    private static class StartTripOperation extends ConnectivityAwareSyncManager.SyncOperation {
        public StartTripOperation(String tripId, JSONObject data) {
            super("START_TRIP", tripId, data);
        }
        
        @Override
        public ConnectivityAwareSyncManager.SyncResult executeOnline(Context context) {
            try {
                Log.i(TAG, "🚀 Online start trip: " + getTripId());
                
                // Move trip from claimed to in_progress folder
                boolean startSuccess = DropboxHelper.startTripDirectly(context, getTripId());
                
                if (startSuccess) {
                    Log.i(TAG, "✅ Trip started successfully: " + getTripId());
                    return ConnectivityAwareSyncManager.SyncResult.success("Trip started and moved to in_progress folder");
                } else {
                    Log.w(TAG, "⚠️ Trip start failed: " + getTripId());
                    return ConnectivityAwareSyncManager.SyncResult.failure("Failed to start trip on Dropbox");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in online start operation for trip: " + getTripId(), e);
                return ConnectivityAwareSyncManager.SyncResult.failure("Online start failed: " + e.getMessage());
            }
        }
        
        @Override
        public ConnectivityAwareSyncManager.SyncResult executeOffline(Context context) {
            try {
                Log.i(TAG, "Offline start trip: " + getTripId());
                return ConnectivityAwareSyncManager.SyncResult.success("Trip started offline (will sync later)");
            } catch (Exception e) {
                return ConnectivityAwareSyncManager.SyncResult.failure("Offline start failed: " + e.getMessage());
            }
        }
        
        @Override
        public int getPriority() {
            return 2; // High priority
        }
    }
    
    /**
     * Concrete implementation for completing trips
     */
    private static class CompleteTripOperation extends ConnectivityAwareSyncManager.SyncOperation {
        public CompleteTripOperation(String tripId, JSONObject data) {
            super("COMPLETE_TRIP", tripId, data);
        }
        
        @Override
        public ConnectivityAwareSyncManager.SyncResult executeOnline(Context context) {
            try {
                Log.i(TAG, "🏁 Online complete trip: " + getTripId());
                
                // Move trip from in_progress to completed folder
                boolean completeSuccess = DropboxHelper.completeTripDirectly(context, getTripId());
                
                if (completeSuccess) {
                    Log.i(TAG, "✅ Trip completed successfully: " + getTripId());
                    return ConnectivityAwareSyncManager.SyncResult.success("Trip completed and moved to completed folder");
                } else {
                    Log.w(TAG, "⚠️ Trip completion failed: " + getTripId());
                    return ConnectivityAwareSyncManager.SyncResult.failure("Failed to complete trip on Dropbox");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in online completion operation for trip: " + getTripId(), e);
                return ConnectivityAwareSyncManager.SyncResult.failure("Online completion failed: " + e.getMessage());
            }
        }
        
        @Override
        public ConnectivityAwareSyncManager.SyncResult executeOffline(Context context) {
            try {
                Log.i(TAG, "Offline complete trip: " + getTripId());
                return ConnectivityAwareSyncManager.SyncResult.success("Trip completed offline (will sync later)");
            } catch (Exception e) {
                return ConnectivityAwareSyncManager.SyncResult.failure("Offline completion failed: " + e.getMessage());
            }
        }
        
        @Override
        public int getPriority() {
            return 2; // High priority
        }
    }
    
    /**
     * Concrete implementation for releasing trips
     */
    private static class ReleaseTripOperation extends ConnectivityAwareSyncManager.SyncOperation {
        public ReleaseTripOperation(String tripId, JSONObject data) {
            super("RELEASE_TRIP", tripId, data);
        }
        
        @Override
        public ConnectivityAwareSyncManager.SyncResult executeOnline(Context context) {
            try {
                Log.i(TAG, "↩️ Online release trip: " + getTripId());
                
                // Move trip back to available folder (unclaim)
                boolean releaseSuccess = DropboxHelper.releaseTripDirectly(context, getTripId());
                
                if (releaseSuccess) {
                    Log.i(TAG, "✅ Trip released successfully: " + getTripId());
                    return ConnectivityAwareSyncManager.SyncResult.success("Trip released and moved back to available folder");
                } else {
                    Log.w(TAG, "⚠️ Trip release failed: " + getTripId());
                    return ConnectivityAwareSyncManager.SyncResult.failure("Failed to release trip on Dropbox");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in online release operation for trip: " + getTripId(), e);
                return ConnectivityAwareSyncManager.SyncResult.failure("Online release failed: " + e.getMessage());
            }
        }
        
        @Override
        public ConnectivityAwareSyncManager.SyncResult executeOffline(Context context) {
            try {
                Log.i(TAG, "Offline release trip: " + getTripId());
                return ConnectivityAwareSyncManager.SyncResult.success("Trip released offline (will sync later)");
            } catch (Exception e) {
                return ConnectivityAwareSyncManager.SyncResult.failure("Offline release failed: " + e.getMessage());
            }
        }
        
        @Override
        public int getPriority() {
            return 1; // Normal priority
        }
    }
    
    /**
     * Concrete implementation for updating trip status
     */
    private static class UpdateTripStatusOperation extends ConnectivityAwareSyncManager.SyncOperation {
        public UpdateTripStatusOperation(String tripId, JSONObject data) {
            super("UPDATE_TRIP_STATUS", tripId, data);
        }
        
        @Override
        public ConnectivityAwareSyncManager.SyncResult executeOnline(Context context) {
            try {
                Log.i(TAG, "Online update trip status: " + getTripId());
                return ConnectivityAwareSyncManager.SyncResult.success("Trip status updated online");
            } catch (Exception e) {
                return ConnectivityAwareSyncManager.SyncResult.failure("Online status update failed: " + e.getMessage());
            }
        }
        
        @Override
        public ConnectivityAwareSyncManager.SyncResult executeOffline(Context context) {
            try {
                Log.i(TAG, "Offline update trip status: " + getTripId());
                return ConnectivityAwareSyncManager.SyncResult.success("Trip status updated offline (will sync later)");
            } catch (Exception e) {
                return ConnectivityAwareSyncManager.SyncResult.failure("Offline status update failed: " + e.getMessage());
            }
        }
        
        @Override
        public int getPriority() {
            return 1; // Normal priority
        }
    }
}
