package com.clone.DeliveryApp.WebService;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class mWebService {

    //use okHttp method
    OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();


    //method of string parameter receiving the url to hit
    //this is the actual method where Json is hit and received
    public String get(String url) throws Exception{

        //the url request
        Request request = new Request.Builder().url(url).build();

        System.out.println("**************** GET URL *****************\n"+request);


        Response response = client.newCall(request).execute();

        //the JSON response from url
        String r=response.body().string();

        System.out.println("**************** GET RESPONSE *****************\n"+r);

        return r;
    }



    //checking internet connection . . . . . . .

    public static  boolean checkInternetConnection(Context context){

        System.out.println("checking internet Connection///////////////////////");

        ConnectivityManager conMgr=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork= conMgr.getActiveNetworkInfo();

        System.out.println("Network Info/////////////"+activeNetwork);

        if (activeNetwork!=null && activeNetwork.isConnected()&activeNetwork.isAvailable()){

            return true;
        }else{
            return false;
        }

    }


    public String postMultipart(String url, MultipartBody.Builder buildernew) {
        String r = null;
        final MediaType MEDIA_TYPE_PNG = MediaType.parse("image/jpg");

        try {

            MultipartBody requestBody = buildernew.build();
            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();


            System.out.println("**************** POST URL *****************\n"+request);

            Response response = client.newCall(request).execute();
            r=response.body().string();

            System.out.println("**************** POST RESPONSE *****************\n"+r);
        }catch (Exception e){
            e.printStackTrace();
        }
        return r;
    }


    public String postJSON(String url, String jsonBody) throws Exception {
        System.out.println("**************** JSON BODY *****************\n"+jsonBody);

        MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

        RequestBody body = RequestBody.create(JSON, jsonBody);
        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();

        Response response = client.newCall(request).execute();
        String r=response.body().string();

        System.out.println("**************** POST RESPONSE *****************\n"+r);
        return r;
    }


    public String postMultipartWithHeader(String url, MultipartBody.Builder buildernew) {
        String r = null;
        final MediaType MEDIA_TYPE_PNG = MediaType.parse("image/jpg");

        try {

            MultipartBody requestBody = buildernew.build();
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization","Basic ZmluZGluZ0dhbmRoaUppOjYxc2MwdjNyR0BuNmgx")
                    .post(requestBody)
                    .build();


            System.out.println("**************** POST URL *****************\n"+request);

            Response response = client.newCall(request).execute();
            r=response.body().string();

            System.out.println("**************** POST RESPONSE *****************\n"+r);
        }catch (Exception e){
            e.printStackTrace();
        }
        return r;
    }
}