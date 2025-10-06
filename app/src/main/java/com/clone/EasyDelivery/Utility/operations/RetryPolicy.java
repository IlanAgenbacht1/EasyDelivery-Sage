package com.clone.EasyDelivery.Utility.operations;

import android.util.Log;

/**
 * RetryPolicy - Defines retry strategies for different types of sync operations
 * 
 * This class implements various retry policies including exponential backoff,
 * operation-specific retry limits, and dead letter queue handling.
 */
public class RetryPolicy {
    
    private static final String TAG = "RetryPolicy";
    
    /**
     * Policy types for different operations
     */
    public enum PolicyType {
        CRITICAL,    // For critical operations like trip claiming/releasing
        NORMAL,      // For standard operations like delivery data sync
        EMAIL,       // For email operations with special retry logic
        LOW_PRIORITY // For non-critical operations
    }
    
    private final PolicyType policyType;
    private final int maxRetries;
    private final long initialDelayMs;
    private final double backoffMultiplier;
    private final long maxDelayMs;
    private final boolean allowDeadLetter;
    
    // Pre-defined retry policies
    public static final RetryPolicy CRITICAL = new RetryPolicy(
        PolicyType.CRITICAL, 
        5,      // max retries
        1000,   // 1 second initial delay
        2.0,    // exponential backoff
        60000,  // 1 minute max delay
        false   // never dead letter critical operations
    );
    
    public static final RetryPolicy NORMAL = new RetryPolicy(
        PolicyType.NORMAL,
        3,      // max retries
        2000,   // 2 second initial delay
        1.5,    // moderate backoff
        30000,  // 30 second max delay
        true    // allow dead letter after max retries
    );
    
    public static final RetryPolicy EMAIL = new RetryPolicy(
        PolicyType.EMAIL,
        10,     // max retries (emails are important)
        5000,   // 5 second initial delay
        1.2,    // gentle backoff
        300000, // 5 minute max delay
        false   // never dead letter emails, always retry
    );
    
    public static final RetryPolicy LOW_PRIORITY = new RetryPolicy(
        PolicyType.LOW_PRIORITY,
        2,      // max retries
        10000,  // 10 second initial delay
        1.0,    // no backoff
        10000,  // constant delay
        true    // allow dead letter quickly
    );
    
    private RetryPolicy(PolicyType policyType, int maxRetries, long initialDelayMs, 
                       double backoffMultiplier, long maxDelayMs, boolean allowDeadLetter) {
        this.policyType = policyType;
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelayMs = maxDelayMs;
        this.allowDeadLetter = allowDeadLetter;
    }
    
    /**
     * Calculate delay before next retry attempt
     */
    public long getRetryDelay(int retryAttempt) {
        if (retryAttempt <= 0) {
            return 0;
        }
        
        double delay = initialDelayMs * Math.pow(backoffMultiplier, retryAttempt - 1);
        return Math.min((long) delay, maxDelayMs);
    }
    
    /**
     * Check if operation should be retried
     */
    public boolean shouldRetry(int currentRetries, Throwable error) {
        if (currentRetries >= maxRetries) {
            Log.w(TAG, "Max retries reached (" + maxRetries + ") for " + policyType + " operation");
            return false;
        }
        
        // Check for non-retryable errors
        if (isNonRetryableError(error)) {
            Log.w(TAG, "Non-retryable error for " + policyType + " operation: " + error.getClass().getSimpleName());
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if operation should go to dead letter queue
     */
    public boolean shouldDeadLetter(int currentRetries, Throwable error) {
        if (!allowDeadLetter) {
            return false;
        }
        
        // Dead letter if we've exceeded retries and it's not a critical operation
        return currentRetries >= maxRetries;
    }
    
    /**
     * Determine if an error is non-retryable (permanent failure)
     */
    private boolean isNonRetryableError(Throwable error) {
        if (error == null) {
            return false;
        }
        
        String errorMessage = error.getMessage();
        if (errorMessage == null) {
            return false;
        }
        
        errorMessage = errorMessage.toLowerCase();
        
        // Authentication/authorization errors are typically non-retryable
        if (errorMessage.contains("unauthorized") || 
            errorMessage.contains("forbidden") ||
            errorMessage.contains("authentication") ||
            errorMessage.contains("invalid_token")) {
            return true;
        }
        
        // File not found errors for uploads are non-retryable
        if (errorMessage.contains("file not found") ||
            errorMessage.contains("no such file")) {
            return true;
        }
        
        // Malformed request errors are non-retryable
        if (errorMessage.contains("bad request") ||
            errorMessage.contains("malformed") ||
            errorMessage.contains("invalid format")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get retry policy for operation type
     */
    public static RetryPolicy getPolicyForOperation(String operationType) {
        if (operationType == null) {
            return NORMAL;
        }
        
        switch (operationType.toLowerCase()) {
            case "claim_trip":
            case "start_trip":
            case "complete_trip":
            case "release_trip":
                return CRITICAL;
                
            case "send_email":
                return EMAIL;
                
            case "sync_delivery_data":
            case "sync_returns":
            case "update_trip_status":
                return NORMAL;
                
            case "download_trips":
                return LOW_PRIORITY;
                
            default:
                Log.d(TAG, "Unknown operation type: " + operationType + ", using NORMAL policy");
                return NORMAL;
        }
    }
    
    /**
     * Get error category for metrics and logging
     */
    public static String getErrorCategory(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        
        String className = error.getClass().getSimpleName().toLowerCase();
        
        if (className.contains("network") || className.contains("connection")) {
            return "network";
        } else if (className.contains("timeout")) {
            return "timeout";
        } else if (className.contains("auth")) {
            return "authentication";
        } else if (className.contains("io") || className.contains("file")) {
            return "io";
        } else if (className.contains("json") || className.contains("parse")) {
            return "data_format";
        } else {
            return "application";
        }
    }
    
    // Getters
    public PolicyType getPolicyType() { return policyType; }
    public int getMaxRetries() { return maxRetries; }
    public long getInitialDelayMs() { return initialDelayMs; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public long getMaxDelayMs() { return maxDelayMs; }
    public boolean isAllowDeadLetter() { return allowDeadLetter; }
    
    @Override
    public String toString() {
        return "RetryPolicy{" +
                "type=" + policyType +
                ", maxRetries=" + maxRetries +
                ", initialDelay=" + initialDelayMs + "ms" +
                ", backoffMultiplier=" + backoffMultiplier +
                ", maxDelay=" + maxDelayMs + "ms" +
                ", allowDeadLetter=" + allowDeadLetter +
                '}';
    }
}