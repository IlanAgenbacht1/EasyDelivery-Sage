package com.clone.EasyDelivery.Utility.operations;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;

import com.clone.EasyDelivery.Database.DeliveryDb;
import com.clone.EasyDelivery.Model.Return;
import com.clone.EasyDelivery.Utility.JsonHandler;
import com.clone.EasyDelivery.Utility.DropboxHelper;
import java.util.List;

/**
 * 📦 Operation for syncing returns data to Dropbox
 * 
 * This operation handles uploading returns information to Dropbox and
 * cleaning up local return records after successful sync.
 */
public class SyncReturnsOperation extends SyncOperation {
    private static final String TAG = "SyncReturnsOperation";
    
    public SyncReturnsOperation(JSONObject data) {
        super("SYNC_RETURNS", null, data); // No specific trip ID for returns
    }
    
    @Override
    public SyncResult executeOnline(Context context) {
        try {
            Log.i(TAG, "Online sync returns data");
            
            DeliveryDb database = new DeliveryDb(context);
            database.open();
            
            try {
                // First download existing returns file from Dropbox
                DropboxHelper.downloadReturnFile(context);
                
                // Get all returns from database
                List<Return> returnsList = database.getReturnsList();
                Log.i(TAG, "Found " + returnsList.size() + " returns to sync");
                
                if (returnsList.isEmpty()) {
                    Log.i(TAG, "No returns to sync");
                    return SyncResult.success("No returns to sync");
                }
                
                int syncedCount = 0;
                int failedCount = 0;
                
                for (Return returnData : returnsList) {
                    try {
                        // Write return data to JSON file
                        File returnFile = JsonHandler.writeReturnFile(context, returnData);
                        
                        if (returnFile == null) {
                            Log.e(TAG, "Failed to write return file for item: " + returnData.getItem());
                            failedCount++;
                            continue;
                        }
                        
                        // Upload returns file to Dropbox
                        boolean uploadSuccess = DropboxHelper.uploadReturnsFile(context);
                        
                        if (uploadSuccess) {
                            // Delete from local database after successful upload
                            database.deleteReturns(returnData.getItem());
                            syncedCount++;
                            
                            Log.i(TAG, "Return " + returnData.getItem() + " synced successfully");
                        } else {
                            // Clean up failed file
                            returnFile.delete();
                            failedCount++;
                            
                            Log.e(TAG, "Failed to upload return: " + returnData.getItem());
                        }
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing return item: " + returnData.getItem(), e);
                        failedCount++;
                    }
                }
                
                String resultMessage = String.format("Returns sync completed: %d synced, %d failed", 
                    syncedCount, failedCount);
                
                Log.i(TAG, resultMessage);
                
                if (failedCount == 0) {
                    return SyncResult.success(resultMessage);
                } else {
                    return SyncResult.success(resultMessage + " (some failures occurred)");
                }
                
            } finally {
                database.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in online sync returns", e);
            return SyncResult.failure("Online returns sync failed: " + e.getMessage());
        }
    }
    
    @Override
    public SyncResult executeOffline(Context context) {
        try {
            Log.i(TAG, "Offline sync returns data");
            
            // For offline execution, returns are already stored in the local database
            // Just confirm they're queued for sync
            return SyncResult.success("Returns queued for sync when online");
            
        } catch (Exception e) {
            return SyncResult.failure("Offline returns sync failed: " + e.getMessage());
        }
    }
    
    @Override
    public int getPriority() {
        return 1; // Normal priority - returns are not urgent
    }
    
    /**
     * Factory method to create returns sync operation
     */
    public static SyncReturnsOperation create() {
        try {
            JSONObject data = new JSONObject();
            data.put("operationType", "returnsSync");
            data.put("timestamp", System.currentTimeMillis());
            
            return new SyncReturnsOperation(data);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating sync returns operation", e);
            return null;
        }
    }
}