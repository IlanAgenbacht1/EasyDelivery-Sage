package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.clone.EasyDelivery.Utility.operations.RetryPolicy;

/**
 * SyncMetrics - Comprehensive metrics and logging for sync operations
 * 
 * This class tracks success rates, failure patterns, retry statistics,
 * and performance metrics for all sync operations.
 */
public class SyncMetrics {
    
    private static final String TAG = "SyncMetrics";
    private static final String PREFS_NAME = "SyncMetrics";
    
    private static SyncMetrics instance;
    private final Context context;
    
    // Real-time metrics
    private final Map<String, AtomicInteger> operationCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> operationSuccesses = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> operationFailures = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> operationRetries = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> operationDurations = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> errorCategories = new ConcurrentHashMap<>();
    
    // Session tracking
    private final AtomicLong sessionStartTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger totalOperations = new AtomicInteger(0);
    private final AtomicInteger totalSuccesses = new AtomicInteger(0);
    private final AtomicInteger totalFailures = new AtomicInteger(0);
    
    private SyncMetrics(Context context) {
        this.context = context.getApplicationContext();
        loadPersistedMetrics();
    }
    
    public static synchronized SyncMetrics getInstance(Context context) {
        if (instance == null) {
            instance = new SyncMetrics(context);
        }
        return instance;
    }
    
    /**
     * Record the start of a sync operation
     */
    public void recordOperationStart(String operationType) {
        try {
            operationCounts.computeIfAbsent(operationType, k -> new AtomicInteger(0)).incrementAndGet();
            totalOperations.incrementAndGet();
            
            Log.v(TAG, "Started " + operationType + " operation");
        } catch (Exception e) {
            Log.e(TAG, "Error recording operation start", e);
        }
    }
    
    /**
     * Record a successful sync operation
     */
    public void recordOperationSuccess(String operationType, long durationMs) {
        try {
            operationSuccesses.computeIfAbsent(operationType, k -> new AtomicInteger(0)).incrementAndGet();
            operationDurations.computeIfAbsent(operationType, k -> new AtomicLong(0)).addAndGet(durationMs);
            totalSuccesses.incrementAndGet();
            
            Log.v(TAG, "Successful " + operationType + " operation (" + durationMs + "ms)");
        } catch (Exception e) {
            Log.e(TAG, "Error recording operation success", e);
        }
    }
    
    /**
     * Record a failed sync operation
     */
    public void recordOperationFailure(String operationType, Throwable error, int retryCount) {
        try {
            operationFailures.computeIfAbsent(operationType, k -> new AtomicInteger(0)).incrementAndGet();
            operationRetries.computeIfAbsent(operationType, k -> new AtomicInteger(0)).addAndGet(retryCount);
            totalFailures.incrementAndGet();
            
            String errorCategory = RetryPolicy.getErrorCategory(error);
            errorCategories.computeIfAbsent(errorCategory, k -> new AtomicInteger(0)).incrementAndGet();
            
            Log.w(TAG, "Failed " + operationType + " operation (retries: " + retryCount + 
                      ", error: " + errorCategory + "): " + 
                      (error != null ? error.getMessage() : "Unknown error"));
        } catch (Exception e) {
            Log.e(TAG, "Error recording operation failure", e);
        }
    }
    
    /**
     * Record dead letter queue operation
     */
    public void recordDeadLetterOperation(String operationType, String reason) {
        try {
            operationFailures.computeIfAbsent("dead_letter_" + operationType, k -> new AtomicInteger(0)).incrementAndGet();
            
            Log.w(TAG, "Operation sent to dead letter queue - Type: " + operationType + ", Reason: " + reason);
        } catch (Exception e) {
            Log.e(TAG, "Error recording dead letter operation", e);
        }
    }
    
    /**
     * Get success rate for an operation type
     */
    public double getSuccessRate(String operationType) {
        int successes = operationSuccesses.getOrDefault(operationType, new AtomicInteger(0)).get();
        int total = operationCounts.getOrDefault(operationType, new AtomicInteger(0)).get();
        
        if (total == 0) {
            return 0.0;
        }
        
        return (double) successes / total * 100.0;
    }
    
    /**
     * Get average duration for an operation type
     */
    public double getAverageDuration(String operationType) {
        long totalDuration = operationDurations.getOrDefault(operationType, new AtomicLong(0)).get();
        int successes = operationSuccesses.getOrDefault(operationType, new AtomicInteger(0)).get();
        
        if (successes == 0) {
            return 0.0;
        }
        
        return (double) totalDuration / successes;
    }
    
