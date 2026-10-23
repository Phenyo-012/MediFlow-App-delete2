package com.medicationinventory.model.domain;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;




/**
 * Immutable snapshot consumed by the analytics report UI. It groups summary
 * totals with category, stock, expiry, alert, and movement distributions, plus
 * movement trends and top-issued medicines for the selected report window.
 * Lists are copied on construction so later changes to a query result cannot
 * mutate a report already being observed by the UI.
 */
public final class AnalyticsReport
{

    private final int movementWindowDays;

    private final Summary summary;

    private final List<DistributionItem>
            categoryDistribution;

    private final List<DistributionItem>
            stockStatusDistribution;

    private final List<DistributionItem>
            expiryDistribution;

    private final List<DistributionItem>
            alertDistribution;

    private final List<DistributionItem>
            movementTypeDistribution;

    private final MovementSummary
            movementSummary;

    private final List<MovementTrendPoint>
            movementTrend;

    private final List<TopMedicineItem>
            topIssuedMedicines;



    public AnalyticsReport(
            int movementWindowDays,
            Summary summary,
            List<DistributionItem> categoryDistribution,
            List<DistributionItem> stockStatusDistribution,
            List<DistributionItem> expiryDistribution,
            List<DistributionItem> alertDistribution,
            List<DistributionItem> movementTypeDistribution,
            MovementSummary movementSummary,
            List<MovementTrendPoint> movementTrend,
            List<TopMedicineItem> topIssuedMedicines)
    {

        this.movementWindowDays =
                movementWindowDays;

        this.summary =
                summary;

        this.categoryDistribution =
                immutableCopy(
                        categoryDistribution
                );

        this.stockStatusDistribution =
                immutableCopy(
                        stockStatusDistribution
                );

        this.expiryDistribution =
                immutableCopy(
                        expiryDistribution
                );

        this.alertDistribution =
                immutableCopy(
                        alertDistribution
                );

        this.movementTypeDistribution =
                immutableCopy(
                        movementTypeDistribution
                );

        this.movementSummary =
                movementSummary;

        this.movementTrend =
                immutableCopy(
                        movementTrend
                );

        this.topIssuedMedicines =
                immutableCopy(
                        topIssuedMedicines
                );
    }


    private static <T> List<T> immutableCopy(
            List<T> source)
    {
        if (source == null
                ||
                source.isEmpty())
        {
            return Collections.emptyList();
        }


        return Collections.unmodifiableList(
                new ArrayList<>(
                        source
                )
        );
    }



    public int getMovementWindowDays()
    {
        return movementWindowDays;
    }


    public Summary getSummary()
    {
        return summary;
    }


    public List<DistributionItem>
    getCategoryDistribution()
    {
        return categoryDistribution;
    }


    public List<DistributionItem>
    getStockStatusDistribution()
    {
        return stockStatusDistribution;
    }


    public List<DistributionItem>
    getExpiryDistribution()
    {
        return expiryDistribution;
    }


    public List<DistributionItem>
    getAlertDistribution()
    {
        return alertDistribution;
    }


    public List<DistributionItem>
    getMovementTypeDistribution()
    {
        return movementTypeDistribution;
    }


    public MovementSummary
    getMovementSummary()
    {
        return movementSummary;
    }


    public List<MovementTrendPoint>
    getMovementTrend()
    {
        return movementTrend;
    }


    public List<TopMedicineItem>
    getTopIssuedMedicines()
    {
        return topIssuedMedicines;
    }


    public static final class Summary
    {
        private final int activeMedicineCount;

        private final long totalUsableStock;

        private final int inStockMedicineCount;

        private final int lowStockMedicineCount;

        private final int outOfStockMedicineCount;

        private final long expiringWithin30DaysQuantity;

        private final long expiredStockQuantity;

        private final int openAlertCount;

        private final int activeAlertCount;



        public Summary(
                int activeMedicineCount,
                long totalUsableStock,
                int inStockMedicineCount,
                int lowStockMedicineCount,
                int outOfStockMedicineCount,
                long expiringWithin30DaysQuantity,
                long expiredStockQuantity,
                int openAlertCount,
                int activeAlertCount)
        {
            this.activeMedicineCount =
                    activeMedicineCount;

            this.totalUsableStock =
                    totalUsableStock;

            this.inStockMedicineCount =
                    inStockMedicineCount;

            this.lowStockMedicineCount =
                    lowStockMedicineCount;

            this.outOfStockMedicineCount =
                    outOfStockMedicineCount;

            this.expiringWithin30DaysQuantity =
                    expiringWithin30DaysQuantity;

            this.expiredStockQuantity =
                    expiredStockQuantity;

            this.openAlertCount =
                    openAlertCount;

            this.activeAlertCount =
                    activeAlertCount;
        }


