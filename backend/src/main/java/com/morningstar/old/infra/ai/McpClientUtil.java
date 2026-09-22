package com.morningstar.old.infra.ai;

import com.morningstar.old.infra.properties.AiMcpClientProperties;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientUtil {
    public static final String TOKEN_PREFIX = "Bearer ";
    private static final SignatureAlgorithm ALGORITHM = SignatureAlgorithm.RS256;

    private final AiMcpClientProperties clientProperties;

    /**
     * 签发私钥/公钥（惰性加载）
     */
    private volatile PrivateKey privateKey;
    private volatile PublicKey publicKey;

    /**
     * 以本应用持有的私钥现场签发 mcp-token，返回带 {@code Bearer } 前缀的完整值
     */
    public String createToken(String account) {
        AiMcpClientProperties.Signing signing = clientProperties.getSigning();
        long now = System.currentTimeMillis();
        return TOKEN_PREFIX + Jwts.builder()
                .setIssuer(signing.getIssuer())
                .setSubject(account)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + signing.getTtlSeconds() * 1000))
                .signWith(ALGORITHM, loadPrivateKey(signing.getPrivateKey()))
                .compact();
    }

    private PrivateKey loadPrivateKey(String base64) {
        if (privateKey == null) {
            synchronized (this) {
                if (privateKey == null) {
                    privateKey = parseKey(base64, true);
                }
            }
        }
        return privateKey;
    }

    private PublicKey loadPublicKey(String base64) {
        if (publicKey == null) {
            synchronized (this) {
                if (publicKey == null) {
                    publicKey = parseKey(base64, false);
                }
            }
        }
        return publicKey;
    }

    /**
     * 验签并解析 mcp-token：剥前缀 → 按 iss 从名册取公钥（未登记即失败）→
     * 验签（显式要求 RS256）→ 校验 exp。成功返回 claims，任何失败返回 null（原因记日志）。
     */
    public Claims parseToken(String token) {
        if (token == null || !token.startsWith(TOKEN_PREFIX)) {
            log.warn("mcp-token 缺失或缺少 Bearer 前缀");
            return null;
        }
        try {
            return Jwts.parser()
                    // 验自己签发的 token：用 client 签名配置里的公钥（惰性加载，首次解析时才解码）
                    .setSigningKey(loadPublicKey(clientProperties.getSigning().getPublicKey()))
                    .parseClaimsJws(token.substring(TOKEN_PREFIX.length()))
                    .getBody();
        } catch (RuntimeException e) {
            log.warn("mcp-token 验签失败: {}", e.getMessage());
            return null;
        }
    }

    private static <T extends Key> T parseKey(String base64, boolean isPrivate) {
        try {
            // getMimeDecoder 容忍换行等空白字符，粘贴多行 PEM 正文也能解
            byte[] der = Base64.getMimeDecoder().decode(base64.trim());
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            @SuppressWarnings("unchecked")
            T key = (T) (isPrivate
                    ? keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der))
                    : keyFactory.generatePublic(new X509EncodedKeySpec(der)));
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("MCP " + (isPrivate ? "私" : "公") + "钥解析失败", e);
        }
    }

    /**
     * 解析出账号（subject）；token 缺失/非法时返回 null，由工具方法决定如何报错
     */
    public String getAccount(String token) {
        Claims claims = parseToken(token);
        return claims == null ? null : claims.getSubject();
    }

    /**
     * 解析出token生成方（issuer）；token 缺失/非法时返回 null，由工具方法决定如何报错
     */
    public String getIssuer(String token) {
        Claims claims = parseToken(token);
        return claims == null ? null : claims.getIssuer();
    }

    public String getAccount(){
        String token = McpTokenHolder.get();
        return getAccount(token);
    }
}
