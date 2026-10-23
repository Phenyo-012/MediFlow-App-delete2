package com.medicationinventory.ui.alerts;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.local.model.AlertListRecord;

import com.medicationinventory.data.repository.AlertRepository;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.UserProfile;

import com.medicationinventory.model.enums.AlertType;
import com.medicationinventory.model.enums.Permission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;



/**
 * Streams the alert list for the current account, applies the selected type filter in memory, and
 * routes acknowledgements through the alert repository. The screen state keeps permission/profile
 * information separate from alert rows; acknowledgements are guarded against duplicate requests
 * and reported through a one-shot action state.
 */
public class AlertsViewModel
        extends ViewModel
{
    public enum Filter
    {
        ALL,
        LOW_STOCK,
        NEAR_EXPIRY,
        EXPIRED
    }

    private final LiveData<UserProfile>
            userProfile;

    private final LiveData<List<AlertListRecord>>
            sourceAlerts;

    private final MutableLiveData<Filter>
            selectedFilter =
            new MutableLiveData<>(
                    Filter.ALL
            );

    private final MediatorLiveData<List<AlertListRecord>>
            visibleAlerts =
            new MediatorLiveData<>();

    private List<AlertListRecord> latestAlerts =
            Collections.emptyList();

    private Filter latestFilter =
            Filter.ALL;

    private final AuthenticationRepository authenticationRepository;

    private final UserRepository userRepository;

    private final AlertRepository alertRepository;


    private final MutableLiveData<ActionState>
            actionState =
            new MutableLiveData<>(
                    ActionState.idle()
            );


    private boolean acknowledgementInProgress =
            false;



    public AlertsViewModel(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            AlertRepository alertRepository)
    {
        if (authenticationRepository == null
                ||
                userRepository == null
                ||
                alertRepository == null)
        {
            throw new IllegalArgumentException(
                    "Alerts dependencies are required."
            );
        }

        this.authenticationRepository =
                authenticationRepository;

        this.userRepository =
                userRepository;

        this.alertRepository =
                alertRepository;


        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null)
        {
            userProfile =
                    new MutableLiveData<>(
                            null
                    );
        }
        else
        {
            userProfile =
                    userRepository
                            .observeUser(
                                    uid
                            );
        }


        sourceAlerts =
                alertRepository
                        .observeOpenAlertList();


        visibleAlerts.addSource(
                sourceAlerts,
                alerts ->
                {
                    latestAlerts =
                            alerts == null
                                    ? Collections.emptyList()
                                    : alerts;

                    publishFilteredAlerts();
                }
        );


        visibleAlerts.addSource(
                selectedFilter,
                filter ->
                {
                    latestFilter =
                            filter == null
                                    ? Filter.ALL
                                    : filter;

                    publishFilteredAlerts();
                }
        );
    }

    public LiveData<ActionState>
    getActionState()
    {
        return actionState;
    }

    public void acknowledgeAlert(
            String alertId)
    {
        if (acknowledgementInProgress)
        {
            return;
        }


        if (alertId == null
                ||
                alertId.trim().isEmpty())
        {
            actionState.setValue(
                    ActionState.error(
                            "Alert ID is required."
                    )
            );

            return;
        }


        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null
                ||
                uid.trim().isEmpty())
        {
            actionState.setValue(
                    ActionState.error(
                            "Authentication is required."
                    )
            );

            return;
        }


        acknowledgementInProgress =
                true;


        actionState.setValue(
                ActionState.loading()
        );


        userRepository.getUser(
                uid,
                userProfile ->
                {
                    if (userProfile == null
                            ||
                            !userProfile.isActive())
                    {
                        finishAcknowledgementWithError(
                                "Your user profile is unavailable or inactive."
                        );

                        return;
                    }


                    if (!userProfile.can(
                            Permission.ACKNOWLEDGE_ALERTS
                    ))
                    {
                        finishAcknowledgementWithError(
                                "You do not have permission to acknowledge alerts."
                        );

                        return;
                    }


                    String latestUid =
                            authenticationRepository
                                    .getCurrentUserId();


                    if (latestUid == null
                            ||
                            !uid.equals(
                                    latestUid
                            ))
                    {
                        finishAcknowledgementWithError(
                                "Your authentication session changed. Please try again."
                        );

                        return;
                    }


                    alertRepository
                            .acknowledgeAlert(
                                    alertId,
                                    new AlertRepository.OperationCallback()
                                    {
                                        @Override

                                        public void onSuccess()
                                        {
                                            acknowledgementInProgress =
                                                    false;


                                            actionState.setValue(
                                                    ActionState.success()
                                            );
                                        }


                                        @Override

                                        public void onError(
                                                Exception exception)
                                        {
                                            finishAcknowledgementWithError(
                                                    "Unable to acknowledge this alert."
                                            );
                                        }
                                    }
                            );
                }
        );
    }


    private void finishAcknowledgementWithError(
            String message)
    {
        acknowledgementInProgress =
                false;


        actionState.setValue(
                ActionState.error(
                        message
                )
        );
    }


    public void markActionHandled()
    {
        actionState.setValue(
                ActionState.idle()
        );
    }

    public LiveData<UserProfile>
    getUserProfile()
    {
        return userProfile;
    }


    public LiveData<List<AlertListRecord>>
    getVisibleAlerts()
    {
        return visibleAlerts;
    }


    public LiveData<Filter>
    getSelectedFilter()
    {
        return selectedFilter;
    }


    public void setFilter(
            Filter filter)
    {
        selectedFilter.setValue(
                filter == null
                        ? Filter.ALL
                        : filter
        );
    }



    private void publishFilteredAlerts()
    {
        if (latestFilter == Filter.ALL)
        {
            visibleAlerts.setValue(
                    new ArrayList<>(
                            latestAlerts
                    )
            );

            return;
        }


        AlertType requiredType;


        switch (latestFilter)
        {
            case LOW_STOCK:

                requiredType =
                        AlertType.LOW_STOCK;

                break;


            case NEAR_EXPIRY:

                requiredType =
                        AlertType.NEAR_EXPIRY;

                break;


            case EXPIRED:

                requiredType =
                        AlertType.EXPIRED;

                break;


            default:

                requiredType =
                        null;

                break;
        }


        List<AlertListRecord> result =
                new ArrayList<>();


        for (AlertListRecord alert :
                latestAlerts)
        {
            if (alert != null
                    &&
                    alert.alertType
                            == requiredType)
            {
                result.add(
                        alert
                );
            }
        }


        visibleAlerts.setValue(
                result
        );
    }

    public static class ActionState
    {
        public enum Status
        {
            IDLE,
            LOADING,
            SUCCESS,
            ERROR
        }


        private final Status status;

        private final String message;



        private ActionState(
                Status status,
                String message)
        {
            this.status =
                    status;

            this.message =
                    message;
        }



        public static ActionState idle()
        {
            return new ActionState(
                    Status.IDLE,
                    null
            );
        }



        public static ActionState loading()
        {
            return new ActionState(
                    Status.LOADING,
                    null
            );
        }



        public static ActionState success()
        {
            return new ActionState(
                    Status.SUCCESS,
                    null
            );
        }



        public static ActionState error(
                String message)
        {
            return new ActionState(
                    Status.ERROR,
                    message
            );
        }


        public Status getStatus()
        {
            return status;
        }


        public String getMessage()
        {
            return message;
        }
    }

}
