package com.musicdreamer.gateway.filter;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 统一鉴权过滤器（设计 4.2 节）：
 * 白名单放行 → 解析 JWT → 吊销地板检查 → 剥离伪造头并透传身份 → 管理员/上传者路由角色校验。
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final ReactiveStringRedisTemplate redis;

    public AuthGlobalFilter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 认证地板：改密/重置/禁用时由 Mod_login 写入，早于该时刻签发的 token 一律拒绝。 */
    private static final String AUTH_FLOOR_KEY = "md_auth_floor:";

    /** 匿名可访问。 */
    private static final String[] WHITELIST = {
            "/api/v1/user/login", "/api/v1/user/register", "/api/v1/user/activate",
            "/api/v1/user/password/reset", "/api/v1/user/wx/login",
            "/api/v1/search/**", "/api/v1/song/play/**", "/api/v1/song/detail/**",
            "/api/v1/song/chart/**",
            "/api/v1/comment/list/**", "/api/v1/recommend/list",
            "/data/**",
    };

    /**
     * 可匿名访问的公开数据（仅 GET）；携带有效 token 时仍解析并透传身份，
     * 服务层据此区分"公开歌单匿名可看 / 私有歌单仅本人"。
     */
    private static final String[] OPTIONAL_AUTH = {
            "/api/v1/playlist/public/**",
            "/api/v1/playlist/*",
            "/api/v1/album/public/**",
            "/api/v1/album/*",
    };

    /** 需要 role=2（管理员）。举报只拦管理动作：提交是登录用户皆可（bug 复盘 P1-2）。 */
    private static final String[] ADMIN_PATHS = {
            "/api/v1/user/list*", "/api/v1/user/status", "/api/v1/user/singer/audit",
            "/api/v1/song/audit", "/api/v1/song/takedown/**", "/api/v1/song/relist/**",
            "/api/v1/song/delete/**", "/api/v1/song/edit/**", "/api/v1/song/admin/**",
            "/api/v1/report/list*", "/api/v1/report/handle",
            "/api/v1/media/tools/**", "/api/v1/media/models/*/download",
            "/api/v1/media/models/*/use", "/api/v1/media/models/*/delete",
            "/api/v1/media/loudness/batch", "/api/v1/setting/set/**",
            "/api/v1/admin/**",
    };

    /** 需要 role>=1（歌手或管理员）：上传与链接导入。 */
    private static final String[] UPLOADER_PATHS = {
            "/api/v1/song/upload", "/api/v1/song/submit", "/api/v1/media/download",
            "/api/v1/upload/**", "/api/v1/media/transcribe/**",
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (matchAny(path, WHITELIST)) {
            return chain.filter(sanitize(exchange));
        }

        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        boolean optional = "GET".equals(request.getMethodValue()) && matchAny(path, OPTIONAL_AUTH);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return optional ? chain.filter(sanitize(exchange))
                    : reject(exchange, HttpStatus.UNAUTHORIZED, 2006, "Token已过期");
        }
        Claims claims = JwtUtil.parse(auth.substring(7));
        if (claims == null) {
            return optional ? chain.filter(sanitize(exchange))
                    : reject(exchange, HttpStatus.UNAUTHORIZED, 2006, "Token已过期");
        }

        Long userId = claims.get(JwtUtil.CLAIM_USER_ID, Number.class).longValue();
        Integer role = claims.get(JwtUtil.CLAIM_ROLE, Number.class).intValue();
        String username = String.valueOf(claims.get(JwtUtil.CLAIM_USERNAME));
        long issuedAtMs = claims.getIssuedAt() == null ? 0 : claims.getIssuedAt().getTime();

        // 吊销地板检查（P1-1）：改密/重置/禁用后旧 token 当场失效。
        // 必须保持响应式链——在 Netty 事件循环线程里 block() 会抛异常导致 fail-open 静默失效；
        // iat 秒级精度留 1 秒宽限；Redis 异常/键缺失时放行（fail-open）。
        return redis.opsForValue().get(AUTH_FLOOR_KEY + userId)
                .map(this::safeParseLong)
                .defaultIfEmpty(0L)
                .onErrorReturn(0L)
                .flatMap(floor -> {
                    // JWT iat 只有秒级精度：宽限 1 秒——与抬升同一秒内签发的 token 放行，
                    // 更早签发的一律拒绝（floor 侧不额外加缓冲，避免误杀改密后的新登录）
                    if (floor > 0 && floor - issuedAtMs > 1000) {
                        return reject(exchange, HttpStatus.UNAUTHORIZED, 2006, "登录状态已变更，请重新登录");
                    }
                    return proceed(exchange, chain, path, userId, role, username);
                });
    }

    /** 地板检查通过后的公共尾段：角色路由校验 + 身份头透传。 */
    private Mono<Void> proceed(ServerWebExchange exchange, GatewayFilterChain chain,
                               String path, Long userId, Integer role, String username) {
        if (matchAny(path, ADMIN_PATHS) && role != 2) {
            return reject(exchange, HttpStatus.FORBIDDEN, 2007, "权限不足");
        }
        if (matchAny(path, UPLOADER_PATHS) && role < 1) {
            return reject(exchange, HttpStatus.FORBIDDEN, 2007, "权限不足");
        }

        ServerHttpRequest mutated = sanitize(exchange).getRequest().mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Role", String.valueOf(role))
                .header("X-Username", username)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /** 剥离外部伪造的身份头，防止绕过网关直透。 */
    private ServerWebExchange sanitize(ServerWebExchange exchange) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Role");
                    h.remove("X-Username");
                })
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private boolean matchAny(String path, String[] patterns) {
        for (String p : patterns) {
            if (matcher.match(p, path)) return true;
        }
        return false;
    }

    /** 宽松解析；脏值当 0（不吊销）。 */
    private long safeParseLong(String v) {
        try {
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, int code, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + code + ",\"message\":\"" + msg
                + "\",\"timestamp\":" + System.currentTimeMillis()
                + ",\"path\":\"" + exchange.getRequest().getPath().value() + "\"}";
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
