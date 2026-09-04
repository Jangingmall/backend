package com.jangingmall.backend.global.docs;

import com.jangingmall.backend.global.exception.ErrorCode;
import org.springframework.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * 빌드타임 ASM 바이트코드 스캐너.
 *
 * <p>호출 그래프:
 * Controller method → Service method → (private helper / Entity method)
 *   각 단계에서 throw된 BusinessException 서브클래스를 탐지하고
 *   "endpoint → ErrorCode set" 맵을 생성한다.
 *
 * <p>탐지 범위:
 * - NEW + INVOKESPECIAL(BusinessException 서브클래스 생성자) → 직접 throw
 * - 메서드 호출 → callee 바이트코드 재귀 분석 (depth 제한: 5)
 * - @ApiErrorCodes 애노테이션 — ASM 탐지 불가 에러코드 수동 보충 후 병합
 *
 * <p>탐지 불가 → @ApiErrorCodes 로 보충:
 * - Security 필터 예외 (UNAUTHORIZED, FORBIDDEN via Security)
 * - DataIntegrityViolationException → CONFLICT
 * - ObjectOptimisticLockingFailureException → CONCURRENT_UPDATE
 * - CannotAcquireLockException → CONCURRENT_UPDATE
 */
public class ErrorCodeScanner {

    private static final int MAX_DEPTH = 5;

    private static final String BUSINESS_EXCEPTION_INTERNAL = "com/jangingmall/backend/global/exception/BusinessException";
    private static final Set<String> BUSINESS_EXCEPTION_SUBCLASSES = Set.of(
        "com/jangingmall/backend/global/exception/NotFoundException",
        "com/jangingmall/backend/global/exception/ConflictException",
        "com/jangingmall/backend/global/exception/ForbiddenException",
        "com/jangingmall/backend/global/exception/ConcurrentUpdateException",
        "com/jangingmall/backend/global/exception/BusinessRuleViolationException"
    );

    // 예외 클래스명 → ErrorCode enum 상수 이름 매핑
    private static final Map<String, String> CLASS_TO_ERROR_CODE = Map.of(
        "com/jangingmall/backend/global/exception/NotFoundException", "NOT_FOUND",
        "com/jangingmall/backend/global/exception/ConflictException", "CONFLICT",
        "com/jangingmall/backend/global/exception/ForbiddenException", "FORBIDDEN",
        "com/jangingmall/backend/global/exception/ConcurrentUpdateException", "CONCURRENT_UPDATE",
        "com/jangingmall/backend/global/exception/BusinessRuleViolationException", "BUSINESS_RULE_VIOLATION"
    );

    private final Path classesRoot;
    // 메서드 단위 캐시: "owner#name#desc" → 탐지된 에러코드들
    private final Map<String, Set<String>> methodCache = new HashMap<>();

    public ErrorCodeScanner(Path classesRoot) {
        this.classesRoot = classesRoot;
    }

