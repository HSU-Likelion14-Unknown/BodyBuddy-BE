package com.centerton.bodybuddy.domain.meal.storage;

import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Component
public class LocalMealImageStorage implements MealImageStorage {

    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final Path root;

    public LocalMealImageStorage(
            @Value("${bodybuddy.image-storage.root:uploads}") String storageRoot
    ) {
        this.root = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Override
    public ValidatedMealImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorResponseCode.INVALID_INPUT_VALUE);
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BaseException(ErrorResponseCode.REQUEST_ENTITY_TOO_LARGE);
        }

        String mediaType = file.getContentType();
        String extension = EXTENSIONS.get(mediaType);
        if (extension == null) {
            throw new BaseException(ErrorResponseCode.INVALID_MEDIA_TYPE);
        }

        try {
            byte[] bytes = file.getBytes();
            if (!matchesSignature(bytes, mediaType)) {
                throw new BaseException(ErrorResponseCode.INVALID_MEDIA_TYPE);
            }
            return new ValidatedMealImage(bytes, mediaType, extension, sha256(bytes));
        } catch (IOException e) {
            throw new BaseException(ErrorResponseCode.SERVER_ERROR);
        }
    }

    @Override
    public String store(ValidatedMealImage image) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String objectKey = "%04d/%02d/%02d/%s.%s".formatted(
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                UUID.randomUUID(),
                image.extension()
        );
        Path target = safeResolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, image.bytes());
            return objectKey;
        } catch (IOException e) {
            throw new BaseException(ErrorResponseCode.SERVER_ERROR);
        }
    }

    @Override
    public StoredMealImage load(String objectKey) {
        Path target = safeResolve(objectKey);
        String extension = extensionOf(objectKey);
        String mediaType = EXTENSIONS.entrySet().stream()
                .filter(entry -> entry.getValue().equals(extension))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorResponseCode.INVALID_MEDIA_TYPE));
        try {
            return new StoredMealImage(Files.readAllBytes(target), mediaType);
        } catch (IOException e) {
            throw new BaseException(ErrorResponseCode.SERVER_ERROR);
        }
    }

    private Path safeResolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BaseException(ErrorResponseCode.SERVER_ERROR);
        }
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new BaseException(ErrorResponseCode.SERVER_ERROR);
        }
        return resolved;
    }

    private String extensionOf(String objectKey) {
        int separator = objectKey.lastIndexOf('.');
        return separator < 0 ? "" : objectKey.substring(separator + 1).toLowerCase();
    }

    private boolean matchesSignature(byte[] bytes, String mediaType) {
        return switch (mediaType) {
            case "image/jpeg" -> bytes.length >= 3
                    && unsigned(bytes[0]) == 0xff
                    && unsigned(bytes[1]) == 0xd8
                    && unsigned(bytes[2]) == 0xff;
            case "image/png" -> bytes.length >= 8
                    && unsigned(bytes[0]) == 0x89
                    && unsigned(bytes[1]) == 0x50
                    && unsigned(bytes[2]) == 0x4e
                    && unsigned(bytes[3]) == 0x47
                    && unsigned(bytes[4]) == 0x0d
                    && unsigned(bytes[5]) == 0x0a
                    && unsigned(bytes[6]) == 0x1a
                    && unsigned(bytes[7]) == 0x0a;
            case "image/webp" -> bytes.length >= 12
                    && ascii(bytes, 0, "RIFF")
                    && ascii(bytes, 8, "WEBP");
            default -> false;
        };
    }

    private boolean ascii(byte[] bytes, int offset, String expected) {
        for (int index = 0; index < expected.length(); index++) {
            if (bytes[offset + index] != (byte) expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }
}
