package com.medicationinventory.ui.inventory;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.MedicineClassificationRepository;
import com.medicationinventory.data.local.entity.MedicineBarcodeEntity;
import com.medicationinventory.model.domain.MedicineClassification;
import com.medicationinventory.model.enums.Permission;
import com.medicationinventory.data.repository.UserRepository;
import com.medicationinventory.model.domain.InventoryFilterState;
import com.medicationinventory.model.domain.InventoryMedicineItem;
import com.medicationinventory.model.domain.UserProfile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;



/**
 * Supplies the inventory screen with profile and medicine streams, current filter state, and the
 * derived list displayed by the activity. The base list follows Room-backed repository updates;
 * filtering and sorting are applied locally, while ABC/VED classifications are fetched only when
 * the signed-in profile has the corresponding permission. Date-sensitive stock snapshots are
 * refreshed when the calendar day changes so expiry and alert counts do not remain stale overnight.
 */
public class InventoryViewModel extends ViewModel {
    private final LiveData<UserProfile> userProfile;
    private final LiveData<Integer> medicineCount;
    private final LiveData<List<String>> availableCategories;
    private final MedicineRepository medicineRepository;
    private final MedicineClassificationRepository medicineClassificationRepository;
    private final MutableLiveData<Boolean> abcVedFiltersAvailable =
            new MutableLiveData<>(false);
    private final MutableLiveData<String> abcVedRefreshStatus = new MutableLiveData<>(null);
    private LiveData<List<MedicineClassification>> activeClassificationSource;
    private Map<String, MedicineClassification> classifiedCache = Collections.emptyMap();
    private Map<String, String> barcodeOwners = Collections.emptyMap();
    private boolean mayViewClassifications = false;
    private UserProfile currentProfile;
    private boolean initialClassificationRefreshRequested = false;
    private boolean classificationRefreshInProgress = false;
    private int permissionGeneration = 0;

    private final MutableLiveData<InventoryFilterState> filterState =
            new MutableLiveData<>(InventoryFilterState.defaults());
    private final MediatorLiveData<List<InventoryMedicineItem>> stockSnapshot =
            new MediatorLiveData<>();
    private final MediatorLiveData<List<InventoryMedicineItem>> medicines =
            new MediatorLiveData<>();

    private LiveData<List<InventoryMedicineItem>> currentStockSource;
    private LocalDate currentSnapshotDate;


    public InventoryViewModel(AuthenticationRepository authenticationRepository,
                              UserRepository userRepository, MedicineRepository medicineRepository,
                              MedicineClassificationRepository medicineClassificationRepository) {
        if (medicineClassificationRepository == null)
            throw new IllegalArgumentException("MedicineClassificationRepository is required");
        this.medicineRepository = medicineRepository;
        this.medicineClassificationRepository = medicineClassificationRepository;
        String uid = authenticationRepository.getCurrentUserId();
        userProfile = uid == null || uid.trim().isEmpty()
                ? new MutableLiveData<>(null)
                : userRepository.observeUser(uid);
        medicineCount = medicineRepository.observeActiveMedicineCount();
        availableCategories = medicineRepository.observeActiveCategories();

        medicines.addSource(userProfile, profile -> {
            currentProfile = profile;
            applyProfilePermissions(profile);
        });
        medicines.addSource(medicineClassificationRepository.observeRestrictedAccess(),
                ignored -> applyProfilePermissions(currentProfile));
        medicines.addSource(stockSnapshot, unused -> {
            recompute();
            refreshClassificationsInitiallyIfReady();
        });
        medicines.addSource(filterState, unused -> recompute());
        medicines.addSource(medicineRepository.observeBarcodeMappings(), rows -> {
            Map<String, String> owners = new HashMap<>();
            if (rows != null) for (MedicineBarcodeEntity row : rows) {
                if (row != null) owners.put(row.getBarcode(), row.getMedicineId());
            }
            barcodeOwners = owners;
            recompute();
        });
        refreshCalendarIfNeeded();
    }


    private void recompute() {
        List<InventoryMedicineItem> snapshot = stockSnapshot.getValue();
        InventoryFilterState state = filterState.getValue();
        InventoryFilterState effective = state == null ? InventoryFilterState.defaults() : state;

        String barcodeMedicineId = barcodeOwners.get(effective.getSearchQuery());
        List<InventoryMedicineItem> rows = InventoryFilterEngine.apply(
                snapshot == null ? Collections.emptyList() : snapshot,
                barcodeMedicineId == null ? effective : effective.withSearchQuery(""),
                mayViewClassifications ? classifiedCache : Collections.emptyMap(),
                mayViewClassifications);
        if (barcodeMedicineId != null) {
            List<InventoryMedicineItem> exact = new ArrayList<>();
            for (InventoryMedicineItem row : rows) {
                if (row != null && row.getMedicine() != null
                        && barcodeMedicineId.equals(row.getMedicine().getMedicineId()))
                    exact.add(row);
            }
            rows = exact;
        }
        medicines.setValue(rows);
    }


    public void refreshCalendarIfNeeded() {
        LocalDate today = LocalDate.now();
        if (today.equals(currentSnapshotDate)) return;
        currentSnapshotDate = today;
        if (currentStockSource != null) stockSnapshot.removeSource(currentStockSource);
        ZoneId zone = ZoneId.systemDefault();
        long day0 = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long day15 = today.plusDays(15).atStartOfDay(zone).toInstant().toEpochMilli();
        long day31 = today.plusDays(31).atStartOfDay(zone).toInstant().toEpochMilli();
        long day91 = today.plusDays(91).atStartOfDay(zone).toInstant().toEpochMilli();
        currentStockSource = medicineRepository.observeInventoryItems(
                day0, day15, day31, day91);
        stockSnapshot.addSource(currentStockSource, stockSnapshot::setValue);
    }



