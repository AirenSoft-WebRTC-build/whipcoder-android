package com.example.whip;


import android.util.Log;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.ResponseHandler;
import cz.msebera.android.httpclient.client.methods.HttpDelete;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.impl.client.BasicResponseHandler;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;

class WHIPClient {
    private URL _url;
    private List<String> _link = new ArrayList<String>();
    private String _location;
    private String _vary;
    // Local SDP
    private String _request_sdp;
    // Remote SDP
    private String _response_sdp;
    WHIPClient() {
    }

    public void setURL(String url)
    {
        try {
            _url = new URL(url);
            Log.d(getClass().getName(), "protocol = " + _url.getProtocol());
            Log.d(getClass().getName(), "authority = " + _url.getAuthority());
            Log.d(getClass().getName(), "host = " + _url.getHost());
            Log.d(getClass().getName(), "port = " + _url.getPort());
            Log.d(getClass().getName(), "path = " + _url.getPath());
            Log.d(getClass().getName(), "query = " + _url.getQuery());
            Log.d(getClass().getName(), "filename = " + _url.getFile());
            Log.d(getClass().getName(), "ref = " + _url.getRef());
        } catch (MalformedURLException e) {
            Log.d(getClass().getName(), "Invalid URL syntax");
        }
    }

    public List<String> GetLink()
    {
        return _link;
    }

    public String GetLocation()
    {
        return _location;
    }

    public void setLocalSDP(String sdp)
    {
        _request_sdp = sdp;
    }

    public String getRemoteSDP()
    {
        return _response_sdp;
    }

    public String GetVary() {
        return _vary;
    }

    public boolean Create() {
        try {
            Log.i(getClass().getName(), "Create : " + _url.toString());

            HttpClient client = HttpClientBuilder.create().build();
            HttpPost request = new HttpPost(_url.toString());
            request.setHeader("Accept", "*/*");
            request.setHeader("Content-Type", "application/sdp");
            request.setHeader("User-Agent:", "Mozilla/5.0 (OBS-Studio/30.1.2; Windows x86_64; en-US) ");
            request.setEntity(new StringEntity(_request_sdp));

            HttpResponse response = client.execute(request);

            if (response.getStatusLine().getStatusCode() == 200 || // OK
                response.getStatusLine().getStatusCode() == 201) { // Created
                ResponseHandler<String> handler = new BasicResponseHandler();

                // ice servers
                _link.clear();
                for(Header header : response.getHeaders("Link"))
                {
                    _link.add(header.getValue());
                }
                _location = response.getFirstHeader("Location").getValue();
                _vary = response.getFirstHeader("Vary").getValue();
                _response_sdp = handler.handleResponse(response);

                Log.i(getClass().getName(), "Create Success");

                Log.d(getClass().getName(), _link.toString());
                Log.d(getClass().getName(), _location);
                Log.d(getClass().getName(), _vary);
                return true;
            } else {
                Log.e(getClass().getName(), "response is error : " + response.getStatusLine().getStatusCode());
            }
        } catch (Exception e){
            Log.e(getClass().getName(), e.toString());
        }

        return false;
    }

    public void Delete()
    {
        try {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String requestURL = String.format("%s://%s%s", _url.getProtocol(), _url.getAuthority(), GetLocation());
                        Log.i(getClass().getName(), "Delete : " + requestURL);
                        HttpClient client = HttpClientBuilder.create().build();
                        HttpDelete request = new HttpDelete(requestURL);
                        request.setHeader("Accept", "*/*");
                        request.setHeader("User-Agent:", "Mozilla/5.0 (OBS-Studio/30.1.2; Windows x86_64; en-US) ");

                        HttpResponse response = client.execute(request);

                        if (response.getStatusLine().getStatusCode() == 200 | response.getStatusLine().getStatusCode() == 201) {
                            ResponseHandler<String> handler = new BasicResponseHandler();
                            handler.handleResponse(response);
                            Log.i(getClass().getName(), "Delete Success");
                        } else {
                            Log.e(getClass().getName(), "response is error : " + response.getStatusLine().getStatusCode());
                        }
                    } catch (Exception e){
                        Log.e(getClass().getName(), e.toString());
                    }
                }
            });
            t.start();
            t.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
