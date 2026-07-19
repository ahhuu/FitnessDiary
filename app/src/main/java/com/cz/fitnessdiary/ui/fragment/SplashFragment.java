package com.cz.fitnessdiary.ui.fragment;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.NavOptions;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.ExerciseLibraryDataLoader;
import com.cz.fitnessdiary.database.FoodLibraryDataLoader;
import com.cz.fitnessdiary.database.ReminderPresetDataLoader;
import com.cz.fitnessdiary.databinding.FragmentSplashBinding;
import com.cz.fitnessdiary.ui.MainActivity;
import com.cz.fitnessdiary.utils.ReminderManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class SplashFragment extends Fragment {

    private FragmentSplashBinding binding;
    private static final long MIN_ANIMATION_DURATION = 2000L;

    // Use countdown latch to wait for both data loading and minimum time
    private CountDownLatch countDownLatch;
    private int destinationId = R.id.welcomeFragment; // default

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSplashBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Setup Latch: 1 for Data Loading, 1 for Min Animation Duration
        countDownLatch = new CountDownLatch(2);

        startAnimations();
        startDataLoading();
        startMinimumDelayTimer();

        waitForCompletion();
    }

    private void startAnimations() {
        binding.splashAnimation.startAnimation();

        // Fade in text with delay
        ObjectAnimator textAlpha = ObjectAnimator.ofFloat(binding.tvBrandName, "alpha", 0f, 1f);
        textAlpha.setDuration(800);
        textAlpha.setStartDelay(500);

        ObjectAnimator textMove = ObjectAnimator.ofFloat(binding.tvBrandName, "translationY", 20f, 0f);
        textMove.setDuration(800);
        textMove.setStartDelay(500);
        textMove.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator sloganAlpha = ObjectAnimator.ofFloat(binding.tvSlogan, "alpha", 0f, 1f);
        sloganAlpha.setDuration(800);
        sloganAlpha.setStartDelay(800);

        textAlpha.start();
        textMove.start();
        sloganAlpha.start();
    }

    private void startDataLoading() {
        Context appContext = requireContext().getApplicationContext();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Pre-load data
                FoodLibraryDataLoader.loadIfNeeded(appContext);
                ExerciseLibraryDataLoader.loadIfNeeded(appContext);
                ReminderPresetDataLoader.loadIfNeeded(appContext);
                ReminderManager.restoreAllReminders(appContext);

                // Determine destination
                AppDatabase database = AppDatabase.getInstance(appContext);
                int registeredCount = database.userDao().getRegisteredUserCount();

                destinationId = (registeredCount == 0) ? R.id.welcomeFragment : R.id.mainHomeFragment;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                countDownLatch.countDown();
            }
        });
    }

    private void startMinimumDelayTimer() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Thread.sleep(MIN_ANIMATION_DURATION);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                countDownLatch.countDown();
            }
        });
    }

    private void waitForCompletion() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Wait for both conditions to be met
                countDownLatch.await();

                // Switch back to Main Thread for Navigation
                if (getActivity() != null) {
                    getActivity().runOnUiThread(this::navigateToNext);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private void navigateToNext() {
        if (!isAdded() || getView() == null) return;
        binding.splashAnimation.stopAnimation();

        NavController navController = Navigation.findNavController(getView());

        // Define animation to make it smooth (fade in next, fade out splash)
        NavOptions navOptions = new NavOptions.Builder()
                .setPopUpTo(R.id.splashFragment, true) // Remove SplashFragment from back stack
                .setEnterAnim(android.R.anim.fade_in)
                .setExitAnim(android.R.anim.fade_out)
                .build();

        navController.navigate(destinationId, null, navOptions);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).processPendingIntents();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.splashAnimation.stopAnimation();
        binding = null;
    }
}