        public int getActiveMedicineCount()
        {
            return activeMedicineCount;
        }


        public long getTotalUsableStock()
        {
            return totalUsableStock;
        }


        public int getInStockMedicineCount()
        {
            return inStockMedicineCount;
        }


        public int getLowStockMedicineCount()
        {
            return lowStockMedicineCount;
        }


        public int getOutOfStockMedicineCount()
        {
            return outOfStockMedicineCount;
        }


        public long getExpiringWithin30DaysQuantity()
        {
            return expiringWithin30DaysQuantity;
        }


        public long getExpiredStockQuantity()
        {
            return expiredStockQuantity;
        }


        public int getOpenAlertCount()
        {
            return openAlertCount;
        }


        public int getActiveAlertCount()
        {
            return activeAlertCount;
        }
    }


    public static final class DistributionItem
    {
        private final String label;

        private final long value;



        public DistributionItem(
                String label,
                long value)
        {
            this.label =
                    label;

            this.value =
                    value;
        }


        public String getLabel()
        {
            return label;
        }


        public long getValue()
        {
            return value;
        }
    }


    public static final class MovementSummary
    {
        private final long receivedQuantity;

        private final long issuedQuantity;

        private final long adjustmentInQuantity;

        private final long adjustmentOutQuantity;

        private final long correctionInQuantity;

        private final long correctionOutQuantity;



        public MovementSummary(
                long receivedQuantity,
                long issuedQuantity,
                long adjustmentInQuantity,
                long adjustmentOutQuantity,
                long correctionInQuantity,
                long correctionOutQuantity)
        {
            this.receivedQuantity =
                    receivedQuantity;

            this.issuedQuantity =
                    issuedQuantity;

            this.adjustmentInQuantity =
                    adjustmentInQuantity;

            this.adjustmentOutQuantity =
                    adjustmentOutQuantity;

            this.correctionInQuantity =
                    correctionInQuantity;

            this.correctionOutQuantity =
                    correctionOutQuantity;
        }


        public long getReceivedQuantity()
        {
            return receivedQuantity;
        }


        public long getIssuedQuantity()
        {
            return issuedQuantity;
        }


        public long getAdjustmentInQuantity()
        {
            return adjustmentInQuantity;
        }


        public long getAdjustmentOutQuantity()
        {
            return adjustmentOutQuantity;
        }


        public long getCorrectionOutQuantity()
        {
            return correctionOutQuantity;
        }


        public long getAdjustmentVolume()
        {
            return adjustmentInQuantity
                    +
                    adjustmentOutQuantity;
        }


        public long getCorrectionVolume()
        {
            return correctionInQuantity
                    +
                    correctionOutQuantity;
        }

    }


    public static final class MovementTrendPoint
    {
        private final long dayStartMillis;

        private final long receivedQuantity;

        private final long issuedQuantity;



        public MovementTrendPoint(
                long dayStartMillis,
                long receivedQuantity,
                long issuedQuantity)
        {
            this.dayStartMillis =
                    dayStartMillis;

            this.receivedQuantity =
                    receivedQuantity;

            this.issuedQuantity =
                    issuedQuantity;
        }


        public long getDayStartMillis()
        {
            return dayStartMillis;
        }


        public long getReceivedQuantity()
        {
            return receivedQuantity;
        }


        public long getIssuedQuantity()
        {
            return issuedQuantity;
        }
    }


    public static final class TopMedicineItem
    {
        private final String medicineId;

        private final String medicineName;

        private final String medicineCode;

        private final long quantityIssued;



        public TopMedicineItem(
                String medicineId,
                String medicineName,
                String medicineCode,
                long quantityIssued)
        {
            this.medicineId =
                    medicineId;

            this.medicineName =
                    medicineName;

            this.medicineCode =
                    medicineCode;

            this.quantityIssued =
                    quantityIssued;
        }


        public String getMedicineId()
        {
            return medicineId;
        }


        public String getMedicineName()
        {
            return medicineName;
        }


        public String getMedicineCode()
        {
            return medicineCode;
        }


        public long getQuantityIssued()
        {
            return quantityIssued;
        }
    }
}