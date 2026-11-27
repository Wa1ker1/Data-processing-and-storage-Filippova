package ru.nsu;

import org.bouncycastle.asn1.x500.X500Name;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.nio.channels.SelectionKey.*;
import static java.nio.charset.StandardCharsets.US_ASCII;

/** Неблокирующий TCP-сервер генерации и кэширования ключей RSA. */
public final class Server {
    static final int MAX_NAME_BYTES = 4096;

    private final PrivateKey signingKey;
    private final X500Name issuer;
    private final Selector selector;
    private final ExecutorService workers;
    private final Map<String, CompletableFuture<byte[]>> cache = new HashMap<>();
    private final Queue<Runnable> ready = new ConcurrentLinkedQueue<>();

    /** Загружает ключ подписи и создаёт фиксированный пул генерации. */
    private Server(int threads, Path key, String issuerName) throws Exception {
        if (threads < 1) throw new IllegalArgumentException("threads must be positive");
        signingKey = Keys.readSigningKey(key);
        issuer = new X500Name(issuerName);
        selector = Selector.open();
        workers = Executors.newFixedThreadPool(threads);
    }

    /** Запускает сервер: порт, число потоков, файл ключа и имя издателя. */
    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("Server <port> <threads> <issuer.key> <issuer DN>");
        }
        int port = Integer.parseInt(args[0]);
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid port");
        new Server(Integer.parseInt(args[1]), Path.of(args[2]), args[3]).run(port);
    }

    /** Обслуживает соединения и очередь готовых ответов до прерывания потока. */
    private void run(int port) throws IOException {
        try (selector; var listener = ServerSocketChannel.open()) {
            listener.configureBlocking(false);
            listener.bind(new InetSocketAddress(port), 256);
            listener.register(selector, OP_ACCEPT);
            System.out.println("Listening on port " + ((InetSocketAddress) listener.getLocalAddress()).getPort());
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    selector.select(this::handle);
                    Runnable task;
                    while ((task = ready.poll()) != null) task.run();
                }
            } finally {
                for (var key : selector.keys()) close(key);
            }
        } finally {
            workers.shutdownNow();
        }
    }

    /** Принимает соединение, читает запрос или передаёт готовый ответ. */
    private void handle(SelectionKey key) {
        try {
            if (!key.isValid()) return;
            if (key.isAcceptable()) {
                var client = ((ServerSocketChannel) key.channel()).accept();
                if (client == null) return;
                try {
                    client.configureBlocking(false);
                    client.register(selector, OP_READ, ByteBuffer.allocate(MAX_NAME_BYTES + 1));
                } catch (IOException e) {
                    client.close();
                    throw e;
                }
            } else if (key.isReadable()) {
                readName(key);
            } else if (key.isWritable()) {
                var response = (ByteBuffer) key.attachment();
                ((SocketChannel) key.channel()).write(response);
                if (!response.hasRemaining()) close(key);
            }
        } catch (IOException | CancelledKeyException e) {
            if (key.channel() instanceof ServerSocketChannel) {
                System.err.println("Accept failed: " + e.getMessage());
            } else {
                close(key);
            }
        }
    }

    /** Накапливает ASCII-имя до нулевого байта, проверяя формат и длину. */
    private void readName(SelectionKey key) throws IOException {
        var input = (ByteBuffer) key.attachment();
        int start = input.position();
        if (((SocketChannel) key.channel()).read(input) < 0) {
            close(key);
            return;
        }
        for (int i = start; i < input.position(); i++) {
            byte value = input.get(i);
            if (value == 0) {
                request(key, new String(input.array(), 0, i, US_ASCII));
                return;
            }
            if (value < 0) throw new IOException("Name must be ASCII");
        }
        if (!input.hasRemaining()) throw new IOException("Name is too long");
    }

    /** Использует общую генерацию для одинаковых имён и ставит ответ в очередь. */
    private void request(SelectionKey key, String name) {
        key.interestOps(0);
        var result = cache.computeIfAbsent(name, n -> CompletableFuture.supplyAsync(() -> {
            try {
                return Keys.generate(n, issuer, signingKey);
            } catch (Exception e) {
                System.err.println("Generation failed: " + e.getMessage());
                throw new CompletionException(e);
            }
        }, workers));
        result.whenComplete((data, error) -> {
            ready.add(() -> {
                if (error != null) {
                    cache.remove(name, result);
                    close(key);
                } else if (key.isValid()) {
                    key.attach(ByteBuffer.wrap(data));
                    key.interestOps(OP_WRITE);
                }
            });
            selector.wakeup();
        });
    }

    /** Снимает канал с регистрации и закрывает соединение. */
    private static void close(SelectionKey key) {
        key.cancel();
        try {
            key.channel().close();
        } catch (IOException ignored) {
        }
    }
}
