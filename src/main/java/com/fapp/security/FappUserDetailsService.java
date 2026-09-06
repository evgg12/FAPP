package com.fapp.security;

import com.fapp.user.User;
import com.fapp.user.UserRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Looks up the user behind a set of credentials.
 *
 * <p>Email is matched case-insensitively, because that is how the address was stored and
 * how a person types it. A user with no password hash is reported the same way as one
 * that does not exist: they cannot sign in, and saying which of the two it is would tell
 * a stranger whether an address is registered.
 */
@Service
public class FappUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    FappUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public FappUserDetails loadUserByUsername(String email) {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("no credentials supplied");
        }
        User user = users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("bad credentials"));
        String hash = user.passwordHash()
                .orElseThrow(() -> new UsernameNotFoundException("bad credentials"));
        return new FappUserDetails(user.id(), user.email(), hash);
    }
}
