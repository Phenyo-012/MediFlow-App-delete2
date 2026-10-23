package com.medicationinventory.util;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;

import com.medicationinventory.BuildConfig;

import com.medicationinventory.model.domain.InventorySettings;

import com.medicationinventory.data.local.dao.UserDao;
import com.medicationinventory.data.local.dao.AuditDao;
import com.medicationinventory.data.local.dao.BatchDao;
import com.medicationinventory.data.local.dao.MedicineDao;
import com.medicationinventory.data.local.dao.MedicineBarcodeDao;
import com.medicationinventory.data.local.dao.SyncQueueDao;
import com.medicationinventory.data.local.dao.AllocationDao;
import com.medicationinventory.data.local.dao.TransactionDao;
import com.medicationinventory.data.local.dao.SyncMetadataDao;
import com.medicationinventory.data.local.database.AppDatabase;
import com.medicationinventory.data.local.dao.StockOperationDao;
import com.medicationinventory.data.local.dao.InventorySettingsDao;
import com.medicationinventory.data.local.dao.InventoryOperationDao;
import com.medicationinventory.data.local.dao.MedicineClassificationDao;

import com.medicationinventory.service.IssueService;
import com.medicationinventory.service.ReceiptService;
import com.medicationinventory.service.AdjustmentService;
import com.medicationinventory.service.CorrectionService;
import com.medicationinventory.service.AlertRefreshService;
import com.medicationinventory.service.FefoAllocationService;
import com.medicationinventory.service.AlertEvaluationService;
import com.medicationinventory.service.AlertInventoryObserver;
import com.medicationinventory.service.ReportDateRangeService;
import com.medicationinventory.service.AnalyticsReportService;
import com.medicationinventory.service.StockLevelReportService;
import com.medicationinventory.service.LocalStockOperationService;
import com.medicationinventory.service.ReorderRecommendationService;
import com.medicationinventory.service.DailyInventoryMaintenanceService;

import com.medicationinventory.util.DeviceIdProvider;
import com.medicationinventory.security.OfflineSessionController;
import com.medicationinventory.security.OfflineSessionStore;

import com.medicationinventory.data.remote.auth.FirebaseAuthDataSource;
import com.medicationinventory.data.remote.firestore.FirestoreAuditDataSource;
import com.medicationinventory.data.remote.firestore.FirestoreUserProfileDataSource;
import com.medicationinventory.data.remote.firestore.FirestoreInventoryOperationDataSource;
import com.medicationinventory.data.remote.firestore.FirestoreMedicineClassificationDataSource;
import com.medicationinventory.data.remote.firestore.FirestoreInventorySettingsDataSource;
import com.medicationinventory.data.remote.backend.TrustedInventoryProcessorDataSource;
import com.medicationinventory.data.remote.backend.TrustedInventoryReconciliationDataSource;
import com.medicationinventory.data.remote.backend.TrustedUserAdminDataSource;
import com.medicationinventory.data.remote.backend.TrustedTotpRecoveryDataSource;
import com.medicationinventory.data.remote.backend.TrustedMedicineClassificationDataSource;
import com.medicationinventory.data.remote.backend.TrustedInventorySettingsDataSource;
import com.medicationinventory.data.remote.backend.TrustedMedicineMasterDataSource;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.UserRepository;
import com.medicationinventory.data.repository.AlertRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.remote.backend.TrustedMedicineBarcodeDataSource;
import com.medicationinventory.sync.MedicineBarcodeCloudSyncCoordinator;
import com.medicationinventory.data.repository.BatchRepository;
import com.medicationinventory.data.repository.AuditRepository;
import com.medicationinventory.data.repository.InventorySyncRepository;
import com.medicationinventory.data.repository.InventoryReconciliationRepository;
import com.medicationinventory.data.repository.IssueStockRepository;
import com.medicationinventory.data.repository.MovementHistoryRepository;
import com.medicationinventory.data.repository.ReorderRecommendationsRepository;
import com.medicationinventory.data.repository.StockLevelReportRepository;
import com.medicationinventory.data.repository.AlertReportRepository;
import com.medicationinventory.data.repository.MovementReportRepository;
import com.medicationinventory.data.repository.SyncStatusRepository;
import com.medicationinventory.data.repository.SyncMetadataRepository;
import com.medicationinventory.data.repository.AdminUserRepository;
import com.medicationinventory.data.repository.InventorySettingsRepository;
import com.medicationinventory.data.repository.MedicineClassificationRepository;
import com.medicationinventory.data.repository.AnalyticsReportRepository;

