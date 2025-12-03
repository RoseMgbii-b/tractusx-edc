/********************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

package org.eclipse.tractusx.gateway;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.tractusx.gateway.filter.GatewayFilter;
import org.eclipse.tractusx.gateway.rate.limit.InMemoryRateLimitStore;
import org.eclipse.tractusx.gateway.rate.limit.RateLimitStore;
import org.eclipse.tractusx.gateway.service.GatewayService;

@Extension("Access Control Gateway")
public class GatewayExtension implements ServiceExtension {


    @Inject
    private WebService webService;

    @Inject
    private Monitor monitor;

    @Setting(value = "Enable access control gateway", defaultValue = "true")
    public static final String GATEWAY_ENABLED = "edc.gateway.enabled";

    @Setting(value = "IP whitelist (comma-separated)", defaultValue = "")
    public static final String IP_WHITELIST = "edc.gateway.ip.whitelist";

    @Setting(value = "IP blacklist (comma-separated)", defaultValue = "")
    public static final String IP_BLACKLIST = "edc.gateway.ip.blacklist";

    @Setting(value = "Max requests per minute per client", defaultValue = "100")
    public static final String RATE_LIMIT_PER_MINUTE = "edc.gateway.rate.limit.per.minute";

    @Setting(value = "Max requests per hour per client", defaultValue = "1000")
    public static final String RATE_LIMIT_PER_HOUR = "edc.gateway.rate.limit.per.hour";

    @Override
    public void initialize(ServiceExtensionContext context) {
        Config config = context.getConfig();
        Monitor monitor = context.getMonitor();

        // Create rate limit store
        RateLimitStore rateLimitStore = new InMemoryRateLimitStore();

        // Create access control service
        GatewayService gatewayService = new GatewayService(
                rateLimitStore,
                monitor,
                config
        );

        // Create and register filter for all API contexts
        GatewayFilter filter = new GatewayFilter(gatewayService, monitor);

        // Register filter for all API contexts
        registerFilterForContext(ApiContext.PROTOCOL, filter);
        registerFilterForContext(ApiContext.MANAGEMENT, filter);
        registerFilterForContext(ApiContext.CONTROL, filter);
        registerFilterForContext(ApiContext.PUBLIC, filter);

        monitor.info("Access Control Gateway initialized and active");
    }

    private void registerFilterForContext(String apiContext, GatewayFilter filter) {
        try {
            webService.registerResource(apiContext, filter);
            monitor.debug(String.format("Access control filter registered for context: %s", apiContext));
        } catch (Exception e) {
            monitor.warning(String.format("Failed to register access control filter for context %s: %s",
                    apiContext, e.getMessage()));
        }
    }
}
