package com.medicationinventory.data.local.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.medicationinventory.data.local.dao.UserDao;
import com.medicationinventory.data.local.dao.MedicineDao;
import com.medicationinventory.data.local.dao.MedicineBarcodeDao;
import com.medicationinventory.data.local.dao.BatchDao;
import com.medicationinventory.data.local.dao.AuditDao;
import com.medicationinventory.data.local.dao.SyncMetadataDao;

import com.medicationinventory.data.local.entity.SyncMetadataEntity;
import com.medicationinventory.data.local.entity.UserProfileEntity;
import com.medicationinventory.data.local.entity.MedicineEntity;
import com.medicationinventory.data.local.entity.MedicineBarcodeEntity;
import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.AlertEntity;
import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.entity.OperationAllocationEntity;
import com.medicationinventory.data.local.entity.SyncQueueEntity;
import com.medicationinventory.data.local.entity.InventorySettingsEntity;
import com.medicationinventory.data.local.entity.AuditCacheEntity;
import com.medicationinventory.data.local.entity.MedicineClassificationEntity;

import com.medicationinventory.data.local.dao.AlertDao;
import com.medicationinventory.data.local.dao.AllocationDao;
import com.medicationinventory.data.local.dao.InventorySettingsDao;
import com.medicationinventory.data.local.dao.InventoryOperationDao;
import com.medicationinventory.data.local.dao.StockOperationDao;
import com.medicationinventory.data.local.dao.SyncQueueDao;
import com.medicationinventory.data.local.dao.TransactionDao;
import com.medicationinventory.data.local.dao.MedicineClassificationDao;


@Database(
        entities = {
                UserProfileEntity.class,
                MedicineEntity.class,
                BatchEntity.class,
                InventoryOperationEntity.class,
                OperationAllocationEntity.class,
                InventoryTransactionEntity.class,
                SyncQueueEntity.class,
                AlertEntity.class,
                AuditCacheEntity.class,
                SyncMetadataEntity.class,
                MedicineClassificationEntity.class,
                InventorySettingsEntity.class,
                MedicineBarcodeEntity.class
        }, version = 12, exportSchema = false
)

/**
 * Room database for local user profiles, medicine master data, batches, inventory
 * operations and their allocations, transaction history, sync work, alerts,
 * audit cache, classifications, settings, and barcode mappings. DAOs expose
 * table-specific reads and writes, while {@link StockOperationDao} groups related
 * stock changes into atomic transactions.
 *
 * Version upgrades are represented by explicit migrations and registered when
 * the singleton is created. Keeping those migrations avoids discarding offline
 * records when an installed database is opened by a newer app version.
 */
public abstract class AppDatabase extends RoomDatabase
{
    private static final String DATABASE_NAME = "medication_inventory.db";
    private static volatile AppDatabase INSTANCE;

    public abstract UserDao userDao();

    public abstract MedicineDao medicineDao();

    public abstract MedicineBarcodeDao medicineBarcodeDao();

    public abstract BatchDao batchDao();

    public abstract InventoryOperationDao inventoryOperationDao();

    public abstract AllocationDao allocationDao();

    public abstract TransactionDao transactionDao();

    public abstract SyncQueueDao syncQueueDao();

    public abstract StockOperationDao stockOperationDao();

    public abstract AlertDao alertDao();

    public abstract AuditDao auditDao();

    public abstract SyncMetadataDao syncMetadataDao();

    public abstract MedicineClassificationDao medicineClassificationDao();

