package com.medicationinventory.model.domain;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;




/**
 * Represents Medicine Category Catalog data used by inventory rules and application workflows.
 */
public final class MedicineCategoryCatalog
{
    public static final String OTHER =
            "Other...";


    private static final List<String>
            PREDEFINED_CATEGORIES =
            Collections.unmodifiableList(
                    Arrays.asList(
                            "Analgesics & Antipyretics",
                            "Antibiotics",
                            "Antifungals",
                            "Antivirals",
                            "Cardiovascular",
                            "Central Nervous System",
                            "Dermatological",
                            "Diabetes & Endocrine",
                            "Emergency Medicines",
                            "Gastrointestinal",
                            "Allergy & Antihistamines",
                            "Musculoskeletal",
                            "Obstetric & Gynaecological",
                            "Ophthalmic",
                            "Respiratory",
                            "Vaccines & Immunologicals",
                            "Vitamins, Minerals & Supplements"
                    )
            );


    private MedicineCategoryCatalog()
    {
    }


    public static List<String>
    predefinedCategories()
    {
        return PREDEFINED_CATEGORIES;
    }


}