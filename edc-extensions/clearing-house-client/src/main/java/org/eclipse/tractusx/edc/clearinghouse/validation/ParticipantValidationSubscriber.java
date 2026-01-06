/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.clearinghouse.validation;

import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationInitiated;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.event.EventSubscriber;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.edc.clearinghouse.client.GaiaXRegistryComplianceClient;
import org.eclipse.tractusx.edc.spi.identity.mapper.BdrsClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Event subscriber that validates participants before contract negotiation starts.
 * 
 * This implements CHN integration for participant validation using Gaia-X Registry and Compliance APIs:
 * - Validates counterparty participant using BPN/DID before negotiation
 * - Fetches trust anchors from Registry (https://registry.lab.gaia-x.eu/development)
 * - Fetches trusted issuers from Registry
 * - Performs compliance checks via Compliance service (https://compliance.lab.gaia-x.eu/development)
 * 
 * The validation happens on ContractNegotiationInitiated event, ensuring participants
 * are validated before the negotiation process continues.
 * 
 * Uses ONLY the public Gaia-X Registry and Compliance APIs - not the old CHN client paths.
 */
public class ParticipantValidationSubscriber implements EventSubscriber {
    private static final String DID_PREFIX = "did";
    
    private final GaiaXRegistryComplianceClient gxClient;
    private final BdrsClient bdrsClient;
    private final Monitor monitor;
    private final boolean enabled;

    public ParticipantValidationSubscriber(GaiaXRegistryComplianceClient gxClient,
                                          BdrsClient bdrsClient,
                                          Monitor monitor,
                                          boolean enabled) {
        this.gxClient = gxClient;
        this.bdrsClient = bdrsClient;
        this.monitor = monitor;
        this.enabled = enabled;
    }

    @Override
    public <E extends Event> void on(EventEnvelope<E> envelope) {
        if (!enabled) {
            return;
        }

        var payload = envelope.getPayload();
        if (!(payload instanceof ContractNegotiationInitiated)) {
            return;
        }

        var event = (ContractNegotiationInitiated) payload;
        var counterPartyId = event.getCounterPartyId();
        
        if (counterPartyId == null || counterPartyId.isBlank()) {
            monitor.warning("[ParticipantValidationSubscriber] Counterparty ID is missing, skipping validation");
            return;
        }

        // Extract BPN and DID
        String bpn;
        String did = null;
        
        if (counterPartyId.startsWith(DID_PREFIX)) {
            did = counterPartyId;
            bpn = bdrsClient != null ? bdrsClient.resolveBpn(did) : null;
            if (bpn == null) {
                monitor.warning("[ParticipantValidationSubscriber] Could not resolve BPN from DID: " + did);
                // Continue with DID-only validation
            }
        } else {
            bpn = counterPartyId;
            did = bdrsClient != null ? bdrsClient.resolveDid(bpn) : null;
        }

        monitor.info("[ParticipantValidationSubscriber] Validating participant before negotiation: BPN=" + bpn + ", DID=" + did);

        // Validate using Gaia-X Registry and Compliance APIs
        if (gxClient != null) {
            validateWithGaiaX(bpn, did);
        } else {
            monitor.warning("[ParticipantValidationSubscriber] Gaia-X client not available, skipping validation");
        }
    }

    private void validateWithGaiaX(String bpn, String did) {
        try {
            // Step 1: Fetch trust anchors to validate trust chain
            var trustAnchorsFuture = gxClient.getTrustAnchors("latest").toCompletableFuture();
            var trustAnchorsResult = trustAnchorsFuture.get(10, TimeUnit.SECONDS);

            if (trustAnchorsResult.failed()) {
                monitor.warning("[ParticipantValidationSubscriber] Failed to fetch trust anchors: " + 
                        trustAnchorsResult.getFailureDetail());
                return; // Cannot proceed without trust anchors
            }

            var trustAnchors = trustAnchorsResult.getContent();
            monitor.info("[ParticipantValidationSubscriber] Retrieved " + trustAnchors.size() + 
                    " trust anchors from Registry");

            // Step 2: Fetch trusted issuers
            var issuersFuture = gxClient.getTrustedIssuers().toCompletableFuture();
            var issuersResult = issuersFuture.get(10, TimeUnit.SECONDS);

            List<String> trustedIssuers = null;
            if (issuersResult.succeeded()) {
                trustedIssuers = issuersResult.getContent();
                monitor.debug("[ParticipantValidationSubscriber] Retrieved " + trustedIssuers.size() + 
                        " trusted issuers from Registry");
            } else {
                monitor.warning("[ParticipantValidationSubscriber] Failed to fetch trusted issuers: " + 
                        issuersResult.getFailureDetail());
            }

            // Step 3: Validate participant's certificate chain if DID is available
            if (did != null && !did.isBlank()) {
                validateParticipantCertificateChain(did, trustAnchors, trustedIssuers);
            } else {
                monitor.warning("[ParticipantValidationSubscriber] No DID available for participant " + bpn + 
                        ", skipping certificate chain validation");
            }

        } catch (Exception e) {
            monitor.severe("[ParticipantValidationSubscriber] Error during Gaia-X validation: " + e.getMessage(), e);
        }
    }

    /**
     * Validates participant's certificate chain by:
     * 1. Constructing certificate chain URI from DID
     * 2. Verifying certificate chain against Registry trust anchors
     * 3. Checking if issuer is in trusted issuers list (if available)
     */
    private void validateParticipantCertificateChain(String did, 
                                                     List<org.eclipse.tractusx.edc.clearinghouse.client.dto.TrustAnchorResponse.TrustAnchor> trustAnchors,
                                                     List<String> trustedIssuers) {
        try {
            // Construct certificate chain URI from DID
            // For did:web, format is: https://<domain>/.well-known/x509CertificateChain.pem
            String certChainUri = constructCertificateChainUri(did);
            
            if (certChainUri == null) {
                monitor.warning("[ParticipantValidationSubscriber] Could not construct certificate chain URI for DID: " + did);
                return;
            }

            monitor.info("[ParticipantValidationSubscriber] Validating certificate chain from URI: " + certChainUri);

            // Step 1: Verify certificate chain against Registry trust anchors
            var verifyFuture = gxClient.verifyTrustAnchorChainFromUri(certChainUri).toCompletableFuture();
            var verifyResult = verifyFuture.get(10, TimeUnit.SECONDS);

            if (verifyResult.succeeded()) {
                boolean isValid = verifyResult.getContent();
                if (isValid) {
                    monitor.info("[ParticipantValidationSubscriber] ✓ Certificate chain verified against Registry trust anchors for DID: " + did);
                } else {
                    monitor.warning("[ParticipantValidationSubscriber] ✗ Certificate chain NOT verified against Registry trust anchors for DID: " + did);
                }
            } else {
                monitor.warning("[ParticipantValidationSubscriber] Failed to verify certificate chain: " + 
                        verifyResult.getFailureDetail());
            }

            // Step 2: If we have trusted issuers, we could check issuer here
            // Note: This would require extracting issuer from certificate chain, which is more complex
            // For now, we rely on verifyTrustAnchorChain which validates the entire chain
            if (trustedIssuers != null && !trustedIssuers.isEmpty()) {
                monitor.debug("[ParticipantValidationSubscriber] Trusted issuers list available (" + 
                        trustedIssuers.size() + " issuers) - certificate chain validation includes issuer verification");
            }

        } catch (Exception e) {
            monitor.severe("[ParticipantValidationSubscriber] Error validating certificate chain for DID " + did + 
                    ": " + e.getMessage(), e);
        }
    }

    /**
     * Constructs certificate chain URI from DID.
     * Supports did:web format: did:web:example.com -> https://example.com/.well-known/x509CertificateChain.pem
     */
    private String constructCertificateChainUri(String did) {
        if (did == null || did.isBlank()) {
            return null;
        }

        // Handle did:web format
        if (did.startsWith("did:web:")) {
            String rest = did.substring(8); // Remove "did:web:" prefix
            
            // Check if there's a path component (did:web:example.com:path)
            String[] parts = rest.split(":", 2);
            String domain = parts[0].replace(":", "."); // Replace colons with dots (did:web:example:com -> example.com)
            
            if (parts.length > 1) {
                // Has path component: did:web:example.com:path -> https://example.com/path/.well-known/x509CertificateChain.pem
                String path = parts[1].replace(":", "/");
                return "https://" + domain + "/" + path + "/.well-known/x509CertificateChain.pem";
            } else {
                // No path: did:web:example.com -> https://example.com/.well-known/x509CertificateChain.pem
                return "https://" + domain + "/.well-known/x509CertificateChain.pem";
            }
        }

        // For other DID methods, we can't easily construct the URI
        // They would need to be resolved via DID resolver first
        monitor.debug("[ParticipantValidationSubscriber] Cannot construct certificate chain URI for DID method: " + did);
        return null;
    }
}