    public abstract InventorySettingsDao inventorySettingsDao();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2)
    {

        @Override

        public void migrate(@NonNull SupportSQLiteDatabase database)
        {

            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medicines` (" +
                            "`medicineId` TEXT NOT NULL, " +
                            "`medicineCode` TEXT NOT NULL, " +
                            "`medicineName` TEXT NOT NULL, " +
                            "`genericName` TEXT, " +
                            "`strength` TEXT, " +
                            "`dosageForm` TEXT NOT NULL, " +
                            "`unitOfMeasure` TEXT NOT NULL, " +
                            "`reorderLevel` INTEGER NOT NULL, " +
                            "`targetStockLevel` INTEGER NOT NULL, " +
                            "`isActive` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "`syncVersion` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`medicineId`)" +
                            ")");

            database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "`index_medicines_medicineCode` " +
                            "ON `medicines` (`medicineCode`)");

            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_medicines_medicineName` " +
                            "ON `medicines` (`medicineName`)");

            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_medicines_isActive` " +
                            "ON `medicines` (`isActive`)");
        }
    };
    public static final Migration MIGRATION_2_3 = new Migration(2, 3)
    {
        @Override

        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            database.execSQL("CREATE TABLE IF NOT EXISTS `batches` (" +
                    "`batchId` TEXT NOT NULL, " +
                    "`medicineId` TEXT NOT NULL, " +
                    "`batchNumber` TEXT NOT NULL, " +
                    "`receivedDate` INTEGER NOT NULL, " +
                    "`expiryDate` INTEGER NOT NULL, " +
                    "`quantityAvailable` INTEGER NOT NULL, " +
                    "`initialQuantity` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +

                    "PRIMARY KEY(`batchId`), " +

                    "FOREIGN KEY(`medicineId`) " +
                    "REFERENCES `medicines`(`medicineId`) " +
                    "ON UPDATE NO ACTION " +
                    "ON DELETE NO ACTION" +
                    ")");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_batches_medicineId` " +
                    "ON `batches` (`medicineId`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_batches_expiryDate` " +
                    "ON `batches` (`expiryDate`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_batches_status` " +
                    "ON `batches` (`status`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_batches_medicineId_expiryDate` " +
                    "ON `batches` (`medicineId`, `expiryDate`)");

            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_batches_medicineId_batchNumber` " +
                    "ON `batches` (`medicineId`, `batchNumber`)");
        }
    };
    public static final Migration MIGRATION_3_4 = new Migration(3, 4)
    {
        @Override

        public void migrate(@NonNull SupportSQLiteDatabase database)
        {

            database.execSQL("CREATE TABLE IF NOT EXISTS `inventory_operations` (" +
                    "`operationId` TEXT NOT NULL, " +
                    "`operationType` TEXT NOT NULL, " +
                    "`medicineId` TEXT NOT NULL, " +
                    "`requestedQuantity` INTEGER NOT NULL, " +
                    "`reason` TEXT, " +
                    "`performedBy` TEXT NOT NULL, " +
                    "`clientCreatedAt` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`relatedOperationId` TEXT, " +
                    "`overrideReason` TEXT, " +
                    "`deviceId` TEXT NOT NULL, " +
                    "`resultCode` TEXT, " +
                    "`resultMessage` TEXT, " +
                    "PRIMARY KEY(`operationId`), " +
                    "FOREIGN KEY(`medicineId`) " +
                    "REFERENCES `medicines`(`medicineId`) " +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION" + ")");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_inventory_operations_medicineId` " +
                    "ON `inventory_operations` (`medicineId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_inventory_operations_status` " +
                    "ON `inventory_operations` (`status`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_inventory_operations_clientCreatedAt` " +
                    "ON `inventory_operations` (`clientCreatedAt`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_inventory_operations_performedBy` " +
                    "ON `inventory_operations` (`performedBy`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `operation_allocations` (" +
                    "`id` TEXT NOT NULL, " +
                    "`operationId` TEXT NOT NULL, " +
                    "`batchId` TEXT NOT NULL, " +
                    "`quantity` INTEGER NOT NULL, " +
                    "`allocationOrder` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`operationId`) " +
                    "REFERENCES `inventory_operations`(`operationId`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`batchId`) " +
                    "REFERENCES `batches`(`batchId`) " +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION" +
                    ")");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_operation_allocations_operationId` " +
                    "ON `operation_allocations` (`operationId`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_operation_allocations_batchId` " +
                    "ON `operation_allocations` (`batchId`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `inventory_transactions` (" +
                    "`transactionId` TEXT NOT NULL, " +
                    "`operationId` TEXT NOT NULL, " +
                    "`medicineId` TEXT NOT NULL, " +
                    "`batchId` TEXT NOT NULL, " +
                    "`transactionType` TEXT NOT NULL, " +
                    "`quantityChange` INTEGER NOT NULL, " +
                    "`reason` TEXT, " +
                    "`performedBy` TEXT NOT NULL, " +
                    "`clientCreatedAt` INTEGER NOT NULL, " +
                    "`serverCreatedAt` INTEGER NOT NULL, " +
                    "`provisional` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`transactionId`), " +
                    "FOREIGN KEY(`operationId`) " +
                    "REFERENCES `inventory_operations`(`operationId`) " +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                    "FOREIGN KEY(`medicineId`) " +
                    "REFERENCES `medicines`(`medicineId`) " +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                    "FOREIGN KEY(`batchId`) " +
                    "REFERENCES `batches`(`batchId`) " +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION" +
                    ")");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_inventory_transactions_operationId` " +
                    "ON `inventory_transactions` (`operationId`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_inventory_transactions_medicineId` " +
                    "ON `inventory_transactions` (`medicineId`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_inventory_transactions_batchId` " +
                    "ON `inventory_transactions` (`batchId`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_inventory_transactions_serverCreatedAt` " +
                    "ON `inventory_transactions` (`serverCreatedAt`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue` (" +
                    "`operationId` TEXT NOT NULL, " +
                    "`attemptCount` INTEGER NOT NULL, " +
                    "`nextAttemptAt` INTEGER NOT NULL, " +
                    "`lastAttemptAt` INTEGER NOT NULL, " +
                    "`lastErrorCode` TEXT, " +
                    "PRIMARY KEY(`operationId`), " +
                    "FOREIGN KEY(`operationId`) " +
                    "REFERENCES `inventory_operations`(`operationId`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE" + ")");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_sync_queue_nextAttemptAt` " +
                    "ON `sync_queue` (`nextAttemptAt`)");
        }
    };
    public static final Migration MIGRATION_4_5 = new Migration(4, 5)
    {
        @Override

        public void migrate(
                @NonNull SupportSQLiteDatabase database)
        {
            database.execSQL("CREATE TABLE IF NOT EXISTS `alerts` (" + "`alertId` TEXT NOT NULL, "
                    + "`alertType` TEXT NOT NULL, " + "`medicineId` TEXT NOT NULL, " +
                    "`batchId` TEXT, " + "`severity` TEXT NOT NULL, " + "`status` TEXT NOT NULL, "
                    + "`message` TEXT NOT NULL, " + "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " + "PRIMARY KEY(`alertId`), " +
                    "FOREIGN KEY(`medicineId`) " + "REFERENCES `medicines`(`medicineId`) " +
                    "ON UPDATE NO ACTION " + "ON DELETE NO ACTION, " + "FOREIGN KEY(`batchId`) " +
                    "REFERENCES `batches`(`batchId`) " + "ON UPDATE NO ACTION " +
                    "ON DELETE NO ACTION" + ")");

            database.execSQL("CREATE INDEX IF NOT EXISTS " + "`index_alerts_status` " +
                    "ON `alerts` (`status`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " + "`index_alerts_alertType` " +
                    "ON `alerts` (`alertType`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " + "`index_alerts_medicineId` " +
                    "ON `alerts` (`medicineId`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " + "`index_alerts_batchId` " +
                    "ON `alerts` (`batchId`)");
        }
    };
    public static final Migration MIGRATION_5_6 = new Migration(5, 6)
    {
        @Override

        public void migrate(
                @NonNull SupportSQLiteDatabase database)
        {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audit_cache` (" +
                            "`auditId` TEXT NOT NULL, " +
                            "`actionType` TEXT NOT NULL, " +
                            "`entityType` TEXT, " +
                            "`entityId` TEXT, " +
                            "`performedBy` TEXT NOT NULL, " +
                            "`performedAt` INTEGER NOT NULL, " +
                            "`detailsJson` TEXT NOT NULL, " +
                            "PRIMARY KEY(`auditId`)" +
                            ")"
            );


            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_audit_cache_performedAt` " +
                            "ON `audit_cache` (`performedAt`)"
            );

            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_audit_cache_actionType` " +
                            "ON `audit_cache` (`actionType`)"
            );
        }
    };
    public static final Migration MIGRATION_6_7 = new Migration(6, 7)
    {
        @Override

        public void migrate(
                @NonNull SupportSQLiteDatabase database)
        {
            database.execSQL("CREATE TABLE IF NOT EXISTS `sync_metadata` " +
                    "(" + "`key` TEXT NOT NULL, " +
                    "`lastSuccessfulSyncAt` INTEGER NOT NULL, " +
                    "`lastServerTimestamp` INTEGER NOT NULL, " +
                    "`value` TEXT, " +
                    "PRIMARY KEY(`key`)" +
                    ")"
            );
        }
    };
    public static final Migration MIGRATION_7_8 = new Migration(7, 8)
    {
        @Override

        public void migrate(
                @NonNull SupportSQLiteDatabase database)
        {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS "
                            + "`medicine_classifications` ("
                            + "`medicineId` TEXT NOT NULL, "
                            + "`abcClassification` TEXT NOT NULL, "
                            + "`vedClassification` TEXT NOT NULL, "
                            + "`updatedAt` INTEGER NOT NULL, "
                            + "PRIMARY KEY(`medicineId`), "
                            + "FOREIGN KEY(`medicineId`) "
                            + "REFERENCES `medicines`(`medicineId`) "
                            + "ON UPDATE NO ACTION "
                            + "ON DELETE NO ACTION"
                            + ")"
            );
        }
    };
    public static final Migration MIGRATION_8_9 = new Migration(8, 9)
    {
        @Override

        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + "`inventory_settings` ("
                    + "`settingId` TEXT NOT NULL, "
                    + "`nearExpiryWarningDays` INTEGER NOT NULL, "
                    + "`updatedAt` INTEGER NOT NULL, " + "`updatedBy` TEXT, "
                    + "PRIMARY KEY(`settingId`)" + ")");
        }
    };
    public static final Migration MIGRATION_9_10 = new Migration(9, 10)
    {
        @Override

        public void migrate(
                @NonNull SupportSQLiteDatabase database)
        {

            database.execSQL(
                    "ALTER TABLE medicines "
                            +
                            "ADD COLUMN category TEXT "
                            +
                            "NOT NULL DEFAULT 'Uncategorised'"
            );


            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS "
                            +
                            "`index_medicines_category` "
                            +
                            "ON `medicines` (`category`)"
            );
        }
    };

    public static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override

        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `medicine_barcodes` ("
                    + "`barcode` TEXT NOT NULL, "
                    + "`medicineId` TEXT NOT NULL, "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`barcode`), "
                    + "FOREIGN KEY(`medicineId`) REFERENCES `medicines`(`medicineId`) "
                    + "ON UPDATE NO ACTION ON DELETE NO ACTION)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_barcodes_medicineId` "
                    + "ON `medicine_barcodes` (`medicineId`)");
        }
    };


    public static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override

        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `inventory_operations` "
                    + "ADD COLUMN `receiptMode` TEXT");
            database.execSQL("ALTER TABLE `inventory_operations` "
                    + "ADD COLUMN `receiptReceivedDate` INTEGER");
        }
    };

    /**
     * Returns the process-wide Room instance, creating it once with the complete
     * migration chain when first requested. The application context prevents the
     * singleton from retaining an Activity, and migrations preserve local work
     * across schema upgrades.
     *
     * @param context context used to obtain the application-scoped database
     * @return shared database instance
     */
    public static AppDatabase getInstance(Context context)
    {
        if (INSTANCE == null)
        {
            synchronized (AppDatabase.class)
            {
                if (INSTANCE == null)
                {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                            MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                            MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12).build();
                }
            }
        }
        return INSTANCE;
    }
}
