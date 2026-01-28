package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * MediaManager - 负责多媒体资源的本地化存储
 * 将 SAF 返回的外部 URI 拷贝到 App 私有目录，防止权限失效或文件丢失
 */
public class MediaManager {
    private static final String TAG = "MediaManager";
    private static final String MEDIA_DIR = "media";

    /**
     * 将外部 URI 对应的图片保存到内部存储
     * 
     * @return 返回保存后的本地 File 对象，失败则返回 null
     */
    public static File saveToInternal(Context context, Uri sourceUri) {
        if (sourceUri == null)
            return null;

        try {
            // 1. 确保内部媒体目录存在
            File mediaDir = new File(context.getFilesDir(), MEDIA_DIR);
            if (!mediaDir.exists()) {
                mediaDir.mkdirs();
            }

            // 2. 生成唯一文件名 (保留原始后缀或使用随机名)
            String fileName = "IMG_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
            File targetFile = new File(mediaDir, fileName);

            // 3. 执行数据流拷贝
            try (InputStream is = context.getContentResolver().openInputStream(sourceUri);
                    OutputStream os = new FileOutputStream(targetFile)) {

                if (is == null)
                    return null;

                byte[] buffer = new byte[8192];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
                os.flush();
            }

            Log.d(TAG, "Media localized to: " + targetFile.getAbsolutePath());
            return targetFile;
        } catch (Exception e) {
            Log.e(TAG, "Failed to localize media", e);
            return null;
        }
    }

    /**
     * 获取媒体根目录
     */
    public static File getMediaDir(Context context) {
        return new File(context.getFilesDir(), MEDIA_DIR);
    }
}
