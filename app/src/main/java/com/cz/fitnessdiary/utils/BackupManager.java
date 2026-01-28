package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.cz.fitnessdiary.database.AppDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * BackupManager - 数据库备份与恢复工具类
 * 基于 SAF (Storage Access Framework) 实现
 */
public class BackupManager {
    private static final String TAG = "BackupManager";
    private static final String DATABASE_NAME = "fitness_diary_db";

    /**
     * [v1.1] 全量备份：将数据库和媒体文件打包成 ZIP
     */
    public static boolean backupDatabase(Context context, Uri targetUri) {
        try {
            // 1. 整理数据库
            AppDatabase.getInstance(context).close();
            File dbFile = context.getDatabasePath(DATABASE_NAME);
            File mediaDir = MediaManager.getMediaDir(context);

            // 2. 创建 ZIP 流
            try (OutputStream os = context.getContentResolver().openOutputStream(targetUri);
                    java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(os)) {

                // 备份数据库文件
                if (dbFile.exists()) {
                    addToZip(zos, dbFile, "database.db");
                }

                // 备份媒体文件夹
                if (mediaDir.exists() && mediaDir.isDirectory()) {
                    File[] files = mediaDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            addToZip(zos, f, "media/" + f.getName());
                        }
                    }
                }
                zos.finish();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Full backup failed", e);
            return false;
        }
    }

    private static void addToZip(java.util.zip.ZipOutputStream zos, File file, String entryName)
            throws java.io.IOException {
        try (InputStream is = new java.io.FileInputStream(file)) {
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryName);
            zos.putNextEntry(entry);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
            zos.closeEntry();
        }
    }

    /**
     * [v1.1] 全量恢复：从 ZIP 中恢复数据库和媒体文件
     */
    public static boolean restoreDatabase(Context context, Uri sourceUri) {
        try {
            // 1. 关闭数据库
            AppDatabase.getInstance(context).close();

            // 2. 准备路径
            File dbFile = context.getDatabasePath(DATABASE_NAME);
            File mediaDir = MediaManager.getMediaDir(context);
            if (!mediaDir.exists())
                mediaDir.mkdirs();

            // 3. 读取 ZIP
            try (InputStream is = context.getContentResolver().openInputStream(sourceUri);
                    java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(is)) {

                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals("database.db")) {
                        // 恢复数据库
                        try (OutputStream os = new FileOutputStream(dbFile)) {
                            copyStream(zis, os);
                        }
                    } else if (entry.getName().startsWith("media/")) {
                        // 恢复媒体文件
                        String fileName = entry.getName().substring(6); // 去掉 "media/"
                        File targetMedia = new File(mediaDir, fileName);
                        try (OutputStream os = new FileOutputStream(targetMedia)) {
                            copyStream(zis, os);
                        }
                    }
                    zis.closeEntry();
                }
            }

            // 4. 清理 WAL/SHM
            new File(dbFile.getPath() + "-wal").delete();
            new File(dbFile.getPath() + "-shm").delete();

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Full restore failed", e);
            return false;
        }
    }

    private static void copyStream(InputStream is, OutputStream os) throws java.io.IOException {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) > 0) {
            os.write(buffer, 0, len);
        }
        os.flush();
    }
}
