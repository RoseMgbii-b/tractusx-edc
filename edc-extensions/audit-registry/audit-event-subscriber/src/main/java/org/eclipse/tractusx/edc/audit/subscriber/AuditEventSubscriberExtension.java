/*
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
 */

package org.eclipse.tractusx.edc.audit.subscriber;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.tractusx.edc.spi.audit.AuditRegistry;

/**
 * Extension that registers the audit event subscriber to capture EDC events.
 */
@Extension("Audit Event Subscriber")
public class AuditEventSubscriberExtension implements ServiceExtension {

    @Setting(value = "Enable audit event subscriber", defaultValue = "true")
    public static final String AUDIT_ENABLED = "tx.edc.audit.enabled";

    @Inject
    private EventRouter eventRouter;

    @Inject
    private AuditRegistry auditRegistry;

    @Override
    public String name() {
        return "Audit Event Subscriber";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var enabled = context.getSetting(AUDIT_ENABLED, true);
        
        if (enabled) {
            var subscriber = new AuditEventSubscriber(auditRegistry);
            eventRouter.register(Event.class, subscriber);
            context.getMonitor().info("Audit event subscriber registered successfully");
        } else {
            context.getMonitor().info("Audit event subscriber is disabled");
        }
    }
}
