package com.dieam.reactnativepushnotification.modules;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dieam.reactnativepushnotification.helpers.ApplicationBadgeHelper;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import in.sriraman.sharedpreferences.RNSharedPreferencesModule;

import static com.dieam.reactnativepushnotification.modules.RNPushNotification.LOG_TAG;

public class RNPushNotificationListenerService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Log.v(LOG_TAG, "[onMessageReceived] start: " + message);

        final Bundle bundle = new Bundle();

        for(Map.Entry<String, String> entry : message.getData().entrySet()) {
            Log.v(LOG_TAG, "[PushData]: key " + entry.getKey() + " value: " + entry.getValue());
            bundle.putString(entry.getKey(), entry.getValue());
        }

        if (bundle.containsKey("callStart") || bundle.containsKey("callEnd")) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                public void run() {
                    // Construct and load our normal React JS code bundle
                    // If it's constructed, send a notification

                    ReactInstanceManager mReactInstanceManager = ((ReactApplication) getApplication()).getReactNativeHost().getReactInstanceManager();
                    ReactContext context = mReactInstanceManager.getCurrentReactContext();

                    if (context != null) {
                        handleRemotePushNotification((ReactApplicationContext) context, bundle);
                    } else {
                        // Otherwise wait for construction, then send the notification
                        mReactInstanceManager.addReactInstanceEventListener(new ReactInstanceManager.ReactInstanceEventListener() {
                            public void onReactContextInitialized(ReactContext context) {
                                handleRemotePushNotification((ReactApplicationContext) context, bundle);
                            }
                        });
                        if (!mReactInstanceManager.hasStartedCreatingInitialContext()) {
                            // Construct it in the background
                            mReactInstanceManager.createReactContextInBackground();
                        }
                    }
                }
            });
            return;
        }

        String from = message.getFrom(); // it is from ... (need help)
        RemoteMessage.Notification remoteNotification = message.getNotification();
        // Putting it from remoteNotification first so it can be overriden if message
        // data has it
        if (remoteNotification != null) {
            // ^ It's null when message is from GCM
            bundle.putString("title", remoteNotification.getTitle());
            bundle.putString("message", remoteNotification.getBody());
        }

        JSONObject data = getPushData(bundle.getString("data"));
        // Copy `twi_body` to `message` to support Twilio
        if (bundle.containsKey("twi_body")) {
            bundle.putString("message", bundle.getString("twi_body"));
        }

        if (data != null) {
            Log.v(LOG_TAG, "[onMessageReceived] data: " + data);

            if (!bundle.containsKey("message")) {
                bundle.putString("message", data.optString("alert", null));
            }
            if (!bundle.containsKey("title")) {
                bundle.putString("title", data.optString("title", null));
            }
            if (!bundle.containsKey("sound")) {
                bundle.putString("soundName", data.optString("sound", null));
            }
            if (!bundle.containsKey("color")) {
                bundle.putString("color", data.optString("color", null));
            }

            final int badge = data.optInt("badge", -1);
            if (badge >= 0) {
                ApplicationBadgeHelper.INSTANCE.setApplicationIconBadgeNumber(this, badge);
            }
        }

        HashMap<String, String> messageMap = parseSenderName(bundle.getString("message"));
        bundle.putString("title", messageMap.get("sender_name"));
        bundle.putString("sender", messageMap.get("sender_name"));
        bundle.putString("message", messageMap.get("message"));
        bundle.putString("sender_id", bundle.getString("user_id"));
        bundle.putInt("date_sent", (int)Math.floor(System.currentTimeMillis() / 1000));

        System.out.println("[onMessageReceived] bundle: " + bundle);

        // We need to run this on the main thread, as the React code assumes that is true.
        // Namely, DevServerHelper constructs a Handler() without a Looper, which triggers:
        // "Can't create handler inside thread that has not called Looper.prepare()"
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                // Construct and load our normal React JS code bundle
                // If it's constructed, send a notification

                ReactInstanceManager mReactInstanceManager = ((ReactApplication) getApplication()).getReactNativeHost().getReactInstanceManager();
                ReactContext context = mReactInstanceManager.getCurrentReactContext();

                if (context != null) {
                    handleRemotePushNotification((ReactApplicationContext) context, bundle);
                } else {
                    // Otherwise wait for construction, then send the notification
                    mReactInstanceManager.addReactInstanceEventListener(new ReactInstanceManager.ReactInstanceEventListener() {
                        public void onReactContextInitialized(ReactContext context) {
                            handleRemotePushNotification((ReactApplicationContext) context, bundle);
                        }
                    });
                    if (!mReactInstanceManager.hasStartedCreatingInitialContext()) {
                        // Construct it in the background
                        mReactInstanceManager.createReactContextInBackground();
                    }
                }
            }
        });
    }


    private JSONObject getPushData(String dataString) {
        try {
            return new JSONObject(dataString);
        } catch (Exception e) {
            return null;
        }
    }

    private void handleRemotePushNotification(ReactApplicationContext context, Bundle bundle) {

        boolean isCallPush = bundle.containsKey("callStart") || bundle.containsKey("callEnd");

        // If notification ID is not provided by the user for push notification, generate one at random
        if (bundle.getString("id") == null && !isCallPush) {
            Random randomNumberGenerator = new Random(System.currentTimeMillis());
            bundle.putString("id", String.valueOf(randomNumberGenerator.nextInt()));
        }

        Boolean isForeground = isApplicationInForeground();
        System.out.println("[In Foreground]");
        System.out.println(isForeground);
        if(isForeground)
        {
            return;
        }

        // RNPushNotificationJsDelivery jsDelivery = new RNPushNotificationJsDelivery(context);
        bundle.putBoolean("foreground", isForeground);
        bundle.putBoolean("userInteraction", false);
        if (!isCallPush) {
            bundle.putString(JSPushNotificationTask.BUNDLE_TASK_NAME_KEY, JSPushNotificationTask.NOTIFY_TASK_KEY);
        } else if (bundle.containsKey("callStart")) {
            bundle.putString(JSPushNotificationTask.BUNDLE_TASK_NAME_KEY, JSPushNotificationTask.START_CALL_TASK_KEY);
        } else if (bundle.containsKey("callEnd")) {
            bundle.putString(JSPushNotificationTask.BUNDLE_TASK_NAME_KEY, JSPushNotificationTask.END_CALL_TASK_KEY);
        }
        // jsDelivery.notifyNotification(bundle);

        // If contentAvailable is set to true, then send out a remote fetch event
//        if (bundle.getString("contentAvailable", "false").equalsIgnoreCase("true")) {
//            jsDelivery.notifyRemoteFetch(bundle);
//        }

        System.out.println("handleRemotePushNotification bundle: " + bundle);
        if (!isCallPush) {
            putPushMessageToRNSharedPreferences(context, bundle);
        }

        Application applicationContext = (Application) context.getApplicationContext();

        /** JS TASK **/

        Intent service = new Intent(applicationContext, JSPushNotificationTask.class);
        service.putExtras(bundle);
        applicationContext.startService(service);

        System.out.println("[JSPushNotificationTask] " + bundle);

        return;
    }

    private HashMap<String, String> parseSenderName(String message)
    {
        int indexOfSep = message.indexOf(":");
        HashMap<String, String> messageMap = new HashMap<String, String>();

        String senderName = message.substring(0, indexOfSep);
        senderName = senderName.replace(" in Private dialog", "");
        senderName = senderName + ":";

        messageMap.put("sender_name", senderName);
        messageMap.put("message", message.substring(indexOfSep + 1));

        System.out.println("[parseSenderName] " + message);

        return messageMap;
    }

    private static void putPushMessageToRNSharedPreferences(ReactApplicationContext context, Bundle pushMessageBundle)
    {
        Log.v(LOG_TAG, "[putPushMessageToRNSharedPreferences]: " + pushMessageBundle);

        RNSharedPreferencesModule sharedPreferences = new RNSharedPreferencesModule(context);

        JSONObject jsonMessage = new JSONObject();
        String message_id = null;

        try {
            message_id = pushMessageBundle.getString("message_id");

            jsonMessage.put("message_id", message_id);
            jsonMessage.put("dialog_id", pushMessageBundle.getString("dialog_id"));
            jsonMessage.put("sender_id", pushMessageBundle.getString("user_id"));
            jsonMessage.put("message", pushMessageBundle.getString("message"));
            jsonMessage.put("date_sent", pushMessageBundle.getInt("date_sent"));
            jsonMessage.put("sender", pushMessageBundle.getString("sender"));
            jsonMessage.put("title", pushMessageBundle.getString("title"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (message_id != null)
        {
            sharedPreferences.setItem(message_id, jsonMessage.toString());
        }
    }

    private boolean isApplicationInForeground() {
        ActivityManager activityManager = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> processInfos = activityManager.getRunningAppProcesses();
        if (processInfos != null) {
            for (RunningAppProcessInfo processInfo : processInfos) {
                if (processInfo.processName.equals(getApplication().getPackageName())) {
                    if (processInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        for (String d : processInfo.pkgList) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
