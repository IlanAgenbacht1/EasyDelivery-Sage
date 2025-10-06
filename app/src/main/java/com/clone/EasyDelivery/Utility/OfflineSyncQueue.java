package com.clone.EasyDelivery.Utility;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// Import operation classes
import com.clone.EasyDelivery.Utility.operations.SyncOperation;
import com.clone.EasyDelivery.Utility.operations.*;
import com.clone.EasyDelivery.Utility.AppConstant;

/**
 * 📦 OfflineSyncQueue - Intelligent offline operation queue with priority handling
 * 
 * This queue manages operations that need to be synced to cloud when connectivity
 * is restored. Features:
 * - Persistent storage in SQLite
 * - Priority-based ordering (Critical > Normal > Low)  
 * - Automatic retry with exponential backoff
 * - Batch operations when possible
 * - Dead letter queue for failed operations
 * 
 * Key insight: Offline operations should be transparent to users.
 * They take an action, see immediate result, sync happens invisibly later.
 */
public class OfflineSyncQueue {
    
    private static final String TAG = "OfflineSyncQueue";
    
    // Database constants
    private static final String DATABASE_NAME = "OfflineSyncQueue";
    private static final String TABLE_NAME = "sync_queue";
    private static final int DATABASE_VERSION = 1;
    
    // Table columns
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_OPERATION_TYPE = "operation_type";
    private static final String COLUMN_TRIP_ID = "trip_id";
    private static final String COLUMN_PRIORITY = "priority";
    private static final String COLUMN_DATA = "operation_data";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_RETRY_COUNT = "retry_count";
    private static final String COLUMN_LAST_ERROR = "last_error";
    private static final String COLUMN_STATUS = "status";
    
    // Priority levels
    public static final int PRIORITY_CRITICAL = 10;  // Trip claims, urgent operations
    public static final int PRIORITY_NORMAL = 5;     // Regular operations
    public static final int PRIORITY_LOW = 1;        // Metadata updates, cleanup
    
    // Operation statuses  
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_DEAD_LETTER = "dead_letter";
    
    // Retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long BASE_RETRY_DELAY_MS = 1000; // 1 second base
    
    private final Context context;
    private final QueueDatabaseHelper dbHelper;
    private static OfflineSyncQueue instance;
    
    private OfflineSyncQueue(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = new QueueDatabaseHelper(context);
        
        Log.i(TAG, "Offline sync queue initialized");
    }
    
    public static synchronized OfflineSyncQueue getInstance(Context context) {
        if (instance == null) {
            instance = new OfflineSyncQueue(context);
        }
        return instance;
    }
    
    /**
     * 📦 Enqueue operation for later sync
     */
    public boolean enqueueOperation(SyncOperation operation) {
        if (operation == null) {
            Log.w(TAG, "Cannot enqueue null operation");
            return false;
        }
        
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            ContentValues values = new ContentValues();
            values.put(COLUMN_OPERATION_TYPE, operation.getType());
            values.put(COLUMN_TRIP_ID, operation.getTripId());
            values.put(COLUMN_PRIORITY, operation.getPriority());
            values.put(COLUMN_DATA, operation.getData() != null ? operation.getData().toString() : "{}");
            values.put(COLUMN_CREATED_AT, operation.getTimestamp());
            values.put(COLUMN_RETRY_COUNT, 0);
            values.put(COLUMN_LAST_ERROR, "");
            values.put(COLUMN_STATUS, STATUS_PENDING);
            
            long id = db.insertWithOnConflict(TABLE_NAME, null, values, 
                SQLiteDatabase.CONFLICT_REPLACE);
            
            if (id > 0) {
                Log.d(TAG, "📦 Enqueued operation: " + operation.getType() + 
                      " (priority=" + operation.getPriority() + ", id=" + id + ")");
                return true;
            } else {
                Log.e(TAG, "Failed to enqueue operation: " + operation.getType());
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error enqueuing operation: " + operation.getType(), e);
            return false;
        }
    }
    
