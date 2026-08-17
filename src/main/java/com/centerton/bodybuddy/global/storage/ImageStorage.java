package com.centerton.bodybuddy.global.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
@Slf4j
public class ImageStorage {

    @Value("${bodybuddy.image-storage.root:uploads}")
    private String rootDir;

    public String store(MultipartFile file) {
        try {
            String extension = extractExtension(file.getOriginalFilename());
            String fileName = UUID.randomUUID() + extension;

            Path directory = Path.of(rootDir);
            Files.createDirectories(directory);

            Path destination = directory.resolve(fileName);
            file.transferTo(destination);

            return "/" + rootDir + "/" + fileName;
        } catch (IOException e) {
            log.error("이미지 저장 실패", e);
            throw new IllegalStateException("이미지 저장에 실패했습니다.", e);
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }
}