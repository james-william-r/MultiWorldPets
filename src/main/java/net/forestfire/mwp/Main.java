//MWP ©2020 Pecacheu. Licensed under GNU GPL 3.0
//Modified to only teleport dogs and cats

package net.forestfire.mwp;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
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

public class Main extends JavaPlugin implements Listener {
static final String MSG="&6", PERM_USE="multiworldpets.use";
static int RADIUS, MAX_LOC_TRIES; static ArrayList<Material> UNSAFE;
static boolean DBG;

//------------------- Initialization -------------------

@Override
public void onEnable() {
	if(Conf.loadConf()) {
		getServer().getPluginManager().registerEvents(this, this);
		msg(null,MSG+"Plugin Loaded!");
	}
}
@Override
public void onDisable() { HandlerList.unregisterAll(); }

//------------------- Plugin Event Handlers -------------------

@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
public void onPlayerChangedWorld(PlayerChangedWorldEvent event) { 
    if (event.getPlayer().hasPermission(PERM_USE)) {
        Player p = event.getPlayer(); 
        World f = event.getFrom();
        
        List<LivingEntity> pets = getPetsOf(p.getUniqueId(), f);
        Map<String, Integer> petCounts = new HashMap<>();
        List<String> uniquePetNames = new ArrayList<>();

        for (LivingEntity pet : pets) {
            if (!pet.isDead() && !pet.isLeashed() && !sitting(pet)) {
                tpPet(pet, p);
                String name = pet.getName();

                // Check if name is generic (Wolf, Cat, etc.)
                if (isGenericPetName(name)) {
                    petCounts.put(name, petCounts.getOrDefault(name, 0) + 1);
                } else {
                    uniquePetNames.add(name);
                }
            }
        }

        if (!petCounts.isEmpty() || !uniquePetNames.isEmpty()) {
            String formattedNames = formatPetNames(uniquePetNames, petCounts);
            msg(p, MSG + "&2" + formattedNames + "&6 jumped in the portal with you!");
        }
    }
}

/**
 * Checks if the pet name is a generic species name that should be counted instead of listed individually.
 */
private boolean isGenericPetName(String name) {
    return name.equalsIgnoreCase("Wolf") || name.equalsIgnoreCase("Cat");
}

/**
 * Formats a list of unique pet names and counted generic pet names into a readable string.
 * Example outputs:
 * - "Charlie jumped in the portal with you!"
 * - "Charlie and Max jumped in the portal with you!"
 * - "Charlie, Max, and Bella jumped in the portal with you!"
 * - "3 Wolves and Bella jumped in the portal with you!"
 * - "2 Cats, 3 Wolves, and Max jumped in the portal with you!"
 */
private String formatPetNames(List<String> uniqueNames, Map<String, Integer> countedNames) {
    List<String> formattedNames = new ArrayList<>();

    // Add counted names with proper pluralization
    for (Map.Entry<String, Integer> entry : countedNames.entrySet()) {
        formattedNames.add(entry.getValue() + " " + pluralize(entry.getKey(), entry.getValue()));
    }

    // Add unique pet names
    formattedNames.addAll(uniqueNames);

    // Format into readable string
    if (formattedNames.size() == 1) {
        return formattedNames.get(0);
    } else if (formattedNames.size() == 2) {
        return formattedNames.get(0) + " and " + formattedNames.get(1);
    } else {
        return String.join(", ", formattedNames.subList(0, formattedNames.size() - 1)) + ", and " + formattedNames.get(formattedNames.size() - 1);
    }
}

/**
 * Returns the plural form of a pet name (e.g., "Wolf" → "Wolves", "Cat" → "Cats").
 */
private String pluralize(String name, int count) {
    if (name.equalsIgnoreCase("Wolf")) {
        return count == 1 ? "Wolf" : "Wolves";
    }
    return count == 1 ? name : name + "s";
}


void tpPet(LivingEntity pet, Player p) {
	// Only teleport if pet is a dog (Wolf) or a Cat
	if (isDogOrCat(pet)) {
		String w=pet.getWorld().getName(), nw=p.getWorld().getName(),
		n=pet.getName(); Location nl=randomLoc(pet, p.getLocation());
		if(nl != null) {
			//Teleport
			killOthers(pet); pet.teleport(nl); sitting(pet,false);
			msg(p,MSG+"&2"+n+"&6 jumped in the portal with you!");
			msg(null,MSG+"&bTeleported "+p.getName()
				+"'s &bpet &a"+n+" &bfrom &d"+w+" &bto &d"+nw);
		} else { //Unsafe
			String e=MSG+"&cFailed to Teleport &a"+n+" &cfrom &d"+w+" &cto &d"+nw+"&c!";
			msg(p,e); msg(null,e);
		}
	}
}

//------------------- Utility Functions -------------------

// Check if entity is a dog (Wolf) or cat
static boolean isDogOrCat(LivingEntity entity) {
	return entity instanceof Wolf || entity instanceof Cat;
}

static ArrayList<LivingEntity> getPetsOf(UUID owner, World w) {
	ArrayList<LivingEntity> pets=new ArrayList<>();
	for(LivingEntity e: w.getEntitiesByClass(LivingEntity.class)) {
		if(e instanceof Tameable && owner.equals(((Tameable)e).getOwnerUniqueId()) && isDogOrCat(e)) {
			pets.add(e);
		}
	}
	return pets;
}

static void killOthers(LivingEntity pet) {
	UUID owner=((Tameable)pet).getOwnerUniqueId(); String n=pet.getName();
	Class<? extends LivingEntity> c=pet.getClass(); for(World w: Bukkit.getWorlds()) { //Iterate worlds
		w.getEntitiesByClass(c).removeIf(e -> owner.equals(((Tameable)e).getOwnerUniqueId())
			&& n.equals(e.getName()) && w.equals(e.getWorld()));
	}
}

static Location randomLoc(LivingEntity pet, Location l) {
	BoundingBox size=pet.getBoundingBox();
	int d=RADIUS*2, y=l.getBlockY();
	double x=l.getBlockX(), z=l.getBlockZ();
	for(int i=0; i<MAX_LOC_TRIES; ++i) {
		l.setX(x+Math.round(Math.random()*d)-RADIUS+.5);
		l.setZ(z+Math.round(Math.random()*d)-RADIUS+.5);
		Location a=findSafeGnd(size, l, true),
			b=findSafeGnd(size, l, false);
		if(DBG) msg(null,MSG+"Try x="+l.getX()+" z="+l.getZ()
			+", Above @ "+locStr(a)+" Below @ "+locStr(b));
		//Choose a or b
		if(a==null) { if(b!=null) return b; }
		else if(b==null) return a;
		else if(Math.abs(a.getBlockY()-y) <= Math.abs(b.getBlockY()-y)) return a;
		else return b;
	}
	return null;
}

static Location findSafeGnd(BoundingBox size, Location l, boolean yDir) {
	l=l.clone();
	int y=l.getBlockY(),
		lim=yDir ? Math.min(y+RADIUS, l.getWorld().getMaxHeight()-1):
		Math.max(y-RADIUS, l.getWorld().getMinHeight()+1);
	for(; yDir?(y<=lim):(y>=lim); y+=yDir?1:-1) {
		l.setY(y); if(isSafe(size, l)) return l;
	}
	return null;
}
static boolean isSafe(BoundingBox size, Location l) {
	//Check floor
	if(_unsafe(l.clone().add(0,-1,0), 2)) return false;
	//Check clearance
	double h=size.getHeight(),
		cx=Math.max(Math.ceil((size.getWidthX()/2)-.5), 0),
		cz=Math.max(Math.ceil((size.getWidthZ()/2)-.5), 0);
	for(double x=-cx; x<=cx; ++x) for(double y=0; y<h; ++y) for(double z=-cz; z<=cz; ++z) {
		if(_unsafe(l.clone().add(x,y,z), 1)) return false;
	}
	return true;
}
static boolean _unsafe(Location l, int mode) {
	Block b=l.getBlock();
	switch(mode) {
	case 1: //Air
		if(!b.isPassable() || b.isLiquid()) return true;
	break; case 2: //Floor
		if(!b.isSolid() && !b.isLiquid()) return true;
	}
	return UNSAFE.contains(b.getType());
}

static boolean sitting(LivingEntity entity) { //Getter
	if(entity instanceof Sittable) return ((Sittable)entity).isSitting();
	return false;
}
static void sitting(LivingEntity entity, boolean sit) { //Setter
	if(entity instanceof Sittable) ((Sittable)entity).setSitting(sit);
}

static String locStr(Location l) {
	return l!=null ? "("+l.getBlockX()+", "+l.getBlockY()+", "+l.getBlockZ()+")" : "null";
}

//-------------------  PecacheuLib Functions -------------------

static Component sc(String s) { return LegacyComponentSerializer.legacyAmpersand().deserialize(s); }
static void msg(CommandSender cm, String s) { if(cm==null) cm=Bukkit.getConsoleSender(); cm.sendMessage(sc(s)); }
}
