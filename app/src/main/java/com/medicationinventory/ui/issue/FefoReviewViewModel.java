package com.medicationinventory.ui.issue;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.model.domain.IssueFefoDraft;

import com.medicationinventory.service.IssueService;

import java.util.List;



/**
 * Owns observable screen state and user actions for Fefo Review.
 */
public class FefoReviewViewModel
        extends ViewModel
{
    private final IssueService
            issueService;


    private final MutableLiveData<State>
            state =
            new MutableLiveData<>(
                    State.idle()
            );


    private final MutableLiveData<Boolean>
            canOverrideFefo =
            new MutableLiveData<>(
                    false
            );



    public FefoReviewViewModel(
            IssueService issueService)
    {
        this.issueService =
                issueService;
    }


    public LiveData<State> getState()
    {
        return state;
    }


    public LiveData<Boolean>
    getCanOverrideFefo()
    {
        return canOverrideFefo;
    }



    public void loadOverridePermission()
    {
        issueService.checkOverridePermission(
                canOverrideFefo::postValue
        );
    }



    public void confirmIssue(
            IssueFefoDraft draft)
    {
        if (!beginSubmission())
        {
            return;
        }


        issueService.commitDefaultFefoIssue(
                draft,
                createCallback()
        );
    }



    public void confirmOverrideIssue(
            IssueFefoDraft draft,
            List<IssueFefoDraft.Allocation>
                    allocations,
            String overrideReason)
    {
        if (!beginSubmission())
        {
            return;
        }


        issueService.commitOverrideFefoIssue(
                draft,
                allocations,
                overrideReason,
                createCallback()
        );
    }



    private boolean beginSubmission()
    {
        State current =
                state.getValue();


        if (current != null
                &&
                (current.getStatus()
                        == Status.LOADING
                        ||
                        current.getStatus()
                                == Status.SUCCESS))
        {
            return false;
        }


        state.setValue(
                State.loading()
        );


        return true;
    }


    private IssueService.IssueCallback
    createCallback()
    {
        return new IssueService.IssueCallback()
        {
            @Override
            public void onSuccess(
                    IssueService.IssueCommitResult result)
            {
                state.postValue(
                        State.success(
                                result
                        )
                );
            }


            @Override
            public void onError(
                    String message)
            {
                state.postValue(
                        State.error(
                                message
                        )
                );
            }
        };
    }



    public void markSuccessHandled()
    {
        state.setValue(
                State.idle()
        );
    }


    public enum Status
    {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR
    }


    public static class State
    {
        private final Status status;

        private final IssueService.IssueCommitResult
                result;

        private final String message;



        private State(
                Status status,
                IssueService.IssueCommitResult result,
                String message)
        {
            this.status =
                    status;

            this.result =
                    result;

            this.message =
                    message;
        }



        public static State idle()
        {
            return new State(
                    Status.IDLE,
                    null,
                    null
            );
        }



        public static State loading()
        {
            return new State(
                    Status.LOADING,
                    null,
                    null
            );
        }



        public static State success(
                IssueService.IssueCommitResult result)
        {
            return new State(
                    Status.SUCCESS,
                    result,
                    null
            );
        }



        public static State error(
                String message)
        {
            return new State(
                    Status.ERROR,
                    null,
                    message
            );
        }


        public Status getStatus()
        {
            return status;
        }


        public IssueService.IssueCommitResult
        getResult()
        {
            return result;
        }


        public String getMessage()
        {
            return message;
        }
    }
}