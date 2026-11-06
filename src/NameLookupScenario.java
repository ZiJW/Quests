package regression;

import me.pikamug.quests.BukkitQuestsPlugin;
import me.pikamug.quests.player.BukkitQuestProgress;
import me.pikamug.quests.player.BukkitQuester;
import me.pikamug.quests.quests.BukkitQuest;
import me.pikamug.quests.quests.components.BukkitStage;
import me.pikamug.quests.storage.QuesterStorage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Shared scenario for the isolated JVM harness and the real Paper plugin probe. */
public final class NameLookupScenario {
    private NameLookupScenario() {
    }

    /**
     * Seed a dedicated, otherwise empty quester, query its name through the public API, and save it again.
     *
     * @param plugin the Quests plugin host
     * @param storage the production storage facade to exercise
     * @param uniqueId a UUID reserved for this test, never a real player's UUID
     * @param dataFile the YAML file used by the storage implementation
     * @param evidence the directory receiving before/after YAML and the assertion report
     * @throws Exception if setup, persistence, or any preservation assertion fails
     */
    public static void run(final BukkitQuestsPlugin plugin, final QuesterStorage storage, final UUID uniqueId,
                           final Path dataFile, final Path evidence) throws Exception {
        if (Files.exists(dataFile)) {
            throw new IllegalStateException("Refusing to replace an existing player data file: " + dataFile);
        }
        final BukkitQuester quester = plugin.getQuester(uniqueId);
        if (quester.hasBaseData() || !quester.getAmountsCompleted().isEmpty()) {
            throw new IllegalStateException("Refusing to modify an existing quester");
        }
        quester.setLastKnownName("NameLookupFixture");
        final BukkitQuest quest = new BukkitQuest(plugin);
        quest.setId("name-lookup-regression");
        quest.setName("Name lookup regression");
        quest.getStages().add(new BukkitStage());
        final BukkitStage currentStage = new BukkitStage();
        currentStage.setFishToCatch(5);
        quest.getStages().add(currentStage);
        quester.hardStagePut(quest, 1);
        final BukkitQuestProgress progress = new BukkitQuestProgress(quester);
        progress.blocksBroken.add(7);
        progress.setFishCaught(2);
        quester.getQuestProgress().put(quest, progress);
        quester.getAmountsCompleted().put(quest, 3);
        quester.setCompletedQuests(Arrays.asList(quest));
        quester.getCompletedTimes().put(quest, 123456789L);
        quester.setQuestPoints(12);

        if (!Integer.valueOf(1).equals(quester.getCurrentQuests().get(quest))
                || quester.getCurrentStage(quest) != currentStage
                || quester.getQuestProgress().get(quest) != progress
                || !quester.getCompletedQuests().contains(quest)) {
            throw new AssertionError("Fixture setup did not populate the real quester");
        }

        Files.createDirectories(evidence);
        storage.saveQuester(quester).get(10, TimeUnit.SECONDS);
        final byte[] before = Files.readAllBytes(dataFile);
        Files.write(evidence.resolve("before.yml"), before);
        storage.saveQuester(quester).get(10, TimeUnit.SECONDS);
        final byte[] control = Files.readAllBytes(dataFile);
        Files.write(evidence.resolve("control-save.yml"), control);

        final String name = storage.getQuesterLastKnownName(uniqueId).get(10, TimeUnit.SECONDS);
        storage.saveQuester(quester).get(10, TimeUnit.SECONDS);
        final byte[] after = Files.readAllBytes(dataFile);
        Files.write(evidence.resolve("after.yml"), after);

        final List<String> results = new ArrayList<>();
        check(results, "control save preserves YAML bytes", Arrays.equals(before, control));
        check(results, "name lookup returns the stored name", "NameLookupFixture".equals(name));
        check(results, "registered quester identity is preserved", plugin.getQuester(uniqueId) == quester);
        check(results, "current quest and stage are preserved", Integer.valueOf(1).equals(
                quester.getCurrentQuests().get(quest)));
        check(results, "current stage object is preserved", quester.getCurrentStage(quest) == currentStage);
        check(results, "quest progress object is preserved", quester.getQuestProgress().get(quest) == progress);
        check(results, "objective counts are preserved", progress.blocksBroken.equals(Arrays.asList(7))
                && progress.getFishCaught() == 2 && quester.getQuestProgress().get(quest) == progress);
        check(results, "completion count is preserved", Integer.valueOf(3).equals(
                quester.getAmountsCompleted().get(quest)));
        check(results, "completed quests and timestamps are preserved", quester.getCompletedQuests().contains(quest)
                && Long.valueOf(123456789L).equals(quester.getCompletedTimes().get(quest)));
        check(results, "quest points are preserved", quester.getQuestPoints() == 12);
        check(results, "save after name lookup preserves YAML bytes", Arrays.equals(before, after));
        Files.write(evidence.resolve("assertions.txt"), results, StandardCharsets.UTF_8);
        for (final String result : results) {
            System.out.println(result);
        }
        if (results.stream().anyMatch(result -> result.startsWith("FAIL"))) {
            throw new AssertionError("Name lookup lost quest data; see " + evidence);
        }
    }

    private static void check(final List<String> results, final String name, final boolean passed) {
        results.add((passed ? "PASS " : "FAIL ") + name);
    }
}
