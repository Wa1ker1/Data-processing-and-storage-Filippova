package ru.nsu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Список на основе синхронизированной обёртки ArrayList. */
public final class SynchronizedArrayList implements SortableList {
    private final List<String> values = Collections.synchronizedList(new ArrayList<>());
    /** Общий доступ для сортировщиков, исключительный — для ввода и снимков. */
    private final ReentrantReadWriteLock accessLock = new ReentrantReadWriteLock(true);

    @Override
    public void addFirst(String value) {
        Objects.requireNonNull(value);
        accessLock.writeLock().lock();
        try {
            values.add(0, value);
        } finally {
            accessLock.writeLock().unlock();
        }
    }

    @Override
    public boolean sortPass(long betweenDelayMillis, long insideDelayMillis, LongAdder steps)
            throws InterruptedException {
        boolean performed = false;
        for (int index = 0; ; index++) {
            StepDelay delay = new StepDelay(insideDelayMillis);
            while (true) {
                accessLock.readLock().lockInterruptibly();
                try {
                    if (!sortPart(index, delay, steps)) {
                        return performed;
                    }
                } finally {
                    accessLock.readLock().unlock();
                }
                if (delay.isComplete()) {
                    break;
                }
            }
            performed = true;
            Thread.sleep(betweenDelayMillis);
        }
    }

    private boolean sortPart(int index, StepDelay delay, LongAdder steps)
            throws InterruptedException {
        synchronized (values) {
            if (index + 1 >= values.size()) {
                return false;
            }
            String first = values.get(index);
            String second = values.get(index + 1);
            boolean swap = first.compareTo(second) > 0;
            if (delay.await(accessLock)) {
                if (swap) {
                    values.set(index, second);
                    values.set(index + 1, first);
                }
                steps.increment();
            }
            return true;
        }
    }

    /** Возвращает итератор согласованного снимка списка. */
    @Override
    public Iterator<String> iterator() {
        accessLock.writeLock().lock();
        try {
            synchronized (values) {
                return new ArrayList<>(values).iterator();
            }
        } finally {
            accessLock.writeLock().unlock();
        }
    }
}
