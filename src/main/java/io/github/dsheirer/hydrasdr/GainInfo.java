package io.github.dsheirer.hydrasdr;

/** Cached value and limits of a gain control. */
public record GainInfo(GainType type, int value, GainRange range, boolean agc, boolean preset) {
}
