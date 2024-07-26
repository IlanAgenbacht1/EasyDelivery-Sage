package com.clone.DeliveryApp.Database;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.util.Log;

import com.clone.DeliveryApp.Model.Delivery;
import com.clone.DeliveryApp.Model.ItemParcel;
import com.clone.DeliveryApp.Utility.AppConstant;
import com.clone.DeliveryApp.Utility.SyncConstant;

import java.util.ArrayList;
import java.util.List;

public class DeliveryDb {

    private static final String DATABASE_NAME = "DeliveryDb";
    private static final String DOCUMENT_TABLE = "DocumentTable";
    private static final String PARCEL_TABLE = "ParcelTable";
    private static final String DELIVERY_TABLE = "DeliveryTable";
    private final int DATABASE_VERSION = 14;
    private Context ourContext;
    private SQLiteDatabase ourDatabase;
    private DBHelper ourHelper;


    public static final String KEY_ROWID = "_id";
    public static final String KEY_DOCUMENT = "_docu";
    public static final String KEY_SIGN = "_sign";
    public static final String KEY_PIC = "_pic";
    public static final String KEY_UNIT = "_unit";
    public static final String KEY_PARCEL1 = "_parcel";
    public static final String KEY_TIME = "_time";
    public static final String KEY_DRIVER = "_driver";
    public static final String KEY_VEHICLE = "_vehicle";
    public static final String KEY_COMPANY = "_company";

    public static final String KEY_TRIPID = "_tripId";
    public static final String KEY_COMPLETED = "_completed";
    public static final String KEY_CUSTOMER = "_customer";
    public static final String KEY_ADDRESS = "_address";
    public static final String KEY_CONTACTNAME = "_contactName";
    public static final String KEY_CONTACTNUMBER = "_contactNumber";
    public static final String KEY_LATITUDE = "_latitude";
    public static final String KEY_LONGITUDE = "_longitude";
    public static final String KEY_CAPTUREDLATITUDE = "_capturedLatitude";
    public static final String KEY_CAPTUREDLONGITUDE = "_capturedLongitude";
    public static final String KEY_PARCELS = "_parcelQty";


    public static final String KEY_ROWID2 = "_id2";


    public static final String KEY_DOCUMENT2 = "_docu2";


    public static final String KEY_PARCEL = "_parcel";


    public DeliveryDb(Context ourContext) {
        this.ourContext = ourContext;
    }


    private class DBHelper extends SQLiteOpenHelper {

        public DBHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            String sqlCreateDocuTable = "CREATE TABLE " + DOCUMENT_TABLE + " (" +
                    KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    KEY_DOCUMENT + " TEXT NOT NULL, " +
                    KEY_SIGN + " TEXT NOT NULL, " +
                    KEY_PIC + " TEXT NOT NULL, " +
                    KEY_PARCEL1 + " TEXT NOT NULL, " +
                    KEY_TIME + " TEXT NOT NULL, " +
                    KEY_DRIVER + " TEXT NOT NULL, " +
                    KEY_VEHICLE + " TEXT NOT NULL, " +
                    KEY_COMPANY + " TEXT NOT NULL, " +
                    KEY_UNIT + " TEXT NOT NULL);";

            db.execSQL(sqlCreateDocuTable);

            String sqlCreateParcelTable = "CREATE TABLE " + PARCEL_TABLE + " (" +
                    KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    KEY_TRIPID + " TEXT, " +
                    KEY_DOCUMENT + " TEXT NOT NULL, " +
                    KEY_PARCEL + " TEXT NOT NULL);";

            db.execSQL(sqlCreateParcelTable);

