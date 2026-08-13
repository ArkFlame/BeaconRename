package com.arkflame.flameforge.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class EquipmentCatalog {
    public static final String FALLBACK_CATEGORY_ID = "amulet";

    public static final class Category {
        private final String id;
        private final boolean fallback;
        private final List<String> materials;
        private final List<String> progression;

        public Category(String id, boolean fallback, List<String> materials, List<String> progression) {
            this.id = id;
            this.fallback = fallback;
            this.materials = Collections.unmodifiableList(new ArrayList<>(materials));
            this.progression = Collections.unmodifiableList(new ArrayList<>(progression));
        }

        public String getId() { return id; }
        public boolean isFallback() { return fallback; }
        public List<String> getMaterials() { return materials; }
        public List<String> getProgression() { return progression; }
    }

    private final Map<String, Category> categories;
    private final Map<String, String> categoryByMaterial;
    private final Category fallbackCategory;

    public EquipmentCatalog(List<Category> categories) {
        Map<String, Category> byId = new LinkedHashMap<>();
        Map<String, String> byMaterial = new LinkedHashMap<>();
        Category fallback = null;
        for (Category category : categories) {
            String id = normalize(category.getId());
            byId.put(id, category);
            if (category.isFallback()) {
                fallback = category;
            }
            for (String material : category.getMaterials()) {
                byMaterial.put(normalize(material), id);
            }
        }
        this.categories = Collections.unmodifiableMap(byId);
        this.categoryByMaterial = Collections.unmodifiableMap(byMaterial);
        this.fallbackCategory = fallback != null ? fallback : byId.get(FALLBACK_CATEGORY_ID);
    }

    public static EquipmentCatalog empty() {
        return new EquipmentCatalog(Collections.singletonList(
            new Category(FALLBACK_CATEGORY_ID, true, Collections.emptyList(), Collections.emptyList())));
    }

    public Map<String, Category> getCategories() {
        return categories;
    }

    public List<Category> all() {
        return Collections.unmodifiableList(new ArrayList<>(categories.values()));
    }

    public boolean isEmpty() {
        return categories.isEmpty();
    }

    public Optional<Category> findCategory(String id) {
        return Optional.ofNullable(categories.get(normalize(id)));
    }

    public Optional<Category> findById(String id) {
        return findCategory(id);
    }

    public Optional<Category> findByMaterial(String material) {
        String categoryId = categoryByMaterial.get(normalize(material));
        return Optional.ofNullable(categoryId != null ? categories.get(categoryId) : null);
    }

    public Category categoryForMaterial(String material) {
        return findByMaterial(material).orElseGet(() -> fallbackCategory != null
            ? fallbackCategory
            : new Category(FALLBACK_CATEGORY_ID, true, Collections.emptyList(), Collections.emptyList()));
    }

    public Optional<String> categoryIdForTier(String tierId) {
        String normalized = normalize(tierId);
        for (Category category : categories.values()) {
            for (String id : category.getProgression()) {
                if (normalized.equals(normalize(id))) {
                    return Optional.of(category.getId());
                }
            }
        }
        return Optional.empty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
