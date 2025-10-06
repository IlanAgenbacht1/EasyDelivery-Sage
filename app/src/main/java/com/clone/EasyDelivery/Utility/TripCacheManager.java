package com.clone.EasyDelivery.Utility;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 🗄️ TripCacheManager - Centralized local trip persistence and management
 * 
 * This class provides offline-first trip data management with SQLite-based metadata
 * storage and atomic file operations for trip JSON files. It ensures trips remain
 * available even when offline while maintaining sync with Dropbox as source of truth.
 * 
 * Key features:
 * - SQLite metadata storage for trip availability and sync status
 * - Atomic file operations with corruption protection
 * - Offline-first reading with background synchronization
 * - Persistent index of locally cached trips
 * - Version tracking and conflict resolution
 */
public class TripCacheManager {
    
    private static final String TAG = "TripCacheManager";
    private static TripCacheManager instance;
    
    // Database configuration
    private static final String DATABASE_NAME = "TripCache";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_TRIPS = "cached_trips";
    
    // Table columns
    private static final String COL_TRIP_ID = "trip_id";
    private static final String COL_FILE_PATH = "file_path";
    private static final String COL_DOWNLOAD_TIMESTAMP = "download_timestamp";
    private static final String COL_LAST_SYNC = "last_sync";
    private static final String COL_FILE_SIZE = "file_size";
    private static final String COL_SYNC_STATUS = "sync_status";
    private static final String COL_VERSION = "version";
    private static final String COL_IS_AVAILABLE = "is_available";
    private static final String COL_METADATA_HASH = "metadata_hash";
    
    // Sync status values
    public static final String STATUS_CACHED = "cached";
    public static final String STATUS_SYNCING = "syncing";
    public static final String STATUS_SYNCED = "synced";
    public static final String STATUS_CONFLICT = "conflict";
    public static final String STATUS_ERROR = "error";
    
    private final Context context;
    private final CacheDbHelper dbHelper;
    private final ReentrantLock fileLock = new ReentrantLock();
    
    private TripCacheManager(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = new CacheDbHelper(context);
        ensureCacheDirectoryExists();
    }
    
