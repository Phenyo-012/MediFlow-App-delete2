package com.medicationinventory.ui.issue;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.service.IssueService;



/**
 * Creates the Fefo Review ViewModel with its application dependencies.
 */
public class FefoReviewViewModelFactory
        implements ViewModelProvider.Factory
{
    private final IssueService
            issueService;



    public FefoReviewViewModelFactory(
            IssueService issueService)
    {
        this.issueService =
                issueService;
    }


    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(
            @NonNull Class<T> modelClass)
    {
        if (modelClass.isAssignableFrom(
                FefoReviewViewModel.class
        ))
        {
            return (T)
                    new FefoReviewViewModel(
                            issueService
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class."
        );
    }
}