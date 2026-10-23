package com.medicationinventory.ui.sync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.local.model.SyncStatusRecord;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.InventorySyncRepository;

import com.medicationinventory.ui.auth.LoginActivity;
import com.medicationinventory.ui.medicine.MedicineDetailsActivity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Displays one inventory operation synchronization status and allows its owner to request an eligible retry.
 */
public class SyncDetailActivity
        extends AppCompatActivity
{
    public static final String EXTRA_OPERATION_ID =
            "operation_id";


    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy, HH:mm"
            );


    private SyncDetailViewModel
            viewModel;


    private TextView statusText;
    private TextView operationText;
    private TextView medicineText;
    private TextView quantityText;
    private TextView dateText;
    private TextView explanationText;
    private TextView retryText;

    private MaterialButton
            primaryButton;


    private SyncStatusRecord
            currentRecord;

    private boolean manualRetryInProgress;


    @Override
    protected void onCreate(
            Bundle savedInstanceState)
    {
        super.onCreate(
                savedInstanceState
        );


        String operationId =
                getIntent()
                        .getStringExtra(
                                EXTRA_OPERATION_ID
                        );


        if (operationId == null
                ||
                operationId.trim().isEmpty())
        {
            finish();

            return;
        }


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


        String userId =
                authenticationRepository
                        .getCurrentUserId();


        if (userId == null
                ||
                userId.trim().isEmpty())
        {
            goToLogin();

            return;
        }


        setContentView(
                R.layout.activity_sync_detail
        );


        bindViews();


        SyncDetailViewModelFactory factory =
                new SyncDetailViewModelFactory(
                        userId,
                        operationId,
                        application
                                .getAppContainer()
                                .getSyncStatusRepository(),
                        application
                                .getAppContainer()
                                .getInventorySyncRepository(),
                        application
                                .getAppContainer()
                                .getInventorySyncScheduler()
                );


        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                SyncDetailViewModel.class
                        );


        viewModel
                .getOperation()
                .observe(
                        this,
                        this::renderOperation
                );


        setupActions();
    }


    private void bindViews()
    {
        statusText =
                findViewById(
                        R.id.syncDetailStatusText
                );

        operationText =
                findViewById(
                        R.id.syncDetailOperationText
                );

        medicineText =
                findViewById(
                        R.id.syncDetailMedicineText
                );

        quantityText =
                findViewById(
                        R.id.syncDetailQuantityText
                );

        dateText =
                findViewById(
                        R.id.syncDetailDateText
                );

        explanationText =
                findViewById(
                        R.id.syncDetailExplanationText
                );

        retryText =
                findViewById(
                        R.id.syncDetailRetryText
                );

        primaryButton =
                findViewById(
                        R.id.syncDetailPrimaryButton
                );
    }


    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.syncDetailBackButton
                );


        backButton.setOnClickListener(
                view ->
                        finish()
        );


        primaryButton.setOnClickListener(
                view ->
                        handlePrimaryAction()
        );
    }


    private void renderOperation(
            SyncStatusRecord record)
    {
        if (record == null)
        {
            Toast.makeText(
                    this,
                    "This synchronisation record is no longer available.",
                    Toast.LENGTH_SHORT
            ).show();


            finish();

            return;
        }


        currentRecord =
                record;


        statusText.setText(
                displayStatus(
                        record
                )
        );


        operationText.setText(
                "Operation: "
                        + friendlyType(
                        record.operationType
                )
        );


        String medicineName =
                hasText(
                        record.medicineName
                )
                        ? record.medicineName.trim()
                        : "Unknown medicine";


        String medicineCode =
                hasText(
                        record.medicineCode
                )
                        ? " · "
                          + record.medicineCode.trim()
                        : "";


        medicineText.setText(
                "Medicine: "
                        + medicineName
                        + medicineCode
        );


        quantityText.setText(
                "Quantity: "
                        + record.requestedQuantity
        );


        dateText.setText(
                "Recorded: "
                        + formatTime(
                        record.clientCreatedAt
                )
        );


        explanationText.setText(
                buildExplanation(
                        record
                )
        );


        renderRetryInformation(
                record
        );


        configurePrimaryAction(
                record
        );
    }



    private void configurePrimaryAction(
            SyncStatusRecord record)
    {
        if (isHeldPermissionDeniedUpload(record))
        {
            primaryButton.setText("Retry Failed Upload");
            primaryButton.setVisibility(View.VISIBLE);
            primaryButton.setEnabled(!manualRetryInProgress);
            return;
        }

        primaryButton.setEnabled(true);
        if ("LOCAL_PENDING".equals(
                record.status
        )
                ||
                "SUBMITTED".equals(
                        record.status
                ))
        {
            primaryButton.setText(
                    "Request Sync"
            );

            primaryButton.setVisibility(
                    View.VISIBLE
            );

            return;
        }


        if ("CONFLICT".equals(
                record.status
        )
                ||
                "REJECTED".equals(
                        record.status
                )
                ||
                "CONFIRMED".equals(
                        record.status
                ))
        {
            primaryButton.setText(
                    "Review Medicine"
            );

            primaryButton.setVisibility(
                    View.VISIBLE
            );

            return;
        }


        primaryButton.setVisibility(
                View.GONE
        );
    }


    private void handlePrimaryAction()
    {
        if (currentRecord == null || manualRetryInProgress)
        {
            return;
        }

        if (isHeldPermissionDeniedUpload(currentRecord))
        {
            new AlertDialog.Builder(this)
                    .setTitle("Retry failed upload?")
                    .setMessage("Firestore previously denied this operation. "
                            + "Retry only after the account permissions or "
                            + "Firestore rules have been corrected. "
                            + "The existing operation will be reused; "
                            + "no additional stock is created by this action.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Retry", (dialog, which) ->
                            retryHeldOperation())
                    .show();
            return;
        }

        if ("LOCAL_PENDING".equals(
                currentRecord.status
        )
                ||
                "SUBMITTED".equals(
                        currentRecord.status
                ))
        {
            viewModel.requestSync();


            Toast.makeText(
                    this,
                    "Synchronisation requested.",
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


        if (!hasText(
                currentRecord.medicineId
        ))
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
                currentRecord.medicineId
        );


        startActivity(
                intent
        );
    }



    private void retryHeldOperation()
    {
        manualRetryInProgress = true;
        primaryButton.setEnabled(false);
        viewModel.retryHeldPermissionDeniedUpload(
                new InventorySyncRepository.ManualRetryCallback()
                {
                    @Override

                    public void onReady()
                    {
                        manualRetryInProgress = false;
                        if (isFinishing() || isDestroyed()) return;
                        if (currentRecord != null) configurePrimaryAction(currentRecord);
                        Toast.makeText(SyncDetailActivity.this,
                                "Retry enabled. Synchronisation requested.",
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String message)
                    {
                        manualRetryInProgress = false;
                        if (isFinishing() || isDestroyed()) return;
                        if (currentRecord != null) configurePrimaryAction(currentRecord);
                        Toast.makeText(SyncDetailActivity.this,
                                message, Toast.LENGTH_LONG).show();
                    }
                });
    }


    private boolean isHeldPermissionDeniedUpload(SyncStatusRecord record)
    {
        return record != null
                && "LOCAL_PENDING".equals(record.status)
                && record.nextAttemptAt == Long.MAX_VALUE
                && "FIRESTORE_PERMISSION_DENIED".equals(record.lastErrorCode);
    }


    private String displayStatus(
            SyncStatusRecord record)
    {
        if (isHeldPermissionDeniedUpload(record))
        {
            return "Needs attention";
        }

        if ("LOCAL_PENDING".equals(
                record.status
        ))
        {
            return record.attemptCount > 0
                    ? "Retrying"
                    : "Pending";
        }


        if ("SUBMITTED".equals(
                record.status
        ))
        {
            return "Processing";
        }


        if ("CONFIRMED".equals(
                record.status
        ))
        {
            return "Synced";
        }


        if ("CONFLICT".equals(
                record.status
        )
                ||
                "REJECTED".equals(
                        record.status
                ))
        {
            return "Needs attention";
        }


        return "Unknown";
    }



    private String buildExplanation(
            SyncStatusRecord record)
    {
        if (isHeldPermissionDeniedUpload(record))
        {
            return "Firestore denied this upload. The operation is held "
                    + "until its access or validation issue is fixed. "
                    + "After the correction, choose Retry Failed Upload "
                    + "to submit this same operation again.";
        }

        if ("LOCAL_PENDING".equals(
                record.status
        ))
        {
            return "This operation is stored safely on this device "
                    + "and is waiting to synchronise with shared inventory.";
        }


        if ("SUBMITTED".equals(
                record.status
        ))
        {
            return "This operation has been submitted and is waiting "
                    + "for authoritative processing or reconciliation.";
        }


        if ("CONFIRMED".equals(
                record.status
        ))
        {
            return "This operation was accepted and applied to the "
                    + "authoritative shared inventory.";
        }


        if ("CONFLICT".equals(
                record.status
        ))
        {
            if (hasText(
                    record.resultMessage
            ))
            {
                return record.resultMessage.trim()
                        + "\n\nReview the current inventory before "
                        + "recording a new action.";
            }


            return "The shared inventory changed before this operation "
                    + "could be accepted. Local stock has been reconciled "
                    + "to the authoritative result. Review the medicine "
                    + "before recording a new action.";
        }


        if ("REJECTED".equals(
                record.status
        ))
        {
            if (hasText(
                    record.resultMessage
            ))
            {
                return record.resultMessage.trim()
                        + "\n\nThe attempted operation remains preserved. "
                        + "Review the medicine before recording another action.";
            }


            return "The operation was not accepted by authoritative "
                    + "validation. The attempted operation remains preserved "
                    + "and should not be rewritten or retried as a new copy.";
        }


        return "Synchronisation information is unavailable.";
    }



    private void renderRetryInformation(
            SyncStatusRecord record)
    {
        if (record.attemptCount <= 0
                ||
                (
                        !"LOCAL_PENDING".equals(
                                record.status
                        )
                                &&
                                !"SUBMITTED".equals(
                                        record.status
                                )
                ))
        {
            retryText.setVisibility(
                    View.GONE
            );

            return;
        }


        String text =
                "Attempts: "
                        + record.attemptCount;


        if (record.lastAttemptAt > 0)
        {
            text +=
                    "\nLast attempt: "
                            + formatTime(
                            record.lastAttemptAt
                    );
        }


        if (record.nextAttemptAt == Long.MAX_VALUE)
        {
            text += "\nAutomatic retries paused. Manual review required.";
        }
        else if (record.nextAttemptAt > 0)
        {
            text +=
                    "\nNext retry: "
                            + formatTime(
                            record.nextAttemptAt
                    );
        }


        retryText.setText(
                text
        );


        retryText.setVisibility(
                View.VISIBLE
        );
    }



    private String friendlyType(
            String value)
    {
        if (!hasText(
                value
        ))
        {
            return "Inventory operation";
        }


        String normalized =
                value.trim()
                        .replace(
                                '_',
                                ' '
                        );


        return normalized.substring(
                0,
                1
        ).toUpperCase(Locale.ROOT)
                +
                normalized.substring(
                        1
                ).toLowerCase(Locale.ROOT);
    }



    private String formatTime(
            long millis)
    {
        if (millis <= 0)
        {
            return "Unavailable";
        }


        return Instant
                .ofEpochMilli(
                        millis
                )
                .atZone(
                        BUSINESS_ZONE
                )
                .format(
                        DATE_FORMAT
                );
    }



    private boolean hasText(
            String value)
    {
        return value != null
                &&
                !value.trim().isEmpty();
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