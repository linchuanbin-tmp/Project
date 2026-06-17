package com.agent.user.filter;

import com.agent.user.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtil.validateToken(jwt)) {
                Claims claims = jwtUtil.parseToken(jwt);
                String username = claims.getSubject();

                // 解析角色列表 (roles)
                List<?> rawRoles = claims.get("roles", List.class);
                List<String> roles = new ArrayList<>();
                if (rawRoles != null) {
                    for (Object r : rawRoles) {
                        roles.add(String.valueOf(r));
                    }
                }

                // 解析权限列表 (permissions)
                List<?> rawPermissions = claims.get("permissions", List.class);
                List<String> permissions = new ArrayList<>();
                if (rawPermissions != null) {
                    for (Object p : rawPermissions) {
                        permissions.add(String.valueOf(p));
                    }
                }

                // 将角色与权限合并转化为 Security Authority
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                for (String role : roles) {
                    // 确保角色有 ROLE_ 前缀
                    if (!role.startsWith("ROLE_")) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    } else {
                        authorities.add(new SimpleGrantedAuthority(role));
                    }
                }
                for (String permission : permissions) {
                    authorities.add(new SimpleGrantedAuthority(permission));
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e);
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
