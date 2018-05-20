package com.songoda.epicspawners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;
import com.songoda.arconix.api.mcupdate.MCUpdate;
import com.songoda.arconix.api.methods.formatting.TextComponent;
import com.songoda.arconix.api.methods.serialize.Serialize;
import com.songoda.arconix.api.utils.ConfigWrapper;
import com.songoda.arconix.plugin.Arconix;
import com.songoda.epicspawners.api.EpicSpawners;
import com.songoda.epicspawners.api.EpicSpawnersAPI;
import com.songoda.epicspawners.api.particles.ParticleDensity;
import com.songoda.epicspawners.api.particles.ParticleEffect;
import com.songoda.epicspawners.api.particles.ParticleType;
import com.songoda.epicspawners.api.spawner.Spawner;
import com.songoda.epicspawners.api.spawner.SpawnerData;
import com.songoda.epicspawners.api.spawner.SpawnerManager;
import com.songoda.epicspawners.api.spawner.SpawnerStack;
import com.songoda.epicspawners.api.utils.ClaimableProtectionPluginHook;
import com.songoda.epicspawners.api.utils.ProtectionPluginHook;
import com.songoda.epicspawners.api.utils.SpawnerDataBuilder;
import com.songoda.epicspawners.boost.BoostData;
import com.songoda.epicspawners.boost.BoostManager;
import com.songoda.epicspawners.boost.BoostType;
import com.songoda.epicspawners.handlers.AppearanceHandler;
import com.songoda.epicspawners.handlers.BlacklistHandler;
import com.songoda.epicspawners.handlers.CommandHandler;
import com.songoda.epicspawners.handlers.HologramHandler;
import com.songoda.epicspawners.hooks.*;
import com.songoda.epicspawners.listeners.*;
import com.songoda.epicspawners.player.PlayerActionManager;
import com.songoda.epicspawners.player.PlayerData;
import com.songoda.epicspawners.spawners.Shop;
import com.songoda.epicspawners.spawners.SpawnManager;
import com.songoda.epicspawners.spawners.editor.SpawnerEditor;
import com.songoda.epicspawners.spawners.object.ESpawner;
import com.songoda.epicspawners.spawners.object.ESpawnerManager;
import com.songoda.epicspawners.spawners.object.ESpawnerStack;
import com.songoda.epicspawners.tasks.SpawnerCustomSpawnTask;
import com.songoda.epicspawners.tasks.SpawnerParticleTask;
import com.songoda.epicspawners.utils.ESpawnerDataBuilder;
import com.songoda.epicspawners.utils.Heads;
import com.songoda.epicspawners.utils.Methods;
import com.songoda.epicspawners.utils.ServerVersion;
import com.songoda.epicspawners.utils.SettingsManager;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Created by songoda on 2/25/2017.
 */
public class EpicSpawnersPlugin extends JavaPlugin implements EpicSpawners {

    private static EpicSpawnersPlugin INSTANCE;

    public Map<String, Integer> cache = new HashMap<>();

    public List<Player> change = new ArrayList<>();

    public Map<Player, Integer> boostAmt = new HashMap<>();

    public String newSpawnerName = "";

    public Map<Player, String> chatEditing = new HashMap<>();

    public Map<Player, SpawnerData> inShow = new HashMap<>();
    public Map<Player, Integer> page = new HashMap<>();

    private ConfigWrapper langFile = new ConfigWrapper(this, "", "lang.yml");
    private ConfigWrapper hooksFile = new ConfigWrapper(this, "", "hooks.yml");
    public ConfigWrapper dataFile = new ConfigWrapper(this, "", "data.yml");
    public ConfigWrapper spawnerFile = new ConfigWrapper(this, "", "spawners.yml");

    public References references = null;


    private SpawnManager spawnManager;
    private PlayerActionManager playerActionManager;
    private SpawnerManager spawnerManager;
    private BoostManager boostManager;
    private SettingsManager settingsManager;

    private BlacklistHandler blacklistHandler;
    private HologramHandler hologramHandler;
    private AppearanceHandler appearanceHandler;

    private SpawnerParticleTask particleTask;
    private SpawnerCustomSpawnTask spawnerCustomSpawnTask;
    private SpawnerEditor spawnerEditor;
    private Heads heads;
    private Shop shop;
    private Locale locale;