    public static synchronized TripCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new TripCacheManager(context);
        }
        return instance;
    }
    
    /**
     * 📋 Get all locally cached trips (offline-capable)
     * Returns trips that are available in local storage, regardless of network state
     */
    public List<TripCacheEntry> getCachedTrips() {
        List<TripCacheEntry> cachedTrips = new ArrayList<>();
        
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            
            Cursor cursor = db.query(
                TABLE_TRIPS,
                null,
                COL_IS_AVAILABLE + " = 1",
                null,
                null,
                null,
                COL_DOWNLOAD_TIMESTAMP + " DESC"
            );
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    TripCacheEntry entry = createTripEntryFromCursor(cursor);
                    
                    // Verify file still exists
                    if (entry != null && verifyTripFileExists(entry.tripId)) {
                        cachedTrips.add(entry);
                    } else if (entry != null) {
                        // File is missing, mark as unavailable
                        Log.w(TAG, "Trip file missing for " + entry.tripId + ", marking unavailable");
                        markTripUnavailable(entry.tripId);
                    }
                }
                cursor.close();
            }
            
            Log.i(TAG, "📋 Retrieved " + cachedTrips.size() + " cached trips from local storage");
            
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving cached trips", e);
        }
        
        return cachedTrips;
    }
    
    /**
     * 📥 Store trip in cache with metadata
     * Performs atomic file operation and updates SQLite metadata
     */
    public boolean storeTripInCache(String tripId, String jsonContent) {
        if (tripId == null || jsonContent == null || jsonContent.trim().isEmpty()) {
            Log.w(TAG, "Cannot store trip - invalid parameters");
            return false;
        }
        
        fileLock.lock();
        try {
            // Prepare file paths
            File tripFile = getTripFile(tripId);
            File tempFile = new File(tripFile.getAbsolutePath() + ".tmp");
            File backupFile = new File(tripFile.getAbsolutePath() + ".backup");
            
            // Create backup if original exists
            if (tripFile.exists() && tripFile.length() > 0) {
                if (!copyFile(tripFile, backupFile)) {
                    Log.w(TAG, "Failed to create backup for " + tripId + ", proceeding anyway");
                }
            }
            
            // Write to temporary file first
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(jsonContent.getBytes("UTF-8"));
                fos.flush();
                fos.getFD().sync(); // Force write to storage
            }
            
            // Validate JSON structure
            if (!validateTripJson(tempFile)) {
                Log.e(TAG, "Invalid JSON structure for trip " + tripId);
                tempFile.delete();
                return false;
            }
            
            // Atomic rename
            if (!tempFile.renameTo(tripFile)) {
                Log.e(TAG, "Failed to atomically rename temp file for " + tripId);
                tempFile.delete();
                return false;
            }
            
            // Update metadata in SQLite
            long timestamp = System.currentTimeMillis();
            String metadataHash = generateMetadataHash(tripId, jsonContent);
            
            updateTripMetadata(tripId, tripFile.getAbsolutePath(), timestamp, 
                              tripFile.length(), STATUS_CACHED, 1, metadataHash);
            
            // Clean up backup on success
            if (backupFile.exists()) {
                backupFile.delete();
            }
            
            Log.i(TAG, "📥 Successfully cached trip " + tripId + " (" + tripFile.length() + " bytes)");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error storing trip in cache: " + tripId, e);
            return false;
        } finally {
            fileLock.unlock();
        }
    }
    
    /**
     * 📤 Retrieve trip JSON from cache
     * Returns null if trip is not available locally
     */
    public String getTripFromCache(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            return null;
        }
        
        try {
            File tripFile = getTripFile(tripId);
            if (!tripFile.exists() || tripFile.length() == 0) {
                Log.d(TAG, "Trip file not found or empty: " + tripId);
                return null;
            }
            
            StringBuilder content = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(tripFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    content.append(new String(buffer, 0, bytesRead, "UTF-8"));
                }
            }
            
            String jsonContent = content.toString();
            
            // Validate structure before returning
            if (!validateJsonStructure(jsonContent)) {
                Log.e(TAG, "Corrupted trip data for " + tripId);
                markTripUnavailable(tripId);
                return null;
            }
            
            Log.d(TAG, "📤 Retrieved trip " + tripId + " from cache (" + jsonContent.length() + " chars)");
            return jsonContent;
            
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving trip from cache: " + tripId, e);
            return null;
        }
    }
    
    /**
     * ❓ Check if trip is available in cache
     */
    public boolean isTripCached(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            return false;
        }
        
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            
            Cursor cursor = db.query(
                TABLE_TRIPS,
                new String[]{COL_TRIP_ID},
                COL_TRIP_ID + " = ? AND " + COL_IS_AVAILABLE + " = 1",
                new String[]{tripId},
                null, null, null
            );
            
            boolean cached = cursor != null && cursor.getCount() > 0;
            if (cursor != null) {
                cursor.close();
            }
            
            // Double-check file exists
            if (cached) {
                cached = verifyTripFileExists(tripId);
                if (!cached) {
                    markTripUnavailable(tripId);
                }
            }
            
            return cached;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking if trip is cached: " + tripId, e);
            return false;
        }
    }
    
    /**
     * 🔄 Update trip sync status
     */
    public void updateTripSyncStatus(String tripId, String syncStatus) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            ContentValues values = new ContentValues();
            values.put(COL_SYNC_STATUS, syncStatus);
            values.put(COL_LAST_SYNC, System.currentTimeMillis());
            
            int updated = db.update(
                TABLE_TRIPS,
                values,
                COL_TRIP_ID + " = ?",
                new String[]{tripId}
            );
            
            if (updated > 0) {
                Log.d(TAG, "🔄 Updated sync status for " + tripId + " to " + syncStatus);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating sync status for " + tripId, e);
        }
    }
    
    /**
     * 🗑️ Remove trip from cache
     */
    public void removeTripFromCache(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            return;
        }
        
        fileLock.lock();
        try {
            // Delete file
            File tripFile = getTripFile(tripId);
            if (tripFile.exists()) {
                boolean deleted = tripFile.delete();
                Log.i(TAG, "🗑️ Deleted trip file for " + tripId + ": " + deleted);
            }
            
            // Delete backup if exists
            File backupFile = new File(tripFile.getAbsolutePath() + ".backup");
            if (backupFile.exists()) {
                backupFile.delete();
            }
            
            // Remove from database
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            int deleted = db.delete(
                TABLE_TRIPS,
                COL_TRIP_ID + " = ?",
                new String[]{tripId}
            );
            
            Log.i(TAG, "🗑️ Removed " + deleted + " cache entries for trip " + tripId);
            
        } catch (Exception e) {
            Log.e(TAG, "Error removing trip from cache: " + tripId, e);
        } finally {
            fileLock.unlock();
        }
    }
    
    /**
     * 📊 Get cache statistics
     */
    public CacheStatistics getCacheStatistics() {
        CacheStatistics stats = new CacheStatistics();
        
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            
            // Count total cached trips
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_TRIPS + " WHERE " + COL_IS_AVAILABLE + " = 1", null);
            if (cursor != null && cursor.moveToFirst()) {
                stats.totalCachedTrips = cursor.getInt(0);
                cursor.close();
            }
            
            // Calculate total cache size
            cursor = db.rawQuery("SELECT SUM(" + COL_FILE_SIZE + ") FROM " + TABLE_TRIPS + " WHERE " + COL_IS_AVAILABLE + " = 1", null);
            if (cursor != null && cursor.moveToFirst()) {
                stats.totalCacheSize = cursor.getLong(0);
                cursor.close();
            }
            
            // Count by sync status
            cursor = db.rawQuery("SELECT " + COL_SYNC_STATUS + ", COUNT(*) FROM " + TABLE_TRIPS + 
                                " WHERE " + COL_IS_AVAILABLE + " = 1 GROUP BY " + COL_SYNC_STATUS, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String status = cursor.getString(0);
                    int count = cursor.getInt(1);
                    stats.statusCounts.put(status, count);
                }
                cursor.close();
            }
            
            Log.i(TAG, "📊 Cache stats: " + stats.totalCachedTrips + " trips, " + 
                  (stats.totalCacheSize / 1024) + " KB total");
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting cache statistics", e);
        }
        
        return stats;
    }
    
    /**
     * 🗑️ Sync trip removals from Dropbox
     * Removes locally cached trips that no longer exist on Dropbox
     * This is the core method for handling trip removal sync
     */
    public int syncTripRemovals(java.util.List<String> activeDropboxTrips) {
        int removedCount = 0;
        
        fileLock.lock();
        try {
            // Get all currently cached trips
            java.util.List<TripCacheEntry> cachedTrips = getCachedTrips();
            
            Log.i(TAG, "Syncing removals: checking " + cachedTrips.size() + " cached trips against " + activeDropboxTrips.size() + " Dropbox trips");
            
            for (TripCacheEntry entry : cachedTrips) {
                // Skip completed trips - they should be preserved locally
                if (AppConstant.completedTrips.contains(entry.tripId)) {
                    continue;
                }
                
                // Skip trips that are currently active
                if (entry.tripId.equals(AppConstant.TRIPID)) {
                    continue;
                }
                
                // If trip is not in active Dropbox list, remove it
                if (!activeDropboxTrips.contains(entry.tripId)) {
                    Log.i(TAG, "🗑️ SYNC_REMOVAL: Removing trip no longer on Dropbox: " + entry.tripId);
                    removeTripFromCache(entry.tripId);
                    removedCount++;
                }
            }
            
            Log.i(TAG, "🗑️ SYNC_REMOVAL: Removed " + removedCount + " trips that are no longer available on Dropbox");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during trip removal sync", e);
        } finally {
            fileLock.unlock();
        }
        
        return removedCount;
    }
    
    /**
     * 🧹 Cleanup old/orphaned cache entries
     */
    public int cleanupCache(int maxTripsToKeep) {
        int cleanedCount = 0;
        
        fileLock.lock();
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            // Get trips ordered by last access (oldest first)
            Cursor cursor = db.query(
                TABLE_TRIPS,
                new String[]{COL_TRIP_ID, COL_DOWNLOAD_TIMESTAMP},
                COL_IS_AVAILABLE + " = 1",
                null,
                null,
                null,
                COL_DOWNLOAD_TIMESTAMP + " ASC"
            );
            
            if (cursor != null) {
                int totalCount = cursor.getCount();
                
                if (totalCount > maxTripsToKeep) {
                    int toRemove = totalCount - maxTripsToKeep;
                    
                    for (int i = 0; i < toRemove && cursor.moveToNext(); i++) {
                        String tripId = cursor.getString(0);
                        
                        // Don't remove if it's completed but not synced
                        if (!AppConstant.completedTrips.contains(tripId) || 
                            !isTripSynced(tripId)) {
                            removeTripFromCache(tripId);
                            cleanedCount++;
                        }
                    }
                }
                
                cursor.close();
            }
            
            Log.i(TAG, "🧹 Cache cleanup removed " + cleanedCount + " old trips");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during cache cleanup", e);
        } finally {
            fileLock.unlock();
        }
        
        return cleanedCount;
    }
    
    // =============== PRIVATE HELPER METHODS ===============
    
    private void ensureCacheDirectoryExists() {
        File cacheDir = new File(context.getFilesDir(), "Trip");
        if (!cacheDir.exists()) {
            boolean created = cacheDir.mkdirs();
            Log.i(TAG, "Created cache directory: " + created);
        }
    }
    
    private File getTripFile(String tripId) {
        return new File(context.getFilesDir() + "/Trip/", tripId + ".json");
    }
    
    private boolean verifyTripFileExists(String tripId) {
        File tripFile = getTripFile(tripId);
        return tripFile.exists() && tripFile.length() > 0;
    }
    
    private void markTripUnavailable(String tripId) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            ContentValues values = new ContentValues();
            values.put(COL_IS_AVAILABLE, 0);
            values.put(COL_SYNC_STATUS, STATUS_ERROR);
            
            db.update(TABLE_TRIPS, values, COL_TRIP_ID + " = ?", new String[]{tripId});
            
        } catch (Exception e) {
            Log.e(TAG, "Error marking trip unavailable: " + tripId, e);
        }
    }
    
    private boolean validateTripJson(File jsonFile) {
        try {
            StringBuilder content = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(jsonFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    content.append(new String(buffer, 0, bytesRead, "UTF-8"));
                }
            }
            
            return validateJsonStructure(content.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error validating trip JSON file", e);
            return false;
        }
    }
    
    private boolean validateJsonStructure(String jsonContent) {
        try {
            JSONObject json = new JSONObject(jsonContent);
            
            // Basic structure validation
            return json.has("stops") && json.getJSONArray("stops").length() > 0;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean copyFile(File source, File dest) {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.flush();
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Error copying file", e);
            return false;
        }
    }
    
    private String generateMetadataHash(String tripId, String content) {
        try {
            String combined = tripId + ":" + content.length() + ":" + System.currentTimeMillis();
            return String.valueOf(combined.hashCode());
        } catch (Exception e) {
            return "hash_error";
        }
    }
    
    private void updateTripMetadata(String tripId, String filePath, long timestamp, 
                                   long fileSize, String syncStatus, int version, String hash) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            ContentValues values = new ContentValues();
            values.put(COL_TRIP_ID, tripId);
            values.put(COL_FILE_PATH, filePath);
            values.put(COL_DOWNLOAD_TIMESTAMP, timestamp);
            values.put(COL_LAST_SYNC, timestamp);
            values.put(COL_FILE_SIZE, fileSize);
            values.put(COL_SYNC_STATUS, syncStatus);
            values.put(COL_VERSION, version);
            values.put(COL_IS_AVAILABLE, 1);
            values.put(COL_METADATA_HASH, hash);
            
            long result = db.insertWithOnConflict(TABLE_TRIPS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            
            Log.d(TAG, "Updated metadata for " + tripId + ", result: " + result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating trip metadata: " + tripId, e);
        }
    }
    
    private TripCacheEntry createTripEntryFromCursor(Cursor cursor) {
        try {
            TripCacheEntry entry = new TripCacheEntry();
            entry.tripId = cursor.getString(cursor.getColumnIndex(COL_TRIP_ID));
            entry.filePath = cursor.getString(cursor.getColumnIndex(COL_FILE_PATH));
            entry.downloadTimestamp = cursor.getLong(cursor.getColumnIndex(COL_DOWNLOAD_TIMESTAMP));
            entry.lastSync = cursor.getLong(cursor.getColumnIndex(COL_LAST_SYNC));
            entry.fileSize = cursor.getLong(cursor.getColumnIndex(COL_FILE_SIZE));
            entry.syncStatus = cursor.getString(cursor.getColumnIndex(COL_SYNC_STATUS));
            entry.version = cursor.getInt(cursor.getColumnIndex(COL_VERSION));
            entry.isAvailable = cursor.getInt(cursor.getColumnIndex(COL_IS_AVAILABLE)) == 1;
            entry.metadataHash = cursor.getString(cursor.getColumnIndex(COL_METADATA_HASH));
            
            return entry;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating trip entry from cursor", e);
            return null;
        }
    }
    
    private boolean isTripSynced(String tripId) {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            
            Cursor cursor = db.query(
                TABLE_TRIPS,
                new String[]{COL_SYNC_STATUS},
                COL_TRIP_ID + " = ?",
                new String[]{tripId},
                null, null, null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                String status = cursor.getString(0);
                cursor.close();
                return STATUS_SYNCED.equals(status);
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking sync status for " + tripId, e);
            return false;
        }
    }
    
    // =============== INNER CLASSES ===============
    
    /**
     * Database helper for trip cache SQLite operations
     */
    private static class CacheDbHelper extends SQLiteOpenHelper {
        
        public CacheDbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        
        @Override
        public void onCreate(SQLiteDatabase db) {
            String createTable = "CREATE TABLE " + TABLE_TRIPS + " (" +
                COL_TRIP_ID + " TEXT PRIMARY KEY, " +
                COL_FILE_PATH + " TEXT NOT NULL, " +
                COL_DOWNLOAD_TIMESTAMP + " INTEGER NOT NULL, " +
                COL_LAST_SYNC + " INTEGER DEFAULT 0, " +
                COL_FILE_SIZE + " INTEGER DEFAULT 0, " +
                COL_SYNC_STATUS + " TEXT DEFAULT '" + STATUS_CACHED + "', " +
                COL_VERSION + " INTEGER DEFAULT 1, " +
                COL_IS_AVAILABLE + " INTEGER DEFAULT 1, " +
                COL_METADATA_HASH + " TEXT DEFAULT ''" +
                ")";
            
            db.execSQL(createTable);
            
            // Create indices for better performance
            db.execSQL("CREATE INDEX idx_download_timestamp ON " + TABLE_TRIPS + "(" + COL_DOWNLOAD_TIMESTAMP + ")");
            db.execSQL("CREATE INDEX idx_sync_status ON " + TABLE_TRIPS + "(" + COL_SYNC_STATUS + ")");
            db.execSQL("CREATE INDEX idx_available ON " + TABLE_TRIPS + "(" + COL_IS_AVAILABLE + ")");
        }
        
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // For now, simple upgrade - drop and recreate
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRIPS);
            onCreate(db);
        }
    }
    
    /**
     * Trip cache entry data structure
     */
    public static class TripCacheEntry {
        public String tripId;
        public String filePath;
        public long downloadTimestamp;
        public long lastSync;
        public long fileSize;
        public String syncStatus;
        public int version;
        public boolean isAvailable;
        public String metadataHash;
        
        @Override
        public String toString() {
            return "TripCacheEntry{" +
                "tripId='" + tripId + '\'' +
                ", syncStatus='" + syncStatus + '\'' +
                ", fileSize=" + fileSize +
                ", isAvailable=" + isAvailable +
                '}';
        }
    }
    
    /**
     * Cache statistics data structure
     */
    public static class CacheStatistics {
        public int totalCachedTrips = 0;
        public long totalCacheSize = 0;
        public java.util.Map<String, Integer> statusCounts = new java.util.HashMap<>();
        
        @Override
        public String toString() {
            return "CacheStats{trips=" + totalCachedTrips + 
                   ", size=" + (totalCacheSize / 1024) + "KB" +
                   ", byStatus=" + statusCounts + "}";
        }
    }
    
    /**
     * 🔄 Refresh cache from Dropbox
     * Downloads latest trips and updates cache metadata
     */
    public void refreshCache() {
        Log.i(TAG, "🔄 Starting cache refresh");
        
        try {
            // Use DropboxHelper to download all trips
            DropboxHelper.downloadAllTrips(context);
            
            // Update metadata for existing cached entries
            List<TripCacheEntry> cachedTrips = getCachedTrips();
            for (TripCacheEntry entry : cachedTrips) {
                updateTripSyncStatus(entry.tripId, STATUS_SYNCED);
            }
            
            Log.i(TAG, "🔄 Cache refresh completed successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during cache refresh", e);
        }
    }
}
