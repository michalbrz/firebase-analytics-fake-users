package com.demo.firebase.view;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.demo.firebase.R;
import com.demo.firebase.StoreApplication;
import com.demo.firebase.TestCheck;
import com.demo.firebase.model.Cart;
import com.demo.firebase.model.Product;
import com.google.firebase.analytics.FirebaseAnalytics.Event;
import com.google.firebase.analytics.FirebaseAnalytics.Param;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.HashMap;
import java.util.UUID;

public class CartActivity extends AppCompatActivity {

    public static final String RC_BUTTON_KEY = "button_label";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        Cart cart = Cart.getInstance();
        if (cart.isEmpty()) {
            TextView emptyCart = findViewById(R.id.emptycart);
            emptyCart.setText(R.string.cart_is_empty);
            Button checkoutBtn = findViewById(R.id.checkout_btn);
            checkoutBtn.setVisibility(View.INVISIBLE);
        } else {
            RecyclerView view = findViewById(R.id.item_list);
            LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
            view.setLayoutManager(layoutManager);
            view.setAdapter(new CartItemAdapter());

            getRemoteConfigurationForButton();
        }
        FirebaseCrashlytics.getInstance().setCustomKey("unique items in the cart", cart.getNumOfUniqueProducts());
        FirebaseCrashlytics.getInstance().log("Entering cart view");
    }

    private void getRemoteConfigurationForButton() {
        Button button = findViewById(R.id.checkout_btn);
        FirebaseRemoteConfig config = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings props = new FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(0).build();
        config.setConfigSettingsAsync(props);
        config.setDefaultsAsync(new HashMap<String, Object>(){{
            put(RC_BUTTON_KEY, "default label");
        }});
        config.fetchAndActivate().addOnCompleteListener(task -> {
            String buttonText = config.getString(RC_BUTTON_KEY);
            button.setText(buttonText);
        });
    }

    public void checkout(View v) {
        Cart cart = Cart.getInstance();

        //track conversion for purchase
        logPurchase(cart);

        cart.pay();
        throwRandomExceptionDuringTest();
        Intent intent = new Intent(this, PaidActivity.class);
        startActivity(intent);
    }

    private void logPurchase(Cart cart) {
        Parcelable[] items = new Parcelable[cart.getNumOfUniqueProducts()];
        double totalValue = 0;
        for (int i = 0; i < cart.getNumOfUniqueProducts(); i++) {
            Product product = cart.getProduct(i);
            int quantity = cart.getQuantity(product);
            totalValue = quantity * product.price;

            Bundle item = new Bundle();
            item.putString(Param.ITEM_ID, Double.toString(product.id));
            item.putString(Param.ITEM_CATEGORY, product.category.toString());
            item.putLong(Param.QUANTITY, quantity);
            item.putDouble(Param.PRICE, product.price);
            items[i] = item;
        }

        String transactionId = UUID.randomUUID().toString();
        Bundle purchaseParams = new Bundle();
        purchaseParams.putString(Param.TRANSACTION_ID, transactionId);
        purchaseParams.putString(Param.CURRENCY, "USD");
        purchaseParams.putDouble(Param.VALUE, totalValue);
        purchaseParams.putParcelableArray(Param.ITEMS, items);
        StoreApplication.logEvent(Event.PURCHASE, purchaseParams);

        FirebaseCrashlytics.getInstance().setCustomKey("checkout value", totalValue);
    }

    public static void navigate(Activity activity) {
        Intent intent = new Intent(activity, CartActivity.class);
        activity.startActivity(intent);
    }

    private void throwRandomExceptionDuringTest() {
        if (TestCheck.isRunningTest()) {
            if (Math.random() > 0.98) {
                throw new IllegalArgumentException();
            }
        }
    }
}
