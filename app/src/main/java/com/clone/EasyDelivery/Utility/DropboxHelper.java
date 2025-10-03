package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.clone.EasyDelivery.BuildConfig;
import com.clone.EasyDelivery.Database.DeliveryDb;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DownloadErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.WriteMode;
import com.clone.EasyDelivery.Security.AuditLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.List;

public class DropboxHelper {

    private static final String TAG = "DropboxHelper";
    private static final String CLIENT_PATH = "dropbox/";
    private static final String CUSTOMER_PATH = "/Customers/" + AppConstant.COMPANY + "/";
    private static final String LOCAL_IMAGE_PATH = "/DeliveryApp/DeliveryImage/";
    private static final String LOCAL_SIGNATURE_PATH = "/DeliveryApp/DeliverySignature/";
    
    // Regex pattern for parsing claimed trip filenames safely
    private static final Pattern CLAIM_PATTERN = 
        Pattern.compile("^([A-Za-z0-9_-]+)_CLAIMED_BY_([A-Za-z0-9_-]+)_AT_([0-9]+)\\.json$");

    private static DbxClientV2 dropboxClient;
    private static SecurityManager securityManager;
    
    /**
     * Immutable class to hold parsed claim information
     * Made public for orphaned trip recovery functionality
     */
    public static class ClaimInfo {
        final String tripId;
        final String deviceId;
        final long timestamp;
        final boolean isValidClaim;
        
        ClaimInfo(String tripId, String deviceId, long timestamp, boolean isValidClaim) {
            this.tripId = tripId;
            this.deviceId = deviceId;
            this.timestamp = timestamp;
            this.isValidClaim = isValidClaim;
        }
        
        @Override
        public String toString() {
            return "ClaimInfo{trip=" + tripId + ", device=" + deviceId + ", time=" + timestamp + ", valid=" + isValidClaim + "}";
        }
    }

    /**
     * Initialize SecurityManager and migrate credentials from BuildConfig if needed
     */
    private static void initializeSecurity(Context context) {
        if (securityManager == null) {
            securityManager = SecurityManager.getInstance(context);

            // Migrate from BuildConfig to secure storage if credentials are missing
            if (securityManager.getDropboxRefreshToken() == null) {
                try {
                    // Try to get from BuildConfig for migration
                    String refreshToken = com.clone.EasyDelivery.BuildConfig.DROPBOX_REFRESH_TOKEN;
                    String appKey = com.clone.EasyDelivery.BuildConfig.DROPBOX_APP_KEY;
                    String appSecret = com.clone.EasyDelivery.BuildConfig.DROPBOX_APP_SECRET;

                    if (refreshToken != null && !refreshToken.isEmpty()) {
                        securityManager.initializeFromBuildConfig(refreshToken, appKey, appSecret);
                        Log.d(TAG, "Migrated Dropbox credentials from BuildConfig to secure storage");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to migrate credentials from BuildConfig", e);
                }
            }
        }
    }

    public static DbxClientV2 getClient(Context context) {
        initializeSecurity(context);

        if (dropboxClient == null) {
            // Get credentials from secure storage
            String refreshToken = securityManager.getDropboxRefreshToken();
            String appKey = securityManager.getDropboxAppKey();
            String appSecret = securityManager.getDropboxAppSecret();

            // Validate credentials before creating client
            if (refreshToken == null || refreshToken.isEmpty() ||
                    appKey == null || appKey.isEmpty() ||
                    appSecret == null || appSecret.isEmpty()) {

                Log.e(TAG, "Dropbox credentials are missing or invalid");
                Log.d(TAG, "Security Status:\n" + securityManager.getSecurityStatus());
                return null;
            }

            try {
                DbxRequestConfig config = DbxRequestConfig.newBuilder(CLIENT_PATH).build();
                DbxCredential credential = new DbxCredential("", 0L, refreshToken, appKey, appSecret);
                dropboxClient = new DbxClientV2(config, credential);

                Log.d(TAG, "Dropbox client initialized successfully with secure credentials");
            } catch (Exception e) {
                Log.e(TAG, "Failed to create Dropbox client", e);
                return null;
            }
        }

        return dropboxClient;
    }


    public static void downloadAllTrips(Context context) {

        try {

            ArrayList<String> dropboxTrips = new ArrayList<>();
            
            // Initialize database for trip validation
            DeliveryDb database = null;
            try {
                database = new DeliveryDb(context);
                database.open();
            } catch (Exception e) {
                Log.e("Dropbox", "Failed to open database for trip validation", e);
            }
            
            // Get Dropbox client with null check
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e("Dropbox", "Cannot download trips - Dropbox client not available (credentials missing or invalid)");
                if (database != null && database.isOpen()) database.close();
                return;
            }

            Log.i("Dropbox", "Fetching trips from Dropbox available folder...");
            
            // Look in the 'available' folder for trip files
            String availablePath = CUSTOMER_PATH + "available";
            ListFolderResult availableFiles = client.files().listFolder(availablePath);

            if (availableFiles == null || availableFiles.getEntries().isEmpty()) {
                Log.w("Dropbox", "No available trip files found on Dropbox. Skipping trip update.");
                if (database != null && database.isOpen()) database.close();
                return;
            }

            Log.i("Dropbox", "Found " + availableFiles.getEntries().size() + " entries in available folder");
            
            for (int i = 0; i < availableFiles.getEntries().size(); i++) {

                String resultString = availableFiles.getEntries().get(i).getName();

                Log.i("Dropbox", "Returned available trip file: " + resultString);

                if (resultString.contains(".json")) {

                    String tripId = resultString.substring(0, resultString.length() - 5);
                    dropboxTrips.add(tripId);
                    
                    // Enhanced validation: Check database in addition to in-memory lists
                    boolean shouldDownload = false;
                    boolean inTripList = AppConstant.tripList.contains(tripId);
                    boolean inCompletedList = AppConstant.completedTrips.contains(tripId);
                    boolean isFullyCompleted = false;
                    
                    // Check database to see if trip is fully completed and synced
                    if (database != null) {
                        try {
                            isFullyCompleted = database.isTripFullyCompleted(tripId);
                        } catch (Exception e) {
                            Log.w("Dropbox", "Failed to check trip completion status in database for " + tripId, e);
                        }
                    }
                    
                    // Only download if:
                    // 1. Not in trip list AND
                    // 2. Not in completed list AND 
                    // 3. Not fully completed in database
                    if (!inTripList && !inCompletedList && !isFullyCompleted) {
                        shouldDownload = true;
                    }
                    
                    Log.d("Dropbox", "Trip " + tripId + " validation - inTripList: " + inTripList + 
                          ", inCompletedList: " + inCompletedList + ", isFullyCompleted: " + isFullyCompleted + 
                          ", shouldDownload: " + shouldDownload);
                          
                    if (shouldDownload) {
                        Log.i("Dropbox", "New trip found on Dropbox, downloading: " + resultString);
                        downloadFile(context, resultString);
                    } else {
                        Log.d("Dropbox", "Skipping download of " + tripId + " - already processed or completed");
                    }
                }
            }
            
            // Close database
            if (database != null && database.isOpen()) {
                database.close();
            }

            Log.i("Dropbox", "Trips found on Dropbox: " + dropboxTrips.toString());
            Log.i("Dropbox", "Updating AppConstant.downloadedTrips. Previous state: " + AppConstant.downloadedTrips.toString());

            for (String trip : dropboxTrips) {

                if (!AppConstant.downloadedTrips.contains(trip)) {

                    AppConstant.downloadedTrips.add(trip);
                }
            }

            ArrayList<String> toRemove = new ArrayList<>();

            for (String trip : AppConstant.downloadedTrips) {

                if (!dropboxTrips.contains(trip)) {

                    toRemove.add(trip);
                }
            }

            if (!toRemove.isEmpty()) {
                Log.i("Dropbox", "Removing trips from AppConstant.downloadedTrips: " + toRemove.toString());
                AppConstant.downloadedTrips.removeAll(toRemove);
            }

            Log.i("Dropbox", "AppConstant.downloadedTrips updated. Current state: " + AppConstant.downloadedTrips.toString());

        } catch (DbxException e) {
            e.printStackTrace();
        }
    }


