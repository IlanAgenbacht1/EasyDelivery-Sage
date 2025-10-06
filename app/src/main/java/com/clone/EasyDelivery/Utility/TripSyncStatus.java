package com.clone.EasyDelivery.Utility;

/**
 * TripSyncStatus - Represents the synchronization status of a trip
 * 
 * This class provides detailed information about the sync state of a trip,
 * useful for displaying progress indicators and status information in the UI.
 */
public class TripSyncStatus {
    
    private final String tripId;
    private final boolean fullySynced;
    private final String statusMessage;
    private final int syncedDocuments;
    private final int totalDocuments;
    
    /**
     * Create a new TripSyncStatus
     * 
     * @param tripId The trip ID this status applies to
     * @param fullySynced Whether all data for this trip is fully synced
     * @param statusMessage Human-readable status message
     * @param syncedDocuments Number of documents that have been synced
     * @param totalDocuments Total number of documents in this trip
     */
    public TripSyncStatus(String tripId, boolean fullySynced, String statusMessage, 
                         int syncedDocuments, int totalDocuments) {
        this.tripId = tripId;
        this.fullySynced = fullySynced;
        this.statusMessage = statusMessage;
        this.syncedDocuments = syncedDocuments;
        this.totalDocuments = totalDocuments;
    }
    
    /**
     * Get the trip ID this status applies to
     */
    public String getTripId() {
        return tripId;
    }
    
    /**
     * Check if the trip is fully synced
     */
    public boolean isFullySynced() {
        return fullySynced;
    }
    
    /**
     * Get human-readable status message
     */
    public String getStatusMessage() {
        return statusMessage;
    }
    
    /**
     * Get number of documents that have been synced
     */
    public int getSyncedDocuments() {
        return syncedDocuments;
    }
    
    /**
     * Get total number of documents in this trip
     */
    public int getTotalDocuments() {
        return totalDocuments;
    }
    
    /**
     * Get sync progress as a percentage (0-100)
     */
    public int getSyncProgressPercentage() {
        if (totalDocuments == 0) {
            return fullySynced ? 100 : 0;
        }
        return (int) Math.round((syncedDocuments * 100.0) / totalDocuments);
    }
    
    /**
     * Check if there are pending documents to sync
     */
    public boolean hasPendingDocuments() {
        return syncedDocuments < totalDocuments;
    }
    
    /**
     * Get a summary string for display purposes
     */
    public String getSummary() {
        if (fullySynced) {
            return "Fully synced (" + totalDocuments + " documents)";
        } else if (totalDocuments == 0) {
            return "No documents to sync";
        } else {
            return syncedDocuments + "/" + totalDocuments + " documents synced (" + getSyncProgressPercentage() + "%)";
        }
    }
    
    @Override
    public String toString() {
        return "TripSyncStatus{" +
                "tripId='" + tripId + '\'' +
                ", fullySynced=" + fullySynced +
                ", statusMessage='" + statusMessage + '\'' +
                ", syncedDocuments=" + syncedDocuments +
                ", totalDocuments=" + totalDocuments +
                ", progress=" + getSyncProgressPercentage() + "%" +
                '}';
    }
}