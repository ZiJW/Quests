Quests YAML name-lookup data-loss reproduction

Scope
The public API call getStorage().getQuesterLastKnownName(uuid) clears a registered quester's current quests,
objective progress and completion counts when YAML storage is used. A subsequent save persists that loss.
This is an API-consumer reproduction. It does not show that ordinary login/restart code invokes this method.
Related upstream report: https://github.com/PikaMug/Quests/issues/2201

Environment tested
Paper 1.21.4 build 232, Java 21, YAML player storage.
Baseline Quests: official CI build 571, source da9ecdd02ff6e7280cdf78fa9b3326b36aecce81.
Patched Quests: complete seven-module Maven package build with commit
8264c7d3b27f1cc236b48f86a92409cbf60d3892 (only the name-lookup clear was removed).
The local build's plugin metadata retains the upstream Jenkins placeholder in its version suffix;
the source commit and the jar SHA-256 identify the actual tested artifact.
The same probe jar is used for both runs. It contains no mocks or third-party runtime libraries.

Reproduce on a disposable Paper server whose operator has accepted the Minecraft EULA
1. Obtain a Quests jar and the Bukkit/Spigot API jar, and compile this probe:
   python3 build-probe.py --quests-jar /path/to/Quests.jar --bukkit-api-jar /path/to/spigot-api.jar
   Use --java-home /path/to/jdk if javac is not on PATH.
2. Put Quests.jar and NameLookupProbe.jar into the disposable server's plugins directory.
3. Configure Quests storage-method.player-data: yaml.
4. Create the empty file plugins/NameLookupProbe/allow-disposable-server-probe explicitly.
5. Start the server. Wait for startup and Quests delayed initialization to finish.
6. Run this SERVER CONSOLE command: quests-name-lookup-probe
7. Read plugins/NameLookupProbe/evidence-<timestamp>/assertions.txt and the three YAML files there.
8. Stop the server with its normal stop command. Repeat with the fixed Quests jar in a separate instance.

Expected results
Baseline: repeated saves without a name query preserve YAML bytes; the name query then causes six
preservation assertions to fail and removes questData, current quests/stages, and completion counts.
Patched: all 11 live-server assertions pass and before.yml/control-save.yml/after.yml are byte-identical.
The extra null-fallback and uncached-name checks are covered separately by the isolated JVM regression.

Fixture behavior
The console-only probe refuses to run without its explicit marker, before Quests finishes loading,
with a non-YAML backend, or if its dedicated synthetic offline UUID already has data. It builds actual
BukkitQuest/BukkitQuestProgress objects, calls the public CompletableFuture storage methods, preserves
before/control/after files, then removes only its own fixture from the registry and live data directory.
No Minecraft player client, real player account, or mocked server is involved in these Paper runs.

AI assistance was used to prepare this patch and reproduction probe.

Recorded results
Both real runs completed on 2026-11-06 UTC (2026-11-07 in Asia/Shanghai), after explicit EULA acceptance.
Baseline: 5 PASS and 6 FAIL. Patched: 11 PASS and 0 FAIL. Both normal stop operations returned exit code 0,
and both loopback ports were confirmed closed afterward. The fixture was removed from both live data stores.
Detailed input/remapped artifact SHA-256 values, timings and YAML hashes are in results.json.

Recordings and diagnostics
baseline/ and patched/ contain full real server logs, raw stdout transcripts and asciicast v2 recordings.
Only absolute task-workspace prefixes have been replaced with <workspace> for publication. No server
messages were added or removed. Input commands and their exact elapsed times are in commands.jsonl.
The original unredacted recordings were preserved separately in the task workspace.
Suggested replay windows: baseline 80-96 seconds; patched 90-105 seconds.
These are console recordings, not gameplay videos or screenshots from a Minecraft client.
The logs preserve setup diagnostics about the default flat-world generator, legacy material support,
and the probe's use of stdout. Both servers completed initialization, and the probe explicitly checked
loading=false before running the test. These diagnostics did not prevent the controls or assertions.

Visual evidence
console-excerpt.gif files are rendered from the actual recorded console with asciinema agg 1.9.0
(commit b27111e78b17e7c6eb4f7af89fecef25e3fa6841). The excerpts use original playback timing:
baseline 80-96 seconds, patched 90-105 seconds; terminal render size 150 columns x 42 rows.
console-result.png files are terminal renders at 96/105 seconds, 150 columns x 50 rows.
They are rendered console evidence, not screenshots of a Minecraft game client.
The complete console.cast and original-time command records remain alongside the visuals.
The probe source is supplied under the upstream project GPL-3.0 license (LICENSE.txt).
