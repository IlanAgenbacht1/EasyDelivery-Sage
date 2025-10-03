package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import com.dropbox.core.v2.DbxClientV2;
import com.clone.EasyDelivery.Database.DeliveryDb;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 🌐 ConnectivityAwareSyncManager - Smart sync that adapts to network conditions
 * 
 * This manager automatically detects online/offline state and provides:
 * - Instant operations when online
 * - Seamless offline queuing when offline
 * - Automatic sync when connectivity restored
 * - Single API surface for both modes
 * 
 * The key insight: Users shouldn't know or care about sync complexity.
 * Everything should just work, instantly, regardless of network state.
 */
public class ConnectivityAwareSyncManager {
    
    private static final String TAG = "ConnectivityAwareSyncManager";
    
    // Network state tracking
    private final Context context;
    private final AtomicBoolean isOnline = new AtomicBoolean(false);
    private final AtomicLong lastConnectivityCheck = new AtomicLong(0);
    private static final long CONNECTIVITY_CHECK_INTERVAL = 2000; // 2 seconds
    
    // Sync state
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private final AtomicLong lastSuccessfulSync = new AtomicLong(0);
    
    // Singleton
    private static ConnectivityAwareSyncManager instance;
    
    private ConnectivityAwareSyncManager(Context context) {
        this.context = context.getApplicationContext();
        updateConnectivityState();
    }
    
