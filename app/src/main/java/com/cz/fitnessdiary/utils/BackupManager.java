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
            File walFile = new File(dbFile.getPath() + "-wal");
            File shmFile = new File(dbFile.getPath() + "-shm");
            File mediaDir = MediaManager.getMediaDir(context);
            File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");

            // 2. 创建 ZIP 流
            try (OutputStream os = context.getContentResolver().openOutputStream(targetUri);
                    java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(os)) {

                // 备份数据库文件及其日志
                if (dbFile.exists()) {
                    addToZip(zos, dbFile, "database.db");
                }
                if (walFile.exists()) {
                    addToZip(zos, walFile, "database.db-wal");
                }
                if (shmFile.exists()) {
                    addToZip(zos, shmFile, "database.db-shm");
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

                // 备份 SharedPreferences
                if (prefsDir.exists() && prefsDir.isDirectory()) {
                    File[] prefFiles = prefsDir.listFiles();
                    if (prefFiles != null) {
                        for (File f : prefFiles) {
                            if (f.isFile() && f.getName().endsWith(".xml")) {
                                addToZip(zos, f, "shared_prefs/" + f.getName());
                            }
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
            File walFile = new File(dbFile.getPath() + "-wal");
            File shmFile = new File(dbFile.getPath() + "-shm");
            File mediaDir = MediaManager.getMediaDir(context);
            if (!mediaDir.exists()) mediaDir.mkdirs();
            File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            if (!prefsDir.exists()) prefsDir.mkdirs();

            // 恢复前先清理旧数据库和缓存文件
            if (dbFile.exists()) dbFile.delete();
            if (walFile.exists()) walFile.delete();
            if (shmFile.exists()) shmFile.delete();

            // 3. 读取 ZIP
            try (InputStream is = context.getContentResolver().openInputStream(sourceUri);
                    java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(is)) {

                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals("database.db")) {
                        try (OutputStream os = new FileOutputStream(dbFile)) {
                            copyStream(zis, os);
                        }
                    } else if (entry.getName().equals("database.db-wal")) {
                        try (OutputStream os = new FileOutputStream(walFile)) {
                            copyStream(zis, os);
                        }
                    } else if (entry.getName().equals("database.db-shm")) {
                        try (OutputStream os = new FileOutputStream(shmFile)) {
                            copyStream(zis, os);
                        }
                    } else if (entry.getName().startsWith("media/")) {
                        String fileName = entry.getName().substring(6); // 去掉 "media/"
                        File targetMedia = new File(mediaDir, fileName);
                        try (OutputStream os = new FileOutputStream(targetMedia)) {
                            copyStream(zis, os);
                        }
                    } else if (entry.getName().startsWith("shared_prefs/")) {
                        String fileName = entry.getName().substring(13); // 去掉 "shared_prefs/"
                        File targetPref = new File(prefsDir, fileName);
                        try (OutputStream os = new FileOutputStream(targetPref)) {
                            copyStream(zis, os);
                        }
                    }
                    zis.closeEntry();
                }
            }

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
