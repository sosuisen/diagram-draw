package com.sosuisha.classdiagram;

public enum ClassStereotype {
    NONE(""),
    INTERFACE("«interface»");

    private final String label;

    ClassStereotype(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
