package io.github.dsheirer.hydrasdr;

import io.github.dsheirer.hydrasdr.internal.Native;

/** Thrown when a libhydrasdr function reports an error. */
public class HydraSdrException extends Exception {
    private static final long serialVersionUID = 1L;

    private final int code;

    HydraSdrException(String function, int code) {
        super(function + " failed: " + Native.errorName(code) + " (" + code + ")");
        this.code = code;
    }

    /** Returns the {@code hydrasdr_error} value the native function returned. */
    public int code() {
        return code;
    }

    /** Returns the error as an enum, or {@link HydraSdrError#OTHER} for an unknown code. */
    public HydraSdrError error() {
        return HydraSdrError.fromCode(code);
    }
}
