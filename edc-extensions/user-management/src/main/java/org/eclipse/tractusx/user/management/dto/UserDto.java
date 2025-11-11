package org.eclipse.tractusx.user.management.dto;


import java.util.List;

public class UserDto {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private boolean emailVerified;
    private List<String> roles;

}
