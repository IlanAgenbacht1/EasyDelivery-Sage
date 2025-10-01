package com.clone.EasyDelivery.Utility;

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

    public interface LocationCallback {
        void onLocationFound(Location location);

        void onLocationNotFound();
    }

    public static LocationManager locationManager;
    public static LocationListener locationListener;

    private static String providerName;


    public static void initialise(Context context) {

        Log.i("LocationHelper", "Initializing LocationHelper");
        
        locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);

        Log.i("LocationHelper", "LocationManager initialized: " + (locationManager != null));

        locationListener = new LocationListener() {

            @Override
            public void onLocationChanged(Location returnedLocation) {

                if (returnedLocation != null) {
                    Log.i("Location", "Location received from " + providerName + ": " + returnedLocation.getLatitude() + ", " + returnedLocation.getLongitude());
                    
                    // Update GPS_LOCATION with the latest location
                    AppConstant.GPS_LOCATION = returnedLocation;
                    
                    // Add to the list for tracking
                    AppConstant.gpsList.add(returnedLocation);
                } else {
                    Log.w("Location", "Received null location from " + providerName);
                }
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

        // Add null checks for location objects to prevent crashes
        if (coordinates == null || preloadedCoordinates == null) {
            Log.e("LocationHelper", "Cannot calculate Haversine distance: location object is null");
            return Double.MAX_VALUE;
        }

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

                        initialise(context);

                        // Try network provider first (preferred)
                        Location lastKnown = null;
                        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                            lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                            if (lastKnown != null) {
                                Log.i("LocationFetch", "Using last known network location (preferred): " + lastKnown.getLatitude() + " " + lastKnown.getLongitude());
                                return lastKnown;
                            }
                            // Request single update from network provider
                            locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, Looper.getMainLooper());
                            Log.i("LocationFetch", "Requested single network location update");
                        } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            // Fallback to GPS if network not available
                            lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (lastKnown != null) {
                                Log.i("LocationFetch", "Using last known GPS location as fallback: " + lastKnown.getLatitude() + " " + lastKnown.getLongitude());
                                return lastKnown;
                            }
                            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, Looper.getMainLooper());
                            Log.i("LocationFetch", "Requested single GPS location update as fallback");
                        } else {
                            Log.w("LocationFetch", "No location providers are enabled");
                        }
                        
                        // Return null for now - location will be available via the listener callback
                        return null;
                    }
                }
                else {

                    initialise(context);

                    // Try network provider first (preferred)
                    Location coord = null;
                    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, Looper.getMainLooper());
                        coord = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if (coord != null) {
                            Log.i("LocationFetch","Using last known network location (preferred): " + coord.getLatitude() + " " + coord.getLongitude());
                            providerName = "NETWORK_PROVIDER";
                            return coord;
                        }
                        Log.i("LocationFetch", "Network provider enabled but no cached location");
                    } 
                    
                    // Fallback to GPS if network not available or no cached network location
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, Looper.getMainLooper());
                        coord = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (coord != null) {
                            Log.i("LocationFetch","Using last known GPS location as fallback: " + coord.getLatitude() + " " + coord.getLongitude());
                            providerName = "GPS_PROVIDER";
                            return coord;
                        }
                        Log.i("LocationFetch", "GPS provider enabled but no cached location");
                    }
                    
                    Log.w("LocationFetch", "No location providers available or no cached locations");
                    return null;
                }

            }catch (SecurityException e) {
                Log.e("LocationHelper", "Security exception when accessing location", e);
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

        Log.i("LocationHelper", "getLocation called - isOnline: " + isOnline);
        
        if (locationManager != null && locationListener != null) {

            locationManager.removeUpdates(locationListener);
            Log.i("LocationHelper", "Removed previous location updates");
        }

        initialise(context);

        startLocationUpdates(isOnline);
    }


    // New method for asynchronous location fetching with callback
    public static void getCurrentLocationAsync(Context context, LocationCallback callback) {
        Log.i("LocationHelper", "getCurrentLocationAsync called");
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("LocationHelper", "Location permissions not granted");
            callback.onLocationNotFound();
            return;
        }
        
        Log.i("LocationHelper", "Location permissions are granted");

        initialise(context);

        // Always prefer network provider first - it's faster and often more accurate
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Location lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (lastKnown != null) {
                Log.i("LocationHelper", "Using last known network location (preferred)");
                callback.onLocationFound(lastKnown);
                return;
            }
            Log.i("LocationHelper", "Network provider enabled but no last known location");
        }

        // Try GPS provider as fallback if network location not available
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnown != null) {
                Log.i("LocationHelper", "Using last known GPS location as fallback");
                callback.onLocationFound(lastKnown);
                return;
            }
            Log.i("LocationHelper", "GPS provider enabled but no last known location");
        }

        Log.w("LocationHelper", "No location providers available or no cached locations found");
        callback.onLocationNotFound();
    }


    private static void startLocationUpdates(boolean isOnline) {

        Log.i("LocationHelper", "startLocationUpdates called - isOnline: " + isOnline);
        
        if (locationManager == null) {
            Log.e("LocationHelper", "LocationManager is null!");
            return;
        }
        
        if (locationListener == null) {
            Log.e("LocationHelper", "LocationListener is null!");
            return;
        }
        
        // Check what providers are available
        Log.i("LocationHelper", "Network provider enabled: " + locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        Log.i("LocationHelper", "GPS provider enabled: " + locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER));
        
        try {

            // Always prefer network provider first (regardless of online status)
            // Network provider is generally faster and more accurate than GPS
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Log.i("LocationHelper", "Using preferred network provider");
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
                providerName = "NETWORK_PROVIDER";
                Log.i("LocationHelper", "Network location updates started successfully");
            } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Log.i("LocationHelper", "Network provider not available, falling back to GPS");
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 0, locationListener);
                providerName = "GPS_PROVIDER";
                Log.i("LocationHelper", "GPS location updates started as fallback");
            } else {
                Log.e("LocationHelper", "No location providers are enabled!");
            }

        } catch (SecurityException e) {

            Log.e("LocationHelper", "Security exception when requesting location updates", e);
            e.printStackTrace();
        } catch (Exception e) {
            Log.e("LocationHelper", "Unexpected exception when requesting location updates", e);
            e.printStackTrace();
        }
    }

}
