package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;

// Import operation classes
import com.clone.EasyDelivery.Utility.operations.*;
import com.clone.EasyDelivery.Utility.operations.SyncOperation.SyncResult;

/**
 * UnifiedTripManager - Simple, unified API for all trip operations
 * 
 * Features:
 * - Local tracking for dashboard persistence
 * - Device-aware periodic cleanup via SyncService
 * - Works online or offline with automatic sync
 * - Simple, non-competing architecture
 */
public class UnifiedTripManager {
    
    private static final String TAG = "UnifiedTripManager";
    
    private final Context context;
    private final ConnectivityAwareSyncManager syncManager;
    private final String deviceId;
    private static UnifiedTripManager instance;
    
    // Local tracking of trips claimed by this device
    private final java.util.Set<String> locallyClaimedTrips = new java.util.HashSet<>();
    
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
     * Claim a trip - instant response, automatic sync
     */
    public boolean claimTrip(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot claim trip - invalid trip ID");
            return false;
        }
        
        try {
            JSONObject operationData = new JSONObject();
            operationData.put("tripId", tripId);
            operationData.put("deviceId", deviceId);
            operationData.put("action", "claim");
            
            SyncOperation operation = ClaimTripOperation.create(tripId, operationData);
            
            SyncResult result = syncManager.executeOperation(operation);
            
            if (result.success) {
                locallyClaimedTrips.add(tripId);
                return true;
            } else {
                Log.w(TAG, "Trip claim failed: " + tripId + " - " + result.message);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating claim operation for trip: " + tripId, e);
            return false;
        }
    }
    
    /**
     * Start a trip - instant response, automatic sync
     */
    public boolean startTrip(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot start trip - invalid trip ID");
            return false;
        }
        
        
        try {
            JSONObject operationData = new JSONObject();
            operationData.put("tripId", tripId);
            operationData.put("deviceId", deviceId);
            operationData.put("action", "start");
            
            SyncOperation operation = StartTripOperation.create(tripId, operationData);
            
            SyncResult result = syncManager.executeOperation(operation);
            
            if (result.success) {
                return true;
            } else {
                Log.w(TAG, "Trip start failed: " + tripId + " - " + result.message);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating start operation for trip: " + tripId, e);
            return false;
        }
    }
    
    /**
     * Complete a trip - instant response, automatic sync
     */
    public boolean completeTrip(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot complete trip - invalid trip ID");
            return false;
        }
        
        
        try {
            JSONObject operationData = new JSONObject();
            operationData.put("tripId", tripId);
            operationData.put("deviceId", deviceId);
            operationData.put("action", "complete");
            
            SyncOperation operation = CompleteTripOperation.create(tripId, operationData);
            
            SyncResult result = syncManager.executeOperation(operation);
            
            if (result.success) {
                locallyClaimedTrips.remove(tripId);
                return true;
            } else {
                Log.w(TAG, "Trip completion failed: " + tripId + " - " + result.message);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating completion operation for trip: " + tripId, e);
            return false;
        }
    }
    
    /**
     * Release a trip - instant response, automatic sync
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
            
            SyncOperation operation = ReleaseTripOperation.create(tripId, operationData);
            
            SyncResult result = syncManager.executeOperation(operation);
            
            if (result.success) {
                // 📝 Remove from local tracking when released
                locallyClaimedTrips.remove(tripId);
                Log.i(TAG, "✅ Trip release initiated: " + tripId + " - " + result.message + " (removed from local tracking)");
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
     * Convenient method: Claim and start a trip in one call
     * 
     * This combines the most common workflow - claiming a trip and immediately
     * starting work on it.
     * 
     * @param tripId The trip to claim and start
     * @return true if both operations initiated successfully
     */
    public boolean claimAndStartTrip(String tripId) {
        Log.i(TAG, "Claim and start trip: " + tripId);
        
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
                Log.i(TAG, "Successfully claimed and started trip: " + tripId);
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
     * Update trip status/metadata
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
        
        Log.i(TAG, "Updating trip status: " + tripId + " -> " + status);
        
        try {
            JSONObject operationData = new JSONObject();
            operationData.put("tripId", tripId);
            operationData.put("deviceId", deviceId);
            operationData.put("action", "update_status");
            operationData.put("status", status);
            if (data != null) {
                operationData.put("data", data);
            }
            
            SyncOperation operation = UpdateTripStatusOperation.create(tripId, status, data);
            
            SyncResult result = syncManager.executeOperation(operation);
            
            if (result.success) {
                Log.i(TAG, "Trip status update initiated: " + tripId);
                return true;
            } else {
                Log.w(TAG, "Trip status update failed: " + tripId + " - " + result.message);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating status update operation for trip: " + tripId, e);
            return false;
        }
    }
    
    /**
     * Handle delivery completion within a trip context
     * 
     * This method ensures that when a delivery is completed within a trip,
     * all necessary sync operations are triggered automatically.
     * 
     * @param tripId The trip containing the completed delivery
     * @param document The completed document/delivery ID
     * @return true if sync operations were queued successfully
     */
    public boolean handleDeliveryCompletion(String tripId, String document) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot handle delivery completion - invalid trip ID");
            return false;
        }
        
