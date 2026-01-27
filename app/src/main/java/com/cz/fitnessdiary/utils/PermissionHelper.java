package com.cz.fitnessdiary.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 权限处理助手类
 * 封装存储和媒体权限的请求逻辑
 */
public class PermissionHelper {
    
    // 权限请求码
    public static final int REQUEST_CODE_READ_MEDIA = 1001;
    
    /**
     * 检查是否已授予读取媒体权限
     * 
     * @param context 上下文
     * @return true 如果已授权
     */
    public static boolean hasMediaPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 及以上使用新权限
            return ContextCompat.checkSelfPermission(context, 
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(context, 
                    Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 12 及以下使用旧权限
            return ContextCompat.checkSelfPermission(context, 
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    /**
     * 请求读取媒体权限
     * 
     * @param activity Activity 上下文
     */
    public static void requestMediaPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 及以上请求新权限
            ActivityCompat.requestPermissions(activity,
                    new String[]{
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO
                    },
                    REQUEST_CODE_READ_MEDIA);
        } else {
            // Android 12 及以下请求旧权限
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_READ_MEDIA);
        }
    }
    
    /**
     * 处理权限请求结果
     * 
     * @param requestCode 请求码
     * @param grantResults 授权结果
     * @return true 如果权限被授予
     */
    public static boolean onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == REQUEST_CODE_READ_MEDIA) {
            if (grantResults.length > 0) {
                for (int result : grantResults) {
                    if (result == PackageManager.PERMISSION_GRANTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * 是否应该显示权限说明（用户曾拒绝过）
     * 
     * @param activity Activity 上下文
     * @return true 如果应该显示说明
     */
    public static boolean shouldShowRationale(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.READ_MEDIA_IMAGES) ||
                   ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            return ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }
}
