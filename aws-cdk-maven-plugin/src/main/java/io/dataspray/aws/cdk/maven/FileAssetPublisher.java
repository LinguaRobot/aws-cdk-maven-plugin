package io.dataspray.aws.cdk.maven;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Publishes file assets to S3.
 */
public class FileAssetPublisher {

    private static final Logger logger = LoggerFactory.getLogger(FileAssetPublisher.class);

    private final ResolvedEnvironment environment;

    private S3AsyncClient s3Client;

    public FileAssetPublisher(ResolvedEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Uploads a file or a directory (zipping it before uploading) to S3 bucket.
     *
     * @param file the file or directory to be uploaded
     * @param objectName the name of the object in the bucket
     * @param bucketName the name of the bucket
     * @throws IOException if I/O error occurs while uploading a file or directory
     */
    public void publish(Path file, String objectName, String bucketName) throws IOException {
        logger.info("Publishing file asset, file={}, bucketName={}, objectName={}", file, bucketName, objectName);
        if (Files.isDirectory(file)) {
            publishDirectory(file, objectName, bucketName);
        } else {
            publishFile(file, objectName, bucketName);
        }
    }

    /**
     * Zips the directory and uploads it to S3 bucket.
     */
    private void publishDirectory(Path directory, String objectName, String bucketName) throws IOException {
        try (
                OutputStream outputStream = new S3ObjectOutputStream(getS3Client(), bucketName, objectName, "application/zip");
                ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)
        ) {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    ZipEntry zipEntry = new ZipEntry(directory.relativize(file).toString());
                    zipOutputStream.putNextEntry(zipEntry);
                    Files.copy(file, zipOutputStream);
                    zipOutputStream.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * Uploads the file to S3 bucket.
     */
    private void publishFile(Path file, String objectName, String bucketName) throws IOException {
        try (OutputStream outputStream = new S3ObjectOutputStream(getS3Client(), bucketName, objectName)) {
            Files.copy(file, outputStream);
        }
    }

    private S3AsyncClient getS3Client() {
        if (this.s3Client == null) {
            this.s3Client = S3AsyncClient.builder()
                    .region(environment.getRegion())
                    .credentialsProvider(environment.getCredentialsProvider())
                    .build();
        }

        return s3Client;
    }

    private static class S3ObjectOutputStream extends OutputStream {

        private static final int MINIMUM_PART_SIZE = 5 * 1024 * 1024;

        private S3AsyncClient s3Client;
        private CreateMultipartUploadResponse createUploadResponse;
        private List<CompletableFuture<CompletedPart>> parts;
        private ByteBuffer buffer;

        S3ObjectOutputStream(S3AsyncClient s3Client, String bucketName, String objectKey) {
            this(s3Client, bucketName, objectKey, null);
        }

        S3ObjectOutputStream(S3AsyncClient s3Client, String bucketName, String objectKey, @Nullable String contentType) {
            this(s3Client, bucketName, objectKey, contentType, MINIMUM_PART_SIZE);
        }

        S3ObjectOutputStream(S3AsyncClient s3Client, String bucketName, String objectKey, String contentType, int partSize) {
            if (partSize <= 0) {
                throw new IllegalArgumentException("The minimum part size is 5 MB (" + MINIMUM_PART_SIZE + " bytes)");
            }
            this.s3Client = s3Client;
            this.buffer = ByteBuffer.allocate(partSize);
            this.parts = new ArrayList<>();
            CreateMultipartUploadRequest uploadRequest = buildUploadRequest(bucketName, objectKey, contentType);
            this.createUploadResponse = s3Client.createMultipartUpload(uploadRequest).join();
        }

        @Override
        public void write(int b) throws IOException {
            if (isClosed()) {
                throw new IOException("The stream is closed");
            }

            if (!buffer.hasRemaining()) {
                flush();
            }

            buffer.put((byte) b);
        }

        @Override
        public void write(@NotNull byte[] bytes, int offset, int length) throws IOException {
            if (isClosed()) {
                throw new IOException("The stream is closed");
            }
            int remaining = buffer.remaining();
            buffer.put(bytes, offset, Math.min(remaining, length));
            if (remaining < length) {
                flush();
                write(bytes, offset + remaining, length - remaining);
            }
        }

        @Override
        public void flush() {
            if (!isClosed()) {
                buffer.flip();
                if (buffer.remaining() > 0) {
                    CompletableFuture<CompletedPart> part = CompletableFuture.completedFuture(parts.size() + 1)
                            .thenApply(this::buildUploadPartRequest)
                            .thenCompose(uploadPartRequest -> {
                                AsyncRequestBody requestBody = AsyncRequestBody.fromByteBuffer(buffer);
                                return s3Client.uploadPart(uploadPartRequest, requestBody)
                                        .thenApply(r -> completedPart(r.eTag(), uploadPartRequest.partNumber()));
                            });
                    parts.add(part);
                }
                buffer.clear();
            }
        }

        @Override
        public void close() {
            if (!isClosed()) {
                flush();
                join(this.parts)
                        .thenCompose(completedParts -> {
                            CompleteMultipartUploadRequest completeUploadRequest = buildCompleteUploadRequest(completedParts);
                            return s3Client.completeMultipartUpload(completeUploadRequest);
                        })
                        .join();

                s3Client = null;
                this.createUploadResponse = null;
                buffer = null;
                this.parts = null;
            }

        }

        private <T> CompletableFuture<List<T>> join(List<CompletableFuture<T>> futures) {
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(r -> futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList()));
        }

        private boolean isClosed() {
            return buffer == null;
        }

        private CreateMultipartUploadRequest buildUploadRequest(String bucketName,
                                                                String objectKey,
                                                                @Nullable String contentType) {

            CreateMultipartUploadRequest.Builder requestBuilder = CreateMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey);
            if (contentType != null) {
                requestBuilder = requestBuilder.contentType(contentType);
            }

            return requestBuilder.build();
        }

        private UploadPartRequest buildUploadPartRequest(int partNumber) {
            return UploadPartRequest.builder()
                    .bucket(createUploadResponse.bucket())
                    .key(createUploadResponse.key())
                    .uploadId(createUploadResponse.uploadId())
                    .partNumber(partNumber)
                    .build();
        }

        private CompletedPart completedPart(String eTag, int partNumber) {
            return CompletedPart.builder()
                    .eTag(eTag)
                    .partNumber(partNumber)
                    .build();
        }

        private CompleteMultipartUploadRequest buildCompleteUploadRequest(Collection<CompletedPart> parts) {
            CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                    .parts(parts)
                    .build();

            return CompleteMultipartUploadRequest.builder()
                    .bucket(createUploadResponse.bucket())
                    .key(createUploadResponse.key())
                    .uploadId(createUploadResponse.uploadId())
                    .multipartUpload(completedMultipartUpload)
                    .build();
        }

    }
}
