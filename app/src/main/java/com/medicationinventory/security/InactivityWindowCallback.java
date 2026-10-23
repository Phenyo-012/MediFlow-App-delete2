package com.medicationinventory.security;

import android.app.Activity;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;



/**
 * Enforces Inactivity Window Callback rules that protect account access and locally cached data.
 */
final class InactivityWindowCallback implements Window.Callback {
    private final Window.Callback delegate;
    private final Activity activity;
    private final OfflineSessionController controller;

    InactivityWindowCallback(Window.Callback delegate, Activity activity, OfflineSessionController controller) {
        this.delegate = delegate;
        this.activity = activity;
        this.controller = controller;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (!controller.onUserInput(activity)) return true;
        return delegate.dispatchTouchEvent(event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (!controller.onUserInput(activity)) return true;
        return delegate.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        if (!controller.onUserInput(activity)) return true;
        return delegate.dispatchKeyShortcutEvent(event);
    }

    @Override public boolean dispatchTrackballEvent(MotionEvent event) {
        if (!controller.onUserInput(activity)) return true;
        return delegate.dispatchTrackballEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (!controller.onUserInput(activity)) return true;
        return delegate.dispatchGenericMotionEvent(event);
    }

    @Override public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return delegate.dispatchPopulateAccessibilityEvent(event);
    }
    @Override public View onCreatePanelView(int featureId) {
        return delegate.onCreatePanelView(featureId);
    }
    @Override public boolean onCreatePanelMenu(int featureId, Menu menu) {
        return delegate.onCreatePanelMenu(featureId, menu);
    }
    @Override public boolean onPreparePanel(int featureId, View view, Menu menu) {
        return delegate.onPreparePanel(featureId, view, menu);
    }
    @Override public boolean onMenuOpened(int featureId, Menu menu) {
        return delegate.onMenuOpened(featureId, menu);
    }
    @Override public boolean onMenuItemSelected(int featureId, MenuItem item) {
        return delegate.onMenuItemSelected(featureId, item);
    }
    @Override public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
        delegate.onWindowAttributesChanged(attrs);
    }
    @Override public void onContentChanged() { delegate.onContentChanged(); }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        delegate.onWindowFocusChanged(hasFocus);
    }
    @Override public void onAttachedToWindow() { delegate.onAttachedToWindow(); }
    @Override public void onDetachedFromWindow() { delegate.onDetachedFromWindow(); }
    @Override public void onPanelClosed(int featureId, Menu menu) {
        delegate.onPanelClosed(featureId, menu);
    }
    @Override public boolean onSearchRequested() { return delegate.onSearchRequested(); }
    @Override public boolean onSearchRequested(SearchEvent event) {
        return delegate.onSearchRequested(event);
    }
    @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
        return delegate.onWindowStartingActionMode(callback);
    }
    @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
        return delegate.onWindowStartingActionMode(callback, type);
    }
    @Override public void onActionModeStarted(ActionMode mode) {
        delegate.onActionModeStarted(mode);
    }
    @Override public void onActionModeFinished(ActionMode mode) {
        delegate.onActionModeFinished(mode);
    }
    @Override public void onProvideKeyboardShortcuts(
            List<KeyboardShortcutGroup> groups, Menu menu, int deviceId) {
        delegate.onProvideKeyboardShortcuts(groups, menu, deviceId);
    }
    @Override public void onPointerCaptureChanged(boolean hasCapture) {
        delegate.onPointerCaptureChanged(hasCapture);
    }
}
