package io.github.dsheirer.hydrasdr;

/** Values of {@code enum hydrasdr_decimation_mode}, in native order. */
public enum DecimationMode {
    /** Uses the lowest hardware rate that reaches the target rate. */
    LOW_BANDWIDTH,
    /** Uses the highest hardware rate and more filtering for better signal quality. */
    HIGH_DEFINITION
}
