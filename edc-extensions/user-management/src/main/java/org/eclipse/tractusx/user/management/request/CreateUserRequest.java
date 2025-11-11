package org.eclipse.tractusx.user.management.request;

import java.util.List;

public class CreateUserRequest {
    private String username;
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private boolean emailVerified;
    private List<String> roles;
}
