package com.clone.DeliveryApp.Model;

import android.location.Location;

import java.util.List;

public class Delivery {

    private String tripId;
    private String document;
    private String customerName;
    private String address;
    private String contactName;
    private String contactNumber;

    private Location location;
    private double latitude;
    private double longitude;
    private int numberOfParcels;
    private List<String> parcelNumbers;
    private boolean completed, uploaded;
    private String time;
    private String imagePath;
    private String signPath;

    // Getters and setters

    public String getTripId() {return tripId; }
    public void setTripId(String tripId) {this.tripId = tripId; }

    public String getDocument() {return document; }
    public void setDocument(String document) {this.document = document; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) {this.contactName = contactName; }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public int getNumberOfParcels() { return numberOfParcels; }
    public void setNumberOfParcels(int numberOfParcels) { this.numberOfParcels = numberOfParcels; }

    public List<String> getParcelNumbers() { return parcelNumbers; }
    public void setParcelNumbers(List<String> parcelNumbers) { this.parcelNumbers = parcelNumbers; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getImagePath() { return imagePath; }

    public void setSignPath(String signPath) { this.signPath = signPath; }
    public String getSignPath() { return  signPath; }

    public boolean completed() {
        return completed;
    }
    public void setCompleted(boolean complete) { this.completed = complete; }

    public boolean uploaded() { return uploaded; }
    public void setUploaded(boolean uploaded) { this.uploaded = uploaded; }
}
