package io.github.turbosebo.arduinospringwebcontroller.service;

/**
 * Klasa z komendami protokołu do komunikacji z Arduino.
 */
public final class ArduinoCommands {

    private static final String DAC_PREFIX = "DAC:";
    private static final String FREQ_PREFIX = "FREQ ";
    private static final String BURST_PREFIX = "BURST ";
    private static final String LED_PREFIX = "LED:";

    private ArduinoCommands() {
        // utility class
    }

    // Proste komendy bez parametrów
    public static final String IDENTIFY = "*IDN?";
    public static final String READ_VOLTAGE = "READV?";
    public static final String START = "START";
    public static final String STOP = "STOP";
    public static final String ONCE = "ONCE";

    // Komendy wyboru typu fali
    public static final String WAVE_SINE = "WAVE SIN";
    public static final String WAVE_TRIANGLE = "WAVE TRI";

    // Sterowanie diodą LED (etap 1 projektu webowego)
    public static String setLed(boolean on) {
        return LED_PREFIX + (on ? "1" : "0");
    }

    // Komendy parametryzowane z inżynierki FESG
    public static String setDac(int value) {
        StringBuilder sb = new StringBuilder(DAC_PREFIX.length() + 5);
        return sb.append(DAC_PREFIX).append(value).toString();
    }

    public static String setDac(String value) {
        return DAC_PREFIX + value;
    }

    public static String setFrequency(float frequency) {
        StringBuilder sb = new StringBuilder(FREQ_PREFIX.length() + 8);
        return sb.append(FREQ_PREFIX).append(frequency).toString();
    }

    public static String burst(int count) {
        StringBuilder sb = new StringBuilder(BURST_PREFIX.length() + 6);
        return sb.append(BURST_PREFIX).append(count).toString();
    }
}