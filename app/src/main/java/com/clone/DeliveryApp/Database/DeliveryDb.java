package com.clone.DeliveryApp.Database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.clone.DeliveryApp.Model.ItemParcel;
import com.clone.DeliveryApp.Model.Schedule;
import com.clone.DeliveryApp.Utility.AppConstant;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.RequiresApi;

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


    public long createScheduleEntry(Schedule schedule) {
        ContentValues cv = new ContentValues();

        cv.put(KEY_TRIPID, schedule.getTripId());
        cv.put(KEY_DOCUMENT, schedule.getDocument());
        cv.put(KEY_CUSTOMER, schedule.getCustomerName());
        cv.put(KEY_CONTACTNAME, schedule.getContactName());
        cv.put(KEY_CONTACTNUMBER, schedule.getContactNumber());
        cv.put(KEY_ADDRESS, schedule.getAddress());
        cv.put(KEY_PARCELS, schedule.getNumberOfParcels());
        cv.put(KEY_LATITUDE, schedule.getLocation().getLatitude());
        cv.put(KEY_LONGITUDE, schedule.getLocation().getLongitude());
        cv.put(KEY_CAPTUREDLATITUDE, "NULL");
        cv.put(KEY_CAPTUREDLONGITUDE, "NULL");
        cv.put(KEY_COMPLETED, schedule.completed());

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


    public Schedule getScheduleData(String document) {

        //return specified document data from the ScheduleTable that matches the tripId in the current schedule file.
        //the tripId is how data in the local db is validated against the downloaded schedule.

        Cursor cursor = ourDatabase.rawQuery("SELECT * FROM " + DELIVERY_TABLE + " WHERE " + KEY_DOCUMENT + " = '" + document + "' AND " + KEY_TRIPID + " = '" + AppConstant.TRIPID + "' AND " + KEY_COMPLETED + " = 0;", null);

        int documentIndex = cursor.getColumnIndex(KEY_DOCUMENT);
        int customerIndex = cursor.getColumnIndex(KEY_CUSTOMER);
        int addressIndex = cursor.getColumnIndex(KEY_ADDRESS);
        int contactNameIndex = cursor.getColumnIndex(KEY_CONTACTNAME);
        int contactNumberIndex = cursor.getColumnIndex(KEY_CONTACTNUMBER);
        int latitudeIndex = cursor.getColumnIndex(KEY_LATITUDE);
        int longitudeIndex = cursor.getColumnIndex(KEY_LONGITUDE);
        int parcelsIndex = cursor.getColumnIndex(KEY_PARCELS);

        Schedule schedule = new Schedule();

        while (cursor.moveToNext()) {

            schedule.setDocument(cursor.getString(documentIndex));
            schedule.setCustomerName(cursor.getString(customerIndex));
            schedule.setAddress(cursor.getString(addressIndex));
            schedule.setContactName(cursor.getString(contactNameIndex));
            schedule.setContactNumber(cursor.getString(contactNumberIndex));

            Location location = new Location("");
            location.setLatitude(cursor.getDouble(latitudeIndex));
            location.setLongitude(cursor.getDouble(longitudeIndex));
            schedule.setLocation(location);

            schedule.setNumberOfParcels(cursor.getInt(parcelsIndex));
        }

        cursor.close();

        cursor = ourDatabase.rawQuery("SELECT * FROM " + PARCEL_TABLE + " WHERE " + KEY_DOCUMENT + " = '" + document + "' AND " + KEY_TRIPID + " = '" + AppConstant.TRIPID + "';", null);

        int parcelNumberIndex = cursor.getColumnIndex(KEY_PARCEL);

        List<String> parcels = new ArrayList<>();

        while (cursor.moveToNext()) {

            parcels.add(cursor.getString(parcelNumberIndex));
        }

        schedule.setParcelNumbers(parcels);

        return schedule;
    }

    public void setDocumentCompleted(String document, String imageFile, String signFile, String date) {
        Cursor cursor = ourDatabase.rawQuery("UPDATE " + DELIVERY_TABLE + " SET " + KEY_COMPLETED + " = 1, " + KEY_CAPTUREDLATITUDE + " = '" + String.valueOf(AppConstant.GPS_LOCATION.getLatitude()) + "', " + KEY_CAPTUREDLONGITUDE + " = '" + String.valueOf(AppConstant.GPS_LOCATION.getLongitude()) + "', " + KEY_PIC + " = '" + imageFile + "', " + KEY_SIGN + " = '" + signFile + "', " + KEY_TIME + " = '" + date + "' WHERE " + KEY_DOCUMENT + " = '" + document + "';", null);

        cursor.moveToFirst();
        cursor.close();
    }


    public String getDataDocu() {

        String[] columns = new String[]{KEY_ROWID,KEY_DOCUMENT, KEY_SIGN, KEY_PIC, KEY_UNIT,KEY_PARCEL1,KEY_TIME,KEY_DRIVER,
                KEY_VEHICLE,KEY_COMPANY};

        Cursor cs = ourDatabase.query(DOCUMENT_TABLE, columns, null, null, null, null, null);

        String result = "";

        int rowID = cs.getColumnIndex(KEY_ROWID);
        int docuID = cs.getColumnIndex(KEY_DOCUMENT);
        int signID = cs.getColumnIndex(KEY_SIGN);
        int picID = cs.getColumnIndex(KEY_PIC);
        int unitID = cs.getColumnIndex(KEY_UNIT);
        int parcelId = cs.getColumnIndex(KEY_PARCEL1);
        int timeId = cs.getColumnIndex(KEY_TIME);
        int driverId = cs.getColumnIndex(KEY_DRIVER);
        int vehicleId = cs.getColumnIndex(KEY_VEHICLE);
        int companyId = cs.getColumnIndex(KEY_COMPANY);

        if (cs != null && cs.moveToFirst()) {

            result = result + cs.getString(rowID) + ": " + cs.getString(docuID) + " "
                    + cs.getString(signID) + "," + cs.getString(picID) + "," +cs.getString(parcelId)+
                    ","+ cs.getString(unitID) + ","+ cs.getString(timeId) +","+ cs.getString(driverId)+","+ cs.getString(vehicleId)
                    +","+ cs.getString(companyId)+
                    "\n";
        }

        //cs.close();
        return result;
    }


    public void printTableData(){

        Cursor cur = ourDatabase.rawQuery("SELECT * FROM " + DOCUMENT_TABLE, null);

        if(cur.getCount() != 0){
            cur.moveToFirst();

            do{
                String row_values = "";

                for(int i = 0 ; i < cur.getColumnCount(); i++){
                    row_values = row_values + " || " + cur.getString(i);
                }

                Log.d("LOG_TAG_HERE", row_values);

            }while (cur.moveToNext());
        }
    }


    public void printParcelTableData(String document){

        Cursor cur = ourDatabase.rawQuery("SELECT * FROM " + PARCEL_TABLE + " WHERE " + KEY_DOCUMENT + " = '" + document + "' AND " + KEY_TRIPID + " = '" + AppConstant.TRIPID + "';", null);

        if(cur.getCount() != 0){
            cur.moveToFirst();

            do{
                String row_values = "";

                for(int i = 0 ; i < cur.getColumnCount(); i++){
                    row_values = row_values + " || " + cur.getString(i);
                }

                Log.d("LOG_TAG_HERE", row_values);

            }while (cur.moveToNext());
        }
    }


    public String getDataParcel() {

        String[] columns = new String[]{KEY_ROWID2,KEY_DOCUMENT2, KEY_PARCEL};

        Cursor cs = ourDatabase.query(PARCEL_TABLE, columns, null, null, null, null, null);

        String result = "";

        int rowID = cs.getColumnIndex(KEY_ROWID2);

        int docuID = cs.getColumnIndex(KEY_DOCUMENT2);

        int parcelID = cs.getColumnIndex(KEY_PARCEL);

        if (cs != null && cs.moveToFirst()) {

            result = result + cs.getString(rowID) + ": " + cs.getString(docuID) + " "
                    + cs.getString(parcelID) +  "\n";
        }

        //cs.close();
        return result;
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public List<ItemParcel> getSyncData() {
        // array of columns to fetch

        String[] columns = new String[]{KEY_ROWID,KEY_DOCUMENT, KEY_SIGN, KEY_PIC, KEY_UNIT,KEY_PARCEL1,KEY_TIME,KEY_DRIVER,KEY_VEHICLE,KEY_COMPANY};

        Cursor cs= ourDatabase.rawQuery("SELECT * from " + DOCUMENT_TABLE,null );

        int rowID= cs.getColumnIndex(KEY_ROWID);

        int docuID=cs.getColumnIndex(KEY_DOCUMENT);

        int signID=cs.getColumnIndex(KEY_SIGN);

        int picID=cs.getColumnIndex(KEY_PIC);

        int unitID=cs.getColumnIndex(KEY_UNIT);

        int parcelID=cs.getColumnIndex(KEY_PARCEL1);

        int timeID=cs.getColumnIndex(KEY_TIME);

        int driverID=cs.getColumnIndex(KEY_DRIVER);

        int vehicleID=cs.getColumnIndex(KEY_VEHICLE);

        int companyID=cs.getColumnIndex(KEY_COMPANY);

        List<ItemParcel> listItems = new ArrayList<ItemParcel>();

        // Traversing through all rows and adding to list
        if (cs.moveToFirst()) {
            do {

                ItemParcel itemParcel = new ItemParcel();
                itemParcel.setDocu(cs.getString(docuID));
                itemParcel.setSign(cs.getString(signID));
                itemParcel.setPic(cs.getString(picID));
                itemParcel.setUnit(cs.getString(unitID));
                itemParcel.setParcels(cs.getString(parcelID));
                itemParcel.setCompany(cs.getString(companyID));
                itemParcel.setVehicle(cs.getString(vehicleID));
                itemParcel.setDriver(cs.getString(driverID));
                itemParcel.setTime(cs.getString(timeID));

                // Adding user record to list
                listItems.add(itemParcel);

            } while (cs.moveToNext());
        }
        cs.close();
        //db.close();

        // return user list
        return listItems;
    }

    public long deleteEntryAsRow( String rowId){

        return ourDatabase.delete(DOCUMENT_TABLE,KEY_DOCUMENT+"=?",new String[]{rowId});
    }

    private void exportDB(){

        File sd = Environment.getExternalStorageDirectory();
        File data = Environment.getDataDirectory();
        FileChannel source=null;
        FileChannel destination=null;
        String currentDBPath = "/data/"+ "com.clone.DeliveryApp" +"/databases/"+"DeliverDb";
        String backupDBPath = "BackupDb";
        File currentDB = new File(data, currentDBPath);
        File backupDB = new File(sd, backupDBPath);

        try {

            source = new FileInputStream(currentDB).getChannel();
            destination = new FileOutputStream(backupDB).getChannel();
            destination.transferFrom(source, 0, source.size());
            source.close();
            destination.close();

            Toast.makeText(ourContext, "DB Exported!", Toast.LENGTH_SHORT).show();

        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}
