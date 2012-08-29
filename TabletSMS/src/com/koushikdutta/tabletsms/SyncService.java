package com.koushikdutta.tabletsms;

import org.json.JSONArray;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpResponse;

public class SyncService extends Service {
    private static final String LOGTAG = "TabletSMS";
    private Settings mSettings;
    private boolean mSyncing = false;
    private long mLastSync = 0;
    private int mSyncCounter = 0;
    private String mAccount;
    SQLiteDatabase mDatabase;

    private boolean handleResult(JSONObject result, boolean isPush) {
        int newCounter = 0;
        if (isPush) {
            try {
                newCounter = result.getInt("this_last_sync");
                if (newCounter != mSyncCounter + 1) {
                    mSyncCounter = newCounter;
                    return false;
                }
                mSyncCounter = newCounter;
            }
            catch (Exception ex) {
                ex.printStackTrace();
                return false;
            }
        }
        
//        System.out.println(result);
        JSONArray data = result.optJSONArray("data");
        if (data == null) {
            Log.i(LOGTAG, "No data?");
            finishSync();
            return false;
        }
        
        if (data.length() == 0) {
            Log.i(LOGTAG, "Done syncing.");
            finishSync();
            return false;
        }
        
        for (int i = 0; i < data.length(); i++) {
            try {
                JSONObject message = data.getJSONObject(i);
                long date = message.getLong("date");
                mLastSync = Math.max(mLastSync, date);
                mSettings.setLong("last_sync_timestamp", mLastSync);
                
                ContentValues args = new ContentValues();
                args.put("key", message.getString("number") + "/" + message.getLong("date"));
                args.put("number", message.getString("number"));
                args.put("date", message.getLong("date"));
                args.put("message", message.optString("message"));
                String type;
                args.put("type", type = message.getString("type"));
                args.put("image", message.optString("image"));
                args.put("unread", "incoming".equals(type) ? 1 : 0);
                
                mDatabase.replace("sms", null, args);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        
        if (isPush) {
            mSettings.setInt("sync_counter", mSyncCounter);
        }

        return !isPush;
    }
    
    private void doAuth() {
        final Handler handler = new Handler();
        new Thread() {
            public void run() {
                try {
                    final String account = mSettings.getString("account");
                    AccountManager accountManager = AccountManager.get(SyncService.this);
                    Account acct = new Account(account, "com.google");
                    String curAuthToken = Settings.getInstance(SyncService.this).getString("web_connect_auth_token");
                    if (!Helper.isJavaScriptNullOrEmpty(curAuthToken))
                        accountManager.invalidateAuthToken(acct.type, curAuthToken);
                    Bundle bundle = accountManager.getAuthToken(acct, TickleServiceHelper.AUTH_TOKEN_TYPE, false, null, null).getResult();
                    final String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                    if (!Helper.isJavaScriptNullOrEmpty(authToken)) {
                        Settings settings = Settings.getInstance(SyncService.this);
                        settings.setString("web_connect_auth_token", authToken);
                    }
                    if (authToken == null) {
                        Log.e(LOGTAG, "Authentication failure.");
                        ServiceHelper.createAuthenticationNotification(SyncService.this);
                        finishSync();
                        return;
                    }
                    String newCookie = TickleServiceHelper.getCookie(SyncService.this);
                    if (newCookie == null) {
                        Log.e(LOGTAG, "Authentication failure.");
                        ServiceHelper.createAuthenticationNotification(SyncService.this);
                        finishSync();
                        return;
                    }
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                    finishSync();
                    return;
                }
                
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        syncNext();
                    }
                });
            };
        }.start();
    }
    
    boolean mHasAuthFailed = false;
    private void syncNext() {
        try {
            AsyncHttpGet get = new AsyncHttpGet(ServiceHelper.SMS_URL + "?limit=100&after_date=" + mLastSync);
            String ascidCookie = mSettings.getString("Cookie");
            get.getHeaders().getHeaders().add("Cookie", ascidCookie);
            get.getHeaders().getHeaders().add("X-Same-Domain", "1");
            get.setFollowRedirect(false);
            AsyncHttpClient.download(get, new AsyncHttpClient.JSONObjectCallback() {
                @Override
                public void onCompleted(Exception e, AsyncHttpResponse response, JSONObject result) {
                    if (response != null && response.getHeaders().getHeaders().getResponseCode() == 302) {
                        if (mHasAuthFailed) {
                            finishSync();
                            return;
                        }
                        mHasAuthFailed = true;
                        doAuth();
                        return;
                    }
                    mHasAuthFailed = false;
                    
                    if (e != null) {
                        e.printStackTrace();
                        finishSync();
                        return;
                    }
//                    System.out.println(result);

                    if (handleResult(result, false))
                        syncNext();
                }
            });
        }
        catch (Exception e) {
            e.printStackTrace();
            finishSync();
        }
    }
    
    private void startSync() {
        if (mSyncing)
            return;
        if (Helper.isJavaScriptNullOrEmpty(mAccount)) {
            finishSync();
            return;
        }
        syncNext();
        mSyncing = true;
        
    }
    
    private void finishSync() {
        mSettings.setInt("sync_counter", mSyncCounter);
        Intent syncComplete = new Intent();
        syncComplete.setAction("com.koushikdutta.tabletsms.SYNC_COMPLETE");
        sendBroadcast(syncComplete);
        mSyncing = false;
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return super.onStartCommand(intent, flags, startId);
        if ("com.koushikdutta.tabletsms.SYNC".equals(intent.getAction())) {
            startSync();
            return START_STICKY;
        }
        else if ("refresh".equals(intent.getStringExtra("type"))) {
            try {
                if ("sms".equals(intent.getStringExtra("bucket"))) {
                    JSONObject envelope = new JSONObject(intent.getStringExtra("envelope"));
                    if (!handleResult(envelope, true))
                        startSync();
                }
            }
            catch (Exception ex) {
                startSync();
            }
        }
        
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        mDatabase.close();
        super.onDestroy();
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        mSettings = Settings.getInstance(this);
        // start from a week ago
        mLastSync = mSettings.getLong("last_sync_timestamp", System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L);
        mSyncCounter = mSettings.getInt("sync_counter", 0);
        mDatabase = Database.open(this);
        mAccount = mSettings.getString("account");
    }
}
