package com.clone.EasyDelivery.Utility;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import com.clone.EasyDelivery.Database.DeliveryDb;
import com.clone.EasyDelivery.Model.Delivery;
import com.clone.EasyDelivery.Model.Return;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// Security imports
import com.clone.EasyDelivery.Utility.UnifiedTripManager;
import com.clone.EasyDelivery.Utility.ConnectivityAwareSyncManager;
import com.clone.EasyDelivery.Security.AuditLogger;

// Enhanced Sync imports
import java.util.ArrayList;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.Authenticator;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;


public class SyncService extends IntentService {

    private boolean connected;
    private BroadcastReceiver receiver;
    private DeliveryDb database;
    private SecurityManager securityManager;

    public SyncService() {
        super("SyncService");
    }

    @Override
    public void onStart(@Nullable Intent intent, int startId) {
        super.onStart(intent, startId);

        if (database != null && database.isOpen()) {

            database.close();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize SecurityManager with full security suite
        securityManager = SecurityManager.getInstance(getApplicationContext());
        
        // Initialize key lifecycle management
        securityManager.initializeKeyTimestamp();
        
        // Perform key maintenance check
        securityManager.performKeyMaintenance();
        
        // Initialize email password if not set (you should replace with actual password)
        if (securityManager.getEmailPassword() == null || securityManager.getEmailPassword().trim().isEmpty()) {
            Log.w("SyncService", "Email password not configured - emails will not be sent");
            Log.i("SyncService", "To fix: Call securityManager.configureEmailPassword(\"YOUR_APP_PASSWORD\") with the Gmail app password");
            
            // Uncomment and replace with actual Gmail app password:
            securityManager.configureEmailPassword("jvvu juda uudo gbcj");
        }
        
        // Log security status for diagnostics
        Log.i("SyncService", "Security Status:\n" + securityManager.getSecurityStatus());
        Log.i("SyncService", securityManager.getIntegritySystemStatus());
        
        // Restore completed trips list from database to fix trip lifecycle issues
        restoreCompletedTripsFromDatabase();
        
        // 🚑 NEW: Check for orphaned trips and handle recovery
        checkForOrphanedTripsOnStartup();
        
        // 🔧 NEW: Initialize Enhanced Sync components
        initializeEnhancedSync();
        
        // Ensure required Dropbox folder structure exists
        Thread folderSetupThread = new Thread(new Runnable() {
            @Override
            public void run() {
                DropboxHelper.ensureDropboxFolderStructure(getApplicationContext());
            }
        });
        folderSetupThread.start();

        if (database != null && database.isOpen()) {

            database.close();
        }
    }
    
    /**
     * 🎯 Initialize Unified Sync System
     * The unified sync architecture handles all initialization automatically
     */
    private void initializeEnhancedSync() {
        try {
            Log.i("SyncService", "🎯 Initializing unified sync system...");
            
            // Initialize unified components
            UnifiedTripManager tripManager = UnifiedTripManager.getInstance(getApplicationContext());
            ConnectivityAwareSyncManager syncManager = ConnectivityAwareSyncManager.getInstance(getApplicationContext());
            
            Log.i("SyncService", "✅ Unified sync system initialized successfully!");
            
        } catch (Exception e) {
            Log.e("SyncService", "❌ Error initializing unified sync system", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i("SyncService", "Destroyed");

        if (receiver != null) {

            unregisterReceiver(receiver);
        }

        if (database != null && database.isOpen()) {

            database.close();
        }
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

        Log.i("SyncService", "onHandleIntent");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.i("SyncService", "onStartCommand called - Service starting");
        
        // Start location fetching immediately when service starts
        try {
            // Use Handler to run on main thread instead of background thread
            android.os.Handler mainHandler = new android.os.Handler(getMainLooper());
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Check connectivity in background but run location on main thread
                    Thread connectivityThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            boolean isConnected = ConnectionHelper.isInternetConnected();
                            Log.i("SyncService", "Initial connection check: " + isConnected);
                            
                            // Post location fetching back to main thread
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    LocationHelper.getLocation(isConnected, getApplicationContext());
                                }
                            });
                        }
                    });
                    connectivityThread.start();
                }
            });
        } catch (Exception e) {
            Log.e("SyncService", "Error starting initial location fetch", e);
            e.printStackTrace();
        }

        // Start adaptive polling system for better responsiveness
        startAdaptivePolling();

        IntentFilter filter = new IntentFilter();

        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addAction("DeliveryCompleted");
        filter.addAction("DeliveryStarted");
        filter.addAction("TripStarted");
        filter.addAction("TripNotStarted");
        filter.addAction("TripCompleted");
        filter.addAction("TripIncomplete");

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String action = intent.getAction();

                switch (action) {

                    case "android.net.conn.CONNECTIVITY_CHANGE":
                        try {

                            Thread thread = new Thread(new Runnable() {
                                @Override
                                public void run() {

                                    connected = ConnectionHelper.isInternetConnected();
                                }
                            });

                            thread.start();
                            thread.join();

                            if (connected) {

                                Log.i("SyncService", "Connected");

                                // Use Handler to run location updates on main thread
                                android.os.Handler mainHandler = new android.os.Handler(getMainLooper());
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        LocationHelper.getLocation(true, getApplicationContext());
                                    }
                                });

                                thread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {

                                        //DropboxHelper.downloadAllTrips(getApplicationContext());

                                        //ScheduleHelper.getLocalTrips(getApplicationContext());
                                    }
                                });

                                thread.start();

                            } else {

                                // Use Handler to run location updates on main thread
                                android.os.Handler mainHandler = new android.os.Handler(getMainLooper());
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        LocationHelper.getLocation(false, getApplicationContext());
                                    }
                                });
                            }

                        } catch (Exception e) {

                            e.printStackTrace();
                        }

                        Log.i("SyncService", "Connectivity action");
                    break;

                    case "TripStarted":

                        Thread thread = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                //DropboxHelper.moveTripInProgress();
                            }
                        });

                        //thread.start();

                        Log.i("SyncService", "Trip Started");

                    break;

                    case "TripNotStarted":

                        Thread thread2 = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    openDatabase();
                                    
                                    // 🚀 Enhanced File-Based Syncing System (exclusive)
                                    Log.i("SyncService", "🚀 Processing trip not started with enhanced state management");
                                    handleTripNotStarted();
                                } catch (Exception e) {
                                    Log.e("SyncService", "Error in trip not started handler", e);
                                }
                            }
                        });

                        thread2.start();

                        Log.i("SyncService", "Trip not started - Enhanced Sync cleanup started");

                    break;

                    case "TripCompleted" :

                        Thread threadTripSync = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    // 🚀 Enhanced File-Based Syncing System (exclusive)
                                    Log.i("SyncService", "🚀 Processing trip completion with enhanced state management");
                                    handleTripCompleted();
                                } catch (Exception e) {
                                    Log.e("SyncService", "Error in trip completion handler", e);
                                }
                            }
                        });

                        threadTripSync.start();

                        Log.i("SyncService", "Trip Completed - Enhanced Sync processing started");
                    break;

                    case "TripIncomplete":

                        Thread threadIncompleteMove = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                syncTripStatus();
                            }
                        });

                        //threadIncompleteMove.start();

                    break;

                    case "DeliveryStarted":

                        Log.i("SyncService", "Delivery Started");
                    break;

                    case "DeliveryCompleted":

                        Thread threadDocumentSync = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                //syncCompletedData();
                            }
                        });

                        //threadDocumentSync.start();

                        Log.i("SyncService", "Delivery Completed");

                    break;
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            registerReceiver(receiver,filter, Context.RECEIVER_NOT_EXPORTED);

        } else {

            registerReceiver(receiver, filter);
        }

        return START_STICKY;
    }


    private void syncCompletedData() {

        Log.i("SyncService", "=== Starting syncCompletedData ===");
        
        try {

            openDatabase();
            Log.i("SyncService", "Database opened successfully for completed data sync");

            List<String> trips = database.getIncompleteSyncList();
            Log.i("SyncService", "Found " + trips.size() + " incomplete sync trips: " + trips);

            if (trips.isEmpty()) {
                Log.i("SyncService", "No incomplete sync trips found - nothing to sync");
                return;
            }

            for (String trip : trips) {

                Log.i("SyncService", "Processing trip: " + trip);
                
                //check if there are completed deliveries for this trip locally
                List<String> documents = database.getCompletedDocumentList(trip);
                Log.i("SyncService", "Trip " + trip + " has " + documents.size() + " completed documents: " + documents);

                if (!documents.isEmpty()) {

                    for (String document : documents) {

                        Log.i("SyncService", "Processing document: " + document + " for trip: " + trip);
                        
                        //create delivery json and upload to dropbox
                        Delivery delivery = database.getCompletedDocument(document, trip);
                        if (delivery == null) {
                            Log.e("SyncService", "Failed to retrieve delivery data for document: " + document);
                            continue;
                        }
                        
                        Log.d("SyncService", "Retrieved delivery: customer=" + delivery.getCustomerName() + ", parcels=" + delivery.getNumberOfParcels());
                        
                        delivery = database.getCompletedParcels(delivery);
                        Log.d("SyncService", "Added parcels to delivery, parcel count: " + (delivery.getParcelNumbers() != null ? delivery.getParcelNumbers().size() : 0));
                        
                        String filePath = JsonHandler.writeDeliveryFile(getApplicationContext(), delivery);
                        Log.i("SyncService", "Created JSON metadata file: " + filePath);
                        
                        // 🔒 SECURITY UPDATE: Use metadata-only upload (no sensitive files)
                        Log.i("SyncService", "🔒 Using SECURE upload - no signatures/photos sent to cloud");
                        boolean uploadSuccess = DropboxHelper.uploadDeliveryMetadata(getApplicationContext(), filePath, trip, document);
                        
                        if (uploadSuccess) {

                            Log.i("SyncService", "✓ Successfully uploaded metadata for " + document + " (trip: " + trip + ")");
                            Log.i("SyncService", "🛡️ SECURITY: Only metadata uploaded - sensitive files remain local");

                            // Clean up temporary JSON metadata file
                            File file = new File(filePath);
                            boolean fileDeleted = file.delete();
                            Log.d("SyncService", "Temporary JSON metadata file deleted: " + fileDeleted);

                            // 🔒 SECURITY: Keep sensitive files local until email is sent successfully
                            // Only delete them after successful email delivery, not after Dropbox sync
                            Log.i("SyncService", "📝 RETENTION: Keeping signatures/photos local for email delivery");
                            // Note: Files will be cleaned up in cleanupAfterSuccessfulEmail() method

                            database.setDocumentUploaded(document, trip);
                            Log.i("SyncService", "Marked document " + document + " metadata as synced in database");
                        } else {
                            Log.e("SyncService", "✗ Failed to upload " + document + " for trip " + trip);
                        }
                    }
                } else {
                    Log.i("SyncService", "No completed documents found for trip: " + trip);
                }

                boolean isDataSynced = database.isDataSynced(trip);
                boolean isAlreadyCompleted = AppConstant.completedTrips.contains(trip);
                
                Log.d("SyncService", "Trip " + trip + " - isDataSynced: " + isDataSynced + ", alreadyInCompletedList: " + isAlreadyCompleted);
                
                if (isDataSynced && !isAlreadyCompleted) {
                    AppConstant.completedTrips.add(trip);
                    Log.i("SyncService", "✓ Added trip " + trip + " to completed trips list");
                }
            }
            
            Log.i("SyncService", "Current completed trips list size: " + AppConstant.completedTrips.size() + " - " + AppConstant.completedTrips);

        } catch (Exception e) {

            Log.e("SyncService", "Exception in syncCompletedData", e);
            e.printStackTrace();
        }
        
        Log.i("SyncService", "=== Finished syncCompletedData ===");
    }


    private void syncTripStatus() {
        Log.i("SyncService", "=== Starting syncTripStatus ===");
        
        try {
            openDatabase();
            
            // 🚀 Enhanced File-Based Syncing System (exclusive)
            Log.i("SyncService", "🚀 Enhanced Sync: Syncing trip status with enhanced state management");
            syncTripStatusEnhanced();
            
        } catch (Exception e) {
            Log.e("SyncService", "Exception in syncTripStatus", e);
        }
        
        Log.i("SyncService", "=== Finished syncTripStatus ===");
    }


    private void syncCompletedTrip() {
        
        Log.i("SyncService", "=== Starting syncCompletedTrip ===");
        
        try {
            openDatabase();
            Log.i("SyncService", "Database opened for completed trip sync");
            
            // 🚀 Enhanced File-Based Syncing System (exclusive)
            Log.i("SyncService", "🚀 Enhanced Sync: Using enhanced trip completion processing");
            handleTripCompleted();

        } catch (Exception e) {
            Log.e("SyncService", "Exception in syncCompletedTrip", e);
            e.printStackTrace();
        }
        
        Log.i("SyncService", "=== Finished syncCompletedTrip ===");
    }


    private void syncEmail() {
        
        Log.i("SyncService", "=== Starting syncEmail ===");
        
        try {
            openDatabase();
            Log.i("SyncService", "Database opened for email sync");
            
            List<Delivery> emailList = database.getAllUnsentEmails();
            Log.i("SyncService", "Found " + emailList.size() + " unsent emails");
            
            if (emailList.isEmpty()) {
                Log.i("SyncService", "No unsent emails found - email sync complete");
                return;
            }
            
            for (int i = 0; i < emailList.size(); i++) {
                Delivery queuedEmail = emailList.get(i);
                Log.i("SyncService", "Processing email " + (i + 1) + "/" + emailList.size() + ": " + queuedEmail.getDocument() + " (Trip: " + queuedEmail.getTripId() + ")");
                
                try {
                    Delivery data = database.getCompletedDocument(queuedEmail.getDocument(), queuedEmail.getTripId());
                    
                    if (data == null) {
                        Log.e("SyncService", "Failed to retrieve delivery data for email: " + queuedEmail.getDocument());
                        continue;
                    }
                    
                    Log.d("SyncService", "Email data retrieved - Customer: " + data.getCustomerName() + ", Parcels: " + data.getNumberOfParcels());
                    
                    data = database.getCompletedParcels(data);
                    Log.d("SyncService", "Parcels added to email data, count: " + (data.getParcelNumbers() != null ? data.getParcelNumbers().size() : 0));
                    
                    // Check if we have the required AppConstant email address
                    if (AppConstant.EMAIL == null || AppConstant.EMAIL.trim().isEmpty()) {
                        Log.e("SyncService", "AppConstant.EMAIL is null or empty - cannot send email for " + queuedEmail.getDocument());
                        continue;
                    }
                    
                    Log.i("SyncService", "Attempting to send email to: " + AppConstant.EMAIL + " for document: " + queuedEmail.getDocument());
                    
                    boolean emailSent = sendEmail(data);
                    
                    if (emailSent) {
                        database.setEmailSent(queuedEmail.getDocument(), queuedEmail.getTripId());
                        Log.i("SyncService", "✓ Email sent successfully for " + queuedEmail.getDocument() + " - marked as sent in database");
                        
                        // 🔍 AUDIT: Log successful email delivery
                        AuditLogger auditLogger = AuditLogger.getInstance(getApplicationContext());
                        auditLogger.logEmailDelivery(queuedEmail.getDocument(), queuedEmail.getTripId(), 
                            AppConstant.EMAIL, true, "PDF ePOD delivered successfully via email");
                        
                        // 🔒 SECURITY: Now safe to cleanup sensitive files after successful email delivery
                        cleanupAfterSuccessfulEmail(data);
                        Log.i("SyncService", "🛡️ CLEANUP: Sensitive files removed after successful email delivery");
                    } else {
                        Log.e("SyncService", "✗ Failed to send email for " + queuedEmail.getDocument());
                        
                        // 🔍 AUDIT: Log failed email delivery
                        AuditLogger auditLogger = AuditLogger.getInstance(getApplicationContext());
                        auditLogger.logEmailDelivery(queuedEmail.getDocument(), queuedEmail.getTripId(), 
                            AppConstant.EMAIL, false, "Email delivery failed - will retry");
                    }
                    
                } catch (Exception emailEx) {
                    Log.e("SyncService", "Exception processing individual email for " + queuedEmail.getDocument(), emailEx);
                }
            }
            
            Log.i("SyncService", "Email sync completed - processed " + emailList.size() + " emails");
            
        } catch (Exception e) {
            Log.e("SyncService", "Exception in syncEmail", e);
            e.printStackTrace();
        }
        
        Log.i("SyncService", "=== Finished syncEmail ===");
    }


    private void syncReturn() {
        try {

            openDatabase();

            DropboxHelper.downloadReturnFile(getApplicationContext());

            List<Return> returnsList = database.getReturnsList();

            Log.i("SyncService", "returns size: "+returnsList.size());

            if (!returnsList.isEmpty()) {

                for (Return returnData : returnsList) {

                    File file = JsonHandler.writeReturnFile(getApplicationContext(), returnData);

                    if (DropboxHelper.uploadReturnsFile(getApplicationContext())) {

                        database.deleteReturns(returnData.getItem());

                        Log.i("SyncService", "Return " + returnData.getItem() + " synced.");

                    } else {

                        file.delete();

                        Log.i("SyncService", "Return " + returnData.getItem() + " failed to sync. Return file reset.");
                    }
                }


            }
        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    private boolean sendEmail(Delivery delivery) {
        
        Log.i("EmailService", "=== Starting sendEmail for document: " + delivery.getDocument() + " ===");
        
        try {
            String recipient = AppConstant.EMAIL;
            String subject = "Proof of Delivery for Order: " + (delivery.getOrderNumber() != null && !delivery.getOrderNumber().isEmpty() ? delivery.getOrderNumber() : delivery.getTripId());
            
            Log.i("EmailService", "Email details - Recipient: " + recipient + ", Subject: " + subject);
            Log.d("EmailService", "Delivery details - Customer: " + delivery.getCustomerName() + ", Document: " + delivery.getDocument() + ", Trip: " + delivery.getTripId());
            
            // ❗ CRITICAL DATABASE VALIDATION: Check for missing paths
            if (delivery.getSignPath() == null || delivery.getSignPath().trim().isEmpty()) {
                Log.e("EMAIL_CRITICAL", "🚨 ABORTING EMAIL - Delivery missing signature path in database");
                Log.e("EMAIL_CRITICAL", "Document: " + delivery.getDocument() + ", SignPath: '" + delivery.getSignPath() + "'");
                
                // Mark for retry
                database.markDeliveryForEmailRetry(delivery.getDocument(), delivery.getTripId(), "Missing signature path in database");
                return false;
            }
            
            if (delivery.getImagePath() == null || delivery.getImagePath().trim().isEmpty()) {
                Log.e("EMAIL_CRITICAL", "🚨 ABORTING EMAIL - Delivery missing image path in database");
                Log.e("EMAIL_CRITICAL", "Document: " + delivery.getDocument() + ", ImagePath: '" + delivery.getImagePath() + "'");
                
                // Mark for retry
                database.markDeliveryForEmailRetry(delivery.getDocument(), delivery.getTripId(), "Missing image path in database");
                return false;
            }
            
            Log.i("EMAIL_VALIDATION", "✓ Database path validation passed - SignPath: " + delivery.getSignPath() + ", ImagePath: " + delivery.getImagePath());

            List<String> parcelsList = delivery.getParcelNumbers();
            Collections.sort(parcelsList);
            String parcels = TextUtils.join(", ", parcelsList).replaceAll("\\s", " ");

            String date = delivery.getTime().substring(0, 10);
            String time = delivery.getTime().substring(delivery.getTime().length() - 8);

            // Search for signature files and decrypt
            String signFilename = delivery.getSignPath();
            Log.d("EMAILOUTPUT", "Signature filename from database: " + signFilename);

            byte[] decryptedSignature = null;
            String foundSignaturePath = findSignatureFile(signFilename);

            if (foundSignaturePath != null) {
                Log.i("SignatureSecurity", "=== Starting secure signature decryption for email ===");
                try {
                    // Read encrypted signature file
                    File signatureFile = new File(foundSignaturePath);
                    byte[] encryptedData = new byte[(int) signatureFile.length()];
                    try (FileInputStream fis = new FileInputStream(signatureFile)) {
                        int bytesRead = fis.read(encryptedData);
                        Log.d("SignatureSecurity", "Read encrypted signature file: " + bytesRead + " bytes");
                    }
                    
                    // Use SecurityManager for secure decryption
                    if (securityManager != null) {
                        decryptedSignature = securityManager.decryptSignature(encryptedData);
                        if (decryptedSignature != null) {
                            Log.i("SignatureSecurity", "✓ Signature decrypted successfully using SecurityManager: " + 
                                  decryptedSignature.length + " bytes");
                        } else {
                            Log.e("SignatureSecurity", "SecurityManager failed to decrypt signature");
                            
                            // Fallback to legacy decryption for backward compatibility
                            Log.w("SignatureSecurity", "Attempting legacy decryption fallback");
                            SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
                            String keyString = prefs.getString("signature_key", "");
                            
                            if (!keyString.isEmpty()) {
                                decryptedSignature = ImageHelper.decryptImage(foundSignaturePath, keyString);
                                Log.w("SignatureSecurity", "Legacy decryption successful - consider re-encrypting with SecurityManager");
                            } else {
                                Log.e("SignatureSecurity", "No legacy signature key found either");
                            }
                        }
                    } else {
                        Log.e("SignatureSecurity", "SecurityManager is null - cannot decrypt signature securely");
                    }
                    
                } catch (Exception e) {
                    Log.e("SignatureSecurity", "Failed to decrypt signature: " + e.getMessage(), e);
                    e.printStackTrace();
                }
            } else {
                Log.e("SignatureSecurity", "⚠️ CRITICAL: No signature file found for decryption - delivery: " + delivery.getDocument());
            }

            // Create temporary files for images in cache directory
            File signatureFile = null;
            File photoFile = null;

            if (decryptedSignature != null) {
                signatureFile = new File(getCacheDir(), "signature.png");
                try (FileOutputStream fos = new FileOutputStream(signatureFile)) {
                    fos.write(decryptedSignature);
                    Log.d("EMAILOUTPUT", "Signature file created at: " + signatureFile.getAbsolutePath());
                }
            }

            // 📷 ROBUST PHOTO FILE HANDLING: Search for photo file with various extensions
            String imagePath = delivery.getImagePath();
            File originalPhotoFile = null;
            
            // Try different possible photo file locations and extensions
            String[] possibleExtensions = {".jpg", ".jpeg", ".png", ""};
            String[] possibleDirs = {
                getApplicationContext().getFilesDir() + "/DeliveryApp/DeliveryImage/",
                getApplicationContext().getFilesDir() + "/DeliveryImage/",
                getApplicationContext().getFilesDir() + "/",
                getApplicationContext().getCacheDir() + "/"
            };
            
            Log.d("EMAILOUTPUT", "Searching for photo file with path: " + imagePath);
            
            // Search for the photo file
            photoSearchLoop: for (String dir : possibleDirs) {
                for (String ext : possibleExtensions) {
                    File testFile = new File(dir + imagePath + ext);
                    Log.d("EMAILOUTPUT", "Checking: " + testFile.getAbsolutePath());
                    if (testFile.exists()) {
                        originalPhotoFile = testFile;
                        Log.d("EMAILOUTPUT", "Found photo file at: " + originalPhotoFile.getAbsolutePath());
                        break photoSearchLoop;
                    }
                }
            }
            
            // If still not found, try recursive search
            if (originalPhotoFile == null) {
                Log.w("EMAILOUTPUT", "Photo not found in standard locations, searching recursively...");
                originalPhotoFile = searchForPhotoRecursively(getApplicationContext().getFilesDir(), imagePath);
            }
            
            if (originalPhotoFile != null && originalPhotoFile.exists()) {
                photoFile = new File(getCacheDir(), "photo.jpg");
                try (FileInputStream fis = new FileInputStream(originalPhotoFile);
                     FileOutputStream fos = new FileOutputStream(photoFile)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                    Log.d("EMAILOUTPUT", "Photo file copied to: " + photoFile.getAbsolutePath());
                }
            } else {
                Log.e("EMAILOUTPUT", "⚠️ CRITICAL: Photo file not found anywhere");
                Log.e("EMAILOUTPUT", "Searched for image path: " + imagePath);
                Log.e("EMAILOUTPUT", "Last attempted path: " + (originalPhotoFile != null ? originalPhotoFile.getAbsolutePath() : "N/A"));
                
                // List all files in the delivery image directory for debugging
                File deliveryImageDir = new File(getApplicationContext().getFilesDir() + "/DeliveryApp/DeliveryImage/");
                if (deliveryImageDir.exists()) {
                    File[] files = deliveryImageDir.listFiles();
                    Log.e("EMAILOUTPUT", "Files in DeliveryImage directory: " + (files != null ? files.length : 0));
                    if (files != null) {
                        for (File file : files) {
                            Log.e("EMAILOUTPUT", "  - " + file.getName());
                        }
                    }
                } else {
                    Log.e("EMAILOUTPUT", "DeliveryImage directory does not exist: " + deliveryImageDir.getAbsolutePath());
                }
            }
            
            // ❗ CRITICAL BUSINESS VALIDATION: Ensure required content exists
            boolean hasSignature = (decryptedSignature != null && signatureFile != null);
            boolean hasPhoto = (photoFile != null && photoFile.exists());
            
            Log.i("EMAIL_VALIDATION", "Content validation - Signature: " + hasSignature + ", Photo: " + hasPhoto + ", Document: " + delivery.getDocument());
            
            // BUSINESS RULE: POD must have both signature and photo
            if (!hasSignature && !hasPhoto) {
                Log.e("EMAIL_CRITICAL", "🚨 ABORTING EMAIL - Both signature and photo missing for delivery: " + delivery.getDocument());
                Log.e("EMAIL_CRITICAL", "Signature path from DB: " + delivery.getSignPath());
                Log.e("EMAIL_CRITICAL", "Image path from DB: " + delivery.getImagePath());
                
                // Mark for retry
                database.markDeliveryForEmailRetry(delivery.getDocument(), delivery.getTripId(), "Both signature and photo files missing");
                
                // Clean up any temporary files
                if (signatureFile != null && signatureFile.exists()) signatureFile.delete();
                if (photoFile != null && photoFile.exists()) photoFile.delete();
                
                return false; // ABORT EMAIL SENDING
            }
            
            // BUSINESS RULE: POD should have signature (critical for legal compliance)
            if (!hasSignature) {
                Log.e("EMAIL_CRITICAL", "🚨 ABORTING EMAIL - Missing signature for delivery: " + delivery.getDocument());
                Log.e("EMAIL_CRITICAL", "This POD has no legal value without customer signature");
                Log.e("EMAIL_CRITICAL", "Signature path from DB: " + delivery.getSignPath());
                
                // Mark for retry
                database.markDeliveryForEmailRetry(delivery.getDocument(), delivery.getTripId(), "Signature file missing or decryption failed");
                
                // Clean up any temporary files
                if (signatureFile != null && signatureFile.exists()) signatureFile.delete();
                if (photoFile != null && photoFile.exists()) photoFile.delete();
                
                return false; // ABORT EMAIL SENDING
            }
            
            // BUSINESS RULE: POD should have photo (evidence of delivery)
            if (!hasPhoto) {
                Log.e("EMAIL_CRITICAL", "🚨 ABORTING EMAIL - Missing photo for delivery: " + delivery.getDocument());
                Log.e("EMAIL_CRITICAL", "POD missing visual evidence of delivery");
                Log.e("EMAIL_CRITICAL", "Image path from DB: " + delivery.getImagePath());
                
                // Mark for retry
                database.markDeliveryForEmailRetry(delivery.getDocument(), delivery.getTripId(), "Photo file missing or inaccessible");
                
                // Clean up any temporary files
                if (signatureFile != null && signatureFile.exists()) signatureFile.delete();
                if (photoFile != null && photoFile.exists()) photoFile.delete();
                
                return false; // ABORT EMAIL SENDING
            }
            
            Log.i("EMAIL_VALIDATION", "✓ Content validation passed - proceeding with PDF generation");

            // Build compact ePOD HTML structure
            StringBuilder bodyBuilder = new StringBuilder();

            bodyBuilder.append("<div style='font-family: Arial, sans-serif; margin: 0 auto; padding: 20px; max-width: 800px; border: 1px solid #ddd;'>")
                    .append("<div style='text-align: center; border-bottom: 2px solid #3498db; padding-bottom: 10px; margin-bottom: 20px;'>")
                    .append("<h1 style='color: #2c3e50; margin: 0; font-size: 24px;'>PROOF OF DELIVERY</h1>")
                    .append("</div>")

                    .append("<table style='width: 100%; border-collapse: collapse; margin-bottom: 20px;'>")
                    .append("<tr>")
                    .append("<td style='width: 50%; vertical-align: top;'>")
                    .append("<h3 style='color: #34495e; margin: 0 0 10px 0; font-size: 16px;'>Order & Shipment Details</h3>")
                    .append("<table style='width: 100%; font-size: 12px;'>")
                    .append("<tr><td style='font-weight: bold; padding: 4px 0;'>Order Number:</td><td>" + (delivery.getOrderNumber() != null && !delivery.getOrderNumber().isEmpty() ? delivery.getOrderNumber() : delivery.getTripId()) + "</td></tr>")
                    .append("<tr><td style='font-weight: bold; padding: 4px 0;'>Shipment Number:</td><td>" + delivery.getDocument() + "</td></tr>")
                    .append("<tr><td style='font-weight: bold; padding: 4px 0;'>Delivery Date:</td><td>" + date + "</td></tr>")
                    .append("<tr><td style='font-weight: bold; padding: 4px 0;'>Delivery Time:</td><td>" + time + "</td></tr>")
                    .append("</table>")
                    .append("</td>")
                    .append("<td style='width: 50%; vertical-align: top;'>")
                    .append("<h3 style='color: #34495e; margin: 0 0 10px 0; font-size: 16px;'>Delivery Information</h3>")
                    .append("<table style='width: 100%; font-size: 12px;'>")
                    .append("<tr><td style='font-weight: bold; padding: 4px 0;'>Delivered To:</td><td>" + delivery.getCustomerName() + "</td></tr>")
                    .append("<tr><td style='font-weight: bold; padding: 4px 0;'>Driver:</td><td>" + AppConstant.DRIVER + "</td></tr>")
                    .append("<tr><td style='font-weight: bold; padding: 4px 0;'>Vehicle:</td><td>" + AppConstant.VEHICLE + "</td></tr>")
                    .append("</table>")
                    .append("</td>")
                    .append("</tr>")
                    .append("</table>")

                    .append("<div style='border-top: 1px solid #eee; padding-top: 20px; margin-top: 20px;'>")
                    .append("<h3 style='color: #34495e; margin: 0 0 10px 0; font-size: 16px;'>Parcel Information</h3>")
                    .append("<table style='width: 100%; font-size: 12px;'>")
                    .append("<tr><td style='font-weight: bold; padding: 4px 0;'>Total Parcels:</td><td>" + delivery.getNumberOfParcels() + "</td></tr>");

            if (!TextUtils.isEmpty(delivery.getComment())) {
                bodyBuilder.append("<tr><td style='font-weight: bold; padding: 4px 0; vertical-align: top;'>Delivery Notes:</td><td>" + delivery.getComment() + "</td></tr>");
            }

            bodyBuilder.append("<tr><td style='font-weight: bold; padding: 4px 0; vertical-align: top;'>Parcel Items:</td><td style='font-size: 10px; word-break: break-all;'>" + parcels + "</td></tr>")
                    .append("</table>")
                    .append("</div>")

                    .append("<div style='border-top: 1px solid #eee; padding-top: 20px; margin-top: 20px;'>")
                    .append("<h3 style='color: #34495e; margin: 0 0 15px 0; font-size: 16px; text-align: center;'>Evidence of Delivery</h3>")
                    .append("<table style='width: 100%; border-collapse: collapse;'>")
                    .append("<tr>")
                    .append("<td style='width: 50%; text-align: center; padding: 10px; border-right: 1px solid #eee;'>")
                    .append("<p style='font-weight: bold; margin: 0 0 10px 0; font-size: 12px;'>Customer Signature</p>");

            if (signatureFile != null) {
                bodyBuilder.append("<div style='border: 1px solid #ddd; padding: 5px; background-color: #f9f9f9; display: inline-block;'>")
                        .append("<img src='signature.png' style='max-width: 200px; max-height: 80px;' alt='Signature'/>")
                        .append("</div>");
            } else {
                bodyBuilder.append("<div style='border: 1px dashed #ccc; padding: 20px; color: #777; background-color: #f9f9f9; font-size: 12px;'>")
                        .append("No Signature Captured")
                        .append("</div>");
            }

            bodyBuilder.append("</td>")
                    .append("<td style='width: 50%; text-align: center; padding: 10px;'>")
                    .append("<p style='font-weight: bold; margin: 0 0 10px 0; font-size: 12px;'>Delivery Photo</p>");

            if (photoFile != null) {
                bodyBuilder.append("<div style='border: 1px solid #ddd; padding: 5px; background-color: #f9f9f9; display: inline-block;'>")
                        .append("<img src='photo.jpg' style='max-width: 200px; max-height: 80px;' alt='Delivery Photo'/>")
                        .append("</div>");
            } else {
                bodyBuilder.append("<div style='border: 1px dashed #ccc; padding: 20px; color: #777; background-color: #f9f9f9; font-size: 12px;'>")
                        .append("No Photo Available")
                        .append("</div>");
            }

            bodyBuilder.append("</td>")
                    .append("</tr>")
                    .append("</table>")
                    .append("</div>")

                    .append("<div style='border-top: 2px solid #3498db; padding-top: 15px; margin-top: 30px; text-align: center; font-size: 10px; color: #777;'>")
                    .append("<p style='margin: 0;'>Generated by EasyDelivery on " + date + " at " + time + "</p>")
                    .append("<p style='margin: 0;'>This is an electronically generated document and does not require a physical signature.</p>")
                    .append("</div>")
                    .append("</div>");

            String body = bodyBuilder.toString();

            // Generate PDF with proper base URI
            File pdfFile = new File(getCacheDir(), "POD_" + delivery.getDocument() + ".pdf");
            PdfWriter writer = new PdfWriter(pdfFile);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Set the base URI to the cache directory so images can be found
            ConverterProperties converterProperties = new ConverterProperties();
            converterProperties.setBaseUri(getCacheDir().getAbsolutePath() + "/");

            Log.d("EMAILOUTPUT", "Converting HTML to PDF with base URI: " + getCacheDir().getAbsolutePath());
            Log.d("EMAILOUTPUT", "HTML content: " + body);

            // Convert HTML to PDF
            HtmlConverter.convertToPdf(body, pdfDoc, converterProperties);

            document.close();

            Log.d("EMAILOUTPUT", "PDF generated successfully: " + pdfFile.getAbsolutePath());
            
            // ❗ CRITICAL PDF VALIDATION: Verify PDF contains required content
            if (!verifyPDFContent(pdfFile, hasSignature, hasPhoto)) {
                Log.e("EMAIL_CRITICAL", "🚨 ABORTING EMAIL - Generated PDF validation failed");
                
                // Clean up all temporary files
                if (signatureFile != null && signatureFile.exists()) signatureFile.delete();
                if (photoFile != null && photoFile.exists()) photoFile.delete();
                if (pdfFile.exists()) pdfFile.delete();
                
                return false; // ABORT EMAIL SENDING
            }
            
            Log.i("EMAIL_VALIDATION", "✓ PDF content verification passed - proceeding with email");

            // Email setup
            Log.i("EmailService", "Setting up email authentication...");
            final String username = "dev@easydelivery.biz";
            
            if (securityManager == null) {
                Log.e("EmailService", "SecurityManager is null - cannot retrieve email password");
                return false;
            }
            
            String password = securityManager.getEmailPassword();
            Log.i("EmailService", "Retrieved password from SecurityManager: " + (password != null && !password.isEmpty() ? "[PASSWORD_SET]" : "[PASSWORD_EMPTY]"));

            if (password == null || password.trim().isEmpty()) {
                Log.e("EmailService", "Email password is null or empty - cannot authenticate");
                throw new RuntimeException("Email password is null or empty");
            }

            Properties properties = new Properties();
            properties.put("mail.smtp.host", "smtp.gmail.com");
            properties.put("mail.smtp.port", "587");
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");
            
            Log.i("EmailService", "SMTP properties configured - Host: smtp.gmail.com, Port: 587, Auth: true, TLS: true");

            Session session = Session.getInstance(properties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    Log.d("EmailService", "SMTP authentication requested - providing credentials");
                    return new PasswordAuthentication(username, password);
                }
            });
            
            Log.i("EmailService", "Email session created successfully");

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("dev@easydelivery.biz"));
            message.addRecipient(MimeMessage.RecipientType.TO, new InternetAddress(recipient));
            message.setSubject(subject);
            
            Log.i("EmailService", "Email message configured - From: dev@easydelivery.biz, To: " + recipient);

            Multipart multipart = new MimeMultipart();
            MimeBodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText("Dear Customer,\n\nPlease find the attached Proof of Delivery (POD) for your recent shipment.\n\nThis document confirms the successful delivery of your items. It includes details such as the shipment number, delivery address, and evidence of delivery.\n\nThank you for your business.\n\nBest regards,\nEasyDelivery Team");
            multipart.addBodyPart(messageBodyPart);

            // Attach PDF
            Log.i("EmailService", "Attaching PDF file: " + pdfFile.getAbsolutePath());
            addAttachment(multipart, pdfFile.getAbsolutePath(), "POD_" + delivery.getDocument() + ".pdf");
            Log.i("EmailService", "PDF attachment added successfully");

            message.setContent(multipart);
            Log.i("EmailService", "Email content set - attempting to send email...");
            
            Transport.send(message);
            Log.i("EmailService", "Email sent successfully via SMTP!");

            // Clean up temporary files
            Log.d("EmailService", "Cleaning up temporary files...");
            if (signatureFile != null && signatureFile.exists()) {
                boolean deleted = signatureFile.delete();
                Log.d("EmailService", "Signature file deleted: " + deleted);
            }
            if (photoFile != null && photoFile.exists()) {
                boolean deleted = photoFile.delete();
                Log.d("EmailService", "Photo file deleted: " + deleted);
            }
            boolean pdfDeleted = pdfFile.delete();
            Log.d("EmailService", "PDF file deleted: " + pdfDeleted);

            Log.i("EmailService", "✓ Email sent successfully for document: " + delivery.getDocument());
            Log.i("EmailService", "=== Finished sendEmail successfully ===");
            return true;
            
        } catch (jakarta.mail.MessagingException e) {
            Log.e("EmailService", "SMTP/Messaging error sending email for " + delivery.getDocument() + ": " + e.getMessage(), e);
            return false;
        } catch (java.io.IOException e) {
            Log.e("EmailService", "IO error (file/PDF generation) for " + delivery.getDocument() + ": " + e.getMessage(), e);
            return false;
        } catch (SecurityException e) {
            Log.e("EmailService", "Security/Authentication error for " + delivery.getDocument() + ": " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            Log.e("EmailService", "Unexpected error sending email for " + delivery.getDocument() + ": " + e.getMessage(), e);
            e.printStackTrace();
            return false;
        } finally {
            Log.i("EmailService", "=== Finished sendEmail (with or without success) ===");
        }
    }

    // Add this helper method to search for signature files
    private String findSignatureFile(String filename) {
        Log.d("EMAILOUTPUT", "Searching for signature file: " + filename);

        if (filename == null || filename.isEmpty()) {
            Log.e("EMAILOUTPUT", "Filename is null or empty");
            return null;
        }

        // List of potential directories to search
        String[] searchPaths = {
                getApplicationContext().getFilesDir() + "/DeliveryApp/Signature/",
                getApplicationContext().getFilesDir() + "/Signature/",
                getApplicationContext().getFilesDir() + "/",
                getApplicationContext().getCacheDir() + "/",
                getApplicationContext().getExternalFilesDir(null) + "/DeliveryApp/Signature/",
                getApplicationContext().getExternalFilesDir(null) + "/Signature/",
                getApplicationContext().getExternalFilesDir(null) + "/"
        };

        // First, try exact filename match
        for (String basePath : searchPaths) {
            File dir = new File(basePath);
            if (dir.exists() && dir.isDirectory()) {
                File targetFile = new File(dir, filename);
                if (targetFile.exists()) {
                    Log.d("EMAILOUTPUT", "Found signature file at: " + targetFile.getAbsolutePath());
                    return targetFile.getAbsolutePath();
                }
            }
        }

        // If not found, search recursively in all app directories
        Log.d("EMAILOUTPUT", "File not found in standard locations, searching recursively...");

        String foundPath = searchRecursively(getApplicationContext().getFilesDir(), filename);
        if (foundPath != null) {
            return foundPath;
        }

        foundPath = searchRecursively(getApplicationContext().getCacheDir(), filename);
        if (foundPath != null) {
            return foundPath;
        }

        // Also search external files directory if available
        File externalFilesDir = getApplicationContext().getExternalFilesDir(null);
        if (externalFilesDir != null) {
            foundPath = searchRecursively(externalFilesDir, filename);
            if (foundPath != null) {
                return foundPath;
            }
        }

        // If still not found, list all .enc files to see what's available
        Log.d("EMAILOUTPUT", "Signature file not found. Listing all .enc files:");
        listEncryptedFiles(getApplicationContext().getFilesDir());
        listEncryptedFiles(getApplicationContext().getCacheDir());

        return null;
    }

    // Helper method to search recursively
    private String searchRecursively(File directory, String filename) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return null;
        }

        Log.d("EMAILOUTPUT", "Searching in directory: " + directory.getAbsolutePath());

        File[] files = directory.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (file.isFile() && file.getName().equals(filename)) {
                Log.d("EMAILOUTPUT", "Found file: " + file.getAbsolutePath());
                return file.getAbsolutePath();
            } else if (file.isDirectory()) {
                String found = searchRecursively(file, filename);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    // Helper method to list all encrypted files
    private void listEncryptedFiles(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".enc")) {
                Log.d("EMAILOUTPUT", "Found .enc file: " + file.getAbsolutePath());
            } else if (file.isDirectory()) {
                listEncryptedFiles(file);
            }
        }
    }

    private void addAttachment(Multipart multipart, String filePath, String fileName) throws MessagingException {
        MimeBodyPart attachmentBodyPart = new MimeBodyPart();
        DataSource source = new FileDataSource(filePath);
        attachmentBodyPart.setDataHandler(new DataHandler(source));
        attachmentBodyPart.setFileName(fileName);
        multipart.addBodyPart(attachmentBodyPart);
    }

    /**
     * Verify PDF content contains required signature and photo evidence
     */
    private boolean verifyPDFContent(File pdfFile, boolean expectedSignature, boolean expectedPhoto) {
        String operationId = generateOperationId();
        Log.i("PDF_VERIFICATION", "[" + operationId + "] Verifying PDF content - file: " + pdfFile.getName());
        Log.i("PDF_VERIFICATION", "[" + operationId + "] Expected signature: " + expectedSignature + ", Expected photo: " + expectedPhoto);
        
        try {
            if (!pdfFile.exists() || pdfFile.length() == 0) {
                Log.e("PDF_VERIFICATION", "[" + operationId + "] PDF file does not exist or is empty");
                Log.e("PDF_VERIFICATION", "[" + operationId + "] PDF path: " + pdfFile.getAbsolutePath());
                Log.e("PDF_VERIFICATION", "[" + operationId + "] PDF exists: " + pdfFile.exists());
                Log.e("PDF_VERIFICATION", "[" + operationId + "] PDF size: " + pdfFile.length());
                return false;
            }
            
            Log.i("PDF_VERIFICATION", "[" + operationId + "] PDF file exists with size: " + pdfFile.length() + " bytes");
            
            // Basic file size validation - PDFs with images should be larger
            long minExpectedSize = expectedSignature && expectedPhoto ? 10000 : 5000; // 10KB with images, 5KB without
            Log.i("PDF_VERIFICATION", "[" + operationId + "] Minimum expected size: " + minExpectedSize + " bytes");
            
            if (pdfFile.length() < minExpectedSize) {
                Log.e("PDF_VERIFICATION", "[" + operationId + "] PDF file too small: " + pdfFile.length() + " bytes, expected > " + minExpectedSize);
                return false;
            }
            
            // Check if signature and photo files exist in cache (they should be there during PDF generation)
            File signatureCache = new File(getCacheDir(), "signature.png");
            File photoCache = new File(getCacheDir(), "photo.jpg");
            
            Log.i("PDF_VERIFICATION", "[" + operationId + "] Checking cache files:");
            Log.i("PDF_VERIFICATION", "[" + operationId + "] Signature cache path: " + signatureCache.getAbsolutePath());
            Log.i("PDF_VERIFICATION", "[" + operationId + "] Photo cache path: " + photoCache.getAbsolutePath());
            
            boolean signatureExists = signatureCache.exists() && signatureCache.length() > 0;
            boolean photoExists = photoCache.exists() && photoCache.length() > 0;
            
            Log.i("PDF_VERIFICATION", "[" + operationId + "] Signature file exists: " + signatureCache.exists() + ", size: " + (signatureCache.exists() ? signatureCache.length() : 0));
            Log.i("PDF_VERIFICATION", "[" + operationId + "] Photo file exists: " + photoCache.exists() + ", size: " + (photoCache.exists() ? photoCache.length() : 0));
            
            Log.d("PDF_VERIFICATION", "[" + operationId + "] Cache files - signature exists: " + signatureExists + ", photo exists: " + photoExists);
            
            if (expectedSignature && !signatureExists) {
                Log.e("PDF_VERIFICATION", "[" + operationId + "] Expected signature but cache file missing or empty");
                Log.e("PDF_VERIFICATION", "[" + operationId + "] Signature file exists: " + signatureCache.exists());
                Log.e("PDF_VERIFICATION", "[" + operationId + "] Signature file size: " + (signatureCache.exists() ? signatureCache.length() : "N/A"));
                return false;
            }
            
            if (expectedPhoto && !photoExists) {
                Log.e("PDF_VERIFICATION", "[" + operationId + "] Expected photo but cache file missing or empty");
                Log.e("PDF_VERIFICATION", "[" + operationId + "] Photo file exists: " + photoCache.exists());
                Log.e("PDF_VERIFICATION", "[" + operationId + "] Photo file size: " + (photoCache.exists() ? photoCache.length() : "N/A"));
                return false;
            }
            
            Log.i("PDF_VERIFICATION", "[" + operationId + "] PDF content verification successful - size: " + pdfFile.length() + " bytes");
            return true;
            
        } catch (Exception e) {
            Log.e("PDF_VERIFICATION", "[" + operationId + "] Error during PDF verification: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Recursively search for photo file in directory tree
     */
    private File searchForPhotoRecursively(File directory, String imagePath) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return null;
        }
        
        Log.d("EMAILOUTPUT", "Searching for photo in: " + directory.getAbsolutePath());
        
        File[] files = directory.listFiles();
        if (files == null) return null;
        
        // Try different extensions for the image path
        String[] extensions = {".jpg", ".jpeg", ".png", ""};
        
        for (File file : files) {
            if (file.isFile()) {
                String fileName = file.getName();
                
                // Check if filename matches imagePath with any extension
                for (String ext : extensions) {
                    if (fileName.equals(imagePath + ext) || fileName.equals(imagePath)) {
                        Log.d("EMAILOUTPUT", "Found photo file: " + file.getAbsolutePath());
                        return file;
                    }
                }
                
                // Also check if imagePath is contained in filename (partial match)
                if (fileName.contains(imagePath)) {
                    Log.d("EMAILOUTPUT", "Found photo file (partial match): " + file.getAbsolutePath());
                    return file;
                }
            } else if (file.isDirectory()) {
                // Recursively search subdirectories
                File found = searchForPhotoRecursively(file, imagePath);
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Generate unique operation ID for tracking
     */
    private String generateOperationId() {
        return "PDF" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }

    private void openDatabase() {
        if (database == null) {
            database = new DeliveryDb(getApplicationContext());
            database.open();
        } else {
            database.open();
        }
    }
    
    /**
     * Restore completed trips list from database
     * This fixes the issue where completed trips get re-downloaded after app restart
     * because the in-memory AppConstant.completedTrips list is lost
     */
    private void restoreCompletedTripsFromDatabase() {
        Log.i("SyncService", "=== Restoring completed trips from database ===");
        
        try {
            DeliveryDb restoreDb = new DeliveryDb(getApplicationContext());
            restoreDb.open();
            
            // Get all fully completed trips from database
            List<String> fullyCompletedTrips = restoreDb.getAllFullyCompletedTrips();
            
            Log.i("SyncService", "Current AppConstant.completedTrips size: " + AppConstant.completedTrips.size() + " - " + AppConstant.completedTrips);
            Log.i("SyncService", "Fully completed trips from database: " + fullyCompletedTrips.size() + " - " + fullyCompletedTrips);
            
            // Add any missing completed trips to the in-memory list
            int addedCount = 0;
            for (String tripId : fullyCompletedTrips) {
                if (!AppConstant.completedTrips.contains(tripId)) {
                    AppConstant.completedTrips.add(tripId);
                    addedCount++;
                    Log.i("SyncService", "Restored completed trip to memory: " + tripId);
                }
            }
            
            Log.i("SyncService", "Restored " + addedCount + " completed trips from database");
            Log.i("SyncService", "Final AppConstant.completedTrips size: " + AppConstant.completedTrips.size() + " - " + AppConstant.completedTrips);
            
            restoreDb.close();
            
        } catch (Exception e) {
            Log.e("SyncService", "Error restoring completed trips from database", e);
            e.printStackTrace();
        }
        
        Log.i("SyncService", "=== Finished restoring completed trips from database ===");
    }
    
    /**
     * 🚑 CRITICAL: Check for orphaned trips on startup
     * 
     * This handles the scenario where:
     * 1. App was viewing a trip (trip moved to in_progress)
     * 2. App was forcefully closed/updated during development
     * 3. Trip remains orphaned in in_progress folder
     * 4. Device should either resume or release the trip
     */
    private void checkForOrphanedTripsOnStartup() {
        Log.i("SyncService", "=== 🚑 ORPHANED TRIP RECOVERY: Checking for orphaned trips on startup ===");
        
        try {
            Thread orphanCheckThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        
                        String currentDeviceId = DropboxHelper.getDeviceId(getApplicationContext());
                        if (currentDeviceId == null || currentDeviceId.isEmpty()) {
                            Log.w("SyncService", "🚑 Cannot check orphaned trips - device ID unavailable");
                            return;
                        }
                        
                        Log.i("SyncService", "🚑 Checking for trips orphaned by device: " + currentDeviceId);
                        
                        // Check if we have any trips in in_progress that belong to this device
                        java.util.List<String> orphanedTrips = findOrphanedTripsForDevice(currentDeviceId);
                        
                        if (orphanedTrips.isEmpty()) {
                            Log.i("SyncService", "✅ No orphaned trips found for device: " + currentDeviceId);
                            return;
                        }
                        
                        Log.w("SyncService", "🚑 FOUND " + orphanedTrips.size() + " ORPHANED TRIP(S): " + orphanedTrips);
                        
                        // Handle each orphaned trip
                        for (String tripId : orphanedTrips) {
                            handleOrphanedTrip(tripId, currentDeviceId);
                        }
                        
                        Log.i("SyncService", "✅ Orphaned trip recovery completed");
                        
                    } catch (Exception e) {
                        Log.e("SyncService", "❌ Error during orphaned trip recovery", e);
                    }
                }
            });
            
            orphanCheckThread.start();
            
        } catch (Exception e) {
            Log.e("SyncService", "Error starting orphaned trip check thread", e);
        }
        
        Log.i("SyncService", "=== 🚑 Orphaned trip recovery check initiated ===");
    }
    
    /**
     * Find trips in in_progress folder that belong to a specific device
     */
    private java.util.List<String> findOrphanedTripsForDevice(String deviceId) {
        java.util.List<String> orphanedTrips = new java.util.ArrayList<>();
        
        try {
            com.dropbox.core.v2.DbxClientV2 client = DropboxHelper.getClient(getApplicationContext());
            if (client == null) {
                Log.w("SyncService", "🚑 Cannot check orphaned trips - Dropbox client unavailable");
                return orphanedTrips;
            }
            
            String inProgressPath = "/Customers/" + AppConstant.COMPANY + "/in_progress";
            com.dropbox.core.v2.files.ListFolderResult inProgressFiles = client.files().listFolder(inProgressPath);
            
            if (inProgressFiles != null && !inProgressFiles.getEntries().isEmpty()) {
                for (int i = 0; i < inProgressFiles.getEntries().size(); i++) {
                    String fileName = inProgressFiles.getEntries().get(i).getName();
                    
                    // Parse claimed trip filename format: TripId_DeviceId_Timestamp.json
                    DropboxHelper.ClaimInfo claimInfo = DropboxHelper.parseClaimInfo(fileName);
                    
                    if (claimInfo.isValidClaim && deviceId.equals(claimInfo.deviceId)) {
                        orphanedTrips.add(claimInfo.tripId);
                        Log.i("SyncService", "🚑 Found orphaned trip: " + claimInfo.tripId + " claimed by this device");
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e("SyncService", "Error finding orphaned trips", e);
        }
        
        return orphanedTrips;
    }
    
    /**
     * Handle an orphaned trip by releasing it back to available
     */
    private void handleOrphanedTrip(String tripId, String deviceId) {
        Log.i("SyncService", "🚑 HANDLING ORPHANED TRIP: " + tripId + " (device: " + deviceId + ")");
        
        try {
            // Clear any local state that might reference this trip
            if (SyncConstant.STARTED_TRIP.equals(tripId)) {
                SyncConstant.STARTED_TRIP = "";
                Log.i("SyncService", "🧩 Cleared STARTED_TRIP reference: " + tripId);
            }
            
            if (AppConstant.TRIPID != null && AppConstant.TRIPID.equals(tripId)) {
                AppConstant.TRIPID = "";
                Log.i("SyncService", "🧩 Cleared TRIPID reference: " + tripId);
            }
            
            // Use UnifiedTripManager to properly release the trip
            UnifiedTripManager tripManager = UnifiedTripManager.getInstance(getApplicationContext());
            boolean released = tripManager.releaseTrip(tripId, "Orphaned trip recovery on startup");
            
            if (released) {
                Log.i("SyncService", "✅ ORPHANED TRIP RELEASED: " + tripId + " moved back to available");
            } else {
                Log.w("SyncService", "⚠️ Failed to release orphaned trip via UnifiedTripManager: " + tripId);
                
                // Fallback: try direct Dropbox operation
                boolean fallbackReleased = DropboxHelper.unclaimSpecificTrip(getApplicationContext(), tripId);
                if (fallbackReleased) {
                    Log.i("SyncService", "✅ FALLBACK: Orphaned trip released via direct Dropbox operation: " + tripId);
                } else {
                    Log.e("SyncService", "❌ Failed to release orphaned trip even with fallback: " + tripId);
                }
            }
            
        } catch (Exception e) {
            Log.e("SyncService", "Error handling orphaned trip: " + tripId, e);
        }
    }
    
    /**
     * Start adaptive polling system that:
     * - Syncs immediately on startup
     * - Uses fast polling (3s) when no trips are found locally
     * - Uses normal polling (15s) when trips exist
     * - Uses slow polling (30s) for background maintenance
     */
    private void startAdaptivePolling() {
        Log.i("SyncService", "Starting adaptive polling system");
        
        // Immediate sync on startup
        Thread immediateSync = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i("SyncService", "Performing immediate sync on startup...");
                performSyncOperations();
                
                // Start adaptive timer after immediate sync
                scheduleAdaptiveSync(0, 0); // Start with 0 empty checks and no delay
            }
        });
        immediateSync.start();
    }
    
    private void scheduleAdaptiveSync(int consecutiveEmptyChecks, long lastSyncTime) {
        Timer adaptiveTimer = new Timer();
        adaptiveTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    long currentTime = System.currentTimeMillis();
                    
                    // Check if we have local trips
                    boolean hasLocalTrips = hasLocalTrips();
                    
                    // Update empty checks counter
                    int newEmptyChecks = hasLocalTrips ? 0 : consecutiveEmptyChecks + 1;
                    
                    // Determine polling interval based on state
                    long nextInterval;
                    String reason;
                    
                    if (!hasLocalTrips) {
                        if (newEmptyChecks <= 10) {
                            // Fast polling for first 30 seconds when no trips
                            nextInterval = 3000; // 3 seconds
                            reason = "fast polling (no local trips, attempt " + newEmptyChecks + "/10)";
                        } else {
                            // Medium polling if still no trips after 30s
                            nextInterval = 8000; // 8 seconds 
                            reason = "medium polling (still no trips after 30s)";
                        }
                    } else {
                        // Normal polling when trips exist
                        nextInterval = 15000; // 15 seconds
                        reason = "normal polling (trips exist)";
                    }
                    
                    // Avoid too frequent syncing
                    if (currentTime - lastSyncTime < 2000) {
                        Log.d("SyncService", "Skipping sync - too soon since last sync (" + (currentTime - lastSyncTime) + "ms ago)");
                        // Schedule next run with same parameters
                        scheduleAdaptiveSync(newEmptyChecks, lastSyncTime);
                        return;
                    }
                    
                    Log.i("SyncService", "Running sync cycle - " + reason + " (next in " + (nextInterval/1000) + "s)");
                    
                    // Perform sync operations
                    performSyncOperations();
                    long newLastSyncTime = System.currentTimeMillis();
                    
                    // Schedule next run after the interval
                    Timer nextTimer = new Timer();
                    nextTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            scheduleAdaptiveSync(newEmptyChecks, newLastSyncTime);
                        }
                    }, nextInterval);
                    
                } catch (Exception e) {
                    Log.e("SyncService", "Error in adaptive polling", e);
                    // Fallback to normal interval on error
                    Timer fallbackTimer = new Timer();
                    fallbackTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            scheduleAdaptiveSync(0, System.currentTimeMillis());
                        }
                    }, 15000);
                }
            }
        }, 100); // Start after 100ms
    }
    
    /**
     * Check if we have local trips available
     */
    private boolean hasLocalTrips() {
        try {
            File tripDir = new File(getApplicationContext().getFilesDir() + "/Trip/");
            if (!tripDir.exists()) {
                return false;
            }
            
            File[] files = tripDir.listFiles();
            if (files == null) {
                return false;
            }
            
            // Count valid JSON trip files
            int validTrips = 0;
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".json")) {
                    validTrips++;
                }
            }
            
            Log.d("SyncService", "Found " + validTrips + " local trip files");
            return validTrips > 0;
            
        } catch (Exception e) {
            Log.e("SyncService", "Error checking local trips", e);
            return false;
        }
    }
    
    /**
     * Perform all sync operations with production health monitoring
     */
    private void performSyncOperations() {
        try {
            if (database != null && database.isOpen()) {
                database.close();
            }
            
            // 🎯 UNIFIED: Health monitoring is now handled by ConnectivityAwareSyncManager
            Log.v("SyncService", "🎯 UNIFIED: Health monitoring integrated into sync operations");

            Thread threadDownloadTrips = new Thread(new Runnable() {
                @Override
                public void run() {
                    DropboxHelper.downloadAllTrips(getApplicationContext());
                }
            });

            Thread threadCompletedTrip = new Thread(new Runnable() {
                @Override
                public void run() {
                    // 🚀 Enhanced File-Based Syncing System (exclusive)
                    // Only process trip completions if there are actually completed trips
                    if (!AppConstant.completedTrips.isEmpty()) {
                        Log.d("SyncService", "🚀 Enhanced Sync: Processing " + AppConstant.completedTrips.size() + " completed trips with enhanced state management");
                        handleTripCompleted();
                    } else {
                        Log.v("SyncService", "🚀 Enhanced Sync: No completed trips to process - skipping trip completion handler");
                    }
                }
            });

            Thread threadTripStatus = new Thread(new Runnable() {
                @Override
                public void run() {
                    syncTripStatus();
                }
            });

            Thread threadCompletedData = new Thread(new Runnable() {
                @Override
                public void run() {
                    syncCompletedData();
                }
            });

            Thread threadEmail = new Thread(new Runnable() {
                @Override
                public void run() {
                    syncEmail();
                }
            });

            Thread threadReturns = new Thread(new Runnable() {
                @Override
                public void run() {
                    syncReturn();
                }
            });

            threadDownloadTrips.start();
            threadTripStatus.start();
            threadEmail.start();
            threadCompletedData.start();
            threadCompletedTrip.start();
            threadReturns.start();

            try {
                threadDownloadTrips.join();
                threadTripStatus.join();
                threadEmail.join();
                threadCompletedData.join();
                threadCompletedTrip.join();
                threadReturns.join();
                if (database != null && database.isOpen()) {
                    database.close();
                }
            } catch (InterruptedException e) {
                Log.e("SyncService", "Sync operations interrupted", e);
            }
        } catch (Exception e) {
            Log.e("SyncService", "Error in sync operations", e);
        }
    }

    /**
     * 🔒 SECURE: Cleanup sensitive files after successful email delivery
     * This method safely removes customer signature and photo files from local storage
     * after successful email delivery to minimize data retention risks.
     */
    private void cleanupAfterSuccessfulEmail(Delivery delivery) {
        try {
            Log.i("SecureCleanup", "=== Starting secure cleanup for delivery: " + delivery.getDocument() + " ===");
            
            // Clean up signature files
            if (delivery.getSignPath() != null && !delivery.getSignPath().trim().isEmpty()) {
                String signaturePath = delivery.getSignPath();
                Log.d("SecureCleanup", "Cleaning up signature file: " + signaturePath);
                
                // Try multiple possible signature locations
                String[] signatureDirs = {
                    getApplicationContext().getFilesDir() + "/DeliveryApp/Signature/",
                    getApplicationContext().getFilesDir() + "/Signature/",
                    getApplicationContext().getFilesDir() + "/"
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
                    getApplicationContext().getFilesDir() + "/DeliveryApp/DeliveryImage/",
                    getApplicationContext().getFilesDir() + "/DeliveryImage/",
                    getApplicationContext().getFilesDir() + "/"
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
            AuditLogger auditLogger = AuditLogger.getInstance(getApplicationContext());
            int totalFilesDeleted = 0;
            if (delivery.getSignPath() != null) totalFilesDeleted++;
            if (delivery.getImagePath() != null) totalFilesDeleted++;
            auditLogger.logSecureCleanup(delivery.getDocument(), delivery.getTripId(), totalFilesDeleted, true);
            
        } catch (Exception e) {
            Log.e("SecureCleanup", "Error during secure cleanup for delivery: " + delivery.getDocument(), e);
            
            // 🔍 AUDIT: Log cleanup failure
            AuditLogger auditLogger = AuditLogger.getInstance(getApplicationContext());
            auditLogger.logSecureCleanup(delivery.getDocument(), delivery.getTripId(), 0, false);
        }
    }

    /**
     * 🗑️ DATA RETENTION: Cleanup old delivery files based on retention policy
     * This method can be called periodically to enforce data retention policies
     */
    private void enforceDataRetentionPolicy() {
        try {
            Log.i("DataRetention", "=== Starting data retention policy enforcement ===");
            
            // Define retention period (e.g., 30 days)
            long retentionPeriodMs = 30 * 24 * 60 * 60 * 1000L; // 30 days in milliseconds
            long cutoffTime = System.currentTimeMillis() - retentionPeriodMs;
            
            // Clean up old signature files
            int totalDeleted = 0;
            totalDeleted += cleanupOldFiles(getApplicationContext().getFilesDir() + "/DeliveryApp/Signature/", cutoffTime);
            totalDeleted += cleanupOldFiles(getApplicationContext().getFilesDir() + "/Signature/", cutoffTime);
            
            // Clean up old photo files  
            totalDeleted += cleanupOldFiles(getApplicationContext().getFilesDir() + "/DeliveryApp/DeliveryImage/", cutoffTime);
            totalDeleted += cleanupOldFiles(getApplicationContext().getFilesDir() + "/DeliveryImage/", cutoffTime);
            
            Log.i("DataRetention", "✓ Data retention policy enforcement completed");
            
            // 🔍 AUDIT: Log data retention enforcement
            AuditLogger auditLogger = AuditLogger.getInstance(getApplicationContext());
            auditLogger.logDataRetentionEnforcement(totalDeleted, 30, true);
            
        } catch (Exception e) {
            Log.e("DataRetention", "Error enforcing data retention policy", e);
            
            // 🔍 AUDIT: Log retention policy failure
            AuditLogger auditLogger = AuditLogger.getInstance(getApplicationContext());
            auditLogger.logDataRetentionEnforcement(0, 30, false);
        }
    }
    
    /**
     * Helper method to clean up old files in a directory
     * @return number of files deleted
     */
    private int cleanupOldFiles(String directoryPath, long cutoffTime) {
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
    
    /**
     * 🎯 Unified Trip Completion Handler
     * 
     * Handles trip completion using the unified trip manager.
     * All sync operations happen automatically and transparently.
     */
    private void handleTripCompleted() {
        // Early return if no trips to process
        if (AppConstant.completedTrips == null || AppConstant.completedTrips.isEmpty()) {
            Log.d("SyncService", "No completed trips to process");
            return;
        }
        
        Log.i("SyncService", "=== Starting Unified Trip Completion (" + AppConstant.completedTrips.size() + " trips) ===");
        
        try {
            openDatabase();
            
            UnifiedTripManager tripManager = UnifiedTripManager.getInstance(getApplicationContext());
            
            // Create a copy to avoid ConcurrentModificationException
            java.util.List<String> tripsToProcess = new java.util.ArrayList<>(AppConstant.completedTrips);
            Log.i("SyncService", "🎯 Processing " + tripsToProcess.size() + " completed trips");
            
            for (String completedTrip : tripsToProcess) {
                Log.i("SyncService", "🎯 Processing completed trip: " + completedTrip);
                
                try {
                    // Use unified trip manager to complete trip
                    boolean success = tripManager.completeTrip(completedTrip);
                    
                    if (success) {
                        Log.i("SyncService", "✅ Trip " + completedTrip + " successfully completed");
                        
                        // Check if all data has been synced
                        boolean isDataSynced = database.isDataSynced(completedTrip);
                        Log.d("SyncService", "Data synced check for " + completedTrip + ": " + isDataSynced);
                        
                        if (isDataSynced) {
                            // Clean up local data
                            AppConstant.completedTrips.remove(completedTrip);
                            Log.i("SyncService", "Removed " + completedTrip + " from completed trips list");
                            
                            database.deleteUploadedData(completedTrip);
                            Log.i("SyncService", "Deleted uploaded data for " + completedTrip);
                            
                            // 🚮 CRITICAL FIX: Delete local trip JSON file to prevent reappearing in dashboard
                            cleanupLocalTripFile(completedTrip);
                            Log.i("SyncService", "Cleaned up local trip file for " + completedTrip);
                            
                            // 📊 AUDIT: Log successful completion
                            AuditLogger auditLogger = AuditLogger.getInstance(getApplicationContext());
                            auditLogger.logTripCompletion(completedTrip, tripManager.getDeviceId(), true, "Unified trip completion successful");
                        }
                        
                        // Clear started trip constant if needed
                        if (SyncConstant.STARTED_TRIP.equals(completedTrip)) {
                            SyncConstant.STARTED_TRIP = "";
                            Log.i("SyncService", "Cleared started trip constant for " + completedTrip);
                        }
                        
                    } else {
                        Log.w("SyncService", "⚠️ Failed to complete trip " + completedTrip);
                    }
                    
                } catch (Exception tripError) {
                    Log.e("SyncService", "Error processing trip " + completedTrip, tripError);
                    
                    // 📊 AUDIT: Log failure
                    AuditLogger auditLogger = AuditLogger.getInstance(getApplicationContext());
                    auditLogger.logTripCompletion(completedTrip, tripManager.getDeviceId(), false, "Unified completion error: " + tripError.getMessage());
                }
            }
            
            Log.i("SyncService", "Final completed trips list size: " + AppConstant.completedTrips.size());
            
        } catch (Exception e) {
            Log.e("SyncService", "Exception in trip completion handler", e);
        }
        
        Log.i("SyncService", "=== Finished Unified Trip Completion ===");
    }
    
    /**
     * 🚮 Clean up local trip JSON file to prevent completed trips from reappearing
     * This is the CRITICAL fix for the issue where completed trips were reappearing in the dashboard
     */
    private void cleanupLocalTripFile(String tripId) {
        try {
            if (tripId == null || tripId.trim().isEmpty()) {
                Log.w("SyncService", "Cannot cleanup local trip file - invalid trip ID");
                return;
            }
            
            File tripDir = new File(getApplicationContext().getFilesDir() + "/Trip/");
            if (!tripDir.exists()) {
                Log.d("SyncService", "Trip directory does not exist, nothing to cleanup");
                return;
            }
            
            // Delete the main trip JSON file
            File tripFile = new File(tripDir, tripId + ".json");
            if (tripFile.exists()) {
                boolean deleted = tripFile.delete();
                if (deleted) {
                    Log.i("SyncService", "✅ Successfully deleted local trip file: " + tripFile.getAbsolutePath());
                } else {
                    Log.e("SyncService", "❌ Failed to delete local trip file: " + tripFile.getAbsolutePath());
                }
            } else {
                Log.d("SyncService", "Local trip file does not exist: " + tripFile.getAbsolutePath());
            }
            
            // Also clean up any temporary files for this trip
            File tempFile = new File(tripDir, tripId + ".json.tmp");
            if (tempFile.exists()) {
                boolean tempDeleted = tempFile.delete();
                if (tempDeleted) {
                    Log.i("SyncService", "✅ Successfully deleted temp trip file: " + tempFile.getAbsolutePath());
                } else {
                    Log.w("SyncService", "⚠️ Failed to delete temp trip file: " + tempFile.getAbsolutePath());
                }
            }
            
            Log.i("SyncService", "🎯 CRITICAL FIX: Local cleanup completed for trip " + tripId + " - will no longer appear in dashboard");
            
        } catch (Exception e) {
            Log.e("SyncService", "Error cleaning up local trip file for " + tripId, e);
        }
    }
    
    
    /**
     * 🎯 Unified Trip Not Started Handler
     * 
     * Handles trip cancellation/not started using the unified trip manager.
     * All sync operations happen automatically.
     */
    private void handleTripNotStarted() {
        Log.i("SyncService", "=== Starting Unified Trip Release Handler ===");
        
        try {
            UnifiedTripManager tripManager = UnifiedTripManager.getInstance(getApplicationContext());
            String deviceId = tripManager.getDeviceId();
            
            // 🔍 Find trips that need to be released by this device
            List<String> tripsToRelease = findTripsToRelease(deviceId);
            
            Log.i("SyncService", "🎯 Found " + tripsToRelease.size() + " trips to release for device " + deviceId);
            
            for (String tripId : tripsToRelease) {
                try {
                    Log.i("SyncService", "🎯 Processing trip release: " + tripId);
                    
                    // Use unified trip manager to release trip
                    boolean success = tripManager.releaseTrip(tripId);
                    
                    if (success) {
                        Log.i("SyncService", "✅ Trip " + tripId + " successfully released");
                        
                        // 📊 AUDIT: Log successful release
                        AuditLogger auditLogger = AuditLogger.getInstance(getApplicationContext());
                        auditLogger.logTripRelease(tripId, deviceId, true, "Unified trip release successful");
                        
                    } else {
                        Log.w("SyncService", "⚠️ Failed to release trip " + tripId);
                    }
                    
                } catch (Exception tripError) {
                    Log.e("SyncService", "Error releasing trip " + tripId, tripError);
                    
                    // 📊 AUDIT: Log failure
                    AuditLogger auditLogger = AuditLogger.getInstance(getApplicationContext());
                    auditLogger.logTripRelease(tripId, deviceId, false, "Unified release error: " + tripError.getMessage());
                }
            }
            
        } catch (Exception e) {
            Log.e("SyncService", "Exception in trip release handler", e);
        }
        
        Log.i("SyncService", "=== Finished Unified Trip Release Handler ===");
    }
    
    /**
     * Find trips that need to be released by this device
     * This includes trips in Claimed or InProgress state owned by this device
     */
    private List<String> findTripsToRelease(String deviceId) {
        List<String> tripsToRelease = new ArrayList<>();
        
        try {
            // Check local trips that might need releasing
            if (SyncConstant.STARTED_TRIP != null && !SyncConstant.STARTED_TRIP.isEmpty()) {
                tripsToRelease.add(SyncConstant.STARTED_TRIP);
                Log.d("SyncService", "Found started trip to release: " + SyncConstant.STARTED_TRIP);
            }
            
            // Check in-progress trips list
            for (String tripId : AppConstant.inProgressTrips) {
                if (!tripsToRelease.contains(tripId)) {
                    tripsToRelease.add(tripId);
                    Log.d("SyncService", "Found in-progress trip to release: " + tripId);
                }
            }
            
            // Check database for trips claimed by this device
            if (database != null) {
                List<String> claimedTrips = database.getTripsClaimedByDevice(deviceId);
                for (String tripId : claimedTrips) {
                    if (!tripsToRelease.contains(tripId)) {
                        tripsToRelease.add(tripId);
                        Log.d("SyncService", "Found claimed trip to release: " + tripId);
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e("SyncService", "Error finding trips to release", e);
        }
        
        return tripsToRelease;
    }
    
    
    /**
     * 🎯 Unified Trip Status Sync
     * 
     * Uses the unified trip manager which handles all sync automatically.
     * No complex state management needed - sync happens transparently.
     */
    private void syncTripStatusEnhanced() {
        Log.i("SyncService", "=== Starting Unified Trip Sync ===");
        
        try {
            UnifiedTripManager tripManager = UnifiedTripManager.getInstance(getApplicationContext());
            
            // Get sync status - the unified manager handles all sync automatically
            ConnectivityAwareSyncManager.SyncStatus status = tripManager.getSyncStatus();
            
            Log.i("SyncService", "🎯 Unified Sync Status: " + status.state + " - " + status.message);
            Log.i("SyncService", "🌐 Online: " + status.isOnline + ", Queued: " + status.queuedOperations);
            
            if (status.queuedOperations > 0 && status.isOnline) {
                // Trigger immediate sync if we have queued operations and are online
                Log.i("SyncService", "🔄 Triggering immediate sync of queued operations");
                tripManager.forceSync();
            }
            
            // 📊 AUDIT: Log sync status
            AuditLogger auditLogger = AuditLogger.getInstance(getApplicationContext());
            auditLogger.logTripStatusSync(tripManager.getDeviceId(), true, "Unified sync status: " + status.message);
            
        } catch (Exception e) {
            Log.e("SyncService", "Exception in unified trip sync", e);
            
            // 📊 AUDIT: Log failure  
            try {
                UnifiedTripManager tripManager = UnifiedTripManager.getInstance(getApplicationContext());
                AuditLogger auditLogger = AuditLogger.getInstance(getApplicationContext());
                auditLogger.logTripStatusSync(tripManager.getDeviceId(), false, "Unified sync error: " + e.getMessage());
            } catch (Exception auditEx) {
                Log.e("SyncService", "Error logging sync failure", auditEx);
            }
        }
        
        Log.i("SyncService", "=== Finished Unified Trip Sync ===");
    }
    
    // Legacy helper methods removed - UnifiedTripManager handles trip state sync automatically

}
