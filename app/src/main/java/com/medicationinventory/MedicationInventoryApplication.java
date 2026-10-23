package com.medicationinventory;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.medicationinventory.util.AppContainer;
import com.medicationinventory.notifications.InventoryNotificationChannels;
import com.medicationinventory.notifications.InventoryAlertNotificationHistory;
import com.medicationinventory.notifications.InventoryAlertNotificationScheduler;
import com.medicationinventory.notifications.InventoryAlertNotificationTrigger;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.model.domain.UserProfile;



/**
 * Provides Medication Inventory Application behavior used by the MediFlow application.
 */
public class MedicationInventoryApplication extends Application
{
    private AppContainer appContainer;
    private InventoryAlertNotificationTrigger notificationTrigger;
    private FirebaseAuth.AuthStateListener notificationAuthListener;
    private ConnectivityManager.NetworkCallback onlineRevalidationCallback;
    @Override

    public void onCreate()
    {
        super.onCreate();

        AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO
        );

        InventoryNotificationChannels.create(this);

        appContainer = new AppContainer(this);

        registerActivityLifecycleCallbacks(appContainer.getOfflineSessionController());


        InventoryAlertNotificationScheduler notifications =
                new InventoryAlertNotificationScheduler(this);
        notificationTrigger = new InventoryAlertNotificationTrigger(
                appContainer.getAppDatabase(), notifications);
        notificationTrigger.start();
        notifications.schedulePeriodicSafetyCheck();

        notificationAuthListener = auth -> {
            FirebaseUser user = auth.getCurrentUser();
            String uid = user == null ? null : user.getUid();
            InventoryAlertNotificationHistory.syncOwner(this, uid);
            if (uid != null) {
                notifications.scheduleAfterAlertChange();
                appContainer.getInventorySyncScheduler().scheduleAfterSignIn();
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(notificationAuthListener);

        ConnectivityManager connectivity = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            onlineRevalidationCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    if (!appContainer.getOfflineSessionController().isUnlocked()) return;
                    appContainer.getAuthenticationRepository().refreshCurrentUser(
                            new AuthenticationRepository.AuthenticationCallback() {
                                @Override public void onSuccess(UserProfile profile) { }
                                @Override public void onInactiveUser() { }
                                @Override public void onUnauthenticated() { }
                                @Override public void onError(Exception error) {

                                }
                            });
                }
            };
            connectivity.registerDefaultNetworkCallback(onlineRevalidationCallback);
        }


        appContainer.getInventorySyncScheduler().schedulePendingSync();
    }
    public AppContainer getAppContainer()
    {
        return appContainer;
    }
}
