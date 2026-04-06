package com.example.invoiceflow.storage;

import com.example.invoiceflow.exception.InvalidFileException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
public class StorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_SIZE_BYTES = 2 * 1024 * 1024; // 2MB

    private final S3Client s3Client;
    private final String bucket;
    private final String region;

    public StorageService(S3Client s3Client,
                          @Value("${app.storage.bucket}") String bucket,
                          @Value("${app.storage.region}") String region) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.region = region;
    }

    public String uploadLogo(UUID userId, MultipartFile file) {
        validateFile(file);

        String extension = getExtension(file.getContentType());
        String key = "logos/" + userId + "." + extension;

        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build(),
                RequestBody.fromBytes(file.getBytes())
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }

        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    public void deleteLogo(UUID userId, String contentType) {
        String extension = getExtension(contentType);
        String key = "logos/" + userId + "." + extension;
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidFileException("File must not be empty");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new InvalidFileException("Only JPEG and PNG images are allowed");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new InvalidFileException("File size must not exceed 2MB");
        }
    }

    private String getExtension(String contentType) {
        return "image/png".equals(contentType) ? "png" : "jpg";
    }
}
