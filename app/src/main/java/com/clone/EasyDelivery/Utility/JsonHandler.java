package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.util.Log;

import com.clone.EasyDelivery.Model.Delivery;
import com.clone.EasyDelivery.Model.Return;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class JsonHandler {


    public static JSONObject readFile(Context context, String trip) {

        StringBuilder jsonString = new StringBuilder();

        JSONObject tripData = new JSONObject();

        try (BufferedReader reader = new BufferedReader(new FileReader(new File(context.getFilesDir() + "/Trip/", trip + ".json")))) {

            String line;

            while ((line = reader.readLine()) != null) {

                jsonString.append(line);
            }

            tripData = new JSONObject(jsonString.toString());

        } catch (Exception e) {

            e.printStackTrace();

            //ToastLogger.exception(context, e);
        }

        return tripData;
    }


    public static JSONObject readReturnFile(Context context) {

        StringBuilder jsonString = new StringBuilder();

        JSONObject tripData = new JSONObject();

        try (BufferedReader reader = new BufferedReader(new FileReader(new File(context.getFilesDir() + "/Return/", "returns" + ".json")))) {

            String line;

            while ((line = reader.readLine()) != null) {

                jsonString.append(line);
            }

            tripData = new JSONObject(jsonString.toString());

        } catch (Exception e) {

            e.printStackTrace();

            //ToastLogger.exception(context, e);
        }

        return tripData;
    }


    public static String writeDeliveryFile(Context context, Delivery delivery) {
        try {
            File file = new File(context.getFilesDir() + "/Sync/");
            file.mkdirs();
            file = new File(file.getPath(), delivery.getDocument() + ".json");
            SyncConstant.DOCUMENT_FILE_PATH = file.getPath();
            if (file.createNewFile()) {
                JSONObject json = new JSONObject();
                json.put("documentNumber", delivery.getDocument());
                json.put("customer", delivery.getCustomerName());
                json.put("address", delivery.getAddress());
                JSONArray parcels = new JSONArray();
                for (String item : delivery.getParcelNumbers()) {
                    parcels.put(item);
                }
                json.put("items", parcels);
                JSONObject location = new JSONObject();
                location.put("latitude", delivery.getLocation().getLatitude());
                location.put("longitude", delivery.getLocation().getLongitude());
                json.put("location", location);
                json.put("image", delivery.getImagePath());
                json.put("signature", delivery.getSignPath());
                json.put("time", delivery.getTime());
                json.put("comment", delivery.getComment());
                Writer writer = new BufferedWriter(new FileWriter(file));
                writer.write(json.toString(4));
                writer.close();
            }
            return file.getPath();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public static int returnDeliveryCount(Context context, String trip) {
        try {
            Log.i("DeliveryCount", "Fetching delivery count for " + trip);
            JSONObject jsonData = readFile(context, trip);
            JSONArray jsonArray = jsonData.getJSONArray("stops");
            return jsonArray.length();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }


    public static File writeReturnFile(Context context, Return data) {
        try {
            JSONObject jsonFinal = new JSONObject();
            JSONArray jsonArray = new JSONArray();
            File file = new File(context.getFilesDir() + "/Return/", "returns.json");
            if (file.exists()) {
                JSONObject jsonInitial = readReturnFile(context);
                jsonArray = jsonInitial.getJSONArray("returns");
                Log.i("SyncService", "returns.json size: " + jsonArray.length());
            }
            JSONObject jsonData = parseReturnData(data);
            jsonArray.put(jsonData);
            jsonFinal.put("returns", jsonArray);
            Writer writer = new BufferedWriter(new FileWriter(file));
            writer.write(jsonFinal.toString(4));
            writer.close();
            return file;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public static JSONObject parseReturnData(Return data) {
        try {
            JSONObject json = new JSONObject();
            json.put("itemNumber", data.getItem());
            json.put("quantity", data.getQuantity());
            json.put("customer", data.getCustomer());
            json.put("comment", data.getComment());
            json.put("reference", data.getReference());
            json.put("date", data.getTime());
            return json;
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }


}