import com.medicationinventory.sync.InventorySyncScheduler;
import com.medicationinventory.sync.DailyInventoryMaintenanceScheduler;
import com.medicationinventory.sync.InventorySettingsSyncCoordinator;
import com.medicationinventory.sync.MedicineCloudSyncCoordinator;
import com.medicationinventory.sync.BatchCloudSyncCoordinator;
import com.medicationinventory.sync.SharedHistoryCloudSyncCoordinator;

import com.medicationinventory.security.RestrictedClassificationCacheGuard;
import com.medicationinventory.security.RestrictedClassificationAccessGate;


/**
 * Acts as the application composition root: creates the Room database and executors, then wires data sources, repositories, domain services, sync coordinators, schedulers, and security guards around those shared instances. This keeps Activities and ViewModels dependent on application-level services rather than constructing persistence or Firebase clients themselves. Construction also starts long-lived observers needed for settings, alerts, account security, and cloud synchronization.
 */
public class AppContainer
{
    private final AppDatabase appDatabase;
    private final AppExecutors appExecutors;
    private final FirebaseAuthDataSource firebaseAuthDataSource;
    private final FirestoreUserProfileDataSource firestoreUserProfileDataSource;
    private final UserRepository userRepository;
    private final MedicineRepository medicineRepository;
    private final MedicineCloudSyncCoordinator medicineCloudSyncCoordinator;
    private final MedicineBarcodeCloudSyncCoordinator medicineBarcodeCloudSyncCoordinator;
    private final BatchCloudSyncCoordinator batchCloudSyncCoordinator;
    private final SharedHistoryCloudSyncCoordinator sharedHistoryCloudSyncCoordinator;
    private final AuthenticationRepository authenticationRepository;
    private final OfflineSessionController offlineSessionController;
    private final BatchRepository batchRepository;
    private final LocalStockOperationService localStockOperationService;
    private final DeviceIdProvider deviceIdProvider;
    private final ReceiptService receiptService;
    private final FirestoreInventoryOperationDataSource firestoreInventoryOperationDataSource;
    private final InventorySyncRepository inventorySyncRepository;
    private final InventorySyncScheduler inventorySyncScheduler;
    private final TrustedInventoryProcessorDataSource trustedInventoryProcessorDataSource;
    private final TrustedInventoryReconciliationDataSource trustedInventoryReconciliationDataSource;
    private final InventoryReconciliationRepository inventoryReconciliationRepository;
    private final FefoAllocationService fefoAllocationService;
    private final IssueStockRepository issueStockRepository;
    private final IssueService issueService;
    private final MovementHistoryRepository movementHistoryRepository;
    private final AdjustmentService adjustmentService;
    private final CorrectionService correctionService;
    private final InventorySettingsSyncCoordinator inventorySettingsSyncCoordinator;
    private final LiveData<InventorySettings> inventorySettingsLiveData;
    private final Observer<InventorySettings> inventorySettingsAlertObserver;
    private final ReorderRecommendationService reorderRecommendationService;
    private final AlertRepository alertRepository;
    private final AlertEvaluationService alertEvaluationService;
    private final AlertRefreshService alertRefreshService;
    private final AlertInventoryObserver alertInventoryObserver;
    private final DailyInventoryMaintenanceService dailyInventoryMaintenanceService;
    private final DailyInventoryMaintenanceScheduler dailyInventoryMaintenanceScheduler;
    private final ReorderRecommendationsRepository reorderRecommendationsRepository;
    private final StockLevelReportService stockLevelReportService;
    private final StockLevelReportRepository stockLevelReportRepository;
    private final AlertReportRepository alertReportRepository;
    private final ReportDateRangeService reportDateRangeService;
    private final MovementReportRepository movementReportRepository;
    private final AnalyticsReportService analyticsReportService;
    private final AnalyticsReportRepository analyticsReportRepository;
    private final FirestoreAuditDataSource firestoreAuditDataSource;
    private final AuditRepository auditRepository;
    private final SyncStatusRepository syncStatusRepository;
    private final SyncMetadataRepository syncMetadataRepository;
    private final TrustedUserAdminDataSource trustedUserAdminDataSource;
    private final TrustedTotpRecoveryDataSource trustedTotpRecoveryDataSource;
    private final AdminUserRepository adminUserRepository;
    private final FirestoreMedicineClassificationDataSource
            firestoreMedicineClassificationDataSource;
    private final MedicineClassificationRepository medicineClassificationRepository;
    private final RestrictedClassificationCacheGuard restrictedClassificationCacheGuard;
    private final RestrictedClassificationAccessGate classificationAccessGate;
    private final TrustedMedicineClassificationDataSource trustedMedicineClassificationDataSource;
    private final FirestoreInventorySettingsDataSource firestoreInventorySettingsDataSource;
    private final InventorySettingsRepository inventorySettingsRepository;
    private final TrustedInventorySettingsDataSource trustedInventorySettingsDataSource;
    private final TrustedMedicineMasterDataSource trustedMedicineMasterDataSource;

