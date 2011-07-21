package com.koushikdutta.desktopsms;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ServiceHelper {
    private static String LOGTAG = ServiceHelper.class.getSimpleName();
    public static final String BASE_URL = "https://2.desksms.appspot.com";
    public static final String SETTINGS_URL = BASE_URL + "/settings";
    public static final String MESSAGE_URL = BASE_URL + "/message";
    public final static String REGISTER_URL = BASE_URL + "/register";
    public static final String AUTH_URL = BASE_URL + "/_ah/login";
    public static final String API_URL = BASE_URL + "/api/v1";
    public static final String SMS_URL = API_URL + "/user/%s/sms";

    static void addAuthentication(Context context, HttpMessage message) {
        Settings settings = Settings.getInstance(context);
        String ascidCookie = settings.getString("Cookie");
        message.setHeader("Cookie", ascidCookie);
        message.setHeader("X-Same-Domain", "1"); // XSRF
    }
    
    static HttpPost getAuthenticatedPost(Context context, String url) throws UnsupportedEncodingException, URISyntaxException {
        URI uri = new URI(url);
        HttpPost post = new HttpPost(uri);
        addAuthentication(context, post);
        return post;
    }
    
    static HttpPost getAuthenticatedPost(Context context, String url, ArrayList<NameValuePair> params) throws UnsupportedEncodingException, URISyntaxException {
        URI uri = new URI(url);
        HttpPost post = new HttpPost(uri);
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, "UTF-8");
        post.setEntity(entity);
        addAuthentication(context, post);
        return post;
    }
    
    static void updateSettings(final Context context, final boolean xmpp, final boolean mail, final Callback<Boolean> callback) {
        new Thread() {
            public void run() {
                try {
                    ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
                    params.add(new BasicNameValuePair("forward_xmpp", String.valueOf(xmpp)));
                    params.add(new BasicNameValuePair("forward_email", String.valueOf(mail)));

                    HttpPost post = ServiceHelper.getAuthenticatedPost(context, SETTINGS_URL, params);
                    DefaultHttpClient client = new DefaultHttpClient();
                    HttpResponse res = client.execute(post);
                    Log.i(LOGTAG, "Status code from register: " + res.getStatusLine().getStatusCode());
                    Settings settings = Settings.getInstance(context);
                    settings.setBoolean("forward_xmpp", xmpp);
                    settings.setBoolean("forward_email", mail);
                    if (callback != null)
                        callback.onCallback(true);
                }
                catch (Exception ex) {
                    //ex.printStackTrace();
                    if (callback != null)
                        callback.onCallback(false);
                }
                finally {
                    Intent i = new Intent(WidgetProvider.UPDATE);
                    context.sendBroadcast(i);
                }
            };
        }.start();        
    }
    
    static void getSettings(final Context context, final Callback<JSONObject> callback) {
        new Thread() {
            @Override
            public void run() {
                try {
                    HttpGet get = new HttpGet(new URI(SETTINGS_URL));
                    ServiceHelper.addAuthentication(context, get);
                    DefaultHttpClient client = new DefaultHttpClient();
                    HttpResponse res = client.execute(get);
                    final JSONObject s = new JSONObject(StreamUtility.readToEnd(res.getEntity().getContent()));
                    Iterator<String> keys = s.keys();
                    Settings settings = Settings.getInstance(context);
                    while (keys.hasNext()) {
                        String key = keys.next();
                        String value = s.optString(key, null);
                        if (value == null)
                            continue;
                        settings.setString(key, value);
                    }
                    callback.onCallback(s);
                    Intent i = new Intent(WidgetProvider.UPDATE);
                    context.sendBroadcast(i);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }.start();
    }
}
