package com.arkflame.flameforge.menu;

public final class MenuLayout {
    public static final int SIZE = 54;

    public static final int SLOT_INPUT = 22;
    public static final int SLOT_CONFIRM = 31;

    private MenuLayout() {
    }

    public static boolean isInputSlot(int slot) {
        return slot == SLOT_INPUT;
    }
}
