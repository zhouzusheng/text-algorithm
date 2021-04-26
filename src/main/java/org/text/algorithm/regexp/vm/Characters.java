package org.text.algorithm.regexp.vm;

/**
 * Wraps Character methods to be overridden for GWT.
 */
final class Characters {
    private Characters() {
    }

    static int toLowerCase(int codePoint) {
        return Character.toLowerCase(codePoint);
    }

    static int toUpperCase(int codePoint) {
        return Character.toUpperCase(codePoint);
    }
}
