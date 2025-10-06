package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages versioned encryption keys for signature security
 * This class solves the key rotation problem by maintaining multiple key versions
 * and ensuring old signatures can always be decrypted with their original keys.
 */
public class KeyVersionManager {
    private static final String TAG = "KeyVersionManager";
    private static final String PREFS_NAME = "key_version_prefs";
    
    // Key versioning constants
    private static final String CURRENT_VERSION_KEY = "current_signature_key_version";
    private static final String KEY_PREFIX = "signature_key_v";
    private static final String VERSION_METADATA_PREFIX = "key_metadata_v";
    
    private static KeyVersionManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    
    // Key retention policy - keep keys for 2 years
    private static final long KEY_RETENTION_PERIOD_MS = 2L * 365L * 24L * 60L * 60L * 1000L;
    
    private KeyVersionManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initializeIfNeeded();
    }
    
    public static synchronized KeyVersionManager getInstance(Context context) {
        if (instance == null) {
            instance = new KeyVersionManager(context);
        }
        return instance;
    }
    
    /**
     * Initialize the key version system if not already set up
     * Enhanced to handle app restarts and existing keys
     */
    private void initializeIfNeeded() {
        int currentVersion = getCurrentVersion();
        
        if (currentVersion == 0) {
            // Check if we already have keys in the keystore from previous runs
            List<Integer> existingVersions = getAvailableVersions();
            
            if (!existingVersions.isEmpty()) {
                // App restart scenario - keys exist but current version was lost
                int highestVersion = existingVersions.get(existingVersions.size() - 1);
                setCurrentVersion(highestVersion);
                Log.w(TAG, "🔄 APP RESTART: Restored current version to " + highestVersion + " (found existing keys: " + existingVersions + ")");
            } else {
                // First time setup - create version 1
                createKeyVersion(1);
                setCurrentVersion(1);
                Log.i(TAG, "Initialized key versioning system with version 1");
            }
        } else {
            Log.d(TAG, "Key version system already initialized - current version: " + currentVersion);
        }
    }
    
    /**
     * Get the current active key version
     */
    public int getCurrentVersion() {
        return prefs.getInt(CURRENT_VERSION_KEY, 0);
    }
    
    /**
     * Set the current active key version
     */
    private void setCurrentVersion(int version) {
        prefs.edit().putInt(CURRENT_VERSION_KEY, version).apply();
        Log.i(TAG, "Set current key version to: " + version);
    }
    
    /**
     * Generate a new key version and make it current
     * Old keys are preserved for decryption of historical data
     */
    public synchronized int generateNewKeyVersion() {
        String operationId = generateOperationId();
        Log.i(TAG, "[" + operationId + "] Generating new key version");
        
        try {
            int currentVersion = getCurrentVersion();
            int newVersion = currentVersion + 1;
            
            // Create the new key
            boolean success = createKeyVersion(newVersion);
            if (!success) {
                Log.e(TAG, "[" + operationId + "] Failed to create key version " + newVersion);
                return -1;
            }
            
            // Store metadata for the new key
            storeKeyMetadata(newVersion, System.currentTimeMillis(), "AES-256-GCM");
            
            // Update current version
            setCurrentVersion(newVersion);
            
            Log.i(TAG, "[" + operationId + "] Successfully generated key version " + newVersion);
            return newVersion;
            
        } catch (Exception e) {
            Log.e(TAG, "[" + operationId + "] Error generating new key version", e);
            return -1;
        }
    }
    
    /**
     * Create a specific key version in Android Keystore
     */
    private boolean createKeyVersion(int version) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String keyAlias = KEY_PREFIX + version;
                
                KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                
                KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .setUserAuthenticationRequired(false)
                        .build();
                
                keyGenerator.init(keyGenParameterSpec);
                keyGenerator.generateKey();
                
                Log.d(TAG, "Created key version " + version + " with alias: " + keyAlias);
                return true;
                
            } else {
                Log.w(TAG, "Android version < 23, using fallback key generation");
                // For older versions, could implement software-based key storage
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create key version " + version, e);
            return false;
        }
    }
    
    /**
     * Get a specific key version from Android Keystore
     */
    public SecretKey getKeyVersion(int version) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String keyAlias = KEY_PREFIX + version;
                
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                
                if (!keyStore.containsAlias(keyAlias)) {
                    Log.w(TAG, "Key version " + version + " not found in keystore");
                    return null;
                }
                
                SecretKey key = (SecretKey) keyStore.getKey(keyAlias, null);
                Log.d(TAG, "Retrieved key version " + version);
                return key;
                
            } else {
                Log.w(TAG, "Android version < 23, key retrieval not supported");
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving key version " + version, e);
            return null;
        }
    }
    
    /**
     * Get the current active key
     */
    public SecretKey getCurrentKey() {
        int currentVersion = getCurrentVersion();
        return getKeyVersion(currentVersion);
    }
    
    /**
     * Store metadata for a key version
     */
    private void storeKeyMetadata(int version, long creationTime, String algorithm) {
        String metadataKey = VERSION_METADATA_PREFIX + version;
        String metadata = creationTime + "," + algorithm + "," + Build.VERSION.SDK_INT;
        prefs.edit().putString(metadataKey, metadata).apply();
        
        Log.d(TAG, "Stored metadata for key version " + version);
    }
    
    /**
     * Get metadata for a key version
     */
    public KeyMetadata getKeyMetadata(int version) {
        String metadataKey = VERSION_METADATA_PREFIX + version;
        String metadata = prefs.getString(metadataKey, null);
        
        if (metadata == null) {
            return null;
        }
        
        try {
            String[] parts = metadata.split(",");
            if (parts.length >= 3) {
                long creationTime = Long.parseLong(parts[0]);
                String algorithm = parts[1];
                int androidVersion = Integer.parseInt(parts[2]);
                
                return new KeyMetadata(version, creationTime, algorithm, androidVersion);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing metadata for version " + version, e);
        }
        
        return null;
    }
    
    /**
     * Get list of all available key versions
     */
    public List<Integer> getAvailableVersions() {
        List<Integer> versions = new ArrayList<>();
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                
                for (String alias : java.util.Collections.list(keyStore.aliases())) {
                    if (alias.startsWith(KEY_PREFIX)) {
                        try {
                            int version = Integer.parseInt(alias.substring(KEY_PREFIX.length()));
                            versions.add(version);
                        } catch (NumberFormatException e) {
                            // Ignore malformed aliases
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error listing available versions", e);
        }
        
        java.util.Collections.sort(versions);
        return versions;
    }
    
    /**
     * Check if a specific key version exists
     */
    public boolean hasKeyVersion(int version) {
        return getAvailableVersions().contains(version);
    }
    
    /**
     * Clean up old keys based on retention policy
     * Called during maintenance operations
     */
    public void cleanupOldKeys() {
        String operationId = generateOperationId();
        Log.i(TAG, "[" + operationId + "] Starting cleanup of old keys");
        
        try {
            long currentTime = System.currentTimeMillis();
            List<Integer> versions = getAvailableVersions();
            int currentVersion = getCurrentVersion();
            int keysRemoved = 0;
            
            for (Integer version : versions) {
                // Never remove the current key
                if (version == currentVersion) {
                    continue;
                }
                
                KeyMetadata metadata = getKeyMetadata(version);
                if (metadata != null) {
                    long keyAge = currentTime - metadata.creationTime;
                    
                    if (keyAge > KEY_RETENTION_PERIOD_MS) {
                        if (removeKeyVersion(version)) {
                            keysRemoved++;
                            Log.i(TAG, "[" + operationId + "] Removed expired key version " + version);
                        }
                    }
                }
            }
            
            Log.i(TAG, "[" + operationId + "] Cleanup completed, removed " + keysRemoved + " expired keys");
            
        } catch (Exception e) {
            Log.e(TAG, "[" + operationId + "] Error during key cleanup", e);
        }
    }
    
    /**
     * Remove a specific key version (use with caution)
     */
    private boolean removeKeyVersion(int version) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String keyAlias = KEY_PREFIX + version;
                
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                
                if (keyStore.containsAlias(keyAlias)) {
                    keyStore.deleteEntry(keyAlias);
                    
                    // Remove metadata
                    String metadataKey = VERSION_METADATA_PREFIX + version;
                    prefs.edit().remove(metadataKey).apply();
                    
                    Log.d(TAG, "Removed key version " + version);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing key version " + version, e);
        }
        
        return false;
    }
    
    /**
     * Get diagnostic information about the key version system
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Key Version Manager Status ===\n");
        
        int currentVersion = getCurrentVersion();
        info.append("Current Version: ").append(currentVersion).append("\n");
        
        List<Integer> versions = getAvailableVersions();
        info.append("Available Versions: ").append(versions).append("\n");
        
        info.append("\n--- Version Details ---\n");
        for (Integer version : versions) {
            KeyMetadata metadata = getKeyMetadata(version);
            if (metadata != null) {
                long ageInDays = (System.currentTimeMillis() - metadata.creationTime) / (1000 * 60 * 60 * 24);
                info.append("Version ").append(version).append(": ")
                    .append("Age=").append(ageInDays).append(" days, ")
                    .append("Algorithm=").append(metadata.algorithm).append(", ")
                    .append("Android=").append(metadata.androidVersion).append("\n");
            }
        }
        
        return info.toString();
    }
    
    private String generateOperationId() {
        return "KVM" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }
    
    /**
     * Key metadata container class
     */
    public static class KeyMetadata {
        public final int version;
        public final long creationTime;
        public final String algorithm;
        public final int androidVersion;
        
        public KeyMetadata(int version, long creationTime, String algorithm, int androidVersion) {
            this.version = version;
            this.creationTime = creationTime;
            this.algorithm = algorithm;
            this.androidVersion = androidVersion;
        }
        
        @Override
        public String toString() {
            return "KeyMetadata{version=" + version + ", creationTime=" + creationTime + 
                   ", algorithm='" + algorithm + "', androidVersion=" + androidVersion + "}";
        }
    }
}