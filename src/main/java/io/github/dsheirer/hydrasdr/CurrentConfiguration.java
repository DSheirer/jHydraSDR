package io.github.dsheirer.hydrasdr;

/**
 * The configuration a device is currently using.
 *
 * @param bandwidth RF bandwidth in Hz, or 0 if not set
 * @param sampleRate effective sample rate in Hz, or 0 if not set
 * @param hardwareSampleRate ADC sample rate in Hz
 * @param adcBits ADC bit depth, or 0 if unknown
 */
public record CurrentConfiguration(DecimationMode decimationMode, boolean packed, boolean bandwidthAutoSelected,
                                   int adcBits, long decimationFactor, long bandwidth, long sampleRate,
                                   long hardwareSampleRate, SampleType sampleType) {
}
