package com.firstory.global.config;

import com.firstory.global.security.JsonRememberMeServices;
import com.firstory.global.security.JsonSecurityErrorHandler;
import com.firstory.global.security.RememberMeTokenRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.web.cors.CorsConfigurationSource;

import javax.sql.DataSource;
import java.time.Duration;

@Configuration
public class SecurityConfig {

    public static final String REMEMBER_ME_COOKIE = "FREMEMBER";
    private static final Duration REMEMBER_ME_VALIDITY = Duration.ofDays(90);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JsonSecurityErrorHandler errorHandler,
                                                   JsonRememberMeServices rememberMeServices,
                                                   SecurityContextRepository securityContextRepository,
                                                   CookieSerializer cookieSerializer,
                                                   FirstoryProperties properties,
                                                   @Qualifier(CorsConfig.CORS_SOURCE)
                                                   ObjectProvider<CorsConfigurationSource> corsSource) {
        CorsConfigurationSource cors = corsSource.getIfAvailable();
        if (cors != null) {
            http.cors(c -> c.configurationSource(cors));
        } else {
            http.cors(AbstractHttpConfigurer::disable);
        }

        http
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // SPA 방식: XSRF-TOKEN 쿠키로 내려주고 X-XSRF-TOKEN 헤더로 돌려받는다. 로그인, 가입도 검사한다
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository(properties.web())))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                // 401이 날 때 요청을 세션에 저장하지 않는다 (API라 되돌아갈 페이지가 없다)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .rememberMe(rememberMe -> rememberMe.rememberMeServices(rememberMeServices))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .addLogoutHandler(clearSessionCookie(cookieSerializer))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/phone/send-code", "/api/auth/phone/verify",
                                "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/health").permitAll()
                        // Swagger. springdoc이 꺼진 운영에서는 이 경로에 핸들러가 없어 404가 난다
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * 로그인과 가입은 필터가 아닌 서비스에서 처리하므로 이 저장소로 SecurityContext를 명시적으로 저장한다.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean
    public RememberMeTokenRepository rememberMeTokenRepository(DataSource dataSource) {
        return new RememberMeTokenRepository(dataSource);
    }

    @Bean
    public JsonRememberMeServices rememberMeServices(FirstoryProperties properties,
                                                     UserDetailsService userDetailsService,
                                                     RememberMeTokenRepository tokenRepository) {
        FirstoryProperties.Web web = properties.web();
        JsonRememberMeServices services = new JsonRememberMeServices(
                properties.auth().rememberMeKey(), userDetailsService, tokenRepository);
        services.setCookieName(REMEMBER_ME_COOKIE);
        services.setTokenValiditySeconds((int) REMEMBER_ME_VALIDITY.toSeconds());
        services.setUseSecureCookie(web.cookieSecure());
        if (web.hasCookieDomain()) {
            services.setCookieDomain(web.cookieDomain());
        }
        services.setCookieCustomizer(cookie -> cookie.setAttribute("SameSite", "Lax"));
        return services;
    }

    private static CookieCsrfTokenRepository csrfTokenRepository(FirstoryProperties.Web web) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> {
            cookie.secure(web.cookieSecure()).sameSite("Lax");
            if (web.hasCookieDomain()) {
                cookie.domain(web.cookieDomain());
            }
        });
        return repository;
    }

    // 세션이 이미 만료됐어도 브라우저의 FSESSION 쿠키를 지운다. 빈 값을 쓰면 Max-Age=0으로 나간다
    private static LogoutHandler clearSessionCookie(CookieSerializer cookieSerializer) {
        return (request, response, authentication) ->
                cookieSerializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, ""));
    }
}
