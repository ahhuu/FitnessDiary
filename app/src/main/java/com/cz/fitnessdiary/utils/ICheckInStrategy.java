package com.cz.fitnessdiary.utils;

import android.content.Context;

public interface ICheckInStrategy {
    boolean isCompleted(Context context, long startMillis, long endMillis);
}