    public static void downloadFile(Context context, String tripName) {
        
        DbxClientV2 client = getClient(context);
        if (client == null) {
            Log.e("Dropbox", "Cannot download file " + tripName + " - Dropbox client not available");
            return;
        }

        File tripDir = new File(context.getFilesDir() + "/Trip/");
        if (!tripDir.exists()) {
            tripDir.mkdirs();
        }

        File finalFile = new File(tripDir, tripName);
        File tempFile = new File(tripDir, tripName + ".tmp");

        try {
            // Download to a temporary file from the available folder
            try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                Log.i("Dropbox", "Download starting for " + tripName + " from available folder to temp file.");
                String downloadPath = CUSTOMER_PATH + "available/" + tripName;
                client.files().downloadBuilder(downloadPath).download(outputStream);
                Log.i("Dropbox", "Download completed for " + tripName + " from available folder to temp file.");
            }

            // Atomically rename the temp file to the final file
            if (tempFile.renameTo(finalFile)) {
                Log.i("Dropbox", "Successfully renamed temp file to " + tripName);
            } else {
                Log.e("Dropbox", "Failed to rename temp file for " + tripName);
                // Attempt to delete the temp file if rename fails
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }

        } catch (DbxException | IOException e) {
            e.printStackTrace();
            // Clean up the temp file on error
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }


    public static void downloadReturnFile(Context context) {
        
        DbxClientV2 client = getClient(context);
        if (client == null) {
            Log.e("Dropbox", "Cannot download return file - Dropbox client not available");
            return;
        }

        File file = new File(context.getFilesDir() + "/Return/");

        if (!file.exists()) {

            file.mkdirs();
        }

        try (OutputStream outputStream = new FileOutputStream(new File(file.getPath()))) {

            Log.i("Dropbox", "Download starting...");

            client.files().downloadBuilder(CUSTOMER_PATH + "Returns/" + "returns.json").download(outputStream);

            Log.i("Dropbox", "Download completed.");

        } catch (FileNotFoundException e) {


        } catch (DownloadErrorException e) {

            e.printStackTrace();

        } catch (IOException | DbxException e) {

            e.printStackTrace();
        }
    }


