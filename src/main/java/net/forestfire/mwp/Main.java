// MWP ©2020 Pecacheu. Licensed under GNU GPL 3.0
// Modified to only teleport dogs and cats

package net.forestfire.mwp;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MVWorld;

public class Main extends JavaPlugin implements Listener {
    static final String MSG = "&6";
    static int RADIUS, MAX_LOC_TRIES;
    static List<Material> UNSAFE;
    static boolean DBG;

    @Override
    public void onEnable() {
        if (Conf.loadConf()) {
            getServer().getPluginManager().registerEvents(this, this);
            msg(null, MSG + "Plugin Loaded!");
        }
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        World fromWorld = event.getFrom();
        
        List<LivingEntity> pets = getPetsOf(p.getUniqueId(), fromWorld);
        Map<String, Integer> petCounts = new HashMap<>();
        List<String> uniquePetNames = new ArrayList<>();
        
        // Use MultiVerse-Core API to check gamemode instead of direct comparison
        MVWorldManager worldManager = MultiverseCore.getPlugin().getMVWorldManager();
        MVWorld fromMVWorld = worldManager.getMVWorld(fromWorld.getName());
        MVWorld toMVWorld = worldManager.getMVWorld(p.getWorld().getName());
        
        // Early return if previous world and new world don't have matching gamemode
        if (fromMVWorld != null && toMVWorld != null && 
            !fromMVWorld.getGameMode().equals(toMVWorld.getGameMode())) {
            return;
        }
            
        for (LivingEntity pet : pets) {
            if (!pet.isDead() && !pet.isLeashed() && !sitting(pet) && tpPet(pet, p)) {
                String name = pet.getName();
                if (isGenericPetName(name)) {
                    petCounts.put(name, petCounts.getOrDefault(name, 0) + 1);
                } else {
                    uniquePetNames.add(name);
                }
            }
        }
        if (!petCounts.isEmpty() || !uniquePetNames.isEmpty()) {
            msg(p, MSG + "&2" + formatPetNames(uniquePetNames, petCounts) + "&6 jumped in the portal with you!");
        }
    }

    private boolean isGenericPetName(String name) {
        return name.equalsIgnoreCase("Wolf") || name.equalsIgnoreCase("Cat");
    }

    private String formatPetNames(List<String> uniqueNames, Map<String, Integer> countedNames) {
        List<String> formattedNames = new ArrayList<>();
        countedNames.forEach((name, count) -> formattedNames.add(count + " " + pluralize(name, count)));
        formattedNames.addAll(uniqueNames);

        if (formattedNames.isEmpty()) return "";
        if (formattedNames.size() == 1) return formattedNames.get(0);
        return String.join(", ", formattedNames.subList(0, formattedNames.size() - 1)) + ", and " + formattedNames.get(formattedNames.size() - 1);
    }

    private String pluralize(String name, int count) {
        if (name.equalsIgnoreCase("Wolf")) return count == 1 ? "Wolf" : "Wolves";
        return count == 1 ? name : name + "s";
    }

    private boolean tpPet(LivingEntity pet, Player p) {
        if (!isDogOrCat(pet)) return false;
        Location newLocation = randomLoc(pet, p.getLocation());
        if (newLocation != null) {
            killOthers(pet);
            pet.teleport(newLocation);
            sitting(pet, false);
            msg(null, MSG + "&bTeleported " + p.getName() + "'s &bpet &a" + pet.getName() + " &bfrom &d" + pet.getWorld().getName() + " &bto &d" + p.getWorld().getName());
            return true;
        } else {
            msg(p, MSG + "&cFailed to Teleport &a" + pet.getName() + " &cfrom &d" + pet.getWorld().getName() + "&c!");
            return false;
        }
    }

    static boolean isDogOrCat(LivingEntity entity) {
        return entity instanceof Wolf || entity instanceof Cat;
    }

    static List<LivingEntity> getPetsOf(UUID owner, World world) {
        List<LivingEntity> pets = new ArrayList<>();
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class)) {
            if (entity instanceof Tameable t && owner.equals(t.getOwnerUniqueId()) && isDogOrCat(entity)) {
                pets.add(entity);
            }
        }
        return pets;
    }

    static void killOthers(LivingEntity pet) {
        UUID owner = ((Tameable) pet).getOwnerUniqueId();
        String name = pet.getName();
        Class<? extends LivingEntity> type = pet.getClass();
        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(type).removeIf(e -> owner.equals(((Tameable) e).getOwnerUniqueId()) && name.equals(e.getName()) && world.equals(e.getWorld())));
    }

    static Location randomLoc(LivingEntity pet, Location target) {
        int range = RADIUS * 2;
        for (int i = 0; i < MAX_LOC_TRIES; ++i) {
            target.setX(target.getX() + Math.round(Math.random() * range) - RADIUS + 0.5);
            target.setZ(target.getZ() + Math.round(Math.random() * range) - RADIUS + 0.5);
            Location safeLocation = findSafeGround(pet.getBoundingBox(), target);
            if (safeLocation != null) return safeLocation;
        }
        return null;
    }

    static Location findSafeGround(BoundingBox size, Location loc) {
        for (int y = loc.getBlockY(); y >= loc.getWorld().getMinHeight(); y--) {
            loc.setY(y);
            if (isSafe(size, loc)) return loc;
        }
        return null;
    }

    static boolean isSafe(BoundingBox size, Location loc) {
        return !loc.getBlock().isLiquid() && loc.getBlock().isPassable();
    }

    static boolean sitting(LivingEntity entity) {
        return entity instanceof Sittable && ((Sittable) entity).isSitting();
    }

    static void sitting(LivingEntity entity, boolean sit) {
        if (entity instanceof Sittable) ((Sittable) entity).setSitting(sit);
    }

    static void msg(CommandSender sender, String message) {
        if (sender == null) sender = Bukkit.getConsoleSender();
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }
}
