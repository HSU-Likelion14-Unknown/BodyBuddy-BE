package com.centerton.bodybuddy.domain.meal.storage;

import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalMealImageStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void validatesStoresAndLoadsPngImage() {
        LocalMealImageStorage storage = new LocalMealImageStorage(tempDir.toString());
        byte[] png = new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3
        };
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "meal.png",
                "image/png",
                png
        );

        ValidatedMealImage validated = storage.validate(file);
        String objectKey = storage.store(validated);
        StoredMealImage loaded = storage.load(objectKey);

        assertThat(objectKey).endsWith(".png");
        assertThat(loaded.mediaType()).isEqualTo("image/png");
        assertThat(loaded.bytes()).isEqualTo(png);
    }

    @Test
    void rejectsContentWhoseSignatureDoesNotMatchMediaType() {
        LocalMealImageStorage storage = new LocalMealImageStorage(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "fake.png",
                "image/png",
                "not-an-image".getBytes()
        );

        assertThatThrownBy(() -> storage.validate(file))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(ErrorResponseCode.INVALID_MEDIA_TYPE));
    }

    @Test
    void rejectsImageLargerThanTenMegabytes() {
        LocalMealImageStorage storage = new LocalMealImageStorage(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "large.jpg",
                "image/jpeg",
                new byte[(int) LocalMealImageStorage.MAX_IMAGE_BYTES + 1]
        );

        assertThatThrownBy(() -> storage.validate(file))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(ErrorResponseCode.REQUEST_ENTITY_TOO_LARGE));
    }
}