    private ServerVersion serverVersion = ServerVersion.fromPackageName(Bukkit.getServer().getClass().getPackage().getName());
    private List<ProtectionPluginHook> protectionHooks = new ArrayList<>();
    private ClaimableProtectionPluginHook factionsHook, townyHook, aSkyblockHook, uSkyblockHook;

    public void onDisable() {
        this.saveToFile();
        this.particleTask.cancel();
        this.protectionHooks.clear();
        this.spawnerCustomSpawnTask.cancel();

        //this.spawnerRegistry.clearRegistry();
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        console.sendMessage(TextComponent.formatText("&a============================="));
        console.sendMessage(TextComponent.formatText("&7EpicSpawners " + this.getDescription().getVersion() + " by &5Songoda <3!"));
        console.sendMessage(TextComponent.formatText("&7Action: &cDisabling&7..."));
        console.sendMessage(TextComponent.formatText("&a============================="));
    }

    @SuppressWarnings("unchecked")
    public void onEnable() {
        INSTANCE = this;
        EpicSpawnersAPI.setImplementation(this);

        Arconix.pl().hook(this);

        ConsoleCommandSender console = Bukkit.getConsoleSender();
        console.sendMessage(TextComponent.formatText("&a============================="));
        console.sendMessage(TextComponent.formatText("&7EpicSpawners " + this.getDescription().getVersion() + " by &5Brianna <3&7!"));
        console.sendMessage(TextComponent.formatText("&7Action: &aEnabling&7..."));

        this.heads = new Heads(this);
        this.settingsManager = new SettingsManager(this);

        this.setupConfig();
        this.setupSpawners();

        // Locales
        Locale.init(this);
        Locale.saveDefaultLocale("en_US");
        this.locale = Locale.getLocale(getConfig().getString("Locale", "en_US"));

        this.langFile.createNewFile("Loading Language File", "EpicSpawners Language File");
        this.dataFile.createNewFile("Loading Data File", "EpicSpawners Data File");
        this.loadDataFile();

        this.references = new References();
        this.boostManager = new BoostManager();
        this.spawnManager = new SpawnManager();
        this.spawnerManager = new ESpawnerManager();
        this.blacklistHandler = new BlacklistHandler();
        this.hologramHandler = new HologramHandler(this);
        this.playerActionManager = new PlayerActionManager();

        // Register spawner data into SpawnerRegistry from configuration.
        FileConfiguration spawnerConfig = spawnerFile.getConfig();
        if (spawnerConfig.contains("Entities")) {
            for (String key : spawnerConfig.getConfigurationSection("Entities").getKeys(false)) {
                ConfigurationSection currentSection = spawnerConfig.getConfigurationSection("Entities." + key);

                List<EntityType> entities = new ArrayList<>();
                List<Material> blocks = new ArrayList<>();
                List<Material> spawnBlocks = new ArrayList<>();
                List<ItemStack> itemDrops = (List<ItemStack>) currentSection.getList("itemDrops", new ArrayList<>());
                List<ItemStack> items = (List<ItemStack>) currentSection.getList("items", new ArrayList<>());
                List<String> commands = currentSection.getStringList("commands");

                for (String block : currentSection.getStringList("blocks")) {
                    blocks.add(Material.matchMaterial(block.toUpperCase()));
                }
                for (String block : currentSection.getString("Spawn-Block").split(",")) {
                    spawnBlocks.add(Material.matchMaterial(block.toUpperCase()));
                }
                for (String entity : currentSection.getStringList("entities")) {
                    entities.add(EntityType.valueOf(entity));
                }

                SpawnerDataBuilder dataBuilder = new ESpawnerDataBuilder(key)
                        .entities(entities).blocks(blocks).items(items).entityDroppedItems(itemDrops).commands(commands)
                        .spawnBlocks(spawnBlocks)
                        .active(currentSection.getBoolean("Allowed"))
                        .spawnOnFire(currentSection.getBoolean("Spawn-On-Fire"))
                        .upgradeable(currentSection.getBoolean("Upgradable"))
                        .convertible(currentSection.getBoolean("Convertible"))
                        .convertRatio(currentSection.getString("Convert-Ratio"))
                        .inShop(currentSection.getBoolean("In-Shop"))
                        .pickupCost(currentSection.getDouble("Pickup-Cost"))
                        .shopPrice(currentSection.getDouble("Shop-Price"))
                        .killGoal(currentSection.getInt("CustomGoal"))
                        .upgradeCostEconomy(currentSection.getInt("Custom-ECO-Cost"))
                        .upgradeCostExperience(currentSection.getInt("Custom-XP-Cost"))
                        .tickRate(currentSection.getString("Tick-Rate"))
                        .particleEffect(ParticleEffect.valueOf(currentSection.getString("Spawn-Effect", "HALO")))
                        .spawnEffectParticle(ParticleType.valueOf(currentSection.getString("Spawn-Effect-Particle", "REDSTONE")))
                        .entitySpawnParticle(ParticleType.valueOf(currentSection.getString("Entity-Spawn-Particle", "SMOKE")))
                        .spawnerSpawnParticle(ParticleType.valueOf(currentSection.getString("Spawner-Spawn-Particle", "FIRE")))
                        .particleDensity(ParticleDensity.valueOf(currentSection.getString("Particle-Amount", "NORMAL")))
                        .particleEffectBoostedOnly(currentSection.getBoolean("Particle-Effect-Boosted-Only"));

                if (currentSection.contains("Display-Name")) {
                    dataBuilder.displayName(currentSection.getString("Display-Name"));
                }
                if (currentSection.contains("Display-Item")) {
                    dataBuilder.displayItem(Material.valueOf(currentSection.getString("Display-Item")));
                }

                this.spawnerManager.addSpawnerData(key, dataBuilder.build());
            }
        }

        FileConfiguration dataConfig = dataFile.getConfig();

        int amtConverted = 0;
        this.getLogger().info("Checking for legacy spawners...");
        if (dataFile.getConfig().contains("data.spawnerstats")) {
            // Adding in Legacy Spawners.
            for (String key : dataConfig.getConfigurationSection("data.spawnerstats").getKeys(false)) {
                Location location = Serialize.getInstance().unserializeLocation(key);
                location.setX(location.getBlockX());
                location.setY(location.getBlockY());
                location.setZ(location.getBlockZ());

                if (location.getBlock().getType() != Material.MOB_SPAWNER) continue;

                CreatureSpawner spawnerState = (CreatureSpawner) location.getBlock().getState();
                String type = spawnerState.getSpawnedType().name().toLowerCase().replace("_", " ");

                // Is custom spawner.
                if (dataConfig.contains("data.spawnerstats." + key + ".type"))
                    type = dataConfig.getString("data.spawnerstats." + key + ".type").toLowerCase().replace("_", " ");

                ESpawner spawner = new ESpawner(location);

                try {
                    spawner.getCreatureSpawner().setSpawnedType(EntityType.valueOf(type.toUpperCase().replace(" ", "_")));
                } catch (Exception ex) {
                    spawner.getCreatureSpawner().setSpawnedType(EntityType.valueOf("PIG"));
                }

                if (!spawnerManager.isSpawnerData(type)) continue;

                int stackSize = 1;
                SpawnerData spawnerData = spawnerManager.getSpawnerData(type);

                if (dataConfig.contains("data.spawner." + key))
                    stackSize = dataConfig.getInt("data.spawner." + key);

                ESpawnerStack spawnerStack = new ESpawnerStack(spawnerData, stackSize);
                amtConverted++;

                if (dataConfig.contains("data.spawnerstats." + key + ".spawns"))
                    spawner.setSpawnCount(dataConfig.getInt("data.spawnerstats." + key + ".spawns"));

                if (dataConfig.contains("data.spawnerstats." + key + ".player"))
                    spawner.setPlacedBy(UUID.fromString(dataConfig.getString("data.spawnerstats." + key + ".player")));

                spawner.addSpawnerStack(spawnerStack);
                this.spawnerManager.addSpawnerToWorld(location, spawner);
            }
        }

        if (amtConverted != 0) {
            this.getLogger().info("Converted " + amtConverted + " legacy spawners...");
            dataConfig.set("data", null);
        } else {
            this.getLogger().info("No legacy spawners found.");
        }


        // Adding in spawners.
        if (dataConfig.contains("data.spawners")) {
            for (String key : dataConfig.getConfigurationSection("data.spawners").getKeys(false)) {
                Location location = Serialize.getInstance().unserializeLocation(key);

                if (location.getWorld() == null || location.getBlock().getType() != Material.MOB_SPAWNER) {
                    if (location.getWorld() != null && location.getBlock().getType() != Material.MOB_SPAWNER) {
                        this.hologramHandler.despawn(location.getBlock());
                    }

                    continue;
                }

                ESpawner spawner = new ESpawner(location);

                for (String stackKey : dataConfig.getConfigurationSection("data.spawners." + key + ".Stacks").getKeys(false)) {
                    if (!spawnerManager.isSpawnerData(stackKey.toLowerCase())) continue;
                    spawner.addSpawnerStack(new ESpawnerStack(spawnerManager.getSpawnerData(stackKey), dataConfig.getInt("data.spawners." + key + ".Stacks." + stackKey)));
                }

                if (dataConfig.contains("data.spawners." + key + ".PlacedBy"))
                    spawner.setPlacedBy(UUID.fromString(dataConfig.getString("data.spawners." + key + ".PlacedBy")));

                spawner.setSpawnCount(dataConfig.getInt("data.spawners." + key + ".Spawns"));
                this.spawnerManager.addSpawnerToWorld(location, spawner);
            }
        }

        // Adding in Boosts
        if (dataConfig.contains("data.boosts")) {
            for (String key : dataConfig.getConfigurationSection("data.boosts").getKeys(false)) {
                if (!dataConfig.contains("data.boosts." + key + ".BoostType")) continue;

                BoostData boostData = new BoostData(
                        BoostType.valueOf(dataConfig.getString("data.boosts." + key + ".BoostType")),
                        dataConfig.getInt("data.boosts." + key + ".Amount"),
                        Long.parseLong(key),
                        dataConfig.get("data.boosts." + key + ".Data"));

                this.boostManager.addBoostToSpawner(boostData);
            }
        }

        // Adding in Player Data
        if (dataConfig.contains("data.players")) {
            for (String key : dataConfig.getConfigurationSection("data.players").getKeys(false)) {
                PlayerData playerData = playerActionManager.getPlayerAction(UUID.fromString(key));

                Map<EntityType, Integer> entityKills = new HashMap<>();
                if (!dataConfig.contains("data.players." + key + ".EntityKills")) continue;
                for (String key2 : dataConfig.getConfigurationSection("data.players." + key + ".EntityKills").getKeys(false)) {
                    EntityType entityType = EntityType.valueOf(key2);
                    int amt = dataConfig.getInt("data.players." + key + ".EntityKills." + key2);
                    entityKills.put(entityType, amt);
                }

                playerData.setEntityKills(entityKills);
            }
        }

        // Save data initially so that if the person reloads again fast they don't lose all their data.
        this.saveToFile();

        this.shop = new Shop(this);
        this.spawnerEditor = new SpawnerEditor(this);
        this.appearanceHandler = new AppearanceHandler();

        new MCUpdate(this, true);

        // Command registration
        this.getCommand("EpicSpawners").setExecutor(new CommandHandler(this));
        this.getCommand("SpawnerStats").setExecutor(new CommandHandler(this));
        this.getCommand("SpawnerShop").setExecutor(new CommandHandler(this));

        // Event registration
        PluginManager manager = Bukkit.getPluginManager();
        manager.registerEvents(new BlockListeners(this), this);
        manager.registerEvents(new ChatListeners(this), this);
        manager.registerEvents(new EntityListeners(this), this);
        manager.registerEvents(new InteractListeners(this), this);
        manager.registerEvents(new InventoryListeners(this), this);
        manager.registerEvents(new SpawnerListeners(this), this);

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::saveToFile, 6000, 6000);

