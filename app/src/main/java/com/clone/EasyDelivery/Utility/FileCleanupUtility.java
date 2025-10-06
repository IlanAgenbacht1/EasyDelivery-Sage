package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.util.Log;

import com.clone.EasyDelivery.Model.Delivery;
import com.clone.EasyDelivery.Security.AuditLogger;

import java.io.File;

/**
 * 🧹 FileCleanupUtility - Secure file cleanup and data retention utility
 * 
 * Handles secure cleanup of customer sensitive files (signatures, photos)
 * and enforcement of data retention policies.
 * 
 * Moved from SyncService for better separation of concerns.
 */
public class FileCleanupUtility {
    
    private static final String TAG = "FileCleanupUtility";
    
    /**
     * 🔒 SECURE: Cleanup sensitive files after successful email delivery
     * This method safely removes customer signature and photo files from local storage
     * after successful email delivery to minimize data retention risks.
     */
    public static void cleanupAfterSuccessfulEmail(Context context, Delivery delivery) {
        try {
            Log.i("SecureCleanup", "=== Starting secure cleanup for delivery: " + delivery.getDocument() + " ===");
            
            // Clean up signature files
            if (delivery.getSignPath() != null && !delivery.getSignPath().trim().isEmpty()) {
                String signaturePath = delivery.getSignPath();
                Log.d("SecureCleanup", "Cleaning up signature file: " + signaturePath);
                
                // Try multiple possible signature locations
                String[] signatureDirs = {
                    context.getFilesDir() + "/DeliveryApp/Signature/",
                    context.getFilesDir() + "/Signature/",
                    context.getFilesDir() + "/"
                };
                
                boolean signatureDeleted = false;
                for (String dir : signatureDirs) {
                    File signFile = new File(dir + signaturePath);
                    if (signFile.exists()) {
                        boolean deleted = signFile.delete();
                        Log.i("SecureCleanup", "Signature file deleted: " + deleted + " (" + signFile.getAbsolutePath() + ")");
                        if (deleted) signatureDeleted = true;
                    }
                }
                
                if (!signatureDeleted) {
                    Log.w("SecureCleanup", "Signature file not found for cleanup: " + signaturePath);
                }
            }
            
            // Clean up photo files
            if (delivery.getImagePath() != null && !delivery.getImagePath().trim().isEmpty()) {
                String imagePath = delivery.getImagePath();
                Log.d("SecureCleanup", "Cleaning up photo file: " + imagePath);
                
                // Try multiple possible photo locations and extensions
                String[] photoDirs = {
                    context.getFilesDir() + "/DeliveryApp/DeliveryImage/",
                    context.getFilesDir() + "/DeliveryImage/",
                    context.getFilesDir() + "/"
                };
                
                String[] extensions = {".jpg", ".jpeg", ".png", ""};
                
                boolean photoDeleted = false;
                for (String dir : photoDirs) {
                    for (String ext : extensions) {
                        File photoFile = new File(dir + imagePath + ext);
                        if (photoFile.exists()) {
                            boolean deleted = photoFile.delete();
                            Log.i("SecureCleanup", "Photo file deleted: " + deleted + " (" + photoFile.getAbsolutePath() + ")");
                            if (deleted) photoDeleted = true;
                        }
                    }
                }
                
                if (!photoDeleted) {
                    Log.w("SecureCleanup", "Photo file not found for cleanup: " + imagePath);
                }
            }
            
            Log.i("SecureCleanup", "✓ Secure cleanup completed for delivery: " + delivery.getDocument());
            Log.i("SecureCleanup", "🛡️ SECURITY: Customer sensitive data removed after successful email delivery");
            
            // 🔍 AUDIT: Log secure cleanup operation
            AuditLogger auditLogger = AuditLogger.getInstance(context);
            int totalFilesDeleted = 0;
            if (delivery.getSignPath() != null) totalFilesDeleted++;
            if (delivery.getImagePath() != null) totalFilesDeleted++;
            auditLogger.logSecureCleanup(delivery.getDocument(), delivery.getTripId(), totalFilesDeleted, true);
            
        } catch (Exception e) {
            Log.e("SecureCleanup", "Error during secure cleanup for delivery: " + delivery.getDocument(), e);
            
            // 🔍 AUDIT: Log cleanup failure
            AuditLogger auditLogger = AuditLogger.getInstance(context);
            auditLogger.logSecureCleanup(delivery.getDocument(), delivery.getTripId(), 0, false);
        }
    }

    /**
     * 🗑️ DATA RETENTION: Cleanup old delivery files based on retention policy
     * This method can be called periodically to enforce data retention policies
     */
    public static void enforceDataRetentionPolicy(Context context) {
        try {
            Log.i("DataRetention", "=== Starting data retention policy enforcement ===");
            
            // Define retention period (e.g., 30 days)
            long retentionPeriodMs = 30 * 24 * 60 * 60 * 1000L; // 30 days in milliseconds
            long cutoffTime = System.currentTimeMillis() - retentionPeriodMs;
            
            // Clean up old signature files
            int totalDeleted = 0;
            totalDeleted += cleanupOldFiles(context.getFilesDir() + "/DeliveryApp/Signature/", cutoffTime);
            totalDeleted += cleanupOldFiles(context.getFilesDir() + "/Signature/", cutoffTime);
            
            // Clean up old photo files  
            totalDeleted += cleanupOldFiles(context.getFilesDir() + "/DeliveryApp/DeliveryImage/", cutoffTime);
            totalDeleted += cleanupOldFiles(context.getFilesDir() + "/DeliveryImage/", cutoffTime);
            
            Log.i("DataRetention", "✓ Data retention policy enforcement completed");
            
            // 🔍 AUDIT: Log data retention enforcement
            AuditLogger auditLogger = AuditLogger.getInstance(context);
            auditLogger.logDataRetentionEnforcement(totalDeleted, 30, true);
            
        } catch (Exception e) {
            Log.e("DataRetention", "Error enforcing data retention policy", e);
            
            // 🔍 AUDIT: Log retention policy failure
            AuditLogger auditLogger = AuditLogger.getInstance(context);
            auditLogger.logDataRetentionEnforcement(0, 30, false);
        }
    }
    
    /**
     * Helper method to clean up old files in a directory
     * @return number of files deleted
     */
    private static int cleanupOldFiles(String directoryPath, long cutoffTime) {
        try {
            File directory = new File(directoryPath);
            if (!directory.exists() || !directory.isDirectory()) {
                return 0;
            }
            
            File[] files = directory.listFiles();
            if (files == null) {
                return 0;
            }
            
            int deletedCount = 0;
            for (File file : files) {
                if (file.isFile() && file.lastModified() < cutoffTime) {
                    boolean deleted = file.delete();
                    if (deleted) {
                        deletedCount++;
                        Log.d("DataRetention", "Deleted old file: " + file.getName());
                    }
                }
            }
            
            Log.i("DataRetention", "Cleaned up " + deletedCount + " old files from: " + directoryPath);
            return deletedCount;
            
        } catch (Exception e) {
            Log.e("DataRetention", "Error cleaning up old files in: " + directoryPath, e);
            return 0;
        }
    }
}