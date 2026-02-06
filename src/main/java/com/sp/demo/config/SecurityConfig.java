package com.sp.demo.config;

import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http,
      ClientRegistrationRepository clientRegistrationRepository) throws Exception {

    OAuth2AuthorizationRequestResolver authorizationRequestResolver =
        authorizationRequestResolver(clientRegistrationRepository);

    http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/**", "/test/**").permitAll()
            .requestMatchers(
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**"
            ).permitAll()
            .anyRequest().authenticated() // TODO: Secure with JWT or session before production
        )
        .oauth2Login(oauth -> oauth
            .authorizationEndpoint(authorization -> authorization
                .authorizationRequestResolver(authorizationRequestResolver)
            )
            .defaultSuccessUrl("/auth/success", true)
        );

    return http.build();
  }

  private OAuth2AuthorizationRequestResolver authorizationRequestResolver(
      ClientRegistrationRepository clientRegistrationRepository) {

    DefaultOAuth2AuthorizationRequestResolver defaultResolver =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository,
            "/oauth2/authorization"
        );

    return new OAuth2AuthorizationRequestResolver() {
      @Override
      public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest authorizationRequest = defaultResolver.resolve(request);
        return customize(authorizationRequest);
      }

      @Override
      public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest authorizationRequest =
            defaultResolver.resolve(request, clientRegistrationId);
        return customize(authorizationRequest);
      }

      private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) {
          return null;
        }

        if (!"google".equals(authorizationRequest.getAttributes().get("registration_id"))) {
          return authorizationRequest;
        }

        Map<String, Object> additionalParameters =
            new HashMap<>(authorizationRequest.getAdditionalParameters());
        additionalParameters.put("access_type", "offline");
        additionalParameters.put("prompt", "consent");

        return OAuth2AuthorizationRequest.from(authorizationRequest)
            .additionalParameters(additionalParameters)
            .build();
      }
    };
  }
}


