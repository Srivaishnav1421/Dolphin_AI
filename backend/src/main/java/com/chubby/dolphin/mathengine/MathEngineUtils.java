package com.chubby.dolphin.mathengine;

public final class MathEngineUtils {

    private MathEngineUtils() {}

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static boolean positive(Double value) {
        return value != null && value > 0;
    }

    public static boolean positive(Number value) {
        return value != null && value.doubleValue() > 0;
    }

    public static double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    public static long safe(Long value) {
        return value == null ? 0L : value;
    }

    public static int safe(Integer value) {
        return value == null ? 0 : value;
    }

    public static Double ctrPercent(Double storedCtr, Long clicks, Long impressions) {
        if (storedCtr != null && storedCtr > 0) {
            return storedCtr <= 0.05 ? storedCtr * 100.0 : storedCtr;
        }
        if (clicks != null && impressions != null && clicks >= 0 && impressions > 0) {
            return (clicks * 100.0) / impressions;
        }
        return null;
    }

    public static Double cpl(Double storedCpl, Double spend, Number conversions) {
        if (storedCpl != null && storedCpl > 0) {
            return storedCpl;
        }
        if (spend != null && spend >= 0 && conversions != null && conversions.doubleValue() > 0) {
            return spend / conversions.doubleValue();
        }
        return null;
    }

    public static Double cpc(Double storedCpc, Double spend, Long clicks) {
        if (storedCpc != null && storedCpc > 0) {
            return storedCpc;
        }
        if (spend != null && spend >= 0 && clicks != null && clicks > 0) {
            return spend / clicks;
        }
        return null;
    }
}
