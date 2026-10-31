package io.github.dsheirer.hydrasdr;

/** Valid values of a gain control. */
public record GainRange(int min, int max, int step, int defaultValue) {
}
