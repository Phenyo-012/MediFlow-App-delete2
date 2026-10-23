package com.medicationinventory.ui.inventory;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.medicationinventory.R;
import com.medicationinventory.model.domain.InventoryFilterState;
import com.medicationinventory.model.domain.InventoryMedicineItem;
import com.medicationinventory.model.domain.Medicine;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;




/**
 * Binds Medicine records to their reusable list rows.
 */
public class MedicineAdapter extends RecyclerView.Adapter<MedicineAdapter.MedicineViewHolder> {
    private final List<InventoryMedicineItem> medicines = new ArrayList<>();
    private final OnMedicineClickListener clickListener;
    private final NumberFormat quantity = NumberFormat.getIntegerInstance(Locale.getDefault());
    private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);


    public MedicineAdapter(OnMedicineClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setMedicines(List<InventoryMedicineItem> updated) {
        medicines.clear();
        if (updated != null) medicines.addAll(updated);
        notifyDataSetChanged();
    }

    @NonNull
    @Override

    public MedicineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_medicine, parent, false);
        return new MedicineViewHolder(view);
    }

    @Override

    public void onBindViewHolder(@NonNull MedicineViewHolder holder, int position) {
        holder.bind(medicines.get(position));
    }

    @Override
    public int getItemCount() { return medicines.size(); }

    class MedicineViewHolder extends RecyclerView.ViewHolder {
        private final TextView medicineNameText;
        private final TextView medicineGenericText;
        private final TextView medicineDetailsText;
        private final TextView medicineStockText;

        MedicineViewHolder(@NonNull View itemView) {
            super(itemView);
            medicineNameText = itemView.findViewById(R.id.medicineNameText);
            medicineGenericText = itemView.findViewById(R.id.medicineGenericText);
            medicineDetailsText = itemView.findViewById(R.id.medicineDetailsText);
            medicineStockText = itemView.findViewById(R.id.medicineStockText);
        }

        void bind(InventoryMedicineItem item) {
            Medicine medicine = item.getMedicine();
            medicineNameText.setText(medicine.getMedicineName());
            String generic = medicine.getGenericName();
            medicineGenericText.setText(generic == null || generic.trim().isEmpty()
                    ? medicine.getMedicineCode() : generic);

            StringBuilder details = new StringBuilder(medicine.getMedicineCode());
            if (medicine.getStrength() != null && !medicine.getStrength().trim().isEmpty())
                details.append(" • ").append(medicine.getStrength());
            details.append(" • ").append(medicine.getDosageForm());
            details.append(" • ").append(medicine.getCategory());
            medicineDetailsText.setText(details.toString());

            String statusText;
            int statusColor;
            InventoryFilterState.StockStatus status = item.getStockStatus();
            if (status == InventoryFilterState.StockStatus.OUT_OF_STOCK) {
                statusText = "Out of stock";
                statusColor = Color.rgb(185, 28, 28);
            } else if (status == InventoryFilterState.StockStatus.LOW_STOCK) {
                statusText = "Low stock";
                statusColor = Color.rgb(161, 98, 7);
            } else {
                statusText = "In stock";
                statusColor = Color.rgb(21, 128, 61);
            }
            StringBuilder stock = new StringBuilder(statusText)
                    .append(" • ").append(quantity.format(item.getUsableUnits()))
                    .append(" usable units");
            if (item.getNearestUsableExpiry() != null)
                stock.append("\nNearest expiry: ")
                        .append(dateFormat.format(new Date(item.getNearestUsableExpiry())));
            if (item.getExpiredUnits() > 0L)
                stock.append("\nExpired: ").append(quantity.format(item.getExpiredUnits()))
                        .append(" units");
            medicineStockText.setText(stock.toString());
            medicineStockText.setTextColor(statusColor);
            itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onMedicineClick(medicine);
            });
        }
    }

    public interface OnMedicineClickListener {
        void onMedicineClick(Medicine medicine);
    }
}
