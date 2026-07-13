package com.cz.fitnessdiary.config;

import com.cz.fitnessdiary.BuildConfig;

/** Public, non-secret Android configuration for the CloudBase API gateway. */
public final class CloudApiConfig {
    private CloudApiConfig() {
    }

    public static String getEnvironmentId() {
        return BuildConfig.CLOUDBASE_ENV_ID.trim();
    }

    public static String getCloudBaseGatewayUrl() {
        String environmentId = getEnvironmentId();
        return environmentId.isEmpty() ? "" : "https://" + environmentId + ".api.tcloudbasegateway.com";
    }

    public static boolean isConfigured() {
        return !getEnvironmentId().isEmpty();
    }
}
