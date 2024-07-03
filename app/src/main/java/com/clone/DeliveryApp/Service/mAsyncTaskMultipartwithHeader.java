package com.clone.DeliveryApp.Service;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;


import com.clone.DeliveryApp.WebService.mWebService;

import okhttp3.MultipartBody;

public class mAsyncTaskMultipartwithHeader extends AsyncTask<Void, Void, Void> {
    String respond,url,message;
    Context context;
    ProgressDialog pd;
    AsyncResponse delegate = null;
    MultipartBody.Builder buildernew;


    public mAsyncTaskMultipartwithHeader(Context context, String url, MultipartBody.Builder buildernew,
                               AsyncResponse asyncResponse, String message) {
        this.context=context;
        this.url=url;
        this.buildernew=buildernew;
        this.delegate = asyncResponse;
        this.message=message;
    }



    // you may separate this or combined to caller class.
    public interface AsyncResponse {
        void processFinish(String output);
    }

    @Override
    public void onPreExecute(){
        pd=new ProgressDialog(context);
        pd.setMessage(message);
        pd.setCancelable(false);
        pd.setCanceledOnTouchOutside(false);
        pd.show();
    }

    @Override
    protected Void doInBackground(Void... params) {

        try {
            respond=new mWebService().postMultipartWithHeader(url,buildernew);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void onPostExecute(Void m_adapter){
        System.out.println("respond="+respond);
        try{
            pd.dismiss();
            delegate.processFinish(respond);
        }catch (Exception ex){
            ex.printStackTrace();
        }

    }
}