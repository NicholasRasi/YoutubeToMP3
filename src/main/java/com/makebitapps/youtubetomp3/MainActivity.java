package com.makebitapps.youtubetomp3;

import android.Manifest;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "YOUTUBETOMP3";
    private static final int MAX_TRIES = 3;
    private RequestQueue queue;
    private TextView textOutput;
    private int tries;
    private boolean errors;
    private EditText yUrlEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        textOutput = findViewById(R.id.textOutput);
        yUrlEditText = findViewById(R.id.EditTextYoutubeLink);

        queue = Volley.newRequestQueue(this);
        Intent intent = getIntent();
        String type = intent.getType();
        if (type != null) {
            if ("text/plain".equals(type)) {
                downloadToMp3Intent(intent); // Handle text being sent
            }
        }
    }

    @Override
    protected void onResume() {
        super.onPostResume();

        String textFromClipboard = (String) getTextFromClipboard();
        if(textFromClipboard != null && isValidYLink(textFromClipboard)){
            yUrlEditText.setText(textFromClipboard);
        }
    }

    public void downloadToMp3(View view) {
        if (isStoragePermissionGranted()) {
            String youtubeUrl = yUrlEditText.getText().toString();

            Log.d(TAG, "Url: " + youtubeUrl);

            if (isValidYLink(youtubeUrl)) {
                Log.d(TAG, "Valid link");

                Uri uri = Uri.parse(youtubeUrl);
                String videoID = uri.getQueryParameter("v");

                if (videoID != null && !videoID.isEmpty() && videoID.length() == 11) {
                    Log.d(TAG, "Video ID: " + videoID);
                    handleDownload(videoID);
                } else {
                    videoID = youtubeUrl.substring(youtubeUrl.lastIndexOf('/') + 1);
                    if (videoID.length() == 11) {
                        Log.d(TAG, "Video ID: " + videoID);
                        handleDownload(videoID);
                    } else {
                        Log.d(TAG, "Not valid video ID" + videoID);
                        textOutput.setText(R.string.not_valid_id);
                    }
                }
            } else {
                Log.d(TAG, "Not valid link");
                textOutput.setText(R.string.not_valid_link);
            }
        } else {
            Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show();
        }
    }

    private void downloadToMp3Intent(Intent intent) {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);

        String youtubeUrl = "https://www.youtube.com/watch?v=" + sharedText.substring(17);

        final TextView youtubeLink = findViewById(R.id.EditTextYoutubeLink);
        youtubeLink.setText(youtubeUrl);

        downloadToMp3(getCurrentFocus());
    }

    private boolean isValidYLink(String youtubeUrl) {
        return youtubeUrl.matches("^(http(s)?:\\/\\/)?((w){3}.)?youtu(be|.be)?(\\.com)?\\/.+");
    }

    private void handleDownload(String videoId) {
        // Disable download button
        Button downloadButton = findViewById(R.id.downloadButton);
        downloadButton.setClickable(false);

        tries = 0;

        Log.d(TAG, "Starting video download");
        AsyncTaskDownload atDownload = new AsyncTaskDownload();
        atDownload.execute(videoId);
    }

    private class AsyncTaskDownload extends AsyncTask<String, Void, Void> {
        private String videoId;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            errors = false;
            tries = 0;

            textOutput.setText(R.string.starting_download);
        }

        @Override
        protected Void doInBackground(String... params) {
            this.videoId = params[0];

            Log.d(TAG, "Downloading..." + videoId);

            // start download
            String url = "https://www.yt-download.org/@grab?vidID=" + videoId + "&format=mp3&streams=mp3&api=button";

            StringRequest stringRequest = new StringRequest(Request.Method.GET, url, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    Log.d(TAG, "Response received");

                    String downloadUrl = getDownloadUrl(response);
                    if (downloadUrl != null) {
                        Log.d(TAG, "Starting download of " + downloadUrl);

                        String title = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);
                        try {
                            title = URLDecoder.decode(URLDecoder.decode(title, "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        Log.d(TAG, "Title: " + title);

                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
                        request.setDescription(getString(R.string.text_downloading));
                        request.setTitle(title);
                        request.setMimeType("audio/mpeg3");
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        request.allowScanningByMediaScanner();
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, title);

                        // get download service and enqueue file
                        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                        manager.enqueue(request);

                        Log.d(TAG, "Download started");
                        enableDownloadButton();
                        textOutput.setText(R.string.download_started);
                    } else {
                        Log.d(TAG, "Download link not found");
                        restartAsyncTask(videoId);
                    }

                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, error.toString());
                    restartAsyncTask(videoId);
                }
            }) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> params = new HashMap<>();
                    params.put("Referer", "https://www.yt-download.org/@api/button/mp3/" + videoId);

                    return params;
                }
            };

            queue.add(stringRequest);

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
        }
    }

    private void restartAsyncTask(String videoId) {
        if (tries < MAX_TRIES) {
            Log.e("e ", "Retrying...");
            AsyncTaskDownload atDownload = new AsyncTaskDownload();
            atDownload.execute(videoId);
        } else {
            textOutput.setText(R.string.download_error);
        }
    }

    private String getDownloadUrl(String response) {
        Document doc = Jsoup.parse(response);

        Elements links = doc.getElementsByTag("a");

        String linkHref;
        for (Element link : links) {
            linkHref = link.attr("href");
            // String linkText = link.text();

            if (!linkHref.isEmpty()) {
                return linkHref;
            }
        }

        return null;
    }

    public void enableDownloadButton() {
        //Enable download button
        Button downloadButton = findViewById(R.id.downloadButton);
        downloadButton.setClickable(true);
    }

    public boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG, "Permission is granted");
                return true;
            } else {
                Log.v(TAG, "Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG, "Permission is granted");
            return true;
        }
    }

    public CharSequence getTextFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            return clip.getItemAt(0).coerceToText(this);
        }
        return null;
    }
}
