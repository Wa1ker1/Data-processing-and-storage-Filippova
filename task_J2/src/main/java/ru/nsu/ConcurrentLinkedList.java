package ru.nsu;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Связанный список с блокировками отдельных узлов. */
public final class ConcurrentLinkedList implements SortableList {
    private final Node head = new Node(null, null);
    /** Общий доступ для сортировщиков, исключительный — для ввода и снимков. */
    private final ReentrantReadWriteLock accessLock = new ReentrantReadWriteLock(true);

    @Override
    public void addFirst(String value) {
        Objects.requireNonNull(value);
        accessLock.writeLock().lock();
        try {
            head.next = new Node(value, head.next);
        } finally {
            accessLock.writeLock().unlock();
        }
    }

    @Override
    public boolean sortPass(long betweenDelayMillis, long insideDelayMillis, LongAdder steps)
            throws InterruptedException {
        Node previous = head;
        boolean performed = false;
        while (true) {
            Node nextPrevious = sortStep(previous, insideDelayMillis, steps);
            if (nextPrevious == null) {
                return performed;
            }
            performed = true;
            previous = nextPrevious;
            Thread.sleep(betweenDelayMillis);
        }
    }

    /** Выполняет шаг, предоставляя доступ вводу между частями внутренней задержки. */
    private Node sortStep(Node previous, long insideDelayMillis, LongAdder steps)
            throws InterruptedException {
        StepDelay delay = new StepDelay(insideDelayMillis);
        while (true) {
            accessLock.readLock().lockInterruptibly();
            try {
                Node nextPrevious = sortPart(previous, delay, steps);
                if (delay.isComplete() || nextPrevious == null) {
                    return nextPrevious;
                }
            } finally {
                accessLock.readLock().unlock();
            }
        }
    }

    /** Захватывает узлы от головы к хвосту и завершает перестановку в последней части. */
    private Node sortPart(Node previous, StepDelay delay, LongAdder steps)
            throws InterruptedException {
        previous.lock.lockInterruptibly();
        try {
            Node first = previous.next;
            if (first == null) {
                return null;
            }
            first.lock.lockInterruptibly();
            try {
                Node second = first.next;
                if (second == null) {
                    return null;
                }
                second.lock.lockInterruptibly();
                try {
                    boolean swap = first.value.compareTo(second.value) > 0;
                    if (!delay.await(accessLock)) {
                        return previous;
                    }
                    if (swap) {
                        first.next = second.next;
                        second.next = first;
                        previous.next = second;
                    }
                    steps.increment();
                    return swap ? second : first;
                } finally {
                    second.lock.unlock();
                }
            } finally {
                first.lock.unlock();
            }
        } finally {
            previous.lock.unlock();
        }
    }

    /** Создаёт снимок между частями сортировки, временно приостанавливая изменения. */
    @Override
    public Iterator<String> iterator() {
        accessLock.writeLock().lock();
        try {
            List<String> snapshot = new ArrayList<>();
            for (Node current = head.next; current != null; current = current.next) {
                snapshot.add(current.value);
            }
            return snapshot.iterator();
        } finally {
            accessLock.writeLock().unlock();
        }
    }

    /** Узел с неизменяемой строкой и собственной блокировкой. */
    private static final class Node {
        private final String value;
        private final ReentrantLock lock = new ReentrantLock();
        private Node next;

        private Node(String value, Node next) {
            this.value = value;
            this.next = next;
        }
    }
}
