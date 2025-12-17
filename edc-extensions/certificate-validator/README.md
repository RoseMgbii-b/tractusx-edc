# Certificate Validator Extension

## Overview

The Certificate Validator Extension provides comprehensive certificate validation capabilities for eIDAS and Clearing House certificates. It serves as the foundation for all certificate-related operations in the trust chain.

## Features

### eIDAS Certificate Validation
- Validates QCStatement extension (eIDAS compliance)
- Validates certificate types (QCP, QCP-l, QCP-n, QCP-n-l)
- Validates certificate profile compliance
- Validates certificate chains against EU Trust List

### Clearing House Certificate Validation
- Validates Clearing House as trust anchor
- Validates certificate chains to CH root
- Validates certificate revocation (CRL support)

### EU Trust List Integration
- Downloads EU Trust List (LOTL)
- Parses and validates trust list XML
- Provides trust service provider lookup
- Caches trust list for performance

### Generic Certificate Validation
- Validates certificate chains
- Validates certificate paths
- Checks certificate expiration and validity


## Dependencies

- BouncyCastle (certificate parsing and validation)
- Apache Santuario (XML processing for EU Trust List)
- OkHttp (HTTP client for trust list download)

## Implementation Status

- ✅ Basic certificate validation
- ✅ eIDAS certificate profile validation (QCStatement)
- ✅ Clearing House trust anchor validation
- ✅ EU Trust List download and caching
- ⚠️ Full EU Trust List XML parsing (simplified implementation)
- ⚠️ CRL validation (placeholder for future implementation)
- ⚠️ OCSP support (future enhancement)

## Notes

- This extension is the foundation for eIDAS and Clearing House integrations
- Other extensions (eIDAS Client, Clearing House Client) depend on this extension
- Certificate caching is enabled by default for performance
- EU Trust List is cached for 24 hours by default

