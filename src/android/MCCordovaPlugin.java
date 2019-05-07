/**
 * Copyright 2018 Salesforce, Inc
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * <p>
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * <p>
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.marketingcloud.cordova;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.app.Application;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.salesforce.marketingcloud.MCLogListener;
import com.salesforce.marketingcloud.MarketingCloudSdk;
import com.salesforce.marketingcloud.notifications.NotificationManager;
import com.salesforce.marketingcloud.notifications.NotificationMessage;
import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Map;

public class MCCordovaPlugin extends CordovaPlugin implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static final int PERMISSIONS_REQUEST_FINE_LOCATION = 1;
    static final String TAG = "~!MCCordova";

    private CallbackContext eventsChannel = null;
    private PluginResult cachedNotificationOpenedResult = null;
    private boolean notificationOpenedSubscribed = false;

    private static JSONObject fromMap(Map<String, String> map) throws JSONException {
        JSONObject data = new JSONObject();
        if (map != null && !map.isEmpty()) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                data.put(entry.getKey(), entry.getValue());
            }
        }
        return data;
    }

    private static JSONArray fromCollection(Collection<String> collection) {
        JSONArray data = new JSONArray();
        if (collection != null && !collection.isEmpty()) {
            for (String s : collection) {
                data.put(s);
            }
        }
        return data;
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        handleNotificationMessage(NotificationManager.extractMessage(cordova.getActivity().getIntent()));
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNotificationMessage(NotificationManager.extractMessage(intent));
    }

    private void handleNotificationMessage(@Nullable NotificationMessage message) {
        if (message != null) {
            // Open from push
            PluginResult result;

            try {
                JSONObject eventArgs = new JSONObject();
                eventArgs.put("timeStamp", System.currentTimeMillis());
                JSONObject values = new JSONObject(message.payload());
                if (message.url() != null) {
                    values.put("url", message.url());
                }
                switch (message.type()) {
                case OTHER:
                    values.put("type", "other");
                    break;
                case CLOUD_PAGE:
                    values.put("type", "cloudPage");
                    break;
                case OPEN_DIRECT:
                    values.put("type", "openDirect");
                    break;
                }
                eventArgs.put("values", values);
                eventArgs.put("type", "notificationOpened");

                result = new PluginResult(PluginResult.Status.OK, eventArgs);
                result.setKeepCallback(true);

                if (eventsChannel != null && notificationOpenedSubscribed) {
                    eventsChannel.sendPluginResult(result);
                } else {
                    cachedNotificationOpenedResult = result;
                }
            } catch (Exception e) {
                // NO_OP
            }
        }
    }

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext)
            throws JSONException {
        if (handleStaticAction(action, args, callbackContext)) {
            return true;
        }

        final ActionHandler handler = getActionHandler(action);

        if (handler == null) {
            return false;
        }

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                if (MarketingCloudSdk.isReady()) {
                    handler.execute(MarketingCloudSdk.getInstance(), args, callbackContext);
                } else if (MarketingCloudSdk.isInitializing()) {
                    MarketingCloudSdk.requestSdk(new MarketingCloudSdk.WhenReadyListener() {
                        @Override
                        public void ready(@NonNull MarketingCloudSdk sdk) {
                            handler.execute(sdk, args, callbackContext);
                        }
                    });
                } else {
                    callbackContext.error("MarketingCloudSdk#init has not been called");
                }
            }
        });

        return true;
    }

    private boolean handleStaticAction(String action, JSONArray args, CallbackContext callbackContext) {
        switch (action) {
        case "enableVerboseLogging":
            MarketingCloudSdk.setLogLevel(MCLogListener.VERBOSE);
            MarketingCloudSdk.setLogListener(new MCLogListener.AndroidLogListener());
            callbackContext.success();
            return true;
        case "disableVerboseLogging":
            MarketingCloudSdk.setLogListener(null);
            callbackContext.success();
            return true;
        case "registerEventsChannel":
            registerEventsChannel(callbackContext);
            return true;
        case "subscribe":
            subscribe(args, callbackContext);
            return true;
        default:
            return false;
        }
    }

    private void registerEventsChannel(CallbackContext callbackContext) {
        this.eventsChannel = callbackContext;
        if (notificationOpenedSubscribed) {
            sendCachedPushEvent(eventsChannel);
        }
    }

    private void subscribe(JSONArray args, CallbackContext context) {
        switch (args.optString(0, null)) {
        case "notificationOpened":
            notificationOpenedSubscribed = true;
            if (eventsChannel != null) {
                sendCachedPushEvent(eventsChannel);
            }
            break;
        default:
            // NO_OP
        }
    }

    private void sendCachedPushEvent(CallbackContext callbackContext) {
        if (cachedNotificationOpenedResult != null) {
            callbackContext.sendPluginResult(cachedNotificationOpenedResult);
            cachedNotificationOpenedResult = null;
        }
    }

    private ActionHandler getActionHandler(String action) {
        switch (action) {
        case "getSystemToken":
            return getSystemToken();
        case "isPushEnabled":
            return isPushEnabled();
        case "enablePush":
            return enabledPush();
        case "disablePush":
            return disablePush();
        case "getAttributes":
            return getAttributes();
        case "setAttribute":
            return setAttribute();
        case "clearAttribute":
            return clearAttribute();
        case "addTag":
            return addTag();
        case "removeTag":
            return removeTag();
        case "getTags":
            return getTags();
        case "setContactKey":
            return setContactKey();
        case "getContactKey":
            return getContactKey();
        case "enableGeofence":
            return enableGeofence();
        case "disableGeofence":
            return disableGeofence();
        case "getSDKState":
            return getSDKState();
        case "askForLocationPermissions":
            return askForLocationPermissions();
        default:
            return null;
        }
    }

    private ActionHandler getContactKey() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                callbackContext.success(sdk.getRegistrationManager().getContactKey());
            }
        };
    }

    private ActionHandler setContactKey() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                String contactKey = args.optString(0, null);
                boolean success = sdk.getRegistrationManager().edit().setContactKey(contactKey).commit();
                callbackContext.success(success ? 1 : 0);
            }
        };
    }

    private ActionHandler getTags() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                callbackContext.success(fromCollection(sdk.getRegistrationManager().getTags()));
            }
        };
    }

    private ActionHandler removeTag() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                String tag = args.optString(0, null);
                boolean success = sdk.getRegistrationManager().edit().removeTag(tag).commit();
                callbackContext.success(success ? 1 : 0);
            }
        };
    }

    private ActionHandler addTag() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                String tag = args.optString(0, null);
                boolean success = sdk.getRegistrationManager().edit().addTag(tag).commit();
                callbackContext.success(success ? 1 : 0);
            }
        };
    }

    private ActionHandler clearAttribute() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                String key = args.optString(0, null);
                boolean success = sdk.getRegistrationManager().edit().clearAttribute(key).commit();
                callbackContext.success(success ? 1 : 0);
            }
        };
    }

    private ActionHandler setAttribute() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                String key = args.optString(0, null);
                String value = args.optString(1);
                boolean success = sdk.getRegistrationManager().edit().setAttribute(key, value).commit();
                callbackContext.success(success ? 1 : 0);
            }
        };
    }

    private ActionHandler getAttributes() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                try {
                    callbackContext.success(fromMap(sdk.getRegistrationManager().getAttributes()));
                } catch (JSONException e) {
                    callbackContext.error(e.getMessage());
                }
            }
        };
    }

    private ActionHandler disablePush() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                sdk.getPushMessageManager().disablePush();
                callbackContext.success();
            }
        };
    }

    private ActionHandler enabledPush() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                sdk.getPushMessageManager().enablePush();
                callbackContext.success();
            }
        };
    }

    private ActionHandler isPushEnabled() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                callbackContext.success(sdk.getPushMessageManager().isPushEnabled() ? 1 : 0);
            }
        };
    }

    private ActionHandler getSystemToken() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                callbackContext.success(sdk.getPushMessageManager().getPushToken());
            }
        };
    }

    private ActionHandler getSDKState() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {

                callbackContext.success(sdk.getSdkState().toString());
            }
        };
    }

    private ActionHandler enableGeofence() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                sdk.getRegionMessageManager().enableGeofenceMessaging();
                /*
                 * if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M
                 * ||cordova.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                 * sdk.getRegionMessageManager().enableGeofenceMessaging(); } else {
                 * cordova.requestPermission(thisObject, PERMISSIONS_REQUEST_FINE_LOCATION,
                 * Manifest.permission.ACCESS_FINE_LOCATION); }
                 */

                callbackContext.success();
            }
        };
    }

    private ActionHandler disableGeofence() {
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                sdk.getRegionMessageManager().disableGeofenceMessaging();
                callbackContext.success();
            }
        };
    }

    private ActionHandler askForLocationPermissions() {
        final MCCordovaPlugin thisObject = this;
        return new ActionHandler() {
            @Override
            public void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext) {
                if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.M) {
                    cordova.requestPermission(thisObject, PERMISSIONS_REQUEST_FINE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION);
                }
                callbackContext.success();
            }
        };
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_FINE_LOCATION) {
            // Se activa geofence si el usuario ha aceptado el permiso
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                MarketingCloudSdk sdk = MarketingCloudSdk.getInstance();
                sdk.getRegionMessageManager().enableGeofenceMessaging();
            }
        }
    }

    interface ActionHandler {
        void execute(MarketingCloudSdk sdk, JSONArray args, CallbackContext callbackContext);
    }
}
