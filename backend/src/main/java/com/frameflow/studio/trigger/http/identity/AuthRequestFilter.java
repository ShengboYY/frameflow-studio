package com.frameflow.studio.trigger.http.identity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import com.frameflow.studio.trigger.http.error.JsonErrors;

/** Strict transport validation after CSRF; the standard Security filter still owns login. */
public class AuthRequestFilter extends OncePerRequestFilter {
    private static final String PREFIX = "/api/v1/auth/";
    private final JsonErrors errors;

    public AuthRequestFilter(JsonErrors errors) {
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String endpoint = path.substring(Math.min(path.length(), PREFIX.length()));
        if (!path.startsWith(PREFIX)
                || !Set.of("login", "logout", "session", "csrf").contains(endpoint)) {
            chain.doFilter(request, response);
            return;
        }
        String method = Set.of("login", "logout").contains(endpoint) ? "POST" : "GET";
        if (!method.equals(request.getMethod())) {
            errors.write(request, response, 405, "METHOD_NOT_ALLOWED", "请求方法不支持");
            return;
        }
        if (endpoint.equals("login")) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                errors.write(request, response, 409, "ALREADY_AUTHENTICATED", "请先退出当前账号");
                return;
            }
            try {
                request = form(request);
            } catch (InvalidForm ex) {
                errors.write(request, response, ex.status, ex.code, ex.getMessage());
                return;
            }
        } else if (endpoint.equals("logout")
                && (request.getQueryString() != null || request.getInputStream().read() != -1)) {
            errors.write(request, response, 400, "INVALID_REQUEST", "退出请求不能包含请求体或查询参数");
            return;
        }
        chain.doFilter(request, response);
    }

    private HttpServletRequest form(HttpServletRequest request) throws IOException, InvalidForm {
        try {
            MediaType type = MediaType.parseMediaType(request.getContentType() == null ? "" : request.getContentType());
            if (!"application".equals(type.getType()) || !"x-www-form-urlencoded".equals(type.getSubtype())
                    || (type.getCharset() != null && !StandardCharsets.UTF_8.equals(type.getCharset()))
                    || !Set.of("charset").containsAll(type.getParameters().keySet())) {
                throw unsupported();
            }
        } catch (IllegalArgumentException ex) {
            throw unsupported();
        }
        if (request.getQueryString() != null) throw invalid();
        byte[] body = request.getInputStream().readNBytes(4097);
        if (body.length > 4096) throw new InvalidForm(413, "PAYLOAD_TOO_LARGE", "请求过大");
        String wire = new String(body, StandardCharsets.ISO_8859_1);
        Map<String, String[]> values = new LinkedHashMap<>();
        for (String pair : wire.split("&", -1)) {
            int equals = pair.indexOf('=');
            if (equals < 0) throw invalid();
            String key = decode(pair.substring(0, equals));
            if (!Set.of("username", "password").contains(key) || values.containsKey(key)) throw invalid();
            values.put(key, new String[]{decode(pair.substring(equals + 1))});
        }
        if (values.size() != 2) throw invalid();
        String username = values.get("username")[0];
        int passwordBytes = values.get("password")[0].getBytes(StandardCharsets.UTF_8).length;
        if (!canonicalUsername(username) || passwordBytes < 1 || passwordBytes > 72) throw invalid();
        return new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                return values.containsKey(name) ? values.get(name)[0] : null;
            }
            @Override public Map<String, String[]> getParameterMap() { return Collections.unmodifiableMap(values); }
            @Override public String[] getParameterValues(String name) { return values.get(name); }
            @Override public Enumeration<String> getParameterNames() { return Collections.enumeration(values.keySet()); }
        };
    }

    private boolean canonicalUsername(String username) {
        return username.length() >= 3 && username.length() <= 32
                && username.chars().allMatch(c -> c >= '!' && c <= '~' && !(c >= 'A' && c <= 'Z'));
    }

    private String decode(String text) throws InvalidForm {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (value == '%') {
                if (i + 2 >= text.length()) throw invalid();
                int high = Character.digit(text.charAt(++i), 16);
                int low = Character.digit(text.charAt(++i), 16);
                if (high < 0 || low < 0) throw invalid();
                bytes.write((high << 4) | low);
            } else {
                bytes.write(value == '+' ? ' ' : value);
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException ex) {
            throw invalid();
        }
    }

    private InvalidForm invalid() { return new InvalidForm(400, "INVALID_REQUEST", "登录表单无效"); }
    private InvalidForm unsupported() { return new InvalidForm(415, "UNSUPPORTED_MEDIA_TYPE", "请求编码不支持"); }

    private static final class InvalidForm extends Exception {
        private final int status;
        private final String code;
        private InvalidForm(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
