package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.cz.fitnessdiary.database.entity.MedicationRecord;
import com.cz.fitnessdiary.repository.MedicationRecordRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.List;

public class MedicationDetailViewModel extends AndroidViewModel {

    private final MedicationRecordRepository repository;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());

    public MedicationDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new MedicationRecordRepository(application);
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
    }

    public LiveData<List<MedicationRecord>> getSelectedDateRecords() {
        return Transformations.switchMap(selectedDate,
                start -> repository.getRecordsByDateRange(start, start + 24L * 60L * 60L * 1000L));
    }

    public LiveData<Integer> getTakenCount() {
        return Transformations.switchMap(selectedDate,
                start -> repository.getTakenCountByDateRange(start, start + 24L * 60L * 60L * 1000L));
    }

    public LiveData<Integer> getUntakenCount() {
        return Transformations.switchMap(selectedDate,
                start -> repository.getUntakenCountByDateRange(start, start + 24L * 60L * 60L * 1000L));
    }

    public void addMedication(String name, String dosage, boolean taken, String note) {
        repository.insert(new MedicationRecord(name, dosage, taken, System.currentTimeMillis(), note));
    }

    public void updateMedication(MedicationRecord record) {
        repository.update(record);
    }

    public void toggleTaken(MedicationRecord record) {
        record.setTaken(!record.isTaken());
        repository.update(record);
    }

    public void deleteMedication(MedicationRecord record) {
        repository.delete(record);
    }
}
