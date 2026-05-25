package com.cz.fitnessdiary.utils;

import android.app.Activity;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cz.fitnessdiary.R;
import com.google.android.material.snackbar.Snackbar;

/**
 * Unified error handling utility with Snackbar + retry support.
 */
public class ErrorHandler {

    private ErrorHandler() {
    }

    /**
     * Show a Snackbar error with an optional retry action.
     * Falls back to Toast if the root view is unavailable.
     */
    public static void showError(@Nullable View rootView, String message, @Nullable Runnable retryAction) {
        if (rootView == null) {
            return;
        }
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(rootView.getContext().getResources().getColor(R.color.error, null));
        snackbar.setTextColor(rootView.getContext().getResources().getColor(R.color.white, null));
        if (retryAction != null) {
            snackbar.setAction("重试", v -> retryAction.run());
            snackbar.setActionTextColor(rootView.getContext().getResources().getColor(R.color.white, null));
        }
        snackbar.show();
    }

    /**
     * Convenience: show error on a Fragment's root view.
     */
    public static void showError(Fragment fragment, String message, @Nullable Runnable retry) {
        View root = fragment.getView();
        if (root != null) {
            showError(root, message, retry);
        } else if (fragment.getContext() != null) {
            Toast.makeText(fragment.getContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Convenience: show error on an Activity's content view.
     */
    public static void showError(Activity activity, String message, @Nullable Runnable retry) {
        View root = activity.findViewById(android.R.id.content);
        if (root != null) {
            showError(root, message, retry);
        } else {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Show a success/info Snackbar (green background).
     */
    public static void showInfo(@Nullable View rootView, String message) {
        if (rootView == null)
            return;
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT);
        snackbar.setBackgroundTint(rootView.getContext().getResources().getColor(R.color.color_success, null));
        snackbar.setTextColor(rootView.getContext().getResources().getColor(R.color.white, null));
        snackbar.show();
    }

    /**
     * Convenience: show info on a Fragment's root view.
     */
    public static void showInfo(Fragment fragment, String message) {
        View root = fragment.getView();
        if (root != null) {
            showInfo(root, message);
        } else if (fragment.getContext() != null) {
            Toast.makeText(fragment.getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
