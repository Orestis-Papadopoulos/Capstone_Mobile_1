package edu.acg.o.papadopoulos.capstone1;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

/**
 * Used in MainActivity by method "postDataToServer()"
 * @source https://www.c-sharpcorner.com/article/send-data-to-the-remote-database-in-android-application/
 * */

public class Singleton {

    private static Singleton mInstance;
    private RequestQueue requestQueue;
    private static Context mCtx;

    private Singleton (Context Context) {
        mCtx = Context;
        requestQueue = getRequestQueue();
    }

    public static  synchronized Singleton getInstance (Context context) {
        if (mInstance==null) {
            mInstance =new Singleton(context);
        }
        return mInstance;
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue==null) {
            requestQueue = Volley.newRequestQueue(mCtx.getApplicationContext());
        }
        return requestQueue;
    }

    public<T> void addToRequestQueue(Request<T> request) {
        requestQueue.add(request);
    }
}
