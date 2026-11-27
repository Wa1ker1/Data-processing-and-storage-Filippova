package ru.nsu;

import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/** Генерация ключей RSA, выпуск сертификатов и работа с PEM. */
final class Keys {
    private Keys() {}

    /** Загружает незашифрованный приватный ключ RSA из PKCS#8 PEM. */
    static PrivateKey readSigningKey(Path path) throws Exception {
        try (var reader = new PemReader(Files.newBufferedReader(path))) {
            var pem = reader.readPemObject();
            if (pem == null || !pem.getType().equals("PRIVATE KEY")) {
                throw new IOException("Expected an unencrypted PKCS#8 PEM private key");
            }
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(pem.getContent()));
        }
    }

    /** Создаёт RSA-8192 и сертификат, упаковывая DER-блоки с их длинами. */
    static byte[] generate(String name, X500Name issuer, PrivateKey signingKey) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(8192);
        var pair = generator.generateKeyPair();
        var subject = new X500NameBuilder()
                .addRDN(BCStyle.CN, new DERUTF8String(name)).build();
        var now = Instant.now();
        var certificate = new JcaX509v3CertificateBuilder(
                issuer, new BigInteger(159, new SecureRandom()).add(BigInteger.ONE),
                Date.from(now.minusSeconds(60)), Date.from(now.plus(365, ChronoUnit.DAYS)),
                subject, pair.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(signingKey))
                .getEncoded();
        var key = pair.getPrivate().getEncoded();
        return ByteBuffer.allocate(8 + key.length + certificate.length)
                .putInt(key.length).put(key).putInt(certificate.length).put(certificate).array();
    }

    /** Сохраняет данные в PEM-файл указанного типа. */
    static void writePem(Path path, String type, byte[] data) throws IOException {
        try (var writer = new PemWriter(Files.newBufferedWriter(path))) {
            writer.writeObject(new PemObject(type, data));
        }
    }
}