    private void applyProfilePermissions(UserProfile profile) {
        boolean allowed = profile != null && profile.isActive()
                && profile.can(Permission.VIEW_MEDICINES)
                && profile.can(Permission.VIEW_ABC_VED)
                && medicineClassificationRepository.isRestrictedAccessGranted();
        if (allowed == mayViewClassifications) return;
        mayViewClassifications = allowed;
        permissionGeneration++;
        abcVedFiltersAvailable.setValue(allowed);
        if (!allowed) {
            if (activeClassificationSource != null) {
                medicines.removeSource(activeClassificationSource);
                activeClassificationSource = null;
            }
            classifiedCache = Collections.emptyMap();
            classificationRefreshInProgress = false;
            initialClassificationRefreshRequested = false;
            abcVedRefreshStatus.setValue(null);
            InventoryFilterState state = getCurrentFilters();
            if (state.hasClassifiedCriteria()) {
                filterState.setValue(state.withoutClassifiedCriteria());
            } else {
                recompute();
            }
            return;
        }

        activeClassificationSource = medicineClassificationRepository.observeActiveForAnalytics();
        medicines.addSource(activeClassificationSource, rows -> {
            if (!mayViewClassifications) return;
            Map<String, MedicineClassification> fresh = new HashMap<>();
            if (rows != null) for (MedicineClassification classification : rows) {
                if (classification != null && classification.getMedicineId() != null)
                    fresh.put(classification.getMedicineId(), classification);
            }
            classifiedCache = fresh;
            recompute();
        });
        recompute();
        refreshClassificationsInitiallyIfReady();
    }


    private void refreshClassificationsInitiallyIfReady() {
        if (!mayViewClassifications || initialClassificationRefreshRequested
                || stockSnapshot.getValue() == null) return;
        initialClassificationRefreshRequested = true;
        refreshClassifications();
    }


    /**
     * Refreshes restricted ABC/VED data only for a permitted profile and only after inventory has
     * loaded. The in-progress guard prevents overlapping requests; results are associated with the
     * captured inventory snapshot before filters are recomputed, and access loss clears the data.
     */
    public void refreshClassifications() {
        if (!mayViewClassifications || classificationRefreshInProgress) return;
        List<InventoryMedicineItem> snapshot = stockSnapshot.getValue();
        if (snapshot == null) {
            abcVedRefreshStatus.setValue("Waiting for inventory data...");
            return;
        }
        List<String> ids = new ArrayList<>();
        for (InventoryMedicineItem item : snapshot)
            if (item != null && item.getMedicine().getMedicineId() != null)
                ids.add(item.getMedicine().getMedicineId());
        if (ids.isEmpty()) {
            abcVedRefreshStatus.setValue("No active medicines to refresh.");
            return;
        }
        classificationRefreshInProgress = true;
        final int thisGeneration = permissionGeneration;
        abcVedRefreshStatus.setValue("Refreshing classifications for " + ids.size()
                + " medicines...");
        medicineClassificationRepository.refreshAllFromServer(ids, (successes, failures) -> {
            if (!mayViewClassifications || thisGeneration != permissionGeneration) return;
            classificationRefreshInProgress = false;
            if (failures == 0) {
                abcVedRefreshStatus.postValue("Classifications refreshed for " + successes
                        + " medicines.");
            } else {
                abcVedRefreshStatus.postValue("Partial/offline data: " + failures
                        + " medicine classification(s) could not be refreshed."
                        + " Showing cached results where available.");
            }
        });
    }

    public LiveData<Boolean> getAbcVedFiltersAvailable() { return abcVedFiltersAvailable; }
    public LiveData<String> getAbcVedRefreshStatus() { return abcVedRefreshStatus; }

    public boolean canViewClassifications() { return mayViewClassifications; }

    public LiveData<UserProfile> getUserProfile() { return userProfile; }
    public LiveData<Integer> getMedicineCount() { return medicineCount; }
    public LiveData<List<String>> getAvailableCategories() { return availableCategories; }
    public LiveData<List<InventoryMedicineItem>> getMedicines() { return medicines; }
    public LiveData<InventoryFilterState> getFilters() { return filterState; }
    public InventoryFilterState getCurrentFilters() {
        InventoryFilterState current = filterState.getValue();
        return current == null ? InventoryFilterState.defaults() : current;
    }
    public void setSearchQuery(String query) {
        InventoryFilterState current = getCurrentFilters();
        String safe = query == null ? "" : query.trim();
        if (!safe.equals(current.getSearchQuery()))
            filterState.setValue(current.withSearchQuery(safe));
    }
    public void setSelections(String category, InventoryFilterState.StockStatus status,
                              InventoryFilterState.ExpiryRange expiry, InventoryFilterState.SortOrder sort) {
        filterState.setValue(getCurrentFilters().withSelections(category, status, expiry, sort));
    }
    public void setAllSelections(String category, InventoryFilterState.StockStatus status,
                                 InventoryFilterState.ExpiryRange expiry, InventoryFilterState.SortOrder sort,
                                 InventoryFilterState.AbcClass abc, InventoryFilterState.VedClass ved) {

        if (!mayViewClassifications) {
            abc = InventoryFilterState.AbcClass.ALL;
            ved = InventoryFilterState.VedClass.ALL;
        }
        filterState.setValue(getCurrentFilters().withAllSelections(category, status, expiry,
                sort, abc, ved));
    }

    public void clearSelections() { filterState.setValue(getCurrentFilters().clearSelections()); }
}
