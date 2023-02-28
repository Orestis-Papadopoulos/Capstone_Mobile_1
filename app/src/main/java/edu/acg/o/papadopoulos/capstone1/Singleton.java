package edu.acg.o.papadopoulos.capstone1;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public class Singleton {

    private static Singleton singleton;
    private RequestQueue requestQueue;
    private static Context context;

    private Singleton (Context Context) {
        context = Context;
        requestQueue = getRequestQueue();
    }

    public static  synchronized Singleton getInstance (Context context) {
        if (singleton == null) singleton = new Singleton(context);
        return singleton;
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue == null) requestQueue = Volley.newRequestQueue(context.getApplicationContext());
        return requestQueue;
    }

    public<T> void addToRequestQueue(Request<T> request) {
        requestQueue.add(request);
    }
}