    /**
     * Builds the shared dependency graph from the application context.
     * Database access uses the application-scoped Room singleton; Firebase and trusted backend sources are then injected into repositories and workflows.
     * @param context application context used for persistent and device-scoped services
     */
    public AppContainer(Context context)
    {
        appDatabase = AppDatabase.getInstance(context.getApplicationContext());
        appExecutors = AppExecutors.getInstance();

        SyncQueueDao syncQueueDao = appDatabase.syncQueueDao();
        UserDao userDao = appDatabase.userDao();
        MedicineDao medicineDao = appDatabase.medicineDao();
        MedicineBarcodeDao barcodeDao = appDatabase.medicineBarcodeDao();
        BatchDao batchDao = appDatabase.batchDao();
        StockOperationDao stockOperationDao = appDatabase.stockOperationDao();
        InventoryOperationDao inventoryOperationDao = appDatabase.inventoryOperationDao();
        AllocationDao allocationDao = appDatabase.allocationDao();
        TransactionDao transactionDao = appDatabase.transactionDao();
        AuditDao auditDao = appDatabase.auditDao();
        SyncMetadataDao syncMetadataDao = appDatabase.syncMetadataDao();
        MedicineClassificationDao medicineClassificationDao = appDatabase
                .medicineClassificationDao();
        InventorySettingsDao inventorySettingsDao = appDatabase.inventorySettingsDao();


        firebaseAuthDataSource =
                new FirebaseAuthDataSource();
        firestoreUserProfileDataSource =
                new FirestoreUserProfileDataSource();
        userRepository =
                new UserRepository(userDao, firestoreUserProfileDataSource, appExecutors);
        trustedMedicineMasterDataSource = new TrustedMedicineMasterDataSource(
                FirebaseAuth.getInstance(), BuildConfig.TRUSTED_BACKEND_URL);
        medicineRepository =
                new MedicineRepository(medicineDao, barcodeDao, appExecutors,
                        trustedMedicineMasterDataSource, FirebaseFirestore.getInstance(),
                        FirebaseAuth.getInstance(),
                        new TrustedMedicineBarcodeDataSource(FirebaseAuth.getInstance(),
                                BuildConfig.TRUSTED_BACKEND_URL));
        medicineBarcodeCloudSyncCoordinator = new MedicineBarcodeCloudSyncCoordinator(
                FirebaseAuth.getInstance(), FirebaseFirestore.getInstance(),
                appDatabase, appExecutors);

        medicineCloudSyncCoordinator = new MedicineCloudSyncCoordinator(
                FirebaseAuth.getInstance(), FirebaseFirestore.getInstance(),
                appDatabase, appExecutors);
        batchCloudSyncCoordinator = new BatchCloudSyncCoordinator(
                FirebaseAuth.getInstance(), FirebaseFirestore.getInstance(),
                appDatabase, appExecutors);
        sharedHistoryCloudSyncCoordinator = new SharedHistoryCloudSyncCoordinator(
                FirebaseAuth.getInstance(), FirebaseFirestore.getInstance(),
                appDatabase, appExecutors);
        offlineSessionController = new OfflineSessionController(new OfflineSessionStore(context));
        authenticationRepository =
                new AuthenticationRepository(firebaseAuthDataSource, userRepository,
                        offlineSessionController);
        batchRepository =
                new BatchRepository(batchDao, appExecutors);
        localStockOperationService =
                new LocalStockOperationService(stockOperationDao, appExecutors);
        deviceIdProvider =
                new DeviceIdProvider(context);
        inventorySyncScheduler =
                new InventorySyncScheduler(context);
        firestoreInventoryOperationDataSource =
                new FirestoreInventoryOperationDataSource(FirebaseFirestore.getInstance());
        inventorySyncRepository =
                new InventorySyncRepository(inventoryOperationDao, allocationDao, batchDao,
                        transactionDao, syncQueueDao, firestoreInventoryOperationDataSource,
                        authenticationRepository, appExecutors);
        receiptService =
                new ReceiptService(authenticationRepository, userRepository, medicineRepository,
                        batchRepository, localStockOperationService, deviceIdProvider,
                        inventorySyncScheduler);
        trustedInventoryProcessorDataSource =
                new TrustedInventoryProcessorDataSource(FirebaseAuth.getInstance(),
                        BuildConfig.TRUSTED_BACKEND_URL);
        trustedInventoryReconciliationDataSource =
                new TrustedInventoryReconciliationDataSource(FirebaseAuth.getInstance(),
                        BuildConfig.TRUSTED_BACKEND_URL);
        inventoryReconciliationRepository =
                new InventoryReconciliationRepository(appDatabase);
        fefoAllocationService =
                new FefoAllocationService();
        reorderRecommendationService =
                new ReorderRecommendationService();
        firestoreInventorySettingsDataSource =
                new FirestoreInventorySettingsDataSource(FirebaseFirestore.getInstance());
        trustedInventorySettingsDataSource =
                new TrustedInventorySettingsDataSource(FirebaseAuth.getInstance(),
                        BuildConfig.TRUSTED_BACKEND_URL);
        inventorySettingsRepository =
                new InventorySettingsRepository(inventorySettingsDao,
                        firestoreInventorySettingsDataSource, trustedInventorySettingsDataSource,
                        appExecutors);
        reorderRecommendationsRepository =
                new ReorderRecommendationsRepository(medicineDao, batchDao,
                        reorderRecommendationService, appExecutors,
                        inventorySettingsRepository);
        stockLevelReportService =
                new StockLevelReportService(reorderRecommendationService);
        alertRepository =
                new AlertRepository(appDatabase, appExecutors);
        alertEvaluationService =
                new AlertEvaluationService(reorderRecommendationService);
        alertRefreshService =
                new AlertRefreshService(alertEvaluationService, alertRepository,
                        inventorySettingsRepository);
        alertInventoryObserver =
                new AlertInventoryObserver(appDatabase, alertRefreshService, appExecutors);
        alertInventoryObserver.start();
        inventorySettingsLiveData =
                inventorySettingsRepository.observeInventorySettings();
        inventorySettingsAlertObserver =
                settings -> appExecutors.databaseIO().execute(
                        alertInventoryObserver::refreshAllNowBlocking);
        inventorySettingsLiveData.observeForever(inventorySettingsAlertObserver);
        inventorySettingsSyncCoordinator =
                new InventorySettingsSyncCoordinator(FirebaseAuth.getInstance(), userRepository,
                        inventorySettingsRepository);
        inventorySettingsSyncCoordinator.start();
        dailyInventoryMaintenanceService =
                new DailyInventoryMaintenanceService(batchDao, alertInventoryObserver);
        dailyInventoryMaintenanceScheduler =
                new DailyInventoryMaintenanceScheduler(context);
        dailyInventoryMaintenanceScheduler.scheduleDaily();

        appExecutors.databaseIO().execute(dailyInventoryMaintenanceService::runNowBlocking);
        issueStockRepository =
                new IssueStockRepository(appDatabase.batchDao(), fefoAllocationService);
        issueService =
                new IssueService(authenticationRepository, userRepository, medicineRepository,
                        localStockOperationService, deviceIdProvider, inventorySyncScheduler);
        adjustmentService =
                new AdjustmentService(authenticationRepository, userRepository, medicineRepository,
                        localStockOperationService, deviceIdProvider, inventorySyncScheduler);
        correctionService =
                new CorrectionService(authenticationRepository, userRepository, medicineRepository,
                        localStockOperationService, deviceIdProvider, inventorySyncScheduler);
        movementHistoryRepository =
                new MovementHistoryRepository(
                        transactionDao);
        stockLevelReportRepository =
                new StockLevelReportRepository(medicineDao, batchDao, stockLevelReportService,
                        appExecutors, inventorySettingsRepository);
        alertReportRepository =
                new AlertReportRepository(appDatabase.alertDao());
        reportDateRangeService =
                new ReportDateRangeService();
        movementReportRepository =
                new MovementReportRepository(appDatabase.transactionDao());
        analyticsReportService =
                new AnalyticsReportService();
        analyticsReportRepository =
                new AnalyticsReportRepository(medicineDao, batchDao, appDatabase.alertDao(),
                        transactionDao, analyticsReportService, appExecutors);
        firestoreAuditDataSource =
                new FirestoreAuditDataSource(FirebaseFirestore.getInstance());
        auditRepository =
                new AuditRepository(auditDao, firestoreAuditDataSource, appExecutors);
        syncStatusRepository =
                new SyncStatusRepository(inventoryOperationDao);
        syncMetadataRepository =
                new SyncMetadataRepository(syncMetadataDao);
        trustedUserAdminDataSource =
                new TrustedUserAdminDataSource(FirebaseAuth.getInstance(), BuildConfig
                        .TRUSTED_BACKEND_URL);
        trustedTotpRecoveryDataSource =
                new TrustedTotpRecoveryDataSource(FirebaseAuth.getInstance(),
                        BuildConfig.TRUSTED_BACKEND_URL);
        adminUserRepository =
                new AdminUserRepository(trustedUserAdminDataSource);
        firestoreMedicineClassificationDataSource =
                new FirestoreMedicineClassificationDataSource(FirebaseFirestore.getInstance());
        trustedMedicineClassificationDataSource =
                new TrustedMedicineClassificationDataSource(FirebaseAuth.getInstance(),
                        BuildConfig.TRUSTED_BACKEND_URL);
        medicineClassificationRepository =
                new MedicineClassificationRepository(medicineClassificationDao,
                        firestoreMedicineClassificationDataSource,
                        trustedMedicineClassificationDataSource,appExecutors);
        classificationAccessGate = new RestrictedClassificationAccessGate(() -> {
            // Use the active authentication context instead of assuming a previously loaded user is still signed in.
            com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            return user == null ? null : user.getUid();
        });
        medicineClassificationRepository.installRestrictedAccessGate(classificationAccessGate);
        restrictedClassificationCacheGuard =
                new RestrictedClassificationCacheGuard(context.getApplicationContext(),
                        FirebaseAuth.getInstance(), userRepository,
                        medicineClassificationRepository, classificationAccessGate,
                        authenticationRepository);
        restrictedClassificationCacheGuard.start();
        // Auth listener initiates one server-backed fetch for each signed-in UID.
        medicineCloudSyncCoordinator.start();
        medicineBarcodeCloudSyncCoordinator.start();
        // MS2.1: read-only shared batch subscription; waits for Room medicines.
        batchCloudSyncCoordinator.start();
        // MS2.2: restore confirmed shared history without replaying stock.
        sharedHistoryCloudSyncCoordinator.start();


    }
    public AppDatabase getAppDatabase()
    {
        return appDatabase;
    }

