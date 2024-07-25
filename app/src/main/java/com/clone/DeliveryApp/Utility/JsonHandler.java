package com.clone.DeliveryApp.Utility;

import android.content.Context;

import com.clone.DeliveryApp.Model.Delivery;

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


    public static JSONObject readFile(Context context) {

        StringBuilder jsonString = new StringBuilder();

        JSONObject tripData = new JSONObject();

        try (BufferedReader reader = new BufferedReader(new FileReader(new File(context.getFilesDir() + "/Trip/", AppConstant.TRIP_NAME + ".json")))) {

            String line;

            while ((line = reader.readLine()) != null) {

                jsonString.append(line);

            }

            tripData = new JSONObject(jsonString.toString());

        } catch (IOException e) {

            e.printStackTrace();

        } catch (JSONException e) {

            throw new RuntimeException(e);
        }

        return tripData;
    }


    public static void writeDeliveryFile(Context context, Delivery delivery) {

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

                Writer writer = new BufferedWriter(new FileWriter(file));
                writer.write(json.toString());
                writer.close();
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

    }


}
