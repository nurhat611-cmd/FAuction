package fr.florianpal.fauction.utils;

import io.papermc.lib.PaperLib;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class SerializationUtil {
    public static byte[] serialize(ItemStack itemStack) throws IllegalStateException {
        if (PaperLib.isPaper()) {
            return itemStack.serializeAsBytes();
        } else {
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

                // Write the size of the inventory
                dataOutput.writeObject(itemStack);

                // Serialize that array
                dataOutput.close();
                return outputStream.toByteArray();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to save item stacks.", e);
            }
        }
    }

    public static ItemStack deserialize(byte[] data) throws IOException {
        if (PaperLib.isPaper()) {
            return ItemStack.deserializeBytes(data);
        } else {
            try {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

                // Read the serialized inventory
                ItemStack itemStack = (ItemStack) dataInput.readObject();
                dataInput.close();
                return itemStack;
            } catch (ClassNotFoundException e) {
                throw new IOException("Unable to decode class type.", e);
            }
        }
    }

    public static byte[] serializeBukkit(ItemStack itemStack) throws IllegalStateException {

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Write the size of the inventory
            dataOutput.writeObject(itemStack);

            // Serialize that array
            dataOutput.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    public static byte[] serializePaper(ItemStack itemStack) throws IllegalStateException {

        return itemStack.serializeAsBytes();
    }

    public static ItemStack deserializePaper(byte[] data) {

        return ItemStack.deserializeBytes(data);
    }

    public static ItemStack deserializeBukkit(byte[] data) throws IOException {

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            // Read the serialized inventory
            ItemStack itemStack = (ItemStack) dataInput.readObject();
            dataInput.close();
            return itemStack;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }
}
