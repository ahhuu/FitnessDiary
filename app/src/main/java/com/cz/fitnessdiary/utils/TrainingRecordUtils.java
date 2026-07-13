package com.cz.fitnessdiary.utils;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.ExtraExerciseLog;
import com.cz.fitnessdiary.database.entity.TrainingPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Common read model for completed training history.
 * Planned logs and one-day extra exercises are deliberately merged only here;
 * plan progress/counting code must continue to read DailyLog directly.
 */
public final class TrainingRecordUtils {

    private TrainingRecordUtils() {
    }

    public static final class Entry {
        public final long date;
        public final int planId;
        public final boolean extra;
        public final String name;
        public final String category;
        public final int sets;
        public final int reps;
        public final float weight;
        public final int duration;

        private Entry(long date, int planId, boolean extra, String name, String category,
                      int sets, int reps, float weight, int duration) {
            this.date = date;
            this.planId = planId;
            this.extra = extra;
            this.name = name;
            this.category = category;
            this.sets = sets;
            this.reps = reps;
            this.weight = weight;
            this.duration = duration;
        }
    }

    public static List<Entry> getCompletedEntries(AppDatabase db, long startDate, long endDate) {
        if (db == null) return Collections.emptyList();

        List<DailyLog> logs = db.dailyLogDao().getLogsByDateRangeSync(startDate, endDate);
        List<TrainingPlan> plans = db.trainingPlanDao().getAllPlansList();
        Map<Integer, TrainingPlan> planMap = new HashMap<>();
        if (plans != null) {
            for (TrainingPlan plan : plans) planMap.put(plan.getPlanId(), plan);
        }

        List<Entry> entries = new ArrayList<>();
        if (logs != null) {
            for (DailyLog log : logs) {
                if (!log.isCompleted()) continue;
                TrainingPlan plan = planMap.get(log.getPlanId());
                if (plan == null) continue;
                entries.add(new Entry(
                        log.getDate(),
                        plan.getPlanId(),
                        false,
                        plan.getName(),
                        plan.getCategory(),
                        log.getActualSets() > 0 ? log.getActualSets() : plan.getSets(),
                        log.getActualReps() > 0 ? log.getActualReps() : plan.getReps(),
                        log.getActualWeight() > 0 ? log.getActualWeight() : plan.getWeight(),
                        log.getDuration() > 0 ? log.getDuration() : plan.getDuration()));
            }
        }

        List<ExtraExerciseLog> extras = db.extraExerciseLogDao()
                .getLogsByDateRangeSync(startDate, endDate);
        if (extras != null) {
            for (ExtraExerciseLog extra : extras) {
                if (!extra.isCompleted()) continue;
                entries.add(new Entry(
                        extra.getDate(),
                        0,
                        true,
                        extra.getName(),
                        extra.getCategory(),
                        extra.getSets(),
                        extra.getReps(),
                        extra.getWeight(),
                        extra.getDuration()));
            }
        }
        return entries;
    }
}
