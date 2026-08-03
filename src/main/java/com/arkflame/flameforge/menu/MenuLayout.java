package com.arkflame.flameforge.menu;

public final class MenuLayout {
    public static final int SIZE = 27;

    public static final int SLOT_INFO = 4;
    public static final int SLOT_INPUT = 13;
    public static final int SLOT_CONFIRM = 22;
    public static final int SLOT_CLOSE = 26;

    private MenuLayout() {
    }

    public static boolean isInputSlot(int slot) {
        return slot == SLOT_INPUT;
    }
}
