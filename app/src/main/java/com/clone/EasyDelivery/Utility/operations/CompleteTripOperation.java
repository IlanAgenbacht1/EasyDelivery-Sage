package com.clone.EasyDelivery.Utility.operations;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import com.clone.EasyDelivery.Utility.DropboxHelper;
import com.clone.EasyDelivery.Utility.AppConstant;
import com.clone.EasyDelivery.Utility.OfflineSyncQueue;
import com.clone.EasyDelivery.Database.DeliveryDb;
import com.clone.EasyDelivery.Model.Delivery;
import java.util.List;

/**
 * 🏁 Operation for completing in-progress trips
 * 
 * This operation moves a trip from the 'in_progress' folder to the 'completed' folder
 * and updates local state to reflect trip completion.
 */
public class CompleteTripOperation extends SyncOperation {
    private static final String TAG = "CompleteTripOperation";
    
    public CompleteTripOperation(String tripId, JSONObject data) {
        super("COMPLETE_TRIP", tripId, data);
    }
    
    public static CompleteTripOperation create(String tripId, JSONObject data) {
        return new CompleteTripOperation(tripId, data);
    }
    
    @Override
    public SyncResult executeOnline(Context context) {
        try {
            Log.i(TAG, "🏁 Online complete trip: " + getTripId());
            
            // Enhanced race protection: Check if already completed
            if (DropboxHelper.isTripBeingProcessed(context, getTripId())) {
                Log.i(TAG, "🛡️ SKIP RETRY: Trip " + getTripId() + " already completed - avoiding infinite retry loop");
                
                // Update local state to match completed status
                updateLocalStateForCompletedTrip();
                
                return SyncResult.success("Trip already completed by another process - local state synchronized");
            }
            
            // Move trip from in_progress to completed folder
            boolean completeSuccess = DropboxHelper.completeTripDirectly(context, getTripId());
            
            if (completeSuccess) {
                // Update local state
                updateLocalStateForCompletedTrip();
                
                // 📧 CRITICAL: Queue delivery data sync and email operations for each completed document
                queuePostCompletionOperations(context);
                
                Log.i(TAG, "Trip completed successfully: " + getTripId());
                return SyncResult.success("Trip completed and post-completion operations queued");
            } else {
                Log.w(TAG, "Trip completion failed: " + getTripId());
                return SyncResult.failure("Failed to complete trip on Dropbox");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in online completion operation for trip: " + getTripId(), e);
            return SyncResult.failure("Online completion failed: " + e.getMessage());
        }
    }
    
    @Override
    public SyncResult executeOffline(Context context) {
        try {
            Log.i(TAG, "Offline complete trip: " + getTripId());
            
            // Update local state optimistically for immediate UI feedback
            updateLocalStateForCompletedTrip();
            
            return SyncResult.success("Trip completed offline (will sync later)");
        } catch (Exception e) {
            return SyncResult.failure("Offline completion failed: " + e.getMessage());
        }
    }
    
    private void updateLocalStateForCompletedTrip() {
        try {
            // Add to completed trips list
            if (!AppConstant.completedTrips.contains(getTripId())) {
                AppConstant.completedTrips.add(getTripId());
                Log.d(TAG, "Added trip to completed list: " + getTripId());
            }
            
            // Remove from in-progress list if present
            if (AppConstant.inProgressTrips.contains(getTripId())) {
                AppConstant.inProgressTrips.remove(getTripId());
                Log.d(TAG, "Removed trip from in-progress list: " + getTripId());
            }
            
            // Clear started trip constant if it matches
            if (getTripId().equals(AppConstant.STARTED_TRIP)) {
                AppConstant.STARTED_TRIP = "";
                Log.d(TAG, "Cleared started trip constant for: " + getTripId());
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating local state for completed trip", e);
        }
    }
    
    /**
     * 📧 Queue post-completion operations for the completed trip
     * This mirrors the original SyncService behavior that handled:
     * 1. Uploading delivery metadata for each document
     * 2. Sending ePOD emails for each delivery
     */
    private void queuePostCompletionOperations(Context context) {
        try {
            Log.i(TAG, "📧 COMPLETE_TRIP: Queueing post-completion operations for trip: " + getTripId());
            
            DeliveryDb database = new DeliveryDb(context);
            database.open();
            
            try {
                // Get all completed document IDs for this trip
                List<String> completedDocumentIds = database.getCompletedDocumentList(getTripId());
                
                if (completedDocumentIds == null || completedDocumentIds.isEmpty()) {
                    Log.w(TAG, "No completed deliveries found for trip: " + getTripId());
                    return;
                }
                
                OfflineSyncQueue syncQueue = OfflineSyncQueue.getInstance(context);
                int queuedOperations = 0;
                
                for (String documentId : completedDocumentIds) {
                    if (documentId == null || documentId.trim().isEmpty()) {
                        Log.w(TAG, "Skipping delivery with empty document ID");
                        continue;
                    }
                    
                    // Get full delivery data for this document
                    Delivery delivery = database.getCompletedDocument(documentId, getTripId());
                    if (delivery == null) {
                        Log.w(TAG, "Could not retrieve delivery data for document: " + documentId);
                        continue;
                    }
                    
                    // 1. Queue delivery metadata sync operation
                    JSONObject metadataData = new JSONObject();
                    metadataData.put("documentId", documentId);
                    metadataData.put("tripId", getTripId());
                    metadataData.put("operation", "sync_metadata");
                    
                    SyncDeliveryDataOperation metadataOp = new SyncDeliveryDataOperation(
                        getTripId(), documentId, metadataData
                    );
                    
                    if (syncQueue.enqueueOperation(metadataOp)) {
                        queuedOperations++;
                        Log.i(TAG, "📦 Queued metadata sync for document: " + documentId);
                    }
                    
                    // 2. Queue email operation (critical for ePOD delivery)
                    JSONObject emailData = new JSONObject();
                    emailData.put("documentId", documentId);
                    emailData.put("tripId", getTripId());
                    emailData.put("operation", "send_email");
                    emailData.put("recipient", AppConstant.EMAIL != null ? AppConstant.EMAIL : "");
                    
                    SendEmailOperation emailOp = new SendEmailOperation(
                        getTripId(), documentId, emailData
                    );
                    
                    if (syncQueue.enqueueOperation(emailOp)) {
                        queuedOperations++;
                        Log.i(TAG, "📧 Queued email for document: " + documentId + " to: " + AppConstant.EMAIL);
                    }
                    
                    // Also create email entry in database for tracking (matches Preview.java:307)
                    database.createEmailEntry(documentId, getTripId());
                }
                
                Log.i(TAG, "✅ COMPLETE_TRIP: Queued " + queuedOperations + " post-completion operations for " + 
                      completedDocumentIds.size() + " deliveries in trip: " + getTripId());
                
            } finally {
                database.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error queueing post-completion operations for trip: " + getTripId(), e);
        }
    }
    
    @Override
    public int getPriority() {
        return 2; // High priority - completion is critical
    }
}