    public AppExecutors getAppExecutors()
    {
        return appExecutors;
    }

    public UserRepository getUserRepository()
    {
        return userRepository;
    }
    public MedicineRepository getMedicineRepository()
    {
        return medicineRepository;
    }
    public TrustedMedicineMasterDataSource getTrustedMedicineMasterDataSource() {
        return trustedMedicineMasterDataSource;
    }
    public MedicineCloudSyncCoordinator getMedicineCloudSyncCoordinator() {
        return medicineCloudSyncCoordinator;
    }
    public BatchCloudSyncCoordinator getBatchCloudSyncCoordinator() {
        return batchCloudSyncCoordinator;
    }
    public SharedHistoryCloudSyncCoordinator getSharedHistoryCloudSyncCoordinator() {
        return sharedHistoryCloudSyncCoordinator;
    }
    public BatchRepository getBatchRepository()
    {
        return batchRepository;
    }

    public OfflineSessionController getOfflineSessionController() {
        return offlineSessionController;
    }
    public AuthenticationRepository getAuthenticationRepository()
    {
        return authenticationRepository;
    }
    public InventorySyncRepository getInventorySyncRepository()
    {
        return inventorySyncRepository;
    }
    public FirestoreInventoryOperationDataSource getFirestoreInventoryOperationDataSource()
    {
        return firestoreInventoryOperationDataSource;
    }
    public ReceiptService getReceiptService()
    {
        return receiptService;
    }
    public LocalStockOperationService getLocalStockOperationService()
    {
        return localStockOperationService;
    }
    public TrustedInventoryProcessorDataSource getTrustedInventoryProcessorDataSource()
    {
        return trustedInventoryProcessorDataSource;
    }
    public TrustedInventoryReconciliationDataSource getTrustedInventoryReconciliationDataSource()
    {
        return trustedInventoryReconciliationDataSource;
    }
    public InventoryReconciliationRepository getInventoryReconciliationRepository()
    {
        return inventoryReconciliationRepository;
    }
    public IssueStockRepository getIssueStockRepository()
    {
        return issueStockRepository;
    }
    public IssueService getIssueService()
    {
        return issueService;
    }
    public MovementHistoryRepository getMovementHistoryRepository()
    {
        return movementHistoryRepository;
    }
    public AdjustmentService getAdjustmentService()
    {
        return adjustmentService;
    }
    public CorrectionService getCorrectionService()
    {
        return correctionService;
    }
    public ReorderRecommendationService getReorderRecommendationService()
    {
        return reorderRecommendationService;
    }
    public AlertRepository getAlertRepository()
    {
        return alertRepository;
    }

