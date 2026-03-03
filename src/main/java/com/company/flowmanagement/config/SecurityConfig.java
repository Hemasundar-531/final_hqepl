package com.company.flowmanagement.config;

import com.company.flowmanagement.security.CustomUserDetailsService;
import com.company.flowmanagement.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          UserRepository userRepository) {
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // Disable CSRF (for simplicity)
                .csrf(csrf -> csrf.disable())

                // Authorization rules
                .authorizeHttpRequests(auth -> auth

                        // Public access
                        .requestMatchers("/login").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/img/**", "/static/**", "/uploads/**").permitAll()
                        .requestMatchers("/debug/**").permitAll()

                        // Role based access
                        .requestMatchers("/superadmin/**").hasRole("SUPERADMIN")

                        .requestMatchers("/admin/**")
                        .hasAnyRole("ADMIN", "SUPERADMIN")

                        // ✅ FIXED: allow all roles to access employee pages
                        .requestMatchers("/employee/**")
                        .hasAnyRole("EMPLOYEE", "ADMIN", "SUPERADMIN")

                        // All other requests need authentication
                        .anyRequest().authenticated()
                )

                // Login config
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(authenticationSuccessHandler())
                        .permitAll()
                )

                // Logout config
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                );

        // Set custom user service
        http.userDetailsService(userDetailsService);

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {

        return (request, response, authentication) -> {

            var authorities = authentication.getAuthorities();

            String targetUrl = "/employee/dashboard";

            // SUPERADMIN
            if (authorities.stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"))) {

                targetUrl = "/superadmin/dashboard";
            }

            // ADMIN
            else if (authorities.stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {

                targetUrl = "/admin/dashboard";
            }

            // EMPLOYEE
            else if (authorities.stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYEE"))) {

                targetUrl = "/employee/dashboard";
            }

            response.sendRedirect(targetUrl);
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }
}
