package com.clone.DeliveryApp.Service;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

import com.clone.DeliveryApp.WebService.mWebService;


public class mAsyncTaskPostJson extends AsyncTask<Void, Void, Void> {

  String respond,url,jsonbody;
  Context context;
  ProgressDialog pd;
  AsyncResponse delegate = null;
  boolean isShow=true;


  public mAsyncTaskPostJson(Context context, String url,String jsonbody, AsyncResponse asyncResponse) {
    this.context=context;
    this.url=url;
    this.jsonbody=jsonbody;
    this.delegate = asyncResponse;

  }

  public mAsyncTaskPostJson(Context context, String url,String jsonbody , AsyncResponse asyncResponse, boolean isShow) {
    this.context=context;
    this.url=url;
    this.jsonbody=jsonbody;
    this.delegate = asyncResponse;
    this.isShow=isShow;
  }
  // you may separate this or combined to caller class.
  public interface AsyncResponse {
    void processFinish(String output);
  }

  @Override
  public void onPreExecute(){


    if(isShow){
      pd=new ProgressDialog(context);
      pd.setMessage("Please wait...");
      pd.setCancelable(false);
      pd.setCanceledOnTouchOutside(false);
      if(!pd.isShowing())
        pd.show();
    }
  }

  @Override
  protected Void doInBackground(Void... params) {

    try {
      respond=new mWebService().postJSON(url,jsonbody);

    } catch (Exception e) {
      e.printStackTrace();
    }

    return null;
  }

  @Override
  public void onPostExecute(Void m_adapter){

    try {
      if ((pd != null) && pd.isShowing()) {
        pd.dismiss();

      }
    } catch (final IllegalArgumentException e) {
      // Handle or log or ignore
    } catch (final Exception e) {
      // Handle or log or ignore
    } finally {
      pd = null;
    }

    delegate.processFinish(respond);

  }
}