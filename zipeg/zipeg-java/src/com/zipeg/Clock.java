package com.zipeg;

import java.util.*;
import java.util.concurrent.*;

public class Clock {

    private static final Map start = new ConcurrentHashMap();

    private Clock() {
    }

    /** @return microsecond count (probably system power up)
     */
    public static long microseconds() {
        return System.nanoTime() / 1000;
    }

    public static long nanoTime() {
        return System.nanoTime();
    }

    /**
     * @param microseconds time to format into string
     * @return string formated milliseconds like 1234 microseconds becomes "1.23" milliseconds
     */
    public static String milliseconds(long microseconds) {
        microseconds /= 10;
        int d = (int)(microseconds % 100);
        return microseconds / 100 + (d < 10 ? ".0" + d : "." + d);
    }

    public static void start(String label) {
        String key = Thread.currentThread().getId() + ":" + label;
        start.put(key, Long.valueOf(Clock.nanoTime()));
    }

    public static long end(String label) {
        long now = System.nanoTime();
        String key = Thread.currentThread().getId() + ":" + label;
        long d = now - ((Long)start.get(key)).longValue();
        Debug.traceln(label + " " + formatNanoTime(d));
        return d;
    }

    public static String formatNanoTime(long delta) {
        if (Debug.isDebug()) {
            // report seen: negative delta: -1369734000 (-1.369sec)
            assert delta >= 0 : "negative delta: " + delta;
        } else if (delta < 0) {
            delta = 0; // ntpd turned clock backward?
        }
        if (delta < 100000) {
            return f((int)delta) + " microseconds";
        }
        delta /= 1000;
        if (delta < 100000) {
            return f((int)delta) + " milliseconds";
        }
        delta /= 1000;
        int d = (int)((delta / 100) % 10);
        delta /= 1000;
        int s = (int)(delta % 60);
        delta /= 60;
        int m = (int)(delta % 60);
        delta /= 60;
        int h = (int)delta;
        return h + ":" + d00(m) + ":" + d00(s) + "." + d;
    }

    private static String f(int n) {
        assert 0 <= n && n < 100000;
        int k = n / 1000;
        int f = (n % 1000) / 100;
        if (f != 0) {
            return k + "." + f;
        } else {
            return d00(k);
        }
    }

    private static String d00(int n) {
        assert 0 <= n && n < 100;
        if (n < 10) {
            return "0" + n;
        } else {
            return "" + n;
        }
    }

}
