package com.zipeg;

import java.util.*;

public class Memory {
    
    public static final String OUT_OF_MEMORY =
            "Fatal Error: Out Of Memory.\n\nZipeg will " +
                    (Util.isWindows ? "exit" : "quit") + " now.";
    private static final int RESERVED = 8 * Util.MB;
    private static byte[] reserve;
    private static final LinkedList waste = new LinkedList();


    private Memory() {
    }

    public static synchronized void reserveSafetyPool() {
        if (reserve == null) {
            reserve = new byte[RESERVED];
            for (int i = 0; i < Memory.reserve.length; i += 1023) {
                Memory.reserve[i] = (byte)(i & 0xFF); // make memory commited
            }
        }
    }

    public static synchronized void releaseSafetyPool() {
        reserve = null;
        System.gc();
        Util.sleep(1000);
    }

    public static synchronized boolean isMemoryLow() {
        return reserve == null;
    }

    public static synchronized boolean isOutOfMemory(Throwable x) {
        while (x.getCause() != null) {
            x = x.getCause();
        }
        if (x instanceof OutOfMemoryError) {
            releaseSafetyPool();
        }
        return reserve == null;
    }

    @SuppressWarnings({"InfiniteLoopStatement"})
    public static synchronized void simulateOutOfMemory(LinkedList wasteLand) {
        try {
            for (;;) {
                wasteLand.add(new byte[128 * Util.KB]);
            }
        } catch (Throwable x) {
            // ignore
        }
        for (;;) {
            wasteLand.add(new byte[1]);
        }
    }

    public static synchronized void simulateOutOfMemory() {
        simulateOutOfMemory(waste);
    }
}