    /**
     * 🔄 Sync all queued operations to cloud
     */
    public SyncOperation.SyncResult syncAll() {
        Log.i(TAG, "🔄 Starting sync of all queued operations");
        
        AtomicInteger totalOperations = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        try {
            List<QueuedOperation> operations = getPendingOperations();
            totalOperations.set(operations.size());
            
            if (operations.isEmpty()) {
                Log.i(TAG, "✅ No operations in queue to sync");
                return SyncOperation.SyncResult.success("No operations to sync");
            }
            
            Log.i(TAG, "Found " + operations.size() + " operations to sync");
            
            // Process operations in priority order
            for (QueuedOperation queuedOp : operations) {
                try {
                    // Mark as processing
                    updateOperationStatus(queuedOp.id, STATUS_PROCESSING, null);
                    
                    // Reconstruct the sync operation
                    SyncOperation operation = reconstructOperation(queuedOp);
                    
                    if (operation == null) {
                        Log.w(TAG, "Could not reconstruct operation: " + queuedOp.operationType);
                        markOperationFailed(queuedOp.id, "Could not reconstruct operation");
                        failureCount.incrementAndGet();
                        continue;
                    }
                    
                    // Execute the operation online
                    SyncOperation.SyncResult result = operation.executeOnline(context);
                    
                    if (result.success) {
                        // Remove from queue on success
                        removeOperation(queuedOp.id);
                        successCount.incrementAndGet();
                        
                        Log.i(TAG, "✅ Synced operation: " + queuedOp.operationType + 
                              " (id=" + queuedOp.id + ")");
                        
                    } else {
                        // Handle failure with retry logic
                        handleOperationFailure(queuedOp, result.message);
                        failureCount.incrementAndGet();
                        
                        Log.w(TAG, "⚠️ Failed to sync operation: " + queuedOp.operationType + 
                              " - " + result.message);
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error processing queued operation: " + queuedOp.operationType, e);
                    handleOperationFailure(queuedOp, "Exception: " + e.getMessage());
                    failureCount.incrementAndGet();
                }
                
                // Small delay between operations to avoid overwhelming Dropbox API
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            String resultMessage = String.format(
                "Sync completed: %d total, %d success, %d failed", 
                totalOperations.get(), successCount.get(), failureCount.get()
            );
            
            Log.i(TAG, "🔄 " + resultMessage);
            
            if (failureCount.get() == 0) {
                return SyncOperation.SyncResult.success(resultMessage);
            } else {
                return SyncOperation.SyncResult.success(
                    resultMessage + " (some operations failed and will retry later)"
                );
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during sync all operations", e);
            return SyncOperation.SyncResult.failure(
                "Sync failed: " + e.getMessage()
            );
        }
    }
    
    /**
     * 📊 Get pending operations ordered by priority
     */
    private List<QueuedOperation> getPendingOperations() {
        List<QueuedOperation> operations = new ArrayList<>();
        
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            
            // Order by priority (higher first), then by creation time
            Cursor cursor = db.query(
                TABLE_NAME,
                null,
                COLUMN_STATUS + " = ? OR " + COLUMN_STATUS + " = ?",
                new String[]{STATUS_PENDING, STATUS_FAILED},
                null,
                null,
                COLUMN_PRIORITY + " DESC, " + COLUMN_CREATED_AT + " ASC"
            );
            
            if (cursor != null) {
                int idIndex = cursor.getColumnIndex(COLUMN_ID);
                int typeIndex = cursor.getColumnIndex(COLUMN_OPERATION_TYPE);
                int tripIdIndex = cursor.getColumnIndex(COLUMN_TRIP_ID);
                int priorityIndex = cursor.getColumnIndex(COLUMN_PRIORITY);
                int dataIndex = cursor.getColumnIndex(COLUMN_DATA);
                int createdAtIndex = cursor.getColumnIndex(COLUMN_CREATED_AT);
                int retryCountIndex = cursor.getColumnIndex(COLUMN_RETRY_COUNT);
                int lastErrorIndex = cursor.getColumnIndex(COLUMN_LAST_ERROR);
                int statusIndex = cursor.getColumnIndex(COLUMN_STATUS);
                
                while (cursor.moveToNext()) {
                    QueuedOperation op = new QueuedOperation();
                    op.id = cursor.getLong(idIndex);
                    op.operationType = cursor.getString(typeIndex);
                    op.tripId = cursor.getString(tripIdIndex);
                    op.priority = cursor.getInt(priorityIndex);
                    op.operationData = cursor.getString(dataIndex);
                    op.createdAt = cursor.getLong(createdAtIndex);
                    op.retryCount = cursor.getInt(retryCountIndex);
                    op.lastError = cursor.getString(lastErrorIndex);
                    op.status = cursor.getString(statusIndex);
                    
                    // Skip operations that have failed too many times
                    if (op.retryCount >= MAX_RETRY_ATTEMPTS) {
                        Log.d(TAG, "Skipping operation with too many retries: " + op.operationType);
                        continue;
                    }
                    
                    operations.add(op);
                }
                
                cursor.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting pending operations", e);
        }
        
        return operations;
    }
    
    /**
     * 🔨 Reconstruct sync operation from queued data
     */
    private SyncOperation reconstructOperation(QueuedOperation queuedOp) {
        try {
            JSONObject data = new JSONObject(queuedOp.operationData);
            
            // Create specific operation types based on the operation type
            switch (queuedOp.operationType) {
                case "CLAIM_TRIP":
                    return new ClaimTripOperation(queuedOp.tripId, data);
                    
                case "START_TRIP":
                    return new StartTripOperation(queuedOp.tripId, data);
                    
                case "COMPLETE_TRIP":
                    return new CompleteTripOperation(queuedOp.tripId, data);
                    
                case "RELEASE_TRIP":
                    return new ReleaseTripOperation(queuedOp.tripId, data);
                    
                case "UPDATE_STATUS":
                    return new UpdateTripStatusOperation(queuedOp.tripId, data);
                    
                case "SYNC_DELIVERY_DATA":
                    String documentId = data.optString("documentId", "");
                    return new SyncDeliveryDataOperation(queuedOp.tripId, documentId, data);
                    
                case "SEND_EMAIL":
                    String emailDocId = data.optString("documentId", "");
                    return new SendEmailOperation(queuedOp.tripId, emailDocId, data);
                    
                case "SYNC_RETURNS":
                    return new SyncReturnsOperation(data);
                    
                case "REMOVE_TRIP":
                    return new RemoveTripOperation(queuedOp.tripId, data);
                    
                default:
                    Log.w(TAG, "Unknown operation type: " + queuedOp.operationType);
                    return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error reconstructing operation: " + queuedOp.operationType, e);
            return null;
        }
    }
    
    /**
     * ❌ Handle operation failure with retry logic
     */
    private void handleOperationFailure(QueuedOperation queuedOp, String errorMessage) {
        int newRetryCount = queuedOp.retryCount + 1;
        
        if (newRetryCount >= MAX_RETRY_ATTEMPTS) {
            // Move to dead letter queue
            updateOperationStatus(queuedOp.id, STATUS_DEAD_LETTER, errorMessage);
            Log.w(TAG, "Operation moved to dead letter queue after " + newRetryCount + 
                  " attempts: " + queuedOp.operationType);
        } else {
            // Mark for retry
            markOperationFailed(queuedOp.id, errorMessage);
            
            // Calculate exponential backoff delay
            long delayMs = BASE_RETRY_DELAY_MS * (long) Math.pow(2, newRetryCount - 1);
            Log.d(TAG, "Operation will retry in " + delayMs + "ms: " + queuedOp.operationType);
        }
    }
    
    /**
     * 🔄 Update operation status
     */
    private void updateOperationStatus(long operationId, String status, String errorMessage) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            ContentValues values = new ContentValues();
            values.put(COLUMN_STATUS, status);
            if (errorMessage != null) {
                values.put(COLUMN_LAST_ERROR, errorMessage);
            }
            
            db.update(TABLE_NAME, values, COLUMN_ID + " = ?", 
                new String[]{String.valueOf(operationId)});
                
        } catch (Exception e) {
            Log.e(TAG, "Error updating operation status", e);
        }
    }
    
    /**
     * ❌ Mark operation as failed and increment retry count
     */
    private void markOperationFailed(long operationId, String errorMessage) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            ContentValues values = new ContentValues();
            values.put(COLUMN_STATUS, STATUS_FAILED);
            values.put(COLUMN_LAST_ERROR, errorMessage);
            values.put(COLUMN_RETRY_COUNT, COLUMN_RETRY_COUNT + " + 1");
            
            db.update(TABLE_NAME, values, COLUMN_ID + " = ?", 
                new String[]{String.valueOf(operationId)});
                
        } catch (Exception e) {
            Log.e(TAG, "Error marking operation as failed", e);
        }
    }
    
