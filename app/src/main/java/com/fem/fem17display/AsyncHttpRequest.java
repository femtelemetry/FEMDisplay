package com.fem.fem17display;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.fem.fem17display.MainActivity.isSleep;


public class AsyncHttpRequest extends AsyncTask<String, Void, JSONObject> {
    private static final String TAG = "Async";
    private static String lastID = "";
    private static int sleepcount = 0;
    public static JSONObject json;

    public AsyncHttpRequest(HTTPSRequest activity) {
    }

    @Override
    protected JSONObject doInBackground(String... params) {
        HttpURLConnection con = null;
        StringBuilder builder = new StringBuilder();
        json = new JSONObject();
        try {
            URL url = new URL(params[0]);
            con = (HttpURLConnection) url.openConnection();
            InputStream stream = con.getInputStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            String line = "";
            while ((line = reader.readLine()) != null)
                builder.append(line);
            stream.close();

            Log.i(TAG, builder.toString());

            json = new JSONObject(builder.toString());

        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            con.disconnect();
        }

        return json;
    }

    public void onPostExecute(JSONObject json) {
    }
}