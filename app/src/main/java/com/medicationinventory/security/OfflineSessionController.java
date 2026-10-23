package com.medicationinventory.security;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Window;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.ui.auth.LoginActivity;

import java.lang.ref.WeakReference;



/**
 * Coordinates the in-memory unlocked account with the encrypted offline enrollment and application lifecycle. It accepts access only for the current Firebase UID, starts the inactivity window after validation or device unlock, and routes expired or invalid sessions back to login. The controller does not replace server authorization for cloud operations.
 */
public final class OfflineSessionController implements Application.ActivityLifecycleCallbacks {
    public static final long BACKGROUND_LOCK_MS = 15L * 60L * 1000L;
    public static final long FOREGROUND_INACTIVITY_MS = 15L * 60L * 1000L;

    private final OfflineSessionStore store;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable inactivityCheck = this::onInactivityDeadline;

    private String unlockedUid;
    private long lastInteractionElapsed = -1L;
    private int visibleActivities;
    private long lastBackgroundElapsed = -1L;
    private WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private boolean routing;


    public OfflineSessionController(OfflineSessionStore store) {
        this.store = store;
    }


    /** Reports whether this UID matches Firebase and has a valid encrypted offline enrollment. */
    public boolean mayUnlock(String uid) {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        return firebaseUser != null && uid != null && uid.equals(firebaseUser.getUid())
                && store.isEligible(uid);
    }


    /**
     * Refreshes the protected offline enrollment after a successful server profile check.
     *
     * @return true when the profile is eligible and the local enrollment was saved
     */
    public synchronized boolean onOnlineValidation(UserProfile profile) {
        if (!validOnlineProfile(profile) || !store.enroll(profile.getUid(), profile.getLastValidatedAt())) {
            return false;
        }
        boolean newUnlock = !profile.getUid().equals(unlockedUid);
        unlockedUid = profile.getUid();
        if (newUnlock || lastInteractionElapsed < 0L) {
            lastInteractionElapsed = SystemClock.elapsedRealtime();
        }
        scheduleCheckOnMain();
        return true;
    }


    /** Refreshes the enrollment timestamp after an active profile has been confirmed by the server. */
    public synchronized boolean onServerRevalidation(UserProfile profile) {
        if (!validOnlineProfile(profile) || !store.enroll(profile.getUid(), profile.getLastValidatedAt())) {
            return false;
        }
        scheduleCheckOnMain();
        return true;
    }


    private boolean validOnlineProfile(UserProfile profile) {
        return profile != null && profile.isActive() && profile.getRole() != null
                && profile.getLastValidatedAt() > 0L && mayMatchFirebase(profile.getUid());
    }


    /**
     * Opens the saved session only when its local enrollment and Firebase identity match.
     * This method assumes the device authentication prompt has already succeeded.
     *
     * @return true when this profile becomes the locally unlocked session
     */
    public synchronized boolean unlockOffline(UserProfile profile) {
        if (profile == null || !profile.isActive() || profile.getRole() == null
                || profile.getLastValidatedAt() <= 0L || !mayUnlock(profile.getUid())) {
            return false;
        }
        unlockedUid = profile.getUid();
        lastInteractionElapsed = SystemClock.elapsedRealtime();
        scheduleCheckOnMain();
        return true;
    }

    /** Rechecks both in-memory state and enrollment eligibility, locking immediately if either check fails. */
    public synchronized boolean isUnlocked() {
        if (unlockedUid == null || !mayUnlock(unlockedUid)) {
            lock();
            return false;
        }
        return true;
    }


    /** Ends the in-memory unlock and cancels its scheduled inactivity callback without deleting enrollment. */
    public synchronized void lock() {
        unlockedUid = null;
        lastInteractionElapsed = -1L;
        mainHandler.removeCallbacks(inactivityCheck);
    }


    /** Locks the current session and removes the encrypted enrollment during explicit sign-out. */
    public synchronized void clearOnSignOut() {
        lock();
        store.clear();
    }


    public void routeLockedSession() {
        mainHandler.post(() -> {
            Activity activity = resumedActivity.get();
            if (activity != null) enforce(activity);
        });
    }


