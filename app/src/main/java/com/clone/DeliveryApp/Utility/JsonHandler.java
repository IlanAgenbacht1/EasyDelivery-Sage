package com.clone.DeliveryApp.Utility;

import android.content.Context;
import android.os.Environment;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class JsonHandler {

    static File file = new File(Environment.getExternalStorageDirectory().getPath() + "/DeliveryApp/TripSchedule/", "trip.json");

    private static String ReadFile() {

        StringBuilder jsonString = new StringBuilder();

        if (file.exists()) {

            try (BufferedReader reader = new BufferedReader(new FileReader(file.getPath()))) {

                String line;

                while ((line = reader.readLine()) != null) {

                    jsonString.append(line);

                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return jsonString.toString();
    }

    public static JSONObject GetJsonData() {

        String jsonString = ReadFile();

        try {

            JSONObject tripData = new JSONObject(jsonString);

            return  tripData;

        } catch (JSONException e) {

            e.printStackTrace();

            return null;
        }
    }

    static void WriteJson(Context context, File file) {



    }

}
