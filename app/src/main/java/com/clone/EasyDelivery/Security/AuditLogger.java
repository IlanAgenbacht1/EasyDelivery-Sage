package com.clone.EasyDelivery.Security;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 🔍 AUDIT LOGGER - Comprehensive Security and Compliance Logging
 * 
 * This class provides centralized audit logging for all security-related operations
 * including data access, file operations, email delivery, cleanup, and compliance events.
 * 
 * Features:
 * - Structured JSON audit logs
 * - Asynchronous logging for performance
 * - Automatic log rotation and retention
 * - Compliance-ready audit trails
 * - Security event categorization
 */
public class AuditLogger {
    
    private static final String TAG = "AuditLogger";
    private static final String AUDIT_DIR = "/AuditLogs/";
    private static final long MAX_LOG_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_LOG_FILES = 10; // Keep last 10 files
    private static final long AUDIT_RETENTION_DAYS = 90; // 90 days retention
    
    private static AuditLogger instance;
    private Context context;
    private ExecutorService executorService;
    private ConcurrentLinkedQueue<AuditEvent> eventQueue;
    private SimpleDateFormat dateFormatter;
    private SimpleDateFormat fileNameFormatter;
    
    // Audit Event Categories
    public enum EventCategory {
        DATA_ACCESS("DATA_ACCESS"),
        FILE_OPERATION("FILE_OPERATION"), 
        CLOUD_SYNC("CLOUD_SYNC"),
        EMAIL_DELIVERY("EMAIL_DELIVERY"),
        DATA_CLEANUP("DATA_CLEANUP"),
        SECURITY_OPERATION("SECURITY_OPERATION"),
        COMPLIANCE("COMPLIANCE"),
        ERROR("ERROR");
        
        private final String value;
        EventCategory(String value) { this.value = value; }
        public String getValue() { return value; }
    }
    
    // Audit Event Severity Levels
    public enum EventSeverity {
        INFO("INFO"),
        WARNING("WARNING"), 
        CRITICAL("CRITICAL"),
        ERROR("ERROR");
        
        private final String value;
        EventSeverity(String value) { this.value = value; }
        public String getValue() { return value; }
    }
    
    // Structured Audit Event
    public static class AuditEvent {
        public String eventId;
        public String timestamp;
        public EventCategory category;
        public EventSeverity severity;
        public String operation;
        public String description;
        public String documentId;
        public String tripId;
        public String userId;
        public String deviceId;
        public JSONObject metadata;
        public boolean success;
        public String errorDetails;
        
        public AuditEvent(EventCategory category, EventSeverity severity, String operation, String description) {
            this.eventId = UUID.randomUUID().toString();
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            this.category = category;
            this.severity = severity;
            this.operation = operation;
            this.description = description;
            this.metadata = new JSONObject();
            this.success = true;
        }
    }
    
    private AuditLogger(Context context) {
        this.context = context.getApplicationContext();
        this.executorService = Executors.newSingleThreadExecutor();
        this.eventQueue = new ConcurrentLinkedQueue<>();
        this.dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        this.fileNameFormatter = new SimpleDateFormat("yyyyMMdd", Locale.US);
        
        // Ensure audit directory exists
        createAuditDirectory();
        
        // Start periodic cleanup of old audit files
        scheduleAuditCleanup();
    }
    
    public static synchronized AuditLogger getInstance(Context context) {
        if (instance == null) {
            instance = new AuditLogger(context);
        }
        return instance;
    }
    
    // ========== SECURITY AUDIT METHODS ==========
    
    /**
     * 🔒 Log secure cloud sync operation (metadata-only)
     */
    public void logSecureCloudSync(String tripId, String documentId, boolean success, String details) {
        AuditEvent event = new AuditEvent(EventCategory.CLOUD_SYNC, EventSeverity.INFO, 
            "SECURE_METADATA_UPLOAD", "Secure metadata-only upload to cloud storage");
        event.tripId = tripId;
        event.documentId = documentId;
        event.success = success;
        event.description = details;
        
        try {
            event.metadata.put("upload_type", "metadata_only");
            event.metadata.put("sensitive_data_excluded", true);
            event.metadata.put("security_compliance", "ENHANCED");
        } catch (Exception e) {
            Log.e(TAG, "Error adding metadata to cloud sync audit", e);
        }
        
        logAuditEvent(event);
    }
    
