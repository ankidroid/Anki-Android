package com.ichi2.anki;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import com.google.gson.Gson;
import com.ichi2.anki.gimgsrch.json.ImageSearchResponse;
import com.ichi2.anki.gimgsrch.json.ResponseData;
import com.ichi2.anki.gimgsrch.json.Result;

public class SearchImageActivity extends Activity implements DialogInterface.OnCancelListener
{
    public static final String EXTRA_SOURCE = "search.image.activity.extra.source";
    private String mSource;
    private WebView mWebView;
    private Button mPrevButton;
    private Button mNextButton;
    private ProgressDialog progressDialog;
    private ArrayList<String> mImages;
    private int mCurrentImage;
    private String mTemplate = null;
    private Button mPickButton;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_image);

        try
        {
            mSource = getIntent().getExtras().getString(EXTRA_SOURCE).toString();
        }
        catch (Exception e)
        {
            mSource = "";
        }

        // If translation fails this is a default - source will be returned.

        mWebView = (WebView) findViewById(R.id.ImageSearchWebView);
        mWebView.setWebViewClient(new WebViewClient()
        {
            @Override
            public void onPageFinished(WebView view, String url)
            {
                super.onPageFinished(view, url);
                processPageLoadFinished();
            }
            
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon)
            {             
                super.onPageStarted(view, url, favicon);
                processPageLoadStarted();
            }
            
            
        });
        
        
        mPickButton = (Button) findViewById(R.id.ImageSearchPick);
        mPickButton.setEnabled(false);
        mPickButton.setOnClickListener(new OnClickListener()
        {   
            @Override
            public void onClick(View v)
            {
                pickImage();
            }
        });

        mNextButton = (Button) findViewById(R.id.ImageSearchNext);

        mNextButton.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                nextClicked();
            }
        });

        mPrevButton = (Button) findViewById(R.id.ImageSearchPrev);

        mPrevButton.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                prevClicked();
            }
        });

        mPrevButton.setEnabled(false);

    }

    protected void pickImage()
    {
        String imageUrl = mImages.get(mCurrentImage);
        
        //And here it is possible to download it... so on,
        // then return file path.
        
        //TODO CONTINUE HERE
        
    }

    protected void processPageLoadStarted()
    {
        mPickButton.setEnabled(false);
        progressDialog = ProgressDialog.show(this, getText(R.string.multimedia_editor_progress_wait_title),
                "Loading image", true, false);

        progressDialog.setCancelable(true);
    }

    protected void processPageLoadFinished()
    {
        dismissCarefullyProgressDialog();
        mPickButton.setEnabled(true);
    }

    public String getLocalIpAddress()
    {
        try
        {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();)
            {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();)
                {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress())
                    {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        }
        catch (Exception ex)
        {
            return "";
        }
        return "";
    }

    private class BackgroundPost extends AsyncTask<Void, Void, ImageSearchResponse>
    {

        private String mQuery;

        @Override
        protected ImageSearchResponse doInBackground(Void... params)
        {
            try
            {
                String ip = getLocalIpAddress();

                URL url = new URL("https://ajax.googleapis.com/ajax/services/search/images?"
                        + "v=1.0&q=Q&userip=IP".replaceAll("Q", getQuery()).replaceAll("IP", ip));
                URLConnection connection = url.openConnection();
                connection.addRequestProperty("Referer", "anki.ichi2.com");

                String line;
                StringBuilder builder = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = reader.readLine()) != null)
                {
                    builder.append(line);
                }

                Gson gson = new Gson();
                ImageSearchResponse resp = gson.fromJson(builder.toString(), ImageSearchResponse.class);

                resp.setOk(true);
                return resp;

            }
            catch (Exception e)
            {
                return new ImageSearchResponse();
            }
        }

        @Override
        protected void onPostExecute(ImageSearchResponse result)
        {
            postFinished(result);
        }

        /**
         * @param query
         * 
         *            Used to set the download address
         * 
         */
        public void setQuery(String query)
        {
            mQuery = query;
        }

        /**
         * @return
         * 
         *         Used to know, which of the posts finished, to differentiate.
         * 
         */
        public String getQuery()
        {
            try
            {
                return URLEncoder.encode(mQuery, "utf-8");
            }
            catch (UnsupportedEncodingException e)
            {
                return mQuery;
            }
        }

    }

    @Override
    protected void onResume()
    {
        super.onResume();

        progressDialog = ProgressDialog.show(this, getText(R.string.multimedia_editor_progress_wait_title),
                getText(R.string.multimedia_editor_trans_translating_online), true, false);

        progressDialog.setCancelable(true);
        progressDialog.setOnCancelListener(this);

        BackgroundPost p = new BackgroundPost();
        p.setQuery(mSource);
        p.execute();
    }

    public void postFinished(ImageSearchResponse response)
    {

        ArrayList<String> theImages = new ArrayList<String>();

        // No loop, just a good construct to break out from
        do
        {
            if (response == null)
                break;

            if (response.getOk() == false)
                break;

            ResponseData rdata = response.getResponseData();

            if (rdata == null)
                break;

            List<Result> results = rdata.getResults();

            if (results == null)
                break;

            for (Result result : results)
            {
                if (result == null)
                {
                    continue;
                }

                String url = result.getUrl();

                if (url != null)
                {
                    theImages.add(url);
                }
            }

            if (theImages.size() == 0)
                break;

            proceedWithImages(theImages);

            return;

        }
        while (false);

        returnFailure("Search failed");
    }

    private void proceedWithImages(ArrayList<String> theImages)
    {
        showToast("Images found");
        dismissCarefullyProgressDialog();

        mImages = theImages;
        mCurrentImage = 0;

        showCurrentImage();

    }

    private void showCurrentImage()
    {
        if (mCurrentImage <= 0)
        {
            mCurrentImage = 0;
            mPrevButton.setEnabled(false);
            mNextButton.setEnabled(mImages.size() > 0);
        }

        if (mCurrentImage > 0)
        {
            mCurrentImage = Math.min(mImages.size() - 1, mCurrentImage);
            mPrevButton.setEnabled(true);
            mNextButton.setEnabled(mCurrentImage < mImages.size() - 1);
        }

        String source = makeImageCodeTemplate();

        source = source.replaceAll("URL", mImages.get(mCurrentImage));

        mWebView.loadData(source, "text/html", null);

    }

    private void returnFailure(String explanation)
    {
        showToast(explanation);
        setResult(RESULT_CANCELED);
        dismissCarefullyProgressDialog();
        finish();
    }

    private void dismissCarefullyProgressDialog()
    {
        try
        {
            if (progressDialog != null)
            {
                if (progressDialog.isShowing())
                {
                    progressDialog.dismiss();
                }
            }
        }
        catch (Exception e)
        {
            // nothing is done intentionally
        }
    }

    private void showToast(CharSequence text)
    {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, text, duration);
        toast.show();
    }

    protected void prevClicked()
    {
        --mCurrentImage;
        showCurrentImage();

    }

    protected void nextClicked()
    {
        ++mCurrentImage;
        showCurrentImage();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_search_image, menu);
        return true;
    }

    private String makeImageCodeTemplate()
    {
        if (mTemplate != null)
        {
            return mTemplate;
        }

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;

        int min = Math.min(height, width);

        String source = "<center><img width=\"WIDTH\" src=\"URL\" /> </center>".replaceAll("WIDTH", min / 2 + "");

        mTemplate = source;

        return source;
    }

    @Override
    public void onCancel(DialogInterface dialog)
    {
        // TODO Auto-generated method stub

    }

}