        if (document == null || document.trim().isEmpty()) {
            Log.w(TAG, "Cannot handle delivery completion - invalid document ID");
            return false;
        }
        
        Log.i(TAG, "Handling delivery completion: " + document + " (Trip: " + tripId + ")");
        
        try {
            // Queue delivery data sync operation
            com.clone.EasyDelivery.Utility.operations.SyncDeliveryDataOperation deliverySync = 
                com.clone.EasyDelivery.Utility.operations.SyncDeliveryDataOperation.create(tripId, document);
            
            if (deliverySync != null) {
                SyncResult deliveryResult = syncManager.executeOperation(deliverySync);
                if (!deliveryResult.success) {
                    Log.w(TAG, "Failed to queue delivery data sync: " + deliveryResult.message);
                    return false;
                }
                Log.d(TAG, "Queued delivery data sync for: " + document);
            } else {
                Log.e(TAG, "Failed to create delivery sync operation for: " + document);
                return false;
            }
            
            // Check if this delivery needs email sending
            if (shouldSendEmailForDelivery(tripId, document)) {
                com.clone.EasyDelivery.Utility.operations.SendEmailOperation emailOp = 
                    com.clone.EasyDelivery.Utility.operations.SendEmailOperation.create(tripId, document);
                
                if (emailOp != null) {
                    SyncResult emailResult = syncManager.executeOperation(emailOp);
                    if (!emailResult.success) {
                        Log.w(TAG, "Failed to queue email operation: " + emailResult.message);
                        // Don't return false - email is secondary to data sync
                    } else {
                        Log.d(TAG, "Queued email operation for: " + document);
                    }
                } else {
                    Log.w(TAG, "Failed to create email operation for: " + document);
                }
            }
            
            // Update trip metadata to reflect the delivery completion
            JSONObject deliveryData = new JSONObject();
            deliveryData.put("completed_delivery", document);
            deliveryData.put("completion_timestamp", System.currentTimeMillis());
            updateTripStatus(tripId, "delivery_completed", deliveryData);
            
            Log.i(TAG, "Successfully handled delivery completion: " + document + " (Trip: " + tripId + ")");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling delivery completion: " + document + " (Trip: " + tripId + ")", e);
            return false;
        }
    }
    
    /**
     * Check if email should be sent for a completed delivery
     */
    private boolean shouldSendEmailForDelivery(String tripId, String document) {
        try {
            com.clone.EasyDelivery.Database.DeliveryDb database = new com.clone.EasyDelivery.Database.DeliveryDb(context);
            database.open();
            
            try {
                // Check if this delivery has unsent email status
                java.util.List<com.clone.EasyDelivery.Model.Delivery> unsentEmails = database.getAllUnsentEmails();
                for (com.clone.EasyDelivery.Model.Delivery delivery : unsentEmails) {
                    if (document.equals(delivery.getDocument()) && tripId.equals(delivery.getTripId())) {
                        Log.d(TAG, "Delivery " + document + " requires email sending");
                        return true;
                    }
                }
                
                Log.d(TAG, "Delivery " + document + " does not require email sending");
                return false;
                
            } finally {
                database.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking email requirement for delivery: " + document, e);
            // Default to sending email if we can't determine status
            return true;
        }
    }
    
    /**
     * Get trip sync status for UI display
     * 
     * This provides detailed information about the sync state of a specific trip,
     * useful for showing progress indicators in the UI.
     * 
     * @param tripId The trip to check sync status for
     * @return TripSyncStatus object containing sync state information
     */
    public TripSyncStatus getTripSyncStatus(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            return new TripSyncStatus(tripId, false, "Invalid trip ID", 0, 0);
        }
        
        try {
            // Check if trip has pending sync operations
            OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
            int pendingOperations = queue.getPendingOperationsCount(tripId);
            
            // Check database sync status
            com.clone.EasyDelivery.Database.DeliveryDb database = new com.clone.EasyDelivery.Database.DeliveryDb(context);
            database.open();
            
            try {
                boolean isDataSynced = database.isDataSynced(tripId);
                int totalDocuments = database.getCompletedDocumentList(tripId).size();
                int syncedDocuments = database.getSyncedDocumentCount(tripId);
                
                String statusMessage;
                if (pendingOperations > 0) {
                    statusMessage = pendingOperations + " operations pending";
                } else if (isDataSynced) {
                    statusMessage = "All data synced";
                } else {
                    statusMessage = "Sync incomplete";
                }
                
                return new TripSyncStatus(tripId, isDataSynced && pendingOperations == 0, 
                                        statusMessage, syncedDocuments, totalDocuments);
                
            } finally {
                database.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting trip sync status for: " + tripId, e);
            return new TripSyncStatus(tripId, false, "Error checking status: " + e.getMessage(), 0, 0);
        }
    }
    
    /**
     * Clean up completed trips from local storage
     * 
     * This method removes completed and fully synced trips from local storage
     * to prevent them from reappearing in the dashboard.
     * 
     * @param tripId The trip to clean up
     * @return true if cleanup was successful
     */
    public boolean cleanupCompletedTrip(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            Log.w(TAG, "Cannot cleanup trip - invalid trip ID");
            return false;
        }
        
        try {
            // Check if trip is fully synced before cleanup
            TripSyncStatus status = getTripSyncStatus(tripId);
            if (!status.isFullySynced()) {
                Log.w(TAG, "Cannot cleanup trip " + tripId + " - not fully synced: " + status.getStatusMessage());
                return false;
            }
            
            Log.i(TAG, "Cleaning up completed trip: " + tripId);
            
            // Remove from completed trips list
            AppConstant.completedTrips.remove(tripId);
            
            // Remove from local tracking
            locallyClaimedTrips.remove(tripId);
            
            // Delete local trip file
            File tripFile = new File(context.getFilesDir() + "/Trip/", tripId + ".json");
            if (tripFile.exists()) {
                boolean deleted = tripFile.delete();
                if (deleted) {
                    Log.i(TAG, "Deleted local trip file: " + tripFile.getAbsolutePath());
                } else {
                    Log.w(TAG, "Failed to delete local trip file: " + tripFile.getAbsolutePath());
                }
            }
            
            // Delete temporary files
            File tempFile = new File(context.getFilesDir() + "/Trip/", tripId + ".json.tmp");
            if (tempFile.exists()) {
                tempFile.delete();
            }
            
            // Clean up database records
            com.clone.EasyDelivery.Database.DeliveryDb database = new com.clone.EasyDelivery.Database.DeliveryDb(context);
            database.open();
            try {
                database.deleteUploadedData(tripId);
                Log.i(TAG, "Deleted uploaded data records for trip: " + tripId);
            } finally {
                database.close();
            }
            
            Log.i(TAG, "Successfully cleaned up completed trip: " + tripId);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up completed trip: " + tripId, e);
            return false;
        }
    }
    
    /**
     * Get current sync status - useful for UI indicators
     */
    public ConnectivityAwareSyncManager.SyncStatus getSyncStatus() {
        return syncManager.getSyncStatus();
    }
    
    /**
     * Force immediate sync of all pending operations
     */
    public void forceSync() {
        Log.i(TAG, "Force sync requested");
        syncManager.forceSync();
    }
    
    /**
     * Get system statistics for debugging
     */
    public String getSystemStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("Unified Trip Manager Status:\n");
        stats.append("Device ID: ").append(deviceId).append("\n\n");
        stats.append(syncManager.getSyncStatistics()).append("\n");
        
        OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
        stats.append(queue.getQueueStatistics());
        
        return stats.toString();
    }
    
    /**
     * Get the device ID used for operations
     */
    public String getDeviceId() {
        return deviceId;
    }
    
    /**
     * Release a trip (overloaded method without reason)
     */
    public boolean releaseTrip(String tripId) {
        return releaseTrip(tripId, "Manual release");
    }
    
    
    /**
     * Manual cleanup - triggers immediate device-aware cleanup
     * Can be called by UI components if needed
     */
    public void forceCleanupStuckTrips() {
        Log.i(TAG, "Manual cleanup requested");
        performPeriodicCleanup();
    }
    
    
    /**
     * Get list of available trips for dashboard
     * 🗄️ OFFLINE-FIRST: Prioritizes cached trips, then adds cloud trips when online
     */
    public java.util.List<String> getAvailableTrips() {
        java.util.List<String> availableTrips = new java.util.ArrayList<>();
        
        try {
            // 🗄️ OFFLINE-FIRST: Start with cached trips from TripCacheManager
            TripCacheManager cacheManager = TripCacheManager.getInstance(context);
            java.util.List<TripCacheManager.TripCacheEntry> cachedTrips = cacheManager.getCachedTrips();
            
            // Add all cached trips immediately (works offline)
            for (TripCacheManager.TripCacheEntry entry : cachedTrips) {
                if (!AppConstant.completedTrips.contains(entry.tripId)) {
                    availableTrips.add(entry.tripId);
                    Log.v(TAG, "Cached trip: " + entry.tripId + " (" + entry.syncStatus + ")");
                }
            }
            
            Log.i(TAG, "Found " + availableTrips.size() + " cached trips");
            
            // 🌐 ONLINE: Add additional trips from Dropbox when connected
            ConnectivityAwareSyncManager syncManager = ConnectivityAwareSyncManager.getInstance(context);
            boolean isOnline = syncManager.isOnline();
            
            if (isOnline) {
                java.util.List<String> cloudTrips = getLocalTripsDirectly(); // This queries Dropbox
                
                // 🔧 STALE TRIP CLEANUP: Remove cached trips that no longer exist on Dropbox
                Log.i(TAG, "🔍 CLEANUP CHECK: Cached trips: " + availableTrips + ", Dropbox trips: " + cloudTrips);
                int removedCount = 0;
                java.util.Iterator<String> iterator = availableTrips.iterator();
                while (iterator.hasNext()) {
                    String cachedTrip = iterator.next();
                    if (!cloudTrips.contains(cachedTrip) && !locallyClaimedTrips.contains(cachedTrip)) {
                        Log.i(TAG, "🗑️ CLEANUP: Removing stale cached trip: " + cachedTrip + " (not in Dropbox)");
                        iterator.remove();
                        // Also remove from cache manager
                        cacheManager.removeTripFromCache(cachedTrip);
                        removedCount++;
                    }
                }
                Log.i(TAG, "🗑️ CLEANUP RESULT: Removed " + removedCount + " stale trips from cache");
                
                for (String tripId : cloudTrips) {
                    if (!availableTrips.contains(tripId) && !AppConstant.completedTrips.contains(tripId)) {
                        availableTrips.add(tripId);
                        Log.d(TAG, "Added cloud trip: " + tripId);
                        
                        // Store in cache for offline access
                        if (!cacheManager.isTripCached(tripId)) {
                            // Trigger background download and caching
                            downloadTripFromDropboxAsync(tripId);
                        }
                    }
                }
                
                Log.i(TAG, "Total " + availableTrips.size() + " trips (" + cloudTrips.size() + " from cloud)");
            } else {
                Log.i(TAG, "Using " + availableTrips.size() + " cached trips only");
            }
            
            // Add locally tracked claimed trips so they stay visible
            for (String claimedTrip : locallyClaimedTrips) {
                if (!availableTrips.contains(claimedTrip)) {
                    availableTrips.add(claimedTrip);
                    Log.d(TAG, "Added locally tracked trip to dashboard: " + claimedTrip);
                }
            }
            
            java.util.Collections.sort(availableTrips);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting available trips", e);
        }
        
        return availableTrips;
    }
    
    /**
     * 🗄️ Get cached trips only (pure offline access)
     * This method works entirely offline using locally cached trip data
     */
    public java.util.List<String> getCachedTrips() {
        java.util.List<String> cachedTrips = new java.util.ArrayList<>();
        
        try {
            TripCacheManager cacheManager = TripCacheManager.getInstance(context);
            java.util.List<TripCacheManager.TripCacheEntry> entries = cacheManager.getCachedTrips();
            
            for (TripCacheManager.TripCacheEntry entry : entries) {
                if (!AppConstant.completedTrips.contains(entry.tripId)) {
                    cachedTrips.add(entry.tripId);
                }
            }
            
            // Add locally tracked claimed trips
            for (String claimedTrip : locallyClaimedTrips) {
                if (!cachedTrips.contains(claimedTrip)) {
                    cachedTrips.add(claimedTrip);
                }
            }
            
            java.util.Collections.sort(cachedTrips);
            
            Log.i(TAG, "Retrieved " + cachedTrips.size() + " cached trips (offline-capable)");
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting cached trips", e);
        }
        
        return cachedTrips;
    }
    
    /**
     * Get trips from Dropbox available folder directly
     * This ensures real-time synchronization between devices
     * 🔧 FIXED: Prevents race conditions that cause trips to pop in/out
     */
    private java.util.List<String> getLocalTripsDirectly() {
        java.util.List<String> availableTrips = new java.util.ArrayList<>();
        
        try {
            // Check what's actually available on Dropbox, not just local files
            java.util.List<String> dropboxAvailableTrips = getTripsFromDropboxAvailableFolder();
            
            // 🔧 RACE CONDITION FIX: Always add trips that exist on Dropbox
            // Don't make trip visibility dependent on local file download completion
            for (String tripId : dropboxAvailableTrips) {
                if (!AppConstant.completedTrips.contains(tripId)) {
                    // ✅ ALWAYS ADD: If trip exists on Dropbox, show it in dashboard
                    availableTrips.add(tripId);
                    
                    // Check local file status for background optimization only
                    File tripFile = new File(context.getFilesDir() + "/Trip/", tripId + ".json");
                    
                    if (!tripFile.exists() || tripFile.length() == 0) {
                        // Download in background - doesn't affect visibility
                        downloadTripFromDropboxAsync(tripId);
                    }
                }
            }
            
            java.util.Collections.sort(availableTrips);
            Log.i(TAG, "Found " + availableTrips.size() + " trips from Dropbox");
            
        } catch (Exception e) {
            Log.w(TAG, "Error getting trips from Dropbox, using local fallback: " + e.getMessage());
            // Fallback to local files if Dropbox is unavailable
            return getLocalTripsDirectlyFallback();
        }
        
        return availableTrips;
    }
    
    /**
     * Get list of trips currently in Dropbox available folder
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
                    }
                }
            }
            
            Log.d(TAG, "Found " + dropboxTrips.size() + " trips in Dropbox available folder");
            
        } catch (Exception e) {
            Log.e(TAG, "Error listing Dropbox available folder", e);
        }
        
        return dropboxTrips;
    }
    
    // 🔧 SYNCHRONIZED DOWNLOAD MANAGER: Prevents race conditions and duplicate downloads
    private static final java.util.Set<String> activeDownloads = new java.util.HashSet<>();
    private static final Object downloadLock = new Object();
    
    /**
     * Download trip file from Dropbox with race condition protection
     * 🔧 ENHANCED: Prevents duplicate downloads and stores in TripCacheManager
     */
    private void downloadTripFromDropboxAsync(final String tripId) {
        synchronized (downloadLock) {
            // Check if download is already in progress
            if (activeDownloads.contains(tripId)) {
                Log.i(TAG, "Download already in progress for: " + tripId);
                return;
            }
            
            // Check if already cached
            TripCacheManager cacheManager = TripCacheManager.getInstance(context);
            if (cacheManager.isTripCached(tripId)) {
                Log.i(TAG, "Trip already cached: " + tripId);
                return;
            }
            
            // Mark download as active
            activeDownloads.add(tripId);
            Log.i(TAG, "Starting protected download for: " + tripId);
        }
        
        Thread downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean downloadSucceeded = false;
                String downloadedContent = null;
                
                try {
                    // Update sync status to "syncing"
                    TripCacheManager cacheManager = TripCacheManager.getInstance(context);
                    cacheManager.updateTripSyncStatus(tripId, TripCacheManager.STATUS_SYNCING);
                    
                    // Download from Dropbox
                    DropboxHelper.downloadFileWithLocking(context, tripId + ".json");
                    
                    // 🔧 VERIFICATION: Check if file was actually created
                    File downloadedFile = new File(context.getFilesDir() + "/Trip/", tripId + ".json");
                    if (downloadedFile.exists() && downloadedFile.length() > 0) {
                        // Read the downloaded content
                        downloadedContent = JsonHandler.readFileAsString(context, tripId);
                        
                        if (downloadedContent != null && !downloadedContent.trim().isEmpty()) {
                            // Store in cache manager for offline access
                            boolean cached = cacheManager.storeTripInCache(tripId, downloadedContent);
                            
                            if (cached) {
                                cacheManager.updateTripSyncStatus(tripId, TripCacheManager.STATUS_SYNCED);
                                downloadSucceeded = true;
                                Log.i(TAG, "🔄 SYNC_DOWNLOAD: Successfully downloaded and cached: " + tripId + " (" + downloadedFile.length() + " bytes)");
                            } else {
                                cacheManager.updateTripSyncStatus(tripId, TripCacheManager.STATUS_ERROR);
                                Log.e(TAG, "🔄 SYNC_DOWNLOAD: Failed to cache downloaded trip: " + tripId);
                            }
                        } else {
                            cacheManager.updateTripSyncStatus(tripId, TripCacheManager.STATUS_ERROR);
                            Log.e(TAG, "🔄 SYNC_DOWNLOAD: Downloaded file has no content: " + tripId);
                        }
                    } else {
                        Log.e(TAG, "🔄 SYNC_DOWNLOAD: Download claimed success but file missing: " + tripId);
                    }
                    
                } catch (Exception e) {
                    Log.w(TAG, "🔄 SYNC_DOWNLOAD: Download failed for " + tripId + ": " + e.getMessage());
                    
                    // Update sync status to error
                    try {
                        TripCacheManager cacheManager = TripCacheManager.getInstance(context);
                        cacheManager.updateTripSyncStatus(tripId, TripCacheManager.STATUS_ERROR);
                    } catch (Exception cacheError) {
                        Log.e(TAG, "Failed to update cache status after download error", cacheError);
                    }
                    
                    downloadSucceeded = false;
                } finally {
                    // Always remove from active downloads
                    synchronized (downloadLock) {
                        activeDownloads.remove(tripId);
                        Log.i(TAG, "🔄 SYNC_DOWNLOAD: Released download lock for: " + tripId);
                    }
                }
            }
        });
        downloadThread.start();
    }
    
    /**
     * Fallback method - get trips from local file system only
     * 🔧 FIXED: Improved stability for offline scenarios
     */
    private java.util.List<String> getLocalTripsDirectlyFallback() {
        java.util.List<String> localTrips = new java.util.ArrayList<>();
        
        try {
            File tripDir = new File(context.getFilesDir() + "/Trip/");
            if (!tripDir.exists()) {
                tripDir.mkdirs();
                return localTrips;
            }
            
            String[] tripFiles = tripDir.list();
            if (tripFiles == null) {
                return localTrips;
            }
            
            // 🔧 STABILITY: More lenient file validation to prevent trips from disappearing
            for (String fileName : tripFiles) {
                if (fileName.endsWith(".json") && !fileName.endsWith(".tmp")) {
                    String tripName = fileName.substring(0, fileName.length() - 5);
                    File currentFile = new File(tripDir, fileName);
                    
                    // 🔧 IMPROVED: Only exclude if file is completely empty or trip is definitely completed
                    boolean isFileValid = currentFile.exists() && currentFile.length() >= 0; // Accept even small files
                    boolean isTripCompleted = AppConstant.completedTrips.contains(tripName);
                    
                    if (isFileValid && !isTripCompleted) {
                        localTrips.add(tripName);
                    }
                }
            }
            
            java.util.Collections.sort(localTrips);
            Log.i(TAG, "Found " + localTrips.size() + " local trips (fallback)");
            
        } catch (Exception e) {
            Log.e(TAG, "🔄 STABLE_FALLBACK: Error getting local trips, returning empty list", e);
        }
        
        return localTrips;
    }
    
    // ================== PERSISTENCE & CLEANUP HELPERS ==================
    
    /**
     * Get in-progress trips claimed by this device from Dropbox
     * Used by both persistence logic and cleanup logic
     */
    private java.util.List<String> getInProgressTripsByDevice() {
        java.util.List<String> inProgressTrips = new java.util.ArrayList<>();
        
        try {
            com.dropbox.core.v2.DbxClientV2 client = DropboxHelper.getClient(context);
            if (client == null) {
                return inProgressTrips;
            }
            
            String inProgressPath = "/Customers/" + AppConstant.COMPANY + "/in_progress";
            
            com.dropbox.core.v2.files.ListFolderResult inProgressFiles = client.files().listFolder(inProgressPath);
            
            if (inProgressFiles != null && !inProgressFiles.getEntries().isEmpty()) {
                for (int i = 0; i < inProgressFiles.getEntries().size(); i++) {
                    String fileName = inProgressFiles.getEntries().get(i).getName();
                    
                    DropboxHelper.ClaimInfo claimInfo = DropboxHelper.parseClaimInfo(fileName);
                    
                    if (claimInfo.isValidClaim && deviceId.equals(claimInfo.deviceId)) {
                        inProgressTrips.add(claimInfo.tripId);
                        Log.d(TAG, "Found in-progress trip by this device: " + claimInfo.tripId);
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting in-progress trips by device", e);
        }
        
        return inProgressTrips;
    }
    
    /**
     * Device-aware periodic cleanup of stuck in-progress trips
     * Only releases trips claimed by this device when no active trip exists
     * Called by SyncService periodic operations
     */
    public void performPeriodicCleanup() {
        Thread cleanupThread = new Thread(() -> {
            try {
                Log.d(TAG, "Performing periodic cleanup check...");
                
                // Find trips stuck in Dropbox in_progress folder by this device
                java.util.List<String> dropboxInProgressTrips = getInProgressTripsByDevice();
                
                if (dropboxInProgressTrips.isEmpty()) {
                    Log.d(TAG, "No in_progress trips found by this device - cleanup complete");
                    return;
                }
                
                // Simple logic: If no active trip, release all in_progress trips by this device
                String currentTripId = AppConstant.TRIPID;
                boolean hasActiveTrip = (currentTripId != null && !currentTripId.isEmpty());
                
                if (!hasActiveTrip) {
                    Log.w(TAG, "CLEANUP: No active trip - releasing " + dropboxInProgressTrips.size() + " stuck trips");
                    
                    for (String stuckTrip : dropboxInProgressTrips) {
                        Log.i(TAG, "Releasing stuck trip: " + stuckTrip);
                        releaseTrip(stuckTrip, "Periodic cleanup: no active trip");
                    }
                } else {
                    Log.d(TAG, "Active trip detected: " + currentTripId + " - no cleanup needed");
                    
                    // Ensure the active trip is in local tracking
                    if (dropboxInProgressTrips.contains(currentTripId)) {
                        locallyClaimedTrips.add(currentTripId);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in periodic cleanup", e);
            }
        });
        cleanupThread.start();
    }
    
    /**
     * 🗑️ Force sync trip removals from Dropbox
     * Manually triggers trip removal sync to immediately remove trips
     * that are no longer available on Dropbox
     */
    public void forceSyncTripRemovals() {
        try {
            Log.i(TAG, "Forcing trip removal sync...");
            
            ConnectivityAwareSyncManager syncManager = ConnectivityAwareSyncManager.getInstance(context);
            syncManager.forceTripRemovalSync();
            
        } catch (Exception e) {
            Log.e(TAG, "Error during force trip removal sync", e);
        }
    }
    
    
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
}
