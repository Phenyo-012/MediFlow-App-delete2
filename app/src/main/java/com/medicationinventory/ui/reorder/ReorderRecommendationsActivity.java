package com.medicationinventory.ui.reorder;

import android.content.Intent;

import android.os.Bundle;

import android.view.View;

import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import androidx.lifecycle.ViewModelProvider;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.ReorderRecommendationsRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.ReorderRecommendationListItem;
import com.medicationinventory.model.domain.UserProfile;

import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.ui.auth.LoginActivity;
import com.medicationinventory.ui.medicine.MedicineDetailsActivity;

import java.util.List;



/**
 * Implements the Android screen for Reorder Recommendations and connects user actions to application state.
 */
public class ReorderRecommendationsActivity
        extends AppCompatActivity
{
    private RecyclerView
            reorderRecyclerView;

    private View
            reorderEmptyState;

    private View
            reorderErrorState;

    private ProgressBar
            reorderProgress;

    private TextView
            reorderErrorText;

    private MaterialButton
            reorderRetryButton;


    private ReorderRecommendationsAdapter
            adapter;

    private ReorderRecommendationsViewModel
            viewModel;


    private boolean permissionGranted =
            false;


    @Override

    protected void onCreate(
            Bundle savedInstanceState)
    {
        super.onCreate(
                savedInstanceState
        );


        MedicationInventoryApplication application =
                (MedicationInventoryApplication)
                        getApplication();


        AuthenticationRepository authenticationRepository =
                application
                        .getAppContainer()
                        .getAuthenticationRepository();


        if (!authenticationRepository.isSignedIn())
        {
            goToLogin();

            return;
        }


        setContentView(
                R.layout.activity_reorder_recommendations
        );


        bindViews();

        setupList();

        setupViewModel(
                application,
                authenticationRepository
        );

        setupActions();

        observeData();
    }



    private void bindViews()
    {
        reorderRecyclerView =
                findViewById(
                        R.id.reorderRecyclerView
                );


        reorderEmptyState =
                findViewById(
                        R.id.reorderEmptyState
                );


        reorderErrorState =
                findViewById(
                        R.id.reorderErrorState
                );


        reorderProgress =
                findViewById(
                        R.id.reorderProgress
                );


        reorderErrorText =
                findViewById(
                        R.id.reorderErrorText
                );


        reorderRetryButton =
                findViewById(
                        R.id.reorderRetryButton
                );
    }


    private void setupList()
    {
        adapter =
                new ReorderRecommendationsAdapter(
                        this::openMedicine
                );


        reorderRecyclerView
                .setLayoutManager(
                        new LinearLayoutManager(
                                this
                        )
                );


        reorderRecyclerView
                .setAdapter(
                        adapter
                );
    }



    private void setupViewModel(
            MedicationInventoryApplication application,
            AuthenticationRepository authenticationRepository)
    {
        UserRepository userRepository =
                application
                        .getAppContainer()
                        .getUserRepository();


        ReorderRecommendationsRepository repository =
                application
                        .getAppContainer()
                        .getReorderRecommendationsRepository();


        ReorderRecommendationsViewModelFactory factory =
                new ReorderRecommendationsViewModelFactory(
                        authenticationRepository,
                        userRepository,
                        repository
                );


        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                ReorderRecommendationsViewModel.class
                        );
    }



    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.reorderBackButton
                );


        backButton.setOnClickListener(
                view ->
                        finish()
        );


        reorderRetryButton.setOnClickListener(
                view ->
                {
                    showLoading();

                    viewModel.retry();
                }
        );
    }



    private void observeData()
    {
        viewModel
                .getUserProfile()
                .observe(
                        this,
                        this::applyPermission
                );


        viewModel
                .getRecommendations()
                .observe(
                        this,
                        this::showRecommendations
                );


        viewModel
                .getError()
                .observe(
                        this,
                        this::showError
                );
    }



    private void applyPermission(
            UserProfile profile)
    {
        if (profile == null
                ||
                !profile.can(
                        Permission.VIEW_STOCK_LEVELS
                ))
        {
            Toast.makeText(
                    this,
                    "You do not have permission to view stock-level recommendations.",
                    Toast.LENGTH_SHORT
            ).show();


            finish();

            return;
        }


        permissionGranted =
                true;
    }



    private void showRecommendations(
            List<ReorderRecommendationListItem> recommendations)
    {
        if (!permissionGranted)
        {
            return;
        }


        adapter.submitList(
                recommendations
        );


        reorderProgress.setVisibility(
                View.GONE
        );


        reorderErrorState.setVisibility(
                View.GONE
        );


        int count =
                recommendations == null
                        ? 0
                        : recommendations.size();


        if (count == 0)
        {
            reorderRecyclerView.setVisibility(
                    View.GONE
            );


            reorderEmptyState.setVisibility(
                    View.VISIBLE
            );
        }
        else
        {
            reorderEmptyState.setVisibility(
                    View.GONE
            );


            reorderRecyclerView.setVisibility(
                    View.VISIBLE
            );
        }
    }



    private void showError(
            String message)
    {
        if (message == null
                ||
                message.trim().isEmpty())
        {
            return;
        }


        reorderProgress.setVisibility(
                View.GONE
        );


        reorderRecyclerView.setVisibility(
                View.GONE
        );


        reorderEmptyState.setVisibility(
                View.GONE
        );


        reorderErrorState.setVisibility(
                View.VISIBLE
        );


        reorderErrorText.setText(
                message
        );
    }



    private void showLoading()
    {
        reorderRecyclerView.setVisibility(
                View.GONE
        );


        reorderEmptyState.setVisibility(
                View.GONE
        );


        reorderErrorState.setVisibility(
                View.GONE
        );


        reorderProgress.setVisibility(
                View.VISIBLE
        );
    }



    private void openMedicine(
            ReorderRecommendationListItem recommendation)
    {
        if (recommendation == null
                ||
                recommendation.getMedicineId() == null)
        {
            return;
        }


        Intent intent =
                new Intent(
                        this,
                        MedicineDetailsActivity.class
                );


        intent.putExtra(
                MedicineDetailsActivity.EXTRA_MEDICINE_ID,
                recommendation.getMedicineId()
        );


        startActivity(
                intent
        );
    }



    private void goToLogin()
    {
        Intent intent =
                new Intent(
                        this,
                        LoginActivity.class
                );


        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );


        startActivity(
                intent
        );


        finish();
    }
}