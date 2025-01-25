/*
 * This file is part of InteractiveChatDiscordSrvAddon.
 *
 * Copyright (C) 2020 - 2025. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2020 - 2025. Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.loohp.multichatdiscordsrvaddon.utils;

import com.loohp.multichatdiscordsrvaddon.VersionManager;
import com.loohp.multichatdiscordsrvaddon.nms.NMS;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class PotionUtils {

    private static final Map<PotionType, String> potionMapping = new EnumMap<>(PotionType.class);

    static {
        if (!VersionManager.version.isLegacy()) {
            potionMapping.put(PotionType.WATER, "water");
            potionMapping.put(PotionType.MUNDANE, "mundane");
            potionMapping.put(PotionType.THICK, "thick");
            potionMapping.put(PotionType.AWKWARD, "awkward");
            potionMapping.put(PotionType.NIGHT_VISION, "night_vision");
            potionMapping.put(PotionType.INVISIBILITY, "invisibility");
            potionMapping.put(PotionType.LEAPING, "leaping");
            potionMapping.put(PotionType.FIRE_RESISTANCE, "fire_resistance");
            potionMapping.put(PotionType.SWIFTNESS, "swiftness");
            potionMapping.put(PotionType.SLOWNESS, "slowness");
            potionMapping.put(PotionType.TURTLE_MASTER, "turtle_master");
            potionMapping.put(PotionType.WATER_BREATHING, "water_breathing");
            potionMapping.put(PotionType.HEALING, "healing");
            potionMapping.put(PotionType.HARMING, "harming");
            potionMapping.put(PotionType.POISON, "poison");
            potionMapping.put(PotionType.REGENERATION, "regeneration");
            potionMapping.put(PotionType.STRENGTH, "strength");
            potionMapping.put(PotionType.WEAKNESS, "weakness");
            potionMapping.put(PotionType.LUCK, "luck");
            potionMapping.put(PotionType.SLOW_FALLING, "slow_falling");
        }
    }

    public static String getVanillaPotionName(PotionType type) {
        if (potionMapping.containsKey(type)) {
            return potionMapping.get(type);
        }
        return null;
    }

    public static final int WATER_COLOR = 3694022;
    public static final int UNCRAFTABLE_COLOR = 16253176;

    @SuppressWarnings("deprecation")
    public static int getPotionBaseColor(PotionType type) {
        PotionEffectType effect = type.getEffectType();
        if (effect == null) {
            if (type.name().equalsIgnoreCase("UNCRAFTABLE")) {
                return UNCRAFTABLE_COLOR;
            } else {
                return WATER_COLOR;
            }
        } else {
            return effect.getColor().asRGB();
        }
    }

    public static List<PotionEffect> getAllPotionEffects(ItemStack potion) {
        return NMS.getInstance().getAllPotionEffects(potion);
    }

    public static ChatColor getPotionEffectChatColor(PotionEffectType type) {
        return NMS.getInstance().getPotionEffectChatColor(type);
    }

    public static Map<String, AttributeModifier> getPotionAttributes(PotionEffect effect) {
        return (Map<String, AttributeModifier>) NMS.getInstance().getPotionAttributeModifiers(effect);
    }

}
