package com.hpfxd.spectatorplus.fabric.sync;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class CustomPacketCodecs {
    /**
     * Maximum number of items allowed in a single items sync packet.
     * Vanilla player inventory has 41 slots; 128 provides generous headroom
     * while preventing malicious packets from causing OutOfMemoryError.
     */
    private static final int MAX_ITEMS = 128;

    private CustomPacketCodecs() {
    }

    public static ItemStack[] readItems(RegistryFriendlyByteBuf buf) {
        final int len = buf.readInt();
        if (len < 0 || len > MAX_ITEMS) {
            throw new DecoderException("Item array length " + len + " is outside allowed range [0, " + MAX_ITEMS + "]");
        }
        final ItemStack[] items = new ItemStack[len];

        for (int slot = 0; slot < len; slot++) {
            items[slot] = buf.readBoolean() ? CustomPacketCodecs.readItem(buf) : null;
        }

        return items;
    }

    public static void writeItems(RegistryFriendlyByteBuf buf, ItemStack[] items) {
        buf.writeInt(items.length);

        for (final ItemStack item : items) {
            buf.writeBoolean(item != null);
            if (item != null) {
                CustomPacketCodecs.writeItem(buf, item);
            }
        }
    }

    public static ItemStack readItem(RegistryFriendlyByteBuf buf) {
        try {
            int length = buf.readInt();
            if (length == 0) {
                return ItemStack.EMPTY;
            }
            byte[] bytes = new byte[length];
            buf.readBytes(bytes);
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            CustomPacketCodecs.normalizeLegacyItemComponents(tag);
            return ItemStack.OPTIONAL_CODEC.parse(buf.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow(DecoderException::new);
        } catch (Exception e) {
            throw new DecoderException("Failed to read ItemStack", e);
        }
    }

    private static void normalizeLegacyItemComponents(CompoundTag itemTag) {
        itemTag.getCompound("components").ifPresent(components -> {
            // Compatibility with SpectatorPlus packets sent by older 1.20.6 servers.
            // Newer clients no longer know some legacy tooltip item components.
            components.remove("minecraft:hide_additional_tooltip");

            CustomPacketCodecs.normalizeLegacyTextComponent(components, "minecraft:custom_name");
            CustomPacketCodecs.normalizeLegacyTextComponent(components, "minecraft:item_name");
            CustomPacketCodecs.normalizeLegacyLoreComponent(components);

            if (components.contains("minecraft:hide_tooltip")) {
                components.remove("minecraft:hide_tooltip");
            }
        });
    }

    private static void normalizeLegacyTextComponent(CompoundTag components, String key) {
        Tag tag = components.get(key);
        Tag normalized = CustomPacketCodecs.normalizeLegacyTextComponentTag(tag);
        if (normalized != tag) {
            components.put(key, normalized);
        }
    }

    private static void normalizeLegacyLoreComponent(CompoundTag components) {
        components.getList("minecraft:lore").ifPresent(lore -> {
            for (int i = 0; i < lore.size(); i++) {
                Tag tag = lore.get(i);
                Tag normalized = CustomPacketCodecs.normalizeLegacyTextComponentTag(tag);
                if (normalized != tag) {
                    lore.set(i, normalized);
                }
            }
        });
    }

    private static Tag normalizeLegacyTextComponentTag(Tag tag) {
        if (!(tag instanceof StringTag stringTag)) {
            return tag;
        }

        String value = stringTag.value().trim();
        if (!value.startsWith("{") && !value.startsWith("[") && !value.startsWith("\"")) {
            return tag;
        }

        try {
            return JsonOps.INSTANCE.convertTo(
                NbtOps.INSTANCE,
                JsonParser.parseString(value)
            );
        } catch (RuntimeException ignored) {
            return tag;
        }
    }

    public static void writeItem(RegistryFriendlyByteBuf buf, @NotNull ItemStack item) {
        try {
            if (item.isEmpty()) {
                buf.writeInt(0);
                return;
            }
            Tag tag = ItemStack.OPTIONAL_CODEC.encodeStart(buf.registryAccess().createSerializationContext(NbtOps.INSTANCE), item).getOrThrow(EncoderException::new);
            if (!(tag instanceof CompoundTag)) {
                buf.writeInt(0);
                return;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed((CompoundTag)tag, out);
            byte[] bytes = out.toByteArray();
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        } catch (Exception e) {
            throw new EncoderException("Failed to write ItemStack", e);
        }
    }
}