            String sqlCreateDeliveryTable = "CREATE TABLE " + DELIVERY_TABLE + " (" +
                    KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    KEY_TRIPID + " TEXT , " +
                    KEY_DOCUMENT + " TEXT NOT NULL, " +
                    KEY_CUSTOMER + " TEXT NOT NULL, " +
                    KEY_ADDRESS + " TEXT NOT NULL, " +
                    KEY_CONTACTNAME + " TEXT NOT NULL, " +
                    KEY_CONTACTNUMBER + " TEXT NOT NULL, " +
                    KEY_PARCELS + " INTEGER NOT NULL, " +
                    KEY_LATITUDE + " TEXT NOT NULL, " +
                    KEY_LONGITUDE + " TEXT NOT NULL, " +
                    KEY_CAPTUREDLATITUDE + " TEXT, " +
                    KEY_CAPTUREDLONGITUDE + " TEXT, " +
                    KEY_SIGN + " TEXT, " +
                    KEY_PIC + " TEXT, " +
                    KEY_TIME + " TEXT, " +
                    KEY_COMPLETED + " BOOLEAN NOT NULL);";

            db.execSQL(sqlCreateDeliveryTable);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            db.execSQL("DROP TABLE IF EXISTS " + DOCUMENT_TABLE);
            db.execSQL("DROP TABLE IF EXISTS " + PARCEL_TABLE);
            db.execSQL("DROP TABLE IF EXISTS " + DELIVERY_TABLE);

