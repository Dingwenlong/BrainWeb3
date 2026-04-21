package com.brainweb3.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final AppUserRepository appUserRepository;

  public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository appUserRepository) {
    this.jwtService = jwtService;
    this.appUserRepository = appUserRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = authorization.substring("Bearer ".length()).trim();
    if (token.isBlank() || !jwtService.isTokenValid(token)) {
      filterChain.doFilter(request, response);
      return;
    }

    String actorId = jwtService.extractSubject(token);
    AppUserEntity user = appUserRepository.findById(actorId).orElse(null);
    if (user == null || !"active".equalsIgnoreCase(user.getStatus())) {
      filterChain.doFilter(request, response);
      return;
    }

    AppUserPrincipal principal = AppUserPrincipal.fromEntity(user);
    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
        principal,
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + principal.actorRole().toUpperCase()))
    );
    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    filterChain.doFilter(request, response);
  }
}
