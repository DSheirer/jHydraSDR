package io.github.dsheirer.hydrasdr;

/**
 * An RF input port.
 *
 * @param biasTeeVoltage output voltage, valid when {@code hasBiasTee} is true
 * @param biasTeeMaxCurrentMilliamps maximum current, valid when {@code hasBiasTee} is true
 */
public record RfPortInfo(String name, long minFrequency, long maxFrequency, boolean hasBiasTee,
                         float biasTeeVoltage, float biasTeeMaxCurrentMilliamps) {
}
