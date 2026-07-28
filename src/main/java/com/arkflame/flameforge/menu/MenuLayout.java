package com.arkflame.flameforge.menu;

public final class MenuLayout {
    public static final int SIZE = 54;

    public static final int SLOT_INFO = 4;
    public static final int SLOT_CATALYST = 11;
    public static final int SLOT_INPUT = 13;
    public static final int SLOT_WARD = 15;
    public static final int SLOT_CONFIRM = 22;
    public static final int SLOT_PREVIOUS = 27;
    public static final int SLOT_TIER_START = 28;
    public static final int SLOT_TIER_END = 34;
    public static final int SLOT_NEXT = 35;
    public static final int SLOT_PITY_HISTORY = 40;
    public static final int SLOT_CLOSE = 49;

    public static final int TIERS_PER_PAGE = 7;

    public static final int SLOT_FILLER_START = 0;
    public static final int SLOT_FILLER_END = 3;
    public static final int SLOT_FILLER_AFTER_INFO = 5;
    public static final int SLOT_FILLER_AFTER_CATALYST = 12;
    public static final int SLOT_FILLER_AFTER_INPUT = 14;
    public static final int SLOT_FILLER_AFTER_WARD = 16;
    public static final int SLOT_FILLER_AFTER_CONFIRM = 23;
    public static final int SLOT_FILLER_AFTER_PREVIOUS = 26;
    public static final int SLOT_FILLER_AFTER_TIERS = 36;
    public static final int SLOT_FILLER_AFTER_NEXT = 39;
    public static final int SLOT_FILLER_AFTER_PITY = 41;
    public static final int SLOT_FILLER_AFTER_CLOSE = 50;
    public static final int SLOT_FILLER_END_OF_INVENTORY = 54;

    private MenuLayout() {
    }

    public static int tierSlot(int index) {
        return SLOT_TIER_START + index;
    }

    public static boolean isFillerSlot(int slot) {
        if (slot >= SLOT_FILLER_START && slot <= SLOT_FILLER_END) return true;
        if (slot == SLOT_FILLER_AFTER_INFO) return true;
        if (slot == SLOT_FILLER_AFTER_CATALYST) return true;
        if (slot == SLOT_FILLER_AFTER_INPUT) return true;
        if (slot == SLOT_FILLER_AFTER_WARD) return true;
        if (slot >= SLOT_FILLER_AFTER_CONFIRM && slot <= SLOT_FILLER_AFTER_PREVIOUS - 1) return true;
        if (slot == SLOT_FILLER_AFTER_TIERS) return true;
        if (slot >= SLOT_FILLER_AFTER_NEXT && slot <= SLOT_FILLER_AFTER_PITY - 1) return true;
        if (slot >= SLOT_FILLER_AFTER_PITY && slot <= SLOT_FILLER_AFTER_CLOSE - 1) return true;
        if (slot >= SLOT_FILLER_AFTER_CLOSE && slot < SLOT_FILLER_END_OF_INVENTORY) return true;
        return false;
    }

    public static boolean isInputSlot(int slot) {
        return slot == SLOT_INPUT;
    }

    public static boolean isCatalystSlot(int slot) {
        return slot == SLOT_CATALYST;
    }

    public static boolean isWardSlot(int slot) {
        return slot == SLOT_WARD;
    }

    public static boolean isTierSlot(int slot) {
        return slot >= SLOT_TIER_START && slot <= SLOT_TIER_END;
    }
}
