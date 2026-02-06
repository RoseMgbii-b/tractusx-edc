/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.filter;

import org.eclipse.edc.policy.engine.spi.PolicyContextImpl;

/**
 * Simple implementation of PolicyContext for service authorization.
 */
public class SimplePolicyContext extends PolicyContextImpl {
    
    @Override
    public String scope() {
        return "service-auth";
    }
}