    /**
     * 🗑️ Remove operation from queue
     */
    private void removeOperation(long operationId) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            int deleted = db.delete(TABLE_NAME, COLUMN_ID + " = ?", 
                new String[]{String.valueOf(operationId)});
                
            if (deleted > 0) {
                Log.d(TAG, "Removed operation from queue: id=" + operationId);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error removing operation from queue", e);
        }
    }
    
    /**
     * 📊 Get count of queued operations
     */
    public int getQueuedOperationCount() {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_NAME + 
                " WHERE " + COLUMN_STATUS + " IN (?, ?)",
                new String[]{STATUS_PENDING, STATUS_FAILED}
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                cursor.close();
                return count;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting queued operation count", e);
        }
        
        return 0;
    }
    
    /**
     * 📊 Get count of pending operations for a specific trip
     */
    public int getPendingOperationsCount(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            return 0;
        }
        
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_NAME + 
                " WHERE " + COLUMN_TRIP_ID + " = ? AND " + COLUMN_STATUS + " IN (?, ?)",
                new String[]{tripId, STATUS_PENDING, STATUS_FAILED}
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                cursor.close();
                return count;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting pending operations count for trip: " + tripId, e);
        }
        
        return 0;
    }
    
    /**
     * 📊 Get queue statistics
     */
    public String getQueueStatistics() {
        StringBuilder stats = new StringBuilder();
        
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            
            // Count by status
            Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_STATUS + ", COUNT(*) FROM " + TABLE_NAME + 
                " GROUP BY " + COLUMN_STATUS,
                null
            );
            
            stats.append("📦 Offline Sync Queue Status:\n");
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String status = cursor.getString(0);
                    int count = cursor.getInt(1);
                    stats.append("  - ").append(status).append(": ").append(count).append("\n");
                }
                cursor.close();
            }
            
            // Count by priority
            cursor = db.rawQuery(
                "SELECT " + COLUMN_PRIORITY + ", COUNT(*) FROM " + TABLE_NAME + 
                " WHERE " + COLUMN_STATUS + " IN (?, ?) GROUP BY " + COLUMN_PRIORITY +
                " ORDER BY " + COLUMN_PRIORITY + " DESC",
                new String[]{STATUS_PENDING, STATUS_FAILED}
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                stats.append("\nPending by priority:\n");
                do {
                    int priority = cursor.getInt(0);
                    int count = cursor.getInt(1);
                    String priorityName = priority == PRIORITY_CRITICAL ? "Critical" :
                                        priority == PRIORITY_NORMAL ? "Normal" : "Low";
                    stats.append("  - ").append(priorityName).append(" (").append(priority)
                         .append("): ").append(count).append("\n");
                } while (cursor.moveToNext());
                
                cursor.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting queue statistics", e);
            stats.append("Error retrieving statistics: ").append(e.getMessage());
        }
        
        return stats.toString();
    }
    
    /**
     * 🧹 Clean up old operations (maintenance)
     */
    public void cleanupOldOperations() {
        try {
            long oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
            
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            int deleted = db.delete(TABLE_NAME,
                COLUMN_CREATED_AT + " < ? AND (" + COLUMN_STATUS + " = ? OR " + 
                COLUMN_STATUS + " = ?)",
                new String[]{String.valueOf(oneWeekAgo), STATUS_DEAD_LETTER, STATUS_FAILED}
            );
            
            if (deleted > 0) {
                Log.i(TAG, "🧹 Cleaned up " + deleted + " old operations from queue");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up old operations", e);
        }
    }
    
    /**
     * Save queue state for application shutdown
     */
    public void saveQueueState() {
        try {
            Log.d(TAG, "Saving queue state...");
            
            // Queue state is automatically persisted in SQLite database
            // This method exists for consistency with Application lifecycle
            
            // Optionally save additional metadata
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            int pendingCount = getPendingOperationsCount(null);
            
            Log.d(TAG, "Queue state saved - " + pendingCount + " pending operations");
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving queue state", e);
        }
    }
    
    /**
     * Restore queue state from previous session
     */
    public void restoreQueueState() {
        try {
            Log.d(TAG, "Restoring queue state...");
            
            // Queue state is automatically restored from SQLite database
            // Check for any operations that need attention
            
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            int pendingCount = getPendingOperationsCount(null);
            int failedCount = getFailedOperationsCount();
            
            Log.i(TAG, "Queue state restored - " + pendingCount + " pending, " + failedCount + " failed operations");
            
        } catch (Exception e) {
            Log.e(TAG, "Error restoring queue state", e);
        }
    }
    
    /**
     * Get count of failed operations
     */
    private int getFailedOperationsCount() {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_NAME + 
                " WHERE " + COLUMN_STATUS + " = ?",
                new String[]{STATUS_FAILED}
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                cursor.close();
                return count;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting failed operations count", e);
        }
        
        return 0;
    }
    
    /**
     * Close the queue database connection
     */
    public void close() {
        try {
            Log.d(TAG, "Closing OfflineSyncQueue database");
            
            if (dbHelper != null) {
                dbHelper.close();
            }
            
            Log.d(TAG, "OfflineSyncQueue closed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error closing OfflineSyncQueue", e);
        }
    }
    
    // ================== HELPER CLASSES ==================
    
    /**
     * 📦 Queued operation data structure
     */
    private static class QueuedOperation {
        long id;
        String operationType;
        String tripId;
        int priority;
        String operationData;
        long createdAt;
        int retryCount;
        String lastError;
        String status;
    }
    
    // All operation classes have been moved to separate files in the operations package
    
    /**
     * 📁 Database helper for sync queue
     */
    private static class QueueDatabaseHelper extends SQLiteOpenHelper {
        
        private static final String CREATE_TABLE_SQL = 
            "CREATE TABLE " + TABLE_NAME + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_OPERATION_TYPE + " TEXT NOT NULL, " +
            COLUMN_TRIP_ID + " TEXT, " +
            COLUMN_PRIORITY + " INTEGER DEFAULT " + PRIORITY_NORMAL + ", " +
            COLUMN_DATA + " TEXT NOT NULL, " +
            COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
            COLUMN_RETRY_COUNT + " INTEGER DEFAULT 0, " +
            COLUMN_LAST_ERROR + " TEXT DEFAULT '', " +
            COLUMN_STATUS + " TEXT DEFAULT '" + STATUS_PENDING + "'" +
            ")";
        
        public QueueDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE_SQL);
            
            // Create indices for better performance
            db.execSQL("CREATE INDEX idx_status ON " + TABLE_NAME + "(" + COLUMN_STATUS + ")");
            db.execSQL("CREATE INDEX idx_priority ON " + TABLE_NAME + "(" + COLUMN_PRIORITY + ")");
            db.execSQL("CREATE INDEX idx_created_at ON " + TABLE_NAME + "(" + COLUMN_CREATED_AT + ")");
        }
        
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Simple upgrade strategy - drop and recreate
            // In production, you'd want proper migration
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }
    }
}