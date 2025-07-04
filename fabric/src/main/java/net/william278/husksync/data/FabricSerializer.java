/*
 * This file is part of HuskSync, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.husksync.data;

import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.item.ItemStack;
//#if MC==12001
//$$ import net.minecraft.nbt.NbtCompound;
//#endif
import net.minecraft.nbt.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.william278.desertwell.util.Version;
import net.william278.husksync.FabricHuskSync;
import net.william278.husksync.HuskSync;
import net.william278.husksync.api.HuskSyncAPI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.william278.husksync.data.Data.Items.Inventory.*;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class FabricSerializer {

    @ApiStatus.Internal
    protected final HuskSync plugin;

    @SuppressWarnings("unused")
    public FabricSerializer(@NotNull HuskSyncAPI api) {
        this.plugin = api.getPlugin();
    }

    @ApiStatus.Internal
    @NotNull
    public HuskSync getPlugin() {
        return plugin;
    }

    public static class Inventory extends FabricSerializer implements Serializer<FabricData.Items.Inventory>,
            ItemDeserializer {

        public Inventory(@NotNull HuskSync plugin) {
            super(plugin);
        }

        @Override
        public FabricData.Items.Inventory deserialize(@NotNull String serialized, @NotNull Version dataMcVersion)
                throws DeserializationException {
            // Read item NBT from string
            final FabricHuskSync plugin = (FabricHuskSync) getPlugin();
            final NbtCompound root;
            try {
                //#if MC<12105
                //$$ root = StringNbtReader.parse(serialized);
                //#else
                root = StringNbtReader.readCompound(serialized);
                //#endif
            } catch (Throwable e) {
                throw new DeserializationException("Failed to read item NBT from string (%s)".formatted(serialized), e);
            }

            // Deserialize the inventory data
            //#if MC<12105
            //$$ final NbtCompound items = root.contains(ITEMS_TAG) ? root.getCompound(ITEMS_TAG) : null;
            //$$ return FabricData.Items.Inventory.from(
            //$$     items != null ? getItems(items, dataMcVersion, plugin) : new ItemStack[INVENTORY_SLOT_COUNT],
            //$$     root.contains(HELD_ITEM_SLOT_TAG) ? root.getInt(HELD_ITEM_SLOT_TAG) : 0
            //$$ );
            //#else
            final NbtCompound items = root.contains(ITEMS_TAG) ? root.getCompoundOrEmpty(ITEMS_TAG) : null;
            return FabricData.Items.Inventory.from(
                items != null ? getItems(items, dataMcVersion, plugin) : new ItemStack[INVENTORY_SLOT_COUNT],
                root.getInt(HELD_ITEM_SLOT_TAG, 0)
            );
            //#endif
        }

        @Override
        public FabricData.Items.Inventory deserialize(@NotNull String serialized) {
            return deserialize(serialized, plugin.getMinecraftVersion());
        }

        @NotNull
        @Override
        public String serialize(@NotNull FabricData.Items.Inventory data) throws SerializationException {
            try {
                final NbtCompound root = new NbtCompound();
                root.put(ITEMS_TAG, serializeItemArray(data.getContents(), (FabricHuskSync) getPlugin()));
                root.putInt(HELD_ITEM_SLOT_TAG, data.getHeldItemSlot());
                return root.toString();
            } catch (Throwable e) {
                throw new SerializationException("Failed to serialize inventory item NBT to string", e);
            }
        }

    }

    public static class EnderChest extends FabricSerializer implements Serializer<FabricData.Items.EnderChest>,
            ItemDeserializer {

        public EnderChest(@NotNull HuskSync plugin) {
            super(plugin);
        }

        @Override
        public FabricData.Items.EnderChest deserialize(@NotNull String serialized, @NotNull Version dataMcVersion)
                throws DeserializationException {
            final FabricHuskSync plugin = (FabricHuskSync) getPlugin();
            try {
                //#if MC<12105
                //$$ final NbtCompound items = StringNbtReader.parse(serialized);
                //#else
                final NbtCompound items = StringNbtReader.readCompound(serialized);
                //#endif
                return FabricData.Items.EnderChest.adapt(getItems(items, dataMcVersion, plugin));
            } catch (Throwable e) {
                throw new DeserializationException("Failed to read item NBT from string (%s)".formatted(serialized), e);
            }
        }

        @Override
        public FabricData.Items.EnderChest deserialize(@NotNull String serialized) {
            return deserialize(serialized, plugin.getMinecraftVersion());
        }

        @NotNull
        @Override
        public String serialize(@NotNull FabricData.Items.EnderChest data) throws SerializationException {
            try {
                return serializeItemArray(data.getContents(), (FabricHuskSync) getPlugin()).toString();
            } catch (Throwable e) {
                throw new SerializationException("Failed to serialize ender chest item NBT to string", e);
            }
        }
    }

    private interface ItemDeserializer {

        @NotNull
        default ItemStack[] getItems(@NotNull NbtCompound tag, @NotNull Version mcVersion, @NotNull FabricHuskSync plugin) {
            try {
                if (mcVersion.compareTo(plugin.getMinecraftVersion()) < 0) {
                    return upgradeItemStacks(tag, mcVersion, plugin);
                }

                final DynamicRegistryManager registryManager = plugin.getMinecraftServer().getRegistryManager();
                //#if MC<12105
                //$$ final ItemStack[] contents = new ItemStack[tag.getInt("size")];
                //$$ final NbtList itemList = tag.getList("items", NbtElement.COMPOUND_TYPE);
                //$$ itemList.forEach(element -> {
                //$$     final NbtCompound compound = (NbtCompound) element;
                //$$     contents[compound.getInt("Slot")] = decodeNbt(element, registryManager);
                //$$ });
                //#else
                final ItemStack[] contents = new ItemStack[tag.getInt("size", 0)];
                final NbtList itemList = tag.getListOrEmpty("items");
                itemList.forEach(element -> {
                    final NbtCompound compound = (NbtCompound) element;
                    int i = compound.getInt("Slot", -1);
                    if (i >= 0) {
                        contents[i] = decodeNbt(element, registryManager);
                    }
                });
                //#endif

                return contents;
            } catch (Throwable e) {
                throw new Serializer.DeserializationException("Failed to read item NBT string (%s)".formatted(tag), e);
            }
        }

        // Serialize items slot-by-slot
        @NotNull
        default NbtCompound serializeItemArray(@Nullable ItemStack @NotNull [] items, @NotNull FabricHuskSync plugin) {
            final NbtCompound container = new NbtCompound();
            container.putInt("size", items.length);
            final NbtList itemList = new NbtList();
            final DynamicRegistryManager registryManager = plugin.getMinecraftServer().getRegistryManager();
            final List<ItemStack> skipped = new ArrayList<>();
            for (int i = 0; i < items.length; i++) {
                final ItemStack item = items[i];
                if (item == null || item.isEmpty() || item.getCount() < 1 || item.getCount() > 99) {
                    continue;
                }

                final NbtCompound entry = encodeNbt(item, registryManager);
                if (entry == null) {
                    skipped.add(item);
                    continue;
                }
                entry.putInt("Slot", i);
                itemList.add(entry);
            }
            if (!skipped.isEmpty()) {
                plugin.debug("Skipped serializing items in array: %s".formatted(Arrays.toString(skipped.toArray())));
            }
            container.put(ITEMS_TAG, itemList);
            return container;
        }

        @NotNull
        private ItemStack @NotNull [] upgradeItemStacks(@NotNull NbtCompound items, @NotNull Version mcVersion,
                                                        @NotNull FabricHuskSync plugin) {
            //#if MC<12105
            //$$ final int size = items.getInt("size");
            //$$ final NbtList list = items.getList("items", NbtElement.COMPOUND_TYPE);
            //$$ final ItemStack[] itemStacks = new ItemStack[size];
            //$$ final DynamicRegistryManager registryManager = plugin.getMinecraftServer().getRegistryManager();
            //$$ Arrays.fill(itemStacks, ItemStack.EMPTY);
            //$$ for (int i = 0; i < size; i++) {
            //$$     if (list.getCompound(i) == null) {
            //$$         continue;
            //$$     }
            //$$     final NbtCompound compound = list.getCompound(i);
            //$$     final int slot = compound.getInt("Slot");
            //$$     itemStacks[slot] = decodeNbt(upgradeItemData(list.getCompound(i), mcVersion, plugin), registryManager);
            //$$ }
            //#else
            final int size = items.getInt("size", 0);
            final NbtList list = items.getListOrEmpty("items");
            final ItemStack[] itemStacks = new ItemStack[size];
            final DynamicRegistryManager registryManager = plugin.getMinecraftServer().getRegistryManager();
            Arrays.fill(itemStacks, ItemStack.EMPTY);
            for (int i = 0; i < size; i++) {
                final NbtCompound compound = list.getCompoundOrEmpty(i);
                if (compound.isEmpty()) {
                    continue;
                }
                final int slot = compound.getInt("Slot", -1);
                if (slot >= 0) {
                    itemStacks[slot] = decodeNbt(upgradeItemData(compound, mcVersion, plugin), registryManager);
                }
            }
            //#endif
            return itemStacks;
        }

        @NotNull
        @SuppressWarnings({"rawtypes", "unchecked"}) // For NBTOps lookup
        private NbtCompound upgradeItemData(@NotNull NbtCompound tag, @NotNull Version mcVersion,
                                            @NotNull FabricHuskSync plugin) {
            return (NbtCompound) plugin.getMinecraftServer().getDataFixer().update(
                    TypeReferences.ITEM_STACK, new Dynamic<Object>((DynamicOps) NbtOps.INSTANCE, tag),
                    plugin.getDataVersion(mcVersion), plugin.getDataVersion(plugin.getMinecraftVersion())
            ).getValue();
        }

        @Nullable
        private NbtCompound encodeNbt(@NotNull ItemStack item, @NotNull DynamicRegistryManager reg) {
            try {
                //#if MC>=12107
                return (NbtCompound) ItemStack.CODEC.encodeStart(reg.getOps(NbtOps.INSTANCE), item).getOrThrow();
                //#elseif MC>=12104
                //$$ return (NbtCompound) item.toNbt(reg);
                //#elseif MC==12101
                //$$ return (NbtCompound) item.encode(reg);
                //#elseif MC==12001
                //$$ final NbtCompound compound = new NbtCompound();
                //$$ item.writeNbt(compound);
                //$$ return compound;
                //#endif
            } catch (Throwable e) {
                return null;
            }
        }

        @NotNull
        private ItemStack decodeNbt(@NotNull NbtElement item, @NotNull DynamicRegistryManager reg) {
            //#if MC>=12107
            final @Nullable ItemStack stack = ItemStack.CODEC.decode(reg.getOps(NbtOps.INSTANCE), item).getOrThrow().getFirst();
            //#elseif MC>12001
            //$$ final @Nullable ItemStack stack = ItemStack.fromNbt(reg, item).orElse(null);
            //#elseif MC==12001
            //$$ final @Nullable ItemStack stack = ItemStack.fromNbt((NbtCompound) item);
            //#endif
            if (stack == null) {
                throw new IllegalStateException("Failed to decode item NBT (decode got null): (%s)".formatted(item));
            }
            return stack;
        }

    }

    public static class PotionEffects extends FabricSerializer implements Serializer<FabricData.PotionEffects> {

        private static final TypeToken<List<Data.PotionEffects.Effect>> TYPE = new TypeToken<>() {
        };

        public PotionEffects(@NotNull HuskSync plugin) {
            super(plugin);
        }

        @Override
        public FabricData.PotionEffects deserialize(@NotNull String serialized) throws DeserializationException {
            return FabricData.PotionEffects.adapt(
                    plugin.getGson().fromJson(serialized, TYPE.getType())
            );
        }

        @NotNull
        @Override
        public String serialize(@NotNull FabricData.PotionEffects element) throws SerializationException {
            return plugin.getGson().toJson(element.getActiveEffects());
        }

    }

    public static class Advancements extends FabricSerializer implements Serializer<FabricData.Advancements> {

        private static final TypeToken<List<Data.Advancements.Advancement>> TYPE = new TypeToken<>() {
        };

        public Advancements(@NotNull HuskSync plugin) {
            super(plugin);
        }

        @Override
        public FabricData.Advancements deserialize(@NotNull String serialized) throws DeserializationException {
            return FabricData.Advancements.from(
                    plugin.getGson().fromJson(serialized, TYPE.getType())
            );
        }

        @NotNull
        @Override
        public String serialize(@NotNull FabricData.Advancements element) throws SerializationException {
            return plugin.getGson().toJson(element.getCompleted());
        }
    }

}
