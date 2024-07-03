package com.clone.DeliveryApp.Utility;


import android.location.Location;

import com.clone.DeliveryApp.Model.ItemParcel;

import java.util.ArrayList;

public class AppConstant {

    public static  String DOCUMENT=" ";
    public static  String PARCEL_NO=" ";
    public static  String SIGN_PATH = " ";
    public static  String PIC_PATH = " ";
    public static  String ZOOM = " ";
    public static Location GPS_LOCATION;
    public static String TRIPID;
    public static boolean parcelsValid;

    public static ArrayList<Location> gpsList = new ArrayList<>();

    public static ArrayList<String> documentList = new ArrayList<>();

    public static ArrayList<String> adapterParcelList = new ArrayList<>();

    public static ArrayList<ItemParcel> parcelList=new ArrayList<>();

    public static ArrayList<ItemParcel> syncList=new ArrayList<>();

}