        if (isServerVersionAtLeast(ServerVersion.V1_8)) {
            Bukkit.getPluginManager().registerEvents(new TestListeners(), this);
        }
        console.sendMessage(TextComponent.formatText("&a============================="));

        // Register default hooks
        this.registerIfEnabled("ASkyblock", HookASkyBlock::new);
        this.registerIfEnabled("Factions", HookFactions::new);
        this.registerIfEnabled("GriefPrevention", HookGriefPrevention::new);
        this.registerIfEnabled("Kingdoms", HookKingdoms::new);
        this.registerIfEnabled("PlotSquared", HookPlotSquared::new);
        this.registerIfEnabled("RedProtect", HookRedProtect::new);
        this.registerIfEnabled("Towny", HookTowny::new);
        this.registerIfEnabled("USkyBlock", HookUSkyBlock::new);
        this.registerIfEnabled("WorldGuard", HookWorldGuard::new);

        this.particleTask = SpawnerParticleTask.startTask(this);
        this.spawnerCustomSpawnTask = SpawnerCustomSpawnTask.startTask(this);
    }

    private void saveToFile() {
        //ToDO: If the defaults are set correctly this could do the initial config save.

        FileConfiguration spawnerConfig = spawnerFile.getConfig();
        spawnerConfig.set("Entities", null);

        ConfigurationSection entitiesSection = spawnerConfig.createSection("Entities");
        for (SpawnerData spawnerData : spawnerManager.getAllSpawnerData()) {
            ConfigurationSection currentSection = entitiesSection.createSection(spawnerData.getIdentifyingName());

            currentSection.set("Display-Name", spawnerData.getDisplayName());

            currentSection.set("blocks", getStrings(spawnerData.getBlocks()));
            currentSection.set("entities", getStrings(spawnerData.getEntities()));
            currentSection.set("itemDrops", spawnerData.getEntityDroppedItems());
            currentSection.set("items", spawnerData.getItems());
            currentSection.set("commands", spawnerData.getCommands());

            currentSection.set("Spawn-Block", String.join(", ", getStrings(spawnerData.getSpawnBlocksList())));
            currentSection.set("Allowed", spawnerData.isActive());
            currentSection.set("Spawn-On-Fire", spawnerData.isSpawnOnFire());
            currentSection.set("Upgradable", spawnerData.isUpgradeable());
            currentSection.set("Convertible", spawnerData.isConvertible());
            currentSection.set("Convert-Price", spawnerData.getConvertRatio());
            currentSection.set("In-Shop", spawnerData.isInShop());
            currentSection.set("Shop-Price", spawnerData.getShopPrice());
            currentSection.set("CustomGoal", spawnerData.getKillGoal());
            currentSection.set("Custom-ECO-Cost", spawnerData.getUpgradeCostExperience());
            currentSection.set("Custom-XP-Cost", spawnerData.getUpgradeCostExperience());
            currentSection.set("Tick-Rate", spawnerData.getTickRate());
            currentSection.set("Pickup-cost", spawnerData.getPickupCost());

            currentSection.set("Spawn-Effect", spawnerData.getParticleEffect().name());
            currentSection.set("Spawn-Effect-Particle", spawnerData.getSpawnEffectParticle().name());
            currentSection.set("Entity-Spawn-Particle", spawnerData.getEntitySpawnParticle().name());
            currentSection.set("Spawner-Spawn-Particle", spawnerData.getSpawnerSpawnParticle().name());
            currentSection.set("Particle-Amount", spawnerData.getParticleDensity().name());
            currentSection.set("Particle-Effect-Boosted-Only", spawnerData.isParticleEffectBoostedOnly());

            if (spawnerData.getDisplayItem() != null) {
                currentSection.set("Display-Item", spawnerData.getDisplayItem().name());
            }
        }

        this.spawnerFile.saveConfig();

        FileConfiguration dataConfig = dataFile.getConfig();
        dataConfig.set("data", null);
        ConfigurationSection dataSection = dataConfig.createSection("data");

        ConfigurationSection spawnersSection = dataSection.createSection("spawners");
        for (Spawner spawner : spawnerManager.getSpawners()) {
            if (spawner.getFirstStack() == null
                    || spawner.getFirstStack().getSpawnerData() == null
                    || spawner.getLocation() == null
                    || spawner.getLocation().getWorld() == null) continue;

            ConfigurationSection currentSection = spawnersSection.createSection(Serialize.getInstance().serializeLocation(spawner.getLocation()));

            for (SpawnerStack stack : spawner.getSpawnerStacks()) {
                currentSection.set("Stacks." + stack.getSpawnerData().getIdentifyingName(), stack.getStackSize());
            }

            currentSection.set("Spawns", spawner.getSpawnCount());

            if (spawner.getPlacedBy() != null) {
                currentSection.set("PlacedBy", spawner.getPlacedBy().getUniqueId().toString());
            }
        }

        for (BoostData boostData : boostManager.getBoosts()) {
            ConfigurationSection currentSection = dataSection.createSection("boosts." + String.valueOf(boostData.getEndTime()));
            currentSection.set("BoostType", boostData.getBoostType().name());
            currentSection.set("Data", boostData.getData());
            currentSection.set("Amount", boostData.getAmtBoosted());
        }

        for (PlayerData playerData : playerActionManager.getRegisteredPlayers()) {
            ConfigurationSection currentSection = dataSection.createSection("players." + playerData.getPlayer().getUniqueId());

            for (Map.Entry<EntityType, Integer> entry : playerData.getEntityKills().entrySet()) {
                currentSection.set("EntityKills." + entry.getKey().name(), entry.getValue());
            }
        }

        //ToDo: Save for player data.

        this.dataFile.saveConfig();
    }

    private <T extends Enum<T>> String[] getStrings(List<T> mats) {
        List<String> strings = new ArrayList<>();

        for (Object object : mats) {
            if (object instanceof Material) {
                strings.add(((Material) object).name());
            } else if (object instanceof EntityType) {
                strings.add(((EntityType) object).name());
            }
        }

        return strings.toArray(new String[strings.size()]);
    }

    private void setupConfig() {
        this.settingsManager.updateSettings();
        this.getConfig().options().copyDefaults(true);
        this.saveConfig();
    }

    private void setupSpawners() {
        for (EntityType value : EntityType.values()) {
            if (value.isSpawnable() && value.isAlive() && !value.toString().toLowerCase().contains("armor")) {
                processDefault(value.name());
            }
        }

        this.processDefault("Omni");
        this.spawnerFile.getConfig().options().copyDefaults(true);
        this.spawnerFile.saveConfig();
    }

    private void registerIfEnabled(String pluginName, Supplier<ProtectionPluginHook> hookSupplier) {
        if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) return;
        this.registerProtectionHook(hookSupplier.get());
    }

    public void processDefault(String value) {
        FileConfiguration spawnerConfig = spawnerFile.getConfig();

        System.out.println("hii");

        String type = Methods.getTypeFromString(value);
        if (!spawnerConfig.contains("Entities." + type + ".Display-Name")) {
            spawnerConfig.set("Entities." + type + ".Display-Name", type);
        }
        if (!spawnerConfig.contains("Entities." + type + ".Pickup-cost")) {
            spawnerConfig.addDefault("Entities." + type + ".Pickup-cost", 0);
        }

        String spawnBlock = "AIR";
        if (value.equalsIgnoreCase("pig") || value.equalsIgnoreCase("sheep") || value.equalsIgnoreCase("chicken") ||
                value.equalsIgnoreCase("cow") || value.equalsIgnoreCase("rabbit") || value.equalsIgnoreCase("llamma") ||
                value.equalsIgnoreCase("horse") || value.equalsIgnoreCase("OCELOT")) {
            spawnBlock = "GRASS";
        }

        if (value.equalsIgnoreCase("MUSHROOM_COW")) {
            spawnBlock = "MYCEL";
        }

        if (value.equalsIgnoreCase("SQUID") || value.equalsIgnoreCase("ELDER_GUARDIAN")) {
            spawnBlock = "WATER";
        }

        if (value.equalsIgnoreCase("OCELOT")) {
            spawnBlock += ", LEAVES";
        }

        for (EntityType val : EntityType.values()) {
            if (val.isSpawnable() && val.isAlive()) {
                if (val.name().equals(value)) {
                    List<String> list = new ArrayList<>();
                    list.add(value);
                    if (!spawnerConfig.contains("Entities." + type + ".entities"))
                        spawnerConfig.addDefault("Entities." + type + ".entities", list);
                }
            }
        }

        if (!spawnerConfig.contains("Entities." + type + ".Spawn-Block"))
            spawnerConfig.addDefault("Entities." + type + ".Spawn-Block", spawnBlock);

        if (!spawnerConfig.contains("Entities." + type + ".Allowed"))
            spawnerConfig.addDefault("Entities." + type + ".Allowed", true);

        if (!spawnerConfig.contains("Entities." + type + ".Spawn-On-Fire"))
            spawnerConfig.addDefault("Entities." + type + ".Spawn-On-Fire", false);

        if (!spawnerConfig.contains("Entities." + type + ".Upgradable"))
            spawnerConfig.addDefault("Entities." + type + ".Upgradable", true);
        if (!spawnerConfig.contains("Entities." + type + ".Convertible"))
            spawnerConfig.addDefault("Entities." + type + ".Convertible", true);
        if (!spawnerConfig.contains("Entities." + type + ".Convert-Ratio"))
            spawnerConfig.addDefault("Entities." + type + ".Convert-Ratio", "45%");
        if (!spawnerConfig.contains("Entities." + type + ".In-Shop"))
            spawnerConfig.addDefault("Entities." + type + ".In-Shop", true);
        if (!spawnerConfig.contains("Entities." + type + ".Shop-Price"))
            spawnerConfig.addDefault("Entities." + type + ".Shop-Price", 1000.00);
        if (!spawnerConfig.contains("Entities." + type + ".CustomGoal"))
            spawnerConfig.addDefault("Entities." + type + ".CustomGoal", 0);
        if (!spawnerConfig.contains("Entities." + type + ".Custom-ECO-Cost"))
            spawnerConfig.addDefault("Entities." + type + ".Custom-ECO-Cost", 0);
        if (!spawnerConfig.contains("Entities." + type + ".Custom-XP-Cost"))
            spawnerConfig.addDefault("Entities." + type + ".Custom-XP-Cost", 0);
        if (!spawnerConfig.contains("Entities." + type + ".Tick-Rate"))
            spawnerConfig.addDefault("Entities." + type + ".Tick-Rate", "800:200");

        if (!spawnerConfig.contains("Entities." + type + ".Spawn-Effect"))
            spawnerConfig.addDefault("Entities." + type + ".Spawn-Effect", "NONE");
        if (!spawnerConfig.contains("Entities." + type + ".Spawn-Effect-Particle"))
            spawnerConfig.addDefault("Entities." + type + ".Spawn-Effect-Particle", "REDSTONE");
        if (!spawnerConfig.contains("Entities." + type + ".Entity-Spawn-Particle"))
            spawnerConfig.addDefault("Entities." + type + ".Entity-Spawn-Particle", "SMOKE");
        if (!spawnerConfig.contains("Entities." + type + ".Spawner-Spawn-Particle"))
            spawnerConfig.addDefault("Entities." + type + ".Spawner-Spawn-Particle", "FIRE");

        if (!spawnerConfig.contains("Entities." + type + ".Particle-Amount"))
            spawnerConfig.addDefault("Entities." + type + ".Particle-Amount", "NORMAL");

        if (!spawnerConfig.contains("Entities." + type + ".Particle-Effect-Boosted-Only"))
            spawnerConfig.addDefault("Entities." + type + ".Particle-Effect-Boosted-Only", false);
    }

    private void loadDataFile() {
        this.dataFile.getConfig().options().copyDefaults(true);
        this.dataFile.saveConfig();
    }

    public void reload() {
        this.locale.reloadMessages();
        this.langFile.createNewFile("Loading language file", "EpicSpawners language file");
        this.spawnerFile.createNewFile("Loading Spawners File", "EpicSpawners Spawners File");
        this.hooksFile.createNewFile("Loading hookHandler File", "EpicSpawners Spawners File");
        this.references = new References();
        this.blacklistHandler.reload();
        this.reloadConfig();
        this.saveConfig();
    }

    public Locale getLocale() {
        return locale;
    }

    public static EpicSpawnersPlugin getInstance() {
        return INSTANCE;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public BoostManager getBoostManager() {
        return boostManager;
    }

    public PlayerActionManager getPlayerActionManager() {
        return playerActionManager;
    }

    public HologramHandler getHologramHandler() {
        return hologramHandler;
    }

    public AppearanceHandler getAppearanceHandler() {
        return appearanceHandler;
    }

    public SpawnerEditor getSpawnerEditor() {
        return spawnerEditor;
    }

    public BlacklistHandler getBlacklistHandler() {
        return blacklistHandler;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public Shop getShop() {
        return shop;
    }

    public Heads getHeads() {
        return heads;
    }

    public ServerVersion getServerVersion() {
        return serverVersion;
    }

    public boolean isServerVersion(ServerVersion version) {
        return serverVersion == version;
    }

    public boolean isServerVersion(ServerVersion... versions) {
        return ArrayUtils.contains(versions, serverVersion);
    }

    public boolean isServerVersionAtLeast(ServerVersion version) {
        return serverVersion.ordinal() >= version.ordinal();
    }

    public boolean canBuild(Player player, Location location) {
        if (player.hasPermission(getDescription().getName() + ".bypass")) {
            return true;
        }

        for (ProtectionPluginHook hook : protectionHooks)
            if (!hook.canBuild(player, location)) return false;
        return true;
    }

    public boolean isInFaction(String name, Location l) {
        return factionsHook != null && factionsHook.isInClaim(l, name);
    }

    public String getFactionId(String name) {
        return (factionsHook != null) ? factionsHook.getClaimID(name) : null;
    }

    public boolean isInTown(String name, Location l) {
        return townyHook != null && townyHook.isInClaim(l, name);
    }

    public String getTownId(String name) {
        return (townyHook != null) ? townyHook.getClaimID(name) : null;
    }

    public boolean isInIsland(String name, Location l) {
        return (aSkyblockHook != null && aSkyblockHook.isInClaim(l, name)) || (uSkyblockHook != null && uSkyblockHook.isInClaim(l, name));
    }

    @SuppressWarnings("deprecation")
    public String getIslandId(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId().toString();
    }

    @Override
    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }

    @Override
    public ItemStack newSpawnerItem(SpawnerData data, int amount) {
        return newSpawnerItem(data, amount, 1);
    }

    @Override
    public ItemStack newSpawnerItem(SpawnerData data, int amount, int stackSize) {
        Preconditions.checkArgument(stackSize > 0, "Stack size must be greater than or equal to 0");

        ItemStack item = new ItemStack(Material.MOB_SPAWNER, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Methods.compileName(data.getIdentifyingName(), stackSize, true));
        item.setItemMeta(meta);

        return item;
    }

    @Override
    public SpawnerData getSpawnerDataFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        String name = item.getItemMeta().getDisplayName();
        if (name == null) return null;

        if (name.contains(":")) {
            String value = name.replace(String.valueOf(ChatColor.COLOR_CHAR), "").split(":")[0];
            return spawnerManager.getSpawnerData(value.toLowerCase().replace("_", " "));
        }

        String typeName = name.replace("_", " ");
        for (EntityType type : EntityType.values()) {
            if (!type.isSpawnable() || !type.isAlive()) continue;

            if (typeName.contains(type.name().toLowerCase())) {
                return spawnerManager.getSpawnerData(type.name().toLowerCase().replace("_", " "));
            }
        }

        return null;
    }

    @Override
    public SpawnerDataBuilder createSpawnerData(String identifier) {
        return new ESpawnerDataBuilder(identifier);
    }

    @Override
    public int getStackSizeFromItem(ItemStack item) {
        Preconditions.checkNotNull(item, "Cannot get stack size of null item");
        if (!item.hasItemMeta()) return 1;

        String name = item.getItemMeta().getDisplayName();
        if (name == null) return 1;

        String amount = name.replace(String.valueOf(ChatColor.COLOR_CHAR), "").split(":")[1];
        return NumberUtils.toInt(amount, 1);
    }

    @Override
    public void registerProtectionHook(ProtectionPluginHook hook) {
        Preconditions.checkNotNull(hook, "Cannot register null hook");
        Preconditions.checkNotNull(hook.getPlugin(), "Protection plugin hook returns null plugin instance (#getPlugin())");

        JavaPlugin hookPlugin = hook.getPlugin();
        for (ProtectionPluginHook existingHook : protectionHooks) {
            if (existingHook.getPlugin().equals(hookPlugin)) {
                throw new IllegalArgumentException("Hook already registered");
            }
        }

        this.hooksFile.getConfig().addDefault("hooks." + hookPlugin.getName(), true);
        if (!hooksFile.getConfig().getBoolean("hooks." + hookPlugin.getName(), true)) return;

        this.protectionHooks.add(hook);
        this.getLogger().info("Registered protection hook for plugin: " + hook.getPlugin().getName());
    }

}