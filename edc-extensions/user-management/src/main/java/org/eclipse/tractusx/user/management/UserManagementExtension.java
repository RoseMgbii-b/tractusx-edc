//package org.eclipse.tractusx.user.management;
//
//import org.eclipse.edc.runtime.metamodel.annotation.Extension;
//import org.eclipse.edc.runtime.metamodel.annotation.Inject;
//import org.eclipse.edc.spi.system.ServiceExtension;
//import org.eclipse.edc.spi.system.ServiceExtensionContext;
//import org.eclipse.edc.web.spi.WebService;
//import org.eclipse.tractusx.user.management.service.KeycloakAdminApiService;
//
//import javax.management.monitor.Monitor;
//
//@Extension(value = "User Management API", categories = { "api", "management" })
//public class UserManagementExtension implements ServiceExtension {
//
//    @Inject
//    private WebService webService;
//
//    @Inject
//    private Monitor monitor;
//
//    @Inject
//    private ServiceExtensionContext context;
//
//    @Override
//    public void initialize(ServiceExtensionContext context) {
//        // Read configuration
//        String keycloakUrl = context.getSetting("edc.usermanagement.keycloak.admin.url", null);
//        String realm = context.getSetting("edc.usermanagement.keycloak.realm", null);
//        // ... other config
//
//        // Create service
//        KeycloakAdminApiService keycloakService = new KeycloakAdminApiService(
//                keycloakUrl, realm, /* other params */
//                monitor
//        );
//
//        // Create controller
//        BaseUserManagementApiController controller =
//                new UserManagementApiV3Controller(keycloakService, monitor);
//
//        // Register with WebService
//        webService.registerResource("management", controller);
//
//        monitor.info("User Management API v3 registered at /api/management/v3/users");
//    }
//}
