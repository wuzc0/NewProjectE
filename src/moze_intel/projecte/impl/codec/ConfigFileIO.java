package moze_intel.projecte.impl.codec;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import moze_intel.projecte.PECore;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;

public final class ConfigFileIO {
    private ConfigFileIO() {
    }

    public static <T> Optional<T> read(Provider registries, Path file, Codec<T> codec, String description) {
        if (!Files.exists(file)) return Optional.empty();
        try (var input = Files.newInputStream(file)) {
            int first = input.read();
            int second = input.read();
            if (first == 0x1f && second == 0x8b) {
                try (var compressed = Files.newInputStream(file)) {
                    var tag = NbtIo.readCompressed(compressed, NbtAccounter.unlimitedHeap());
                    return codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                        .ifError(error -> PECore.LOGGER.warn("读取 {} 失败: {}", description, error.message())).result();
                }
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.startsWith("\uFEFF")) content = content.substring(1);
            return codec.parse(registries.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(content))
                .ifError(error -> PECore.LOGGER.warn("读取 {} 失败: {}", description, error.message())).result();
        } catch (IOException | RuntimeException failure) {
            PECore.LOGGER.warn("无法读取 {} 文件 '{}': {}", description, file, failure.getMessage());
            return Optional.empty();
        }
    }

    public static <T> void write(Provider registries, Path file, Codec<T> codec, T value, String description) {
        Path temporary = null;
        try {
            var encoded = codec.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), value).getOrThrow();
            String content = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(encoded) + "\n";
            Path target = file.toAbsolutePath();
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("无法写入 " + description + " 文件 '" + file + "'", failure);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException failure) {
                    PECore.LOGGER.warn("无法清理配置临时文件 '{}'", temporary, failure);
                }
            }
        }
    }
}