    public InventorySyncScheduler getInventorySyncScheduler()
    {
        return inventorySyncScheduler;
    }
    public DailyInventoryMaintenanceService getDailyInventoryMaintenanceService()
    {
        return dailyInventoryMaintenanceService;
    }
    public ReorderRecommendationsRepository getReorderRecommendationsRepository()
    {
        return reorderRecommendationsRepository;
    }
    public StockLevelReportService getStockLevelReportService()
    {
        return stockLevelReportService;
    }
    public StockLevelReportRepository getStockLevelReportRepository()
    {
        return stockLevelReportRepository;
    }
    public AlertReportRepository getAlertReportRepository()
    {
        return alertReportRepository;
    }
    public ReportDateRangeService getReportDateRangeService()
    {
        return reportDateRangeService;
    }
    public MovementReportRepository getMovementReportRepository()
    {
        return movementReportRepository;
    }
    public AnalyticsReportRepository getAnalyticsReportRepository()
    {
        return analyticsReportRepository;
    }
    public AuditRepository getAuditRepository()
    {
        return auditRepository;
    }
    public SyncStatusRepository getSyncStatusRepository()
    {
        return syncStatusRepository;
    }
    public SyncMetadataRepository getSyncMetadataRepository()
    {
        return syncMetadataRepository;
    }
    public TrustedTotpRecoveryDataSource getTrustedTotpRecoveryDataSource()
    {
        return trustedTotpRecoveryDataSource;
    }
    public AdminUserRepository getAdminUserRepository()
    {
        return adminUserRepository;
    }
    public MedicineClassificationRepository getMedicineClassificationRepository()
    {
        return medicineClassificationRepository;
    }
    public InventorySettingsRepository getInventorySettingsRepository()
    {
        return inventorySettingsRepository;
    }
}
