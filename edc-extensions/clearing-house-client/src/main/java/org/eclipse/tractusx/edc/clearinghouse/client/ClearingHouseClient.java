package org.eclipse.tractusx.edc.clearinghouse.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.tractusx.edc.clearinghouse.config.ClearingHouseConfig;
import org.eclipse.tractusx.edc.spi.clearinghouse.model.LogReceipt;
import org.eclipse.tractusx.edc.spi.clearinghouse.model.ParticipantStatus;
import org.eclipse.tractusx.edc.spi.clearinghouse.model.TransactionEvent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import static org.eclipse.edc.spi.result.Result.failure;
import static org.eclipse.edc.spi.result.Result.success;

/**
 * Client for interacting with the Clearing House API.
 * todo: change constants/paths to config values if needed
 */
public class ClearingHouseClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // todo: confirm actual CHN paths; keep as config later if needed
    private static final String EVENTS_LOG_PATH = "/api/v1/events/log";
    private static final String RECEIPTS_VERIFY_PATH = "/api/v1/receipts/verify";
    private static final String PARTICIPANTS_VALIDATE_PATH = "/api/v1/participants/validate";
    private static final String TRUST_ANCHOR_PATH = "/api/v1/trust-anchor/certificate";


    private final EdcHttpClient httpClient;
    private final ClearingHouseConfig config;
    private final ObjectMapper mapper;
    private final Monitor monitor;
    private final Executor executor;

    public ClearingHouseClient(EdcHttpClient httpClient,
                               ClearingHouseConfig config,
                               ObjectMapper mapper,
                               Monitor monitor,
                               Executor executor) {
        this.httpClient = httpClient;
        this.config = config;
        this.mapper = mapper;
        this.monitor = monitor;
        this.executor = executor;
    }

    public CompletionStage<Result<LogReceipt>> logEvent(TransactionEvent event) {
        return CompletableFuture.supplyAsync(() -> doLogEvent(event), executor);
    }

    public CompletionStage<Result<Boolean>> verifyReceipt(LogReceipt receipt) {
        return CompletableFuture.supplyAsync(() -> doVerifyReceipt(receipt), executor);
    }

    public CompletionStage<Result<ParticipantStatus>> validateParticipant(String bpn, String did) {
        return CompletableFuture.supplyAsync(() -> doValidateParticipant(bpn, did), executor);
    }

    public CompletionStage<Result<X509Certificate>> fetchTrustAnchor() {
        return CompletableFuture.supplyAsync(this::doFetchTrustAnchor, executor);
    }

    private Result<LogReceipt> doLogEvent(TransactionEvent event) {
        if (event == null) return failure("event is null");

        var url = config.getBaseUrl() + EVENTS_LOG_PATH;

        try {
            var bodyJson = mapper.writeValueAsString(event);

            var request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(bodyJson, JSON))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("CHN logEvent failed: HTTP " + response.code());
                }

                if (response.body() == null) return failure("CHN logEvent response body is empty");

                var receiptJson = response.body().string();
                var receipt = mapper.readValue(receiptJson, LogReceipt.class);
                return success(receipt);
            }
        } catch (Exception e) {
            monitor.severe("[ClearingHouseClient] logEvent failed: " + e.getMessage(), e);
            return failure("logEvent failed: " + e.getMessage());
        }
    }

    private Result<Boolean> doVerifyReceipt(LogReceipt receipt) {
        if (receipt == null) return failure("receipt is null");
        var url = config.getBaseUrl() + RECEIPTS_VERIFY_PATH;

        try {
            var bodyJson = mapper.writeValueAsString(receipt);

            var req = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(bodyJson, JSON))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .build();
            try (Response response = httpClient.execute(req)) {
                if (!response.isSuccessful()) return failure("CHN verifyReceipt failed: HTTP " + response.code());
                if (response.body() == null) return success(false);

                var json = response.body().string();
                var tree = mapper.readTree(json);
                var valid = tree.has("valid") && tree.get("valid").asBoolean();
                return success(valid);
            }
        } catch (Exception e) {
            monitor.severe("[ClearingHouseClient] verifyReceipt failed: " + e.getMessage(), e);
            return failure("verifyReceipt failed: " + e.getMessage());
        }
    }

    private Result<ParticipantStatus> doValidateParticipant(String bpn, String did) {
        if (bpn == null || bpn.isBlank()) return failure("bpn is blank");
        var url = config.getBaseUrl() + PARTICIPANTS_VALIDATE_PATH;

        try {
            var payload = new HashMap<String, Object>();
            payload.put("bpn", bpn);

            if (did != null && !did.isBlank()) payload.put("did", did);

            var bodyJson = mapper.writeValueAsString(payload);
            var req = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(bodyJson, JSON))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .build();

            try (Response response = httpClient.execute(req)) {
                if (!response.isSuccessful()) return failure("CHN validateParticipant failed: HTTP " + response.code());
                if (response.body() == null) return failure("CHN validateParticipant response body is empty");

                var json = response.body().string();
                var status = mapper.readValue(json, ParticipantStatus.class);
                return success(status);
            }
        } catch (Exception e) {
            monitor.severe("[ClearingHouseClient] validateParticipant failed: " + e.getMessage(), e);
            return failure("validateParticipant failed: " + e.getMessage());
        }
    }

    private Result<X509Certificate> doFetchTrustAnchor(){
        var url = config.getBaseUrl() + TRUST_ANCHOR_PATH;

        var request = new Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer " + config.getApiKey())
                .build();

        try (Response response = httpClient.execute(request)) {
            if (!response.isSuccessful()) return failure("CHN fetchTrustAnchor failed: HTTP " + response.code());
            if (response.body() == null) return failure("CHN trust anchor response body is empty");

            var pem = response.body().string();
            var cleaned = pem.replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s+", "");

            var decoded = Base64.getDecoder().decode(cleaned);

            var cf = CertificateFactory.getInstance("X.509");
            var cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(decoded));
            return success(cert);

        }  catch (IOException | CertificateException | IllegalArgumentException e) {
            monitor.severe("[ClearingHouseClient] fetchTrustAnchor failed: " + e.getMessage(), e);
            return failure("fetchTrustAnchor failed: " + e.getMessage());
        }
    }

}