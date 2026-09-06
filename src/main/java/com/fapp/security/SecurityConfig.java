package com.fapp.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Who may call what.
 *
 * <p>Everything is closed unless it has to be open. Registration must be, or nobody could
 * ever get an account, and the liveness probe must be, or nothing could check the service
 * is up without a credential. Every other endpoint serves somebody's financial history
 * and requires authentication.
 *
 * <p><strong>HTTP Basic over a stateless chain.</strong> Chosen for what it does not
 * bring: no session store to keep, no cookie, and therefore no CSRF surface to defend —
 * a cross-site request cannot attach an Authorization header. It is also completely
 * transparent, which for an application whose point is trustworthy financial figures is
 * worth more than sophistication. The cost is that credentials travel on every request,
 * so <strong>this must only ever be served over HTTPS</strong>; a token or session scheme
 * is the natural next step and is a change to this class alone.
 *
 * <p>CSRF protection is switched off deliberately rather than by oversight: it defends
 * against a browser attaching an ambient credential to a forged request, and with no
 * cookie and no session there is no ambient credential to attach.
 *
 * <p>Failing authentication answers 401 with an empty body rather than a
 * {@code WWW-Authenticate} challenge, so a browser does not put up its own login dialog
 * in front of the application.
 */
@Configuration
class SecurityConfig {

    /**
     * Delegating so the algorithm a hash was made with is recorded in the hash itself.
     * Today that is bcrypt; a stronger default later re-encodes on next sign-in rather
     * than invalidating every stored password.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        // Registration, or there would be no way to acquire an account.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/users").permitAll()
                        // Liveness, which must answer without a credential.
                        .requestMatchers("/api/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // No cookie and no session means no ambient credential for a forged
                // request to ride on, which is what CSRF protection exists to stop.
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}
