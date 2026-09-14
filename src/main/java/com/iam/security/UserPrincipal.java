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
 * The important piece is {@link #buildAuthorities}: it flattens a user's *global*
 * roles into the two kinds of authority Spring Security checks throughout this app -
 * {@code ROLE_<name>} (e.g. "ROLE_ADMIN", for role-based checks) and each individual
 * permission name from every role the user holds (e.g. "USER_WRITE", for the
 * {@code hasAuthority(...)} checks on controller endpoints) - plus a literal
 * {@code "MASTER_ADMIN"} authority when {@link User#isMasterAdmin()} is set.
 * <p>
 * Project-scoped roles (a user's {@code SUPER_ADMIN}/etc. membership in a particular
 * {@code Project}) are deliberately <em>not</em> included here - those can't be
 * expressed as a flat, project-agnostic authority string, since holding a role in
 * project A must not grant anything in project B. See
 * {@code ProjectAuthorizationService} for how those are checked instead.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final boolean masterAdmin;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.enabled = user.isEnabled();
        this.accountNonLocked = user.isAccountNonLocked();
        this.masterAdmin = user.isMasterAdmin();
        this.authorities = buildAuthorities(user.getRoles(), this.masterAdmin);
    }

    /** Union of "ROLE_&lt;name&gt;" for every global role, every permission name across all of them, and "MASTER_ADMIN" if applicable - deduplicated. */
    private static Collection<? extends GrantedAuthority> buildAuthorities(Set<Role> roles, boolean masterAdmin) {
        Stream<String> roleAuthorities = roles.stream().map(r -> "ROLE_" + r.getName());
        Stream<String> permissionAuthorities = roles.stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getName);
        Stream<String> masterAdminAuthority = masterAdmin ? Stream.of("MASTER_ADMIN") : Stream.empty();
        return Stream.concat(Stream.concat(roleAuthorities, permissionAuthorities), masterAdminAuthority)
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
