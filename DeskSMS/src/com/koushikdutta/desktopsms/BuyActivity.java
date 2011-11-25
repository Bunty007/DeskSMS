package com.koushikdutta.desktopsms;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.clockworkmod.billing.ClockworkModBillingClient;
import com.clockworkmod.billing.PurchaseCallback;
import com.clockworkmod.billing.PurchaseResult;
import com.clockworkmod.billing.PurchaseType;

public class BuyActivity extends ActivityBase implements PurchaseCallback {
    protected boolean allowThemeOverride() {
        return false;
    }
    
    String mBuyerId;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        LinearLayout title = (LinearLayout)findViewById(R.id.title_container);
        title.addView(getLayoutInflater().inflate(R.layout.buy_title, null));

        TextView tv = (TextView)findViewById(R.id.title);
        tv.setText(R.string.desksms_one_year_license);
        
        final ClockworkModBillingClient client = ClockworkModBillingClient.getInstance();
        
        final JSONObject data = new JSONObject();
        try {
            data.put("device_id", Helper.getSafeDeviceId(this));
            data.put("account", mSettings.getString("account"));
        }
        catch (JSONException e) {
            e.printStackTrace();
        }

        ListItem status = addItem(R.string.account_status, new ListItem(this, R.string.account_status, 0));

        ListItem paypal = addItem(R.string.payment_options, new ListItem(this, R.string.paypal, 0, R.drawable.paypal) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                client.startPurchase(BuyActivity.this, "desksms.subscription0", mBuyerId, mSettings.getString("account"), data.toString(), PurchaseType.PAYPAL, BuyActivity.this);
            }
        });

        ListItem market = addItem(R.string.payment_options, new ListItem(this, R.string.android_market_inapp, 0, R.drawable.market) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                client.startPurchase(BuyActivity.this, "desksms.subscription0", mBuyerId, mSettings.getString("account"), data.toString(), PurchaseType.MARKET_INAPP, BuyActivity.this);
            }
        });

        ListItem redeem = addItem(R.string.payment_options, new ListItem(this, R.string.redeem_code, 0, R.drawable.icon) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                client.startPurchase(BuyActivity.this, "desksms.subscription0", mBuyerId, mSettings.getString("account"), data.toString(), PurchaseType.REDEEM, BuyActivity.this);
            }
        });

        JSONObject payload;
        try {
            payload = new JSONObject(getIntent().getStringExtra("payload"));
            mBuyerId = payload.getString("buyer_id");
            long expiration = payload.getLong("subscription_expiration");
            long daysLeft = (expiration - System.currentTimeMillis()) / 1000L / 60L / 60L / 24L;
            status.setTitle(getString(R.string.days_left, String.valueOf(daysLeft)));
        }
        catch (Exception ex) {
            ex.printStackTrace();
            finish();
        }
    }

    @Override
    public void onFinished(PurchaseResult result) {
        if (result != PurchaseResult.SUCCEEDED) {
            finish();
            return;
        }
        
        Helper.showAlertDialog(this, R.string.purchase_thanks, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
    }
}
