package com.clone.DeliveryApp.Utility;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.clone.DeliveryApp.Database.DeliveryDb;
import com.clone.DeliveryApp.Model.Schedule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ScheduleHelper {

    public static void getSchedule(Context context, String companyName, Activity activity) {

        downloadSchedule(context, companyName);
        parseAndInsertScheduleData(context);
    }

    private static void parseAndInsertScheduleData(Context context) {

        Schedule schedule = new Schedule();

        JSONObject jsonData = JsonHandler.GetJsonData(context);

        if (jsonData != null) {

            try {

                //Set TripId for this schedule

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

                    schedule.setDocument(documentNumber);
                    schedule.setTripId(tripId);
                    schedule.setCustomerName(customerName);
                    schedule.setAddress(street + ", " + city + ", " + state + ", " + postalCode + ", " + country);
                    schedule.setContactName(customerContactName);
                    schedule.setContactNumber(customerContact);
                    schedule.setLocation(location);
                    schedule.setNumberOfParcels(numParcels);
                    schedule.setCompleted(false);

                    schedule.setParcelNumbers(parcelList);

                    insertScheduleData(context, schedule);
                }

            } catch (JSONException e) {

                e.printStackTrace();
            }
        }
    }


    public static void downloadSchedule(Context context, String companyName) {

        if (ConnectionHelper.isInternetConnected()) {

            DropboxHelper.DownloadFile(context, companyName);

        } else {

            Handler handler2 = new Handler(Looper.getMainLooper());
            handler2.post(new Runnable() {
                @Override
                public void run() {

                    Toast.makeText(context, "Connection failed. Continuing offline.", Toast.LENGTH_LONG).show();
                }
            });

            Log.i("Internet", "No internet connection.");
        }
    }


    private static void insertScheduleData(Context context, Schedule schedule) {

        try {

            DeliveryDb database = new DeliveryDb(context);

            database.open();

            //Check if document exists first.

            if (!documentExists(database, schedule.getDocument(), false)) {

                database.createScheduleEntry(schedule);

                Log.i("Document Table", "Document inserted.");

                for (String parcel : schedule.getParcelNumbers()) {

                    database.createParcelEntry(parcel, schedule.getDocument(), schedule.getTripId());

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

        boolean documentExists = false;

        for (int i = 0; i < documentList.size(); i++) {

            if (document.equals(documentList.get(i))) {

                documentExists = true;

                Log.i("Document Table", "Document " + document + " already exists.");
            }
        }

        return documentExists;
    }

}