    public static synchronized ConnectivityAwareSyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConnectivityAwareSyncManager(context);
        }
        return instance;
    }
    
    /**
     * 🌐 Check current connectivity and update internal state
     */
    public boolean updateConnectivityState() {
        long now = System.currentTimeMillis();
        
        // Cache connectivity checks to avoid excessive system calls
        if (now - lastConnectivityCheck.get() < CONNECTIVITY_CHECK_INTERVAL) {
            return isOnline.get();
        }
        
        boolean wasOnline = isOnline.get();
        boolean nowOnline = checkNetworkConnectivity() && checkDropboxConnectivity();
        
        isOnline.set(nowOnline);
        lastConnectivityCheck.set(now);
        
        // Log connectivity changes
        if (wasOnline != nowOnline) {
            Log.i(TAG, "🌐 Connectivity changed: " + (nowOnline ? "ONLINE" : "OFFLINE"));
            
            if (nowOnline && !wasOnline) {
                // Just came online - trigger sync of queued operations
                Log.i(TAG, "📡 Network restored - triggering sync of queued operations");
                triggerOfflineSync();
            }
        }
        
        return nowOnline;
    }
    
    /**
     * 📶 Check basic network connectivity
     */
    private boolean checkNetworkConnectivity() {
        try {
            ConnectivityManager connectivityManager = 
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            
            if (connectivityManager == null) {
                return false;
            }
            
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnectedOrConnecting();
            
        } catch (Exception e) {
            Log.w(TAG, "Error checking network connectivity", e);
            return false;
        }
    }
    
    /**
     * 🗂️ Check Dropbox API connectivity (lightweight test)
     */
    private boolean checkDropboxConnectivity() {
        try {
            DbxClientV2 client = DropboxHelper.getClient(context);
            if (client == null) {
                return false;
            }
            
            // Quick, lightweight test - just check if we can get account info
            // This is cached by Dropbox client, so very fast
            client.users().getCurrentAccount();
            return true;
            
        } catch (Exception e) {
            // Not necessarily an error - might just be offline
            Log.d(TAG, "Dropbox connectivity check failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 🔄 Execute operation with automatic online/offline handling
     * 
     * This is the core method that provides the unified API:
     * - Online: Execute immediately against Dropbox
     * - Offline: Queue locally, return success immediately
     */
    public SyncResult executeOperation(SyncOperation operation) {
        updateConnectivityState();
        
        if (isOnline.get()) {
            return executeOnlineOperation(operation);
        } else {
            return executeOfflineOperation(operation);
        }
    }
    
    /**
     * ⚡ Execute operation immediately (online mode)
     */
    private SyncResult executeOnlineOperation(SyncOperation operation) {
        Log.d(TAG, "⚡ Executing online operation: " + operation.getType());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Execute directly against Dropbox
            SyncResult result = operation.executeOnline(context);
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (result.success) {
                Log.i(TAG, "✅ Online operation completed: " + operation.getType() + 
                      " (" + duration + "ms)");
                
                // Update local state immediately
                updateLocalState(operation, result);
                lastSuccessfulSync.set(System.currentTimeMillis());
                
            } else {
                Log.w(TAG, "⚠️ Online operation failed: " + operation.getType() + 
                      " - " + result.message);
                
                // If online operation fails, fall back to offline queue
                Log.i(TAG, "🔄 Falling back to offline queue for: " + operation.getType());
                return executeOfflineOperation(operation);
            }
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Online operation error: " + operation.getType(), e);
            
            // Network might have gone down during operation
            isOnline.set(false);
            return executeOfflineOperation(operation);
        }
    }
    
    /**
     * 💾 Queue operation for later sync (offline mode)
     */
    private SyncResult executeOfflineOperation(SyncOperation operation) {
        Log.d(TAG, "💾 Queueing offline operation: " + operation.getType());
        
        try {
            // Update local state immediately (optimistic)
            SyncResult localResult = operation.executeOffline(context);
            
            if (localResult.success) {
                // Queue for sync when online
                OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
                queue.enqueueOperation(operation);
                
                Log.i(TAG, "✅ Offline operation queued: " + operation.getType());
                
                // Return success immediately - user sees instant response
                return SyncResult.success("Operation completed (will sync when online)");
                
            } else {
                Log.w(TAG, "⚠️ Offline operation failed: " + operation.getType() + 
                      " - " + localResult.message);
                return localResult;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Offline operation error: " + operation.getType(), e);
            return SyncResult.failure("Offline operation failed: " + e.getMessage());
        }
    }
    
    /**
     * 📡 Trigger sync of all queued offline operations
     */
    private void triggerOfflineSync() {
        if (syncInProgress.get()) {
            Log.d(TAG, "Sync already in progress, skipping trigger");
            return;
        }
        
        // Run sync in background thread
        new Thread(() -> {
            try {
                syncInProgress.set(true);
                
                OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
                SyncResult result = queue.syncAll();
                
                if (result.success) {
                    Log.i(TAG, "✅ Offline sync completed successfully");
                    lastSuccessfulSync.set(System.currentTimeMillis());
                } else {
                    Log.w(TAG, "⚠️ Offline sync completed with issues: " + result.message);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error during offline sync", e);
            } finally {
                syncInProgress.set(false);
            }
        }).start();
    }
    
    /**
     * 🔄 Update local database state after successful operation
     */
    private void updateLocalState(SyncOperation operation, SyncResult result) {
        try {
            // Update sync metadata to reflect successful cloud operation
            DeliveryDb database = new DeliveryDb(context);
            database.open();
            
            // Mark as synced in local database
            if (operation.getTripId() != null) {
                database.updateLastCloudSync(operation.getTripId(), 
                    System.currentTimeMillis(), "synced");
            }
            
            database.close();
            
        } catch (Exception e) {
            Log.w(TAG, "Error updating local state after sync", e);
        }
    }
    
    /**
     * 📊 Get current sync status for UI display
     */
    public SyncStatus getSyncStatus() {
        updateConnectivityState();
        
        boolean online = isOnline.get();
        boolean syncing = syncInProgress.get();
        long lastSync = lastSuccessfulSync.get();
        
        OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
        int queuedOperations = queue.getQueuedOperationCount();
        
        if (syncing) {
            return new SyncStatus(SyncStatus.State.SYNCING, "Synchronizing...", 
                online, queuedOperations, lastSync);
        } else if (queuedOperations > 0) {
            if (online) {
                return new SyncStatus(SyncStatus.State.PENDING, 
                    queuedOperations + " operations pending sync", 
                    online, queuedOperations, lastSync);
            } else {
                return new SyncStatus(SyncStatus.State.OFFLINE, 
                    "Offline - " + queuedOperations + " operations queued", 
                    online, queuedOperations, lastSync);
            }
        } else {
            if (online) {
                return new SyncStatus(SyncStatus.State.SYNCED, "All data synced", 
                    online, queuedOperations, lastSync);
            } else {
                return new SyncStatus(SyncStatus.State.OFFLINE, "Offline", 
                    online, queuedOperations, lastSync);
            }
        }
    }
    
    /**
     * 🧹 Force sync of all pending operations (user-initiated)
     */
    public void forceSync() {
        updateConnectivityState();
        
        if (!isOnline.get()) {
            Log.w(TAG, "Cannot force sync - device is offline");
            return;
        }
        
        Log.i(TAG, "🔄 User-initiated force sync");
        triggerOfflineSync();
    }
    
    /**
     * 📊 Get sync statistics for debugging
     */
    public String getSyncStatistics() {
        updateConnectivityState();
        
        OfflineSyncQueue queue = OfflineSyncQueue.getInstance(context);
        SyncStatus status = getSyncStatus();
        
        StringBuilder stats = new StringBuilder();
        stats.append("🌐 Connectivity-Aware Sync Status:\n");
        stats.append("  - Online: ").append(status.isOnline).append("\n");
        stats.append("  - State: ").append(status.state).append("\n");
        stats.append("  - Message: ").append(status.message).append("\n");
        stats.append("  - Queued operations: ").append(status.queuedOperations).append("\n");
        stats.append("  - Last successful sync: ");
        
        if (status.lastSuccessfulSync > 0) {
            long minutesAgo = (System.currentTimeMillis() - status.lastSuccessfulSync) / (60 * 1000);
            stats.append(minutesAgo).append(" minutes ago\n");
        } else {
            stats.append("Never\n");
        }
        
        return stats.toString();
    }
    
    // ================== HELPER CLASSES ==================
    
    /**
     * 📊 Sync status information for UI
     */
    public static class SyncStatus {
        public enum State {
            SYNCED,     // Everything is synced
            SYNCING,    // Currently syncing
            PENDING,    // Has operations to sync
            OFFLINE     // Device is offline
        }
        
        public final State state;
        public final String message;
        public final boolean isOnline;
        public final int queuedOperations;
        public final long lastSuccessfulSync;
        
        public SyncStatus(State state, String message, boolean isOnline, 
                         int queuedOperations, long lastSuccessfulSync) {
            this.state = state;
            this.message = message;
            this.isOnline = isOnline;
            this.queuedOperations = queuedOperations;
            this.lastSuccessfulSync = lastSuccessfulSync;
        }
    }
    
    /**
     * 🔄 Base class for sync operations
     */
    public static abstract class SyncOperation {
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
         * Execute operation when online (direct Dropbox operation)
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