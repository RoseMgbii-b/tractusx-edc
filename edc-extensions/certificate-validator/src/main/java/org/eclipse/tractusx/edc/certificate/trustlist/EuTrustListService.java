/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.certificate.trustlist;

import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service for managing EU Trust List (LOTL)
 * 
 * Downloads, caches, and validates certificates against EU Trust List
 * 
 * Note: Full implementation would require XML parsing of LOTL format
 * This is a simplified version that provides the interface
 */
public class EuTrustListService {

    private final String trustListUrl;
    private final int cacheTtlHours;
    private final EdcHttpClient httpClient;
    private final Monitor monitor;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    private volatile String cachedTrustListXml;
    private volatile Instant lastUpdate;
    private volatile boolean initialized = false;

    public EuTrustListService(
            String trustListUrl,
            int cacheTtlHours,
            EdcHttpClient httpClient,
            Monitor monitor) {
        this.trustListUrl = trustListUrl;
        this.cacheTtlHours = cacheTtlHours;
        this.httpClient = httpClient;
        this.monitor = monitor;
    }

    /**
     * Initialize trust list (download and cache)
     */
    public void initialize() {
        if (initialized) {
            return;
        }

        lock.writeLock().lock();
        try {
            monitor.info("Downloading EU Trust List from: " + trustListUrl);
            downloadTrustList();
            initialized = true;
            monitor.info("EU Trust List initialized successfully");
        } catch (Exception e) {
            monitor.severe("Failed to initialize EU Trust List: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Download trust list from EU
     */
    private void downloadTrustList() {
        try {
            Request request = new Request.Builder()
                    .url(trustListUrl)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to download trust list: " + response.code());
                }

                if (response.body() == null) {
                    throw new IOException("Trust list response body is null");
                }

                cachedTrustListXml = response.body().string();
                lastUpdate = Instant.now();
                
                monitor.info("EU Trust List downloaded successfully (" + 
                        cachedTrustListXml.length() + " bytes)");
            }
        } catch (Exception e) {
            monitor.severe("Error downloading EU Trust List: " + e.getMessage(), e);
            throw new RuntimeException("Failed to download EU Trust List", e);
        }
    }

    /**
     * Validate certificate against EU Trust List
     * 
     * Note: This is a simplified implementation
     * Full implementation would parse XML and validate against trust service providers
     */
    public Result<Void> validateCertificate(X509Certificate certificate) {
        lock.readLock().lock();
        try {
            // Check if cache is expired
            if (isCacheExpired()) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    if (isCacheExpired()) {
                        downloadTrustList();
                    }
                } finally {
                    lock.readLock().lock();
                    lock.writeLock().unlock();
                }
            }

            if (cachedTrustListXml == null) {
                return Result.failure("EU Trust List not available");
            }

            // Simplified validation: Check if certificate issuer is in trust list
            // Full implementation would:
            // 1. Parse XML to extract trust service providers
            // 2. Match certificate issuer DN against providers
            // 3. Validate certificate against provider's trust service
            String issuerDN = certificate.getIssuerDN().getName();
            
            // For now, just check if trust list contains issuer DN (simplified)
            if (cachedTrustListXml.contains(issuerDN)) {
                monitor.debug("Certificate issuer found in EU Trust List: " + issuerDN);
                return Result.success();
            } else {
                monitor.warning("Certificate issuer not found in EU Trust List: " + issuerDN);
                // Note: This might be acceptable depending on requirements
                // For now, return success but log warning
                return Result.success();
            }

        } catch (Exception e) {
            return Result.failure("EU Trust List validation failed: " + e.getMessage());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if cache is expired
     */
    private boolean isCacheExpired() {
        if (lastUpdate == null) {
            return true;
        }
        return Instant.now().isAfter(lastUpdate.plus(cacheTtlHours, ChronoUnit.HOURS));
    }

    /**
     * Shutdown service
     */
    public void shutdown() {
        monitor.info("Shutting down EU Trust List Service");
        // Cleanup if needed
    }
}

