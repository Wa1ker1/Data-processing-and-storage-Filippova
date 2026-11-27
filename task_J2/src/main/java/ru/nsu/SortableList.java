package ru.nsu;

import java.util.concurrent.atomic.LongAdder;

/** Список строк с одним проходом пузырьковой сортировки. */
public interface SortableList extends Iterable<String> {
    /** Добавляет строку в начало списка. */
    void addFirst(String value);

    /** Выполняет полный проход с задержками и подсчётом шагов. */
    boolean sortPass(long betweenDelayMillis, long insideDelayMillis, LongAdder steps)
            throws InterruptedException;
}
