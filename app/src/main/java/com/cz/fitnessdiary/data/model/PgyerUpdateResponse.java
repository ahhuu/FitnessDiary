package com.cz.fitnessdiary.data.model;

import com.google.gson.annotations.SerializedName;

public class PgyerUpdateResponse {
    
    @SerializedName("code")
    private int code;

    @SerializedName("message")
    private String message;

    @SerializedName("data")
    private UpdateData data;

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public UpdateData getData() {
        return data;
    }

    public static class UpdateData {
        // versionCode
        @SerializedName("buildVersionNo")
        private String buildVersionNo;

        // versionName
        @SerializedName("buildVersion")
        private String buildVersion;

        @SerializedName("buildUpdateDescription")
        private String buildUpdateDescription;

        // 下载链接
        @SerializedName("downloadURL")
        private String downloadURL;

        public String getBuildVersionNo() {
            return buildVersionNo;
        }

        public String getBuildVersion() {
            return buildVersion;
        }

        public String getBuildUpdateDescription() {
            return buildUpdateDescription;
        }

        public String getDownloadURL() {
            return downloadURL;
        }
    }
}
