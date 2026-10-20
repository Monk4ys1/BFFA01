package infinode.org.example.bFFA01.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin jar is compiled against Paper 1.20.4 and must still link on Spigot.
 * Version-specific types stay off the constant pool.
 */
class BytecodeCompatibilityTest {

    private static final List<String> FORBIDDEN_TYPES = List.of(
            "io/papermc/",
            "com/destroystokyo/",
            "net/minecraft/",
            "org/bukkit/craftbukkit/"
    );

    @Test
    void classesStayOnJava17WithoutServerSpecificLinks() throws IOException {
        Path classes = Path.of("target", "classes");
        assertTrue(Files.isDirectory(classes), "compile before scanning bytecode");
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(classes)) {
            files.filter(path -> path.toString().endsWith(".class")).forEach(path -> scan(path, problems));
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static void scan(Path path, List<String> problems) {
        try (InputStream input = Files.newInputStream(path)) {
            ClassReader reader = new ClassReader(input);
            String label = path.toString();
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    if ((version & 0xFFFF) > 61) {
                        problems.add(label + " class version " + (version & 0xFFFF));
                    }
                    check(label, superName, problems);
                    if (interfaces != null) {
                        for (String iface : interfaces) {
                            check(label, iface, problems);
                        }
                    }
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    check(label, descriptor, problems);
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    check(label, descriptor, problems);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String field, String fieldDescriptor) {
                            if (owner.equals("org/bukkit/enchantments/Enchantment")
                                    || owner.equals("org/bukkit/potion/PotionEffectType")
                                    || owner.equals("org/bukkit/Sound")
                                    || owner.equals("org/bukkit/attribute/Attribute")) {
                                problems.add(label + " field " + owner + "." + field);
                            }
                            check(label, owner, problems);
                            check(label, fieldDescriptor, problems);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method, String methodDescriptor, boolean isInterface) {
                            if (owner.equals("org/bukkit/inventory/InventoryView")) {
                                problems.add(label + " InventoryView." + method);
                            }
                            if (owner.equals("org/bukkit/Bukkit") && (method.equals("getGlobalRegionScheduler")
                                    || method.equals("getRegionScheduler") || method.equals("getAsyncScheduler"))) {
                                problems.add(label + " Bukkit." + method);
                            }
                            if ("getScheduler".equals(method) && owner.startsWith("org/bukkit/entity/")) {
                                problems.add(label + " " + owner + ".getScheduler");
                            }
                            check(label, owner, problems);
                            check(label, methodDescriptor, problems);
                        }

                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            check(label, type, problems);
                        }
                    };
                }
            }, ClassReader.SKIP_FRAMES);
        } catch (IOException ex) {
            problems.add(path + " " + ex.getMessage());
        }
    }

    private static void check(String label, String value, List<String> problems) {
        if (value == null) {
            return;
        }
        for (String forbidden : FORBIDDEN_TYPES) {
            if (value.contains(forbidden)) {
                problems.add(label + " references " + forbidden);
            }
        }
    }
}
