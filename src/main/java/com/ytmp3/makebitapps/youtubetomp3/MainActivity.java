package com.ytmp3.makebitapps.youtubetomp3;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    public RequestQueue queue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        // Instantiate the RequestQueue.
        queue = Volley.newRequestQueue(this);
        Intent intent = getIntent();
        String type = intent.getType();
        if (type!= null) {
            if ("text/plain".equals(type)) {
                downloadToMp3Intent(intent); // Handle text being sent
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public boolean downloadToMp3(View view)
    {
        final TextView youtubeLink = (TextView) findViewById(R.id.youtubeLink);

        if(youtubeLink.getText()!=""){
            String url ="http://youtubeinmp3.com/fetch/?format=JSON&video="+youtubeLink.getText();
            handleDownload(url);
        }
        return true;
    }


    public boolean downloadToMp3Intent(Intent intent)
    {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        final TextView youtubeLink = (TextView) findViewById(R.id.youtubeLink);
        youtubeLink.setText("https://www.youtube.com/watch?v="+sharedText.substring(17));

        if(sharedText != null){
            String url ="http://youtubeinmp3.com/fetch/?format=JSON&video=https://www.youtube.com/watch?v="+sharedText.substring(17);
            handleDownload(url);
        }
        return true;
    }

    private void handleDownload(String url) {
        final TextView textOutput = (TextView) findViewById(R.id.textOutput);
        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            String url = response.getString("link");
                            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                            request.setDescription(getString(R.string.text_downloading));
                            request.setTitle(response.getString("title"));
                            // in order for this if to run, you must use the android 3.2 to compile your app
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                                request.allowScanningByMediaScanner();
                                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                            }
                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, response.getString("title")+".mp3");
                            // get download service and enqueue file
                            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                            manager.enqueue(request);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            textOutput.setText(getString(R.string.text_novideofound));
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        textOutput.setText(getString(R.string.text_connectionerror));
                    }
                });
        queue.add(jsObjRequest);
    }
}
