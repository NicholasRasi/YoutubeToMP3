package com.ytmp3.makebitapps.youtubetomp3;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;


public class MainActivity extends AppCompatActivity {
    private String gKey = googleAPI.getKey();

    CountDownLatch countDownLatch = new CountDownLatch(2);
    private RequestQueue queue;
    private String sessionKey = "";
    private String yUrl = "";
    private String yTitle = "";
    private int errorCode = 0;
    private int tryNumber = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        queue = Volley.newRequestQueue(this);
        Intent intent = getIntent();
        String type = intent.getType();
        if (type!= null) {
            if ("text/plain".equals(type)) {
                downloadToMp3Intent(intent); // Handle text being sent
            }
        }
    }


    public void downloadToMp3(View view)
    {
        final TextView yUrlEditText = (TextView) findViewById(R.id.youtubeLink);
        yUrl = yUrlEditText.getText().toString();
        if(!yUrl.equals("")){
            handleDownload();
        }
    }


    private void downloadToMp3Intent(Intent intent)
    {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        final TextView youtubeLink = (TextView) findViewById(R.id.youtubeLink);
        String youtubeUrl = "https://www.youtube.com/watch?v="+sharedText.substring(17);
        youtubeLink.setText(youtubeUrl);

        if(!sharedText.equals("")){
            yUrl = "https://www.youtube.com/watch?v="+sharedText.substring(17);
            handleDownload();
        }
    }



    private class AsyncTaskJsoupGetSessionKey extends AsyncTask<Void, Void, Void> {
        private Document htmlDocument = null;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            // Get sessionKey
            try {
                htmlDocument = Jsoup.connect("http://www.saveitoffline.com/").get();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            //var session = 'MUYy9YRYVhNiwN5wJHc4UEx+3GeEJovlkclfBEzesA8=';
            if(htmlDocument==null){
                errorCode = 1;
            }
            else {
                int pos = htmlDocument.toString().indexOf("session");
                sessionKey = htmlDocument.toString().substring(pos + 11, pos + 55);
                Log.e("sk", sessionKey);
            }
            countDownLatch.countDown();
        }
    }


    private class AsyncTaskGetVideoTitle extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            TextView textOutput = (TextView) findViewById(R.id.textOutput);
            textOutput.setText(R.string.text_getvideotitle);
        }

        @Override
        protected Void doInBackground(Void... params) {
            // Get video title
            String urlToGetTitle = "https://www.googleapis.com/youtube/v3/videos?id="+yUrl.substring(yUrl.indexOf("?v=")+3,yUrl.indexOf("?v=")+14)+"&key="+gKey+"&part=snippet";
            StringRequest strRequest = new StringRequest(Request.Method.GET, urlToGetTitle,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            Log.e("r ",response);
                            try {
                                JSONObject jsResponse = new JSONObject(response);
                                JSONArray jsItems = jsResponse.getJSONArray("items");
                                JSONObject jsSnippet = jsItems.getJSONObject(0).getJSONObject("snippet");
                                yTitle = jsSnippet.getString("title");
                                Log.e("r ", yTitle);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            countDownLatch.countDown();
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("e ",error.toString());
                    errorCode = 2;
                    countDownLatch.countDown();
                }
            });
            queue.add(strRequest);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

        }
    }

    private class AsyncTaskDownload extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            TextView textOutput = (TextView) findViewById(R.id.textOutput);
            textOutput.setText(R.string.text_downloadisstarting);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                // Wait for sessionKey and video title
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Log.e("s ","download");
            final TextView textOutput = (TextView) findViewById(R.id.textOutput);
            if(errorCode!=0){textOutput.setText(getString(R.string.text_downloaderror)); enableDownloadButton(); return;}
            // start download
            String url = "http://www.saveitoffline.com/request.php";
            StringRequest strRequest = new StringRequest(Request.Method.POST, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            Log.e("r ", response);
                            if(response.equals("") && tryNumber<3){
                                // Empty response, retry
                                AsyncTaskDownload atDownload = new AsyncTaskDownload();
                                atDownload.execute();
                            }
                            try {
                                JSONObject jObj = new JSONObject(response);
                                // Search the correct format
                                Iterator keys = jObj.keys();
                                Boolean found = false;
                                String currentDynamicKey = "";
                                JSONObject currentDynamicValue;
                                while(keys.hasNext() && !found) {
                                    currentDynamicKey = (String)keys.next();
                                    currentDynamicValue = jObj.getJSONObject(currentDynamicKey);
                                    String type = currentDynamicValue.getString("type");
                                    if(type.substring(0,18).equals("(Audio Only) - m4a")) found=true;
                                    Log.e("r ",type.substring(0,18));
                                }
                                if(found){
                                    JSONObject number = jObj.getJSONObject(currentDynamicKey);
                                    String link = number.getString("id");
                                    String downloadUrl = "http://www.saveitoffline.com/"+link;
                                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
                                    request.setDescription(getString(R.string.text_downloading));
                                    request.setTitle(yTitle);
                                    request.setMimeType("audio/mpeg3");
                                    // in order for this if to run, you must use the android 3.2 to compile your app
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                                        request.allowScanningByMediaScanner();
                                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                    }
                                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, yTitle + ".mp3");
                                    // get download service and enqueue file
                                    DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                                    manager.enqueue(request);
                                    textOutput.setText(getString(R.string.text_downloadstart));

                                    enableDownloadButton();
                                }
                                else {
                                    textOutput.setText(getString(R.string.text_downloaderror));
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("e ",error.toString());
                    textOutput.setText(getString(R.string.text_downloaderror));
                    enableDownloadButton();
                }
            }) {
                @Override
                protected Map<String,String> getParams(){
                    Map<String,String> params = new HashMap<String, String>();
                    params.put("input",yUrl);
                    Log.e("using ",sessionKey);
                    params.put("sessionKey",sessionKey);
                    return params;
                }
            };
            queue.add(strRequest);
        }
    }

    public void enableDownloadButton(){
        //Enable download button
        Button downloadButton = (Button) findViewById(R.id.downloadButton);
        downloadButton.setClickable(true);
    }

    private void handleDownload() {
        // Disable download button
        Button downloadButton = (Button) findViewById(R.id.downloadButton);
        downloadButton.setClickable(false);
        errorCode=0;
        tryNumber=0;

        Log.e("s ", "start gsk");
        AsyncTaskJsoupGetSessionKey atGetSessionKey = new AsyncTaskJsoupGetSessionKey();
        atGetSessionKey.execute();

        Log.e("s ", "start gvt");
        AsyncTaskGetVideoTitle atGetVideoTitle = new AsyncTaskGetVideoTitle();
        atGetVideoTitle.execute();

        Log.e("s ", "start d");
        AsyncTaskDownload atDownload = new AsyncTaskDownload();
        atDownload.execute();
    }
}