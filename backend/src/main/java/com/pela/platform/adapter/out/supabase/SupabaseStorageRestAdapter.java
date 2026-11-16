package com.pela.platform.adapter.out.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.pela.shared.application.port.out.StoragePort;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

/**
 * Supabase Storage qua REST ({@code /storage/v1}) bằng service role key — key chỉ nằm ở backend (AGENTS mục 7).
 * Không log nội dung file hay URL ký.
 */
public class SupabaseStorageRestAdapter implements StoragePort {

    private final RestClient client;
    private final String storageBaseUrl;
    private final String serviceRoleKey;

    public SupabaseStorageRestAdapter(RestClient client, String supabaseUrl, String serviceRoleKey) {
        this.client = client;
        this.storageBaseUrl = supabaseUrl + "/storage/v1";
        this.serviceRoleKey = serviceRoleKey;
    }

    @Override
    public void upload(String bucket, String path, byte[] content, String contentType) {
        call("upload", () -> client.post().uri(objectUri(bucket, path))
                .headers(this::auth)
                .header("x-upsert", "true")
                .contentType(MediaType.parseMediaType(contentType))
                .body(content)
                .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw failure("upload", res.getStatusCode());
                })
                .toBodilessEntity());
    }

    @Override
    public byte[] download(String bucket, String path) {
        return call("download", () -> client.get().uri(objectUri(bucket, path))
                .headers(this::auth)
                .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw failure("download", res.getStatusCode());
                })
                .body(byte[].class));
    }

    @Override
    public void copy(String fromBucket, String fromPath, String toBucket, String toPath) {
        // Storage API không ghi đè khi copy: xoá đích trước (không có thì bỏ qua)
        delete(toBucket, toPath);
        call("copy", () -> client.post().uri(URI.create(storageBaseUrl + "/object/copy"))
                .headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("bucketId", fromBucket, "sourceKey", fromPath, "destinationBucket", toBucket,
                        "destinationKey", toPath))
                .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw failure("copy", res.getStatusCode());
                })
                .toBodilessEntity());
    }

    @Override
    public void delete(String bucket, String path) {
        call("delete", () -> client.method(org.springframework.http.HttpMethod.DELETE)
                .uri(URI.create(storageBaseUrl + "/object/" + encodeSegment(bucket)))
                .headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("prefixes", new String[] {path}))
                .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw failure("delete", res.getStatusCode());
                })
                .toBodilessEntity());
    }

    @Override
    public String signedUrl(String bucket, String path, Duration ttl) {
        JsonNode body = call("sign", () -> client.post()
                .uri(URI.create(storageBaseUrl + "/object/sign/" + encodeSegment(bucket) + "/" + encodePath(path)))
                .headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expiresIn", Math.max(1, ttl.toSeconds())))
                .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw failure("sign", res.getStatusCode());
                })
                .body(JsonNode.class));
        String signed = body == null ? "" : body.path("signedURL").asText("");
        if (signed.isEmpty()) {
            throw new StorageException("Storage sign returned no URL", null);
        }
        // signedURL là đường dẫn tương đối dạng /object/sign/<bucket>/<path>?token=...
        return signed.startsWith("http") ? signed : storageBaseUrl + (signed.startsWith("/") ? "" : "/") + signed;
    }

    private URI objectUri(String bucket, String path) {
        return URI.create(storageBaseUrl + "/object/" + encodeSegment(bucket) + "/" + encodePath(path));
    }

    /** Giống Admin API của Auth: secret key mới gửi ở apikey; service_role JWT kiểu cũ gửi thêm Bearer. */
    private void auth(HttpHeaders headers) {
        if (serviceRoleKey.isEmpty()) {
            throw new StorageException("SUPABASE_SERVICE_ROLE_KEY is not configured", null);
        }
        headers.set("apikey", serviceRoleKey);
        if (serviceRoleKey.startsWith("eyJ")) {
            headers.setBearerAuth(serviceRoleKey);
        }
    }

    private static <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (StorageException e) {
            throw e;
        } catch (RestClientException e) {
            throw new StorageException("Storage " + operation + " failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private static StorageException failure(String operation, HttpStatusCode status) {
        return new StorageException("Storage " + operation + " failed with HTTP " + status.value(), null);
    }

    private static String encodePath(String path) {
        return Arrays.stream(path.split("/")).map(SupabaseStorageRestAdapter::encodeSegment)
                .collect(Collectors.joining("/"));
    }

    private static String encodeSegment(String segment) {
        return UriUtils.encodePathSegment(segment, java.nio.charset.StandardCharsets.UTF_8);
    }
}