    /**
     * Get comprehensive metrics report
     */
    public String getMetricsReport() {
        StringBuilder report = new StringBuilder();
        
        try {
            long sessionDuration = System.currentTimeMillis() - sessionStartTime.get();
            double sessionHours = sessionDuration / (1000.0 * 60.0 * 60.0);
            
            report.append("📊 Sync Metrics Report\n");
            report.append("==========================================\n");
            report.append(String.format("Session Duration: %.1f hours\n", sessionHours));
            report.append(String.format("Total Operations: %d\n", totalOperations.get()));
            report.append(String.format("Total Successes: %d\n", totalSuccesses.get()));
            report.append(String.format("Total Failures: %d\n", totalFailures.get()));
            
            if (totalOperations.get() > 0) {
                double overallSuccessRate = (double) totalSuccesses.get() / totalOperations.get() * 100.0;
                report.append(String.format("Overall Success Rate: %.1f%%\n", overallSuccessRate));
            }
            
            report.append("\n📈 Operation Breakdown:\n");
            report.append("------------------------------------------\n");
            
            for (String operationType : operationCounts.keySet()) {
                int total = operationCounts.get(operationType).get();
                int successes = operationSuccesses.getOrDefault(operationType, new AtomicInteger(0)).get();
                int failures = operationFailures.getOrDefault(operationType, new AtomicInteger(0)).get();
                int retries = operationRetries.getOrDefault(operationType, new AtomicInteger(0)).get();
                
                double successRate = getSuccessRate(operationType);
                double avgDuration = getAverageDuration(operationType);
                
                report.append(String.format("%s:\n", operationType));
                report.append(String.format("  Total: %d, Success: %d, Failed: %d\n", total, successes, failures));
                report.append(String.format("  Success Rate: %.1f%%, Avg Duration: %.0fms\n", successRate, avgDuration));
                report.append(String.format("  Total Retries: %d\n", retries));
                report.append("\n");
            }
            
            report.append("🚨 Error Categories:\n");
            report.append("------------------------------------------\n");
            
            for (Map.Entry<String, AtomicInteger> entry : errorCategories.entrySet()) {
                report.append(String.format("%s: %d occurrences\n", entry.getKey(), entry.getValue().get()));
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating metrics report", e);
            report.append("Error generating metrics report: ").append(e.getMessage());
        }
        
        return report.toString();
    }
    
    /**
     * Get metrics as JSON for external analysis
     */
    public JSONObject getMetricsAsJson() {
        try {
            JSONObject metrics = new JSONObject();
            
            metrics.put("session_start_time", sessionStartTime.get());
            metrics.put("session_duration_ms", System.currentTimeMillis() - sessionStartTime.get());
            metrics.put("total_operations", totalOperations.get());
            metrics.put("total_successes", totalSuccesses.get());
            metrics.put("total_failures", totalFailures.get());
            
            JSONObject operationMetrics = new JSONObject();
            for (String operationType : operationCounts.keySet()) {
                JSONObject opMetrics = new JSONObject();
                opMetrics.put("total", operationCounts.get(operationType).get());
                opMetrics.put("successes", operationSuccesses.getOrDefault(operationType, new AtomicInteger(0)).get());
                opMetrics.put("failures", operationFailures.getOrDefault(operationType, new AtomicInteger(0)).get());
                opMetrics.put("retries", operationRetries.getOrDefault(operationType, new AtomicInteger(0)).get());
                opMetrics.put("success_rate", getSuccessRate(operationType));
                opMetrics.put("avg_duration_ms", getAverageDuration(operationType));
                
                operationMetrics.put(operationType, opMetrics);
            }
            metrics.put("operations", operationMetrics);
            
            JSONObject errorMetrics = new JSONObject();
            for (Map.Entry<String, AtomicInteger> entry : errorCategories.entrySet()) {
                errorMetrics.put(entry.getKey(), entry.getValue().get());
            }
            metrics.put("error_categories", errorMetrics);
            
            return metrics;
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating JSON metrics", e);
            return new JSONObject();
        }
    }
    
    /**
     * Reset all metrics (useful for testing or new sessions)
     */
    public void resetMetrics() {
        try {
            operationCounts.clear();
            operationSuccesses.clear();
            operationFailures.clear();
            operationRetries.clear();
            operationDurations.clear();
            errorCategories.clear();
            
            sessionStartTime.set(System.currentTimeMillis());
            totalOperations.set(0);
            totalSuccesses.set(0);
            totalFailures.set(0);
            
            Log.i(TAG, "Metrics reset");
        } catch (Exception e) {
            Log.e(TAG, "Error resetting metrics", e);
        }
    }
    
    /**
     * Save metrics to persistent storage
     */
    public void saveMetrics() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            // Save basic counters
            editor.putInt("total_operations", totalOperations.get());
            editor.putInt("total_successes", totalSuccesses.get());
            editor.putInt("total_failures", totalFailures.get());
            editor.putLong("session_start_time", sessionStartTime.get());
            
            // Save as JSON for complex data
            editor.putString("metrics_json", getMetricsAsJson().toString());
            
            editor.apply();
            
            Log.d(TAG, "Metrics saved to persistent storage");
        } catch (Exception e) {
            Log.e(TAG, "Error saving metrics", e);
        }
    }
    
    /**
     * Load metrics from persistent storage
     */
    private void loadPersistedMetrics() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            totalOperations.set(prefs.getInt("total_operations", 0));
            totalSuccesses.set(prefs.getInt("total_successes", 0));
            totalFailures.set(prefs.getInt("total_failures", 0));
            sessionStartTime.set(prefs.getLong("session_start_time", System.currentTimeMillis()));
            
            Log.d(TAG, "Loaded persisted metrics - Total ops: " + totalOperations.get());
        } catch (Exception e) {
            Log.e(TAG, "Error loading persisted metrics", e);
        }
    }
}