    /**
     * 📧 Log email delivery with PDF generation
     */
    public void logEmailDelivery(String documentId, String tripId, String recipient, boolean success, String details) {
        AuditEvent event = new AuditEvent(EventCategory.EMAIL_DELIVERY, EventSeverity.INFO,
            "PDF_EMAIL_DELIVERY", "Generated PDF and sent via email to customer");
        event.documentId = documentId;
        event.tripId = tripId;
        event.success = success;
        event.description = details;
        
        try {
            event.metadata.put("recipient", maskEmail(recipient));
            event.metadata.put("contains_signature", true);
            event.metadata.put("contains_photo", true);
            event.metadata.put("pdf_generated", true);
            event.metadata.put("temporary_files", true);
        } catch (Exception e) {
            Log.e(TAG, "Error adding metadata to email audit", e);
        }
        
        logAuditEvent(event);
    }
    
    /**
     * 🗑️ Log secure file cleanup after email delivery
     */
    public void logSecureCleanup(String documentId, String tripId, int filesDeleted, boolean success) {
        AuditEvent event = new AuditEvent(EventCategory.DATA_CLEANUP, EventSeverity.INFO,
            "POST_EMAIL_CLEANUP", "Secure cleanup of sensitive files after successful email delivery");
        event.documentId = documentId;
        event.tripId = tripId;
        event.success = success;
        
        try {
            event.metadata.put("files_deleted", filesDeleted);
            event.metadata.put("cleanup_trigger", "successful_email_delivery");
            event.metadata.put("security_policy", "minimize_data_retention");
        } catch (Exception e) {
            Log.e(TAG, "Error adding metadata to cleanup audit", e);
        }
        
        logAuditEvent(event);
    }
    
    /**
     * 📅 Log data retention policy enforcement
     */
    public void logDataRetentionEnforcement(int totalFilesDeleted, long retentionPeriodDays, boolean success) {
        AuditEvent event = new AuditEvent(EventCategory.COMPLIANCE, EventSeverity.INFO,
            "DATA_RETENTION_ENFORCEMENT", "Automatic cleanup of files exceeding retention period");
        event.success = success;
        
        try {
            event.metadata.put("files_deleted", totalFilesDeleted);
            event.metadata.put("retention_period_days", retentionPeriodDays);
            event.metadata.put("policy_type", "automatic_data_lifecycle");
            event.metadata.put("compliance_framework", "POPIA_GDPR");
        } catch (Exception e) {
            Log.e(TAG, "Error adding metadata to retention audit", e);
        }
        
        logAuditEvent(event);
    }
    
    /**
     * 🔐 Log file encryption/decryption operations
     */
    public void logSecurityOperation(String operation, String documentId, boolean success, String details) {
        AuditEvent event = new AuditEvent(EventCategory.SECURITY_OPERATION, EventSeverity.INFO,
            operation, "Security operation on sensitive data");
        event.documentId = documentId;
        event.success = success;
        event.description = details;
        
        try {
            event.metadata.put("security_manager_used", true);
            event.metadata.put("hardware_backed", true);
        } catch (Exception e) {
            Log.e(TAG, "Error adding metadata to security audit", e);
        }
        
        logAuditEvent(event);
    }
    
    /**
     * 🚨 Log security violations or errors
     */
    public void logSecurityViolation(String violation, String documentId, String details) {
        AuditEvent event = new AuditEvent(EventCategory.ERROR, EventSeverity.CRITICAL,
            "SECURITY_VIOLATION", violation);
        event.documentId = documentId;
        event.success = false;
        event.errorDetails = details;
        
        logAuditEvent(event);
    }
    
    /**
     * 📊 Log data access events
     */
    public void logDataAccess(String documentId, String tripId, String operation, boolean success) {
        AuditEvent event = new AuditEvent(EventCategory.DATA_ACCESS, EventSeverity.INFO,
            operation, "Access to customer delivery data");
        event.documentId = documentId;
        event.tripId = tripId;
        event.success = success;
        
        logAuditEvent(event);
    }
    
    /**
     * 🚚 Log trip completion events
     */
    public void logTripCompletion(String tripId, String deviceId, boolean success, String details) {
        AuditEvent event = new AuditEvent(EventCategory.DATA_ACCESS, EventSeverity.INFO,
            "TRIP_COMPLETION", "Trip marked as completed by device");
        event.tripId = tripId;
        event.deviceId = deviceId;
        event.success = success;
        event.description = details;
        
        try {
            event.metadata.put("operation_type", "state_transition");
            event.metadata.put("from_state", "IN_PROGRESS");
            event.metadata.put("to_state", "COMPLETED");
        } catch (Exception e) {
            Log.e(TAG, "Error adding trip completion metadata", e);
        }
        
        logAuditEvent(event);
    }
    
