# 산출물 1 — 인증·인가 API + 권한 검증 미들웨어

---

## SecurityConfig.java
`src/main/java/com/jangingmall/backend/global/config/SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, EmailVerificationProperties.class, AiProperties.class, InternalApiProperties.class})
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final Environment environment;

    public SecurityConfig(ObjectMapper objectMapper, Environment environment) {
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain filterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtAuthenticationFilter,
        AiCallbackFilter aiCallbackFilter,
        ObjectProvider<MemberOAuthSecurity> memberOAuthSecurity
    ) throws Exception {
        if (memberOAuthSecurity.getIfAvailable() != null) {
            memberOAuthSecurity.getObject().configure(http);
        }
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                    .requestMatchers("/internal/**").hasRole("AGENT")
                    .requestMatchers(HttpMethod.POST, "/api/images/presigned-url").hasAnyRole("USER", "ARTISAN", "AGENT")
                    .requestMatchers(PermitAllPaths.PATHS.toArray(String[]::new)).permitAll()
                    .requestMatchers(HttpMethod.GET,"/api/member/artisans","/api/member/artisans/{artisanId:[0-9]+}").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/payments/webhooks/toss").permitAll()
                    .requestMatchers(HttpMethod.PATCH, "/api/payments/cart/items/*/options").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/payments/cart/merge").authenticated()
                    .requestMatchers("/api/payments/cart", "/api/payments/cart/items", "/api/payments/cart/items/*").permitAll();
                if (isLocalProfile()) {
                    auth.requestMatchers("/dev/**").permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED)
                )
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    writeError(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN)
                )
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(aiCallbackFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
```

---

## JwtAuthenticationFilter.java
`src/main/java/com/jangingmall/backend/global/security/JwtAuthenticationFilter.java`

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String SSE_PATH = "/api/notifications/stream";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = resolveToken(request);
        if (token != null) {
            try {
                JwtTokenProvider.JwtMemberClaims claims = jwtTokenProvider.parseAccessToken(token);
                var authorities = claims.role().authorities().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(claims.memberId(), null, authorities)
                );
            } catch (JwtException | JwtTokenProvider.InvalidTokenTypeException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        if (SSE_PATH.equals(request.getServletPath())) {
            return request.getParameter("token");
        }
        return null;
    }
}
```

---

## AiCallbackFilter.java
`src/main/java/com/jangingmall/backend/global/security/AiCallbackFilter.java`

```java
@RequiredArgsConstructor
public class AiCallbackFilter extends OncePerRequestFilter {

    static final String ROLE_AGENT = "ROLE_AGENT";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    static final String PRESIGNED_URL_PATH = "/api/images/presigned-url";

    private final InternalApiProperties internalApiProperties;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith(INTERNAL_PATH_PREFIX)) {
            handleInternalPath(request, response, filterChain);
            return;
        }

        if (PRESIGNED_URL_PATH.equals(path)) {
            tryAgentAuth(request);
        }

        filterChain.doFilter(request, response);
    }

    private void handleInternalPath(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws IOException, ServletException {
        String configuredToken = internalApiProperties.backendAuthToken();
        if (!StringUtils.hasText(configuredToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!configuredToken.equals(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("ai-agent", null, List.of(new SimpleGrantedAuthority(ROLE_AGENT)))
        );
        filterChain.doFilter(request, response);
    }
}
```

---

## MemberController.java
`src/main/java/com/jangingmall/backend/member/presentation/MemberController.java`

```java
@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberSignupResponse>> signUp(@Valid @RequestBody MemberSignupRequest request) {
        MemberSignupResponse response = MemberSignupResponse.from(memberService.signUp(request.toCommand()));
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MemberLoginResponse>> login(@Valid @RequestBody MemberLoginRequest request) {
        MemberSession session = memberAuthenticationService.login(request.email(), request.password());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
            .body(ApiResponse.ok(MemberLoginResponse.from(session)));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<MemberTokenRefreshResponse>> refresh(
        @CookieValue(value = "refreshToken", required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }
        MemberSession session = memberAuthenticationService.refresh(refreshToken);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
            .body(ApiResponse.ok(MemberTokenRefreshResponse.from(session, jwtProperties)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal Long memberId) {
        memberAuthenticationService.logout(memberId);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
            .body(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    public ApiResponse<MemberProfileResponse> getMe(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(MemberProfileResponse.from(memberAuthenticationService.getProfile(memberId)));
    }
}
```

---

## OAuthController.java
`src/main/java/com/jangingmall/backend/member/presentation/OAuthController.java`

```java
@RestController
@RequestMapping("/api/member/oauth2")
@RequiredArgsConstructor
public class OAuthController {

    @GetMapping("/kakao")
    public ResponseEntity<Void> kakao() {
        return ResponseEntity.status(302).location(URI.create("/oauth2/authorization/kakao")).build();
    }

    @GetMapping("/naver")
    public ResponseEntity<Void> naver() {
        return ResponseEntity.status(302).location(URI.create("/oauth2/authorization/naver")).build();
    }

    @PostMapping("/exchange")
    public ResponseEntity<ExchangeResponse> exchange(@CookieValue(value="oauthTicket",required=false) String ticket) {
        var grant = oauth.exchange(Optional.ofNullable(ticket).filter(v -> !v.isBlank())
            .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED)));
        var builder = ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, MemberCookies.ticket("", Duration.ZERO, jwt).toString());
        if (grant.memberId() == null) {
            return builder
                .header(HttpHeaders.SET_COOKIE, MemberCookies.onboarding(oauth.onboarding(grant.identity()), Duration.ofMinutes(10), jwt).toString())
                .body(new ExchangeResponse(true, null));
        }
        var session = authentication.socialSession(grant.memberId());
        return builder
            .header(HttpHeaders.SET_COOKIE, MemberCookies.refresh(session.refreshToken(), jwt).toString())
            .body(new ExchangeResponse(false, session.accessToken()));
    }

    @PostMapping("/complete-profile")
    public ResponseEntity<CompletionResponse> complete(
        @CookieValue(value="oauthOnboarding",required=false) String token,
        @Valid @RequestBody CompleteProfile request
    ) {
        token = Optional.ofNullable(token).filter(v -> !v.isBlank())
            .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED));
        var result = oauth.complete(token, request.toCommand());
        var session = authentication.socialSession(result.memberId());
        return ResponseEntity.status(201)
            .header(HttpHeaders.SET_COOKIE, MemberCookies.onboarding("", Duration.ZERO, jwt).toString())
            .header(HttpHeaders.SET_COOKIE, MemberCookies.refresh(session.refreshToken(), jwt).toString())
            .body(new CompletionResponse(result.memberId(), result.email(), MemberRole.USER, session.accessToken(), session.provider()));
    }
}
```
