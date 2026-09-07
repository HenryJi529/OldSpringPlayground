package com.morningstar.old.infra.ai;

import com.morningstar.old.infra.properties.AiMcpServerProperties;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.chat.ChatSession;
import org.noear.solon.ai.chat.tool.MethodExecuteHandler;
import org.noear.solon.core.handle.Context;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpServerUtil {
    public static final String TOKEN_PREFIX = "Bearer ";
    private static final SignatureAlgorithm ALGORITHM = SignatureAlgorithm.RS256;

    /**
     * 名册公钥缓存：iss → 解析后的公钥（惰性加载，名册里未接入签发方的占位项不会影响启动）
     */
    private final Map<String, PublicKey> publicKeyCache = new ConcurrentHashMap<>();

    private final AiMcpServerProperties serverProperties;

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
                    .setSigningKeyResolver(new IssuerRoutedKeyResolver())
                    .parseClaimsJws(token.substring(TOKEN_PREFIX.length()))
                    .getBody();
        } catch (RuntimeException e) {
            log.warn("mcp-token 验签失败: {}", e.getMessage());
            return null;
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

    /**
     * 按 iss 路由的验签密钥解析器：签名验证前先读 iss（此时 claims 未验签，仅用于选 key，
     * 不可信），从名册取对应公钥交给 jjwt 完成真正的验签——iss 伪造没有攻击价值，
     * 选错 key 只会验签失败
     */
    private class IssuerRoutedKeyResolver extends io.jsonwebtoken.SigningKeyResolverAdapter {
        @Override
        public Key resolveSigningKey(JwsHeader header, Claims claims) {
            // 显式约束算法，防 alg 混淆（如 HS256/none）
            if (!ALGORITHM.getValue().equals(header.getAlgorithm())) {
                throw new SignatureException("不接受的签名算法: " + header.getAlgorithm());
            }
            String iss = claims.getIssuer();
            if (iss == null) {
                throw new SignatureException("mcp-token 缺少 iss");
            }
            PublicKey key = publicKeyCache.computeIfAbsent(iss, McpServerUtil.this::loadPublicKey);
            if (key == null) {
                throw new SignatureException("未登记的签发方: " + iss);
            }
            return key;
        }
    }

    /**
     * 从名册取 iss 对应公钥；未登记或公钥不可解析时返回 null（computeIfAbsent 不缓存 null，
     * 名册修正后无需重启即可生效）
     */
    private PublicKey loadPublicKey(String iss) {
        String base64 = serverProperties.getIssuers().get(iss);
        if (base64 == null) {
            return null;
        }
        try {
            return parsePublicKey(base64);
        } catch (RuntimeException e) {
            log.error("签发方[{}]的公钥配置无法解析", iss, e);
            return null;
        }
    }

    private static <T extends Key> T parsePublicKey(String base64) {
        try {
            // getMimeDecoder 容忍换行等空白字符，粘贴多行 PEM 正文也能解
            byte[] der = Base64.getMimeDecoder().decode(base64.trim());
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            @SuppressWarnings("unchecked")
            T key = (T) keyFactory.generatePublic(new X509EncodedKeySpec(der));
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("MCP 公钥解析失败", e);
        }
    }

    public String getAccount() {
        return getAccount(Context.current().header(HttpHeaders.AUTHORIZATION));
    }

    public String getIssuer(){
        return getIssuer(Context.current().header(HttpHeaders.AUTHORIZATION));
    }

    public String getSessionId() {
        Map<String, Object> args = Context.current().attr(MethodExecuteHandler.MCP_BODY_ATTR);
        return args == null ? null : (String) args.get(ChatSession.ATTR_SESSIONID);
    }
}
