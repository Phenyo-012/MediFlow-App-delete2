package com.medicationinventory.ui.issue;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.IssueStockRepository;


/**
 * Creates the Issue Stock ViewModel with its application dependencies.
 */
public class IssueStockViewModelFactory
        implements ViewModelProvider.Factory {

    private final IssueStockRepository
            issueStockRepository;


    public IssueStockViewModelFactory(
            IssueStockRepository issueStockRepository
    ) {

        this.issueStockRepository =
                issueStockRepository;
    }


    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(
            @NonNull Class<T> modelClass
    ) {

        if (modelClass.isAssignableFrom(
                IssueStockViewModel.class
        )) {

            return (T)
                    new IssueStockViewModel(
                            issueStockRepository
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class."
        );
    }
}