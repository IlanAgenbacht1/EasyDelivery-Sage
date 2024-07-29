package com.clone.DeliveryApp.Utility;

import static android.content.Context.LOCATION_SERVICE;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.function.Consumer;

public class LocationHelper {


    public static LocationManager locationManager;
    public static LocationListener locationListener;

    private static String providerName;


    public static void initialise(Context context) {

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
    }


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

        Location closestCoordinate = null;

        if (AppConstant.gpsList == null || AppConstant.gpsList.isEmpty()) {

            try {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                        final Location[] coord = {null};

                        locationManager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, ContextCompat.getMainExecutor(context), new Consumer<Location>() {
                            @Override
                            public void accept(Location location) {

                                providerName = "NETWORK_PROVIDER";

                                Log.i("LocationFetch", String.valueOf(location.getLatitude()) + " " + String.valueOf(location.getLongitude()));

                                coord[0] = location;
                            }
                        });

                        double closestDistance = Double.MAX_VALUE;

                        for (Location c : coord) {

                            double distance = calculateHaversine(c, preloadedCoordinate);

                            if (distance < closestDistance) {

                                closestDistance = distance;
                                closestCoordinate = c;
                            }
                        }

                        return closestCoordinate;
                    }
                }
                else {

                    initialise(context);

                    locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, Looper.getMainLooper());

                    Location coord = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                    Log.i("Location","Using last known location: " + String.valueOf(coord.getLatitude()) + " " + String.valueOf(coord.getLongitude()));

                    providerName = "NETWORK_PROVIDER";

                    double closestDistance = Double.MAX_VALUE;

                    double distance = calculateHaversine(coord, preloadedCoordinate);

                    if (distance < closestDistance) {

                        closestDistance = distance;
                        closestCoordinate = coord;
                    }

                    return closestCoordinate;
                }

            }catch (SecurityException e) {
                e.printStackTrace();
            }

        } else {

            double closestDistance = Double.MAX_VALUE;

            for (Location coord : AppConstant.gpsList) {

                double distance = calculateHaversine(coord, preloadedCoordinate);

                if (distance < closestDistance) {

                    closestDistance = distance;
                    closestCoordinate = coord;
                }
            }
        }

        return closestCoordinate;
    }


    public static boolean isWithinDistance(Location specifiedCoord, double distanceInMeters) {

        if (AppConstant.GPS_LOCATION == null) {

            Log.i("Location", "No location stored");

            return false;
        }

        double distance = calculateHaversine(specifiedCoord, AppConstant.GPS_LOCATION);

        return distance <= distanceInMeters;
    }


    public static void getLocation(boolean isOnline, Context context) {

        if (locationManager != null || locationListener != null) {

            locationManager.removeUpdates(locationListener);
        }

        initialise(context);

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

}
