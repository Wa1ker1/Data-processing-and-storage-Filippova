package ru.nsu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Читает строки и выводит список при вводе пустой строки. */
public final class Main {
    private static final int MAX_LINE_LENGTH = 80;

    private Main() {
    }

    /** Запускает выбранный список или сравнение числа шагов. */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length > 0 && args[0].equals("measure")) {
            Measurement.run(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length > 4) {
            throw new IllegalArgumentException("Слишком много аргументов.");
        }
        String implementation = args.length > 0 ? args[0] : "linked";
        SortableList list = switch (implementation) {
            case "linked" -> new ConcurrentLinkedList();
            case "array" -> new SynchronizedArrayList();
            default -> throw new IllegalArgumentException("Неизвестный тип списка: " + implementation);
        };
        int threadCount = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        long betweenDelay = args.length > 2 ? Long.parseLong(args[2]) : 1000;
        long insideDelay = args.length > 3 ? Long.parseLong(args[3]) : 1000;
        try (SortingWorkers workers = new SortingWorkers(list, threadCount, betweenDelay, insideDelay);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            workers.start();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    for (String value : list) {
                        System.out.println(value);
                    }
                } else {
                    addLine(list, line);
                }
            }
        }
    }

    /** Разбивает длинную строку и добавляет части в начало. */
    private static void addLine(SortableList list, String line) {
        int remaining = line.codePointCount(0, line.length());
        int start = 0;
        while (remaining > 0) {
            int length = Math.min(MAX_LINE_LENGTH, remaining);
            int end = line.offsetByCodePoints(start, length);
            list.addFirst(line.substring(start, end));
            start = end;
            remaining -= length;
        }
    }
}
