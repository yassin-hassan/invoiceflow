package com.example.invoiceflow.storage;

import com.example.invoiceflow.exception.InvalidFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock private S3Client s3Client;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new StorageService(s3Client, "my-bucket", "eu-west-3");
    }

    @Test
    void uploadLogo_validJpeg_returnsS3Url() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "image/jpeg", new byte[100]);

        String url = storageService.uploadLogo(userId, file);

        assertThat(url).isEqualTo("https://my-bucket.s3.eu-west-3.amazonaws.com/logos/" + userId + ".jpg");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadLogo_validPng_returnsPngUrl() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[100]);

        String url = storageService.uploadLogo(userId, file);

        assertThat(url).endsWith(".png");
    }

    @Test
    void uploadLogo_emptyFile_throwsInvalidFileException() {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> storageService.uploadLogo(userId, file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void uploadLogo_unsupportedType_throwsInvalidFileException() {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "logo.gif", "image/gif", new byte[100]);

        assertThatThrownBy(() -> storageService.uploadLogo(userId, file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("JPEG and PNG");
    }

    @Test
    void uploadLogo_fileTooLarge_throwsInvalidFileException() {
        UUID userId = UUID.randomUUID();
        byte[] bigFile = new byte[3 * 1024 * 1024]; // 3MB
        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "image/jpeg", bigFile);

        assertThatThrownBy(() -> storageService.uploadLogo(userId, file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("2MB");
    }
}