    public static boolean uploadReturnsFile(Context context) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e("Dropbox", "Cannot upload returns file - Dropbox client not available");
                return false;
            }

            try (InputStream inputStream = new FileInputStream(new File(context.getFilesDir() + "/Return/", "returns.json"))) {

                client.files().uploadBuilder(CUSTOMER_PATH + "Returns/" + "returns.json").withMode(WriteMode.OVERWRITE).uploadAndFinish(inputStream);

                ToastLogger.message(context, "Uploaded return");
            }

            return true;

        } catch(Exception e) {

            e.printStackTrace();

            ToastLogger.exception(context, e);

            return false;
        }
    }


    public static void moveTripInProgress(Context context, String trip) {
        // 🎯 UNIFIED: Trip claiming now handled by UnifiedTripManager
        Log.i(TAG, "🎯 Trip claiming delegated to UnifiedTripManager - this method is deprecated");
        Log.w(TAG, "⚠️ Direct DropboxHelper.moveTripInProgress() calls should be replaced with UnifiedTripManager.claimTrip()");
    }
    
    // Legacy Enhanced Sync method removed - functionality moved to UnifiedTripManager
    
    /**
     * 📶 LEGACY: Original file-based trip claiming
     */
    private static void moveTripInProgressLegacy(Context context, String trip) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot move trip - Dropbox client not available");
                return;
            }
            
            String deviceId = getDeviceId(context);
            if (deviceId == null || deviceId.isEmpty()) {
                Log.e("TripClaiming", "Cannot claim trip - device ID generation failed");
                return;
            }
            
            String timestamp = String.valueOf(System.currentTimeMillis());

            if (!SyncConstant.STARTED_TRIP.isEmpty()) {
                String tripId = SyncConstant.STARTED_TRIP;
                
                if (!claimTripSafely(context, client, tripId, deviceId, timestamp)) {
                    Log.w("TripClaiming", "Failed to safely claim started trip: " + tripId);
                }

            } else if (trip != null && !trip.isEmpty()) {
                
                if (!claimTripSafely(context, client, trip, deviceId, timestamp)) {
                    Log.w("TripClaiming", "Failed to safely claim specified trip: " + trip);
                }
            } else {
                Log.d("TripClaiming", "No trip to claim - both STARTED_TRIP and trip parameter are empty");
            }

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in moveTripInProgress", e);
        }
    }
    
    /**
     * Safely claim a trip with comprehensive error handling and validation
     * Returns true if successfully claimed, false otherwise
     */
    private static boolean claimTripSafely(Context context, DbxClientV2 client, String tripId, String deviceId, String timestamp) {
        try {
            // Validate inputs
            if (tripId == null || tripId.isEmpty()) {
                Log.e("TripClaiming", "Cannot claim - trip ID is null or empty");
                return false;
            }
            
            if (deviceId == null || deviceId.isEmpty()) {
                Log.e("TripClaiming", "Cannot claim - device ID is null or empty");
                return false;
            }
            
            // Check if trip is already claimed by another device
            if (isTripClaimedByOtherDevice(context, client, tripId, deviceId)) {
                Log.w("TripClaiming", "🚨 Trip " + tripId + " is already claimed by another device - aborting claim");
                return false;
            }
            
            // Prepare file paths - use new folder structure
            String fromFile = CUSTOMER_PATH + "available/" + tripId + ".json";
            String claimedName = createClaimedTripName(tripId, deviceId, timestamp);
            String toFolder = CUSTOMER_PATH + "claimed/" + claimedName + ".json";
            
            Log.d("TripClaiming", "Attempting to claim trip: " + fromFile + " -> " + toFolder);
            
            // Perform the move operation
            client.files().moveV2(fromFile, toFolder);
            Log.i("TripClaiming", "✓ Successfully claimed and moved " + tripId + " from available to claimed with device ID: " + deviceId);
            return true;
            
        } catch (com.dropbox.core.v2.files.RelocationErrorException e) {
            // Handle specific Dropbox move errors
            Log.e("TripClaiming", "Dropbox move error for trip " + tripId + ": " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("not_found")) {
                Log.w("TripClaiming", "Source file not found - trip may have been moved by another device");
            } else if (e.getMessage() != null && e.getMessage().contains("conflict")) {
                Log.w("TripClaiming", "File conflict - trip may already be claimed");
            }
            return false;
            
        } catch (com.dropbox.core.NetworkIOException e) {
            Log.e("TripClaiming", "Network error claiming trip " + tripId + " - will retry on next sync", e);
            return false;
            
        } catch (Exception e) {
            Log.e("TripClaiming", "Unexpected error claiming trip " + tripId, e);
            return false;
        }
    }


    public static void moveIncompleteTrip(Context context, DeliveryDb database) {
        // 🎯 UNIFIED: Trip cleanup now handled by UnifiedTripManager
        Log.i(TAG, "🎯 Trip cleanup delegated to UnifiedTripManager - this method is deprecated");
        Log.w(TAG, "⚠️ Direct DropboxHelper.moveIncompleteTrip() calls should use UnifiedTripManager");
        
        // Fallback to legacy cleanup for backwards compatibility
        moveIncompleteTripLegacy(context, database);
    }
    
    // Legacy Enhanced Sync method removed - functionality moved to UnifiedTripManager
    
    /**
     * Determine if a trip should be cleaned up (moved back to available)
     */
    private static boolean shouldCleanupTrip(String tripId, String claimingDevice, String currentDevice, DeliveryDb database) {
        try {
            // Don't cleanup currently active trip
            if (SyncConstant.STARTED_TRIP.equals(tripId)) {
                return false;
            }
            
            // Don't cleanup completed trips
            if (AppConstant.completedTrips.contains(tripId)) {
                return false;
            }
            
            // Check if trip has been started (has completed deliveries)
            if (database != null && database.tripStarted(tripId)) {
                return false;
            }
            
            // Check if trip is fully completed in database
            if (database != null && database.isTripFullyCompleted(tripId)) {
                return false;
            }
            
            // Only cleanup trips claimed by this device or stale claims
            if (claimingDevice == null || currentDevice.equals(claimingDevice)) {
                return true;
            }
            
            // Could add timestamp-based stale detection here if needed
            return false;
            
        } catch (Exception e) {
            Log.w("ENHANCED_SYNCCleanup", "Error checking cleanup criteria for trip: " + tripId, e);
            return false; // Fail safe - don't cleanup on error
        }
    }
    
    /**
     * 📶 LEGACY: Original file-based trip cleanup
     */
    private static void moveIncompleteTripLegacy(Context context, DeliveryDb database) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot move incomplete trips - Dropbox client not available");
                return;
            }
            
            // Check if InProgress folder exists, create it if it doesn't
            String inProgressPath = CUSTOMER_PATH + "InProgress/";
            ListFolderResult result;
            
            try {
                result = client.files().listFolder(inProgressPath);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("not_found")) {
                    Log.e(TAG, "❌ InProgress folder doesn't exist - server-side setup required: " + inProgressPath);
                    Log.e(TAG, "The main skeleton folder structure should be created server-side");
                    return; // Don't create - require server-side setup
                } else {
                    Log.e(TAG, "Error accessing InProgress folder", e);
                    return;
                }
            }

            if (!result.getEntries().isEmpty()) {

                String currentDeviceId = getDeviceId(context);
                
                for (int i = 0; i < result.getEntries().size(); i++) {

                    String item = result.getEntries().get(i).getName();
                    
                    // Extract trip ID from potentially claimed filename
                    String tripId = extractTripId(item);
                    String claimingDeviceId = extractClaimingDevice(item);
                    long claimTimestamp = extractClaimTimestamp(item);
                    
                    Log.d("TripClaiming", "Evaluating InProgress trip: " + tripId + " (claimed by: " + claimingDeviceId + ")");

                    // Enhanced checks to prevent moving completed trips back to pending
                    boolean isCurrentTrip = SyncConstant.STARTED_TRIP.equals(tripId);
                    boolean inCompletedList = AppConstant.completedTrips.contains(tripId);
                    boolean isTripStarted = database != null && database.tripStarted(tripId);
                    boolean isFullyCompleted = false;
                    boolean isClaimedByThisDevice = currentDeviceId.equals(claimingDeviceId);
                    boolean isStaleCliam = (System.currentTimeMillis() - claimTimestamp) > (30 * 60 * 1000); // 30 minutes
                    
                    // Check if trip is fully completed in database
                    if (database != null) {
                        try {
                            isFullyCompleted = database.isTripFullyCompleted(tripId);
                        } catch (Exception e) {
                            Log.w("Dropbox", "Failed to check trip completion status for " + tripId, e);
                        }
                    }
                    
                    // 🛡️ CRITICAL PROTECTION: Check if trip is actually completed by checking Dropbox Completed folder
                    boolean isInCompletedFolder = false;
                    try {
                        String completedTripPath = CUSTOMER_PATH + "Completed/" + tripId + "/" + tripId + ".json";
                        client.files().getMetadata(completedTripPath);
                        isInCompletedFolder = true;
                        Log.i("TripClaiming", "🛡️ PROTECTION: Trip " + tripId + " already exists in Completed folder - will NOT move back");
                    } catch (Exception e) {
                        // Trip is not in Completed folder, which is normal for InProgress trips
                        isInCompletedFolder = false;
                    }
                    
                    // Only move back to pending if:
                    // 1. NOT the currently active trip AND
                    // 2. NOT in the completed trips list AND
                    // 3. NOT fully completed in database AND
                    // 4. NOT started (has no completed deliveries) AND
                    // 5. NOT already in Completed folder AND
                    // 6. (Claimed by this device OR no device OR claim is stale)
                    boolean shouldMoveBack = !isCurrentTrip && !inCompletedList && !isFullyCompleted && !isTripStarted && !isInCompletedFolder &&
                                           (isClaimedByThisDevice || claimingDeviceId == null || isStaleCliam);
                    
                    Log.d("TripClaiming", "Trip " + tripId + " analysis:");
                    Log.d("TripClaiming", "  - isCurrentTrip: " + isCurrentTrip);
                    Log.d("TripClaiming", "  - inCompletedList: " + inCompletedList);
                    Log.d("TripClaiming", "  - isTripStarted: " + isTripStarted);
                    Log.d("TripClaiming", "  - isFullyCompleted: " + isFullyCompleted);
                    Log.d("TripClaiming", "  - isInCompletedFolder: " + isInCompletedFolder);
                    Log.d("TripClaiming", "  - isClaimedByThisDevice: " + isClaimedByThisDevice);
                    Log.d("TripClaiming", "  - isStaleCliam: " + isStaleCliam);
                    Log.d("TripClaiming", "  - shouldMoveBack: " + shouldMoveBack);
                    
                    // 🔧 SINGLE DEVICE FIX: For single device testing, be more aggressive about moving back
                    // But still respect completion folder protection
                    if (isClaimedByThisDevice && !isCurrentTrip && !inCompletedList && !isFullyCompleted && !isInCompletedFolder) {
                        shouldMoveBack = true;
                        Log.i("TripClaiming", "SINGLE DEVICE MODE: Moving back trip " + tripId + " claimed by this device");
                    }
                    
                    if (shouldMoveBack) {

                        String fromFile = CUSTOMER_PATH + "InProgress/" + item;
                        String toFile = CUSTOMER_PATH + tripId + ".json"; // Remove claiming info when moving back

                        client.files().moveV2(fromFile, toFile);

                        Log.i("TripClaiming", "Trip " + tripId + " removed from InProgress (moved back to pending) - no activity detected");
                        
                    } else {
                        if (!isClaimedByThisDevice && claimingDeviceId != null && !isStaleCliam) {
                            Log.d("TripClaiming", "Trip " + tripId + " kept in InProgress - claimed by " + claimingDeviceId);
                        } else {
                            Log.d("TripClaiming", "Trip " + tripId + " kept in InProgress - active or completed");
                        }
                    }
                }
            }

        } catch (Exception e) {

            Log.e(TAG, "Error in moveIncompleteTrip", e);
            e.printStackTrace();
        }
    }


    /**
     * ❌ REMOVED: Use uploadDeliveryMetadata() for secure uploads
     * This deprecated method has been removed - it uploaded sensitive customer data to cloud
     */
    @Deprecated
    public static boolean uploadCompletedDelivery(Context context, String filePath, String tripName, String document, String image, String signature) {
        Log.e(TAG, "❌ DEPRECATED METHOD REMOVED: uploadCompletedDelivery() has been removed for security reasons");
        Log.e(TAG, "Use uploadDeliveryMetadata() instead - it only uploads metadata, not sensitive files");
        return uploadDeliveryMetadata(context, filePath, tripName, document);
    }

    /**
     * 🔒 SECURE: Upload only delivery completion metadata (no sensitive files)
     * This method follows security best practices by avoiding cloud storage of:
     * - Customer signature images
     * - Delivery photos  
     * - Personal identifiable information in files
     */
    public static boolean uploadDeliveryMetadata(Context context, String filePath, String tripName, String document) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot upload delivery metadata - Dropbox client not available");
                return false;
            }

            // Create secure metadata-only folder structure
            String dropboxPath = CUSTOMER_PATH + "Completed/" + tripName + "/Metadata";
            
            Log.i(TAG, "🔒 Uploading METADATA ONLY (no sensitive files) for delivery: " + document);
            
            createMetadataUploadFolder(context, tripName);

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
    public static void createMetadataUploadFolder(Context context, String tripName) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot create metadata upload folder - Dropbox client not available");
                return;
            }

            boolean tripExists = false;
            boolean metadataFolderExists = false;

            String completedPath = CUSTOMER_PATH + "Completed/";
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
     * 🏁 Create completed trip folder structure
     */
    private static void createCompletedTripFolder(Context context, String tripName) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot create completed trip folder - Dropbox client not available");
                return;
            }
            
            String completedPath = CUSTOMER_PATH + "completed/";
            String tripPath = completedPath + tripName + "/";
            
            try {
                // Check if trip folder already exists
                client.files().getMetadata(tripPath.substring(0, tripPath.length() - 1)); // Remove trailing slash
                Log.d(TAG, "Completed trip folder already exists: " + tripName);
            } catch (Exception e) {
                // Folder doesn't exist, create it
                try {
                    client.files().createFolderV2(completedPath + tripName);
                    Log.i(TAG, "Created completed trip folder: " + tripName);
                } catch (Exception createEx) {
                    Log.e(TAG, "Failed to create completed trip folder: " + tripName, createEx);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in createCompletedTripFolder", e);
        }
    }
    
    /**
     * ❌ REMOVED: Use createMetadataUploadFolder() for secure uploads
     * This deprecated method has been removed - it created insecure folder structures
     */
    @Deprecated
    public static void createUploadFolders(Context context, String tripName, String document) {
        Log.e(TAG, "❌ DEPRECATED METHOD REMOVED: createUploadFolders() has been removed for security reasons");
        Log.e(TAG, "Use createMetadataUploadFolder() instead - creates secure metadata-only folders");
        createMetadataUploadFolder(context, tripName);
    }


    public static void moveCompletedTrip(Context context, String tripName) {
        // 🎯 UNIFIED: Trip completion now handled by UnifiedTripManager
        Log.i(TAG, "🎯 Trip completion delegated to UnifiedTripManager - this method is deprecated");
        Log.w(TAG, "⚠️ Direct DropboxHelper.moveCompletedTrip() calls should be replaced with UnifiedTripManager.completeTrip()");
        
        // Fallback to legacy completion for backwards compatibility
        moveCompletedTripLegacy(context, tripName);
    }
    
    // Legacy Enhanced Sync method removed - functionality moved to UnifiedTripManager
    
    /**
     * 📶 LEGACY: Original file-based trip completion
     */
    private static void moveCompletedTripLegacy(Context context, String tripName) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot move completed trip - Dropbox client not available");
                return;
            }
            
            Log.i("TripCompletion", "🏁 MOVE COMPLETED: Starting move to Completed folder for trip: " + tripName);
            
            // 🔍 FIXED: Find the actual claimed filename in in_progress folder (new structure)
            String inProgressPath = CUSTOMER_PATH + "in_progress/";
            String actualFilename = null;
            
            try {
                ListFolderResult result = client.files().listFolder(inProgressPath);
                
                if (result != null && !result.getEntries().isEmpty()) {
                    for (int i = 0; i < result.getEntries().size(); i++) {
                        String filename = result.getEntries().get(i).getName();
                        ClaimInfo claimInfo = parseClaimInfo(filename);
                        
                        // Check if this file belongs to our completed trip
                        if (tripName.equals(claimInfo.tripId)) {
                            actualFilename = filename;
                            Log.i("TripCompletion", "🔍 Found claimed file for trip " + tripName + ": " + filename);
                            break;
                        }
                    }
                }
                
            } catch (Exception searchEx) {
                Log.w("TripCompletion", "Error searching for claimed file for trip " + tripName, searchEx);
            }
            
            if (actualFilename == null) {
                Log.w("TripCompletion", "⚠️ CRITICAL: Could not find claimed file for completed trip " + tripName + " in in_progress folder");
                Log.w("TripCompletion", "Trying fallback locations...");
                
                // 🛠️ FALLBACK: Try to find it in claimed folder or available folder
                String[] fallbackFolders = {"claimed/", "available/"};
                boolean foundAndMoved = false;
                
                for (String folder : fallbackFolders) {
                    try {
                        String fallbackFolderPath = CUSTOMER_PATH + folder;
                        ListFolderResult fallbackResult = client.files().listFolder(fallbackFolderPath);
                        
                        if (fallbackResult != null && !fallbackResult.getEntries().isEmpty()) {
                            for (int i = 0; i < fallbackResult.getEntries().size(); i++) {
                                String filename = fallbackResult.getEntries().get(i).getName();
                                ClaimInfo claimInfo = parseClaimInfo(filename);
                                
                                if (tripName.equals(claimInfo.tripId) || filename.equals(tripName + ".json")) {
                                    String fallbackFromPath = fallbackFolderPath + filename;
                                    String toPath = CUSTOMER_PATH + "completed/" + tripName + "/" + tripName + ".json";
                                    
                                    // Create the completed folder first
                                    createCompletedTripFolder(context, tripName);
                                    
                                    client.files().moveV2(fallbackFromPath, toPath);
                                    Log.i("TripCompletion", "🛠️ FALLBACK SUCCESS: Moved " + tripName + " from " + folder + " to Completed folder");
                                    foundAndMoved = true;
                                    break;
                                }
                            }
                        }
                        
                        if (foundAndMoved) break;
                        
                    } catch (Exception fallbackEx) {
                        Log.d("TripCompletion", "No trip found in " + folder + " folder");
                    }
                }
                
                if (!foundAndMoved) {
                    Log.e("TripCompletion", "🚫 FALLBACK FAILED: Cannot find trip " + tripName + " in any folder");
                }
                return;
            }
            
            // Move the found claimed file to Completed folder
            String fromPath = CUSTOMER_PATH + "in_progress/" + actualFilename;
            String toPath = CUSTOMER_PATH + "completed/" + tripName + "/" + tripName + ".json";  // Store as clean filename
            
            // Create the completed folder structure first
            createCompletedTripFolder(context, tripName);
            
            client.files().moveV2(fromPath, toPath);
            Log.i("TripCompletion", "✅ SUCCESS: Moved completed trip " + tripName + " from in_progress to completed folder");
            Log.i("TripCompletion", "FROM: " + fromPath);
            Log.i("TripCompletion", "TO: " + toPath);

        } catch (Exception e) {
            Log.e(TAG, "🚫 CRITICAL ERROR: Failed to move completed trip " + tripName + " to Completed folder", e);
            e.printStackTrace();
        }
    }


    public static void updateListInProgressTrips(Context context) {
        // 🎯 UNIFIED: In-progress trips now handled by UnifiedTripManager
        Log.i(TAG, "🎯 In-progress trip discovery delegated to UnifiedTripManager - this method is deprecated");
        Log.w(TAG, "⚠️ Direct DropboxHelper.updateListInProgressTrips() calls should use UnifiedTripManager");
        
        // Fallback to legacy for backwards compatibility
        updateInProgressTripsLegacy(context);
    }
    
    // Legacy Enhanced Sync method removed - functionality moved to UnifiedTripManager
    
    /**
     * 📶 LEGACY: Original file-based in-progress trip listing
     */
    private static void updateInProgressTripsLegacy(Context context) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot update in progress trips - Dropbox client not available");
                return;
            }

            ArrayList<String> dropboxTrips = new ArrayList<>();
            String inProgressPath = CUSTOMER_PATH + "InProgress/";
            ListFolderResult folders;
            
            try {
                folders = client.files().listFolder(inProgressPath);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("not_found")) {
                    Log.e(TAG, "❌ InProgress folder doesn't exist - server-side setup required: " + inProgressPath);
                    Log.e(TAG, "The main skeleton folder structure should be created server-side");
                    return; // Don't create - require server-side setup
                } else {
                    Log.e(TAG, "Failed to update in progress trips list", e);
                    return;
                }
            }

            for (int i = 0; i < folders.getEntries().size(); i++) {
                String resultString = folders.getEntries().get(i).getName();
                Log.i("Dropbox", "Returned InProgress file " + resultString);

                if (resultString.contains(".json")) {
                    dropboxTrips.add(resultString.substring(0, resultString.length() - 5));
                }
            }

            for (String trip : dropboxTrips) {
                if (!AppConstant.inProgressTrips.contains(trip)) {
                    AppConstant.inProgressTrips.add(trip);
                }
            }

            ArrayList<String> toRemove = new ArrayList<>();
            for (String trip : AppConstant.inProgressTrips) {
                if (!dropboxTrips.contains(trip)) {
                    toRemove.add(trip);
                }
            }

            AppConstant.inProgressTrips.removeAll(toRemove);

        } catch (Exception e) {
            Log.e(TAG, "Failed to update in progress trips list", e);
        }
    }
    
    /**
     * ❌ REMOVED: Initial folder structure creation disabled
     * The main skeleton folder structure (available, claimed, in_progress, completed, etc.)
     * should be created server-side, not by the app. Only individual trip folders
     * within the skeleton are created by the app.
     */
    @Deprecated
    public static void ensureDropboxFolderStructure(Context context) {
        Log.w(TAG, "❌ DISABLED: Initial folder structure creation has been disabled");
        Log.w(TAG, "The main skeleton folder structure should be created server-side");
        Log.w(TAG, "Only individual trip folders within the skeleton are created by the app");
        // No folder creation - server-side setup required
    }
    
    /**
     * Get consistent device identifier for trip claiming
     * 🔧 FIXED: Now generates consistent ID across sessions for single-device operation
     * Made public for orphaned trip recovery functionality
     */
    public static String getDeviceId(Context context) {
        try {
            // Get stable device identifiers (consistent across app sessions)
            String androidId = android.provider.Settings.Secure.getString(
                context.getContentResolver(), 
                android.provider.Settings.Secure.ANDROID_ID
            );
            
            String driverName = AppConstant.DRIVER != null ? AppConstant.DRIVER.replaceAll("[^A-Za-z0-9]", "") : "Driver";
            
            // Build STABLE composite identifier (no timestamp for consistency)
            StringBuilder composite = new StringBuilder();
            composite.append(driverName).append("_");
            
            // Add Android ID (sanitized) - this is stable across sessions
            if (androidId != null && androidId.length() > 4) {
                composite.append(androidId.substring(0, Math.min(8, androidId.length())));
            } else {
                composite.append("DEV");
            }
            
            // Add device model hash for extra uniqueness (but stable)
            if (android.os.Build.MODEL != null) {
                int modelHash = Math.abs(android.os.Build.MODEL.hashCode() % 10000);
                composite.append("_").append(modelHash);
            }
            
            // 🔧 REMOVED timestamp to make device ID consistent across sessions
            
            String deviceId = composite.toString();
            Log.d("DeviceId", "Stable device ID generated: " + deviceId + " (consistent across sessions)");
            return deviceId;
            
        } catch (Exception e) {
            Log.e("DeviceId", "Error generating device ID, using fallback", e);
            // Fallback to a stable identifier
            return "Device_FALLBACK";
        }
    }
    
    /**
     * Create claimed trip filename with device and timestamp info
     */
    private static String createClaimedTripName(String tripId, String deviceId, String timestamp) {
        return tripId + "_CLAIMED_BY_" + deviceId + "_AT_" + timestamp;
    }
    
    /**
     * Check if trip is already claimed by another device
     * Uses fail-safe approach - defaults to preventing claims on errors
     */
    private static boolean isTripClaimedByOtherDevice(Context context, DbxClientV2 client, String tripId, String currentDeviceId) {
        if (tripId == null || tripId.isEmpty() || currentDeviceId == null || currentDeviceId.isEmpty()) {
            Log.e("TripClaiming", "Invalid parameters for claim check - tripId: " + tripId + ", deviceId: " + currentDeviceId);
            return true; // Fail safe - prevent claim if parameters are invalid
        }
        
        try {
            // Check both claimed and in_progress folders for existing claims
            String[] foldersToCheck = {"claimed/", "in_progress/"};
            
            for (String folder : foldersToCheck) {
                String folderPath = CUSTOMER_PATH + folder;
                
                try {
                    ListFolderResult result = client.files().listFolder(folderPath);
                    
                    if (result != null && result.getEntries() != null) {
                        for (int i = 0; i < result.getEntries().size(); i++) {
                            String filename = result.getEntries().get(i).getName();
                            
                            if (filename == null) {
                                Log.w("TripClaiming", "Null filename encountered, skipping");
                                continue;
                            }
                            
                            // Use safe parsing instead of brittle string operations
                            ClaimInfo claimInfo = parseClaimInfo(filename);
                            
                            // Check if this file is a claimed version of our trip
                            if (claimInfo.isValidClaim && tripId.equals(claimInfo.tripId)) {
                                if (!currentDeviceId.equals(claimInfo.deviceId)) {
                                    Log.w("TripClaiming", "Trip " + tripId + " is claimed by device: " + claimInfo.deviceId + " in " + folder);
                                    
                                    // Check if claim is stale (older than 30 minutes)
                                    long currentTime = System.currentTimeMillis();
                                    long ageMinutes = (currentTime - claimInfo.timestamp) / (1000 * 60);
                                    
                                    if (ageMinutes > 30) {
                                        Log.w("TripClaiming", "Claim is stale (" + ageMinutes + " minutes old) - allowing override");
                                        return false; // Stale claim - allow override
                                    } else {
                                        Log.i("TripClaiming", "Active claim found (" + ageMinutes + " minutes old) - preventing duplicate claim");
                                        return true; // Active claim - prevent duplicate
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception folderEx) {
                    Log.d("TripClaiming", "Error checking folder " + folder + ": " + folderEx.getMessage());
                }
            }
            
            Log.d("TripClaiming", "No conflicting claims found for trip: " + tripId);
            return false; // Trip not claimed by other devices
            
        } catch (Exception e) {
            Log.e("TripClaiming", "Error checking trip claims for " + tripId + " - failing safe (preventing claim)", e);
            return true; // FAIL SAFE: Assume claimed if we can't check (prevents race conditions)
        }
    }
    
    /**
     * Parse claim information from filename using safe regex validation
     * Returns ClaimInfo object with all parsed data
     * Made public for orphaned trip recovery functionality
     */
    public static ClaimInfo parseClaimInfo(String filename) {
        try {
            if (filename == null || filename.isEmpty()) {
                Log.w("TripClaiming", "Filename is null or empty");
                return new ClaimInfo("UNKNOWN", null, 0, false);
            }
            
            // Try to match claimed filename pattern
            Matcher matcher = CLAIM_PATTERN.matcher(filename);
            if (matcher.matches()) {
                // Valid claimed filename
                String tripId = matcher.group(1);
                String deviceId = matcher.group(2);
                long timestamp = Long.parseLong(matcher.group(3));
                
                Log.d("TripClaiming", "Parsed claimed filename: " + tripId + " claimed by " + deviceId + " at " + timestamp);
                return new ClaimInfo(tripId, deviceId, timestamp, true);
            }
            
            // Not a claimed filename - treat as regular trip filename
            if (filename.endsWith(".json")) {
                String tripId = filename.substring(0, filename.length() - 5);
                Log.d("TripClaiming", "Parsed regular filename: " + tripId);
                return new ClaimInfo(tripId, null, 0, false);
            }
            
            // Unknown format
            Log.w("TripClaiming", "Unknown filename format: " + filename);
            return new ClaimInfo(filename.replaceAll("\\.json$", ""), null, 0, false);
            
        } catch (NumberFormatException e) {
            Log.e("TripClaiming", "Invalid timestamp in filename: " + filename, e);
            return new ClaimInfo("INVALID", null, 0, false);
        } catch (Exception e) {
            Log.e("TripClaiming", "Error parsing filename: " + filename, e);
            return new ClaimInfo("ERROR", null, 0, false);
        }
    }
    
    /**
     * Extract trip ID from filename (safe wrapper for backward compatibility)
     */
    private static String extractTripId(String filename) {
        return parseClaimInfo(filename).tripId;
    }
    
    /**
     * Extract claiming device ID from filename (safe wrapper for backward compatibility)
     */
    private static String extractClaimingDevice(String filename) {
        return parseClaimInfo(filename).deviceId;
    }
    
    /**
     * Extract claim timestamp from filename (safe wrapper for backward compatibility)
     */
    private static long extractClaimTimestamp(String filename) {
        return parseClaimInfo(filename).timestamp;
    }
    
    /**
     * 🔧 SINGLE DEVICE FIX: Force unclaim all trips claimed by this device
     * This is useful when switching between trips or backing out of trip selection
     */
    public static void forceUnclaimCurrentDeviceTrips(Context context) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot force unclaim - Dropbox client not available");
                return;
            }
            
            String currentDeviceId = getDeviceId(context);
            String inProgressPath = CUSTOMER_PATH + "InProgress/";
            
            Log.i("TripClaiming", "🔧 FORCE UNCLAIM: Checking for trips claimed by device: " + currentDeviceId);
            
            try {
                ListFolderResult result = client.files().listFolder(inProgressPath);
                
                if (result == null || result.getEntries().isEmpty()) {
                    Log.i("TripClaiming", "No trips in InProgress folder to unclaim");
                    return;
                }
                
                int unclaimedCount = 0;
                for (int i = 0; i < result.getEntries().size(); i++) {
                    String filename = result.getEntries().get(i).getName();
                    ClaimInfo claimInfo = parseClaimInfo(filename);
                    
                    // If this trip is claimed by current device, move it back
                    if (claimInfo.isValidClaim && currentDeviceId.equals(claimInfo.deviceId)) {
                        String fromFile = CUSTOMER_PATH + "InProgress/" + filename;
                        String toFile = CUSTOMER_PATH + claimInfo.tripId + ".json";
                        
                        try {
                            client.files().moveV2(fromFile, toFile);
                            unclaimedCount++;
                            Log.i("TripClaiming", "✓ Force unclaimed trip: " + claimInfo.tripId);
                        } catch (Exception moveEx) {
                            Log.w("TripClaiming", "Failed to force unclaim trip: " + claimInfo.tripId, moveEx);
                        }
                    }
                }
                
                Log.i("TripClaiming", "🔧 FORCE UNCLAIM completed: " + unclaimedCount + " trips moved back to pending");
                
            } catch (Exception listEx) {
                Log.w("TripClaiming", "Error listing InProgress folder for force unclaim", listEx);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in forceUnclaimCurrentDeviceTrips", e);
        }
    }
    
    /**
     * 🔧 SINGLE DEVICE FIX: Unclaim a specific trip by trip ID
     * This is useful when explicitly switching from one trip to another
     */
    public static boolean unclaimSpecificTrip(Context context, String tripId) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot unclaim trip - Dropbox client not available");
                return false;
            }
            
            String currentDeviceId = getDeviceId(context);
            String inProgressPath = CUSTOMER_PATH + "InProgress/";
            
            Log.i("TripClaiming", "🔧 UNCLAIM SPECIFIC: Looking for trip " + tripId + " claimed by " + currentDeviceId);
            
            try {
                ListFolderResult result = client.files().listFolder(inProgressPath);
                
                if (result == null || result.getEntries().isEmpty()) {
                    Log.i("TripClaiming", "No trips in InProgress folder to unclaim");
                    return false;
                }
                
                for (int i = 0; i < result.getEntries().size(); i++) {
                    String filename = result.getEntries().get(i).getName();
                    ClaimInfo claimInfo = parseClaimInfo(filename);
                    
                    // If this is the specific trip we want to unclaim and it's claimed by current device
                    if (claimInfo.isValidClaim && 
                        tripId.equals(claimInfo.tripId) && 
                        currentDeviceId.equals(claimInfo.deviceId)) {
                        
                        String fromFile = CUSTOMER_PATH + "InProgress/" + filename;
                        String toFile = CUSTOMER_PATH + claimInfo.tripId + ".json";
                        
                        try {
                            client.files().moveV2(fromFile, toFile);
                            Log.i("TripClaiming", "✓ Successfully unclaimed specific trip: " + tripId);
                            return true;
                        } catch (Exception moveEx) {
                            Log.w("TripClaiming", "Failed to unclaim specific trip: " + tripId, moveEx);
                            return false;
                        }
                    }
                }
                
                Log.i("TripClaiming", "Trip " + tripId + " not found or not claimed by this device");
                return false;
                
            } catch (Exception listEx) {
                Log.w("TripClaiming", "Error listing InProgress folder for specific unclaim", listEx);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in unclaimSpecificTrip", e);
            return false;
        }
    }
    
    // ================== UNIFIED SYNC INTEGRATION METHODS ==================
    
    /**
     * 🎯 UNIFIED: Direct trip claiming method for UnifiedTripManager
     */
    public static boolean claimTripDirectly(Context context, String tripId, String deviceId) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot claim trip directly - Dropbox client not available");
                return false;
            }
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            return claimTripSafely(context, client, tripId, deviceId, timestamp);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in direct trip claiming", e);
            return false;
        }
    }
    
    /**
     * 🚀 UNIFIED: Direct trip starting method for UnifiedTripManager
     */
    public static boolean startTripDirectly(Context context, String tripId) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot start trip directly - Dropbox client not available");
                return false;
            }
            
            // Find the claimed file in claimed folder and move to in_progress
            String claimedPath = CUSTOMER_PATH + "claimed/";
            String inProgressPath = CUSTOMER_PATH + "in_progress/";
            
            ListFolderResult claimedFiles = client.files().listFolder(claimedPath);
            
            for (int i = 0; i < claimedFiles.getEntries().size(); i++) {
                String filename = claimedFiles.getEntries().get(i).getName();
                ClaimInfo claimInfo = parseClaimInfo(filename);
                
                if (claimInfo.tripId.equals(tripId)) {
                    String fromPath = claimedPath + filename;
                    String toPath = inProgressPath + filename;
                    
                    client.files().moveV2(fromPath, toPath);
                    Log.i(TAG, "✅ Trip started: " + tripId + " moved from claimed to in_progress");
                    return true;
                }
            }
            
            Log.w(TAG, "Trip not found in claimed folder: " + tripId);
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error in direct trip starting", e);
            return false;
        }
    }
    
    /**
     * 🏁 UNIFIED: Direct trip completion method for UnifiedTripManager
     */
    public static boolean completeTripDirectly(Context context, String tripId) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot complete trip directly - Dropbox client not available");
                return false;
            }
            
            Log.i(TAG, "🏁 UNIFIED: Starting direct trip completion for: " + tripId);
            
            // Find the trip file in in_progress folder
            String inProgressPath = CUSTOMER_PATH + "in_progress/";
            String actualFilename = null;
            
            try {
                ListFolderResult result = client.files().listFolder(inProgressPath);
                
                if (result != null && !result.getEntries().isEmpty()) {
                    for (int i = 0; i < result.getEntries().size(); i++) {
                        String filename = result.getEntries().get(i).getName();
                        ClaimInfo claimInfo = parseClaimInfo(filename);
                        
                        if (tripId.equals(claimInfo.tripId)) {
                            actualFilename = filename;
                            Log.i(TAG, "🔍 UNIFIED: Found trip file: " + filename);
                            break;
                        }
                    }
                }
            } catch (Exception searchEx) {
                Log.w(TAG, "Error searching in_progress folder", searchEx);
            }
            
            if (actualFilename == null) {
                Log.w(TAG, "⚠️ UNIFIED: Trip not found in in_progress, trying fallback locations");
                return completeTripFallback(context, client, tripId);
            }
            
            // Create completed folder structure
            createCompletedTripFolder(context, tripId);
            
            // Move from in_progress to completed
            String fromPath = CUSTOMER_PATH + "in_progress/" + actualFilename;
            String toPath = CUSTOMER_PATH + "completed/" + tripId + "/" + tripId + ".json";
            
            client.files().moveV2(fromPath, toPath);
            
            Log.i(TAG, "✅ UNIFIED: Trip completed successfully - " + tripId);
            Log.i(TAG, "FROM: " + fromPath);
            Log.i(TAG, "TO: " + toPath);
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error in unified trip completion", e);
            return false;
        }
    }
    
    /**
     * 🛠️ UNIFIED: Fallback completion method
     */
    private static boolean completeTripFallback(Context context, DbxClientV2 client, String tripId) {
        Log.i(TAG, "🛠️ UNIFIED: Attempting fallback trip completion for: " + tripId);
        
        // Try to find the trip in claimed or available folders
        String[] fallbackFolders = {"claimed/", "available/"};
        
        for (String folder : fallbackFolders) {
            try {
                String folderPath = CUSTOMER_PATH + folder;
                ListFolderResult result = client.files().listFolder(folderPath);
                
                if (result != null && !result.getEntries().isEmpty()) {
                    for (int i = 0; i < result.getEntries().size(); i++) {
                        String filename = result.getEntries().get(i).getName();
                        ClaimInfo claimInfo = parseClaimInfo(filename);
                        
                        if (tripId.equals(claimInfo.tripId) || filename.equals(tripId + ".json")) {
                            // Create completed folder structure
                            createCompletedTripFolder(context, tripId);
                            
                            // Move to completed folder
                            String fromPath = folderPath + filename;
                            String toPath = CUSTOMER_PATH + "completed/" + tripId + "/" + tripId + ".json";
                            
                            client.files().moveV2(fromPath, toPath);
                            
                            Log.i(TAG, "✅ UNIFIED: Fallback completion successful - moved from " + folder);
                            Log.i(TAG, "FROM: " + fromPath);
                            Log.i(TAG, "TO: " + toPath);
                            
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "No trip found in " + folder + " folder: " + e.getMessage());
            }
        }
        
        Log.e(TAG, "🚫 UNIFIED: All fallback completion attempts failed for: " + tripId);
        return false;
    }
    
    /**
     * ↩️ UNIFIED: Direct trip release method for UnifiedTripManager
     */
    public static boolean releaseTripDirectly(Context context, String tripId) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot release trip directly - Dropbox client not available");
                return false;
            }
            
            // Try to find and move the trip back to available from any folder
            String[] folders = {"claimed/", "in_progress/"};
            
            for (String folder : folders) {
                String folderPath = CUSTOMER_PATH + folder;
                
                try {
                    ListFolderResult files = client.files().listFolder(folderPath);
                    
                    for (int i = 0; i < files.getEntries().size(); i++) {
                        String filename = files.getEntries().get(i).getName();
                        ClaimInfo claimInfo = parseClaimInfo(filename);
                        
                        if (claimInfo.tripId.equals(tripId)) {
                            String fromPath = folderPath + filename;
                            String toPath = CUSTOMER_PATH + "available/" + tripId + ".json";
                            
                            client.files().moveV2(fromPath, toPath);
                            Log.i(TAG, "✅ Trip released: " + tripId + " moved back to available");
                            return true;
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "No files in folder: " + folder);
                }
            }
            
            Log.w(TAG, "Trip not found in any claimable folder: " + tripId);
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error in direct trip release", e);
            return false;
        }
    }
    
    /**
     * 🚀 FAST UNCLAIM: Immediate unclaim operation for responsive UI
     * This method performs unclaim operations with connectivity check for speed
     */
    public static void fastUnclaimOnSwitch(Context context, String previousTripId) {
        // Run in background thread for non-blocking UI
        Thread fastUnclaimThread = new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // Check connectivity first
                boolean isConnected = ConnectionHelper.isInternetConnected();
                
                if (!isConnected) {
                    Log.i("FastUnclaim", "🚀 FAST UNCLAIM: No connection, will be handled by sync service later");
                    return;
                }
                
                Log.i("FastUnclaim", "🚀 FAST UNCLAIM: Starting immediate unclaim for " + previousTripId);
                
                if (previousTripId != null && !previousTripId.isEmpty()) {
                    // Try to unclaim the specific trip
                    boolean success = unclaimSpecificTrip(context, previousTripId);
                    
                    if (success) {
                        long duration = System.currentTimeMillis() - startTime;
                        Log.i("FastUnclaim", "✅ FAST UNCLAIM: Successfully unclaimed " + previousTripId + " in " + duration + "ms");
                    }
                } else {
                    // Fall back to unclaiming all trips from this device
                    forceUnclaimCurrentDeviceTrips(context);
                    long duration = System.currentTimeMillis() - startTime;
                    Log.i("FastUnclaim", "✅ FAST UNCLAIM: Force unclaimed all device trips in " + duration + "ms");
                }
                
            } catch (Exception e) {
                Log.w("FastUnclaim", "⚠️ FAST UNCLAIM failed, sync service will handle it", e);
            }
        });
        
        fastUnclaimThread.start();
    }
    
    /**
     * 🛡️ RACE CONDITION PROTECTION: Check if trip is being processed by another device
     * This helps prevent multiple devices from moving the same completed trip
     */
    public static boolean isTripBeingProcessed(Context context, String tripId) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.w("TripCompletion", "Cannot check trip processing status - Dropbox client not available");
                return false;
            }
            
            // Check if trip already exists in Completed folder
            String completedTripPath = CUSTOMER_PATH + "Completed/" + tripId + "/" + tripId + ".json";
            try {
                client.files().getMetadata(completedTripPath);
                Log.i("TripCompletion", "🛡️ RACE PROTECTION: Trip " + tripId + " already completed by another device");
                return true; // Already processed
            } catch (Exception e) {
                // Trip not in Completed folder yet
                return false;
            }
            
        } catch (Exception e) {
            Log.w("TripCompletion", "Error checking trip processing status for " + tripId, e);
            return false; // Assume not being processed
        }
    }
}