    private boolean mayMatchFirebase(String uid) {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        return firebaseUser != null && uid != null && uid.equals(firebaseUser.getUid());
    }

    private boolean isLogin(Activity activity) {
        return activity instanceof LoginActivity;
    }


    synchronized boolean onUserInput(Activity activity) {
        if (isLogin(activity)) return true;
        if (!isUnlocked() || lockIfInactive()) {
            enforce(activity);
            return false;
        }
        lastInteractionElapsed = SystemClock.elapsedRealtime();
        scheduleCheckOnMain();
        return true;
    }


    private synchronized boolean lockIfInactive() {
        if (unlockedUid != null && InactivityTimeoutPolicy.isExpired(
                lastInteractionElapsed, SystemClock.elapsedRealtime(), FOREGROUND_INACTIVITY_MS)) {
            lock();
            return true;
        }
        return false;
    }


    private void scheduleCheckOnMain() {
        // Online authentication can complete off the main thread.
        mainHandler.post(this::scheduleCheck);
    }


    private synchronized void scheduleCheck() {
        mainHandler.removeCallbacks(inactivityCheck);
        Activity activity = resumedActivity.get();
        if (activity == null || isLogin(activity) || unlockedUid == null) return;
        long elapsed = SystemClock.elapsedRealtime() - lastInteractionElapsed;
        long delay = Math.max(0L, FOREGROUND_INACTIVITY_MS - elapsed);
        mainHandler.postDelayed(inactivityCheck, delay);
    }


    private void onInactivityDeadline() {
        Activity activity;
        synchronized (this) {
            activity = resumedActivity.get();
            if (activity == null || isLogin(activity) || unlockedUid == null) return;
            if (!lockIfInactive() && isUnlocked()) {
                scheduleCheck();
                return;
            }
        }
        enforce(activity);
    }


    /** Routes protected screens to LoginActivity when the session is locked, inactive, or bound to another UID. */
    private void enforce(Activity activity) {
        if (isLogin(activity) || activity.isFinishing()) return;
        lockIfInactive();
        if (isUnlocked()) return;
        if (routing) {
            activity.finish();
            return;
        }
        routing = true;
        try {
            Intent intent = new Intent(activity, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish();
        } finally {
            routing = false;
        }
    }


    private void wrapInputCallback(Activity activity) {
        Window window = activity.getWindow();
        Window.Callback current = window.getCallback();
        if (current != null && !(current instanceof InactivityWindowCallback)) {
            window.setCallback(new InactivityWindowCallback(current, activity, this));
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {
        enforce(activity);
    }

    /** Applies the background lock when the first activity returns after the configured interval. */
    @Override public void onActivityStarted(Activity activity) {
        if (visibleActivities == 0 && lastBackgroundElapsed >= 0
                && SystemClock.elapsedRealtime() - lastBackgroundElapsed >= BACKGROUND_LOCK_MS) {
            lock();
        }
        visibleActivities++;
        enforce(activity);
    }

    /** Tracks the foreground screen, enforces access, and starts input/inactivity monitoring. */
    @Override public void onActivityResumed(Activity activity) {
        if (!isLogin(activity)) {
            resumedActivity = new WeakReference<>(activity);
            enforce(activity);
            if (!activity.isFinishing() && isUnlocked()) {
                wrapInputCallback(activity);
                scheduleCheck();
            }
        } else {
            resumedActivity = new WeakReference<>(null);
            mainHandler.removeCallbacks(inactivityCheck);
        }
    }

    @Override public void onActivityPaused(Activity activity) {
        if (resumedActivity.get() == activity) {
            resumedActivity = new WeakReference<>(null);
            mainHandler.removeCallbacks(inactivityCheck);
        }
    }

    @Override public void onActivityStopped(Activity activity) {
        visibleActivities = Math.max(0, visibleActivities - 1);
        if (visibleActivities == 0) lastBackgroundElapsed = SystemClock.elapsedRealtime();
    }

    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
    @Override public void onActivityDestroyed(Activity activity) {
        if (resumedActivity.get() == activity) {
            resumedActivity = new WeakReference<>(null);
            mainHandler.removeCallbacks(inactivityCheck);
        }
    }
}
