package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.clone.EasyDelivery.Activity.TripDash;
import com.clone.EasyDelivery.Database.DeliveryDb;
import com.clone.EasyDelivery.Model.Delivery;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class ScheduleHelper {

    private static int documentQty;

    static boolean internetConnected;

    public static void getSchedule(Context context, String trip) {

        parseAndInsertScheduleData(context, trip);
    }

    private static void parseAndInsertScheduleData(Context context, String trip) {

        Delivery delivery = new Delivery();

        JSONObject jsonData = JsonHandler.readFile(context, trip);

        documentQty = 0;

        if (jsonData != null) {

            try {

                AppConstant.documentList.clear();

                //Continue parsing and inserting data

                JSONArray stops = jsonData.getJSONArray("stops");

                for (int i = 0; i < stops.length(); i++) {

                    JSONObject stop = stops.getJSONObject(i);
                    String documentNumber = stop.getString("documentNumber");
                    AppConstant.documentList.add(documentNumber);

                    JSONObject customer = stop.getJSONObject("customer");
                    String customerName = customer.getString("name");
                    String customerContactName = customer.getString("contactName");
                    String customerContact = customer.getString("contactNumber");

                    String address = stop.getString("address");

                    JSONObject gpsLocation = stop.getJSONObject("gpsLocation");
                    double latitude = gpsLocation.getDouble("latitude");
                    double longitude = gpsLocation.getDouble("longitude");

                    Location location = new Location("");
                    location.setLongitude(longitude);
                    location.setLatitude(latitude);

                    int numParcels = stop.getInt("numParcels");

                    JSONArray parcelNumbers = stop.getJSONArray("parcelNumbers");

                    List<String> parcelList = new ArrayList<>();

                    for (int j = 0; j < parcelNumbers.length(); j++) {

                        String parcelNumber = parcelNumbers.getString(j);

                        parcelList.add(parcelNumber);
                    }

                    delivery.setDocument(documentNumber);
                    delivery.setTripId(AppConstant.TRIPID);
                    delivery.setCustomerName(customerName);
                    delivery.setAddress(address);
                    delivery.setContactName(customerContactName);
                    delivery.setContactNumber(customerContact);
                    delivery.setLocation(location);
                    delivery.setNumberOfParcels(numParcels);
                    delivery.setCompleted(false);
                    delivery.setUploaded(false);
                    delivery.setParcelNumbers(parcelList);

                    insertScheduleData(context, delivery);

                    documentQty++;
                }

                DeliveryDb database = new DeliveryDb(context);

                database.open();

                database.createSyncEntry(AppConstant.TRIPID, documentQty);

                database.close();

            } catch (Exception e) {

                e.printStackTrace();

                //ToastLogger.exception(context, e);
            }
        }
    }


    private static void insertScheduleData(Context context, Delivery delivery) {
        try {
            DeliveryDb database = new DeliveryDb(context);

            database.open();

            //Check if document exists first.

            if (!documentValid(database, delivery.getDocument(), false)) {

                database.createScheduleEntry(delivery);

                Log.i("Document Table", "Document inserted.");

                for (String parcel : delivery.getParcelNumbers()) {

                    database.createParcelEntry(parcel, delivery.getDocument(), delivery.getTripId());

                    Log.i("Parcel Table", "Parcel inserted.");
                }
            }

            database.close();

        } catch (Exception e) {

            throw new RuntimeException(e);
        }
    }


    public static boolean documentValid(DeliveryDb database, String document, boolean isIncompleteDocument) {

        List<String> documentList = database.getDocumentList(isIncompleteDocument);

        for (int i = 0; i < documentList.size(); i++) {

            if (document.equals(documentList.get(i))) {

                Log.i("Document Table", "Document " + document + " already exists.");

                return true;
            }
        }

        return false;
    }


    public static ArrayList<String> getLocalTrips(Context context) {
        ArrayList<String> finalTripList = new ArrayList<>();
        try {
            Log.i("Trip List", "Fetching local trips...");

            File tripDir = new File(context.getFilesDir() + "/Trip/");
            if (!tripDir.exists()) {
                Log.w("Trip List", "Trip directory does not exist.");
                return finalTripList; // Return empty list if directory doesn't exist
            }

            DeliveryDb database = new DeliveryDb(context);
            database.open();

            // 1. Get all local trip files
            String[] tripFiles = tripDir.list();
            if (tripFiles == null) {
                database.close();
                Log.w("Trip List", "No files found in trip directory.");
                return finalTripList; // Return empty list if no files
            }

            // 2. Filter out invalid, completed, or started trips
            for (String fileName : tripFiles) {
                String tripName = fileName.substring(0, fileName.length() - 5);
                File currentFile = new File(tripDir, fileName);

                if (currentFile.length() > 0 && !AppConstant.completedTrips.contains(tripName)) {
                    finalTripList.add(tripName);
                }
            }

            Log.i("Trip List", "Initial local trips before filtering: " + finalTripList.toString());
            // 3. Perform online-only cleanup and filtering
            if (ConnectionHelper.isInternetConnected()) {
                Log.i("Trip List", "Internet connected, performing online cleanup.");
                Log.i("Trip List", "AppConstant.downloadedTrips: " + AppConstant.downloadedTrips.toString());
                Log.i("Trip List", "AppConstant.inProgressTrips: " + AppConstant.inProgressTrips.toString());

                Iterator<String> iterator = finalTripList.iterator();
                while (iterator.hasNext()) {
                    String trip = iterator.next();

                    // Remove trips that are no longer present in the downloaded list from the server
                    if (!AppConstant.downloadedTrips.isEmpty() && !AppConstant.downloadedTrips.contains(trip)) {
                        Log.i("Trip List", "Checking if trip '" + trip + "' should be removed.");
                        if (!database.tripStarted(trip) && !database.tripDataExists(trip)) {
                            Log.i("Trip List", "Removing stale trip: " + trip + " (not started, no data exists, not in downloadedTrips)");
                            deleteTripFile(context, trip);
                            iterator.remove();
                        } else if (!AppConstant.inProgressTrips.contains(trip)) {
                            Log.i("Trip List", "Deleting data for trip: " + trip + " (not in inProgressTrips)");
                            database.deleteData(trip);
                        }
                    }
                }
            }

            database.close();
            Collections.sort(finalTripList);
            Log.i("Trip List", "Found " + finalTripList.size() + " valid trips.");

        } catch (Exception e) {
            e.printStackTrace();
        }
        return finalTripList;
    }


    public static void deleteTripFile(Context context, String tripName) {
        try {

            File file = new File(context.getFilesDir() + "/Trip/", tripName + ".json");

            file.delete();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

}
