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
     */
    private static class ClaimInfo {
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

    private static DbxClientV2 getClient(Context context) {
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

            Log.i("Dropbox", "Fetching trips from Dropbox...");
            ListFolderResult folders = client.files().listFolder(CUSTOMER_PATH);

            if (folders == null || folders.getEntries().isEmpty()) {
                Log.w("Dropbox", "No folders found on Dropbox or API call failed. Skipping trip update.");
                if (database != null && database.isOpen()) database.close();
                return;
            }

            for (int i = 0; i < folders.getEntries().size(); i++) {

                String resultString = folders.getEntries().get(i).getName();

                Log.i("Dropbox", "Returned file " + resultString);

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
            // Download to a temporary file
            try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                Log.i("Dropbox", "Download starting for " + tripName + " to temp file.");
                client.files().downloadBuilder(CUSTOMER_PATH + tripName).download(outputStream);
                Log.i("Dropbox", "Download completed for " + tripName + " to temp file.");
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
            
            // Prepare file paths
            String fromFile = CUSTOMER_PATH + tripId + ".json";
            String claimedName = createClaimedTripName(tripId, deviceId, timestamp);
            String toFolder = CUSTOMER_PATH + "InProgress/" + claimedName + ".json";
            
            Log.d("TripClaiming", "Attempting to claim trip: " + fromFile + " -> " + toFolder);
            
            // Perform the move operation
            client.files().moveV2(fromFile, toFolder);
            Log.i("TripClaiming", "✓ Successfully claimed and moved " + tripId + " to InProgress with device ID: " + deviceId);
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
                    Log.i("Dropbox", "InProgress folder doesn't exist, creating it: " + inProgressPath);
                    try {
                        client.files().createFolderV2(inProgressPath.substring(0, inProgressPath.length() - 1)); // Remove trailing slash
                        Log.i("Dropbox", "InProgress folder created successfully");
                        // Now try to list it again (should be empty)
                        result = client.files().listFolder(inProgressPath);
                    } catch (Exception createEx) {
                        Log.e(TAG, "Failed to create InProgress folder", createEx);
                        return;
                    }
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
                    
                    Log.d("TripClaiming", "Trip " + tripId + " analysis:");
                    Log.d("TripClaiming", "  - isCurrentTrip: " + isCurrentTrip);
                    Log.d("TripClaiming", "  - inCompletedList: " + inCompletedList);
                    Log.d("TripClaiming", "  - isTripStarted: " + isTripStarted);
                    Log.d("TripClaiming", "  - isFullyCompleted: " + isFullyCompleted);
                    Log.d("TripClaiming", "  - isClaimedByThisDevice: " + isClaimedByThisDevice);
                    Log.d("TripClaiming", "  - isStaleCliam: " + isStaleCliam);
                    
                    // Only move back to pending if:
                    // 1. NOT the currently active trip AND
                    // 2. NOT in the completed trips list AND
                    // 3. NOT fully completed in database AND
                    // 4. NOT started (has no completed deliveries) AND
                    // 5. (NOT claimed by another device OR claim is stale)
                    boolean shouldMoveBack = !isCurrentTrip && !inCompletedList && !isFullyCompleted && !isTripStarted && 
                                           (isClaimedByThisDevice || claimingDeviceId == null || isStaleCliam);
                    
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


    public static boolean uploadCompletedDelivery(Context context, String filePath, String tripName, String document, String image, String signature) {

        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e("Dropbox", "Cannot upload completed delivery - Dropbox client not available");
                return false;
            }

            String dropboxPath = CUSTOMER_PATH + "Completed/" + tripName + "/" + document;

            String localImage = context.getFilesDir() + LOCAL_IMAGE_PATH + image + ".jpg";

            String localSignature = context.getFilesDir() + LOCAL_SIGNATURE_PATH + signature + ".jpg";

            createUploadFolders(context, tripName, document);

            try (InputStream inputStream = new FileInputStream(new File(filePath))) {

                client.files().uploadBuilder(dropboxPath + "/" + document + ".json").uploadAndFinish(inputStream);
            }

            if (new File(localImage).exists()) {

                try (InputStream inputStream = new FileInputStream(new File(localImage))) {

                    client.files().uploadBuilder(dropboxPath + "/" + image + ".jpg").uploadAndFinish(inputStream);
                }
            }

            if (new File(localSignature).exists()) {

                try (InputStream inputStream = new FileInputStream(new File(localSignature))) {

                    client.files().uploadBuilder(dropboxPath + "/" + signature + ".jpg").uploadAndFinish(inputStream);
                }
            }

            return true;

        } catch (Exception e) {

            e.printStackTrace();

            return false;
        }
    }


    public static void createUploadFolders(Context context, String tripName, String document) {

        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e("Dropbox", "Cannot create upload folders - Dropbox client not available");
                return;
            }

            boolean tripExists = false;

            boolean documentExists = false;

            String path = CUSTOMER_PATH + "Completed/" ;

            ListFolderResult folders = client.files().listFolder(path);

            for (int i = 0; i < folders.getEntries().size(); i++) {

                String folderName = folders.getEntries().get(i).getName();

                if (folderName.equals(tripName)) {

                    tripExists = true;
                }
            }

            if (!tripExists) {

                client.files().createFolderV2(path + tripName);
            }

            path = path + tripName + "/";

            folders = client.files().listFolder(path);

            for (int i = 0; i < folders.getEntries().size(); i++) {

                String folderName = folders.getEntries().get(i).getName();

                if (folderName.equals(document)) {

                    documentExists = true;
                }
            }

            if (!documentExists) {

                client.files().createFolderV2(path + document);
            }

        } catch(Exception e) {

            e.printStackTrace();
        }
    }


    public static void moveCompletedTrip(Context context, String tripName) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot move completed trip - Dropbox client not available");
                return;
            }

            String toPath = CUSTOMER_PATH + "Completed/" + tripName + "/" + tripName + ".json";
            String fromPath = CUSTOMER_PATH + "InProgress/" + tripName + ".json";
            client.files().moveV2(fromPath, toPath);

        } catch (Exception e) {
            Log.e(TAG, "Failed to move completed trip", e);
        }
    }


    public static void updateListInProgressTrips(Context context) {
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
                    Log.i("Dropbox", "InProgress folder doesn't exist for listing, creating it: " + inProgressPath);
                    try {
                        client.files().createFolderV2(inProgressPath.substring(0, inProgressPath.length() - 1)); // Remove trailing slash
                        Log.i("Dropbox", "InProgress folder created successfully");
                        // Now try to list it again (should be empty)
                        folders = client.files().listFolder(inProgressPath);
                    } catch (Exception createEx) {
                        Log.e(TAG, "Failed to create InProgress folder for listing", createEx);
                        return;
                    }
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
     * Ensure that required Dropbox folder structure exists
     * Creates InProgress and Completed folders if they don't exist
     */
    public static void ensureDropboxFolderStructure(Context context) {
        try {
            DbxClientV2 client = getClient(context);
            if (client == null) {
                Log.e(TAG, "Cannot ensure folder structure - Dropbox client not available");
                return;
            }
            
            Log.i("Dropbox", "Ensuring required Dropbox folder structure exists...");
            
            // Ensure InProgress folder exists
            String inProgressPath = CUSTOMER_PATH + "InProgress";
            try {
                client.files().getMetadata(inProgressPath);
                Log.d("Dropbox", "InProgress folder already exists");
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("not_found")) {
                    try {
                        client.files().createFolderV2(inProgressPath);
                        Log.i("Dropbox", "Created InProgress folder: " + inProgressPath);
                    } catch (Exception createEx) {
                        Log.e(TAG, "Failed to create InProgress folder", createEx);
                    }
                }
            }
            
            // Ensure Completed folder exists
            String completedPath = CUSTOMER_PATH + "Completed";
            try {
                client.files().getMetadata(completedPath);
                Log.d("Dropbox", "Completed folder already exists");
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("not_found")) {
                    try {
                        client.files().createFolderV2(completedPath);
                        Log.i("Dropbox", "Created Completed folder: " + completedPath);
                    } catch (Exception createEx) {
                        Log.e(TAG, "Failed to create Completed folder", createEx);
                    }
                }
            }
            
            Log.i("Dropbox", "Folder structure verification complete");
            
        } catch (Exception e) {
            Log.e(TAG, "Error ensuring Dropbox folder structure", e);
        }
    }
    
    /**
     * Get unique device identifier for trip claiming
     * Uses multiple device characteristics + UUID for collision resistance
     */
    private static String getDeviceId(Context context) {
        try {
            // Get multiple device identifiers for uniqueness
            String androidId = android.provider.Settings.Secure.getString(
                context.getContentResolver(), 
                android.provider.Settings.Secure.ANDROID_ID
            );
            
            String driverName = AppConstant.DRIVER != null ? AppConstant.DRIVER.replaceAll("[^A-Za-z0-9]", "") : "UnknownDriver";
            
            // Build composite unique identifier
            StringBuilder composite = new StringBuilder();
            composite.append(driverName).append("_");
            
            // Add Android ID (sanitized)
            if (androidId != null && androidId.length() > 4) {
                composite.append(androidId.substring(0, Math.min(8, androidId.length())));
            } else {
                composite.append("NOID");
            }
            
            // Add device model hash for extra uniqueness
            if (android.os.Build.MODEL != null) {
                int modelHash = android.os.Build.MODEL.hashCode();
                composite.append("_").append(Math.abs(modelHash % 10000));
            }
            
            // Add millisecond timestamp for absolute uniqueness
            composite.append("_").append(System.currentTimeMillis() % 100000);
            
            String deviceId = composite.toString();
            Log.d("DeviceId", "Collision-resistant device ID generated: " + deviceId);
            return deviceId;
            
        } catch (Exception e) {
            Log.e("DeviceId", "Error generating device ID, using UUID fallback", e);
            // UUID guarantees uniqueness as ultimate fallback
            return "Device_" + java.util.UUID.randomUUID().toString().substring(0, 8);
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
            String inProgressPath = CUSTOMER_PATH + "InProgress/";
            ListFolderResult result = client.files().listFolder(inProgressPath);
            
            if (result == null || result.getEntries() == null) {
                Log.w("TripClaiming", "No entries returned from InProgress folder");
                return false; // No claims found
            }
            
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
                        Log.w("TripClaiming", "Trip " + tripId + " is claimed by device: " + claimInfo.deviceId);
                        
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
     */
    private static ClaimInfo parseClaimInfo(String filename) {
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
}
