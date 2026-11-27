package ru.nsu;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Измеряет число шагов сортировки для двух реализаций списка. */
public final class Measurement {
    private static final int LIST_SIZE = 1000;

    private Measurement() {
    }

    /** Измеряет оба варианта на одинаковом списке без ввода и вывода. */
    public static void run(String[] args) throws InterruptedException {
        if (args.length > 4) {
            throw new IllegalArgumentException("Для замера нужно не более четырёх аргументов.");
        }
        int threadCount = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int durationSeconds = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        long betweenDelay = args.length > 2 ? Long.parseLong(args[2]) : 100;
        long insideDelay = args.length > 3 ? Long.parseLong(args[3]) : 100;
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("Время замера должно быть положительным.");
        }

        long linkedSteps = measure(new ConcurrentLinkedList(), threadCount,
                durationSeconds, betweenDelay, insideDelay);
        long arraySteps = measure(new SynchronizedArrayList(), threadCount,
                durationSeconds, betweenDelay, insideDelay);
        System.out.println("Связанный список: " + linkedSteps);
        System.out.println("ArrayList: " + arraySteps);
    }

    /** Считает завершённые шаги за заданный промежуток времени. */
    private static long measure(SortableList list, int threadCount,
                                int durationSeconds, long betweenDelay, long insideDelay)
            throws InterruptedException {
        SortingWorkers workers = new SortingWorkers(list, threadCount, betweenDelay, insideDelay);
        for (int index = LIST_SIZE - 1; index >= 0; index--) {
            list.addFirst(String.format(Locale.ROOT, "%04d", index));
        }
        try (workers) {
            workers.start();
            TimeUnit.SECONDS.sleep(durationSeconds);
        }
        return workers.getStepCount();
    }
}
