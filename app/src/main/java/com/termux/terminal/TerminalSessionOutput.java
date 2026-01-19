package com.termux.terminal;

/** Callback for sending terminal output to an external transport (for example, SSH). */
public interface TerminalSessionOutput {
    void write(byte[] data, int offset, int count);
}
