package io.github.dsheirer.hydrasdr;

/** Error codes of {@code enum hydrasdr_error}. */
public enum HydraSdrError {
    INVALID_PARAM(-2),
    NOT_FOUND(-5),
    BUSY(-6),
    NO_MEM(-11),
    UNSUPPORTED(-12),
    LIBUSB(-1000),
    THREAD(-1001),
    STREAMING_THREAD_ERR(-1002),
    STREAMING_STOPPED(-1003),
    OTHER(-9999);

    private final int code;

    HydraSdrError(int code) {
        this.code = code;
    }

    /** Returns the native error code. */
    public int code() {
        return code;
    }

    /** Returns the error for a native code, or {@link #OTHER} if it is unknown. */
    public static HydraSdrError fromCode(int code) {
        for (HydraSdrError error : values()) {
            if (error.code == code) {
                return error;
            }
        }
        return OTHER;
    }
}
