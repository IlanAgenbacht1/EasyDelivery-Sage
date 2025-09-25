package com.clone.EasyDelivery.Activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.Utility.AppConstant;
import com.squareup.picasso.Picasso;

public class ZoomView extends AppCompatActivity {



    private ImageView ivZoom;

    private RelativeLayout ivClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zoom_view);



        ivZoom = (ImageView) findViewById(R.id.photo_view);

        ivClose=findViewById(R.id.llTop);


        DisplayMetrics dm= new DisplayMetrics();

        getWindowManager().getDefaultDisplay().getMetrics(dm);

        int width=dm.widthPixels;
        int height=dm.heightPixels;

        getWindow().setLayout((int) ((int) width*.85), (int) ((int)height*.85));

        WindowManager.LayoutParams params=getWindow().getAttributes();
        params.gravity= Gravity.CENTER;
        params.x=0;
        params.y=-20;

        getWindow().setAttributes(params);


        Picasso.with(this)

                //bytearray converted to bitmap and bitmap converted to uri
                .load("file://"+AppConstant.ZOOM)
                .into(ivZoom); //this is your ImageView



        ivClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                finish();
            }
        });
    }
}
