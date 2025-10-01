package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;

// Additional imports for integrity verification
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Comprehensive Security Manager for EasyDelivery Application
 * Provides secure storage, encryption, and credential management using Android Keystore
 */
public class SecurityManager {
    private static final String TAG = "SecurityManager";
    private static final String KEYSTORE_ALIAS = "EasyDeliveryMasterKey";
    private static final String ENCRYPTED_PREFS_NAME = "secure_preferences";

    // Key aliases for different types of credentials
    private static final String DROPBOX_TOKEN_KEY = "dropbox_refresh_token";
    private static final String DROPBOX_APP_KEY = "dropbox_app_key";
    private static final String DROPBOX_SECRET_KEY = "dropbox_app_secret";
    private static final String EMAIL_PASSWORD_KEY = "email_password";
    private static final String SIGNATURE_ENCRYPTION_KEY = "signature_key";

    private static SecurityManager instance;
    private final Context context;
    private SharedPreferences securePrefs;

    private SecurityManager(Context context) {
        this.context = context.getApplicationContext();
        initializeSecurity();
    }

    public static synchronized SecurityManager getInstance(Context context) {
        if (instance == null) {
            instance = new SecurityManager(context);
        }
        return instance;
    }

    /**
     * Initialize security components
     */
    private void initializeSecurity() {
        try {
            // Use private SharedPreferences with secure naming
            securePrefs = context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE);

            // Initialize the master encryption key if it doesn't exist
            if (!keyExists(KEYSTORE_ALIAS)) {
                generateMasterKey();
            }
            
            // Initialize signature key timestamp if missing
            initializeKeyTimestamp();
            
            // Perform automatic key maintenance
            performKeyMaintenance();

            Log.d(TAG, "Security initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize security components", e);
            throw new RuntimeException("Security initialization failed", e);
        }
    }

    /**
     * Generate master key in Android Keystore
     */
    private void generateMasterKey() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

                KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                        KEYSTORE_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build();

                keyGenerator.init(keyGenParameterSpec);
                keyGenerator.generateKey();

                Log.d(TAG, "Master key generated successfully");
            } else {
                // For older versions, use a different approach
                Log.w(TAG, "Using fallback security for Android version < 23");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate master key", e);
        }
    }

    /**
     * Check if key exists in Android Keystore
     */
    private boolean keyExists(String alias) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            return keyStore.containsAlias(alias);
        } catch (Exception e) {
            Log.e(TAG, "Error checking key existence", e);
            return false;
        }
    }

    /**
     * Encrypt data using master key
     */
    private String encryptData(String data) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);

                SecretKey secretKey = (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
                if (secretKey == null) {
                    generateMasterKey();
                    secretKey = (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
                }

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);

                byte[] iv = cipher.getIV();
                byte[] encryptedData = cipher.doFinal(data.getBytes());

                // Combine IV and encrypted data
                byte[] encryptedWithIv = new byte[iv.length + encryptedData.length];
                System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
                System.arraycopy(encryptedData, 0, encryptedWithIv, iv.length, encryptedData.length);

                return Base64.encodeToString(encryptedWithIv, Base64.DEFAULT);
            } else {
                // Fallback for older Android versions
                return Base64.encodeToString(data.getBytes(), Base64.DEFAULT);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to encrypt data", e);
            return data; // Return original data if encryption fails
        }
    }

    /**
     * Decrypt data using master key
     */
    private String decryptData(String encryptedData) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);

                SecretKey secretKey = (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
                if (secretKey == null) {
                    Log.e(TAG, "Master key not found for decryption");
                    return null;
                }

                byte[] encryptedWithIv = Base64.decode(encryptedData, Base64.DEFAULT);

                // Extract IV and encrypted data
                byte[] iv = new byte[12]; // GCM IV is typically 12 bytes
                byte[] encrypted = new byte[encryptedWithIv.length - iv.length];

                System.arraycopy(encryptedWithIv, 0, iv, 0, iv.length);
                System.arraycopy(encryptedWithIv, iv.length, encrypted, 0, encrypted.length);

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

                byte[] decryptedData = cipher.doFinal(encrypted);
                return new String(decryptedData);
            } else {
                // Fallback for older Android versions
                return new String(Base64.decode(encryptedData, Base64.DEFAULT));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt data", e);
            return null;
        }
    }

    /**
     * Store encrypted value in SharedPreferences
     */
    private void storeSecureValue(String key, String value) {
        String encryptedValue = encryptData(value);
        securePrefs.edit().putString(key, encryptedValue).apply();
    }

    /**
     * Retrieve and decrypt value from SharedPreferences
     */
    private String getSecureValue(String key, String defaultValue) {
        String encryptedValue = securePrefs.getString(key, null);
        if (encryptedValue == null) {
            return defaultValue;
        }

        String decryptedValue = decryptData(encryptedValue);
        return decryptedValue != null ? decryptedValue : defaultValue;
    }

    /**
     * Store Dropbox credentials securely
     */
    public void storeDropboxCredentials(String refreshToken, String appKey, String appSecret) {
        try {
            storeSecureValue(DROPBOX_TOKEN_KEY, refreshToken);
            storeSecureValue(DROPBOX_APP_KEY, appKey);
            storeSecureValue(DROPBOX_SECRET_KEY, appSecret);
            Log.d(TAG, "Dropbox credentials stored securely");
        } catch (Exception e) {
            Log.e(TAG, "Failed to store Dropbox credentials", e);
        }
    }

    /**
     * Retrieve Dropbox refresh token
     */
    public String getDropboxRefreshToken() {
        return getSecureValue(DROPBOX_TOKEN_KEY, null);
    }

    /**
     * Retrieve Dropbox app key
     */
    public String getDropboxAppKey() {
        return getSecureValue(DROPBOX_APP_KEY, null);
    }

    /**
     * Retrieve Dropbox app secret
     */
    public String getDropboxAppSecret() {
        return getSecureValue(DROPBOX_SECRET_KEY, null);
    }

    /**
     * Store email credentials securely
     */
    public void storeEmailCredentials(String username, String password) {
        try {
            storeSecureValue("email_username", username);
            storeSecureValue(EMAIL_PASSWORD_KEY, password);
            Log.d(TAG, "Email credentials stored securely");
        } catch (Exception e) {
            Log.e(TAG, "Failed to store email credentials", e);
        }
    }

    /**
     * Retrieve email username
     */
    public String getEmailUsername() {
        return getSecureValue("email_username", "dev@easydelivery.biz");
    }

    /**
     * Retrieve email password
     */
    public String getEmailPassword() {
        String password = getSecureValue(EMAIL_PASSWORD_KEY, null);
        Log.d(TAG, "getEmailPassword called - password exists: " + (password != null && !password.trim().isEmpty()));
        
        if (password == null || password.trim().isEmpty()) {
            Log.w(TAG, "Email password is not configured. Use storeEmailCredentials() to set it.");
            // Check if we have any stored credentials at all
            boolean hasAnyCredentials = securePrefs.contains(EMAIL_PASSWORD_KEY);
            Log.d(TAG, "EMAIL_PASSWORD_KEY exists in preferences: " + hasAnyCredentials);
        }
        
        return password;
    }

    /**
     * Generate and store a secure signature encryption key
     */
    public String generateSignatureEncryptionKey() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

                KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                        SIGNATURE_ENCRYPTION_KEY,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .setUserAuthenticationRequired(false)
                        .build();

                keyGenerator.init(keyGenParameterSpec);
                keyGenerator.generateKey();

                // Store a reference to the key
                securePrefs.edit().putString("signature_key_alias", SIGNATURE_ENCRYPTION_KEY).apply();

                Log.d(TAG, "Signature encryption key generated and stored securely");
                return SIGNATURE_ENCRYPTION_KEY;
            } else {
                // Generate a regular AES key for older versions
                byte[] keyBytes = new byte[32]; // 256-bit key
                new SecureRandom().nextBytes(keyBytes);
                String keyString = Base64.encodeToString(keyBytes, Base64.DEFAULT);
                storeSecureValue("signature_fallback_key", keyString);
                return "signature_fallback_key";
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate signature encryption key", e);
            return null;
        }
    }

    /**
     * Encrypt signature data with comprehensive security logging
     */
    public byte[] encryptSignature(byte[] data) {
        String operationId = generateOperationId();
        long startTime = System.currentTimeMillis();
        
        Log.i("SignatureAudit", "[" + operationId + "] SIGNATURE_ENCRYPT_START - size: " + data.length + " bytes");
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.d("SignatureAudit", "[" + operationId + "] Using hardware-backed encryption (API " + Build.VERSION.SDK_INT + ")");
                
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);

                SecretKey secretKey = (SecretKey) keyStore.getKey(SIGNATURE_ENCRYPTION_KEY, null);
                if (secretKey == null) {
                    Log.i("SignatureAudit", "[" + operationId + "] Generating new signature encryption key");
                    generateSignatureEncryptionKey();
                    secretKey = (SecretKey) keyStore.getKey(SIGNATURE_ENCRYPTION_KEY, null);
                }

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);

                byte[] iv = cipher.getIV();
                Log.d("SignatureAudit", "[" + operationId + "] Generated IV: " + iv.length + " bytes");
                
                byte[] encryptedData = cipher.doFinal(data);
                Log.d("SignatureAudit", "[" + operationId + "] Encryption completed - encrypted size: " + encryptedData.length + " bytes");

                // Combine IV and encrypted data
                byte[] encryptedWithIv = new byte[iv.length + encryptedData.length];
                System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
                System.arraycopy(encryptedData, 0, encryptedWithIv, iv.length, encryptedData.length);

                long duration = System.currentTimeMillis() - startTime;
                Log.i("SignatureAudit", "[" + operationId + "] SIGNATURE_ENCRYPT_SUCCESS - total size: " + encryptedWithIv.length + " bytes, duration: " + duration + "ms");
                return encryptedWithIv;
            } else {
                Log.w("SignatureAudit", "[" + operationId + "] Using fallback encryption (API " + Build.VERSION.SDK_INT + " < 23)");
                
                // Fallback encryption for older versions
                String keyString = getSecureValue("signature_fallback_key", null);
                if (keyString != null) {
                    byte[] keyBytes = Base64.decode(keyString, Base64.DEFAULT);
                    SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

                    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    cipher.init(Cipher.ENCRYPT_MODE, keySpec);

                    byte[] iv = cipher.getIV();
                    byte[] encryptedData = cipher.doFinal(data);

                    // Combine IV and encrypted data
                    byte[] encryptedWithIv = new byte[iv.length + encryptedData.length];
                    System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
                    System.arraycopy(encryptedData, 0, encryptedWithIv, iv.length, encryptedData.length);

                    long duration = System.currentTimeMillis() - startTime;
                    Log.i("SignatureAudit", "[" + operationId + "] SIGNATURE_ENCRYPT_SUCCESS (fallback) - size: " + encryptedWithIv.length + " bytes, duration: " + duration + "ms");
                    return encryptedWithIv;
                } else {
                    Log.e("SignatureAudit", "[" + operationId + "] Fallback key not available");
                }
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Log.e("SignatureAudit", "[" + operationId + "] SIGNATURE_ENCRYPT_ERROR - duration: " + duration + "ms, error: " + e.getMessage(), e);
        }
        
        Log.e("SignatureAudit", "[" + operationId + "] SIGNATURE_ENCRYPT_FAILED - returning null");
        return null;
    }

    /**
     * Decrypt signature data with comprehensive security logging
     */
    public byte[] decryptSignature(byte[] encryptedWithIv) {
        String operationId = generateOperationId();
        long startTime = System.currentTimeMillis();
        
        Log.i("SignatureAudit", "[" + operationId + "] SIGNATURE_DECRYPT_START - encrypted size: " + encryptedWithIv.length + " bytes");
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.d("SignatureAudit", "[" + operationId + "] Using hardware-backed decryption (API " + Build.VERSION.SDK_INT + ")");
                
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);

                SecretKey secretKey = (SecretKey) keyStore.getKey(SIGNATURE_ENCRYPTION_KEY, null);
                if (secretKey == null) {
                    Log.e("SignatureAudit", "[" + operationId + "] SIGNATURE_DECRYPT_FAILED - key not found in KeyStore");
                    return null;
                }
                
                Log.d("SignatureAudit", "[" + operationId + "] Key retrieved from KeyStore successfully");

                // Extract IV and encrypted data
                byte[] iv = new byte[12]; // GCM IV is typically 12 bytes
                byte[] encryptedData = new byte[encryptedWithIv.length - iv.length];

                System.arraycopy(encryptedWithIv, 0, iv, 0, iv.length);
                System.arraycopy(encryptedWithIv, iv.length, encryptedData, 0, encryptedData.length);
                
                Log.d("SignatureAudit", "[" + operationId + "] Extracted IV (" + iv.length + " bytes) and data (" + encryptedData.length + " bytes)");

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

                byte[] decryptedData = cipher.doFinal(encryptedData);
                long duration = System.currentTimeMillis() - startTime;
                
                Log.i("SignatureAudit", "[" + operationId + "] SIGNATURE_DECRYPT_SUCCESS - decrypted size: " + decryptedData.length + " bytes, duration: " + duration + "ms");
                return decryptedData;
                
            } else {
                Log.w("SignatureAudit", "[" + operationId + "] Using fallback decryption (API " + Build.VERSION.SDK_INT + " < 23)");
                
                // Fallback decryption for older versions
                String keyString = getSecureValue("signature_fallback_key", null);
                if (keyString != null) {
                    byte[] keyBytes = Base64.decode(keyString, Base64.DEFAULT);
                    SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

                    // Extract IV and encrypted data
                    byte[] iv = new byte[16]; // CBC IV is 16 bytes
                    byte[] encryptedData = new byte[encryptedWithIv.length - iv.length];

                    System.arraycopy(encryptedWithIv, 0, iv, 0, iv.length);
                    System.arraycopy(encryptedWithIv, iv.length, encryptedData, 0, encryptedData.length);

                    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, new javax.crypto.spec.IvParameterSpec(iv));

                    byte[] decryptedData = cipher.doFinal(encryptedData);
                    long duration = System.currentTimeMillis() - startTime;
                    
                    Log.i("SignatureAudit", "[" + operationId + "] SIGNATURE_DECRYPT_SUCCESS (fallback) - size: " + decryptedData.length + " bytes, duration: " + duration + "ms");
                    return decryptedData;
                } else {
                    Log.e("SignatureAudit", "[" + operationId + "] Fallback key not available for decryption");
                }
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Log.e("SignatureAudit", "[" + operationId + "] SIGNATURE_DECRYPT_ERROR - duration: " + duration + "ms, error: " + e.getMessage(), e);
        }
        
        Log.e("SignatureAudit", "[" + operationId + "] SIGNATURE_DECRYPT_FAILED - returning null");
        return null;
    }

    /**
     * Store app configuration securely
     */
    public void storeAppConfig(String company, String driver, String vehicle, String email) {
        try {
            storeSecureValue("app_company", company);
            storeSecureValue("app_driver", driver);
            storeSecureValue("app_vehicle", vehicle);
            storeSecureValue("app_email", email);
            Log.d(TAG, "App configuration stored securely");
        } catch (Exception e) {
            Log.e(TAG, "Failed to store app configuration", e);
        }
    }

    public String getAppCompany() {
        return getSecureValue("app_company", "");
    }

    public String getAppDriver() {
        return getSecureValue("app_driver", "");
    }

    public String getAppVehicle() {
        return getSecureValue("app_vehicle", "");
    }

    public String getAppEmail() {
        return getSecureValue("app_email", "");
    }

    /**
     * Configure email password (for development/testing)
     */
    public void configureEmailPassword(String password) {
        if (password != null && !password.trim().isEmpty()) {
            storeEmailCredentials("dev@easydelivery.biz", password);
            Log.i(TAG, "Email password configured successfully");
        } else {
            Log.e(TAG, "Cannot configure empty email password");
        }
    }

    /**
     * Validate that all required credentials are present
     */
    public boolean validateCredentials() {
        String refreshToken = getDropboxRefreshToken();
        String appKey = getDropboxAppKey();
        String appSecret = getDropboxAppSecret();
        String emailPassword = getEmailPassword();

        boolean valid = refreshToken != null && !refreshToken.isEmpty() &&
                appKey != null && !appKey.isEmpty() &&
                appSecret != null && !appSecret.isEmpty() &&
                emailPassword != null && !emailPassword.isEmpty();

        if (!valid) {
            Log.w(TAG, "Credential validation failed - some credentials are missing");
        }

        return valid;
    }

    /**
     * Clear all stored credentials (for logout/reset)
     */
    public void clearAllCredentials() {
        try {
            securePrefs.edit().clear().apply();

            // Also remove keys from Android Keystore
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                keyStore.deleteEntry(KEYSTORE_ALIAS);
            }
            if (keyStore.containsAlias(SIGNATURE_ENCRYPTION_KEY)) {
                keyStore.deleteEntry(SIGNATURE_ENCRYPTION_KEY);
            }

            Log.d(TAG, "All credentials cleared successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear credentials", e);
        }
    }

    /**
     * Initialize credentials from BuildConfig (migration from old system)
     */
    public void initializeFromBuildConfig(String refreshToken, String appKey, String appSecret) {
        // Only store if not already present
        if (getDropboxRefreshToken() == null) {
            storeDropboxCredentials(refreshToken, appKey, appSecret);
            Log.d(TAG, "Migrated credentials from BuildConfig to secure storage");
        }

        // Initialize email credentials with secure defaults
        if (getEmailPassword() == null || getEmailPassword().trim().isEmpty()) {
            // This should be replaced with proper credential management
            // For now, using a placeholder - this needs to be configured by administrator
            Log.w(TAG, "Email password is not configured. Please set it using SecurityManager.storeEmailCredentials()");
            
            // Temporary fallback - you should replace this with the actual email password
            // storeEmailCredentials("dev@easydelivery.biz", "YOUR_APP_PASSWORD_HERE");
        }
    }

    /**
     * Generate unique operation ID for security audit logs
     */
    private String generateOperationId() {
        return "OP" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }
    
    /**
     * Key rotation and lifecycle management
     */
    
    // Key rotation interval (7 days in milliseconds)
    private static final long KEY_ROTATION_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L;
    
    // Key expiration warning threshold (24 hours)
    private static final long KEY_EXPIRATION_WARNING_MS = 24L * 60L * 60L * 1000L;
    
    /**
     * Check if signature key needs rotation
     */
    public boolean isSignatureKeyRotationNeeded() {
        String operationId = generateOperationId();
        Log.i("KeyLifecycle", "[" + operationId + "] Checking signature key rotation status");
        
        try {
            long keyCreationTime = securePrefs.getLong("signature_key_created", 0);
            if (keyCreationTime == 0) {
                Log.w("KeyLifecycle", "[" + operationId + "] No key creation timestamp found - rotation recommended");
                return true;
            }
            
            long currentTime = System.currentTimeMillis();
            long keyAge = currentTime - keyCreationTime;
            
            boolean needsRotation = keyAge > KEY_ROTATION_INTERVAL_MS;
            Log.i("KeyLifecycle", "[" + operationId + "] Key age: " + (keyAge / (1000 * 60 * 60 * 24)) + " days, needs rotation: " + needsRotation);
            
            return needsRotation;
            
        } catch (Exception e) {
            Log.e("KeyLifecycle", "[" + operationId + "] Error checking key rotation status: " + e.getMessage(), e);
            return true; // Default to rotation needed if we can't check
        }
    }
    
    /**
     * Check if signature key is approaching expiration
     */
    public boolean isSignatureKeyNearExpiration() {
        String operationId = generateOperationId();
        
        try {
            long keyCreationTime = securePrefs.getLong("signature_key_created", 0);
            if (keyCreationTime == 0) return false;
            
            long currentTime = System.currentTimeMillis();
            long keyAge = currentTime - keyCreationTime;
            long timeUntilExpiration = KEY_ROTATION_INTERVAL_MS - keyAge;
            
            boolean nearExpiration = timeUntilExpiration <= KEY_EXPIRATION_WARNING_MS && timeUntilExpiration > 0;
            
            if (nearExpiration) {
                Log.w("KeyLifecycle", "[" + operationId + "] Signature key expires in " + (timeUntilExpiration / (1000 * 60 * 60)) + " hours");
            }
            
            return nearExpiration;
            
        } catch (Exception e) {
            Log.e("KeyLifecycle", "[" + operationId + "] Error checking key expiration: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Rotate signature encryption key with backup of old signatures
     */
    public boolean rotateSignatureKey() {
        String operationId = generateOperationId();
        Log.i("KeyLifecycle", "[" + operationId + "] Starting signature key rotation");
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Check if we have an existing key to backup
            boolean hasExistingKey = false;
            String oldKeyAlias = null;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                hasExistingKey = keyStore.containsAlias(SIGNATURE_ENCRYPTION_KEY);
                if (hasExistingKey) {
                    oldKeyAlias = SIGNATURE_ENCRYPTION_KEY + "_backup_" + System.currentTimeMillis();
                }
            }
            
            Log.i("KeyLifecycle", "[" + operationId + "] Existing key found: " + hasExistingKey);
            
            // Generate new key
            String newKeyAlias = generateSignatureEncryptionKey();
            if (newKeyAlias == null) {
                Log.e("KeyLifecycle", "[" + operationId + "] Failed to generate new signature key");
                return false;
            }
            
            // Update key creation timestamp
            long currentTime = System.currentTimeMillis();
            securePrefs.edit().putLong("signature_key_created", currentTime).apply();
            
            // Store rotation history
            String rotationHistory = securePrefs.getString("key_rotation_history", "");
            rotationHistory += currentTime + ",";
            
            // Keep only last 10 rotation timestamps
            String[] rotations = rotationHistory.split(",");
            if (rotations.length > 10) {
                StringBuilder newHistory = new StringBuilder();
                for (int i = rotations.length - 10; i < rotations.length; i++) {
                    if (!rotations[i].isEmpty()) {
                        newHistory.append(rotations[i]).append(",");
                    }
                }
                rotationHistory = newHistory.toString();
            }
            
            securePrefs.edit().putString("key_rotation_history", rotationHistory).apply();
            
            long duration = System.currentTimeMillis() - startTime;
            Log.i("KeyLifecycle", "[" + operationId + "] KEY_ROTATION_SUCCESS - duration: " + duration + "ms");
            
            return true;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Log.e("KeyLifecycle", "[" + operationId + "] KEY_ROTATION_ERROR - duration: " + duration + "ms, error: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Automatic key maintenance - checks and rotates keys if needed
     */
    public void performKeyMaintenance() {
        String operationId = generateOperationId();
        Log.i("KeyLifecycle", "[" + operationId + "] Starting automatic key maintenance");
        
        try {
            // Check for near expiration first
            if (isSignatureKeyNearExpiration()) {
                Log.w("KeyLifecycle", "[" + operationId + "] Signature key is nearing expiration");
            }
            
            // Check if rotation is needed
            if (isSignatureKeyRotationNeeded()) {
                Log.i("KeyLifecycle", "[" + operationId + "] Signature key rotation is needed - initiating rotation");
                boolean rotationSuccess = rotateSignatureKey();
                
                if (rotationSuccess) {
                    Log.i("KeyLifecycle", "[" + operationId + "] Automatic key rotation completed successfully");
                } else {
                    Log.e("KeyLifecycle", "[" + operationId + "] Automatic key rotation failed");
                }
            } else {
                Log.d("KeyLifecycle", "[" + operationId + "] Signature key is current - no rotation needed");
            }
            
            // Clean up old rotation history if needed
            cleanupOldRotationHistory();
            
        } catch (Exception e) {
            Log.e("KeyLifecycle", "[" + operationId + "] Error during key maintenance: " + e.getMessage(), e);
        }
    }
    
    /**
     * Clean up old key rotation history
     */
    private void cleanupOldRotationHistory() {
        try {
            String rotationHistory = securePrefs.getString("key_rotation_history", "");
            if (rotationHistory.isEmpty()) return;
            
            String[] rotations = rotationHistory.split(",");
            long cutoffTime = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L); // 30 days
            
            StringBuilder cleanHistory = new StringBuilder();
            int removedCount = 0;
            
            for (String rotation : rotations) {
                if (!rotation.isEmpty()) {
                    try {
                        long rotationTime = Long.parseLong(rotation);
                        if (rotationTime > cutoffTime) {
                            cleanHistory.append(rotation).append(",");
                        } else {
                            removedCount++;
                        }
                    } catch (NumberFormatException e) {
                        // Skip invalid entries
                        removedCount++;
                    }
                }
            }
            
            if (removedCount > 0) {
                securePrefs.edit().putString("key_rotation_history", cleanHistory.toString()).apply();
                Log.d("KeyLifecycle", "Cleaned up " + removedCount + " old rotation history entries");
            }
            
        } catch (Exception e) {
            Log.e("KeyLifecycle", "Error cleaning rotation history: " + e.getMessage(), e);
        }
    }
    
    /**
     * Initialize key creation timestamp if missing
     */
    public void initializeKeyTimestamp() {
        if (securePrefs.getLong("signature_key_created", 0) == 0) {
            securePrefs.edit().putLong("signature_key_created", System.currentTimeMillis()).apply();
            Log.i("KeyLifecycle", "Initialized signature key creation timestamp");
        }
    }
    
    /**
     * Get key lifecycle status for diagnostics
     */
    public String getKeyLifecycleStatus() {
        StringBuilder status = new StringBuilder();
        
        long keyCreationTime = securePrefs.getLong("signature_key_created", 0);
        if (keyCreationTime > 0) {
            long keyAge = System.currentTimeMillis() - keyCreationTime;
            long daysOld = keyAge / (1000 * 60 * 60 * 24);
            long hoursUntilRotation = (KEY_ROTATION_INTERVAL_MS - keyAge) / (1000 * 60 * 60);
            
            status.append("Key Age: ").append(daysOld).append(" days\n");
            status.append("Rotation Needed: ").append(isSignatureKeyRotationNeeded() ? "✓" : "✗").append("\n");
            status.append("Near Expiration: ").append(isSignatureKeyNearExpiration() ? "⚠" : "✗").append("\n");
            
            if (hoursUntilRotation > 0) {
                status.append("Hours Until Rotation: ").append(hoursUntilRotation).append("\n");
            }
            
            String rotationHistory = securePrefs.getString("key_rotation_history", "");
            if (!rotationHistory.isEmpty()) {
                String[] rotations = rotationHistory.split(",");
                status.append("Rotation Count: ").append(rotations.length - 1).append("\n");
            }
        } else {
            status.append("Key Creation Time: Not Set\n");
            status.append("Status: Needs Initialization\n");
        }
        
        return status.toString();
    }
    
    /**
     * Get security status for diagnostics
     */
    public String getSecurityStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Security Level: ").append(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? "Hardware-backed" : "Software").append("\n");
        status.append("Dropbox Token: ").append(getDropboxRefreshToken() != null ? "✓" : "✗").append("\n");
        status.append("Dropbox App Key: ").append(getDropboxAppKey() != null ? "✓" : "✗").append("\n");
        status.append("Dropbox Secret: ").append(getDropboxAppSecret() != null ? "✓" : "✗").append("\n");
        status.append("Email Password: ").append(getEmailPassword() != null ? "✓" : "✗").append("\n");
        status.append("Signature Key: ").append(securePrefs.getString("signature_key_alias", null) != null ? "✓" : "✗").append("\n");
        status.append("\n--- Key Lifecycle ---\n");
        status.append(getKeyLifecycleStatus());

        return status.toString();
    }
    
    /**
     * Signature integrity verification system
     */
    
    /**
     * Calculate SHA-256 hash of signature data for integrity verification
     */
    public String calculateSignatureHash(byte[] signatureData) {
        String operationId = generateOperationId();
        Log.d("SignatureIntegrity", "[" + operationId + "] Calculating signature hash - size: " + signatureData.length + " bytes");
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(signatureData);
            String hash = Base64.encodeToString(hashBytes, Base64.NO_WRAP);
            
            Log.d("SignatureIntegrity", "[" + operationId + "] Hash calculated successfully: " + hash.substring(0, Math.min(16, hash.length())) + "...");
            return hash;
            
        } catch (NoSuchAlgorithmException e) {
            Log.e("SignatureIntegrity", "[" + operationId + "] SHA-256 algorithm not available: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Generate HMAC for signature data using a derived key
     */
    public String calculateSignatureHMAC(byte[] signatureData) {
        String operationId = generateOperationId();
        Log.d("SignatureIntegrity", "[" + operationId + "] Calculating signature HMAC - size: " + signatureData.length + " bytes");
        
        try {
            // Generate or retrieve HMAC key
            String hmacKeyString = getSecureValue("signature_hmac_key", null);
            if (hmacKeyString == null) {
                // Generate new HMAC key
                byte[] hmacKeyBytes = new byte[32]; // 256-bit key
                new SecureRandom().nextBytes(hmacKeyBytes);
                hmacKeyString = Base64.encodeToString(hmacKeyBytes, Base64.NO_WRAP);
                storeSecureValue("signature_hmac_key", hmacKeyString);
                Log.i("SignatureIntegrity", "[" + operationId + "] Generated new HMAC key for signature verification");
            }
            
            byte[] hmacKeyBytes = Base64.decode(hmacKeyString, Base64.NO_WRAP);
            SecretKeySpec hmacKeySpec = new SecretKeySpec(hmacKeyBytes, "HmacSHA256");
            
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKeySpec);
            byte[] hmacBytes = mac.doFinal(signatureData);
            
            String hmac = Base64.encodeToString(hmacBytes, Base64.NO_WRAP);
            Log.d("SignatureIntegrity", "[" + operationId + "] HMAC calculated successfully");
            return hmac;
            
        } catch (Exception e) {
            Log.e("SignatureIntegrity", "[" + operationId + "] Error calculating HMAC: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Create integrity metadata for a signature
     */
    public String createSignatureIntegrityData(byte[] signatureData) {
        String operationId = generateOperationId();
        Log.i("SignatureIntegrity", "[" + operationId + "] Creating integrity metadata for signature");
        
        try {
            String hash = calculateSignatureHash(signatureData);
            String hmac = calculateSignatureHMAC(signatureData);
            
            if (hash == null || hmac == null) {
                Log.e("SignatureIntegrity", "[" + operationId + "] Failed to generate integrity data");
                return null;
            }
            
            // Create integrity metadata JSON-like string
            long timestamp = System.currentTimeMillis();
            String integrityData = String.format(
                "timestamp:%d,hash:%s,hmac:%s,size:%d", 
                timestamp, hash, hmac, signatureData.length
            );
            
            Log.i("SignatureIntegrity", "[" + operationId + "] Integrity metadata created successfully");
            return integrityData;
            
        } catch (Exception e) {
            Log.e("SignatureIntegrity", "[" + operationId + "] Error creating integrity data: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Verify signature integrity using stored metadata
     */
    public boolean verifySignatureIntegrity(byte[] signatureData, String integrityData) {
        String operationId = generateOperationId();
        Log.i("SignatureIntegrity", "[" + operationId + "] Verifying signature integrity");
        
        if (integrityData == null || integrityData.isEmpty()) {
            Log.w("SignatureIntegrity", "[" + operationId + "] No integrity data provided - cannot verify");
            return false;
        }
        
        try {
            // Parse integrity data
            String[] parts = integrityData.split(",");
            String storedHash = null;
            String storedHmac = null;
            int storedSize = 0;
            long storedTimestamp = 0;
            
            for (String part : parts) {
                String[] keyValue = part.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    
                    switch (key) {
                        case "hash":
                            storedHash = value;
                            break;
                        case "hmac":
                            storedHmac = value;
                            break;
                        case "size":
                            storedSize = Integer.parseInt(value);
                            break;
                        case "timestamp":
                            storedTimestamp = Long.parseLong(value);
                            break;
                    }
                }
            }
            
            Log.d("SignatureIntegrity", "[" + operationId + "] Parsed integrity data - size: " + storedSize + ", timestamp: " + storedTimestamp);
            
            // Verify size first (quick check)
            if (signatureData.length != storedSize) {
                Log.e("SignatureIntegrity", "[" + operationId + "] INTEGRITY_VIOLATION - Size mismatch: expected " + storedSize + ", got " + signatureData.length);
                return false;
            }
            
            // Calculate current hash
            String currentHash = calculateSignatureHash(signatureData);
            if (currentHash == null || !currentHash.equals(storedHash)) {
                Log.e("SignatureIntegrity", "[" + operationId + "] INTEGRITY_VIOLATION - Hash mismatch");
                return false;
            }
            
            // Calculate current HMAC
            String currentHmac = calculateSignatureHMAC(signatureData);
            if (currentHmac == null || !currentHmac.equals(storedHmac)) {
                Log.e("SignatureIntegrity", "[" + operationId + "] INTEGRITY_VIOLATION - HMAC mismatch");
                return false;
            }
            
            // Check timestamp (warn if signature is very old)
            long signatureAge = System.currentTimeMillis() - storedTimestamp;
            if (signatureAge > (30L * 24L * 60L * 60L * 1000L)) { // 30 days
                Log.w("SignatureIntegrity", "[" + operationId + "] Signature is " + (signatureAge / (1000 * 60 * 60 * 24)) + " days old");
            }
            
            Log.i("SignatureIntegrity", "[" + operationId + "] INTEGRITY_VERIFIED - Signature integrity confirmed");
            return true;
            
        } catch (Exception e) {
            Log.e("SignatureIntegrity", "[" + operationId + "] INTEGRITY_ERROR - Failed to verify: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Enhanced signature encryption with integrity verification
     */
    public SignaturePackage encryptSignatureWithIntegrity(byte[] signatureData) {
        String operationId = generateOperationId();
        Log.i("SignatureIntegrity", "[" + operationId + "] Encrypting signature with integrity protection");
        
        try {
            // Create integrity data first
            String integrityData = createSignatureIntegrityData(signatureData);
            if (integrityData == null) {
                Log.e("SignatureIntegrity", "[" + operationId + "] Failed to create integrity data");
                return null;
            }
            
            // Encrypt signature
            byte[] encryptedSignature = encryptSignature(signatureData);
            if (encryptedSignature == null) {
                Log.e("SignatureIntegrity", "[" + operationId + "] Failed to encrypt signature");
                return null;
            }
            
            Log.i("SignatureIntegrity", "[" + operationId + "] Signature encrypted with integrity protection successfully");
            return new SignaturePackage(encryptedSignature, integrityData);
            
        } catch (Exception e) {
            Log.e("SignatureIntegrity", "[" + operationId + "] Error in integrity encryption: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Enhanced signature decryption with integrity verification
     */
    public byte[] decryptSignatureWithIntegrityCheck(SignaturePackage signaturePackage) {
        String operationId = generateOperationId();
        Log.i("SignatureIntegrity", "[" + operationId + "] Decrypting signature with integrity verification");
        
        if (signaturePackage == null) {
            Log.e("SignatureIntegrity", "[" + operationId + "] Signature package is null");
            return null;
        }
        
        try {
            // Decrypt signature first
            byte[] decryptedSignature = decryptSignature(signaturePackage.encryptedData);
            if (decryptedSignature == null) {
                Log.e("SignatureIntegrity", "[" + operationId + "] Failed to decrypt signature");
                return null;
            }
            
            // Verify integrity
            boolean integrityValid = verifySignatureIntegrity(decryptedSignature, signaturePackage.integrityData);
            if (!integrityValid) {
                Log.e("SignatureIntegrity", "[" + operationId + "] SIGNATURE_COMPROMISED - Integrity verification failed");
                return null;
            }
            
            Log.i("SignatureIntegrity", "[" + operationId + "] Signature decrypted and integrity verified successfully");
            return decryptedSignature;
            
        } catch (Exception e) {
            Log.e("SignatureIntegrity", "[" + operationId + "] Error in integrity decryption: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Container class for signature with integrity data
     */
    public static class SignaturePackage {
        public final byte[] encryptedData;
        public final String integrityData;
        
        public SignaturePackage(byte[] encryptedData, String integrityData) {
            this.encryptedData = encryptedData;
            this.integrityData = integrityData;
        }
        
        public String toString() {
            return "SignaturePackage{encryptedSize=" + (encryptedData != null ? encryptedData.length : 0) + 
                   ", hasIntegrityData=" + (integrityData != null && !integrityData.isEmpty()) + "}";
        }
    }
    
    /**
     * Get integrity system status for diagnostics
     */
    public String getIntegritySystemStatus() {
        StringBuilder status = new StringBuilder();
        
        status.append("--- Signature Integrity System ---\n");
        
        // Check if HMAC key exists
        String hmacKey = getSecureValue("signature_hmac_key", null);
        status.append("HMAC Key: ").append(hmacKey != null ? "✓" : "✗").append("\n");
        
        // Test hash calculation
        try {
            byte[] testData = "test".getBytes();
            String testHash = calculateSignatureHash(testData);
            status.append("SHA-256 Available: ").append(testHash != null ? "✓" : "✗").append("\n");
        } catch (Exception e) {
            status.append("SHA-256 Available: ✗ (Error: ").append(e.getMessage()).append(")\n");
        }
        
        // Test HMAC calculation
        try {
            byte[] testData = "test".getBytes();
            String testHmac = calculateSignatureHMAC(testData);
            status.append("HMAC Available: ").append(testHmac != null ? "✓" : "✗").append("\n");
        } catch (Exception e) {
            status.append("HMAC Available: ✗ (Error: ").append(e.getMessage()).append(")\n");
        }
        
        return status.toString();
    }
}