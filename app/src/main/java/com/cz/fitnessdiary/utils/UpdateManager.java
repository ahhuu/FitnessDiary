package com.cz.fitnessdiary.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import com.cz.fitnessdiary.BuildConfig;
import com.cz.fitnessdiary.data.model.PgyerUpdateResponse;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateManager {

    private static final String PGYER_CHECK_URL = "https://www.pgyer.com/apiv2/app/check";

    /**
     * 检查版本更新
     *
     * @param context  上下文
     * @param isManual 是否是手动检查（手动检查时，如果是最新版会 Toast 提示）
     */
    public static void checkUpdate(Context context, boolean isManual) {
        String apiKey = BuildConfig.PGYER_API_KEY;
        String appKey = BuildConfig.PGYER_APP_KEY;

        if (TextUtils.isEmpty(apiKey) || TextUtils.isEmpty(appKey)) {
            if (isManual) {
                Toast.makeText(context, "未配置蒲公英 API Key，无法检查更新", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (isManual) {
            Toast.makeText(context, "正在检查更新...", Toast.LENGTH_SHORT).show();
        }

        OkHttpClient client = new OkHttpClient();
        FormBody body = new FormBody.Builder()
                .add("_api_key", apiKey)
                .add("appKey", appKey)
                .build();

        Request request = new Request.Builder()
                .url(PGYER_CHECK_URL)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (isManual) {
                    runOnMainThread(() -> Toast.makeText(context, "检查更新失败，请检查网络", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    if (isManual) {
                        runOnMainThread(() -> Toast.makeText(context, "检查更新失败: 服务器异常", Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                String responseBody = response.body().string();
                try {
                    Gson gson = new Gson();
                    PgyerUpdateResponse updateResponse = gson.fromJson(responseBody, PgyerUpdateResponse.class);

                    if (updateResponse != null && updateResponse.getCode() == 0 && updateResponse.getData() != null) {
                        PgyerUpdateResponse.UpdateData data = updateResponse.getData();
                        String onlineVersionNoStr = data.getBuildVersionNo();
                        
                        if (!TextUtils.isEmpty(onlineVersionNoStr)) {
                            int onlineVersionNo = Integer.parseInt(onlineVersionNoStr);
                            int currentVersionNo = BuildConfig.VERSION_CODE;

                            if (onlineVersionNo > currentVersionNo) {
                                // 发现新版本
                                runOnMainThread(() -> showUpdateDialog(context, data));
                            } else {
                                if (isManual) {
                                    runOnMainThread(() -> Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show());
                                }
                            }
                        }
                    } else {
                        if (isManual) {
                            runOnMainThread(() -> Toast.makeText(context, "检查更新失败: " + 
                                    (updateResponse != null ? updateResponse.getMessage() : "未知错误"), Toast.LENGTH_SHORT).show());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (isManual) {
                        runOnMainThread(() -> Toast.makeText(context, "解析更新数据失败", Toast.LENGTH_SHORT).show());
                    }
                }
            }
        });
    }

    private static void showUpdateDialog(Context context, PgyerUpdateResponse.UpdateData data) {
        if (context instanceof Activity && ((Activity) context).isFinishing()) {
            return;
        }

        String title = "发现新版本: v" + data.getBuildVersion();
        String message = data.getBuildUpdateDescription();
        if (TextUtils.isEmpty(message)) {
            message = "有新版本可以下载了，快去更新吧！";
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("立即更新", (dialog, which) -> {
                    // 跳转到浏览器下载
                    String downloadUrl = data.getDownloadURL();
                    if (!TextUtils.isEmpty(downloadUrl)) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } else {
                        Toast.makeText(context, "下载链接无效", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("稍后", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private static void runOnMainThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }
}
