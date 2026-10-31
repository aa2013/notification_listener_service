package notification.listener.service;

import static notification.listener.service.NotificationUtils.isPermissionGranted;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.content.ActivityNotFoundException;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import notification.listener.service.models.Action;
import notification.listener.service.models.ActionCache;
import android.annotation.SuppressLint;
import android.os.Build;

import java.util.List;
import java.util.Map;

public class NotificationListenerServicePlugin implements FlutterPlugin, ActivityAware, MethodCallHandler, PluginRegistry.ActivityResultListener, EventChannel.StreamHandler {

    private static final String CHANNEL_TAG = "x-slayer/notifications_channel";
    private static final String EVENT_TAG = "x-slayer/notifications_event";

    private static final String LOG_TAG = "NotificationPlugin";

    private MethodChannel channel;
    private EventChannel eventChannel;
    private NotificationReceiver notificationReceiver;
    private Context context;
    private Activity mActivity;

    /** 等待用户从系统设置页返回的权限请求回调，同一时间最多一个 */
    private Result pendingResult;
    final int REQUEST_CODE_FOR_NOTIFICATIONS = 1199;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        context = flutterPluginBinding.getApplicationContext();
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), CHANNEL_TAG);
        channel.setMethodCallHandler(this);
        eventChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), EVENT_TAG);
        eventChannel.setStreamHandler(this);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (call.method.equals("isPermissionGranted")) {
            result.success(isPermissionGranted(context));
        } else if (call.method.equals("requestPermission")) {
            requestPermission(result);
        } else if (call.method.equals("sendReply")) {
            final String message = call.argument("message");
            final int notificationId = call.argument("notificationId");

            final Action action = ActionCache.cachedNotifications.get(notificationId);
            if (action == null) {
                result.error("Notification", "Can't find this cached notification", null);
            }
            try {
                action.sendReply(context, message);
                result.success(true);
            } catch (PendingIntent.CanceledException e) {
                result.success(false);
                e.printStackTrace();
            }
        } else if (call.method.equals("getActiveNotifications")) {
            NotificationListener service = NotificationListener.getInstance();
            if (service != null) {
                List<Map<String, Object>> notifications = service.getActiveNotificationData();
                result.success(notifications);
            } else {
                result.error("ServiceUnavailable", "NotificationService not running", null);
            }
        }
        else {
            result.notImplemented();
        }
    }

    /**
     * 打开系统通知监听设置页，并在用户返回后按实际权限状态完成回调。
     *
     * @param result 本次请求的回调，权限只能由用户在系统设置页授予，因此不能在打开页面时完成
     */
    private void requestPermission(Result result) {
        if (mActivity == null) {
            result.error("NO_ACTIVITY", "The plugin is not attached to an Activity", null);
            return;
        }
        if (pendingResult != null) {
            result.error("REQUEST_IN_PROGRESS", "A permission request is already in progress", null);
            return;
        }

        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        pendingResult = result;
        try {
            mActivity.startActivityForResult(intent, REQUEST_CODE_FOR_NOTIFICATIONS);
        } catch (ActivityNotFoundException e) {
            pendingResult = null;
            Log.e(LOG_TAG, "ActivityNotFoundException: " + e.getMessage());
            result.error("ACTIVITY_NOT_FOUND", "No activity found to handle notification listener settings", null);
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        eventChannel.setStreamHandler(null);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.mActivity = binding.getActivity();
        binding.addActivityResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        // 配置变更后会立即重新附加 Activity，等待中的权限请求保留到设置页返回
        this.mActivity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        // Activity 销毁后不会再回调 onActivityResult，用当前权限状态结束挂起的请求，避免 Dart 侧永久等待
        final Result result = pendingResult;
        pendingResult = null;
        if (result != null) {
            result.success(isPermissionGranted(context));
        }
        this.mActivity = null;
    }
    @SuppressLint("WrongConstant")
    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(NotificationConstants.INTENT);
        notificationReceiver = new NotificationReceiver(events);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.registerReceiver(notificationReceiver, intentFilter, Context.RECEIVER_EXPORTED);
        }else{
            context.registerReceiver(notificationReceiver, intentFilter);
        }
        Intent listenerIntent = new Intent(context, NotificationReceiver.class);
        context.startService(listenerIntent);
        Log.i(LOG_TAG, "Started the notifications tracking service.");
    }

    @Override
    public void onCancel(Object arguments) {
        context.unregisterReceiver(notificationReceiver);
        notificationReceiver = null;
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE_FOR_NOTIFICATIONS) {
            return false;
        }

        final Result result = pendingResult;
        pendingResult = null;
        if (result == null) {
            return false;
        }

        // 系统通知监听设置页返回时 resultCode 恒为 RESULT_CANCELED，权限状态以实际设置为准
        result.success(isPermissionGranted(context));
        return true;
    }
}
