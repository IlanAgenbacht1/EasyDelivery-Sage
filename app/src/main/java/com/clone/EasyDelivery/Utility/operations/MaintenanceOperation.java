package com.clone.EasyDelivery.Utility.operations;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;

/**
 * 🛠️ Combined maintenance operation
 * 
 * Groups multiple maintenance tasks into a single operation for efficiency:
 * - Cache refresh
 * - Trip cleanup
 * - Stale data removal
 * 
 * This reduces the number of separate timers and Dropbox API calls.
 */
public class MaintenanceOperation extends SyncOperation {
    private static final String TAG = "MaintenanceOperation";
    
    public MaintenanceOperation() {
        super("MAINTENANCE", "system", createMaintenanceData());
    }
    
    public static MaintenanceOperation create() {
        return new MaintenanceOperation();
    }
    
    private static JSONObject createMaintenanceData() {
        try {
            JSONObject data = new JSONObject();
            data.put("timestamp", System.currentTimeMillis());
            data.put("tasks", "cache_refresh,cleanup,stale_removal");
            return data;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
    
    @Override
    public SyncResult executeOnline(Context context) {
        try {
            Log.i(TAG, "🛠️ Starting combined maintenance operation");
            long startTime = System.currentTimeMillis();
            
            StringBuilder results = new StringBuilder("Maintenance completed: ");
            boolean anyFailures = false;
            
            // Task 1: Cache refresh
            try {
                Log.d(TAG, "📥 Performing cache refresh");
                // Get TripCacheManager and refresh
                com.clone.EasyDelivery.Utility.TripCacheManager cacheManager = 
                    com.clone.EasyDelivery.Utility.TripCacheManager.getInstance(context);
                cacheManager.refreshCache();
                results.append("cache_refresh=✅ ");
            } catch (Exception e) {
                Log.w(TAG, "Cache refresh failed", e);
                results.append("cache_refresh=❌ ");
                anyFailures = true;
            }
            
            // Task 2: Trip cleanup
            try {
                Log.d(TAG, "🧹 Performing trip cleanup");
                // Execute cleanup operation
                CleanupCompletedTripsOperation cleanup = CleanupCompletedTripsOperation.create();
                SyncResult cleanupResult = cleanup.executeOnline(context);
                results.append("trip_cleanup=").append(cleanupResult.success ? "✅" : "❌").append(" ");
                if (!cleanupResult.success) {
                    anyFailures = true;
                }
            } catch (Exception e) {
                Log.w(TAG, "Trip cleanup failed", e);
                results.append("trip_cleanup=❌ ");
                anyFailures = true;
            }
            
            // Task 3: Stale data removal
            try {
                Log.d(TAG, "🗑️ Removing stale data");
                removeStaleData(context);
                results.append("stale_removal=✅ ");
            } catch (Exception e) {
                Log.w(TAG, "Stale data removal failed", e);
                results.append("stale_removal=❌ ");
                anyFailures = true;
            }
            
            long duration = System.currentTimeMillis() - startTime;
            String finalMessage = results.toString() + "(" + duration + "ms)";
            
            if (anyFailures) {
                Log.w(TAG, "⚠️ Maintenance completed with some failures: " + finalMessage);
                return SyncResult.failure(finalMessage);
            } else {
                Log.i(TAG, "✅ Maintenance completed successfully: " + finalMessage);
                return SyncResult.success(finalMessage);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Maintenance operation failed", e);
            return SyncResult.failure("Maintenance failed: " + e.getMessage());
        }
    }
    
    @Override
    public SyncResult executeOffline(Context context) {
        // Maintenance operations require online connectivity
        return SyncResult.failure("Maintenance requires online connectivity");
    }
    
    /**
     * Remove stale local data that's no longer needed
     */
    private void removeStaleData(Context context) {
        try {
            // Clean up temporary files
            java.io.File tripDir = new java.io.File(context.getFilesDir(), "Trip");
            if (tripDir.exists()) {
                java.io.File[] files = tripDir.listFiles();
                if (files != null) {
                    int cleanedCount = 0;
                    for (java.io.File file : files) {
                        // Remove .tmp files older than 1 hour
                        if (file.getName().endsWith(".tmp") && 
                            (System.currentTimeMillis() - file.lastModified()) > (60 * 60 * 1000)) {
                            if (file.delete()) {
                                cleanedCount++;
                            }
                        }
                        // Remove .backup files older than 24 hours
                        if (file.getName().endsWith(".backup") && 
                            (System.currentTimeMillis() - file.lastModified()) > (24 * 60 * 60 * 1000)) {
                            if (file.delete()) {
                                cleanedCount++;
                            }
                        }
                    }
                    if (cleanedCount > 0) {
                        Log.d(TAG, "Cleaned up " + cleanedCount + " stale files");
                    }
                }
            }
            
            // Clean up old sync operation logs if they exist
            java.io.File syncDir = new java.io.File(context.getFilesDir(), "Sync");
            if (syncDir.exists()) {
                java.io.File[] syncFiles = syncDir.listFiles();
                if (syncFiles != null) {
                    for (java.io.File file : syncFiles) {
                        // Remove sync files older than 7 days
                        if ((System.currentTimeMillis() - file.lastModified()) > (7 * 24 * 60 * 60 * 1000)) {
                            file.delete();
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error during stale data removal", e);
            throw e;
        }
    }
    
    @Override
    public int getPriority() {
        return 1; // Low priority - maintenance should not interfere with user operations
    }
}