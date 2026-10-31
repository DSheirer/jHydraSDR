package io.github.dsheirer.hydrasdr;

/** A DDC conversion algorithm accepted by {@link HydraSdrDevice#setConversionAlgorithm}. */
public record ConversionAlgorithm(String name, String description) {
}
