package com.arkflame.flameforge.model;

import java.util.Objects;

public final class AttributeSpec {
    private final String attribute;
    private final double minValue;
    private final double maxValue;
    private final String operation;

    private AttributeSpec(String attribute, double minValue, double maxValue, String operation) {
        this.attribute = attribute;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.operation = operation;
    }

    public static AttributeSpec of(String attribute, double minValue, double maxValue, String operation) {
        return new AttributeSpec(Objects.requireNonNull(attribute), minValue, maxValue, Objects.requireNonNull(operation));
    }

    public String getAttribute() { return attribute; }
    public double getMinValue() { return minValue; }
    public double getMaxValue() { return maxValue; }
    public String getOperation() { return operation; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttributeSpec)) return false;
        AttributeSpec that = (AttributeSpec) o;
        return Double.compare(that.minValue, minValue) == 0 &&
               Double.compare(that.maxValue, maxValue) == 0 &&
               Objects.equals(attribute, that.attribute) &&
               Objects.equals(operation, that.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attribute, minValue, maxValue, operation);
    }

    @Override
    public String toString() {
        return "AttributeSpec{attribute=" + attribute + ", minValue=" + minValue +
               ", maxValue=" + maxValue + ", operation=" + operation + "}";
    }
}
