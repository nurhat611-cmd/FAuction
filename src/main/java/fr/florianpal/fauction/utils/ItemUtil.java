package fr.florianpal.fauction.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class ItemUtil {

    public static final List<Material> SHULKERS = Arrays.asList(Material.SHULKER_BOX, Material.BLUE_SHULKER_BOX, Material.BLACK_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.GRAY_SHULKER_BOX, Material.GREEN_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.LIGHT_GRAY_SHULKER_BOX, Material.LIME_SHULKER_BOX, Material.MAGENTA_SHULKER_BOX, Material.ORANGE_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.RED_SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX, Material.PURPLE_SHULKER_BOX);

    public static boolean isShulker(ItemStack itemStack) {

        return SHULKERS.contains(itemStack.getType());
    }

    public static boolean isBarrel(ItemStack itemStack) {

        return Material.BARREL.equals(itemStack.getType());
    }
}
