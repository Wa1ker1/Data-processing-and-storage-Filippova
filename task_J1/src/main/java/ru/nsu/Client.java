package ru.nsu;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.US_ASCII;

/** TCP-клиент для получения ключа и сертификата в PEM-файлах. */
public final class Client {
    /** Отправляет запрос с опциями задержки чтения и отключения без ответа. */
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                    "Client <host> <port> <name> [--delay seconds] [--abort] [--out prefix]");
        }
        String name = args[2];
        if (name.length() > Server.MAX_NAME_BYTES || name.chars().anyMatch(c -> c == 0 || c > 127)) {
            throw new IllegalArgumentException("Name must be ASCII, at most 4096 bytes, without NUL");
        }
        long delay = 0;
        boolean abort = false;
        String output = "client";
        for (int i = 3; i < args.length; i++) {
            switch (args[i]) {
                case "--abort" -> abort = true;
                case "--delay", "--out" -> {
                    String option = args[i];
                    if (++i == args.length) throw new IllegalArgumentException("Missing value for " + option);
                    if (option.equals("--delay")) delay = Long.parseLong(args[i]);
                    else output = args[i];
                }
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (delay < 0) throw new IllegalArgumentException("Delay must be nonnegative");
        try (var socket = new Socket(args[0], Integer.parseInt(args[1]))) {
            socket.getOutputStream().write((name + '\0').getBytes(US_ASCII));
            if (abort) return;
            TimeUnit.SECONDS.sleep(delay);
            var input = new DataInputStream(socket.getInputStream());
            byte[] key = readBlock(input);
            byte[] certificate = readBlock(input);
            Keys.writePem(Path.of(output + ".key"), "PRIVATE KEY", key);
            Keys.writePem(Path.of(output + ".crt"), "CERTIFICATE", certificate);
            System.out.println("Saved " + output + ".key and " + output + ".crt");
        }
    }

    /** Читает блок с 32-битной длиной, проверяя размер и полноту данных. */
    private static byte[] readBlock(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > 1_000_000) throw new IOException("Invalid response length");
        byte[] data = input.readNBytes(length);
        if (data.length != length) throw new EOFException("Incomplete response");
        return data;
    }
}
