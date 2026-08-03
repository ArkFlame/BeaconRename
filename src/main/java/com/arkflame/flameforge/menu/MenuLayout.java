package com.arkflame.flameforge.menu;

public final class MenuLayout {
    public static final int SIZE = 54;

    public static final int SLOT_INFO = 4;
    public static final int SLOT_CURRENT_TIER = 20;
    public static final int SLOT_INPUT = 22;
    public static final int SLOT_VARIANTS = 24;
    public static final int SLOT_REQUIREMENTS = 29;
    public static final int SLOT_CONFIRM = 31;
    public static final int SLOT_CHANCES = 33;
    public static final int SLOT_CLOSE = 49;

    private MenuLayout() {
    }

    public static boolean isInputSlot(int slot) {
        return slot == SLOT_INPUT;
    }
}
