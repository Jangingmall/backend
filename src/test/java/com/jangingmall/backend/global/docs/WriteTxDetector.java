package com.jangingmall.backend.global.docs;

import org.springframework.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * ASM 기반 쓰기 트랜잭션 탐지기.
 *
 * <p>@Transactional(readOnly=false) 또는 @Transactional(default) 선언된 메서드를
 * 쓰기 경로로 판단한다. dirty-checking 방식의 암시적 쓰기도 커버된다.
 *
 * <p>탐지 기준:
 * - @Transactional 존재 + readOnly 속성이 false(=기본값) → 쓰기 tx
 * - readOnly=true 명시 → 읽기 tx, 쓰기 신호 없음
 * - 메서드 단위만 스캔 (클래스 레벨 @Transactional은 과-태깅 허용)
 */
class WriteTxDetector {

    private static final String TRANSACTIONAL_DESC =
        "Lorg/springframework/transaction/annotation/Transactional;";

    private final Path classesRoot;

    WriteTxDetector(Path classesRoot) {
        this.classesRoot = classesRoot;
    }

    /**
     * owner/name/descriptor 메서드가 write-tx 경로인지 판단한다.
     *
     * <p>판단 방법: callee 체인(depth≤5)을 따라가며 쓰기 트랜잭션 선언을 탐지.
     * 탐지 실패(클래스 없음 등)는 false 반환 — 과-태깅 방향이므로 안전.
     */
    boolean isWritePath(String owner, String methodName, String descriptor, int depth) throws IOException {
        if (depth > 5) return false;
        if (!owner.startsWith("com/jangingmall/backend")) return false;

        Path classFile = classesRoot.resolve(owner + ".class");
        if (!Files.exists(classFile)) return false;

        MethodTransactionalScanner scanner = new MethodTransactionalScanner(methodName, descriptor);
        try (InputStream in = Files.newInputStream(classFile)) {
            new ClassReader(in).accept(scanner, ClassReader.SKIP_DEBUG);
        }
        return scanner.isWrite();
    }

    /**
     * 패키지 하위 전체 클래스에서 쓰기 tx 메서드 집합을 반환한다.
     * key: "owner#name#descriptor"
     */
    Set<String> scanWriteMethods(String packagePrefix) throws IOException {
        Set<String> result = new HashSet<>();
        String packagePath = packagePrefix.replace('.', '/');

        Files.walkFileTree(classesRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".class")) return FileVisitResult.CONTINUE;
                String relative = classesRoot.relativize(file).toString()
                    .replace(FileSystems.getDefault().getSeparator(), "/")
                    .replace(".class", "");
                if (!relative.startsWith(packagePath)) return FileVisitResult.CONTINUE;

                AllWriteMethodScanner scanner = new AllWriteMethodScanner(relative);
                try (InputStream in = Files.newInputStream(file)) {
                    new ClassReader(in).accept(scanner, ClassReader.SKIP_DEBUG);
                }
                result.addAll(scanner.writeMethods());
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    private static class MethodTransactionalScanner extends ClassVisitor {
        private final String targetMethod;
        private final String targetDescriptor;
        private boolean write = false;

        MethodTransactionalScanner(String targetMethod, String targetDescriptor) {
            super(Opcodes.ASM9);
            this.targetMethod = targetMethod;
            this.targetDescriptor = targetDescriptor;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if (!name.equals(targetMethod) || !descriptor.equals(targetDescriptor)) return null;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (!TRANSACTIONAL_DESC.equals(desc)) return null;
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        boolean readOnly = false;

                        @Override
                        public void visit(String name, Object value) {
                            if ("readOnly".equals(name) && Boolean.TRUE.equals(value)) {
                                readOnly = true;
                            }
                        }

                        @Override
                        public void visitEnd() {
                            if (!readOnly) write = true;
                        }
                    };
                }
            };
        }

        boolean isWrite() {
            return write;
        }
    }

    private static class AllWriteMethodScanner extends ClassVisitor {
        private final String owner;
        private final List<String> writeMethods = new ArrayList<>();

        AllWriteMethodScanner(String owner) {
            super(Opcodes.ASM9);
            this.owner = owner;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (!TRANSACTIONAL_DESC.equals(desc)) return null;
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        boolean readOnly = false;

                        @Override
                        public void visit(String attrName, Object value) {
                            if ("readOnly".equals(attrName) && Boolean.TRUE.equals(value)) {
                                readOnly = true;
                            }
                        }

                        @Override
                        public void visitEnd() {
                            if (!readOnly) {
                                writeMethods.add(owner + "#" + name + "#" + descriptor);
                            }
                        }
                    };
                }
            };
        }

        List<String> writeMethods() {
            return writeMethods;
        }
    }
}
