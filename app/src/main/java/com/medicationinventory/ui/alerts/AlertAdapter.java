package com.medicationinventory.ui.alerts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;

import androidx.annotation.NonNull;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import com.medicationinventory.R;

import com.medicationinventory.data.local.model.AlertListRecord;

import com.medicationinventory.model.enums.AlertSeverity;
import com.medicationinventory.model.enums.AlertType;
import com.medicationinventory.model.enums.AlertStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Binds Alert records to their reusable list rows.
 */
public class AlertAdapter
        extends RecyclerView.Adapter<AlertAdapter.AlertViewHolder>
{
    public interface OnAlertClickListener
    {
        void onAlertClicked(
                AlertListRecord alert
        );
    }

    public interface OnAcknowledgeClickListener
    {
        void onAcknowledgeClicked(
                AlertListRecord alert
        );
    }


    private final List<AlertListRecord> alerts =
            new ArrayList<>();

    private final OnAlertClickListener alertClickListener;

    private final OnAcknowledgeClickListener acknowledgeClickListener;

    private boolean canAcknowledge = false;

    private boolean acknowledgementInProgress = false;



    public AlertAdapter(
            OnAlertClickListener alertClickListener,
            OnAcknowledgeClickListener acknowledgeClickListener)
    {
        this.alertClickListener =
                alertClickListener;

        this.acknowledgeClickListener =
                acknowledgeClickListener;
    }

    public void setCanAcknowledge(
            boolean canAcknowledge)
    {
        this.canAcknowledge =
                canAcknowledge;

        notifyDataSetChanged();
    }


    public void setAcknowledgementInProgress(
            boolean acknowledgementInProgress)
    {
        this.acknowledgementInProgress =
                acknowledgementInProgress;

        notifyDataSetChanged();
    }


    public void submitList(
            List<AlertListRecord> items)
    {
        alerts.clear();


        if (items != null)
        {
            alerts.addAll(
                    items
            );
        }


        notifyDataSetChanged();
    }


    @NonNull
    @Override

    public AlertViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType)
    {
        View view =
                LayoutInflater
                        .from(
                                parent.getContext()
                        )
                        .inflate(
                                R.layout.item_alert,
                                parent,
                                false
                        );


        return new AlertViewHolder(
                view
        );
    }


    @Override

    public void onBindViewHolder(
            @NonNull AlertViewHolder holder,
            int position)
    {
        AlertListRecord alert =
                alerts.get(
                        position
                );


        holder.typeText.setText(
                formatType(
                        alert.alertType
                )
        );


        holder.severityText.setText(
                alert.severity == null
                        ? "UNKNOWN"
                        : alert.severity.name()
        );


        String medicineLabel =
                safe(
                        alert.medicineName
                );


        if (alert.medicineCode != null
                &&
                !alert.medicineCode.trim().isEmpty())
        {
            medicineLabel +=
                    " · "
                            + alert.medicineCode;
        }


        holder.medicineText.setText(
                medicineLabel
        );


        if (alert.batchId != null)
        {
            String batchLabel =
                    alert.batchNumber == null
                            ||
                            alert.batchNumber.trim().isEmpty()
                            ? alert.batchId
                            : alert.batchNumber;


            holder.batchText.setText(
                    "Batch: "
                            + batchLabel
            );


            holder.batchText.setVisibility(
                    View.VISIBLE
            );
        }
        else
        {
            holder.batchText.setVisibility(
                    View.GONE
            );
        }


        holder.messageText.setText(
                safe(
                        alert.message
                )
        );


        holder.statusText.setText(
                alert.status == null
                        ? "UNKNOWN"
                        : alert.status.name()
        );


        holder.itemView.setOnClickListener(
                view ->
                {
                    if (alertClickListener != null)
                    {
                        alertClickListener
                                .onAlertClicked(
                                        alert
                                );
                    }
                }
        );


        boolean showAcknowledge =
                canAcknowledge
                        &&
                        alert.status
                                == AlertStatus.ACTIVE;


        holder.acknowledgeButton
                .setVisibility(
                        showAcknowledge
                                ? View.VISIBLE
                                : View.GONE
                );


        holder.acknowledgeButton
                .setEnabled(
                        showAcknowledge
                                &&
                                !acknowledgementInProgress
                );


        holder.acknowledgeButton
                .setOnClickListener(
                        view ->
                        {
                            if (acknowledgeClickListener
                                    != null)
                            {
                                acknowledgeClickListener
                                        .onAcknowledgeClicked(
                                                alert
                                        );
                            }
                        }
                );
    }


    @Override
    public int getItemCount()
    {
        return alerts.size();
    }


    private String formatType(
            AlertType type)
    {
        if (type == null)
        {
            return "ALERT";
        }


        switch (type)
        {
            case LOW_STOCK:
                return "LOW STOCK";

            case NEAR_EXPIRY:
                return "NEAR EXPIRY";

            case EXPIRED:
                return "EXPIRED";

            default:
                return type.name();
        }
    }


    private String safe(
            String value)
    {
        return value == null
                ? ""
                : value;
    }


    static class AlertViewHolder
            extends RecyclerView.ViewHolder
    {
        final TextView typeText;

        final TextView severityText;

        final TextView medicineText;

        final TextView batchText;

        final TextView messageText;

        final TextView statusText;

        final MaterialButton acknowledgeButton;


        AlertViewHolder(
                @NonNull View itemView)
        {
            super(
                    itemView
            );


            typeText =
                    itemView.findViewById(
                            R.id.alertTypeText
                    );

            severityText =
                    itemView.findViewById(
                            R.id.alertSeverityText
                    );

            medicineText =
                    itemView.findViewById(
                            R.id.alertMedicineText
                    );

            batchText =
                    itemView.findViewById(
                            R.id.alertBatchText
                    );

            messageText =
                    itemView.findViewById(
                            R.id.alertMessageText
                    );

            statusText =
                    itemView.findViewById(
                            R.id.alertStatusText
                    );

            acknowledgeButton =
                    itemView.findViewById(
                            R.id.alertAcknowledgeButton
                    );
        }
    }
}