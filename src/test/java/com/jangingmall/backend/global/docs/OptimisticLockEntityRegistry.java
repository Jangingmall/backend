package com.jangingmall.backend.global.docs;

import org.springframework.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * 빌드 시점에 @Version 필드를 가진 @Entity 클래스 내부명 집합을 구성한다.
 *
 * <p>예: "com/jangingmall/backend/payment/domain/SellerApplication"
 * 이 집합을 OptimisticLockDetector가 소비해 CONCURRENT_UPDATE 신호를 생성한다.
 */
class OptimisticLockEntityRegistry {

    private static final String VERSION_DESC = "Ljakarta/persistence/Version;";
    private static final String ENTITY_DESC = "Ljakarta/persistence/Entity;";

    private final Path classesRoot;

    OptimisticLockEntityRegistry(Path classesRoot) {
        this.classesRoot = classesRoot;
    }

    /**
     * packagePrefix 하위에서 @Entity + @Version 필드를 모두 가진 클래스의 내부명 집합을 반환한다.
     */
    Set<String> scan(String packagePrefix) throws IOException {
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
                if (relative.contains("$")) return FileVisitResult.CONTINUE;

                EntityVersionScanner scanner = new EntityVersionScanner();
                try (InputStream in = Files.newInputStream(file)) {
                    new ClassReader(in).accept(scanner, ClassReader.SKIP_DEBUG);
                }
                if (scanner.isVersionedEntity()) {
                    result.add(relative);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    private static class EntityVersionScanner extends ClassVisitor {
        private boolean hasEntity = false;
        private boolean hasVersion = false;
        private String className;

        EntityVersionScanner() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.className = name;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (ENTITY_DESC.equals(descriptor)) hasEntity = true;
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            return new FieldVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (VERSION_DESC.equals(desc)) hasVersion = true;
                    return null;
                }
            };
        }

        boolean isVersionedEntity() {
            return hasEntity && hasVersion;
        }
    }
}
