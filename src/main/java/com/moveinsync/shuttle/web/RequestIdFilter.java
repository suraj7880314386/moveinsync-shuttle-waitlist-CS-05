package com.moveinsync.shuttle.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("access");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String incoming = req.getHeader("X-Request-Id");
        String rid = (incoming != null && incoming.matches("[A-Za-z0-9-]{1,64}")) ? incoming
                : UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", rid);
        res.setHeader("X-Request-Id", rid);
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            if (!req.getRequestURI().startsWith("/actuator")) {
                log.info("event=http method={} path={} status={} durationMs={}", req.getMethod(), req.getRequestURI(),
                        res.getStatus(), (System.nanoTime() - start) / 1_000_000);
            }
            MDC.clear();
        }
    }
}
