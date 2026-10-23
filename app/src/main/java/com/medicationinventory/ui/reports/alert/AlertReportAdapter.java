package com.medicationinventory.ui.reports.alert;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.medicationinventory.R;

import com.medicationinventory.data.local.model.AlertReportRecord;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;



/**
 * Binds Alert Report records to their reusable list rows.
 */
public class AlertReportAdapter
        extends RecyclerView.Adapter<
        AlertReportAdapter.AlertReportViewHolder>
{
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy HH:mm"
            );


    private final List<AlertReportRecord>
            items =
            new ArrayList<>();


    public void submitList(
            List<AlertReportRecord> result)
    {
        items.clear();


        if (result != null)
        {
            items.addAll(
                    result
            );
        }


        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public AlertReportViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType)
    {
        View view =
                LayoutInflater
                        .from(
                                parent.getContext()
                        )
                        .inflate(
                                R.layout.item_alert_report,
                                parent,
                                false
                        );


        return new AlertReportViewHolder(
                view
        );
    }


    @Override
    public void onBindViewHolder(
            @NonNull AlertReportViewHolder holder,
            int position)
    {
        AlertReportRecord item =
                items.get(
                        position
                );


        holder.typeText.setText(
                formatEnum(
                        item.alertType == null
                                ? "ALERT"
                                : item.alertType.name()
                )
        );


        holder.statusText.setText(
                item.status == null
                        ? "UNKNOWN"
                        : item.status.name()
        );


        String medicine =
                safe(
                        item.medicineName
                );


        if (item.medicineCode != null
                &&
                !item.medicineCode.trim().isEmpty())
        {
            medicine +=
                    " · "
                            + item.medicineCode;
        }


        holder.medicineText.setText(
                medicine
        );


        if (item.batchId != null)
        {
            String batch =
                    item.batchNumber == null
                            ||
                            item.batchNumber.trim().isEmpty()
                            ? item.batchId
                            : item.batchNumber;


            holder.batchText.setText(
                    "Batch: "
                            + batch
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


        holder.severityText.setText(
                "Severity: "
                        + (
                        item.severity == null
                                ? "UNKNOWN"
                                : item.severity.name()
                )
        );


        holder.messageText.setText(
                safe(
                        item.message
                )
        );


        holder.createdText.setText(
                "Created: "
                        + formatDate(
                        item.createdAt
                )
        );
    }


    @Override
    public int getItemCount()
    {
        return items.size();
    }


    private String formatDate(
            long epochMillis)
    {
        if (epochMillis <= 0)
        {
            return "Unknown";
        }


        return Instant
                .ofEpochMilli(
                        epochMillis
                )
                .atZone(
                        BUSINESS_ZONE
                )
                .format(
                        DATE_FORMATTER
                );
    }


    private String formatEnum(
            String value)
    {
        return value
                .replace(
                        '_',
                        ' '
                );
    }


    private String safe(
            String value)
    {
        return value == null
                ? ""
                : value;
    }


    static class AlertReportViewHolder
            extends RecyclerView.ViewHolder
    {
        final TextView typeText;
        final TextView statusText;
        final TextView medicineText;
        final TextView batchText;
        final TextView severityText;
        final TextView messageText;
        final TextView createdText;


        AlertReportViewHolder(
                @NonNull View itemView)
        {
            super(
                    itemView
            );


            typeText =
                    itemView.findViewById(
                            R.id.alertReportTypeText
                    );

            statusText =
                    itemView.findViewById(
                            R.id.alertReportStatusText
                    );

            medicineText =
                    itemView.findViewById(
                            R.id.alertReportMedicineText
                    );

            batchText =
                    itemView.findViewById(
                            R.id.alertReportBatchText
                    );

            severityText =
                    itemView.findViewById(
                            R.id.alertReportSeverityText
                    );

            messageText =
                    itemView.findViewById(
                            R.id.alertReportMessageText
                    );

            createdText =
                    itemView.findViewById(
                            R.id.alertReportCreatedText
                    );
        }
    }
}