package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.MultiVersionCompat;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.interfaces.ICreativeSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PlayerRandCracker {

    // ===== RNG IMPLEMENTATION ===== //

    public static final long MULTIPLIER = 0x5deece66dL;
    public static final long ADDEND = 0xbL;
    public static final long MASK = (1L << 48) - 1;

    private static long seed;

    private static int next(int bits) {
        seed = (seed * MULTIPLIER + ADDEND) & MASK;
        return (int) (seed >>> (48 - bits));
    }

    public static int nextInt() {
        return next(32);
    }

    public static int nextInt(int bound) {
        if ((bound & -bound) == bound) {
            return (int) ((bound * (long)next(31)) >> 31);
        }

        int bits, val;
        do {
            bits = next(31);
            val = bits % bound;
        } while (bits - val + (bound-1) < 0);

        return val;
    }

    public static float nextFloat() {
        return next(24) / (float) (1 << 24);
    }

    public static void setSeed(long seed) {
        PlayerRandCracker.seed = seed;
    }

    public static long getSeed() {
        return seed;
    }


    // ===== RESET DETECTION + PLAYER RNG MAINTENANCE ===== //

    public static boolean isPredictingBlockBreaking = false;
    @Nullable
    private static Runnable postBlockBreakPredictAction = null;

    public static void postSendBlockBreakingPredictionPacket() {
        if (postBlockBreakPredictAction != null) {
            postBlockBreakPredictAction.run();
            postBlockBreakPredictAction = null;
        }
    }

    // TODO: update-sensitive: call hierarchy of Player.random and Player.getRandom()

    private static int expectedThrows = 0;

    public static void resetCracker() {
        Configs.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
    }

    public static void resetCracker(String reason) {
        if (Configs.playerCrackState != PlayerRandCracker.CrackState.UNCRACKED) {
            ClientCommandHelper.sendFeedback(Component.translatable("playerManip.reset", Component.translatable("playerManip.reset." + reason))
                    .withStyle(ChatFormatting.RED));
        }
        resetCracker();
    }

    public static void onDropItem() {
        if (expectedThrows > 0 || canMaintainPlayerRNG()) {
            for (int i = 0; i < 4; i++) {
                nextInt();
            }
        } else {
            resetCracker("dropItem");
        }
        if (expectedThrows > 0) {
            expectedThrows--;
        }
    }

    public static void onEntityCramming() {
        resetCracker("entityCramming");
    }

    public static void onDrink() {
        // TODO: was this ever a thing?
        //resetCracker("drink");
    }

    public static void onEat(ItemStack stack, Vec3 pos, int particleCount, int itemUseTimeLeft) {
        if (canMaintainPlayerRNG()) {

            if (itemUseTimeLeft < 0 && particleCount != 16) {
                // We have accounted for all eating ticks, that on the server should be calculated
                // Sometimes if the connection is laggy we eat more than 24 ticks so just hope for the best
                return;
            }

            //Every time a person eats, the particles are random, and when finished more particles spawn(16)
            for (int i = 0; i < particleCount * 3 + 3; i++) {
                nextInt();
            }

            if (Configs.getChorusManipulation() && stack.getItem() == Items.CHORUS_FRUIT) {
                ChorusManipulation.onEat(pos, particleCount, itemUseTimeLeft);
                if (particleCount == 16) {
                    //Consumption randoms
                    for (int i = 0; i < 5; i++) {
                        nextInt();
                    }
                }
            }
        } else {
            resetCracker("food");
        }
    }

    public static void onUnderwater() {
        resetCracker("swim");
    }

    public static void onSwimmingStart() {
        resetCracker("enterWater");
    }

    public static void onAmethystChime() {
        resetCracker("amethystChime");
    }

    public static void onDamage() {
        resetCracker("playerHurt");
    }

    public static void onSprinting() {
        resetCracker("sprint");
    }

    public static void onEquipmentBreak() {
        if (MultiVersionCompat.INSTANCE.getProtocolVersion() <= MultiVersionCompat.V1_13_2) {
            resetCracker("itemBreak");
        }
    }

    public static void onPotionParticles() {
        resetCracker("potion");
    }

    public static void onGiveCommand() {
        resetCracker("give");
    }

    public static void onAnvilUse() {
        if (canMaintainPlayerRNG()) {
            nextInt();
        } else {
            resetCracker("anvil");
        }
    }

    public static void onMending() {
        resetCracker("mending");
    }

    public static void onXpOrb() {
        if (MultiVersionCompat.INSTANCE.getProtocolVersion() >= MultiVersionCompat.V1_17) {
            // TODO: is there a way to be smarter about this?
            resetCracker("xp");
        }
    }

    public static void onFrostWalker() {
        resetCracker("frostWalker");
    }

    public static void onSoulSpeed() {
        resetCracker("soulSpeed");
    }

    public static void onBaneOfArthropods() {
        if (canMaintainPlayerRNG()) {
            nextInt();
        } else {
            resetCracker("baneOfArthropods");
        }
    }

    public static void onRecreatePlayer() {
        resetCracker("recreated");
    }

    public static void onUnbreaking(ItemStack stack, int amount, int unbreakingLevel) {
        if (canMaintainPlayerRNG()) {
            for (int i = 0; i < amount; i++) {
                if (!(stack.getItem() instanceof ArmorItem) || nextFloat() >= 0.6) {
                    nextInt(unbreakingLevel + 1);
                } else {
                    resetCracker("unbreaking");
                }
            }
        }
    }

    public static void onUnbreakingUncertain(ItemStack stack, int minAmount, int maxAmount, int unbreakingLevel) {
        resetCracker("unbreaking");
    }

    // TODO: update-sensitive: call hierarchy of ItemStack.damage
    public static void onItemDamage(int amount, LivingEntity holder, ItemStack stack) {
        if (holder instanceof LocalPlayer && !((LocalPlayer) holder).getAbilities().instabuild) {
            if (stack.isDamageableItem()) {
                if (amount > 0) {
                    int unbreakingLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, stack);
                    if (unbreakingLevel > 0) {
                        onUnbreaking(stack, amount, unbreakingLevel);
                    }

                    if (Configs.toolBreakWarning && stack.getDamageValue() + amount >= stack.getMaxDamage() - 30) {

                        if(stack.getDamageValue() + amount >= stack.getMaxDamage() - 15) {
                            Minecraft.getInstance().player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 10,0.1f);
                        }

                        MutableComponent durability = Component.literal(String.valueOf(stack.getMaxDamage() - stack.getDamageValue() - 1)).withStyle(ChatFormatting.RED);

                        Minecraft.getInstance().gui.setOverlayMessage(
                                Component.translatable("playerManip.toolBreakWarning", durability).withStyle(ChatFormatting.GOLD),
                                false);
                    }

                    if (Configs.infiniteTools && Configs.playerCrackState.knowsSeed()) {
                        Runnable action = () -> throwItemsUntil(rand -> {
                            for (int i = 0; i < amount; i++) {
                                if (stack.getItem() instanceof ArmorItem && rand.nextFloat() < 0.6)
                                    return false;
                                if (rand.nextInt(unbreakingLevel + 1) == 0)
                                    return false;
                            }
                            return true;
                        }, 64);
                        if (isPredictingBlockBreaking) {
                            postBlockBreakPredictAction = action;
                        } else {
                            action.run();
                        }
                    }
                }
            }
        }
    }

    public static void onItemDamageUncertain(int minAmount, int maxAmount, LivingEntity holder, ItemStack stack) {
        if (holder instanceof LocalPlayer && !((LocalPlayer) holder).getAbilities().instabuild) {
            if (stack.isDamageableItem()) {
                if (maxAmount > 0) {
                    int unbreakingLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, stack);
                    if (unbreakingLevel > 0)
                        onUnbreakingUncertain(stack, minAmount, maxAmount, unbreakingLevel);
                }
            }
        }
    }

    public static void onUnexpectedItemEnchant() {
        resetCracker("enchanted");
    }

    private static boolean canMaintainPlayerRNG() {
        if (Configs.playerRNGMaintenance && Configs.playerCrackState.knowsSeed()) {
            Configs.playerCrackState = CrackState.CRACKED;
            return true;
        } else {
            return false;
        }
    }


    // ===== UTILITIES ===== //

    public static ThrowItemsResult throwItemsUntil(Predicate<Random> condition, int max) {
        if (!Configs.playerCrackState.knowsSeed()) {
            return new ThrowItemsResult(ThrowItemsResult.Type.UNKNOWN_SEED);
        }
        Configs.playerCrackState = CrackState.CRACKED;

        long seed = PlayerRandCracker.seed;
        Random rand = new Random(seed ^ MULTIPLIER);

        int itemsNeeded = 0;
        for (; itemsNeeded <= max && !condition.test(rand); itemsNeeded++) {
            for (int i = 0; i < 4; i++) {
                seed = (seed * MULTIPLIER + ADDEND) & MASK;
            }
            rand.setSeed(seed ^ MULTIPLIER);
        }
        if (itemsNeeded > max) {
            return new ThrowItemsResult(ThrowItemsResult.Type.NOT_POSSIBLE, itemsNeeded);
        }
        for (int i = 0; i < itemsNeeded; i++) {
            if (!throwItem()) {
                return new ThrowItemsResult(ThrowItemsResult.Type.NOT_ENOUGH_ITEMS, i, itemsNeeded);
            }
        }

        return new ThrowItemsResult(ThrowItemsResult.Type.SUCCESS);
    }

    public static boolean throwItem() {
        LocalPlayer player = Minecraft.getInstance().player;

        Slot matchingSlot = getBestItemThrowSlot(player.containerMenu.slots);
        if (matchingSlot == null) {
            return false;
        }
        expectedThrows++;
        Minecraft.getInstance().gameMode.handleInventoryMouseClick(player.containerMenu.containerId,
                matchingSlot.index, 0, ClickType.THROW, player);

        return true;
    }

    public static Slot getBestItemThrowSlot(List<Slot> slots) {
        slots = slots.stream().filter(slot -> {
            if (!slot.hasItem()) {
                return false;
            }
            if (slot instanceof ICreativeSlot) {
                return false;
            }
            if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BINDING_CURSE, slot.getItem()) != 0) {
                return false;
            }
            if (slot.getItem().getItem() == Items.CHORUS_FRUIT) {
                return false;
            }
            return true;
        }).collect(Collectors.toList());

        Map<Item, Integer> itemCounts = new HashMap<>();
        for (Slot slot : slots) {
            itemCounts.put(slot.getItem().getItem(), itemCounts.getOrDefault(slot.getItem().getItem(), 0) + slot.getItem().getCount());
        }
        if (itemCounts.isEmpty()) {
            return null;
        }
        //noinspection OptionalGetWithoutIsPresent
        Item preferredItem = itemCounts.keySet().stream().max(Comparator.comparingInt(Item::getMaxStackSize).thenComparing(itemCounts::get)).get();
        //noinspection OptionalGetWithoutIsPresent
        return slots.stream().filter(slot -> slot.getItem().getItem() == preferredItem).findFirst().get();
    }

    private static final Field RANDOM_SEED;
    static {
        Field randomSeedField;
        try {
            randomSeedField = Random.class.getDeclaredField("seed");
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
        try {
            randomSeedField.setAccessible(true);
        } catch (Exception e) {
            // Java 14+ can't access private fields in these classes
            randomSeedField = null;
        }
        RANDOM_SEED = randomSeedField;
    }
    public static OptionalLong getSeed(Random rand) {
        if (RANDOM_SEED == null) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(((AtomicLong) RANDOM_SEED.get(rand)).get());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static class ThrowItemsResult {
        private final Type type;
        private final MutableComponent message;

        public ThrowItemsResult(Type type, Object... args) {
            this.type = type;
            this.message = Component.translatable(type.getTranslationKey(), args);
        }

        public Type getType() {
            return type;
        }

        public MutableComponent getMessage() {
            return message;
        }

        public enum Type {
            NOT_ENOUGH_ITEMS(false, "playerManip.notEnoughItems"),
            NOT_POSSIBLE(false, "playerManip.throwError"),
            UNKNOWN_SEED(false, "playerManip.uncracked"),
            SUCCESS(true, null),
            ;

            private final boolean success;
            private final String translationKey;

            Type(boolean success, String translationKey) {
                this.success = success;
                this.translationKey = translationKey;
            }

            public boolean isSuccess() {
                return success;
            }

            public String getTranslationKey() {
                return translationKey;
            }
        }
    }

    public static enum CrackState implements StringRepresentable {
        UNCRACKED("uncracked"),
        CRACKED("cracked", true),
        ENCH_CRACKING_1("ench_cracking_1"),
        HALF_CRACKED("half_cracked"),
        ENCH_CRACKING_2("ench_cracking_2"),
        CRACKING("cracking"),
        EATING("eating"),
        MANIPULATING_ENCHANTMENTS("manipulating_enchantments"),
        WAITING_DUMMY_ENCHANT("waiting_dummy_enchant", true),
        ;

        private final String name;
        private final boolean knowsSeed;
        CrackState(String name) {
            this(name, false);
        }
        CrackState(String name, boolean knowsSeed) {
            this.name = name;
            this.knowsSeed = knowsSeed;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public boolean knowsSeed() {
            return knowsSeed;
        }
    }

}
