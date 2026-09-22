package com.morningstar.old.system.filter;

import com.morningstar.old.infra.constant.RedisConstant;
import com.morningstar.old.infra.response.R;
import com.morningstar.old.infra.response.ResponseCode;
import com.morningstar.old.infra.util.WebUtil;
import com.morningstar.old.system.pojo.bo.LoginUser;
import com.morningstar.old.system.util.JwtUtil;
import io.jsonwebtoken.Claims;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // MCP 端点有自己的认证体系（mcp-token，RS256），与系统登录 token（HS256）互不通用；
        // 本过滤器若尝试用系统 JwtUtil 解析 mcp-token 会误判为 TOKEN_INVALID 并短路请求，
        // 故 /mcp/** 直接跳过（身份鉴定在 MCP 工具方法内完成，见 SecurityConfig 白名单注释）
        String uri = request.getRequestURI();
        return "/mcp".equals(uri) || uri.startsWith("/mcp/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        // 获取JWT
        String token = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.isBlank(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 解析JWT
        Claims claims = jwtUtil.parse(token);
        if (claims == null) {
            WebUtil.renderJson(R.error(ResponseCode.TOKEN_INVALID), response);
            return;
        }
        String userId = claims.getSubject();

        // 从Redis中获取用户信息
        LoginUser loginUser = (LoginUser) redisTemplate.opsForValue().get(RedisConstant.AUTH_LOGIN + RedisConstant.KEY_SEPARATOR + userId);
        if (loginUser == null) {
            WebUtil.renderJson(R.error(ResponseCode.TOKEN_EXPIRED), response);
            return;
        }

        // 将认证token存入SecurityContextHolder
        SecurityContextHolder.getContext().
                setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));

        // 放行
        filterChain.doFilter(request, response);
    }
}
