package com.medicationinventory.ui.batch;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.medicationinventory.R;
import com.medicationinventory.model.domain.Batch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;



/**
 * Binds Batch records to their reusable list rows.
 */
public class BatchAdapter
        extends RecyclerView.Adapter<BatchAdapter.BatchViewHolder>
{
    private final List<Batch> batches = new ArrayList<>();
    private final OnBatchClickListener clickListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy",
            Locale.getDefault());

    public BatchAdapter(OnBatchClickListener clickListener)
    {
        this.clickListener =
                clickListener;
    }
    public void setBatches(List<Batch> newBatches)
    {
        batches.clear();

        if (newBatches != null)
        {
            batches.addAll(newBatches);
        }

        notifyDataSetChanged();
    }
    @NonNull
    @Override

    public BatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_batch,
                parent, false);

        return new BatchViewHolder(view);
    }
    @Override

    public void onBindViewHolder(@NonNull BatchViewHolder holder, int position)
    {
        holder.bind(batches.get(position));
    }
    @Override
    public int getItemCount()
    {
        return batches.size();
    }
    class BatchViewHolder
            extends RecyclerView.ViewHolder
    {
        private final TextView batchNumberText;
        private final TextView batchStatusText;
        private final TextView batchQuantityText;
        private final TextView batchReceivedDateText;
        private final TextView batchExpiryDateText;
        BatchViewHolder(@NonNull View itemView)
        {
            super(itemView);

            batchNumberText = itemView.findViewById(R.id.batchNumberText);

            batchStatusText = itemView.findViewById(R.id.batchStatusText);

            batchQuantityText = itemView.findViewById(R.id.batchQuantityText);

            batchReceivedDateText = itemView.findViewById(R.id.batchReceivedDateText);

            batchExpiryDateText = itemView.findViewById(R.id.batchExpiryDateText);
        }
        void bind(Batch batch)
        {
            batchNumberText.setText("Batch " + batch.getBatchNumber());

            batchStatusText.setText(batch.getStatus().name());

            batchQuantityText.setText("Available: " + batch.getQuantityAvailable());

            batchReceivedDateText.setText("Received: " + formatDate(batch.getReceivedDate()));

            batchExpiryDateText.setText("Expires: " + formatDate(batch.getExpiryDate()));

            itemView.setOnClickListener(view ->
                    {
                        if (clickListener != null)
                        {
                            clickListener.onBatchClick(batch);
                        }
                    }
            );
        }
    }

    private String formatDate(long timestamp)
    {
        if (timestamp <= 0L)
        {
            return "Not available";
        }

        return dateFormat.format(new Date(timestamp));
    }
    public interface OnBatchClickListener
    {
        void onBatchClick(Batch batch);
    }
}