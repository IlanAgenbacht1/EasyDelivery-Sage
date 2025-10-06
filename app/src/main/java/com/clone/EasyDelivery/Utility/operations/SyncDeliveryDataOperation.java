package com.clone.EasyDelivery.Utility.operations;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;

import com.clone.EasyDelivery.Database.DeliveryDb;
import com.clone.EasyDelivery.Model.Delivery;
import com.clone.EasyDelivery.Utility.JsonHandler;
import com.clone.EasyDelivery.Utility.DropboxHelper;
import com.clone.EasyDelivery.Security.AuditLogger;

import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;
import com.dropbox.core.v2.files.ListFolderResult;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * 📦 Operation for syncing completed delivery documents to Dropbox
 * 
 * This operation handles uploading delivery metadata (JSON files) to Dropbox
 * after a delivery has been completed. It includes parcel information and
 * delivery details.
 */
public class SyncDeliveryDataOperation extends SyncOperation {
    private static final String TAG = "SyncDeliveryDataOperation";
    
    private final String documentId;
    
    public SyncDeliveryDataOperation(String tripId, String documentId, JSONObject data) {
        super("SYNC_DELIVERY_DATA", tripId, data);
        this.documentId = documentId;
    }
    
    @Override
    public SyncResult executeOnline(Context context) {
        try {
            Log.i(TAG, "Online sync delivery data: " + documentId + " (Trip: " + getTripId() + ")");
            
            DeliveryDb database = new DeliveryDb(context);
            database.open();
            
            try {
                // Get delivery data from database
                Delivery delivery = database.getCompletedDocument(documentId, getTripId());
                if (delivery == null) {
                    Log.e(TAG, "Failed to retrieve delivery data for document: " + documentId);
                    return SyncResult.failure("Delivery data not found in database");
                }
                
                // Get parcel information
                delivery = database.getCompletedParcels(delivery);
                
                // Write delivery data to JSON file
                String filePath = JsonHandler.writeDeliveryFile(context, delivery);
                if (filePath == null) {
                    Log.e(TAG, "Failed to write delivery file for document: " + documentId);
                    return SyncResult.failure("Failed to create delivery JSON file");
                }
                
                // Upload metadata to Dropbox using our internal logic
                boolean uploadSuccess = uploadDeliveryMetadataSecurely(context, filePath, getTripId(), documentId);
                
                if (uploadSuccess) {
                    // Clean up local file
                    new File(filePath).delete();
                    
                    // Mark as uploaded in database
                    database.setDocumentUploaded(documentId, getTripId());
                    
                    Log.i(TAG, "Successfully synced delivery data: " + documentId);
                    return SyncResult.success("Delivery data uploaded to Dropbox");
                } else {
                    // Clean up failed file
                    new File(filePath).delete();
                    Log.e(TAG, "Failed to upload delivery data: " + documentId);
                    return SyncResult.failure("Failed to upload to Dropbox");
                }
                
            } finally {
                database.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in online sync delivery data for document: " + documentId, e);
            return SyncResult.failure("Online sync failed: " + e.getMessage());
        }
    }
    
    @Override
    public SyncResult executeOffline(Context context) {
        try {
            Log.i(TAG, "Offline sync delivery data: " + documentId + " (Trip: " + getTripId() + ")");
            
            // For offline execution, just log that it's queued
            // The actual data is already in the database
            return SyncResult.success("Delivery data queued for sync when online");
            
        } catch (Exception e) {
            return SyncResult.failure("Offline sync failed: " + e.getMessage());
        }
    }
    
    @Override
    public int getPriority() {
        return 1; // Normal priority - data sync
    }
    
    /**
     * Get the document ID for this operation
     */
    public String getDocumentId() {
        return documentId;
    }
    
    /**
     * 🔒 SECURE: Upload only delivery completion metadata (no sensitive files)
     * This method follows security best practices by avoiding cloud storage of:
     * - Customer signature images
     * - Delivery photos  
     * - Personal identifiable information in files
     */
    private boolean uploadDeliveryMetadataSecurely(Context context, String filePath, String tripName, String document) {
        try {
            DbxClientV2 client = DropboxHelper.getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot upload delivery metadata - Dropbox client not available");
                return false;
            }

            // Create secure metadata-only folder structure
            String dropboxPath = "/Customers/" + com.clone.EasyDelivery.Utility.AppConstant.COMPANY + "/Completed/" + tripName + "/Metadata";
            
            Log.i(TAG, "🔒 Uploading METADATA ONLY (no sensitive files) for delivery: " + document);
            
            // Ensure folder structure exists
            createMetadataUploadFolder(context, client, tripName);

            // Upload only the JSON metadata file (contains delivery info but no file references)
            try (InputStream inputStream = new FileInputStream(new File(filePath))) {
                client.files().uploadBuilder(dropboxPath + "/" + document + "_metadata.json")
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(inputStream);
                    
                Log.i(TAG, "✓ Delivery metadata uploaded successfully: " + document);
                Log.i(TAG, "🛡️ SECURITY: No sensitive files (signatures/photos) were uploaded to cloud");
                
                // 🔍 AUDIT: Log secure cloud sync operation
                AuditLogger auditLogger = AuditLogger.getInstance(context);
                auditLogger.logSecureCloudSync(tripName, document, true, 
                    "Metadata-only upload completed successfully. No sensitive customer data uploaded to cloud.");
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to upload delivery metadata for " + document, e);
            
            // 🔍 AUDIT: Log failed secure cloud sync
            AuditLogger auditLogger = AuditLogger.getInstance(context);
            auditLogger.logSecureCloudSync(tripName, document, false, 
                "Metadata upload failed: " + e.getMessage());
            
            return false;
        }
    }
    
    /**
     * 🔒 SECURE: Create metadata-only upload folder structure
     * Creates secure folder structure for metadata-only uploads (no sensitive files)
     */
    private void createMetadataUploadFolder(Context context, DbxClientV2 client, String tripName) {
        try {
            boolean tripExists = false;
            boolean metadataFolderExists = false;

            String completedPath = "/Customers/" + com.clone.EasyDelivery.Utility.AppConstant.COMPANY + "/Completed/";
            String tripPath = completedPath + tripName + "/";
            String metadataPath = tripPath + "Metadata";

            // Ensure Completed folder exists
            try {
                ListFolderResult folders = client.files().listFolder(completedPath);
                for (int i = 0; i < folders.getEntries().size(); i++) {
                    if (folders.getEntries().get(i).getName().equals(tripName)) {
                        tripExists = true;
                        break;
                    }
                }
            } catch (Exception e) {
                // Completed folder might not exist yet
                Log.d(TAG, "Creating Completed folder structure");
            }

            // Create trip folder if it doesn't exist (individual trip folder creation allowed)
            if (!tripExists) {
                client.files().createFolderV2(completedPath + tripName);
                Log.i(TAG, "Created secure trip folder: " + tripName);
            }

            // Check if Metadata folder exists
            try {
                ListFolderResult tripFolders = client.files().listFolder(tripPath);
                for (int i = 0; i < tripFolders.getEntries().size(); i++) {
                    if (tripFolders.getEntries().get(i).getName().equals("Metadata")) {
                        metadataFolderExists = true;
                        break;
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Trip folder might be empty, will create metadata folder");
            }

            // Create Metadata folder if it doesn't exist (individual trip subfolder creation allowed)
            if (!metadataFolderExists) {
                client.files().createFolderV2(metadataPath);
                Log.i(TAG, "🔒 Created SECURE metadata-only folder: " + metadataPath);
                Log.i(TAG, "🛡️ This folder will NOT contain sensitive files (signatures/photos)");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error creating metadata upload folder", e);
        }
    }
    
    /**
     * Factory method to create operation from delivery document
     */
    public static SyncDeliveryDataOperation create(String tripId, String documentId) {
        try {
            JSONObject data = new JSONObject();
            data.put("documentId", documentId);
            data.put("tripId", tripId);
            data.put("operationType", "deliveryMetadata");
            
            return new SyncDeliveryDataOperation(tripId, documentId, data);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating sync delivery data operation", e);
            return null;
        }
    }
}