    /**
     * 주어진 패키지 접두사 하위의 모든 Controller 클래스를 스캔하여
     * "GET /api/notifications/{id}" → {"NOT_FOUND", "FORBIDDEN"} 형태의 맵을 반환한다.
     *
     * <p>ASM 탐지 결과에 @ApiErrorCodes 선언 값을 병합한다.
     */
    public Map<String, Set<String>> scan(String packagePrefix) throws IOException {
        Map<String, Set<String>> asmResults = scanAsmOnly(packagePrefix);
        Map<String, Set<String>> annotationResults = scanAnnotationsOnly(packagePrefix);

        Map<String, Set<String>> result = new LinkedHashMap<>(asmResults);
        for (Map.Entry<String, Set<String>> entry : annotationResults.entrySet()) {
            result.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>())
                .addAll(entry.getValue());
        }
        return result;
    }

    /**
     * ASM 바이트코드 분석만으로 탐지된 에러코드 맵을 반환한다 (@ApiErrorCodes 미포함).
     * CoverageVerifier의 A 조건에 해당한다.
     */
    public Map<String, Set<String>> scanAsmOnly(String packagePrefix) throws IOException {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        walkControllers(packagePrefix, (endpoint, ref) -> {
            Set<String> errorCodes = new LinkedHashSet<>(
                analyzeMethod(ref.owner(), ref.name(), ref.descriptor(), 0)
            );
            if (!errorCodes.isEmpty()) {
                result.put(endpoint, errorCodes);
            }
        });
        return result;
    }

    /**
     * @ApiErrorCodes 애노테이션 선언만으로 구성된 에러코드 맵을 반환한다 (ASM 미포함).
     * CoverageVerifier의 수동 보충 경로에 해당한다.
     */
    public Map<String, Set<String>> scanAnnotationsOnly(String packagePrefix) throws IOException {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        walkControllers(packagePrefix, (endpoint, ref) -> {
            Set<String> annotationCodes = resolveApiErrorCodes(ref);
            if (!annotationCodes.isEmpty()) {
                result.put(endpoint, annotationCodes);
            }
        });
        return result;
    }

    @FunctionalInterface
    private interface EndpointConsumer {
        void accept(String endpoint, MethodRef ref) throws IOException;
    }

    private void walkControllers(String packagePrefix, EndpointConsumer consumer) throws IOException {
        String packagePath = packagePrefix.replace('.', '/');
        Files.walkFileTree(classesRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".class")) return FileVisitResult.CONTINUE;

                String relative = classesRoot.relativize(file).toString()
                    .replace(FileSystems.getDefault().getSeparator(), "/")
                    .replace(".class", "");

                if (!relative.startsWith(packagePath)) return FileVisitResult.CONTINUE;
                if (relative.contains("$")) return FileVisitResult.CONTINUE;

                ControllerScanner scanner = new ControllerScanner();
                try (InputStream in = Files.newInputStream(file)) {
                    new ClassReader(in).accept(scanner, ClassReader.SKIP_DEBUG);
                }

                for (Map.Entry<String, MethodRef> entry : scanner.endpointMethods().entrySet()) {
                    consumer.accept(entry.getKey(), entry.getValue());
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 런타임 리플렉션으로 @ApiErrorCodes 애노테이션 값을 읽어 ErrorCode 이름 Set으로 반환한다.
     * 클래스 로드 실패 시 빈 Set을 반환해 스캔을 중단하지 않는다.
     */
    private Set<String> resolveApiErrorCodes(MethodRef ref) {
        String className = ref.owner().replace('/', '.');
        try {
            Class<?> clazz = Class.forName(className);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.getName().equals(ref.name())) continue;
                ApiErrorCodes annotation = method.getAnnotation(ApiErrorCodes.class);
                if (annotation == null) continue;
                Set<String> codes = new LinkedHashSet<>();
                for (ErrorCode code : annotation.value()) {
                    codes.add(code.name());
                }
                return codes;
            }
        } catch (ClassNotFoundException ignored) {
        }
        return Set.of();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 메서드 재귀 분석
    // ──────────────────────────────────────────────────────────────────────────

    private Set<String> analyzeMethod(String owner, String name, String descriptor, int depth) throws IOException {
        if (depth > MAX_DEPTH) return Set.of();

        String cacheKey = owner + "#" + name + "#" + descriptor;
        if (methodCache.containsKey(cacheKey)) return methodCache.get(cacheKey);

        // 순환 방지용 임시 마킹
        methodCache.put(cacheKey, new HashSet<>());

        Path classFile = classesRoot.resolve(owner + ".class");
        if (!Files.exists(classFile)) return Set.of();

        MethodAnalyzer analyzer = new MethodAnalyzer(name, descriptor, depth);
        try (InputStream in = Files.newInputStream(classFile)) {
            new ClassReader(in).accept(analyzer, ClassReader.SKIP_DEBUG);
        }

        Set<String> found = analyzer.errorCodes();
        methodCache.put(cacheKey, found);
        return found;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ClassVisitor: Controller 클래스에서 @RequestMapping 메서드 추출
    // ──────────────────────────────────────────────────────────────────────────

    private class ControllerScanner extends ClassVisitor {

        private static final String GET_MAPPING = "Lorg/springframework/web/bind/annotation/GetMapping;";
        private static final String POST_MAPPING = "Lorg/springframework/web/bind/annotation/PostMapping;";
        private static final String PUT_MAPPING = "Lorg/springframework/web/bind/annotation/PutMapping;";
        private static final String PATCH_MAPPING = "Lorg/springframework/web/bind/annotation/PatchMapping;";
        private static final String DELETE_MAPPING = "Lorg/springframework/web/bind/annotation/DeleteMapping;";
        private static final String REQUEST_MAPPING = "Lorg/springframework/web/bind/annotation/RequestMapping;";

        private static final Map<String, String> MAPPING_HTTP_METHODS = Map.of(
            GET_MAPPING, "GET",
            POST_MAPPING, "POST",
            PUT_MAPPING, "PUT",
            PATCH_MAPPING, "PATCH",
            DELETE_MAPPING, "DELETE"
        );

        private String className;
        private String classBasePath = "";
        private final Map<String, MethodRef> endpoints = new LinkedHashMap<>();

        ControllerScanner() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (REQUEST_MAPPING.equals(descriptor)) {
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        if ("value".equals(name) || "path".equals(name)) {
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String name, Object value) {
                                    classBasePath = (String) value;
                                }
                            };
                        }
                        return super.visitArray(name);
                    }
                };
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
                private String httpMethod;
                private String methodPath = "";

                @Override
                public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                    String http = MAPPING_HTTP_METHODS.get(annotationDescriptor);
                    if (http != null) {
                        httpMethod = http;
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public AnnotationVisitor visitArray(String name) {
                                if ("value".equals(name) || "path".equals(name)) {
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override
                                        public void visit(String name, Object value) {
                                            methodPath = (String) value;
                                        }
                                    };
                                }
                                return super.visitArray(name);
                            }
                        };
                    }
                    return null;
                }

                @Override
                public void visitEnd() {
                    if (httpMethod != null) {
                        String fullPath = classBasePath + methodPath;
                        String endpointKey = httpMethod + " " + fullPath;
                        endpoints.put(endpointKey, new MethodRef(className, methodName, descriptor));
                    }
                }
            };
        }

        Map<String, MethodRef> endpointMethods() {
            return endpoints;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MethodVisitor: 특정 메서드 내에서 에러코드 탐지 (재귀)
    // ──────────────────────────────────────────────────────────────────────────

    private class MethodAnalyzer extends ClassVisitor {

        private final String targetMethod;
        private final String targetDescriptor;
        private final int depth;
        private final Set<String> found = new LinkedHashSet<>();

        MethodAnalyzer(String targetMethod, String targetDescriptor, int depth) {
            super(Opcodes.ASM9);
            this.targetMethod = targetMethod;
            this.targetDescriptor = targetDescriptor;
            this.depth = depth;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (!name.equals(targetMethod) || !descriptor.equals(targetDescriptor)) return null;

            return new MethodVisitor(Opcodes.ASM9) {
                private String lastNewClass;

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    if (opcode == Opcodes.NEW && BUSINESS_EXCEPTION_SUBCLASSES.contains(type)) {
                        lastNewClass = type;
                    }
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String mname, String mdesc, boolean itf) {
                    // 직접 new XxxException() 생성자 호출
                    if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(mname) && lastNewClass != null) {
                        String errorCode = CLASS_TO_ERROR_CODE.get(lastNewClass);
                        if (errorCode != null) found.add(errorCode);
                        lastNewClass = null;
                        return;
                    }
                    lastNewClass = null;

                    // 메서드 참조 (method reference로 넘기는 경우: orElseThrow(NotFoundException::new))
                    // INVOKEDYNAMIC은 별도 처리가 필요하나, 여기서는 INVOKESPECIAL <init> 패턴으로 충분

                    // 호출된 메서드를 재귀 분석
                    boolean isApplicationCode = owner.startsWith("com/jangingmall/backend");
                    boolean isNotSelf = !(owner.contains("Controller") && mname.equals(targetMethod));
                    if (isApplicationCode && isNotSelf) {
                        try {
                            found.addAll(analyzeMethod(owner, mname, mdesc, depth + 1));
                        } catch (IOException ignored) {
                        }
                    }
                }

                @Override
                public void visitInvokeDynamicInsn(String name, String descriptor, Handle bsm, Object... bsmArgs) {
                    // method reference: NotFoundException::new → 생성자 핸들 추출
                    for (Object arg : bsmArgs) {
                        if (arg instanceof Handle handle) {
                            if ("<init>".equals(handle.getName()) && BUSINESS_EXCEPTION_SUBCLASSES.contains(handle.getOwner())) {
                                String errorCode = CLASS_TO_ERROR_CODE.get(handle.getOwner());
                                if (errorCode != null) found.add(errorCode);
                            }
                        }
                    }
                }
            };
        }

        Set<String> errorCodes() {
            return found;
        }
    }

    record MethodRef(String owner, String name, String descriptor) {}
}
