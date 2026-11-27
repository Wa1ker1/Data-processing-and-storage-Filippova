package ru.nsu;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Сохраняет остаток задержки при временном предоставлении доступа вводу. */
final class StepDelay {
    private static final long PART_MILLIS = 10;

    private long remainingMillis;

    StepDelay(long delayMillis) {
        remainingMillis = delayMillis;
    }

    boolean await(ReentrantReadWriteLock accessLock) throws InterruptedException {
        do {
            long part = Math.min(remainingMillis, PART_MILLIS);
            Thread.sleep(part);
            remainingMillis -= part;
        } while (remainingMillis > 0 && !accessLock.hasQueuedThreads());
        return isComplete();
    }

    boolean isComplete() {
        return remainingMillis == 0;
    }
}