    /**
     * 🔓 Log trip release events
     */
    public void logTripRelease(String tripId, String deviceId, boolean success, String details) {
        AuditEvent event = new AuditEvent(EventCategory.DATA_ACCESS, EventSeverity.INFO,
            "TRIP_RELEASE", "Trip released back to available state");
        event.tripId = tripId;
        event.deviceId = deviceId;
        event.success = success;
        event.description = details;
        
        try {
            event.metadata.put("operation_type", "state_transition");
            event.metadata.put("from_state", "CLAIMED_OR_IN_PROGRESS");
            event.metadata.put("to_state", "AVAILABLE");
        } catch (Exception e) {
            Log.e(TAG, "Error adding trip release metadata", e);
        }
        
        logAuditEvent(event);
    }
    
    /**
     * 🔄 Log trip status synchronization events
     */
    public void logTripStatusSync(String deviceId, boolean success, String details) {
        AuditEvent event = new AuditEvent(EventCategory.CLOUD_SYNC, EventSeverity.INFO,
            "TRIP_STATUS_SYNC", "Synchronization of trip status from cloud");
        event.deviceId = deviceId;
        event.success = success;
        event.description = details;
        
        try {
            event.metadata.put("sync_type", "enhanced_state_management");
            event.metadata.put("atomic_operations", true);
        } catch (Exception e) {
            Log.e(TAG, "Error adding trip sync metadata", e);
        }
        
        logAuditEvent(event);
    }
    
    // ========== CORE AUDIT FUNCTIONALITY ==========
    
    private void logAuditEvent(AuditEvent event) {
        // Set common fields
        event.userId = getCurrentUserId();
        event.deviceId = getDeviceId();
        
        // Add to queue for asynchronous processing
        eventQueue.offer(event);
        
        // Process queue asynchronously
        executorService.submit(this::processAuditQueue);
        
        // Also log to Android system log for immediate visibility
        String logMessage = String.format("[AUDIT] %s | %s | %s | %s | Doc:%s | Success:%s", 
            event.category.getValue(), event.operation, event.description, 
            event.timestamp, event.documentId != null ? event.documentId : "N/A", event.success);
        Log.i("SecurityAudit", logMessage);
    }
    
    private void processAuditQueue() {
        AuditEvent event;
        while ((event = eventQueue.poll()) != null) {
            writeAuditEventToFile(event);
        }
    }
    
    private void writeAuditEventToFile(AuditEvent event) {
        try {
            String fileName = "audit_" + fileNameFormatter.format(new Date()) + ".log";
            File auditFile = new File(context.getFilesDir() + AUDIT_DIR, fileName);
            
            // Check if file needs rotation
            if (auditFile.exists() && auditFile.length() > MAX_LOG_FILE_SIZE) {
                rotateAuditLog();
                auditFile = new File(context.getFilesDir() + AUDIT_DIR, fileName);
            }
            
            // Convert event to JSON
            JSONObject auditJson = eventToJson(event);
            
            // Write to file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(auditFile, true))) {
                writer.write(auditJson.toString());
                writer.newLine();
                writer.flush();
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to write audit event to file", e);
        }
    }
    
    private JSONObject eventToJson(AuditEvent event) {
        JSONObject json = new JSONObject();
        try {
            json.put("eventId", event.eventId);
            json.put("timestamp", event.timestamp);
            json.put("category", event.category.getValue());
            json.put("severity", event.severity.getValue());
            json.put("operation", event.operation);
            json.put("description", event.description);
            json.put("documentId", event.documentId);
            json.put("tripId", event.tripId);
            json.put("userId", event.userId);
            json.put("deviceId", event.deviceId);
            json.put("success", event.success);
            json.put("errorDetails", event.errorDetails);
            json.put("metadata", event.metadata);
        } catch (Exception e) {
            Log.e(TAG, "Error converting audit event to JSON", e);
        }
        return json;
    }
    
    // ========== AUDIT MANAGEMENT ==========
    
