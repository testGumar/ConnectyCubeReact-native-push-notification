package com.dieam.reactnativepushnotification.modules;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import com.dieam.reactnativepushnotification.helpers.ApplicationBadgeHelper;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RNPushNotification extends ReactContextBaseJavaModule implements ActivityEventListener {
    public static final String LOG_TAG = "RNPushNotification";// all logging should use this tag

    private RNPushNotificationHelper mRNPushNotificationHelper;
    private final Random mRandomNumberGenerator = new Random(System.currentTimeMillis());
    private RNPushNotificationJsDelivery mJsDelivery;
    private Application applicationContext;
    public static boolean isAndroidXOrHigher = Build.VERSION.SDK_INT > Build.VERSION_CODES.P;
    public NotificationChannelManager notificationChannelManager;

    public RNPushNotification(ReactApplicationContext reactContext) {
        super(reactContext);

        reactContext.addActivityEventListener(this);

        applicationContext = (Application) reactContext.getApplicationContext();

        // The @ReactNative methods use this
        mRNPushNotificationHelper = new RNPushNotificationHelper(applicationContext);
        notificationChannelManager = new NotificationChannelManager(reactContext);
        // This is used to delivery callbacks to JS
        mJsDelivery = new RNPushNotificationJsDelivery(reactContext);

        registerNotificationsRegistration();
    }

    @Override
    public String getName() {
        return "RNPushNotification";
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();

        return constants;
    }

    private Bundle getBundleFromIntent(Intent intent) {
        Bundle bundle = null;
        if (intent.hasExtra("notification")) {
            bundle = intent.getBundleExtra("notification");
        } else if (intent.hasExtra("google.message_id")) {
            bundle = intent.getExtras();
        }
        return bundle;
    }
    public void onNewIntent(Intent intent) {
        Bundle bundle = this.getBundleFromIntent(intent);
        if (bundle != null) {
            bundle.putBoolean("foreground", false);
            intent.putExtra("notification", bundle);
            mJsDelivery.notifyNotification(bundle);
        }
    }

    private void registerNotificationsRegistration() {
        IntentFilter intentFilter = new IntentFilter(getReactApplicationContext().getPackageName() + ".RNPushNotificationRegisteredToken");

        getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String token = intent.getStringExtra("token");
                WritableMap params = Arguments.createMap();
                params.putString("deviceToken", token);

                mJsDelivery.sendEvent("remoteNotificationsRegistered", params);
            }
        }, intentFilter);
    }

    private void registerNotificationsReceiveNotificationActions(ReadableArray actions) {
        IntentFilter intentFilter = new IntentFilter();
        // Add filter for each actions.
        for (int i = 0; i < actions.size(); i++) {
            String action = actions.getString(i);
            intentFilter.addAction(getReactApplicationContext().getPackageName() + "." + action);
        }
        getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle bundle = intent.getBundleExtra("notification");

                // Notify the action.
                mJsDelivery.notifyNotificationAction(bundle);

                // Dismiss the notification popup.
                NotificationManager manager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);
                int notificationID = Integer.parseInt(bundle.getString("id"));
                manager.cancel(notificationID);
            }
        }, intentFilter);
    }

    @ReactMethod
    public void checkPermissions(Promise promise) {
        ReactContext reactContext = getReactApplicationContext();
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(reactContext);
        promise.resolve(managerCompat.areNotificationsEnabled());
    }

    @ReactMethod
    public void requestPermissions(String senderID) {
        ReactContext reactContext = getReactApplicationContext();

        Intent GCMService = new Intent(reactContext, RNPushNotificationRegistrationService.class);

        try {
            GCMService.putExtra("senderID", senderID);
            reactContext.startService(GCMService);
        } catch (Exception e) {
            Log.d("EXCEPTION SERVICE::::::", "requestPermissions: " + e);
        }
    }

    @ReactMethod
    public void subscribeToTopic(String topic) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic);
    }

    /** ConnectyCube Group Notifications **/

    @ReactMethod
    public void createCallNotification(ReadableMap details) {
        Bundle bundle = Arguments.toBundle(details);
        mRNPushNotificationHelper.sendToCallNotifications(bundle, true);
    }

    @ReactMethod
    public void createMessageNotification(ReadableMap details) {
        Bundle bundle = Arguments.toBundle(details);
        // If notification ID is not provided by the user, generate one at random
        if (bundle.getString("id") == null) {
            String dialog_id = bundle.getString("dialog_id");
            if (dialog_id == null) {
              return;
            }
            bundle.putInt("notificationID", dialog_id.hashCode());
        } else {
            bundle.putInt("notificationID", Integer.parseInt(bundle.getString("id")));
            bundle.remove("id");
        }

        mRNPushNotificationHelper.sendToMessagingNotifications(bundle);
    }

    @ReactMethod
    public void createGroupNotification(ReadableMap details) {
      Bundle bundle = Arguments.toBundle(details);
      // If notification ID is not provided by the user, generate one at random
      if (bundle.getString("id") == null) {
        bundle.putString("id", String.valueOf(mRandomNumberGenerator.nextInt()));
      }

      mRNPushNotificationHelper.sendToGroupNotifications(bundle);

      System.out.println("[createGroupNotification][arguments]");
      System.out.println(bundle);
    }

    @ReactMethod
    public void presentLocalNotification(ReadableMap details) {
        Bundle bundle = Arguments.toBundle(details);
        // If notification ID is not provided by the user, generate one at random
        if (bundle.getString("id") == null) {
            bundle.putString("id", String.valueOf(mRandomNumberGenerator.nextInt()));
        }
        mRNPushNotificationHelper.sendToNotificationCentre(bundle);
    }

    @ReactMethod
    public void scheduleLocalNotification(ReadableMap details) {
        Bundle bundle = Arguments.toBundle(details);
        // If notification ID is not provided by the user, generate one at random
        if (bundle.getString("id") == null) {
            bundle.putString("id", String.valueOf(mRandomNumberGenerator.nextInt()));
        }
        mRNPushNotificationHelper.sendNotificationScheduled(bundle);
    }

    @ReactMethod
    public void getInitialNotification(Promise promise) {
        WritableMap params = Arguments.createMap();
        Activity activity = getCurrentActivity();
        if (activity != null) {
            Bundle bundle = this.getBundleFromIntent(activity.getIntent());
            if (bundle != null) {
                bundle.putBoolean("foreground", false);
                String bundleString = mJsDelivery.convertJSON(bundle);
                params.putString("dataJSON", bundleString);
            }
        }
        promise.resolve(params);
    }

    @ReactMethod
    public void setApplicationIconBadgeNumber(int number) {
        ApplicationBadgeHelper.INSTANCE.setApplicationIconBadgeNumber(getReactApplicationContext(), number);
    }

    // removed @Override temporarily just to get it working on different versions of RN
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        onActivityResult(requestCode, resultCode, data);
    }

    // removed @Override temporarily just to get it working on different versions of RN
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Ignored, required to implement ActivityEventListener for RN 0.33
    }

    @ReactMethod
    /**
     * Cancels all scheduled local notifications, and removes all entries from the notification
     * centre.
     *
     * We're attempting to keep feature parity with the RN iOS implementation in
     * <a href="https://github.com/facebook/react-native/blob/master/Libraries/PushNotificationIOS/RCTPushNotificationManager.m#L289">RCTPushNotificationManager</a>.
     *
     * @see <a href="https://facebook.github.io/react-native/docs/pushnotificationios.html">RN docs</a>
     */
    public void cancelAllLocalNotifications() {
        mRNPushNotificationHelper.cancelAllScheduledNotifications();
        mRNPushNotificationHelper.clearNotifications();
    }

    @ReactMethod
    /**
     * Cancel scheduled notifications, and removes notifications from the notification centre.
     *
     * Note - as we are trying to achieve feature parity with iOS, this method cannot be used
     * to remove specific alerts from the notification centre.
     *
     * @see <a href="https://facebook.github.io/react-native/docs/pushnotificationios.html">RN docs</a>
     */
    public void cancelLocalNotifications(ReadableMap userInfo) {
        mRNPushNotificationHelper.cancelScheduledNotification(userInfo);
    }

    @ReactMethod
    /**
     * Clear notification from the notification centre.
     */
    public void clearLocalNotification(int notificationID) {
        Log.d("clear[int]: ", notificationID + "");
        mRNPushNotificationHelper.clearNotification(notificationID);
        RNPushNotificationMessageLine.clear(notificationID);
    }


    @ReactMethod
    /**
     * Clear notification from the notification centre.
     */
    public void clearLocalNotificationByString(String notificationID) {
        Log.d("clear[String]: ", notificationID + "");
        int notificationIntID = notificationID.hashCode();
        mRNPushNotificationHelper.clearNotification(notificationIntID);
        RNPushNotificationMessageLine.clear(notificationIntID);
    }

    @ReactMethod
    public void cancelCallNotification() {
        Intent stopCallService = new Intent(applicationContext, CallsService.class);
        applicationContext.stopService(stopCallService);
        mRNPushNotificationHelper.clearNotification(CallsService.CALL_NOTIFICATION_ID);
    }

    @ReactMethod
    public void registerNotificationActions(ReadableArray actions) {
        registerNotificationsReceiveNotificationActions(actions);
    }

    @ReactMethod
    public void backToForeground(ReadableMap notificationBundle) {
        if (isAndroidXOrHigher) {
            mRNPushNotificationHelper.sendToCallNotifications(Arguments.toBundle(notificationBundle), false);
        } else {
            String packageName = applicationContext.getApplicationContext().getPackageName();
            Intent focusIntent = applicationContext.getPackageManager().getLaunchIntentForPackage(packageName).cloneFilter();
            focusIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            Activity activity = getCurrentActivity();
            activity.startActivity(focusIntent);
        }
    }

    @ReactMethod
    public void launchApp(ReadableMap notificationData) {
        if (isAndroidXOrHigher) {
            mRNPushNotificationHelper.sendToCallNotifications(Arguments.toBundle(notificationData), false);
        } else {
            Intent launchIntent = new Intent(applicationContext, getMainActivityClass(applicationContext));
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Bundle launchIntentDateBundle = Arguments.toBundle(notificationData);
            launchIntent.putExtra("notification", launchIntentDateBundle);
            applicationContext.startActivity(launchIntent);
        }
    }

    @ReactMethod
    public void updateMessageNotificationSettings(ReadableMap notificationSettings, Promise promise) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            promise.resolve(true);
            return;
        }
        String notificationType = notificationSettings.getString("??hannelType");
        NotificationChannelManager.CHANNELS channelType = notificationChannelManager.getType(notificationType);
        if (notificationSettings.hasKey("sound")) {
            notificationChannelManager.updateChannelSound(channelType, notificationSettings.getString("sound"));
        }
        if (notificationSettings.hasKey("vibrate")) {
            notificationChannelManager.updateChannelVibration(channelType, notificationSettings.getBoolean("vibrate"));
        }
        promise.resolve(true);
    }

    public Class getMainActivityClass(Context context) {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

}
