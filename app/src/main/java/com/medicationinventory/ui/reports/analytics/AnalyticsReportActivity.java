package com.medicationinventory.ui.reports.analytics;


import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;

import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineClassificationRepository;

import com.medicationinventory.model.domain.AnalyticsReport;
import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.domain.AbcVedAnalytics;

import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.ui.auth.LoginActivity;

import java.text.NumberFormat;

import java.util.List;
import java.util.Locale;
import java.util.Collections;



/**
 * Implements the Analytics Report Activity behavior for its Android screen and the application state it presents.
 */
public class AnalyticsReportActivity
        extends AppCompatActivity
{
    private AnalyticsReportViewModel
            viewModel;
    private MedicineClassificationRepository classificationRepository;
    private UserProfile currentProfile;


    private ProgressBar
            progressBar;


    private View
            content;


    private TextView
            errorText;


    private TextView
            lastSyncText;


    private TextView
            activeMedicinesValue;


    private TextView
            usableStockValue;


    private TextView
            inStockValue;


    private TextView
            lowStockValue;


    private TextView
            outOfStockValue;


    private TextView
            expiringSoonValue;


    private TextView
            expiredStockValue;


    private TextView
            activeAlertsValue;


    private TextView
            movementPeriodDescription;


    private TextView
            categoryDistributionText;


    private TextView
            stockStatusDistributionText;


    private TextView
            expiryDistributionText;


    private TextView
            alertDistributionText;


    private TextView
            movementSummaryText;


    private TextView
            movementTrendText;


    private TextView
            topIssuedText;

    private TextView
            alertOverviewSummaryText;

    private View
            abcVedAnalyticsSection;


    private TextView
            abcVedCoverageText;

    private TextView
            abcVedRefreshStatusText;

    private TextView
            abcDistributionText;

    private TextView
            vedDistributionText;


    private TextView
            abcVedAvText;

    private TextView
            abcVedAeText;

    private TextView
            abcVedAdText;

    private TextView
            abcVedBvText;

    private TextView
            abcVedBeText;

    private TextView
            abcVedBdText;

    private TextView
            abcVedCvText;

    private TextView
            abcVedCeText;

    private TextView
            abcVedCdText;


    private FrameLayout
            abcDistributionChartContainer;

    private FrameLayout
            vedDistributionChartContainer;


    private AnalyticsChartView
            abcDistributionChart;

    private AnalyticsChartView
            vedDistributionChart;


    private AbcVedAnalytics
            latestAbcVedAnalytics;


    private boolean
            abcVedPermissionGranted =
            false;

    private FrameLayout
            categoryChartContainer;


    private FrameLayout
            stockStatusChartContainer;


    private FrameLayout
            expiryChartContainer;


    private FrameLayout
            alertChartContainer;


    private FrameLayout
            movementTrendChartContainer;


    private FrameLayout
            topIssuedChartContainer;


    private AnalyticsChartView
            categoryChart;


    private AnalyticsChartView
            stockStatusChart;


    private AnalyticsChartView
            expiryChart;


    private AnalyticsChartView
            alertChart;


    private AnalyticsChartView
            movementTrendChart;


    private AnalyticsChartView
            topIssuedChart;


    private MaterialButtonToggleGroup
            periodToggle;


    private AnalyticsReport
            latestReport;

    private TextView
            healthyStockRateText;

    private TextView
            reorderAttentionRateText;

    private TextView
            expiryExposureRateText;

    private TextView
            movementTypeDistributionText;


    private FrameLayout
            movementTypeChartContainer;


    private AnalyticsChartView
            movementTypeChart;


    private boolean
            permissionGranted =
            false;


    private final NumberFormat
            numberFormat =
            NumberFormat.getIntegerInstance(
                    Locale.getDefault()
            );


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


        if (!authenticationRepository
                .isSignedIn())
        {
            goToLogin();

            return;
        }


        setContentView(
                R.layout.activity_analytics_report
        );


        bindViews();

        attachCharts();


        AnalyticsReportViewModelFactory factory =
                new AnalyticsReportViewModelFactory(
                        authenticationRepository,
                        application
                                .getAppContainer()
                                .getUserRepository(),
                        application
                                .getAppContainer()
                                .getAnalyticsReportRepository(),
                        application
                                .getAppContainer()
                                .getSyncMetadataRepository(),
                        application
                                .getAppContainer()
                                .getMedicineRepository(),
                        application
                                .getAppContainer()
                                .getMedicineClassificationRepository()
                );


        classificationRepository = application.getAppContainer()
                .getMedicineClassificationRepository();

        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                AnalyticsReportViewModel.class
                        );


        setupActions();

        observeViewModel();
    }


    private void bindViews()
    {
        progressBar =
                findViewById(
                        R.id.analyticsProgress
                );


        content =
                findViewById(
                        R.id.analyticsContent
                );


        errorText =
                findViewById(
                        R.id.analyticsErrorText
                );


        lastSyncText =
                findViewById(
                        R.id.analyticsLastSyncText
                );


        activeMedicinesValue =
                findViewById(
                        R.id.activeMedicinesValue
                );


        usableStockValue =
                findViewById(
                        R.id.usableStockValue
                );


        inStockValue =
                findViewById(
                        R.id.inStockValue
                );


        lowStockValue =
                findViewById(
                        R.id.lowStockValue
                );


        outOfStockValue =
                findViewById(
                        R.id.outOfStockValue
                );


        expiringSoonValue =
                findViewById(
                        R.id.expiringSoonValue
                );


        expiredStockValue =
                findViewById(
                        R.id.expiredStockValue
                );


        activeAlertsValue =
                findViewById(
                        R.id.activeAlertsValue
                );


        movementPeriodDescription =
                findViewById(
                        R.id.movementPeriodDescription
                );


        categoryDistributionText =
                findViewById(
                        R.id.categoryDistributionText
                );


        stockStatusDistributionText =
                findViewById(
                        R.id.stockStatusDistributionText
                );


        expiryDistributionText =
                findViewById(
                        R.id.expiryDistributionText
                );


        alertDistributionText =
                findViewById(
                        R.id.alertDistributionText
                );


        movementSummaryText =
                findViewById(
                        R.id.movementSummaryText
                );


        movementTrendText =
                findViewById(
                        R.id.movementTrendText
                );


        topIssuedText =
                findViewById(
                        R.id.topIssuedText
                );


        categoryChartContainer =
                findViewById(
                        R.id.categoryChartContainer
                );


        stockStatusChartContainer =
                findViewById(
                        R.id.stockStatusChartContainer
                );


        expiryChartContainer =
                findViewById(
                        R.id.expiryChartContainer
                );


        alertChartContainer =
                findViewById(
                        R.id.alertChartContainer
                );


        movementTrendChartContainer =
                findViewById(
                        R.id.movementTrendChartContainer
                );


        topIssuedChartContainer =
                findViewById(
                        R.id.topIssuedChartContainer
                );


        periodToggle =
                findViewById(
                        R.id.analyticsPeriodToggle
                );

        healthyStockRateText =
                findViewById(
                        R.id.healthyStockRateText
                );


        reorderAttentionRateText =
                findViewById(
                        R.id.reorderAttentionRateText
                );


        expiryExposureRateText =
                findViewById(
                        R.id.expiryExposureRateText
                );


        movementTypeDistributionText =
                findViewById(
                        R.id.movementTypeDistributionText
                );


        movementTypeChartContainer =
                findViewById(
                        R.id.movementTypeChartContainer
                );

        alertOverviewSummaryText =
                findViewById(
                        R.id.alertOverviewSummaryText
                );

        abcVedAnalyticsSection =
                findViewById(
                        R.id.abcVedAnalyticsSection
                );


        abcVedCoverageText =
                findViewById(
                        R.id.abcVedCoverageText
                );


        abcVedRefreshStatusText =
                findViewById(
                        R.id.abcVedRefreshStatusText
                );


        abcDistributionText =
                findViewById(
                        R.id.abcDistributionText
                );


        vedDistributionText =
                findViewById(
                        R.id.vedDistributionText
                );


        abcDistributionChartContainer =
                findViewById(
                        R.id.abcDistributionChartContainer
                );


        vedDistributionChartContainer =
                findViewById(
                        R.id.vedDistributionChartContainer
                );


        abcVedAvText =
                findViewById(
                        R.id.abcVedAvText
                );

        abcVedAeText =
                findViewById(
                        R.id.abcVedAeText
                );

        abcVedAdText =
                findViewById(
                        R.id.abcVedAdText
                );

        abcVedBvText =
                findViewById(
                        R.id.abcVedBvText
                );

        abcVedBeText =
                findViewById(
                        R.id.abcVedBeText
                );

        abcVedBdText =
                findViewById(
                        R.id.abcVedBdText
                );

        abcVedCvText =
                findViewById(
                        R.id.abcVedCvText
                );

        abcVedCeText =
                findViewById(
                        R.id.abcVedCeText
                );

        abcVedCdText =
                findViewById(
                        R.id.abcVedCdText
                );
    }


    private void attachCharts()
    {
        categoryChart =
                new AnalyticsChartView(
                        this
                );


        stockStatusChart =
                new AnalyticsChartView(
                        this
                );


        expiryChart =
                new AnalyticsChartView(
                        this
                );


        alertChart =
                new AnalyticsChartView(
                        this
                );


        movementTrendChart =
                new AnalyticsChartView(
                        this
                );


        topIssuedChart =
                new AnalyticsChartView(
                        this
                );

        movementTypeChart =
                new AnalyticsChartView(
                        this
                );

        abcDistributionChart =
                new AnalyticsChartView(
                        this
                );


        vedDistributionChart =
                new AnalyticsChartView(
                        this
                );

        addChart(
                categoryChartContainer,
                categoryChart
        );


        addChart(
                stockStatusChartContainer,
                stockStatusChart
        );


        addChart(
                expiryChartContainer,
                expiryChart
        );


        addChart(
                alertChartContainer,
                alertChart
        );


        addChart(
                movementTrendChartContainer,
                movementTrendChart
        );


        addChart(
                topIssuedChartContainer,
                topIssuedChart
        );

        addChart(
                movementTypeChartContainer,
                movementTypeChart
        );

        addChart(
                abcDistributionChartContainer,
                abcDistributionChart
        );


        addChart(
                vedDistributionChartContainer,
                vedDistributionChart
        );


        categoryChart.setVisibility(
                View.GONE
        );


        stockStatusChart.setVisibility(
                View.GONE
        );


        expiryChart.setVisibility(
                View.GONE
        );


        alertChart.setVisibility(
                View.GONE
        );


        movementTrendChart.setVisibility(
                View.GONE
        );


        topIssuedChart.setVisibility(
                View.GONE
        );

        movementTypeChart.setVisibility(
                View.GONE
        );

        abcDistributionChart.setVisibility(
                View.GONE
        );


        vedDistributionChart.setVisibility(
                View.GONE
        );
    }



    private void addChart(
            FrameLayout container,
            AnalyticsChartView chart)
    {
        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );


        container.addView(
                chart,
                params
        );
    }


    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.analyticsBackButton
                );


        backButton.setOnClickListener(
                view ->
                        finish()
        );


        MaterialButton refreshButton =
                findViewById(
                        R.id.analyticsRefreshButton
                );


        refreshButton.setOnClickListener(
                view ->
                {
                    if (!permissionGranted)
                    {
                        return;
                    }


                    showLoading();


                    viewModel.refresh();
                }
        );


        periodToggle
                .addOnButtonCheckedListener(
                        (
                                group,
                                checkedId,
                                isChecked
                        ) ->
                        {
                            if (!isChecked
                                    ||
                                    !permissionGranted)
                            {
                                return;
                            }


                            int days;


                            if (checkedId
                                    ==
                                    R.id.period7Button)
                            {
                                days =
                                        AnalyticsReportViewModel
                                                .WINDOW_7_DAYS;
                            }
                            else if (checkedId
                                    ==
                                    R.id.period90Button)
                            {
                                days =
                                        AnalyticsReportViewModel
                                                .WINDOW_90_DAYS;
                            }
                            else
                            {
                                days =
                                        AnalyticsReportViewModel
                                                .WINDOW_30_DAYS;
                            }


                            movementPeriodDescription
                                    .setText(
                                            "Movement analytics: last "
                                                    +
                                                    days
                                                    +
                                                    " days"
                                    );


                            showLoading();


                            viewModel
                                    .setMovementWindowDays(
                                            days
                                    );
                        }
                );
    }



    private void observeViewModel()
    {
        viewModel
                .getUserProfile()
                .observe(this, profile -> {
                    currentProfile = profile;
                    applyPermission(profile);
                });
        classificationRepository.observeRestrictedAccess()
                .observe(this, ignored -> applyPermission(currentProfile));


        viewModel
                .getReport()
                .observe(
                        this,
                        report ->
                        {
                            latestReport =
                                    report;


                            if (permissionGranted
                                    &&
                                    report != null)
                            {
                                renderReport(
                                        report
                                );
                            }
                        }
                );


        viewModel
                .getError()
                .observe(
                        this,
                        message ->
                        {
                            if (!permissionGranted
                                    ||
                                    message == null
                                    ||
                                    message.trim()
                                            .isEmpty())
                            {
                                return;
                            }


                            progressBar.setVisibility(
                                    View.GONE
                            );


                            errorText.setText(
                                    message
                            );


                            errorText.setVisibility(
                                    View.VISIBLE
                            );
                        }
                );


        viewModel
                .getLastSuccessfulSyncAt()
                .observe(
                        this,
                        this::renderLastSync
                );

        viewModel
                .getAbcVedAnalytics()
                .observe(
                        this,
                        analytics ->
                        {
                            latestAbcVedAnalytics =
                                    analytics;


                            if (abcVedPermissionGranted
                                    &&
                                    analytics != null)
                            {
                                renderAbcVedAnalytics(
                                        analytics
                                );
                            }
                        }
                );


        viewModel
                .getAbcVedRefreshStatus()
                .observe(
                        this,
                        status ->
                        {
                            if (!abcVedPermissionGranted)
                            {
                                return;
                            }


                            if (status == null
                                    ||
                                    status.trim()
                                            .isEmpty())
                            {
                                abcVedRefreshStatusText.setText(
                                        "Restricted local classification cache"
                                );
                            }
                            else
                            {
                                abcVedRefreshStatusText.setText(
                                        status
                                );
                            }
                        }
                );
    }



    private void applyPermission(
            UserProfile profile)
    {

        if (profile == null || !profile.isActive())
        {
            permissionGranted = false;
            latestReport = null;
            content.setVisibility(View.GONE);
            applyAbcVedPermission(false);
            return;
        }


        if (!profile.can(
                Permission.GENERATE_REPORTS
        ))
        {
            permissionGranted = false;
            latestReport = null;
            content.setVisibility(View.GONE);
            applyAbcVedPermission(false);


            Toast.makeText(
                    this,
                    "You do not have permission to generate analytics reports.",
                    Toast.LENGTH_SHORT
            ).show();


            finish();

            return;
        }


        /*
         * ABC/VED is independently protected.
         */
        applyAbcVedPermission(
                profile.can(
                        Permission.VIEW_ABC_VED
                ) && classificationRepository.isRestrictedAccessGranted()
        );


        if (permissionGranted)
        {
            return;
        }


        permissionGranted =
                true;


        viewModel.start();


        if (latestReport != null)
        {
            renderReport(
                    latestReport
            );
        }
        else
        {
            showLoading();
        }
    }

    private void applyAbcVedPermission(
            boolean granted)
    {
        abcVedPermissionGranted =
                granted;


        abcVedAnalyticsSection
                .setVisibility(
                        granted
                                ? View.VISIBLE
                                : View.GONE
                );


        viewModel.setAbcVedAccess(
                granted
        );


        if (!granted)
        {
            latestAbcVedAnalytics =
                    null;


            clearAbcVedUi();
        }
    }


    private void showLoading()
    {
        errorText.setVisibility(
                View.GONE
        );


        progressBar.setVisibility(
                View.VISIBLE
        );
    }


    private void renderReport(
            AnalyticsReport report)
    {
        if (report == null
                ||
                !permissionGranted)
        {
            return;
        }


        AnalyticsReport.Summary summary =
                report.getSummary();


        activeMedicinesValue.setText(
                format(
                        summary.getActiveMedicineCount()
                )
        );


        usableStockValue.setText(
                format(
                        summary.getTotalUsableStock()
                )
        );


        inStockValue.setText(
                format(
                        summary.getInStockMedicineCount()
                )
        );


        lowStockValue.setText(
                format(
                        summary.getLowStockMedicineCount()
                )
        );


        outOfStockValue.setText(
                format(
                        summary.getOutOfStockMedicineCount()
                )
        );


        expiringSoonValue.setText(
                format(
                        summary.getExpiringWithin30DaysQuantity()
                )
        );


        expiredStockValue.setText(
                format(
                        summary.getExpiredStockQuantity()
                )
        );


        activeAlertsValue.setText(
                format(
                        summary.getActiveAlertCount()
                )
        );

        renderInventoryHealth(
                summary
        );


        movementPeriodDescription.setText(
                "Movement analytics: last "
                        +
                        report.getMovementWindowDays()
                        +
                        " days"
        );

        alertOverviewSummaryText.setText(
                "Open unresolved alerts: "
                        +
                        format(
                                summary.getOpenAlertCount()
                        )
                        +
                        " • Active: "
                        +
                        format(
                                summary.getActiveAlertCount()
                        )
        );

        renderCategoryChart(
                report.getCategoryDistribution()
        );


        renderStockStatusChart(
                report.getStockStatusDistribution()
        );


        renderExpiryChart(
                report.getExpiryDistribution()
        );


        renderAlertChart(
                report.getAlertDistribution()
        );


        renderMovementSummary(
                report.getMovementSummary()
        );

        renderMovementTypeChart(
                report.getMovementTypeDistribution()
        );


        renderMovementTrendChart(
                report.getMovementTrend()
        );


        renderTopIssuedChart(
                report.getTopIssuedMedicines()
        );

        if (abcVedPermissionGranted
                &&
                latestAbcVedAnalytics != null)
        {
            renderAbcVedAnalytics(
                    latestAbcVedAnalytics
            );
        }

        progressBar.setVisibility(
                View.GONE
        );


        errorText.setVisibility(
                View.GONE
        );


        content.setVisibility(
                View.VISIBLE
        );
    }


    private void renderMovementTypeChart(
            List<AnalyticsReport.DistributionItem> items)
    {
        if (!hasPositiveDistribution(
                items
        ))
        {
            showEmptyChart(
                    movementTypeChart,
                    movementTypeDistributionText,
                    "No confirmed movement activity in the selected period."
            );

            return;
        }


        movementTypeDistributionText
                .setVisibility(
                        View.GONE
                );


        movementTypeChart
                .setVisibility(
                        View.VISIBLE
                );


        movementTypeChart
                .setDistributionData(
                        items,
                        AnalyticsChartView.Mode.VERTICAL_BAR,
                        "",
                        0
                );
    }


    private void renderAbcVedAnalytics(
            AbcVedAnalytics analytics)
    {
        if (!abcVedPermissionGranted
                ||
                analytics == null)
        {
            return;
        }


        int totalActiveMedicines =
                latestReport == null
                        ? analytics
                        .getClassifiedMedicineCount()
                        : latestReport
                        .getSummary()
                        .getActiveMedicineCount();


        int classified =
                analytics
                        .getClassifiedMedicineCount();


        double coverage =
                percentage(
                        classified,
                        totalActiveMedicines
                );


        abcVedCoverageText.setText(
                "Classified active medicines: "
                        +
                        format(
                                classified
                        )
                        +
                        " of "
                        +
                        format(
                                totalActiveMedicines
                        )
                        +
                        " ("
                        +
                        formatPercentage(
                                coverage
                        )
                        +
                        ")"
        );


        renderAbcDistribution(
                analytics
                        .getAbcDistribution()
        );


        renderVedDistribution(
                analytics
                        .getVedDistribution()
        );


        setMatrixValue(
                abcVedAvText,
                "AV",
                analytics.getCombinedCount(
                        "AV"
                )
        );


        setMatrixValue(
                abcVedAeText,
                "AE",
                analytics.getCombinedCount(
                        "AE"
                )
        );


        setMatrixValue(
                abcVedAdText,
                "AD",
                analytics.getCombinedCount(
                        "AD"
                )
        );


        setMatrixValue(
                abcVedBvText,
                "BV",
                analytics.getCombinedCount(
                        "BV"
                )
        );


        setMatrixValue(
                abcVedBeText,
                "BE",
                analytics.getCombinedCount(
                        "BE"
                )
        );


        setMatrixValue(
                abcVedBdText,
                "BD",
                analytics.getCombinedCount(
                        "BD"
                )
        );


        setMatrixValue(
                abcVedCvText,
                "CV",
                analytics.getCombinedCount(
                        "CV"
                )
        );


        setMatrixValue(
                abcVedCeText,
                "CE",
                analytics.getCombinedCount(
                        "CE"
                )
        );


        setMatrixValue(
                abcVedCdText,
                "CD",
                analytics.getCombinedCount(
                        "CD"
                )
        );
    }



    private void renderAbcDistribution(
            List<AnalyticsReport.DistributionItem> items)
    {
        if (!hasPositiveDistribution(
                items
        ))
        {
            showEmptyChart(
                    abcDistributionChart,
                    abcDistributionText,
                    "No ABC classifications are currently available."
            );

            return;
        }


        abcDistributionText.setVisibility(
                View.GONE
        );


        abcDistributionChart.setVisibility(
                View.VISIBLE
        );


        abcDistributionChart
                .setDistributionData(
                        items,
                        AnalyticsChartView.Mode.DONUT,
                        "ABC",
                        0
                );
    }


    private void renderVedDistribution(
            List<AnalyticsReport.DistributionItem> items)
    {
        if (!hasPositiveDistribution(
                items
        ))
        {
            showEmptyChart(
                    vedDistributionChart,
                    vedDistributionText,
                    "No VED classifications are currently available."
            );

            return;
        }


        vedDistributionText.setVisibility(
                View.GONE
        );


        vedDistributionChart.setVisibility(
                View.VISIBLE
        );


        vedDistributionChart
                .setDistributionData(
                        items,
                        AnalyticsChartView.Mode.DONUT,
                        "VED",
                        0
                );
    }


    private void setMatrixValue(
            TextView view,
            String classification,
            long value)
    {
        view.setText(
                classification
                        +
                        "\n"
                        +
                        format(
                                value
                        )
        );
    }



    private void clearAbcVedUi()
    {

        if (abcDistributionChart != null)
        {
            abcDistributionChart
                    .setDistributionData(
                            Collections.emptyList(),
                            AnalyticsChartView.Mode.DONUT,
                            "ABC",
                            0
                    );


            abcDistributionChart.setVisibility(
                    View.GONE
            );
        }


        if (vedDistributionChart != null)
        {
            vedDistributionChart
                    .setDistributionData(
                            Collections.emptyList(),
                            AnalyticsChartView.Mode.DONUT,
                            "VED",
                            0
                    );


            vedDistributionChart.setVisibility(
                    View.GONE
            );
        }


        abcDistributionText.setText(
                "ABC classification access required."
        );


        abcDistributionText.setVisibility(
                View.VISIBLE
        );


        vedDistributionText.setText(
                "VED classification access required."
        );


        vedDistributionText.setVisibility(
                View.VISIBLE
        );


        abcVedCoverageText.setText(
                "Classified active medicines: —"
        );


        abcVedRefreshStatusText.setText(
                ""
        );


        setMatrixValue(
                abcVedAvText,
                "AV",
                0L
        );

        setMatrixValue(
                abcVedAeText,
                "AE",
                0L
        );

        setMatrixValue(
                abcVedAdText,
                "AD",
                0L
        );

        setMatrixValue(
                abcVedBvText,
                "BV",
                0L
        );

        setMatrixValue(
                abcVedBeText,
                "BE",
                0L
        );

        setMatrixValue(
                abcVedBdText,
                "BD",
                0L
        );

        setMatrixValue(
                abcVedCvText,
                "CV",
                0L
        );

        setMatrixValue(
                abcVedCeText,
                "CE",
                0L
        );

        setMatrixValue(
                abcVedCdText,
                "CD",
                0L
        );
    }


    private void renderCategoryChart(
            List<AnalyticsReport.DistributionItem> items)
    {
        if (!hasPositiveDistribution(
                items
        ))
        {
            showEmptyChart(
                    categoryChart,
                    categoryDistributionText,
                    "No active medicine category data available."
            );

            return;
        }


        categoryDistributionText.setVisibility(
                View.GONE
        );


        categoryChart.setVisibility(
                View.VISIBLE
        );


        categoryChart.setDistributionData(
                items,
                AnalyticsChartView.Mode.DONUT,
                "Medicines",
                7
        );
    }



    private void renderStockStatusChart(
            List<AnalyticsReport.DistributionItem> items)
    {
        if (!hasPositiveDistribution(
                items
        ))
        {
            showEmptyChart(
                    stockStatusChart,
                    stockStatusDistributionText,
                    "No active medicine stock data available."
            );

            return;
        }


        stockStatusDistributionText.setVisibility(
                View.GONE
        );


        stockStatusChart.setVisibility(
                View.VISIBLE
        );


        stockStatusChart.setDistributionData(
                items,
                AnalyticsChartView.Mode.DONUT,
                "Medicines",
                0
        );
    }



    private void renderExpiryChart(
            List<AnalyticsReport.DistributionItem> items)
    {
        if (!hasPositiveDistribution(
                items
        ))
        {
            showEmptyChart(
                    expiryChart,
                    expiryDistributionText,
                    "No non-expired stock is available for expiry analysis."
            );

            return;
        }


        expiryDistributionText.setVisibility(
                View.GONE
        );


        expiryChart.setVisibility(
                View.VISIBLE
        );


        expiryChart.setDistributionData(
                items,
                AnalyticsChartView.Mode.VERTICAL_BAR,
                "",
                0
        );
    }



    private void renderAlertChart(
            List<AnalyticsReport.DistributionItem> items)
    {
        if (!hasPositiveDistribution(
                items
        ))
        {
            showEmptyChart(
                    alertChart,
                    alertDistributionText,
                    "No unresolved inventory alerts."
            );

            return;
        }


        alertDistributionText.setVisibility(
                View.GONE
        );


        alertChart.setVisibility(
                View.VISIBLE
        );


        alertChart.setDistributionData(
                items,
                AnalyticsChartView.Mode.DONUT,
                "Open Alerts",
                0
        );
    }



    private void renderMovementSummary(
            AnalyticsReport.MovementSummary summary)
    {
        if (summary == null)
        {
            movementSummaryText.setText(
                    "No movement information available."
            );

            return;
        }


        String value =
                "Received: "
                        +
                        format(
                                summary.getReceivedQuantity()
                        )
                        +
                        "   •   Issued: "
                        +
                        format(
                                summary.getIssuedQuantity()
                        )
                        +
                        "\nAdjustments: "
                        +
                        format(
                                summary.getAdjustmentVolume()
                        )
                        +
                        "   •   Corrections: "
                        +
                        format(
                                summary.getCorrectionVolume()
                        );


        movementSummaryText.setText(
                value
        );
    }



    private void renderMovementTrendChart(
            List<AnalyticsReport.MovementTrendPoint> points)
    {
        if (!hasMovementActivity(
                points
        ))
        {
            showEmptyChart(
                    movementTrendChart,
                    movementTrendText,
                    "No confirmed movement activity in the selected period."
            );

            return;
        }


        movementTrendText.setVisibility(
                View.GONE
        );


        movementTrendChart.setVisibility(
                View.VISIBLE
        );


        movementTrendChart.setMovementTrendData(
                points
        );
    }



    private void renderTopIssuedChart(
            List<AnalyticsReport.TopMedicineItem> items)
    {
        if (!hasTopIssuedData(
                items
        ))
        {
            showEmptyChart(
                    topIssuedChart,
                    topIssuedText,
                    "No issued stock recorded in this period."
            );

            return;
        }


        topIssuedText.setVisibility(
                View.GONE
        );


        topIssuedChart.setVisibility(
                View.VISIBLE
        );


        topIssuedChart.setTopIssuedData(
                items
        );
    }


    private void showEmptyChart(
            AnalyticsChartView chart,
            TextView placeholder,
            String message)
    {
        chart.setVisibility(
                View.GONE
        );


        placeholder.setText(
                message
        );


        placeholder.setVisibility(
                View.VISIBLE
        );
    }

    private void renderInventoryHealth(
            AnalyticsReport.Summary summary)
    {
        int totalMedicines =
                summary.getActiveMedicineCount();


        int healthyMedicines =
                summary.getInStockMedicineCount();


        int attentionMedicines =
                summary.getLowStockMedicineCount()
                        +
                        summary.getOutOfStockMedicineCount();


        long totalUsableUnits =
                summary.getTotalUsableStock();


        long expiringUnits =
                summary.getExpiringWithin30DaysQuantity();


        double healthyRate =
                percentage(
                        healthyMedicines,
                        totalMedicines
                );


        double attentionRate =
                percentage(
                        attentionMedicines,
                        totalMedicines
                );


        double expiryExposure =
                percentage(
                        expiringUnits,
                        totalUsableUnits
                );


        healthyStockRateText.setText(
                "Healthy medicine availability: "
                        +
                        formatPercentage(
                                healthyRate
                        )
                        +
                        " ("
                        +
                        format(
                                healthyMedicines
                        )
                        +
                        " of "
                        +
                        format(
                                totalMedicines
                        )
                        +
                        ")"
        );


        reorderAttentionRateText.setText(
                "Medicines requiring stock attention: "
                        +
                        formatPercentage(
                                attentionRate
                        )
                        +
                        " ("
                        +
                        format(
                                attentionMedicines
                        )
                        +
                        " of "
                        +
                        format(
                                totalMedicines
                        )
                        +
                        ")"
        );


        expiryExposureRateText.setText(
                "Usable stock expiring within 30 days: "
                        +
                        formatPercentage(
                                expiryExposure
                        )
                        +
                        " ("
                        +
                        format(
                                expiringUnits
                        )
                        +
                        " units)"
        );
    }


    private double percentage(
            long numerator,
            long denominator)
    {
        if (denominator <= 0L)
        {
            return 0.0;
        }


        return (
                numerator
                        *
                        100.0
        )
                /
                denominator;
    }



    private String formatPercentage(
            double value)
    {
        return String.format(
                Locale.getDefault(),
                "%.1f%%",
                value
        );
    }

    private boolean hasPositiveDistribution(
            List<AnalyticsReport.DistributionItem> items)
    {
        if (items == null
                ||
                items.isEmpty())
        {
            return false;
        }


        for (AnalyticsReport.DistributionItem item :
                items)
        {
            if (item != null
                    &&
                    item.getValue() > 0L)
            {
                return true;
            }
        }


        return false;
    }


    private boolean hasMovementActivity(
            List<AnalyticsReport.MovementTrendPoint> points)
    {
        if (points == null
                ||
                points.isEmpty())
        {
            return false;
        }


        for (AnalyticsReport.MovementTrendPoint point :
                points)
        {
            if (point != null
                    &&
                    (
                            point.getReceivedQuantity() > 0L
                                    ||
                                    point.getIssuedQuantity() > 0L
                    ))
            {
                return true;
            }
        }


        return false;
    }


    private boolean hasTopIssuedData(
            List<AnalyticsReport.TopMedicineItem> items)
    {
        if (items == null
                ||
                items.isEmpty())
        {
            return false;
        }


        for (AnalyticsReport.TopMedicineItem item :
                items)
        {
            if (item != null
                    &&
                    item.getQuantityIssued() > 0L)
            {
                return true;
            }
        }


        return false;
    }


    private void renderLastSync(
            Long timestamp)
    {
        if (timestamp == null
                ||
                timestamp <= 0L)
        {
            lastSyncText.setText(
                    "Local inventory data • No successful sync recorded"
            );

            return;
        }


        CharSequence relative =
                DateUtils
                        .getRelativeTimeSpanString(
                                timestamp,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS
                        );


        lastSyncText.setText(
                "Local inventory data • Last sync "
                        +
                        relative
        );
    }


    private String format(
            long value)
    {
        return numberFormat
                .format(
                        value
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
