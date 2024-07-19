package com.clone.DeliveryApp.Utility;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class JsonHandler {


    private static String readFile(Context context) {

        StringBuilder jsonString = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(new File(context.getFilesDir(), AppConstant.TRIP_NAME + ".json")))) {

            String line;

            while ((line = reader.readLine()) != null) {

                jsonString.append(line);

            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return jsonString.toString();
    }

    public static JSONObject getJsonData(Context context) {

        String jsonString = readFile(context);

        try {

            JSONObject tripData = new JSONObject(jsonString);

            return  tripData;

        } catch (JSONException e) {

            e.printStackTrace();

            return null;
        }
    }

}
