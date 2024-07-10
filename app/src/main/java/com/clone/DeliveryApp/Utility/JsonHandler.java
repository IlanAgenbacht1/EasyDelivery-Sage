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


    private static String ReadFile(Context context) {

        StringBuilder jsonString = new StringBuilder();

        File file = new File(context.getFilesDir(), "trip.json");

        if (file.exists()) {

            try (BufferedReader reader = new BufferedReader(new FileReader(new File(file.getPath(), "trip.json")))) {

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

    public static JSONObject GetJsonData(Context context) {

        String jsonString = ReadFile(context);

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
