package com.termux.terminal;

/** Callback for sending terminal output to an external transport (for example, SSH). */
public interface TerminalSessionOutput {
    void write(byte[] data, int offset, int count);

    /**
     * Called when the terminal size changes.
     * Implementors should resize the PTY/connection to match.
     *
     * @param columns Number of columns
     * @param rows Number of rows
     */
    default void onResize(int columns, int rows) {
        // Default empty implementation for backwards compatibility
    }
}
