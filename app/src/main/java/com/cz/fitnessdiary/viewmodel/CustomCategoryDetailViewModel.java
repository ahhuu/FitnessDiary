package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.cz.fitnessdiary.database.entity.CustomRecord;
import com.cz.fitnessdiary.database.entity.CustomTracker;
import com.cz.fitnessdiary.repository.CustomRecordRepository;
import com.cz.fitnessdiary.repository.CustomTrackerRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomCategoryDetailViewModel extends AndroidViewModel {

    public static class TrackerMeta {
        public int todayCount;
        public String latestText;

        public TrackerMeta(int todayCount, String latestText) {
            this.todayCount = todayCount;
            this.latestText = latestText;
        }
    }

    private final CustomTrackerRepository trackerRepository;
    private final CustomRecordRepository recordRepository;

    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());
    private final MutableLiveData<Long> selectedTrackerId = new MutableLiveData<>(0L);
    private final MutableLiveData<List<Float>> trendSeries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Map<Long, TrackerMeta>> trackerMeta = new MutableLiveData<>(new HashMap<>());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public CustomCategoryDetailViewModel(@NonNull Application application) {
        super(application);
        trackerRepository = new CustomTrackerRepository(application);
        recordRepository = new CustomRecordRepository(application);
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
        refreshTrend();
        refreshTrackerMeta();
    }

    public void setSelectedTrackerId(long trackerId) {
        selectedTrackerId.setValue(trackerId);
        refreshTrend();
    }

    public LiveData<List<CustomTracker>> getAllTrackers() {
        return trackerRepository.getAllTrackers();
    }

    public LiveData<List<CustomRecord>> getSelectedTrackerRecords() {
        return Transformations.switchMap(selectedDate,
                start -> Transformations.switchMap(selectedTrackerId,
                        trackerId -> recordRepository.getRecordsByTrackerAndDateRange(trackerId, start, start + 24L * 60L * 60L * 1000L)));
    }

    public LiveData<Integer> getSelectedTrackerTodayCount() {
        return Transformations.switchMap(selectedDate,
                start -> Transformations.switchMap(selectedTrackerId,
                        trackerId -> recordRepository.getRecordCountByTrackerAndDateRange(trackerId, start, start + 24L * 60L * 60L * 1000L)));
    }

    public LiveData<Double> getSelectedTrackerTodaySum() {
        return Transformations.switchMap(selectedDate,
                start -> Transformations.switchMap(selectedTrackerId,
                        trackerId -> recordRepository.getSumValueByTrackerAndDateRange(trackerId, start, start + 24L * 60L * 60L * 1000L)));
    }

    public LiveData<List<Float>> getTrendSeries() {
        return trendSeries;
    }

    public LiveData<Map<Long, TrackerMeta>> getTrackerMeta() {
        return trackerMeta;
    }

    public void refreshTrend() {
        Long selected = selectedDate.getValue();
        Long trackerId = selectedTrackerId.getValue();
        if (selected == null || trackerId == null || trackerId <= 0) return;
        long dayStart = DateUtils.getDayStartTimestamp(selected);
        executor.execute(() -> {
            List<Float> values = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                long start = dayStart - i * 24L * 60L * 60L * 1000L;
                List<CustomRecord> list = recordRepository.getRecordsByTrackerAndDateRangeSync(trackerId, start, start + 24L * 60L * 60L * 1000L);
                float total = 0f;
                for (CustomRecord record : list) {
                    total += record.getNumericValue() == null ? 0f : record.getNumericValue().floatValue();
                }
                values.add(total);
            }
            trendSeries.postValue(values);
        });
    }

    public void refreshTrackerMeta() {
        Long selected = selectedDate.getValue();
        if (selected == null) return;
        long dayStart = DateUtils.getDayStartTimestamp(selected);
        long dayEnd = dayStart + 24L * 60L * 60L * 1000L;

        executor.execute(() -> {
            List<CustomTracker> trackers = trackerRepository.getAllTrackersSync();
            Map<Long, TrackerMeta> map = new HashMap<>();
            for (CustomTracker tracker : trackers) {
                int count = recordRepository.getRecordsByTrackerAndDateRangeSync(tracker.getId(), dayStart, dayEnd).size();
                Long latestTs = recordRepository.getLatestTimestampByTrackerSync(tracker.getId());
                String latestText;
                if (latestTs == null) {
                    latestText = "暂无更新";
                } else {
                    long days = Math.max(0L, (System.currentTimeMillis() - latestTs) / (24L * 60L * 60L * 1000L));
                    latestText = days == 0 ? "今日更新" : (days + "天前");
                }
                map.put(tracker.getId(), new TrackerMeta(count, latestText));
            }
            trackerMeta.postValue(map);
        });
    }

    public void addTracker(String name, String unit) {
        trackerRepository.insert(new CustomTracker(name, unit, "#4CAF50", true, 0));
        refreshTrackerMeta();
    }

    public void updateTracker(CustomTracker tracker) {
        trackerRepository.update(tracker);
        refreshTrackerMeta();
    }

    public List<CustomTracker> getAllTrackersSync() {
        return trackerRepository.getAllTrackersSync();
    }

    public void addRecord(long trackerId, Double value, String textValue) {
        recordRepository.insert(new CustomRecord(trackerId, value, textValue, System.currentTimeMillis()));
        refreshTrend();
        refreshTrackerMeta();
    }

    public void updateRecord(CustomRecord record) {
        recordRepository.update(record);
        refreshTrend();
        refreshTrackerMeta();
    }

    public void deleteRecord(CustomRecord record) {
        recordRepository.delete(record);
        refreshTrend();
        refreshTrackerMeta();
    }
}
