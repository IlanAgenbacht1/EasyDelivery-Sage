package com.clone.DeliveryApp.Service;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.view.View;


import com.clone.DeliveryApp.WebService.mWebService;

import java.util.HashMap;

public class mAsyncTaskGet extends AsyncTask<Void, Void, Void> {

    String respond,url,message;

    View view;
    //Context context;
    private Context context;
    ProgressDialog pd;

    // private AlertDialog pd;



    AsyncResponse delegate = null;
    HashMap<String,String> hashMap;
    boolean isShow=true;

    public mAsyncTaskGet(Context context, String url, AsyncResponse delegate,String message){
        this.context=context;
        this.url=url;
        this.delegate = delegate;
        this.message=message;

    }
    public mAsyncTaskGet(Context context, String url, AsyncResponse delegate,boolean isShow){
        this.context=context;
        this.url=url;
        this.delegate = delegate;
        this.isShow=isShow;



    }
    public interface AsyncResponse {
        void processFinish(String output);
    }

    @Override
    public void onPreExecute(){



        if(isShow){

//
//            shimmerFrameLayout=(ShimmerFrameLayout)view.findViewById(R.id.my_shimmer);
//            shimmerFrameLayout.startShimmer();

            // pd=new SpotsDialog(context, R.style.Custom);
            pd=new ProgressDialog(context);
            pd.setMessage(message);
            pd.setCancelable(false);
            pd.setCanceledOnTouchOutside(true);
            if(!pd.isShowing())
                pd.show();
        }

    }

    @Override
    protected Void doInBackground(Void... params) {

        try {
            respond=new mWebService().get(url);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void onPostExecute(Void m_adapter){

        if(isShow)
            pd.dismiss();
        delegate.processFinish(respond);



    }


}