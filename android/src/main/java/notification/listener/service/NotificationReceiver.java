package notification.listener.service;

import static notification.listener.service.NotificationConstants.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import androidx.annotation.RequiresApi;

import io.flutter.plugin.common.EventChannel.EventSink;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;

public class NotificationReceiver extends BroadcastReceiver {

    private EventSink eventSink;

    public NotificationReceiver(EventSink eventSink) {
        this.eventSink = eventSink;
    }

    @RequiresApi(api = VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onReceive(Context context, Intent intent) {
        String packageName = intent.getStringExtra(PACKAGE_NAME);
        String title = intent.getStringExtra(NOTIFICATION_TITLE);
        String content = intent.getStringExtra(NOTIFICATION_CONTENT);
        byte[] notificationIcon = intent.getByteArrayExtra(NOTIFICATIONS_ICON);
        String notificationExtrasPictureFilePath = intent.getStringExtra(EXTRAS_PICTURE);
        byte[] largeIcon = intent.getByteArrayExtra(NOTIFICATIONS_LARGE_ICON);
        boolean haveExtraPicture = intent.getBooleanExtra(HAVE_EXTRA_PICTURE, false);
        boolean hasRemoved = intent.getBooleanExtra(IS_REMOVED, false);
        boolean canReply = intent.getBooleanExtra(CAN_REPLY, false);
        boolean isOngoing = intent.getBooleanExtra(IS_ONGOING, false);
        int id = intent.getIntExtra(ID, -1);
        byte[] notificationExtrasPicture = null;
        if (haveExtraPicture) {
            notificationExtrasPicture = fileToByteArray(notificationExtrasPictureFilePath);
            deleteFileSafely(notificationExtrasPictureFilePath);
        }

        HashMap<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("packageName", packageName);
        data.put("title", title);
        data.put("content", content);
        data.put("notificationIcon", notificationIcon);
        data.put("notificationExtrasPicture", notificationExtrasPicture);
        data.put("haveExtraPicture", haveExtraPicture);
        data.put("largeIcon", largeIcon);
        data.put("hasRemoved", hasRemoved);
        data.put("canReply", canReply);
        data.put("onGoing", isOngoing);

        eventSink.success(data);
    }
    private static byte[] fileToByteArray(String filePath) {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            Log.e("NotificationReceiver", "file not found: " + filePath);
            return null;
        }

        long fileSize = file.length();

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int totalRead = 0;
            int bytesRead;

            while (totalRead < data.length && (bytesRead = fis.read(data, totalRead, data.length - totalRead)) != -1) {
                totalRead += bytesRead;
            }

            if (totalRead != data.length) {
                Log.e("NotificationReceiver", "Incomplete file reading: " + totalRead + "/" + data.length);
                return null;
            }

            return data;
        } catch (Exception e) {
            Log.e("NotificationReceiver", "read file failed : " + filePath, e);
            return null;
        }
    }

    private static boolean deleteFileSafely(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                return file.delete();
            }
            return true;
        } catch (Exception e) {
            Log.e("NotificationReceiver", "delete file failed!: " + filePath, e);
            return false;
        }
    }
}
