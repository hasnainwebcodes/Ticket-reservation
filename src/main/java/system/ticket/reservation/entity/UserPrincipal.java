package system.ticket.reservation.entity;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id        = user.getId();
        this.email     = user.getEmail();
        this.password  = user.getPassword();
        this.firstName = user.getFirstName();
        this.lastName  = user.getLastName();
        this.authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
    }

    @Override public String getUsername()                    { return email; }
    @Override public String getPassword()                    { return password; }
    @Override public Collection<? extends GrantedAuthority>
    getAuthorities()                { return authorities; }
    @Override public boolean isAccountNonExpired()           { return true; }
    @Override public boolean isAccountNonLocked()            { return true; }
    @Override public boolean isCredentialsNonExpired()       { return true; }
    @Override public boolean isEnabled()                     { return true; }
}