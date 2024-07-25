package com.clone.DeliveryApp.Utility;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.clone.DeliveryApp.Database.DeliveryDb;
import com.clone.DeliveryApp.Model.Delivery;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ScheduleHelper {

    public static void getSchedule(Context context, String companyName, String tripNumber) {

        //downloadSchedule(context, companyName, tripNumber);
        parseAndInsertScheduleData(context);
    }

    private static void parseAndInsertScheduleData(Context context) {

        Delivery delivery = new Delivery();

        JSONObject jsonData = JsonHandler.readFile(context);

        if (jsonData != null) {

            try {

                //Set TripId for this delivery

                String tripId = jsonData.getString("tripId");

                AppConstant.TRIPID = tripId;

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

                    JSONObject address = stop.getJSONObject("address");
                    String street = address.getString("street");
                    String city = address.getString("city");
                    String state = address.getString("state");
                    String postalCode = address.getString("postalCode");
                    String country = address.getString("country");

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
                    delivery.setTripId(tripId);
                    delivery.setCustomerName(customerName);
                    delivery.setAddress(street + ", " + city + ", " + state + ", " + postalCode + ", " + country);
                    delivery.setContactName(customerContactName);
                    delivery.setContactNumber(customerContact);
                    delivery.setLocation(location);
                    delivery.setNumberOfParcels(numParcels);
                    delivery.setCompleted(false);

                    delivery.setParcelNumbers(parcelList);

                    insertScheduleData(context, delivery);
                }

            } catch (JSONException e) {

                e.printStackTrace();
            }
        }
    }


    private static void insertScheduleData(Context context, Delivery delivery) {

        try {
            DeliveryDb database = new DeliveryDb(context);

            database.open();

            //Check if document exists first.

            if (!documentExists(database, delivery.getDocument(), false)) {

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


    public static boolean documentExists(DeliveryDb database, String document, boolean isIncompleteDocument) {

        List<String> documentList = database.getDocumentList(isIncompleteDocument);

        for (int i = 0; i < documentList.size(); i++) {

            if (document.equals(documentList.get(i))) {

                Log.i("Document Table", "Document " + document + " already exists.");

                return true;
            }
        }

        return false;
    }


    public static void getLocalTrips(Context context) {

        File file = new File(context.getFilesDir() + "/Trip/");

        for (String item : file.list()) {

            if (!AppConstant.tripList.contains(item.substring(0, item.length() - 5))) {

                AppConstant.tripList.add(item.substring(0, item.length() - 5));
            }
        }

    }


}
