package io.github.dsheirer.hydrasdr;

/** A device temperature reading. The values are meaningful only when {@code valid} is true. */
public record Temperature(float celsius, float fahrenheit, boolean valid) {
}