    private void createAuditDirectory() {
        try {
            File auditDir = new File(context.getFilesDir() + AUDIT_DIR);
            if (!auditDir.exists()) {
                boolean created = auditDir.mkdirs();
                Log.i(TAG, "Audit directory created: " + created);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create audit directory", e);
        }
    }
    
    private void rotateAuditLog() {
        try {
            File auditDir = new File(context.getFilesDir() + AUDIT_DIR);
            File[] files = auditDir.listFiles((dir, name) -> name.startsWith("audit_") && name.endsWith(".log"));
            
            if (files != null && files.length >= MAX_LOG_FILES) {
                // Sort by last modified and delete oldest
                java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
                for (int i = 0; i < files.length - MAX_LOG_FILES + 1; i++) {
                    boolean deleted = files[i].delete();
                    Log.d(TAG, "Rotated audit log: " + files[i].getName() + ", deleted: " + deleted);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error rotating audit logs", e);
        }
    }
    
    private void scheduleAuditCleanup() {
        // This would typically be called periodically by a background service
        // For now, we'll clean up old files when AuditLogger is initialized
        cleanupOldAuditFiles();
    }
    
    private void cleanupOldAuditFiles() {
        try {
            long cutoffTime = System.currentTimeMillis() - (AUDIT_RETENTION_DAYS * 24 * 60 * 60 * 1000L);
            File auditDir = new File(context.getFilesDir() + AUDIT_DIR);
            File[] files = auditDir.listFiles();
            
            if (files != null) {
                int deletedCount = 0;
                for (File file : files) {
                    if (file.lastModified() < cutoffTime) {
                        boolean deleted = file.delete();
                        if (deleted) deletedCount++;
                    }
                }
                Log.i(TAG, "Cleaned up " + deletedCount + " old audit files");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up old audit files", e);
        }
    }
    
    // ========== HELPER METHODS ==========
    
    private String getCurrentUserId() {
        // In a real implementation, get current user ID
        return "SYSTEM_USER";
    }
    
    private String getDeviceId() {
        // Get device ID securely
        return android.provider.Settings.Secure.getString(
            context.getContentResolver(), 
            android.provider.Settings.Secure.ANDROID_ID);
    }
    
    private String maskEmail(String email) {
        if (email == null || email.length() < 3) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            String prefix = email.substring(0, Math.min(2, atIndex));
            String domain = email.substring(atIndex);
            return prefix + "***" + domain;
        }
        return email.substring(0, 2) + "***";
    }
    
    // ========== AUDIT REPORTING ==========
    
    /**
     * Get audit events for compliance reporting
     */
    public JSONArray getAuditEventsForPeriod(Date startDate, Date endDate, EventCategory category) {
        JSONArray events = new JSONArray();
        // Implementation would read from audit files and filter by date/category
        // This is a placeholder for the reporting functionality
        return events;
    }
    
    /**
     * Log emergency unlock operations (CRITICAL for production monitoring)
     */
    public void logEmergencyUnlock(String tripId, long stuckDuration, String reason) {
        try {
            JSONObject auditEntry = new JSONObject();
            auditEntry.put("type", "emergency_unlock");
            auditEntry.put("trip_id", tripId);
            auditEntry.put("stuck_duration_ms", stuckDuration);
            auditEntry.put("reason", reason);
            auditEntry.put("timestamp", System.currentTimeMillis());
            auditEntry.put("device_id", getDeviceId());
            
            // Convert JSONObject to AuditEvent format and write
            AuditEvent emergencyEvent = new AuditEvent(EventCategory.SECURITY_OPERATION, EventSeverity.CRITICAL, 
                "EMERGENCY_UNLOCK", "Emergency unlock of stuck trip due to system failure");
            emergencyEvent.tripId = tripId;
            try {
                emergencyEvent.metadata.put("stuck_duration_ms", stuckDuration);
                emergencyEvent.metadata.put("reason", reason);
            } catch (Exception metaEx) {
                Log.w(TAG, "Failed to add emergency unlock metadata", metaEx);
            }
            logAuditEvent(emergencyEvent);
            
            // Also log as high-priority event
            Log.w("AUDIT_EMERGENCY", "🚨 EMERGENCY UNLOCK: " + tripId + " (stuck " + (stuckDuration/1000/60) + "min) - " + reason);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to log emergency unlock event", e);
        }
    }
    
    /**
     * Generate security compliance report
     */
    public JSONObject generateComplianceReport(Date startDate, Date endDate) {
        JSONObject report = new JSONObject();
        try {
            report.put("reportPeriod", startDate + " to " + endDate);
            report.put("secureUploads", 0); // Count from audit logs
            report.put("emailDeliveries", 0); // Count from audit logs
            report.put("cleanupOperations", 0); // Count from audit logs
            report.put("retentionEnforcements", 0); // Count from audit logs
            report.put("securityViolations", 0); // Count from audit logs
        } catch (Exception e) {
            Log.e(TAG, "Error generating compliance report", e);
        }
        return report;
    }
}
