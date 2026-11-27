package ru.nsu;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.LongAdder;

/** Управляет потоками бесконечной пузырьковой сортировки. */
public final class SortingWorkers implements AutoCloseable {
    private final Thread[] threads;
    private final LongAdder steps = new LongAdder();
    private final CountDownLatch startSignal = new CountDownLatch(1);

    /** Создаёт заданное число сортировщиков с общим счётчиком. */
    public SortingWorkers(SortableList list, int threadCount,
                          long betweenDelayMillis, long insideDelayMillis) {
        Objects.requireNonNull(list);
        if (threadCount <= 0) {
            throw new IllegalArgumentException("Число потоков должно быть положительным.");
        }
        if (betweenDelayMillis < 0) {
            throw new IllegalArgumentException("Задержка между шагами не может быть отрицательной.");
        }
        if (insideDelayMillis < 0) {
            throw new IllegalArgumentException("Задержка внутри шага не может быть отрицательной.");
        }
        threads = new Thread[threadCount];
        for (int index = 0; index < threadCount; index++) {
            threads[index] = new Thread(
                    () -> sort(list, betweenDelayMillis, insideDelayMillis),
                    "sorter-" + (index + 1));
        }
    }

    /** Запускает потоки и открывает общий сигнал старта. */
    public void start() {
        for (Thread thread : threads) {
            thread.start();
        }
        startSignal.countDown();
    }

    /** Возвращает суммарное число завершённых шагов. */
    public long getStepCount() {
        return steps.sum();
    }

    /** Повторяет полные проходы даже по отсортированному списку. */
    private void sort(SortableList list, long betweenDelayMillis, long insideDelayMillis) {
        try {
            startSignal.await();
            while (!Thread.currentThread().isInterrupted()) {
                if (!list.sortPass(betweenDelayMillis, insideDelayMillis, steps)) {
                    Thread.sleep(Math.max(1, betweenDelayMillis));
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** Прерывает сортировку и дожидается завершения всех потоков. */
    @Override
    public void close() {
        for (Thread thread : threads) {
            thread.interrupt();
        }
        boolean interrupted = false;
        for (Thread thread : threads) {
            while (thread.isAlive()) {
                try {
                    thread.join();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
