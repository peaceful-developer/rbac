package com.iam.security;

import com.iam.domain.Permission;
import com.iam.domain.Role;
import com.iam.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Adapts our {@link User} entity to Spring Security's {@link UserDetails} contract.
 * This is what {@code @PreAuthorize} and friends ultimately see as "the current user."
 * <p>
 * The important piece is {@link #buildAuthorities}: it flattens a user's roles into
 * the two kinds of authority Spring Security checks throughout this app -
 * {@code ROLE_<name>} (e.g. "ROLE_ADMIN", for role-based checks) and each individual
 * permission name from every role the user holds (e.g. "USER_WRITE", for the
 * {@code hasAuthority(...)} checks on controller endpoints). Both live in the same
 * flat authority set; Spring Security doesn't distinguish them.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.enabled = user.isEnabled();
        this.accountNonLocked = user.isAccountNonLocked();
        this.authorities = buildAuthorities(user.getRoles());
    }

    /** Union of "ROLE_&lt;name&gt;" for every role, plus every permission name across all of them, deduplicated. */
    private static Collection<? extends GrantedAuthority> buildAuthorities(Set<Role> roles) {
        Stream<String> roleAuthorities = roles.stream().map(r -> "ROLE_" + r.getName());
        Stream<String> permissionAuthorities = roles.stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getName);
        return Stream.concat(roleAuthorities, permissionAuthorities)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    /** Always true - this codebase has no notion of account expiry, only enabled/locked. */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    /** Always true - this codebase has no notion of credential expiry (e.g. forced periodic password rotation). */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