            onCreate(db);
        }
    }


    public DeliveryDb open() throws SQLException {

        ourHelper = new DBHelper(ourContext);

        ourDatabase = ourHelper.getWritableDatabase();

        return this;
    }


    public void close() {

        ourHelper.close();
    }

    public boolean isOpen() {

        return ourDatabase.isOpen();
    }


    public long createScheduleEntry(Delivery delivery) {
        ContentValues cv = new ContentValues();

        cv.put(KEY_TRIPID, delivery.getTripId());
        cv.put(KEY_DOCUMENT, delivery.getDocument());
        cv.put(KEY_CUSTOMER, delivery.getCustomerName());
        cv.put(KEY_CONTACTNAME, delivery.getContactName());
        cv.put(KEY_CONTACTNUMBER, delivery.getContactNumber());
        cv.put(KEY_ADDRESS, delivery.getAddress());
        cv.put(KEY_PARCELS, delivery.getNumberOfParcels());
        cv.put(KEY_LATITUDE, delivery.getLocation().getLatitude());
        cv.put(KEY_LONGITUDE, delivery.getLocation().getLongitude());
        cv.put(KEY_CAPTUREDLATITUDE, "NULL");
        cv.put(KEY_CAPTUREDLONGITUDE, "NULL");
        cv.put(KEY_COMPLETED, delivery.completed());

        return ourDatabase.insert(DELIVERY_TABLE, null, cv);
    }


    public long createDocuEntry(ItemParcel item) {
        ContentValues cv = new ContentValues();

        cv.put(KEY_DOCUMENT, item.getDocu());
        cv.put(KEY_SIGN, item.getSign());
        cv.put(KEY_PIC, item.getPic());
        cv.put(KEY_UNIT, item.getUnit());
        cv.put(KEY_PARCEL1, item.getParcels());
        cv.put(KEY_TIME, item.getTime());
        cv.put(KEY_DRIVER, item.getDriver());
        cv.put(KEY_VEHICLE, item.getVehicle());
        cv.put(KEY_COMPANY, item.getCompany());

        return ourDatabase.insert(DOCUMENT_TABLE, null, cv);
    }


    public long createParcelEntry(String parcel, String docu, String tripId) {
        ContentValues cv = new ContentValues();

        cv.put(KEY_DOCUMENT, docu);
        cv.put(KEY_PARCEL, parcel);
        cv.put(KEY_TRIPID, tripId);

        return ourDatabase.insert(PARCEL_TABLE, null, cv);
    }


    public List<String> getDocumentList(boolean incompleteDocument) {

        //return list of document numbers that have _completed = 0 (false)

        List<String> documents = new ArrayList<>();

        Cursor cursor;

        if (incompleteDocument) {

            cursor = ourDatabase.rawQuery("SELECT " + KEY_DOCUMENT + " FROM " + DELIVERY_TABLE + " WHERE " + KEY_COMPLETED + " = 0" + " AND " + KEY_TRIPID + " = '" + AppConstant.TRIPID + "'", null);
        }
        else {

            cursor = ourDatabase.rawQuery("SELECT " + KEY_DOCUMENT + " FROM " + DELIVERY_TABLE + " WHERE " + KEY_TRIPID + " = '" + AppConstant.TRIPID + "'", null);
        }

        int documentIndex = cursor.getColumnIndex(KEY_DOCUMENT);

        while (cursor != null && cursor.moveToNext()) {

            documents.add(cursor.getString(documentIndex));
        }

        return documents;
    }


    public Delivery getDeliveryData(String document) {

        //return specified document data from the ScheduleTable that matches the tripId in the current trip file.
        //the tripId is how data in the local db is validated against the downloaded delivery.

        Cursor cursor = ourDatabase.rawQuery("SELECT * FROM " + DELIVERY_TABLE + " WHERE " + KEY_DOCUMENT + " = '" + document + "' AND " + KEY_TRIPID + " = '" + AppConstant.TRIPID + "' AND " + KEY_COMPLETED + " = 0;", null);

        int documentIndex = cursor.getColumnIndex(KEY_DOCUMENT);
        int customerIndex = cursor.getColumnIndex(KEY_CUSTOMER);
        int addressIndex = cursor.getColumnIndex(KEY_ADDRESS);
        int contactNameIndex = cursor.getColumnIndex(KEY_CONTACTNAME);
        int contactNumberIndex = cursor.getColumnIndex(KEY_CONTACTNUMBER);
        int latitudeIndex = cursor.getColumnIndex(KEY_LATITUDE);
        int longitudeIndex = cursor.getColumnIndex(KEY_LONGITUDE);
        int parcelsIndex = cursor.getColumnIndex(KEY_PARCELS);

        Delivery delivery = new Delivery();

        while (cursor.moveToNext()) {

            delivery.setDocument(cursor.getString(documentIndex));
            delivery.setCustomerName(cursor.getString(customerIndex));
            delivery.setAddress(cursor.getString(addressIndex));
            delivery.setContactName(cursor.getString(contactNameIndex));
            delivery.setContactNumber(cursor.getString(contactNumberIndex));

            Location location = new Location("");
            location.setLatitude(cursor.getDouble(latitudeIndex));
            location.setLongitude(cursor.getDouble(longitudeIndex));
            delivery.setLocation(location);

            delivery.setNumberOfParcels(cursor.getInt(parcelsIndex));
        }

        cursor.close();

        cursor = ourDatabase.rawQuery("SELECT * FROM " + PARCEL_TABLE + " WHERE " + KEY_DOCUMENT + " = '" + document + "' AND " + KEY_TRIPID + " = '" + AppConstant.TRIPID + "';", null);

        int parcelNumberIndex = cursor.getColumnIndex(KEY_PARCEL);

        List<String> parcels = new ArrayList<>();

        while (cursor.moveToNext()) {

            parcels.add(cursor.getString(parcelNumberIndex));
        }

        delivery.setParcelNumbers(parcels);

        return delivery;
    }


    public List<String> getCompletedDocumentList(String tripID) {

        //return specified document data from the ScheduleTable that matches the tripId in the current trip file.
        //the tripId is how data in the local db is validated against the downloaded delivery.

        Cursor cursor = ourDatabase.rawQuery("SELECT " + KEY_DOCUMENT + " FROM " + DELIVERY_TABLE + " WHERE " + KEY_TRIPID + " = '" + tripID + "' AND " + KEY_COMPLETED + " = 1;", null);

        int documentIndex = cursor.getColumnIndex(KEY_DOCUMENT);

        List<String> documents = new ArrayList<>();

        while (cursor.moveToNext()) {

            documents.add(cursor.getString(documentIndex));

            Log.i("Completed Documents", tripID + " : " + cursor.getString(documentIndex));
        }

        cursor.close();

        return documents;
    }


    public Delivery getCompletedParcels(Delivery delivery) {

        Cursor cursor = ourDatabase.rawQuery("SELECT * FROM " + PARCEL_TABLE + " WHERE " + KEY_TRIPID + " = '" + delivery.getTripId() + "' AND " + KEY_DOCUMENT + " = '" + delivery.getDocument() + "'", null);

        int parcelIndex = cursor.getColumnIndex(KEY_PARCEL);

        List<String> parcelList = new ArrayList<>();

        while (cursor.moveToNext()) {

            parcelList.add(cursor.getString(parcelIndex));
        }

        delivery.setParcelNumbers(parcelList);

        return delivery;
    }


    public Delivery getCompletedDocument(String document, String tripID) {

        Delivery delivery = new Delivery();

        Cursor cursor = ourDatabase.rawQuery("SELECT * FROM " + DELIVERY_TABLE + " WHERE " + KEY_DOCUMENT + " = '" + document + "' AND " + KEY_TRIPID + " = '" + tripID + "' AND " + KEY_COMPLETED + " = 1;", null);

        int documentIndex = cursor.getColumnIndex(KEY_DOCUMENT);
        int customerIndex = cursor.getColumnIndex(KEY_CUSTOMER);
        int addressIndex = cursor.getColumnIndex(KEY_ADDRESS);
        int contactNameIndex = cursor.getColumnIndex(KEY_CONTACTNAME);
        int contactNumberIndex = cursor.getColumnIndex(KEY_CONTACTNUMBER);
        int latitudeIndex = cursor.getColumnIndex(KEY_CAPTUREDLATITUDE);
        int longitudeIndex = cursor.getColumnIndex(KEY_CAPTUREDLONGITUDE);
        int parcelsIndex = cursor.getColumnIndex(KEY_PARCELS);
        int imageIndex = cursor.getColumnIndex(KEY_PIC);
        int signIndex = cursor.getColumnIndex(KEY_SIGN);
        int timeIndex = cursor.getColumnIndex(KEY_TIME);

        while (cursor.moveToNext()) {

            delivery.setTripId(tripID);
            delivery.setDocument(cursor.getString(documentIndex));
            delivery.setCustomerName(cursor.getString(customerIndex));
            delivery.setAddress(cursor.getString(addressIndex));
            delivery.setContactName(cursor.getString(contactNameIndex));
            delivery.setContactNumber(cursor.getString(contactNumberIndex));

            Location location = new Location("");
            location.setLongitude(cursor.getDouble(longitudeIndex));
            location.setLatitude(cursor.getDouble(latitudeIndex));

            delivery.setLocation(location);
            delivery.setNumberOfParcels(cursor.getInt(parcelsIndex));
            delivery.setImagePath(cursor.getString(imageIndex));
            delivery.setSignPath(cursor.getString(signIndex));
            delivery.setTime(cursor.getString(timeIndex));
        }

        return  delivery;
    }


    public void setDocumentCompleted(String document, String imageFile, String signFile, String date, Context context) {
        Cursor cursor = ourDatabase.rawQuery("UPDATE " + DELIVERY_TABLE + " SET " + KEY_COMPLETED + " = 1, " + KEY_CAPTUREDLATITUDE + " = '" + String.valueOf(AppConstant.GPS_LOCATION.getLatitude()) + "', " + KEY_CAPTUREDLONGITUDE + " = '" + String.valueOf(AppConstant.GPS_LOCATION.getLongitude()) + "', " + KEY_PIC + " = '" + imageFile + "', " + KEY_SIGN + " = '" + signFile + "', " + KEY_TIME + " = '" + date + "' WHERE " + KEY_DOCUMENT + " = '" + document + "' AND " + KEY_TRIPID + " = '" + AppConstant.TRIPID + "';", null);

        cursor.moveToFirst();
        cursor.close();

        context.sendBroadcast(new Intent().setAction("DeliveryCompleted"));
    }


    public long deleteEntryAsRow( String rowId){

        return ourDatabase.delete(DOCUMENT_TABLE,KEY_DOCUMENT+"=?",new String[]{rowId});
    }

}
