package com.clone.DeliveryApp.Utility;

import static android.content.Context.LOCATION_SERVICE;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.function.Consumer;

public class LocationHelper {


    public static LocationManager locationManager;
    public static LocationListener locationListener;

    private static String providerName;


    public static double calculateHaversine(Location coordinates, Location preloadedCoordinates) {

        final int R = 6371000; // Radius of the earth in meters

        double latDistance = Math.toRadians(coordinates.getLatitude() - preloadedCoordinates.getLatitude());

        double lonDistance = Math.toRadians(coordinates.getLongitude() - preloadedCoordinates.getLongitude());

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(preloadedCoordinates.getLatitude())) * Math.cos(Math.toRadians(coordinates.getLatitude()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distance = R * c; // Convert to meters

        return distance;
    }


    public static Location returnClosestCoordinate(Location preloadedCoordinate, Context context) {

        if (AppConstant.gpsList == null || AppConstant.gpsList.isEmpty()) {

            try {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                        locationManager.getCurrentLocation(LocationManager.GPS_PROVIDER, null, ContextCompat.getMainExecutor(context), new Consumer<Location>() {
                            @Override
                            public void accept(Location location) {

                                providerName = "GPS_PROVIDER";

                                Log.i("Location", String.valueOf(location.getLatitude()) + " " + String.valueOf(location.getLongitude()));
                            }
                        });
                    }

                }
                else {

                    locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, Looper.getMainLooper());

                    Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                    Log.i("Location","Using last known location: " + String.valueOf(location.getLatitude()) + " " + String.valueOf(location.getLongitude()));

                    providerName = "GPS_PROVIDER";
                }

            }catch (SecurityException e) {
                e.printStackTrace();
            }

            //return null;
        }

        Location closestCoordinate = null;

        double closestDistance = Double.MAX_VALUE;

        for (Location coord : AppConstant.gpsList) {

            double distance = calculateHaversine(coord, preloadedCoordinate);

            if (distance < closestDistance) {

                closestDistance = distance;
                closestCoordinate = coord;
            }
        }

        return closestCoordinate;
    }


    public static boolean isWithinDistance(Location specifiedCoord, double distanceInMeters) {

        if (AppConstant.GPS_LOCATION == null) {

            return false;
        }

        double distance = calculateHaversine(specifiedCoord, AppConstant.GPS_LOCATION);

        return distance <= distanceInMeters;
    }


    public static void GetLocation(Context context, boolean isOnline) {

        locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);

        Log.i("Location", "Manager initialized.");

        locationListener = new LocationListener() {

            @Override
            public void onLocationChanged(Location returnedLocation) {

                Log.i("Location", "Fetching location... (" + providerName + ")");

                AppConstant.gpsList.add(returnedLocation);

                Log.i("Location", String.valueOf(returnedLocation.getLatitude()) + " " + String.valueOf(returnedLocation.getLongitude()));
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

                Log.i("Location", "Status changed.");
            }
            @Override
            public void onProviderEnabled(String s) {

                Log.i("Location", "Provider enabled.");

            }
            @Override
            public void onProviderDisabled(String s) {

                Log.i("Location", "Provider disabled.");

            }
        };

        locationManager.removeUpdates(locationListener);

        startLocationUpdates(isOnline);
    }


    private static void startLocationUpdates(boolean isOnline) {

        try {

            if (isOnline) {

                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);

                providerName = "NETWORK_PROVIDER";

            } else {

                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 0, locationListener);

                providerName = "GPS_PROVIDER";
            }

        } catch (SecurityException e) {

            e.printStackTrace();
        }
    }


    public static void removeLocationUpdates() {

        locationManager.removeUpdates(locationListener);
    }

}
