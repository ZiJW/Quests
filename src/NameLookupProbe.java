package regression;

import me.pikamug.quests.BukkitQuestsPlugin;
import me.pikamug.quests.storage.implementation.file.BukkitQuesterYamlStorage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;

/** Explicit console-only integration probe for a disposable Paper server. */
public final class NameLookupProbe extends JavaPlugin {
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label,
                             final String[] arguments) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("Run this disposable-server test from the server console only.");
            return true;
        }
        if (!Files.exists(getDataFolder().toPath().resolve("allow-disposable-server-probe"))) {
            sender.sendMessage("Missing explicit disposable-server marker file; probe was not run.");
            return true;
        }
        final BukkitQuestsPlugin quests = (BukkitQuestsPlugin) getServer().getPluginManager().getPlugin("Quests");
        if (quests == null || !quests.isEnabled()
                || !(quests.getStorage().getImplementation() instanceof BukkitQuesterYamlStorage)) {
            sender.sendMessage("An enabled Quests plugin using YAML storage is required.");
            return true;
        }
        if (quests.isLoading()) {
            sender.sendMessage("Quests is still running delayed initialization; probe was not run.");
            return true;
        }
        final UUID id = UUID.fromString("d8136823-1872-4f77-a395-375803674bc0");
        final Path dataFile = quests.getDataFolder().toPath().resolve("data").resolve(id + ".yml");
        final Path evidence = getDataFolder().toPath().resolve("evidence-" + System.currentTimeMillis());
        if (Files.exists(dataFile) || quests.getOfflineQuesters().stream().anyMatch(q -> q.getUUID().equals(id))) {
            sender.sendMessage("Reserved fixture UUID already exists; probe was not run.");
            return true;
        }
        try {
            getLogger().info("Real server: " + getServer().getVersion() + "; Quests "
                    + quests.getDescription().getVersion() + "; YAML backend; public async storage API; loading=false.");
            getLogger().info("Loaded Quests code: " + BukkitQuestsPlugin.class.getProtectionDomain()
                    .getCodeSource().getLocation());
            NameLookupScenario.run(quests, quests.getStorage(), id, dataFile, evidence);
            getLogger().info("PASS: name lookup preserved all quest state and persisted YAML bytes.");
        } catch (final Exception | AssertionError failure) {
            getLogger().log(Level.SEVERE, "FAIL: name lookup probe; evidence at " + evidence, failure);
        } finally {
            quests.removeQuester(id);
            try {
                Files.deleteIfExists(dataFile);
            } catch (final Exception failure) {
                getLogger().log(Level.WARNING, "Could not remove fixture data file " + dataFile, failure);
            }
        }
        return true;
    }
}
