import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

public class PortBinary {
    static final Map<String, byte[]> target = new HashMap<>();
    static final Map<String, List<String>> simpleNames = new HashMap<>();
    static final Map<String, String> fields = new HashMap<>();
    static final Map<String, String> moves = new TreeMap<>();
    static final String HOOKS = "moze_intel/projecte/fabric/PortHooks";

    static String move(String name) {
        if (target.containsKey(name) || !(name.startsWith("net/minecraft/") || name.startsWith("com/mojang/blaze3d/"))) return name;
        List<String> candidates = simpleNames.getOrDefault(name.substring(name.lastIndexOf('/') + 1), List.of());
        if (candidates.size() == 1) { moves.put(name, candidates.getFirst()); return candidates.getFirst(); }
        return name;
    }

    public static void main(String[] args) throws Exception {
        try (var paths = Files.walk(Path.of(args[1]))) {
            for (Path path : paths.filter(path -> path.toString().endsWith(".jar") && !path.toString().contains("bundle")).toList()) {
                try (JarFile jar = new JarFile(path.toFile(), false)) {
                    for (JarEntry entry : Collections.list(jar.entries())) {
                        if (!entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/versions/")) continue;
                        byte[] bytes = jar.getInputStream(entry).readAllBytes();
                        ClassReader reader = new ClassReader(bytes);
                        target.put(reader.getClassName(), bytes);
                    }
                }
            }
        }
        target.forEach((name, bytes) -> {
            simpleNames.computeIfAbsent(name.substring(name.lastIndexOf('/') + 1), ignored -> new ArrayList<>()).add(name);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                public FieldVisitor visitField(int access, String fieldName, String descriptor, String signature, Object value) { fields.put(name + "." + fieldName, descriptor); return null; }
            }, ClassReader.SKIP_CODE);
        });
        Remapper remapper = new Remapper(Opcodes.ASM9) { public String map(String name) { return move(name); } };
        try (JarFile original = new JarFile(args[0], false); ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(Path.of(args[2])))) {
            for (JarEntry entry : Collections.list(original.entries())) {
                if (entry.isDirectory()) continue;
                byte[] bytes = original.getInputStream(entry).readAllBytes();
                if (entry.getName().startsWith("moze_intel/") && entry.getName().endsWith(".class")) {
                    if (entry.getName().equals("moze_intel/projecte/gameObjs/registries/PEBlockTypes.class") || entry.getName().equals("moze_intel/projecte/gameObjs/registration/impl/BlockTypeDeferredRegister.class")) continue;
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    ClassVisitor adapter = new ClassVisitor(Opcodes.ASM9, writer) {
                        public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                            if (descriptor.contains("FuelValues$Builder")) return null;
                            if (method.equals("codec") && (entry.getName().endsWith("/MatterFurnace.class") || entry.getName().endsWith("/TransmutationStone.class"))) return null;
                            MethodVisitor delegate = super.visitMethod(access, method, descriptor, signature, exceptions);
                            return new MethodVisitor(Opcodes.ASM9, delegate) {
                                boolean skipping = false;
                                public void visitLdcInsn(Object value) { super.visitLdcInsn("pregenerated_emc.json".equals(value) ? "pregenerated_emc-26.3-r5.json" : value); }
                                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                                    if (owner.equals("net/fabricmc/fabric/api/registry/FuelValueEvents") && name.equals("BUILD")) {
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "initialize", "()V", false); skipping = true; return;
                                    }
                                    if (owner.endsWith("/PEBlockTypes") && name.equals("BLOCK_TYPES")) { skipping = true; return; }
                                    if (skipping) return;
                                    if (owner.equals("net/minecraft/world/entity/EntityType") && !fields.containsKey(owner + "." + name)) owner = "net/minecraft/world/entity/EntityTypes";
                                    if (owner.equals("net/minecraft/world/level/material/PushReaction") && name.equals("DESTROY")) name = "POPPED";
                                    if ((owner.equals("net/minecraft/world/item/Items") || owner.equals("net/minecraft/world/level/block/Blocks")) && (!fields.containsKey(owner + "." + name) || fields.get(owner + "." + name).contains("Collection"))) {
                                        boolean item = owner.endsWith("/Items");
                                        super.visitLdcInsn(name.toLowerCase(java.util.Locale.ROOT));
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, item ? "item" : "block", "(Ljava/lang/String;)" + (item ? "Lnet/minecraft/world/item/Item;" : "Lnet/minecraft/world/level/block/Block;"), false); return;
                                    }
                                    if (owner.equals("net/minecraft/tags/ItemTags") && name.equals("SMALL_FLOWERS")) {
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "smallFlowers", "()Lnet/minecraft/tags/TagKey;", false); return;
                                    }
                                    if (owner.equals("net/minecraft/world/entity/player/Player") && name.equals("swinging")) {
                                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "isSwinging", "()Z", false); return;
                                    }
                                    if (owner.equals("net/minecraft/client/Options") && name.equals("hideGui")) {
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "moze_intel/projecte/fabric/ClientPortHooks", "hideGui", "(Lnet/minecraft/client/Options;)Z", false); return;
                                    }
                                    String actual = fields.getOrDefault(owner + "." + name, desc);
                                    super.visitFieldInsn(opcode, owner, name, actual);
                                    if (desc.equals("Lnet/minecraft/sounds/SoundEvent;") && !actual.equals(desc)) {
                                        super.visitMethodInsn(Opcodes.INVOKEINTERFACE, "net/minecraft/core/Holder", "value", "()Ljava/lang/Object;", true);
                                        super.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/sounds/SoundEvent");
                                    }
                                }
                                public void visitInvokeDynamicInsn(String name, String desc, Handle bootstrap, Object... arguments) { if (!skipping) super.visitInvokeDynamicInsn(name, desc, bootstrap, arguments); }
                                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                                    if (skipping) { if (name.equals("register")) skipping = false; return; }
                                    if (entry.getName().endsWith("/EMCMappingHandler.class") && method.equals("mapInternal")
                                        && owner.equals("moze_intel/projecte/emc/generator/BigFractionToLongGenerator") && name.equals("<init>")) {
                                        super.visitVarInsn(Opcodes.ALOAD, 0);
                                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/server/ReloadableServerResources", "getRecipeManager", "()Lnet/minecraft/world/item/crafting/RecipeManager;", false);
                                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/crafting/RecipeManager", "getRecipes", "()Ljava/util/Collection;", false);
                                        desc = desc.replace(")V", "Ljava/util/Collection;)V");
                                    }
                                    if (entry.getName().endsWith("/BaseRecipeTypeMapper.class") && owner.endsWith("/RecipeCompat") && name.equals("getIngredients")) name = "getMappingIngredients";
                                    if (owner.equals("moze_intel/projecte/utils/ToolHelper") && name.equals("digBasedOnMode")) owner = "moze_intel/projecte/fabric/ToolActions";
                                    if (owner.equals("com/mojang/blaze3d/vertex/PoseStack") && name.equals("mulPose") && desc.equals("(Lorg/joml/Quaternionfc;)V")) name = "rotate";
                                    if (owner.equals("net/minecraft/core/BlockPos") && name.equals("getCenter")) {
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/world/phys/Vec3", "atCenterOf", "(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;", false); return;
                                    }
                                    if (owner.endsWith("/BundleContents") && name.equals("itemCopyStream")) name = "itemCopies";
                                    if (owner.equals("net/minecraft/world/level/Level") && name.equals("fuelValues")) {
                                        super.visitInsn(Opcodes.POP); super.visitVarInsn(Opcodes.ALOAD, 0); return;
                                    }
                                    if (owner.endsWith("/FuelValues") && name.equals("burnDuration")) {
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "burnDuration", "(Lmoze_intel/projecte/gameObjs/block_entities/DMFurnaceBlockEntity;Lnet/minecraft/world/item/ItemStack;)I", false); return;
                                    }
                                    if (owner.endsWith("/ValueInput") && name.equals("keySet")) {
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "keys", "(Lnet/minecraft/world/level/storage/ValueInput;)Ljava/util/Collection;", false); return;
                                    }
                                    if (owner.endsWith("/SubmitNodeCollector") && name.equals("submitModel") && desc.contains("CrumblingOverlay")) {
                                        super.visitInsn(Opcodes.POP);
                                        desc = desc.replace("Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;", "");
                                    }
                                    if (owner.endsWith("/CampfireBlock") && name.equals("dowse")) {
                                        name = "douse";
                                    }
                                    if (owner.equals("net/minecraft/world/level/block/BonemealableBlock") && (name.equals("performBonemeal") || name.equals("isBonemealSuccess") || name.equals("isValidBonemealTarget"))) {
                                        super.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/level/block/BonemealSource", "INTERACTION", "Lnet/minecraft/world/level/block/BonemealSource;");
                                        desc = desc.replace(")", "Lnet/minecraft/world/level/block/BonemealSource;)");
                                    }
                                    if (owner.endsWith("/LivingEntity") && name.equals("knockback") && desc.equals("(DDD)V")) {
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "knockback", "(Lnet/minecraft/world/entity/LivingEntity;DDD)V", false); return;
                                    }
                                    if (owner.endsWith("/SignBlockEntity") && (name.equals("getText") || name.equals("setText"))) {
                                        String arguments = name.equals("getText") ? "Z)Lnet/minecraft/world/level/block/entity/SignText;" : "Lnet/minecraft/world/level/block/entity/SignText;Z)Z";
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, name, "(Lnet/minecraft/world/level/block/entity/SignBlockEntity;" + arguments, false); return;
                                    }
                                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                                }
                            };
                        }
                    };
                    new ClassReader(bytes).accept(new ClassRemapper(adapter, remapper), 0);
                    bytes = writer.toByteArray();
                }
                output.putNextEntry(new ZipEntry(entry.getName())); output.write(bytes); output.closeEntry();
            }
        }
        moves.forEach((before, after) -> System.out.println(before + " -> " + after));
    }
}
