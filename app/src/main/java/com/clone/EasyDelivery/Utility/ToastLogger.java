package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public class ToastLogger {

    public static void exception(Context context, Exception exception) {

        Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {

                Toast.makeText(context, "Exception: " + exception.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }


    public static void message(Context context, String message) {

        Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {

                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        });
    }

}
