package com.casper.docrag.ratelimit;

import com.casper.docrag.config.AppProperties;
import com.casper.docrag.error.InvalidAccessCodeException;
import com.casper.docrag.error.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Enumeration;

/**
 * 查詢前置攔截（SPEC §4.2 步驟 0）：存取碼檢查 → 取用 IP 限流 token。
 * 任一未過即拋例外，由 GlobalExceptionHandler 轉為 401/429，請求不進入控制器。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final AppProperties.RateLimit cfg;

    public RateLimitInterceptor(RateLimiter rateLimiter, AppProperties props) {
        this.rateLimiter = rateLimiter;
        this.cfg = props.ratelimit();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!cfg.enabled()) {
            return true;
        }

//        System.out.println("METHOD = " + request.getMethod());
//        System.out.println("URI = " + request.getRequestURI());
//        System.out.println("X-Access-Code = " + request.getHeader("x-access-code"));
//        Enumeration<String> headers = request.getHeaderNames();
//
//        String requiredCode = cfg.accessCode();
//        if (requiredCode != null && !requiredCode.isBlank()) {
//            String provided = request.getHeader("X-Access-Code");
//            if (!requiredCode.equals(provided)) {
//                throw new InvalidAccessCodeException("缺少或錯誤的存取碼（X-Access-Code）");
//            }
//        }

        RateLimiter.Result result = rateLimiter.tryConsume(clientIp(request));
        if (!result.allowed()) {
            throw new RateLimitExceededException("請求過於頻繁，請稍後再試", result.retryAfterSeconds());
        }
        return true;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
