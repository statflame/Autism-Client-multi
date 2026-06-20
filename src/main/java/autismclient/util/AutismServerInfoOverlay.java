package autismclient.util;

import autismclient.gui.macro.editor.ActionEditorOverlay;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import autismclient.gui.vanillaui.components.CompactTextInput;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.OverlayTopBar;
import autismclient.gui.vanillaui.components.ScrollState;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectInfoRow;
import autismclient.gui.vanillaui.direct.DirectProgressBar;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.direct.DirectRow;
import autismclient.gui.vanillaui.direct.DirectSlider;
import autismclient.gui.vanillaui.direct.DirectSpacer;
import autismclient.gui.vanillaui.direct.DirectSurface;
import autismclient.gui.vanillaui.direct.DirectTabStrip;
import autismclient.gui.vanillaui.direct.DirectUiButton;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.gui.vanillaui.direct.DirectUiLabel;
import autismclient.gui.vanillaui.direct.DirectViewport;
import autismclient.gui.vanillaui.direct.DirectViewportSlot;
import autismclient.gui.vanillaui.direct.DirectWindow;
import autismclient.modules.PackHideState;
import autismclient.util.macro.PayloadAction;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.resources.Identifier;

public class AutismServerInfoOverlay extends AutismOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final String OVERLAY_ID = "autism-serverinfo";
   private static final int COMPLETION_ID = 1337;
   private static final int HEADER_CONTROL = 12;
   private static final int HEADER_ARROW_WIDTH = 10;
   private static final int HEADER_ARROW_GAP = 3;
   private static final float HEADER_CLICK_DRAG_THRESHOLD = 3.0F;
   private int panelX = 350;
   private int panelY = 30;
   private int panelW = 236;
   private int panelH = 246;
   private static final int ROW_H = 16;
   private static final int PAD = 8;
   private boolean visible = false;
   private boolean collapsed = false;
   private boolean isDragging = false;
   private double dragOffsetX;
   private double dragOffsetY;
   private float pressStartUiX;
   private float pressStartUiY;
   private int pressStartPanelX;
   private int pressStartPanelY;
   private boolean dragMoved = false;
   private final Font textRenderer;
   private final CompactTheme theme = new CompactTheme();
   private final DirectWindow windowNode = new DirectWindow("Server Info");
   private final DirectSurface surface = new DirectSurface(this.theme, this.windowNode);
   private final DirectTabStrip tabStrip = new DirectTabStrip();
   private final CompactTextInput searchField = new CompactTextInput();
   private final CompactTextInput radarSearchField = new CompactTextInput();
   private final DirectSlider probeDelaySlider = new DirectSlider();
   private final DirectProgressBar scanProgressBar = new DirectProgressBar();
   private final DirectViewportSlot pluginListSlot = new DirectViewportSlot();
   private final DirectViewportSlot radarListSlot = new DirectViewportSlot();
   private boolean pluginScrollbarDragging = false;
   private int pluginScrollbarGrabOffset = 0;
   private boolean radarScrollbarDragging = false;
   private int radarScrollbarGrabOffset = 0;
   private long lastUiRebuildMs = 0L;
   private static final String[] TAB_NAMES = new String[]{"Info", "Plugins", "DupeDB"};
   private int activeTab = 0;
   private final List<AutismServerInfoOverlay.ClickRegion> clickRegions = new ArrayList<>();
   private volatile String resolvedIp = null;
   private String lastResolvedAddress = null;
   private volatile boolean resolvingIp = false;
   private final List<String> detectedPlugins = new ArrayList<>();
   private final Map<String, List<String>> pluginCommands = new LinkedHashMap<>();
   private final Map<String, List<String>> pluginChannels = new LinkedHashMap<>();
   private final Map<String, List<String>> pluginGuis = new LinkedHashMap<>();
   private List<AutismServerInfoOverlay.PluginListRow> cachedPluginRows = List.of();
   private String cachedPluginRowsQuery = null;
   private String cachedPluginRowsSelectedKey = null;
   private int cachedPluginRowsWidth = -1;
   private int[] cachedPluginRowOffsets = new int[0];
   private int cachedPluginRowsHeight = 0;
   private int pluginDataRevision = 0;
   private int pluginRowsRevision = 0;
   private int cachedPluginRowsRevision = -1;
   private Map<String, AutismServerInfoOverlay.PluginDetail> cachedPluginDetails = Map.of();
   private int cachedPluginDetailsRevision = -1;
   private final ScrollState pluginScrollState = new ScrollState();
   private final ScrollState radarScrollState = new ScrollState();
   private int pluginContentHeight = 0;
   private int radarContentHeight = 0;
   private String selectedPlugin = null;
   private final Set<String> expandedRadarPlugins = new HashSet<>();
   private String lastRadarAutoCheckSignature = "";
   private long lastRadarAutoCheckAt = 0L;
   private Boolean lastRadarAuthenticated = null;
   private int pluginProbeDelayMs = 50;
   private boolean pluginScanDone = false;
   private boolean pluginScanInProgress = false;
   private long pluginScanStartedAt = 0L;
   private long pluginScanLastResponseAt = 0L;
   private final Set<Integer> pendingPluginProbeIds = new HashSet<>();
   private final Map<Integer, AutismServerInfoOverlay.PluginProbeSpec> pluginProbes = new HashMap<>();
   private final Set<String> observedPluginCommands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
   private final Deque<AutismServerInfoOverlay.PluginProbeRequest> queuedPluginProbes = new ArrayDeque<>();
   private final Map<String, AutismServerInfoOverlay.PluginEvidence> pluginEvidence = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
   private final Map<String, AutismServerInfoOverlay.PluginEvidence> pluginCopyEvidence = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
   private final Map<String, AutismServerInfoOverlay.PluginConfidence> pluginConfidence = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
   private final Map<String, AutismServerInfoOverlay.PluginResultKind> pluginResultKinds = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
   private final Map<String, List<String>> pluginCopyCommands = new LinkedHashMap<>();
   private final Map<String, AutismServerInfoOverlay.PluginScanEntry> scanWorkingEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
   private final Map<String, AutismServerInfoOverlay.PluginScanEntry> legacyScanEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
   private final Map<Integer, AutismServerInfoOverlay.PluginProbeSpec> observedSuggestionRequests = new LinkedHashMap<>();
   private final Deque<AutismServerInfoOverlay.ObservedCommand> recentCommands = new ArrayDeque<>();
   private long nextPluginProbeSendAt = 0L;
   private int pluginScanTotalSteps = 0;
   private boolean pluginScanCompletionAnnounced = false;
   private boolean uiRebuildRequested = true;
   private boolean pluginManualSetupOpen = false;
   private int activeProbeDelayMs = 50;
   private String lastAutoScanContext = null;
   private String scannedServerAddress = null;
   private String lastRadarServerAddress = "";
   private String scannedPluginContextSignature = "";
   private String cachedPluginContextServerAddress = "";
   private String cachedPluginContextBrand = "";
   private String cachedPluginContextSignature = "";
   private boolean pluginContextSignatureDirty = true;
   private long lastPayloadFingerprintRevision = -1L;
   private static final long PLUGIN_SCAN_IDLE_MS = 700L;
   private static final long PLUGIN_SCAN_TIMEOUT_MS = 12000L;
   private static final int DEFAULT_PLUGIN_PROBE_DELAY_MS = 50;
   private static final int MIN_PLUGIN_PROBE_DELAY_MS = 10;
   private static final int MAX_PLUGIN_PROBE_DELAY_MS = 500;
   private static final long PLUGIN_CACHE_MAX_AGE_MS = 1209600000L;
   private static final long PLUGIN_SCAN_SETTLE_MS = 450L;
   private static final long PLUGIN_SCAN_FINAL_SETTLE_MS = 1400L;
   private static final String PLUGIN_SCAN_MODEL_VERSION = "hybrid-plugin-scan-v11";
   private static final boolean PLUGIN_SCAN_TRACE = Boolean.getBoolean("autism.plugin.scan.trace");
   private static final long COMMAND_GUI_LINK_MS = 5000L;
   private static final int MAX_TRACKED_SUGGESTION_REQUESTS = 256;
   private static final int MAX_RECENT_COMMANDS = 24;
   private int pluginScanCommandTreeRoots = 0;
   private int pluginScanSuggestionReplies = 0;
   private int pluginScanSuggestionEntries = 0;
   private static final int PLUGIN_HEADER_H = 14;
   private static final int SHARED_PANEL_WIDTH = 200;
   private static final int PLUGIN_SETUP_WIDTH = 200;
   private static final int PLUGIN_SETUP_HEIGHT = 126;
   private static final int PLUGIN_SCANNING_WIDTH = 200;
   private static final int PLUGIN_SCANNING_HEIGHT = 92;
   private static final int INFO_MIN_WIDTH = 200;
   private static final int INFO_MIN_HEIGHT = 258;
   private static final int PLUGIN_RESULTS_MIN_WIDTH = 200;
   private static final int PLUGIN_RESULTS_MIN_HEIGHT = 280;
   private static final int RADAR_RESULTS_MIN_WIDTH = 200;
   private static final int RADAR_RESULTS_MIN_HEIGHT = 286;
   private static final int RADAR_HEADER_H = 14;
   private static final int RADAR_CARD_H = 22;
   private static final int RADAR_FINDING_H = 18;
   private int infoPreferredWidth = 200;
   private int infoPreferredHeight = 258;
   private int pluginPreferredWidth = 200;
   private int pluginPreferredHeight = 280;
   private static final String[] COMMON_PLUGIN_NAMESPACES = new String[]{
      "essentials",
      "essentialsx",
      "worldedit",
      "worldguard",
      "luckperms",
      "vault",
      "citizens",
      "cmi",
      "cmilib",
      "multiverse-core",
      "multiverse",
      "viaversion",
      "viabackwards",
      "viarewind",
      "geysermc",
      "geyser",
      "floodgate",
      "protocollib",
      "coreprotect",
      "griefprevention",
      "shopkeepers",
      "dynmap",
      "placeholderapi",
      "skinsrestorer",
      "skript",
      "advancedanticheat",
      "vulcan",
      "grimac",
      "matrix",
      "spartan",
      "aac",
      "karhu",
      "verus",
      "nocheatplus",
      "authme",
      "authmereloaded",
      "librelogin",
      "nlogin",
      "openlogin",
      "limboauth",
      "loginsecurity",
      "fastlogin",
      "bungeeguard",
      "deluxemenus",
      "plotsquared",
      "supervanish",
      "packetevents",
      "oraxen",
      "itemsadder",
      "fawe",
      "fastasyncworldedit",
      "luckpermsbukkit",
      "essentialsgeoip",
      "essentialsprotect",
      "essentialsspawn",
      "essentialsxspawn",
      "multiverse-inventories",
      "multiverse-netherportals",
      "worldborder",
      "votifier",
      "nuVotifier",
      "votingplugin",
      "excellentcrates",
      "crazycrates",
      "goldencrates",
      "goldencrate",
      "cratereloaded",
      "magiccrates",
      "simplelootcrates",
      "cratesplus",
      "cratekeys",
      "advancedcrates",
      "phoenixcrates",
      "mysterycrates",
      "deluxecrates",
      "crazyenvoys",
      "crazyrewards",
      "jobs",
      "jobsreborn",
      "mcmmo",
      "towny",
      "factions",
      "factionsuuid",
      "lands",
      "residence",
      "claimchunk",
      "quickshop",
      "quickshop-hikari",
      "chestshop",
      "shopgui",
      "auctionhouse",
      "combatlogx",
      "litebans",
      "advancedban",
      "libertybans",
      "luckpermsgui",
      "tab",
      "tablist",
      "scoreboard",
      "animatedscoreboard",
      "ajleaderboards",
      "ajqueue",
      "spark",
      "sparkbukkit",
      "plan",
      "minimotd",
      "protocolsupport",
      "viaversion",
      "excellentenchants",
      "eco",
      "ecoenchants",
      "mythicmobs",
      "mythiclib",
      "modelengine",
      "mmoitems",
      "mmocore",
      "denizen",
      "citizenscmd",
      "sentinel",
      "npcs",
      "proantitab",
      "pat",
      "papiproxybridge",
      "groupmanager",
      "vulcanbungee",
      "grimacbukkit",
      "negativity",
      "intave",
      "polar",
      "horizon",
      "themis",
      "libreforge",
      "casinoslots",
      "slotmachine",
      "slotmachineplus",
      "roulette",
      "casino",
      "crazycasino",
      "coinflip",
      "deluxecoinflip",
      "crazycoinflip",
      "jackpot",
      "lottery",
      "lotteryplus",
      "blackjack",
      "crash",
      "plinko",
      "casebattle"
   };
   private static final String[] PLUGIN_LIST_PROBE_COMMANDS = new String[]{"/plugins ", "/pl ", "/bukkit:plugins ", "/bukkit:pl "};
   private static final String[] VERSION_PROBE_COMMANDS = new String[]{"/ver ", "/version ", "/about ", "/icanhasbukkit ", "/bukkit:ver ", "/bukkit:version "};
   private static final String[] HELP_PROBE_COMMANDS = new String[]{"/help ", "/? ", "/bukkit:help ", "/minecraft:help "};
   private static final String[] HIGH_VALUE_PLUGIN_HINTS = new String[]{
      "essentials",
      "essentialsx",
      "worldedit",
      "worldguard",
      "luckperms",
      "vault",
      "cmi",
      "citizens",
      "multiverse",
      "viaversion",
      "geyser",
      "floodgate",
      "protocollib",
      "coreprotect",
      "placeholderapi",
      "skinsrestorer",
      "skript",
      "vulcan",
      "grimac",
      "matrix",
      "spartan",
      "authme",
      "librelogin",
      "nlogin",
      "limboauth",
      "loginsecurity",
      "crazycrates",
      "excellentcrates",
      "goldencrates",
      "cratereloaded",
      "deluxemenus",
      "plotsquared",
      "tab",
      "spark",
      "proantitab"
   };
   private static final Set<String> SCANNER_AUTOCOMPLETE_BLOCKLIST = Set.of(
      "aliases",
      "bukkit:aliases",
      "icanhasbukkit",
      "plugins",
      "plugin",
      "pl",
      "version",
      "ver",
      "about",
      "help",
      "?",
      "bukkit:plugins",
      "bukkit:pl",
      "bukkit:version",
      "bukkit:ver",
      "bukkit:help",
      "minecraft:help"
   );
   private static final String ROOT_PROBE_PREFIXES = "abcdefghijklmnopqrstuvwxyz0123456789";
   private static final Map<String, String> ROOT_COMMAND_PLUGIN_ALIASES = Map.ofEntries(
      Map.entry("lp", "luckperms"),
      Map.entry("we", "worldedit"),
      Map.entry("rg", "worldguard"),
      Map.entry("mv", "multiverse-core"),
      Map.entry("npc", "citizens"),
      Map.entry("papi", "placeholderapi"),
      Map.entry("cmi", "cmi"),
      Map.entry("co", "coreprotect"),
      Map.entry("grim", "grimac"),
      Map.entry("geyser", "geysermc"),
      Map.entry("floodgate", "floodgate"),
      Map.entry("viaver", "viaversion"),
      Map.entry("sr", "skinsrestorer"),
      Map.entry("authme", "authme"),
      Map.entry("authmereloaded", "authme"),
      Map.entry("librelogin", "librelogin"),
      Map.entry("nlogin", "nlogin"),
      Map.entry("openlogin", "openlogin"),
      Map.entry("limboauth", "limboauth"),
      Map.entry("loginsecurity", "loginsecurity"),
      Map.entry("fastlogin", "fastlogin"),
      Map.entry("bungeeguard", "bungeeguard"),
      Map.entry("dm", "deluxemenus"),
      Map.entry("plots", "plotsquared"),
      Map.entry("sv", "supervanish"),
      Map.entry("spawn", "essentialsx"),
      Map.entry("home", "essentialsx"),
      Map.entry("homes", "essentialsx"),
      Map.entry("warp", "essentialsx"),
      Map.entry("warps", "essentialsx"),
      Map.entry("tpa", "essentialsx"),
      Map.entry("tpahere", "essentialsx"),
      Map.entry("bal", "essentialsx"),
      Map.entry("balance", "essentialsx"),
      Map.entry("money", "essentialsx"),
      Map.entry("eco", "essentialsx"),
      Map.entry("ban", "essentialsx"),
      Map.entry("kick", "essentialsx"),
      Map.entry("mute", "essentialsx"),
      Map.entry("jail", "essentialsx"),
      Map.entry("seen", "essentialsx"),
      Map.entry("ptime", "essentialsx"),
      Map.entry("pweather", "essentialsx"),
      Map.entry("tppos", "essentialsx"),
      Map.entry("near", "essentialsx"),
      Map.entry("back", "essentialsx"),
      Map.entry("afk", "essentialsx"),
      Map.entry("msg", "essentialsx"),
      Map.entry("r", "essentialsx"),
      Map.entry("reply", "essentialsx"),
      Map.entry("mail", "essentialsx"),
      Map.entry("pay", "essentialsx"),
      Map.entry("sell", "essentialsx"),
      Map.entry("worth", "essentialsx"),
      Map.entry("kit", "essentialsx"),
      Map.entry("kits", "essentialsx"),
      Map.entry("lb", "litebans"),
      Map.entry("litebans", "litebans"),
      Map.entry("ab", "advancedban"),
      Map.entry("advancedban", "advancedban"),
      Map.entry("tab", "tab"),
      Map.entry("pat", "proantitab"),
      Map.entry("proantitab", "proantitab"),
      Map.entry("spark", "spark"),
      Map.entry("plan", "plan"),
      Map.entry("votingplugin", "votingplugin"),
      Map.entry("vote", "votingplugin"),
      Map.entry("votes", "votingplugin"),
      Map.entry("jobs", "jobsreborn"),
      Map.entry("mcmmo", "mcmmo"),
      Map.entry("towny", "towny"),
      Map.entry("f", "factions"),
      Map.entry("factions", "factions"),
      Map.entry("lands", "lands"),
      Map.entry("res", "residence"),
      Map.entry("residence", "residence"),
      Map.entry("qs", "quickshop"),
      Map.entry("quickshop", "quickshop"),
      Map.entry("auctionhouse", "auctionhouse"),
      Map.entry("crazycrates", "crazycrates"),
      Map.entry("excellentcrates", "excellentcrates"),
      Map.entry("goldencrates", "goldencrates"),
      Map.entry("cratereloaded", "cratereloaded"),
      Map.entry("magiccrates", "magiccrates"),
      Map.entry("simplelootcrates", "simplelootcrates"),
      Map.entry("cratesplus", "cratesplus"),
      Map.entry("cratekeys", "cratekeys"),
      Map.entry("casino", "casino"),
      Map.entry("casinoslots", "casinoslots"),
      Map.entry("slotmachine", "slotmachine"),
      Map.entry("coinflip", "coinflip"),
      Map.entry("jackpot", "jackpot"),
      Map.entry("lottery", "lottery"),
      Map.entry("mythicmobs", "mythicmobs"),
      Map.entry("mm", "mythicmobs"),
      Map.entry("mmoitems", "mmoitems"),
      Map.entry("denizen", "denizen"),
      Map.entry("sentinel", "sentinel"),
      Map.entry("vulcan", "vulcan"),
      Map.entry("matrix", "matrix"),
      Map.entry("karhu", "karhu"),
      Map.entry("verus", "verus"),
      Map.entry("ncp", "nocheatplus")
   );
   private static final Map<String, String> COMMAND_FEATURE_LABELS = Map.ofEntries(
      Map.entry("shop", "Shop"),
      Map.entry("shops", "Shop"),
      Map.entry("store", "Shop"),
      Map.entry("stores", "Shop"),
      Map.entry("market", "Market"),
      Map.entry("marketplace", "Market"),
      Map.entry("ah", "Auction"),
      Map.entry("auction", "Auction"),
      Map.entry("auctions", "Auction"),
      Map.entry("crate", "Crates"),
      Map.entry("crates", "Crates"),
      Map.entry("key", "Crates"),
      Map.entry("keys", "Crates"),
      Map.entry("case", "Crates"),
      Map.entry("cases", "Crates"),
      Map.entry("box", "Crates"),
      Map.entry("boxes", "Crates"),
      Map.entry("lootbox", "Crates"),
      Map.entry("lootboxes", "Crates"),
      Map.entry("cratekey", "Crates"),
      Map.entry("cratekeys", "Crates"),
      Map.entry("votekey", "Crates"),
      Map.entry("votekeys", "Crates"),
      Map.entry("casino", "Gambling"),
      Map.entry("slot", "Gambling"),
      Map.entry("slots", "Gambling"),
      Map.entry("slotmachine", "Gambling"),
      Map.entry("roulette", "Gambling"),
      Map.entry("coinflip", "Gambling"),
      Map.entry("cf", "Gambling"),
      Map.entry("jackpot", "Gambling"),
      Map.entry("lottery", "Gambling"),
      Map.entry("lotto", "Gambling"),
      Map.entry("raffle", "Gambling"),
      Map.entry("dice", "Gambling"),
      Map.entry("blackjack", "Gambling"),
      Map.entry("poker", "Gambling"),
      Map.entry("crash", "Gambling"),
      Map.entry("plinko", "Gambling"),
      Map.entry("casebattle", "Gambling"),
      Map.entry("casebattles", "Gambling"),
      Map.entry("wheel", "Gambling"),
      Map.entry("spin", "Gambling"),
      Map.entry("login", "Auth"),
      Map.entry("register", "Auth"),
      Map.entry("reg", "Auth"),
      Map.entry("logout", "Auth"),
      Map.entry("unregister", "Auth"),
      Map.entry("changepassword", "Auth"),
      Map.entry("changepw", "Auth"),
      Map.entry("captcha", "Auth"),
      Map.entry("2fa", "Auth"),
      Map.entry("totp", "Auth"),
      Map.entry("email", "Auth"),
      Map.entry("verify", "Auth"),
      Map.entry("verification", "Auth"),
      Map.entry("premium", "Auth"),
      Map.entry("cracked", "Auth"),
      Map.entry("backpack", "Backpacks"),
      Map.entry("backpacks", "Backpacks"),
      Map.entry("bp", "Backpacks"),
      Map.entry("pv", "Player Vaults"),
      Map.entry("playervault", "Player Vaults"),
      Map.entry("playervaults", "Player Vaults"),
      Map.entry("vaults", "Player Vaults"),
      Map.entry("enderchest", "Ender Chest"),
      Map.entry("echest", "Ender Chest"),
      Map.entry("ec", "Ender Chest"),
      Map.entry("shulker", "Shulker"),
      Map.entry("shulkers", "Shulker"),
      Map.entry("minion", "Minions"),
      Map.entry("minions", "Minions"),
      Map.entry("mine", "Mines"),
      Map.entry("mines", "Mines"),
      Map.entry("kit", "Kits"),
      Map.entry("kits", "Kits")
   );
   private static final Set<String> ANTICHEATS = Set.of(
      "nocheatplus",
      "aac",
      "spartan",
      "matrix",
      "vulcan",
      "grim",
      "grimac",
      "intave",
      "karhu",
      "verus",
      "polar",
      "negativity",
      "themis",
      "fairfight",
      "wraith",
      "horizon",
      "reflex",
      "antiaura",
      "guardian",
      "hac",
      "thotpatrol",
      "alice"
   );
   private static final Set<String> VANILLA_NAMESPACES = Set.of(
      "minecraft", "brigadier", "bukkit", "spigot", "paper", "purpur", "velocity", "bungeecord", "waterfall"
   );
   private static final Set<String> VANILLA_COMMAND_ROOTS = Set.of(
      "advancement",
      "attribute",
      "ban",
      "ban-ip",
      "banlist",
      "bossbar",
      "clear",
      "clone",
      "damage",
      "data",
      "datapack",
      "debug",
      "defaultgamemode",
      "deop",
      "difficulty",
      "effect",
      "enchant",
      "execute",
      "experience",
      "xp",
      "fill",
      "fillbiome",
      "forceload",
      "function",
      "gamemode",
      "gamerule",
      "give",
      "help",
      "item",
      "jfr",
      "kick",
      "kill",
      "list",
      "locate",
      "loot",
      "me",
      "msg",
      "op",
      "pardon",
      "pardon-ip",
      "particle",
      "perf",
      "place",
      "playsound",
      "publish",
      "random",
      "recipe",
      "reload",
      "return",
      "ride",
      "rotate",
      "save-all",
      "save-off",
      "save-on",
      "say",
      "schedule",
      "scoreboard",
      "seed",
      "setblock",
      "setidletimeout",
      "setworldspawn",
      "spawnpoint",
      "spectate",
      "spreadplayers",
      "stop",
      "stopsound",
      "summon",
      "tag",
      "team",
      "teammsg",
      "tm",
      "teleport",
      "tell",
      "tellraw",
      "tick",
      "time",
      "title",
      "tp",
      "transfer",
      "trigger",
      "w",
      "weather",
      "whitelist",
      "worldborder"
   );

   public AutismServerInfoOverlay(Font textRenderer) {
      this.textRenderer = textRenderer;
      this.buildUi();
   }

   private void buildUi() {
      this.windowNode.setCenterTitle(false);
      this.windowNode.setTitleTone(UiTone.LABEL);
      this.windowNode.setHeaderControls(true, true);
      this.windowNode
         .setTitleAreaInsets(this.panelPadding() + 1, this.panelPadding() + this.headerControlSize() + this.headerArrowWidth() + this.headerArrowGap() + 12);
      this.windowNode.content().setGap(this.contentGap()).setPadding(DirectUiInsets.all(this.panelPadding()));
      this.tabStrip.setTabs(TAB_NAMES).setActiveIndex(this.activeTab).setOnSelect(this::selectTab);
      this.searchField
         .setPlaceholder("Search plugins...")
         .setPreferredWidth(this.pluginSearchWidth())
         .setFieldHeight(this.searchFieldHeight())
         .setOnChange(text -> {
            this.pluginScrollState.jumpTo(0, 0);
            this.requestUiRebuild();
         });
      this.radarSearchField
         .setPlaceholder("Search radar...")
         .setPreferredWidth(this.pluginSearchWidth())
         .setFieldHeight(this.searchFieldHeight())
         .setOnChange(text -> {
            this.radarScrollState.jumpTo(0, 0);
            this.requestUiRebuild();
         });
      this.probeDelaySlider
         .setRange(10.0F, 500.0F)
         .setStep(10.0F)
         .setValue(this.pluginProbeDelayMs)
         .setOnChange(value -> this.setPluginProbeDelayMs(Math.round(value)));
      this.scanProgressBar.setProgress(0.0F);
   }

   public void saveState() {
      AutismSharedState shared = AutismSharedState.get();
      this.rememberCurrentTabSize();
      String stateAddress = this.currentServerAddress();
      if (stateAddress.isEmpty() && this.scannedServerAddress != null) {
         stateAddress = this.scannedServerAddress;
      }

      shared.setServerDataOverlayActiveTab(this.activeTab);
      shared.setServerDataOverlayPluginScrollOffset(this.pluginScrollState.targetOffset());
      shared.setServerDataOverlaySelectedPlugin(this.selectedPlugin);
      shared.setServerDataOverlayStateAddress(stateAddress);
      shared.setServerDataOverlayProbeDelayMs(this.pluginProbeDelayMs);
      shared.setServerDataOverlayInfoWidth(this.infoPreferredWidth);
      shared.setServerDataOverlayInfoHeight(this.infoPreferredHeight);
      shared.setServerDataOverlayPluginWidth(this.pluginPreferredWidth);
      shared.setServerDataOverlayPluginHeight(this.pluginPreferredHeight);
      this.cacheCurrentScan();
      this.saveLayout();
   }

   public void restoreState() {
      AutismSharedState shared = AutismSharedState.get();
      this.restoreLayout();
      this.activeTab = Math.max(0, Math.min(TAB_NAMES.length - 1, shared.getServerDataOverlayActiveTab()));
      this.pluginProbeDelayMs = Math.max(10, Math.min(500, shared.getServerDataOverlayProbeDelayMs()));
      this.infoPreferredWidth = Math.max(this.infoMinWidth(), shared.getServerDataOverlayInfoWidth());
      this.infoPreferredHeight = Math.max(this.infoMinHeight(), shared.getServerDataOverlayInfoHeight());
      this.pluginPreferredWidth = Math.max(this.pluginResultsMinWidth(), shared.getServerDataOverlayPluginWidth());
      this.pluginPreferredHeight = Math.max(this.pluginResultsMinHeightPreset(), shared.getServerDataOverlayPluginHeight());
      this.syncScanStateForCurrentServer();
      String stateAddress = this.currentServerAddress();
      if (stateAddress.isEmpty() && this.scannedServerAddress != null) {
         stateAddress = this.scannedServerAddress;
      }

      if (!stateAddress.isEmpty() && stateAddress.equals(shared.getServerDataOverlayStateAddress())) {
         this.pluginScrollState.restore(shared.getServerDataOverlayPluginScrollOffset());
         this.selectedPlugin = shared.getServerDataOverlaySelectedPlugin();
         if (this.selectedPlugin != null && this.selectedPlugin.isBlank()) {
            this.selectedPlugin = null;
         }

         if (this.selectedPlugin != null && !this.hasDetectedPlugin(this.selectedPlugin)) {
            this.selectedPlugin = null;
         }
      } else {
         this.pluginScrollState.jumpTo(0, 0);
         this.selectedPlugin = null;
      }

      if (this.activeTab == 0) {
         this.applyInfoLayout();
      } else if (this.activeTab == 2) {
         this.applyRadarLayout();
      } else if (this.pluginScanInProgress) {
         this.applyPluginScanningLayout();
      } else if (!this.pluginScanDone) {
         this.applyPluginSetupLayout();
      }

      this.tabStrip.setActiveIndex(this.activeTab);
      this.probeDelaySlider.setValue(this.pluginProbeDelayMs);
   }

   public static boolean shouldRestoreSavedVisible() {
      AutismWindowLayout layout = AutismSharedState.get().getWindowLayout("autism-serverinfo");
      return layout != null && layout.visible;
   }

   private void selectTab(int tabIdx) {
      this.rememberCurrentTabSize();
      this.activeTab = Math.max(0, Math.min(TAB_NAMES.length - 1, tabIdx));
      this.tabStrip.setActiveIndex(this.activeTab);
      this.pluginScrollState.jumpTo(0, 0);
      this.radarScrollState.jumpTo(0, 0);
      this.selectedPlugin = null;
      if (this.activeTab == 0) {
         this.applyInfoLayout();
      } else if (this.activeTab == 2) {
         this.applyRadarLayout();
      } else if (this.pluginScanInProgress) {
         this.applyPluginScanningLayout();
      } else if (!this.pluginScanDone) {
         this.applyPluginSetupLayout();
      } else {
         this.applyPluginResultsLayout();
      }

      this.saveState();
   }

   private void rebuildUi() {
      this.uiRebuildRequested = false;
      this.windowNode.content().clearChildren();
      this.tabStrip.setActiveIndex(this.activeTab);
      this.probeDelaySlider.setValue(this.pluginProbeDelayMs);
      this.scanProgressBar.setProgress(this.getPluginScanProgress());
      if (!this.pluginScanInProgress || this.activeTab == 2) {
         this.windowNode.content().add(this.tabStrip);
      }

      if (this.activeTab == 0) {
         this.buildInfoUi();
      } else if (this.activeTab == 2) {
         this.buildRadarUi();
      } else if (!this.pluginScanDone && !this.pluginScanInProgress) {
         this.buildPluginSetupUi();
      } else if (this.pluginScanInProgress) {
         this.buildPluginScanningUi();
      } else {
         this.buildPluginResultsUi();
      }
   }

   private void requestUiRebuild() {
      this.uiRebuildRequested = true;
   }

   private void buildInfoUi() {
      ServerData entry = MC.getCurrentServer();
      String displayedAddress = this.getDisplayedServerAddress();
      String realIp = this.getDisplayedRealIp(displayedAddress);
      String software = this.getSoftwareGuess(entry);
      String versionNote = this.getVersionNote(entry);
      String ping = entry != null ? entry.ping + " ms" : "--";
      String players = "--";
      if (MC.getConnection() != null) {
         int online = MC.getConnection().getListedOnlinePlayers().size();
         players = entry != null && entry.players != null ? online + " / " + entry.players.max() : String.valueOf(online);
      }

      String proto = entry != null ? String.valueOf(entry.protocol) : "--";
      String diff = MC.level != null ? MC.level.getDifficulty().getDisplayName().getString() : "--";
      String time = "--";
      if (MC.level != null) {
         long dayCount = MC.level.getDayTime() / 24000L;
         long timeOfDay = MC.level.getDayTime() % 24000L;
         int hours = (int)((timeOfDay / 1000L + 6L) % 24L);
         int minutes = (int)(timeOfDay % 1000L * 60L / 1000L);
         time = "Day " + dayCount + " (" + String.format("%02d:%02d", hours, minutes) + ")";
      }

      double estimatedTps = AutismSharedState.get().getEstimatedTps();
      String tps = estimatedTps > 0.0 ? String.format("%.1f", Math.min(20.0, estimatedTps)) : "--";
      this.addInfoRow("IP:", displayedAddress, null, () -> this.copyClipboardValue(displayedAddress, "Server address copied.", "Server address unavailable."));
      String host = this.extractLookupHost(displayedAddress);
      if (!host.isBlank() && (!host.equals(realIp) || this.resolvingIp)) {
         this.addInfoRow("Real IP:", realIp, null, this::copyResolvedServerIp);
      }

      this.addInfoRow("Version:", this.getReportedVersion(entry), null);
      this.addInfoRow("Real Version:", this.getRealServerVersion(), null);
      this.addInfoRow("Brand:", this.getLiveBrand(), null);
      if (!"--".equals(software)) {
         this.addInfoRow("Software:", software, null);
      }

      this.addInfoRow("Ping:", ping, null);
      this.addInfoRow("Players:", players, null);
      this.addInfoRow("Protocol:", proto, null);
      if (!"--".equals(versionNote)) {
         this.addInfoRow("Version Note:", versionNote, AutismColors.packetYellow());
      }

      this.addInfoRow("Difficulty:", diff, null);
      this.addInfoRow("World:", this.getCurrentWorldName(), null);
      this.addInfoRow("Time:", time, null);
      this.addInfoRow("TPS:", tps, null);
      List<String> detectedAcs = this.getDetectedAnticheats();
      if (this.pluginScanInProgress) {
         this.addInfoRow("AntiCheats:", "Scanning...", -48060);
      } else if (!this.pluginScanDone) {
         this.addInfoRow("AntiCheats:", "Probe Plugins First", -48060);
      } else if (detectedAcs.isEmpty()) {
         this.addInfoRow("AntiCheats:", "None detected", null);
      } else {
         this.addInfoRow("AntiCheats:", String.join(", ", detectedAcs), -48060);
      }

      this.windowNode.content().add(new DirectSpacer(0.0F, 2.0F));
      this.windowNode
         .content()
         .add(
            new DirectUiButton("Copy Report", DirectUiButton.Variant.SECONDARY, this::copyServerData).setGrowX(true).setButtonHeight(this.actionButtonHeight())
         );
   }

   private void addInfoRow(String label, String value, Integer valueColor) {
      this.addInfoRow(label, value, valueColor, null);
   }

   private void addInfoRow(String label, String value, Integer valueColor, Runnable onPress) {
      this.windowNode.content().add(new DirectInfoRow(label, value).setLabelWidth(this.infoLabelWidth()).setValueColorOverride(valueColor).setOnPress(onPress));
   }

   private void buildPluginSetupUi() {
      this.windowNode.content().add(this.autoProbeToggleButton());
      this.windowNode.content().add(new DirectUiLabel("Auto-Scans on join.", UiTone.MUTED));
      this.windowNode.content().add(new DirectSpacer(0.0F, 2.0F));
      this.windowNode.content().add(new DirectUiLabel("Start probing manually", UiTone.MUTED));
      this.windowNode.content().add(new DirectInfoRow("Delay:", this.pluginProbeDelayMs + " ms").setLabelWidth(this.pluginSetupLabelWidth()));
      this.windowNode.content().add(this.probeDelaySlider);
      this.windowNode.content().add(new DirectUiLabel("Default: 50 ms", UiTone.MUTED));
      this.windowNode.content().add(new DirectUiLabel("If you get kicked increase the delay.", UiTone.MUTED));
      this.windowNode
         .content()
         .add(
            new DirectUiButton("Start Probing", DirectUiButton.Variant.SECONDARY, () -> this.startManualPluginScan())
               .setGrowX(true)
               .setButtonHeight(this.actionButtonHeight())
         );
      if (!this.detectedPlugins.isEmpty()) {
         this.windowNode.content().add(new DirectUiButton("Back to Results", DirectUiButton.Variant.SECONDARY, () -> {
            this.pluginManualSetupOpen = false;
            this.pluginScanDone = true;
            this.pluginScanInProgress = false;
            this.applyPluginResultsLayout();
            this.saveState();
         }).setGrowX(true).setButtonHeight(this.actionButtonHeight()));
      }
   }

   private void buildPluginScanningUi() {
      int progressPercent = Math.max(0, Math.min(100, Math.round(this.getPluginScanProgress() * 100.0F)));
      this.windowNode
         .content()
         .add(
            new DirectInfoRow("State:", this.getPluginScanPhaseLabel())
               .setLabelWidth(this.pluginScanLabelWidth())
               .setValueColorOverride(this.getPluginScanPhaseColor())
         );
      this.windowNode
         .content()
         .add(
            new DirectInfoRow("Detail:", this.getPluginScanPhaseDetail())
               .setLabelWidth(this.pluginScanLabelWidth())
               .setValueColorOverride(this.getPluginScanPhaseColor())
         );
      int foundCount = this.getDisplayedPluginCount();
      this.windowNode
         .content()
         .add(new DirectInfoRow("Found:", foundCount + " plugin" + (foundCount == 1 ? "" : "s")).setLabelWidth(this.pluginScanLabelWidth()));
      this.windowNode
         .content()
         .add(
            new DirectInfoRow("Progress:", progressPercent + "%")
               .setLabelWidth(this.pluginScanLabelWidth())
               .setValueColorOverride(this.getPluginScanPhaseColor())
         );
      this.windowNode.content().add(this.scanProgressBar);
   }

   private void buildPluginResultsUi() {
      this.windowNode.content().add(this.autoProbeToggleButton());
      this.searchField.setPreferredWidth(Math.max(this.pluginSearchWidth(), this.panelW - this.pluginSearchReserveWidth()));
      this.windowNode.content().add(this.searchField);
      DirectRow buttons = new DirectRow().setGap(this.buttonRowGap());
      buttons.add(
         new DirectUiButton("Rescan", DirectUiButton.Variant.SECONDARY, () -> this.openPluginProbeSetup())
            .setGrowX(true)
            .setButtonHeight(this.actionButtonHeight())
      );
      buttons.add(
         new DirectUiButton("Copy Plugins", DirectUiButton.Variant.SECONDARY, this::copyPluginList).setGrowX(true).setButtonHeight(this.actionButtonHeight())
      );
      this.windowNode.content().add(buttons);
      String query = this.searchField.text().toLowerCase(Locale.ROOT);
      int filteredCount = 0;

      for (AutismServerInfoOverlay.PluginDetail detail : this.getCachedPluginDetails()) {
         if (detail != null && (query.isEmpty() || detail.searchText.contains(query))) {
            filteredCount++;
         }
      }

      String header = this.detectedPlugins.isEmpty() ? "No plugins detected" : "Detected: " + filteredCount + " plugin" + (filteredCount == 1 ? "" : "s");
      this.windowNode.content().add(new DirectUiLabel(header, UiTone.MUTED));
      float viewportHeight = Math.max(this.pluginViewportMinHeight(), (float)(this.panelH - this.getPluginResultsReservedHeight()));
      this.pluginListSlot.setPreferredHeight(viewportHeight);
      this.windowNode.content().add(this.pluginListSlot);
   }

   private void buildRadarUi() {
      AutismDupeRadar.RadarState radarState = AutismDupeRadar.state();
      this.syncRadarAuthState(radarState);
      int pluginCount = this.radarPluginSnapshots().size();
      this.maybeAutoCheckRadar(radarState, pluginCount);
      radarState = AutismDupeRadar.state();
      this.syncRadarAuthState(radarState);
      if (!radarState.authenticated()) {
         this.windowNode.content().add(new DirectUiLabel("Login to continue.", UiTone.MUTED).setTrimToBounds(true));
         if (radarState.error() != null && !radarState.error().isBlank()) {
            this.windowNode
               .content()
               .add(new DirectInfoRow("Error:", radarState.error()).setLabelWidth(38.0F).setValueColorOverride(AutismColors.dangerText()));
         }

         DirectRow loginButtons = new DirectRow().setGap(this.buttonRowGap());
         loginButtons.add(
            new DirectUiButton(radarState.authenticating() ? "Login..." : "Login DupeDB", DirectUiButton.Variant.SUCCESS, AutismDupeRadar::login)
               .setGrowX(true)
               .setButtonHeight(this.actionButtonHeight())
         );
         this.windowNode.content().add(loginButtons);
      } else {
         this.windowNode.content().add(new DirectInfoRow("Server:", this.radarServerLabel()).setLabelWidth(42.0F));
         if (!this.pluginScanDone) {
            this.windowNode
               .content()
               .add(new DirectInfoRow("Alert:", "Plugins not scanned").setLabelWidth(42.0F).setValueColorOverride(AutismColors.dangerText()));
         }

         DirectRow authButtons = new DirectRow().setGap(this.buttonRowGap());
         authButtons.add(
            new DirectUiButton("Logout", DirectUiButton.Variant.DANGER, AutismDupeRadar::logout).setGrowX(true).setButtonHeight(this.actionButtonHeight())
         );
         this.windowNode.content().add(authButtons);
         this.radarSearchField.setPreferredWidth(Math.max(this.pluginSearchWidth(), this.panelW - this.pluginSearchReserveWidth()));
         this.windowNode.content().add(this.radarSearchField);
         float viewportHeight = Math.max(this.pluginViewportMinHeight(), (float)(this.panelH - this.getRadarResultsReservedHeight(!this.pluginScanDone)));
         this.radarListSlot.setPreferredHeight(viewportHeight);
         this.windowNode.content().add(this.radarListSlot);
      }
   }

   private void syncRadarAuthState(AutismDupeRadar.RadarState state) {
      boolean authenticated = state != null && state.authenticated();
      if (this.lastRadarAuthenticated != null && this.lastRadarAuthenticated != authenticated) {
         this.lastRadarAutoCheckSignature = "";
         this.lastRadarAutoCheckAt = 0L;
         this.expandedRadarPlugins.clear();
         this.radarScrollState.jumpTo(0, 0);
      }

      this.lastRadarAuthenticated = authenticated;
   }

   private DirectUiButton autoProbeToggleButton() {
      boolean autoOn = AutismConfig.getGlobal().autoProbePlugins;
      return new DirectUiButton(
            "Auto Probe: " + (autoOn ? "ON" : "OFF"), autoOn ? DirectUiButton.Variant.SUCCESS : DirectUiButton.Variant.SECONDARY, this::toggleAutoProbePlugins
         )
         .setGrowX(true)
         .setButtonHeight(this.actionButtonHeight());
   }

   private void toggleAutoProbePlugins() {
      AutismConfig cfg = AutismConfig.getGlobal();
      cfg.autoProbePlugins = !cfg.autoProbePlugins;
      cfg.save();
      this.lastAutoScanContext = null;
      this.requestUiRebuild();
      this.saveState();
   }

   private void openPluginProbeSetup() {
      this.pluginScanInProgress = false;
      this.pluginScanDone = false;
      this.pluginManualSetupOpen = true;
      this.pendingPluginProbeIds.clear();
      this.pluginProbes.clear();
      this.queuedPluginProbes.clear();
      this.nextPluginProbeSendAt = 0L;
      this.pluginScanStartedAt = 0L;
      this.pluginScanLastResponseAt = 0L;
      this.pluginScanTotalSteps = 0;
      this.pluginScanCompletionAnnounced = false;
      this.selectedPlugin = null;
      this.searchField.setText("");
      this.searchField.setFocused(false);
      this.pluginScrollState.jumpTo(0, 0);
      this.activeTab = 1;
      this.applyPluginSetupLayout();
      this.saveState();
   }

   private String currentServerAddress() {
      ServerData entry = MC.getCurrentServer();
      if (entry != null && entry.ip != null && !entry.ip.isBlank()) {
         return entry.ip.trim().toLowerCase(Locale.ROOT);
      } else {
         if (MC.getConnection() != null && MC.getConnection().getConnection() != null) {
            SocketAddress address = MC.getConnection().getConnection().getRemoteAddress();
            if (address instanceof InetSocketAddress inet) {
               String host = inet.getHostString();
               if ((host == null || host.isBlank()) && inet.getAddress() != null) {
                  host = inet.getAddress().getHostAddress();
               }

               if (host != null && !host.isBlank()) {
                  return (host + ":" + inet.getPort()).trim().toLowerCase(Locale.ROOT);
               }
            } else if (address != null) {
               String raw = address.toString();
               if (raw != null && !raw.isBlank()) {
                  return raw.replaceFirst("^/", "").trim().toLowerCase(Locale.ROOT);
               }
            }
         }

         return "";
      }
   }

   private String normalizePluginContextSignature(String signature) {
      return signature == null ? "" : signature.trim().toLowerCase(Locale.ROOT);
   }

   private String currentPluginContextSignature() {
      if (MC.getConnection() == null) {
         return "";
      } else {
         String currentAddress = this.currentServerAddress();
         String brand = MC.getConnection().serverBrand();
         String normalizedBrand = brand == null ? "" : brand.trim().toLowerCase(Locale.ROOT);
         if (!this.pluginContextSignatureDirty
            && currentAddress.equals(this.cachedPluginContextServerAddress)
            && normalizedBrand.equals(this.cachedPluginContextBrand)) {
            return this.cachedPluginContextSignature;
         } else {
            List<String> parts = new ArrayList<>();
            parts.add("scan=hybrid-plugin-scan-v11");
            if (brand != null && !brand.isBlank()) {
               parts.add("brand=" + normalizedBrand);
            }

            try {
               CommandDispatcher<?> dispatcher = MC.getConnection().getCommands();
               if (dispatcher != null) {
                  RootCommandNode<?> root = dispatcher.getRoot();
                  if (root != null && !root.getChildren().isEmpty()) {
                     List<String> rootCommands = new ArrayList<>();

                     for (CommandNode<?> child : root.getChildren()) {
                        String name = child.getName();
                        if (name != null && !name.isBlank()) {
                           rootCommands.add(name.trim().toLowerCase(Locale.ROOT));
                        }
                     }

                     rootCommands.sort(String.CASE_INSENSITIVE_ORDER);
                     if (!rootCommands.isEmpty()) {
                        parts.add("cmd=" + String.join(",", rootCommands));
                     }
                  }
               }
            } catch (Exception var11) {
            }

            this.cachedPluginContextServerAddress = currentAddress;
            this.cachedPluginContextBrand = normalizedBrand;
            this.cachedPluginContextSignature = this.normalizePluginContextSignature(String.join("|", parts));
            this.pluginContextSignatureDirty = false;
            return this.cachedPluginContextSignature;
         }
      }
   }

   private void invalidatePluginContextSignature() {
      this.pluginContextSignatureDirty = true;
   }

   private void setPluginProbeDelayMs(int delayMs) {
      this.pluginProbeDelayMs = Math.max(10, Math.min(500, delayMs));
      AutismSharedState.get().setServerDataOverlayProbeDelayMs(this.pluginProbeDelayMs);
   }

   private void applyClampedBounds() {
      AutismWindowLayout clamped = this.clampToViewport(
         new AutismWindowLayout(this.panelX, this.panelY, this.panelW, this.panelH, this.visible, this.collapsed)
      );
      this.panelX = clamped.x;
      this.panelY = clamped.y;
      this.panelW = clamped.width;
      this.panelH = clamped.height;
   }

   private void applyPluginSetupLayout() {
      this.panelW = this.getSharedPanelWidth();
      this.panelH = this.getPluginSetupPanelHeight();
      this.applyClampedBounds();
      this.requestUiRebuild();
   }

   private void applyPluginScanningLayout() {
      this.panelW = this.getSharedPanelWidth();
      this.panelH = this.getPluginScanningPanelHeight();
      this.applyClampedBounds();
      this.requestUiRebuild();
   }

   private void applyPluginResultsLayout() {
      this.panelW = this.getSharedPanelWidth();
      this.panelH = Math.max(this.getPluginResultsMinHeight(), Math.min(this.getSharedPanelHeight(), this.getPluginResultsMaxHeight()));
      this.applyClampedBounds();
      this.requestUiRebuild();
   }

   private void applyRadarLayout() {
      this.panelW = this.getSharedPanelWidth();
      this.panelH = this.getRadarPanelHeight();
      this.applyClampedBounds();
      this.requestUiRebuild();
   }

   private void applyInfoLayout() {
      this.panelW = this.getSharedPanelWidth();
      this.panelH = this.getSharedPanelHeight();
      this.applyClampedBounds();
      this.requestUiRebuild();
   }

   private int getSharedPanelWidth() {
      DirectViewport viewport = this.surface.viewport();
      return Math.max(this.infoMinWidth(), Math.min(this.sharedPanelWidth(), Math.round(viewport.uiWidth())));
   }

   private int getPluginResultsMinHeight() {
      return this.getSharedPanelHeight();
   }

   private int getPluginResultsMaxHeight() {
      DirectViewport viewport = this.surface.viewport();
      int viewportHeight = Math.round(viewport.uiHeight());
      return Math.max(this.getPluginResultsMinHeight(), viewportHeight - 28);
   }

   private int getRadarResultsMinHeight() {
      return Math.max(286, this.getSharedPanelHeight());
   }

   private int getRadarResultsMaxHeight() {
      DirectViewport viewport = this.surface.viewport();
      int viewportHeight = Math.round(viewport.uiHeight());
      return Math.max(this.getRadarResultsMinHeight(), viewportHeight - 28);
   }

   private boolean isRadarAuthenticated() {
      AutismDupeRadar.RadarState state = AutismDupeRadar.state();
      return state != null && state.authenticated();
   }

   private int getRadarPanelHeight() {
      if (this.isRadarAuthenticated()) {
         return Math.max(this.getRadarResultsMinHeight(), Math.min(this.getSharedPanelHeight(), this.getRadarResultsMaxHeight()));
      } else {
         DirectViewport viewport = this.surface.viewport();
         int viewportLimit = Math.max(this.getRadarLoginPanelHeight(), Math.round(viewport.uiHeight()) - this.viewportHeightMargin());
         return Math.min(this.getRadarLoginPanelHeight(), viewportLimit);
      }
   }

   private int getRadarLoginPanelHeight() {
      AutismDupeRadar.RadarState state = AutismDupeRadar.state();
      boolean hasError = state != null && state.error() != null && !state.error().isBlank();
      int tabHeight = Math.max(14, this.theme.buttonHeight());
      int labelHeight = this.theme.lineHeight(UiTone.MUTED, 3);
      int errorHeight = hasError ? 12 : 0;
      int nodeCount = hasError ? 4 : 3;
      return this.theme.headerHeight()
         + this.panelPadding() * 2
         + this.contentGap() * Math.max(0, nodeCount - 1)
         + tabHeight
         + labelHeight
         + errorHeight
         + this.actionButtonHeight();
   }

   private int getSharedPanelHeight() {
      DirectViewport viewport = this.surface.viewport();
      int measured = Math.max(this.infoMinHeight(), Math.max(this.infoPreferredHeight, this.measurePreferredInfoPanelHeight()));
      return Math.max(this.infoMinHeight(), Math.min(measured, Math.max(this.infoMinHeight(), Math.round(viewport.uiHeight()) - this.viewportHeightMargin())));
   }

   private int getPluginSetupPanelHeight() {
      DirectViewport viewport = this.surface.viewport();
      int measured = Math.max(this.pluginSetupHeight(), this.measurePreferredHeightForState(1, false, false, Math.max(this.panelW, this.pluginSetupWidth())));
      return Math.min(measured, Math.max(this.pluginSetupHeight(), Math.round(viewport.uiHeight()) - this.viewportHeightMargin()));
   }

   private int getPluginScanningPanelHeight() {
      DirectViewport viewport = this.surface.viewport();
      int measured = Math.max(
         this.pluginScanningHeight(), this.measurePreferredHeightForState(1, false, true, Math.max(this.panelW, this.pluginScanningWidth()))
      );
      return Math.min(measured, Math.max(this.pluginScanningHeight(), Math.round(viewport.uiHeight()) - this.viewportHeightMargin()));
   }

   private int getPluginResultsReservedHeight() {
      int contentPadding = this.panelPadding() * 2;
      int contentGap = this.contentGap() * 4;
      int autoToggleHeight = this.actionButtonHeight();
      int searchHeight = this.searchFieldHeight();
      int buttonRowHeight = this.actionButtonHeight();
      int headerLabelHeight = 13;
      return this.theme.headerHeight() + contentPadding + contentGap + autoToggleHeight + searchHeight + buttonRowHeight + headerLabelHeight;
   }

   private int getRadarResultsReservedHeight(boolean includePluginAlert) {
      int contentPadding = this.panelPadding() * 2;
      int rowCount = includePluginAlert ? 2 : 1;
      int contentGap = this.contentGap() * (3 + rowCount);
      int infoRows = 12 * rowCount;
      int actionRows = this.actionButtonHeight();
      int searchHeight = this.searchFieldHeight();
      return this.theme.headerHeight() + contentPadding + contentGap + infoRows + actionRows + searchHeight;
   }

   private int measurePreferredInfoPanelHeight() {
      return Math.max(
         this.infoMinHeight(),
         this.measurePreferredHeightForState(0, this.pluginScanDone, this.pluginScanInProgress, Math.max(this.infoMinWidth(), this.panelW))
      );
   }

   private int measurePreferredHeightForState(int tab, boolean scanDone, boolean scanInProgress, int measureWidth) {
      int previousTab = this.activeTab;
      boolean previousScanDone = this.pluginScanDone;
      boolean previousScanInProgress = this.pluginScanInProgress;
      this.activeTab = tab;
      this.pluginScanDone = scanDone;
      this.pluginScanInProgress = scanInProgress;
      this.rebuildUi();
      int measured = Math.round(this.surface.measurePreferredHeight(measureWidth));
      this.activeTab = previousTab;
      this.pluginScanDone = previousScanDone;
      this.pluginScanInProgress = previousScanInProgress;
      this.rebuildUi();
      return measured;
   }

   private int getInfoRequiredHeight() {
      int rowCount = 11;
      String displayedAddress = this.getDisplayedServerAddress();
      String host = this.extractLookupHost(displayedAddress);
      String realIp = this.getDisplayedRealIp(displayedAddress);
      if (!host.isBlank() && (!host.equals(realIp) || this.resolvingIp)) {
         rowCount++;
      }

      ServerData entry = MC.getCurrentServer();
      String software = this.getSoftwareGuess(entry);
      if (!"--".equals(software)) {
         rowCount++;
      }

      String versionNote = this.getVersionNote(entry);
      if (!"--".equals(versionNote)) {
         rowCount++;
      }

      int contentHeight = 5 + rowCount * 13 + 11 + this.actionButtonHeight();
      return this.theme.headerHeight() + this.panelPadding() * 2 + this.contentGap() * 2 + contentHeight;
   }

   private void rememberCurrentTabSize() {
      if (this.activeTab == 0) {
         this.infoPreferredWidth = Math.max(this.infoMinWidth(), this.panelW);
         this.infoPreferredHeight = Math.max(this.getInfoRequiredHeight(), this.panelH);
         this.pluginPreferredWidth = Math.max(this.pluginPreferredWidth, this.infoPreferredWidth);
      } else if (this.activeTab == 2) {
         this.pluginPreferredWidth = Math.max(this.pluginPreferredWidth, this.panelW);
         this.pluginPreferredHeight = Math.max(this.pluginPreferredHeight, Math.min(this.getRadarResultsMaxHeight(), this.panelH));
         this.infoPreferredWidth = Math.max(this.infoPreferredWidth, this.pluginPreferredWidth);
      } else {
         if (this.pluginScanDone && !this.pluginScanInProgress) {
            this.pluginPreferredWidth = Math.max(this.pluginResultsMinWidth(), this.panelW);
            this.pluginPreferredHeight = Math.max(this.getPluginResultsMinHeight(), Math.min(this.getPluginResultsMaxHeight(), this.panelH));
            this.infoPreferredWidth = Math.max(this.infoPreferredWidth, this.pluginPreferredWidth);
         }
      }
   }

   private void clearLocalScanState(String address, String contextSignature) {
      this.pluginScanDone = false;
      this.pluginManualSetupOpen = false;
      this.pluginScanInProgress = false;
      this.pluginScanCompletionAnnounced = false;
      this.pluginScanStartedAt = 0L;
      this.pluginScanLastResponseAt = 0L;
      this.pendingPluginProbeIds.clear();
      this.pluginProbes.clear();
      this.observedPluginCommands.clear();
      this.queuedPluginProbes.clear();
      this.pluginEvidence.clear();
      this.pluginCopyEvidence.clear();
      this.pluginCopyCommands.clear();
      this.pluginConfidence.clear();
      this.pluginResultKinds.clear();
      this.scanWorkingEntries.clear();
      this.legacyScanEntries.clear();
      this.nextPluginProbeSendAt = 0L;
      this.pluginScanTotalSteps = 0;
      this.pluginScanCommandTreeRoots = 0;
      this.pluginScanSuggestionReplies = 0;
      this.pluginScanSuggestionEntries = 0;
      this.scannedServerAddress = address;
      this.scannedPluginContextSignature = this.normalizePluginContextSignature(contextSignature);
      this.detectedPlugins.clear();
      this.pluginCommands.clear();
      this.pluginChannels.clear();
      this.pluginGuis.clear();
      this.invalidatePluginRows();
      this.pluginScrollState.jumpTo(0, 0);
      this.selectedPlugin = null;
      this.lastPayloadFingerprintRevision = -1L;
      this.observedSuggestionRequests.clear();
      this.recentCommands.clear();
      this.invalidatePluginContextSignature();
   }

   private boolean loadCachedScan(String address, String contextSignature) {
      AutismConfig.PluginScanCacheEntry cached = AutismConfig.getGlobal().getPluginScan(address, contextSignature);
      if (cached == null) {
         return false;
      } else {
         this.detectedPlugins.clear();
         if (cached.plugins != null) {
            this.detectedPlugins.addAll(this.dedupePluginNames(cached.plugins));
         }

         this.pluginCommands.clear();
         if (cached.commands != null) {
            this.pluginCommands.putAll(cached.commands);
         }

         this.pluginChannels.clear();
         if (cached.channels != null) {
            this.pluginChannels.putAll(cached.channels);
         }

         this.pluginGuis.clear();
         if (cached.guis != null) {
            this.pluginGuis.putAll(cached.guis);
         }

         this.pluginCopyCommands.clear();
         if (cached.copyCommands != null) {
            this.pluginCopyCommands.putAll(cached.copyCommands);
         }

         this.pluginScrollState.jumpTo(0, 0);
         this.selectedPlugin = null;
         this.pluginScanDone = true;
         this.pluginManualSetupOpen = false;
         this.pluginScanInProgress = false;
         this.pluginScanStartedAt = 0L;
         this.pluginScanLastResponseAt = 0L;
         this.pendingPluginProbeIds.clear();
         this.pluginProbes.clear();
         this.observedPluginCommands.clear();
         this.queuedPluginProbes.clear();
         this.pluginEvidence.clear();
         this.pluginCopyEvidence.clear();
         this.pluginConfidence.clear();
         this.pluginResultKinds.clear();
         this.scanWorkingEntries.clear();
         this.legacyScanEntries.clear();
         this.observedSuggestionRequests.clear();
         this.recentCommands.clear();
         this.nextPluginProbeSendAt = 0L;
         this.pluginScanTotalSteps = 0;
         this.pluginScanCommandTreeRoots = 0;
         this.pluginScanSuggestionReplies = 0;
         this.pluginScanSuggestionEntries = 0;
         this.scannedServerAddress = address;
         this.scannedPluginContextSignature = this.normalizePluginContextSignature(cached.contextSignature);
         Map<String, String> cachedEvidence = cached.evidence == null ? Map.of() : cached.evidence;
         Map<String, String> cachedCopyEvidence = cached.copyEvidence == null ? Map.of() : cached.copyEvidence;
         Map<String, String> cachedConfidence = cached.confidence == null ? Map.of() : cached.confidence;

         for (String plugin : this.detectedPlugins) {
            String key = this.normalizePluginKey(plugin);
            this.pluginEvidence.put(key, this.parseEvidenceName(cachedEvidence.get(key)));
            AutismServerInfoOverlay.PluginEvidence copyEvidence = this.parseEvidenceName(cachedCopyEvidence.get(key));
            if (copyEvidence == AutismServerInfoOverlay.PluginEvidence.UNKNOWN) {
               copyEvidence = this.fallbackCopyEvidence(plugin, this.pluginEvidence.get(key));
            }

            this.pluginCopyEvidence.put(key, copyEvidence);
            this.pluginConfidence.put(key, this.parseConfidenceName(cachedConfidence.get(key), this.confidenceForEvidence(this.pluginEvidence.get(key))));
            this.pluginResultKinds.put(key, this.resultKindForStoredPlugin(plugin, this.pluginEvidence.get(key), this.pluginConfidence.get(key)));
         }

         this.lastPayloadFingerprintRevision = -1L;
         this.syncPayloadFingerprintsIntoCurrentScan();
         this.invalidatePluginRows();
         return true;
      }
   }

   private void cacheCurrentScan() {
      if (this.scannedServerAddress != null && !this.scannedServerAddress.isBlank()) {
         if (this.pluginScanDone && !this.pluginScanInProgress) {
            if (!this.detectedPlugins.isEmpty()) {
               Map<String, String> evidenceSnapshot = new LinkedHashMap<>();
               Map<String, String> copyEvidenceSnapshot = new LinkedHashMap<>();

               for (Entry<String, AutismServerInfoOverlay.PluginEvidence> entry : this.pluginEvidence.entrySet()) {
                  if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                     evidenceSnapshot.put(entry.getKey(), entry.getValue().name());
                  }
               }

               for (Entry<String, AutismServerInfoOverlay.PluginEvidence> entryx : this.pluginCopyEvidence.entrySet()) {
                  if (entryx.getKey() != null && !entryx.getKey().isBlank() && entryx.getValue() != null) {
                     copyEvidenceSnapshot.put(entryx.getKey(), entryx.getValue().name());
                  }
               }

               Map<String, String> confidenceSnapshot = new LinkedHashMap<>();

               for (Entry<String, AutismServerInfoOverlay.PluginConfidence> entryxx : this.pluginConfidence.entrySet()) {
                  if (entryxx.getKey() != null && !entryxx.getKey().isBlank() && entryxx.getValue() != null) {
                     confidenceSnapshot.put(entryxx.getKey(), entryxx.getValue().name());
                  }
               }

               AutismConfig.getGlobal()
                  .putPluginScan(
                     this.scannedServerAddress,
                     this.scannedPluginContextSignature,
                     this.detectedPlugins,
                     this.pluginCommands,
                     evidenceSnapshot,
                     this.pluginChannels,
                     this.pluginGuis,
                     confidenceSnapshot,
                     copyEvidenceSnapshot,
                     this.pluginCopyCommands
                  );
            }
         }
      }
   }

   private void syncScanStateForCurrentServer() {
      String currentAddress = this.currentServerAddress();
      if (!currentAddress.isEmpty()) {
         if (!currentAddress.equals(this.lastRadarServerAddress)) {
            this.lastRadarServerAddress = currentAddress;
            this.lastRadarAutoCheckSignature = "";
            this.lastRadarAutoCheckAt = 0L;
            this.expandedRadarPlugins.clear();
            this.radarScrollState.jumpTo(0, 0);
            AutismDupeRadar.clearServerResults();
         }

         String currentContextSignature = this.currentPluginContextSignature();
         boolean sameAddress = currentAddress.equals(this.scannedServerAddress);
         boolean hasCurrentSignature = !currentContextSignature.isEmpty();
         boolean sameContext = sameAddress && currentContextSignature.equals(this.scannedPluginContextSignature);
         if (!this.pluginManualSetupOpen || !sameAddress || !sameContext && hasCurrentSignature) {
            if (!sameAddress || hasCurrentSignature && !sameContext) {
               this.pluginManualSetupOpen = false;
            }

            if (!sameContext || !this.pluginScanDone && !this.pluginScanInProgress) {
               if (!sameAddress || hasCurrentSignature || !this.pluginScanDone && !this.pluginScanInProgress) {
                  if (!this.loadCachedScan(currentAddress, currentContextSignature)) {
                     this.clearLocalScanState(currentAddress, currentContextSignature);
                  }
               }
            }
         }
      }
   }

   private String normalizePluginKey(String plugin) {
      return AutismPluginPayloadFingerprints.canonicalPluginKey(plugin);
   }

   private int evidenceRank(AutismServerInfoOverlay.PluginEvidence evidence) {
      return switch (evidence) {
         case PAYLOAD_CHANNEL -> 0;
         case COMMAND_GUI -> 4;
         case COMMAND_TREE -> 1;
         case USER_AUTOCOMPLETE -> 8;
         case SCANNER_AUTOCOMPLETE -> 7;
         case NAMESPACE -> 2;
         case ROOT_HINT -> 3;
         case HELP_HINT -> 10;
         case PLUGIN_LIST -> 6;
         case VERSION_HINT -> 5;
         case FEATURE -> 9;
         case UNKNOWN -> 11;
      };
   }

   private int confidenceRank(AutismServerInfoOverlay.PluginConfidence confidence) {
      return switch (confidence == null ? AutismServerInfoOverlay.PluginConfidence.UNKNOWN : confidence) {
         case EXACT -> 0;
         case STRONG -> 1;
         case FEATURE -> 2;
         case UNKNOWN -> 3;
      };
   }

   private AutismServerInfoOverlay.PluginEvidence mergeEvidence(
      AutismServerInfoOverlay.PluginEvidence current, AutismServerInfoOverlay.PluginEvidence candidate
   ) {
      if (current == null) {
         return candidate == null ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : candidate;
      } else if (candidate == null) {
         return current;
      } else {
         return this.evidenceRank(candidate) < this.evidenceRank(current) ? candidate : current;
      }
   }

   private int copyEvidenceRank(AutismServerInfoOverlay.PluginEvidence evidence) {
      return switch (evidence == null ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : evidence) {
         case PAYLOAD_CHANNEL -> 4;
         case COMMAND_GUI -> 5;
         case COMMAND_TREE -> 0;
         case USER_AUTOCOMPLETE, FEATURE -> 6;
         case SCANNER_AUTOCOMPLETE, PLUGIN_LIST -> 2;
         case NAMESPACE, ROOT_HINT -> 1;
         case HELP_HINT, UNKNOWN -> 7;
         case VERSION_HINT -> 3;
      };
   }

   private AutismServerInfoOverlay.PluginEvidence mergeCopyEvidence(
      AutismServerInfoOverlay.PluginEvidence current, AutismServerInfoOverlay.PluginEvidence candidate
   ) {
      if (candidate == null) {
         return current == null ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : current;
      } else if (current == null) {
         return candidate;
      } else {
         return this.copyEvidenceRank(candidate) < this.copyEvidenceRank(current) ? candidate : current;
      }
   }

   private boolean isCopyCommandEvidence(AutismServerInfoOverlay.PluginEvidence evidence) {
      return switch (evidence == null ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : evidence) {
         case COMMAND_TREE, NAMESPACE, ROOT_HINT, PLUGIN_LIST -> true;
         default -> false;
      };
   }

   private boolean isCompactCopyEvidence(AutismServerInfoOverlay.PluginEvidence evidence) {
      return switch (evidence == null ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : evidence) {
         case PAYLOAD_CHANNEL, COMMAND_GUI, COMMAND_TREE, NAMESPACE, ROOT_HINT, PLUGIN_LIST, VERSION_HINT -> true;
         default -> false;
      };
   }

   private AutismServerInfoOverlay.PluginConfidence mergeConfidence(
      AutismServerInfoOverlay.PluginConfidence current, AutismServerInfoOverlay.PluginConfidence candidate
   ) {
      if (current == null) {
         return candidate == null ? AutismServerInfoOverlay.PluginConfidence.UNKNOWN : candidate;
      } else if (candidate == null) {
         return current;
      } else {
         return this.confidenceRank(candidate) < this.confidenceRank(current) ? candidate : current;
      }
   }

   private AutismServerInfoOverlay.PluginConfidence confidenceForEvidence(AutismServerInfoOverlay.PluginEvidence evidence) {
      return switch (evidence == null ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : evidence) {
         case PAYLOAD_CHANNEL, NAMESPACE, VERSION_HINT -> AutismServerInfoOverlay.PluginConfidence.EXACT;
         case COMMAND_GUI, COMMAND_TREE, USER_AUTOCOMPLETE, SCANNER_AUTOCOMPLETE, HELP_HINT, PLUGIN_LIST, FEATURE -> AutismServerInfoOverlay.PluginConfidence.FEATURE;
         case ROOT_HINT -> AutismServerInfoOverlay.PluginConfidence.STRONG;
         case UNKNOWN -> AutismServerInfoOverlay.PluginConfidence.UNKNOWN;
      };
   }

   private boolean isWeakCommandEvidence(AutismServerInfoOverlay.PluginEvidence evidence) {
      return switch (evidence == null ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : evidence) {
         case USER_AUTOCOMPLETE, SCANNER_AUTOCOMPLETE, HELP_HINT, FEATURE, UNKNOWN -> true;
         default -> false;
      };
   }

   private boolean isFallbackCommandEntry(AutismServerInfoOverlay.PluginScanEntry entry) {
      return entry == null ? false : entry.resultKind == AutismServerInfoOverlay.PluginResultKind.FEATURE || this.isWeakCommandEvidence(entry.evidence);
   }

   private boolean isStrongCommandOwner(AutismServerInfoOverlay.PluginScanEntry entry) {
      return entry != null
         && entry.resultKind == AutismServerInfoOverlay.PluginResultKind.PLUGIN
         && !this.isWeakCommandEvidence(entry.evidence)
         && entry.evidence != AutismServerInfoOverlay.PluginEvidence.COMMAND_GUI;
   }

   private int commandOwnerScore(AutismServerInfoOverlay.PluginScanEntry entry) {
      if (entry == null) {
         return Integer.MAX_VALUE;
      } else {
         int score = this.evidenceRank(entry.evidence) * 100 + this.confidenceRank(entry.confidence) * 10;
         if (entry.resultKind == AutismServerInfoOverlay.PluginResultKind.FEATURE) {
            score += 1000;
         }

         if (this.isWeakCommandEvidence(entry.evidence)) {
            score += 500;
         }

         if (!entry.commandBacked) {
            score += 25;
         }

         String name = entry.displayName == null ? "" : entry.displayName.trim();
         if (name.startsWith("/")) {
            score += 75;
         }

         return score;
      }
   }

   private AutismServerInfoOverlay.PluginEvidence parseEvidenceName(String value) {
      if (value != null && !value.isBlank()) {
         try {
            return AutismServerInfoOverlay.PluginEvidence.valueOf(value);
         } catch (IllegalArgumentException var3) {
            return AutismServerInfoOverlay.PluginEvidence.UNKNOWN;
         }
      } else {
         return AutismServerInfoOverlay.PluginEvidence.UNKNOWN;
      }
   }

   private AutismServerInfoOverlay.PluginConfidence parseConfidenceName(String value, AutismServerInfoOverlay.PluginConfidence fallback) {
      if (value != null && !value.isBlank()) {
         try {
            return AutismServerInfoOverlay.PluginConfidence.valueOf(value);
         } catch (IllegalArgumentException var4) {
            return fallback == null ? AutismServerInfoOverlay.PluginConfidence.UNKNOWN : fallback;
         }
      } else {
         return fallback == null ? AutismServerInfoOverlay.PluginConfidence.UNKNOWN : fallback;
      }
   }

   private int getConfidenceColor(AutismServerInfoOverlay.PluginConfidence confidence, boolean hovered) {
      if (hovered) {
         return AutismColors.packetWhite();
      } else {
         return switch (confidence == null ? AutismServerInfoOverlay.PluginConfidence.UNKNOWN : confidence) {
            case EXACT -> AutismColors.packetGreen();
            case STRONG -> AutismColors.packetCyan();
            case FEATURE -> AutismColors.packetYellow();
            case UNKNOWN -> -4743522;
         };
      }
   }

   private String confidenceLabel(AutismServerInfoOverlay.PluginConfidence confidence) {
      return switch (confidence == null ? AutismServerInfoOverlay.PluginConfidence.UNKNOWN : confidence) {
         case EXACT -> "Exact";
         case STRONG -> "Strong";
         case FEATURE -> "Feature";
         case UNKNOWN -> "Unknown";
      };
   }

   private Map<String, AutismServerInfoOverlay.PluginScanEntry> buildCurrentPluginEntries() {
      Map<String, AutismServerInfoOverlay.PluginScanEntry> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

      for (String plugin : this.detectedPlugins) {
         if (plugin != null && !plugin.isBlank()) {
            List<String> existing = this.getPluginCommands(plugin);
            AutismServerInfoOverlay.PluginEvidence evidence = this.pluginEvidence
               .getOrDefault(
                  this.normalizePluginKey(plugin),
                  existing != null && !existing.isEmpty() ? AutismServerInfoOverlay.PluginEvidence.ROOT_HINT : AutismServerInfoOverlay.PluginEvidence.UNKNOWN
               );
            List<String> existingChannels = this.getPluginChannels(plugin);
            List<String> existingGuis = this.getPluginGuis(plugin);
            AutismServerInfoOverlay.PluginConfidence confidence = this.pluginConfidence
               .getOrDefault(this.normalizePluginKey(plugin), this.confidenceForEvidence(evidence));
            AutismServerInfoOverlay.PluginResultKind resultKind = this.resultKindForStoredPlugin(plugin, evidence, confidence);
            this.mergePluginEntry(
               entries, null, plugin, existing, existingChannels, existingGuis, existing != null && !existing.isEmpty(), evidence, confidence, resultKind
            );
            String key = this.normalizePluginKey(plugin);
            AutismServerInfoOverlay.PluginScanEntry entry = entries.get(key);
            if (entry != null) {
               AutismServerInfoOverlay.PluginEvidence copyEvidence = this.pluginCopyEvidence.getOrDefault(key, this.fallbackCopyEvidence(plugin, evidence));
               if (this.copyEvidenceRank(copyEvidence) < this.copyEvidenceRank(entry.copyEvidence)) {
                  entry.copyCommands.clear();
               }

               entry.copyEvidence = this.mergeCopyEvidence(entry.copyEvidence, copyEvidence);
               List<String> copyCommands = this.listForPluginKey(this.pluginCopyCommands, plugin);
               if (copyCommands != null) {
                  for (String command : copyCommands) {
                     if (command != null && !command.isBlank()) {
                        entry.copyCommands.add(command.trim());
                     }
                  }
               }
            }
         }
      }

      return entries;
   }

   private boolean mergePluginEntry(
      Map<String, AutismServerInfoOverlay.PluginScanEntry> entries,
      String pluginName,
      Collection<String> commands,
      boolean commandBacked,
      AutismServerInfoOverlay.PluginEvidence evidence
   ) {
      return this.mergePluginEntry(entries, null, pluginName, commands, List.of(), List.of(), commandBacked, evidence, this.confidenceForEvidence(evidence));
   }

   private boolean mergePluginEntry(
      Map<String, AutismServerInfoOverlay.PluginScanEntry> entries,
      String pluginName,
      Collection<String> commands,
      Collection<String> channels,
      boolean commandBacked,
      AutismServerInfoOverlay.PluginEvidence evidence
   ) {
      return this.mergePluginEntry(entries, null, pluginName, commands, channels, List.of(), commandBacked, evidence, this.confidenceForEvidence(evidence));
   }

   private boolean mergePluginEntry(
      Map<String, AutismServerInfoOverlay.PluginScanEntry> entries,
      String explicitKey,
      String pluginName,
      Collection<String> commands,
      Collection<String> channels,
      boolean commandBacked,
      AutismServerInfoOverlay.PluginEvidence evidence
   ) {
      return this.mergePluginEntry(
         entries, explicitKey, pluginName, commands, channels, List.of(), commandBacked, evidence, this.confidenceForEvidence(evidence)
      );
   }

   private boolean mergePluginEntry(
      Map<String, AutismServerInfoOverlay.PluginScanEntry> entries,
      String explicitKey,
      String pluginName,
      Collection<String> commands,
      Collection<String> channels,
      Collection<String> guis,
      boolean commandBacked,
      AutismServerInfoOverlay.PluginEvidence evidence,
      AutismServerInfoOverlay.PluginConfidence confidence
   ) {
      return this.mergePluginEntry(
         entries, explicitKey, pluginName, commands, channels, guis, commandBacked, evidence, confidence, AutismServerInfoOverlay.PluginResultKind.PLUGIN
      );
   }

   private boolean mergePluginEntry(
      Map<String, AutismServerInfoOverlay.PluginScanEntry> entries,
      String explicitKey,
      String pluginName,
      Collection<String> commands,
      Collection<String> channels,
      Collection<String> guis,
      boolean commandBacked,
      AutismServerInfoOverlay.PluginEvidence evidence,
      AutismServerInfoOverlay.PluginConfidence confidence,
      AutismServerInfoOverlay.PluginResultKind resultKind
   ) {
      if (pluginName != null && !pluginName.isBlank()) {
         String cleanName = pluginName.trim();
         String key = explicitKey != null && !explicitKey.isBlank() ? this.normalizePluginKey(explicitKey) : this.normalizePluginKey(cleanName);
         if (!key.isEmpty() && (!VANILLA_NAMESPACES.contains(key) || evidence == AutismServerInfoOverlay.PluginEvidence.PAYLOAD_CHANNEL)) {
            AutismServerInfoOverlay.PluginScanEntry entry = entries.computeIfAbsent(key, unused -> new AutismServerInfoOverlay.PluginScanEntry());
            String beforeName = entry.displayName;
            boolean beforeBacked = entry.commandBacked;
            AutismServerInfoOverlay.PluginEvidence beforeEvidence = entry.evidence;
            AutismServerInfoOverlay.PluginEvidence beforeCopyEvidence = entry.copyEvidence;
            AutismServerInfoOverlay.PluginConfidence beforeConfidence = entry.confidence;
            AutismServerInfoOverlay.PluginResultKind beforeKind = entry.resultKind;
            int beforeCommandCount = entry.commands.size();
            int beforeCopyCommandCount = entry.copyCommands.size();
            int beforeChannelCount = entry.channels.size();
            int beforeGuiCount = entry.guis.size();
            boolean candidateHasBetterCopySource = this.copyEvidenceRank(evidence) < this.copyEvidenceRank(entry.copyEvidence);
            boolean allowReadableRename = !this.isCompactCopyEvidence(entry.copyEvidence) || candidateHasBetterCopySource;
            if (candidateHasBetterCopySource) {
               entry.copyCommands.clear();
            }

            if (entry.displayName == null
               || entry.displayName.isBlank()
               || commandBacked && !entry.commandBacked
               || allowReadableRename && this.isMoreReadablePluginName(cleanName, entry.displayName)) {
               entry.displayName = cleanName;
            }

            entry.resultKind = this.mergeResultKind(entry.resultKind, resultKind);
            if (commandBacked) {
               entry.commandBacked = true;
            }

            entry.evidence = this.mergeEvidence(entry.evidence, evidence);
            entry.copyEvidence = this.mergeCopyEvidence(entry.copyEvidence, evidence);
            entry.confidence = this.mergeConfidence(entry.confidence, confidence);
            this.pluginEvidence.put(key, this.mergeEvidence(this.pluginEvidence.get(key), evidence));
            this.pluginCopyEvidence.put(key, this.mergeCopyEvidence(this.pluginCopyEvidence.get(key), evidence));
            this.pluginConfidence.put(key, this.mergeConfidence(this.pluginConfidence.get(key), confidence));
            this.pluginResultKinds.put(key, this.mergeResultKind(this.pluginResultKinds.get(key), resultKind));
            if (commands != null) {
               for (String command : commands) {
                  if (command != null) {
                     String cleanCommand = command.trim();
                     if (!cleanCommand.isEmpty()) {
                        entry.commands.add(cleanCommand);
                     }

                     if (!cleanCommand.isEmpty()
                        && this.isCopyCommandEvidence(evidence)
                        && this.copyEvidenceRank(evidence) <= this.copyEvidenceRank(entry.copyEvidence)) {
                        entry.copyCommands.add(cleanCommand);
                     }
                  }
               }
            }

            if (channels != null) {
               for (String channel : channels) {
                  if (channel != null) {
                     String cleanChannel = channel.trim();
                     if (!cleanChannel.isEmpty()) {
                        entry.channels.add(cleanChannel);
                     }
                  }
               }
            }

            if (guis != null) {
               for (String gui : guis) {
                  if (gui != null) {
                     String cleanGui = gui.trim();
                     if (!cleanGui.isEmpty()) {
                        entry.guis.add(cleanGui);
                     }
                  }
               }
            }

            return !Objects.equals(beforeName, entry.displayName)
               || beforeBacked != entry.commandBacked
               || beforeEvidence != entry.evidence
               || beforeCopyEvidence != entry.copyEvidence
               || beforeConfidence != entry.confidence
               || beforeKind != entry.resultKind
               || beforeCommandCount != entry.commands.size()
               || beforeCopyCommandCount != entry.copyCommands.size()
               || beforeChannelCount != entry.channels.size()
               || beforeGuiCount != entry.guis.size();
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean mergeScanEntry(
      Map<String, AutismServerInfoOverlay.PluginScanEntry> target, String explicitKey, AutismServerInfoOverlay.PluginScanEntry source
   ) {
      if (target != null && source != null && source.displayName != null && !source.displayName.isBlank()) {
         boolean changed = this.mergePluginEntry(
            target,
            explicitKey,
            source.displayName,
            source.commands,
            source.channels,
            source.guis,
            source.commandBacked,
            source.evidence,
            source.confidence,
            source.resultKind
         );
         String key = explicitKey != null && !explicitKey.isBlank() ? this.normalizePluginKey(explicitKey) : this.normalizePluginKey(source.displayName);
         AutismServerInfoOverlay.PluginScanEntry entry = target.get(key);
         if (entry != null) {
            AutismServerInfoOverlay.PluginEvidence beforeCopyEvidence = entry.copyEvidence;
            int beforeCopyCommandCount = entry.copyCommands.size();
            boolean betterCopySource = this.copyEvidenceRank(source.copyEvidence) < this.copyEvidenceRank(entry.copyEvidence);
            if (betterCopySource) {
               entry.copyCommands.clear();
            }

            entry.copyEvidence = this.mergeCopyEvidence(entry.copyEvidence, source.copyEvidence);
            Collection<String> sourceCopyCommands = source.copyCommands.isEmpty() && this.isCopyCommandEvidence(source.copyEvidence)
               ? source.commands
               : source.copyCommands;
            if (sourceCopyCommands != null && this.copyEvidenceRank(source.copyEvidence) <= this.copyEvidenceRank(entry.copyEvidence)) {
               for (String command : sourceCopyCommands) {
                  if (command != null && !command.isBlank()) {
                     entry.copyCommands.add(command.trim());
                  }
               }
            }

            changed |= beforeCopyEvidence != entry.copyEvidence || beforeCopyCommandCount != entry.copyCommands.size();
         }

         return changed;
      } else {
         return false;
      }
   }

   private boolean mergeScanEntries(Map<String, AutismServerInfoOverlay.PluginScanEntry> target, Map<String, AutismServerInfoOverlay.PluginScanEntry> source) {
      if (target != null && source != null && !source.isEmpty()) {
         boolean changed = false;

         for (Entry<String, AutismServerInfoOverlay.PluginScanEntry> entry : source.entrySet()) {
            changed |= this.mergeScanEntry(target, entry.getKey(), entry.getValue());
         }

         return changed;
      } else {
         return false;
      }
   }

   private boolean normalizeCommandSet(Set<String> commands) {
      if (commands != null && !commands.isEmpty()) {
         TreeSet<String> normalized = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

         for (String command : commands) {
            String token = this.normalizeCommandToken(command);
            if (!token.isBlank()) {
               normalized.add(token);
            }
         }

         if (normalized.size() == commands.size() && normalized.containsAll(commands)) {
            return false;
         } else {
            commands.clear();
            commands.addAll(normalized);
            return true;
         }
      } else {
         return false;
      }
   }

   private String bestCommandOwnerKey(Map<String, AutismServerInfoOverlay.PluginScanEntry> entries, String command) {
      if (entries != null && !entries.isEmpty()) {
         String token = this.normalizeCommandToken(command);
         if (token.isBlank()) {
            return null;
         } else {
            String bestKey = null;
            int bestScore = Integer.MAX_VALUE;

            for (Entry<String, AutismServerInfoOverlay.PluginScanEntry> mapEntry : entries.entrySet()) {
               AutismServerInfoOverlay.PluginScanEntry entry = mapEntry.getValue();
               if (entry != null && entry.commands != null && entry.commands.contains(token)) {
                  int score = this.commandOwnerScore(entry);
                  if (score < bestScore) {
                     bestScore = score;
                     bestKey = mapEntry.getKey();
                  }
               }
            }

            String aliasedPlugin = ROOT_COMMAND_PLUGIN_ALIASES.get(token);
            if (aliasedPlugin != null && !aliasedPlugin.isBlank()) {
               String aliasedKey = this.normalizePluginKey(aliasedPlugin);
               AutismServerInfoOverlay.PluginScanEntry aliasedEntry = entries.get(aliasedKey);
               if (this.isStrongCommandOwner(aliasedEntry)) {
                  int score = this.commandOwnerScore(aliasedEntry);
                  if (bestKey == null || score <= bestScore || !this.isStrongCommandOwner(entries.get(bestKey))) {
                     bestKey = aliasedKey;
                  }
               }
            }

            return bestKey;
         }
      } else {
         return null;
      }
   }

   private AutismServerInfoOverlay.PluginScanEntry commandHintEntry(String command) {
      String token = this.normalizeCommandToken(command);
      AutismServerInfoOverlay.PluginScanEntry entry = new AutismServerInfoOverlay.PluginScanEntry();
      entry.displayName = "/" + token;
      entry.commandBacked = true;
      entry.resultKind = AutismServerInfoOverlay.PluginResultKind.PLUGIN;
      entry.evidence = AutismServerInfoOverlay.PluginEvidence.SCANNER_AUTOCOMPLETE;
      entry.copyEvidence = AutismServerInfoOverlay.PluginEvidence.UNKNOWN;
      entry.confidence = AutismServerInfoOverlay.PluginConfidence.FEATURE;
      entry.commands.add(token);
      return entry;
   }

   private boolean cleanupPluginCommandOwnership(Map<String, AutismServerInfoOverlay.PluginScanEntry> entries) {
      if (entries != null && !entries.isEmpty()) {
         boolean changed = false;

         for (AutismServerInfoOverlay.PluginScanEntry entry : entries.values()) {
            if (entry != null) {
               changed |= this.normalizeCommandSet(entry.commands);
               changed |= this.normalizeCommandSet(entry.copyCommands);
            }
         }

         TreeSet<String> allCommands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

         for (AutismServerInfoOverlay.PluginScanEntry entryx : entries.values()) {
            if (entryx != null && entryx.commands != null) {
               allCommands.addAll(entryx.commands);
            }
         }

         Map<String, String> ownerByCommand = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

         for (String command : allCommands) {
            String ownerKey = this.bestCommandOwnerKey(entries, command);
            if (ownerKey != null && !ownerKey.isBlank()) {
               ownerByCommand.put(command, ownerKey);
            }
         }

         for (Entry<String, AutismServerInfoOverlay.PluginScanEntry> mapEntry : new ArrayList<>(entries.entrySet())) {
            String key = mapEntry.getKey();
            AutismServerInfoOverlay.PluginScanEntry entryxx = mapEntry.getValue();
            if (key != null && entryxx != null && !entryxx.commands.isEmpty()) {
               for (String commandx : new ArrayList<>(entryxx.commands)) {
                  String ownerKey = ownerByCommand.get(commandx);
                  if (ownerKey != null && !ownerKey.equals(key)) {
                     AutismServerInfoOverlay.PluginScanEntry owner = entries.get(ownerKey);
                     if (owner != null) {
                        if (owner.commands.add(commandx)) {
                           changed = true;
                        }

                        if (this.isCopyCommandEvidence(owner.copyEvidence) && owner.copyCommands.add(commandx)) {
                           changed = true;
                        }

                        if (!entryxx.guis.isEmpty()) {
                           int beforeGuiCount = owner.guis.size();
                           owner.guis.addAll(entryxx.guis);
                           changed |= beforeGuiCount != owner.guis.size();
                        }

                        entryxx.commands.remove(commandx);
                        entryxx.copyCommands.remove(commandx);
                        changed = true;
                     }
                  }
               }
            }
         }

         List<String> removeKeys = new ArrayList<>();
         Map<String, AutismServerInfoOverlay.PluginScanEntry> addEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

         for (Entry<String, AutismServerInfoOverlay.PluginScanEntry> mapEntryx : new ArrayList<>(entries.entrySet())) {
            String key = mapEntryx.getKey();
            AutismServerInfoOverlay.PluginScanEntry entryxx = mapEntryx.getValue();
            if (key == null || entryxx == null) {
               removeKeys.add(key);
            } else if (this.isFallbackCommandEntry(entryxx)) {
               if (entryxx.commands.isEmpty()) {
                  removeKeys.add(key);
               } else if (entryxx.commands.size() == 1) {
                  String commandxx = entryxx.commands.iterator().next();
                  String expectedName = "/" + commandxx;
                  if (!expectedName.equals(entryxx.displayName)) {
                     entryxx.displayName = expectedName;
                     changed = true;
                  }

                  if (entryxx.resultKind != AutismServerInfoOverlay.PluginResultKind.PLUGIN) {
                     entryxx.resultKind = AutismServerInfoOverlay.PluginResultKind.PLUGIN;
                     changed = true;
                  }

                  if (entryxx.evidence != AutismServerInfoOverlay.PluginEvidence.SCANNER_AUTOCOMPLETE) {
                     entryxx.evidence = AutismServerInfoOverlay.PluginEvidence.SCANNER_AUTOCOMPLETE;
                     changed = true;
                  }

                  if (entryxx.copyEvidence != AutismServerInfoOverlay.PluginEvidence.UNKNOWN) {
                     entryxx.copyEvidence = AutismServerInfoOverlay.PluginEvidence.UNKNOWN;
                     entryxx.copyCommands.clear();
                     changed = true;
                  }

                  entryxx.confidence = AutismServerInfoOverlay.PluginConfidence.FEATURE;
               } else {
                  for (String commandxxx : entryxx.commands) {
                     String hintKey = this.normalizePluginKey("command_" + commandxxx);
                     if (!entries.containsKey(hintKey) && !addEntries.containsKey(hintKey)) {
                        addEntries.put(hintKey, this.commandHintEntry(commandxxx));
                     }
                  }

                  removeKeys.add(key);
                  changed = true;
               }
            }
         }

         for (String key : removeKeys) {
            if (key != null && entries.remove(key) != null) {
               changed = true;
            }
         }

         if (!addEntries.isEmpty()) {
            entries.putAll(addEntries);
            changed = true;
         }

         return changed;
      } else {
         return false;
      }
   }

   private int countPluginEntries(Map<String, AutismServerInfoOverlay.PluginScanEntry> entries) {
      if (entries != null && !entries.isEmpty()) {
         int count = 0;

         for (AutismServerInfoOverlay.PluginScanEntry entry : entries.values()) {
            if (entry != null && entry.displayName != null && !entry.displayName.isBlank()) {
               count++;
            }
         }

         return count;
      } else {
         return 0;
      }
   }

   private void tracePluginScan(String message) {
      if (PLUGIN_SCAN_TRACE) {
         System.out.println("[AUTISM][PluginScan] " + message);
      }
   }

   private AutismServerInfoOverlay.PluginResultKind mergeResultKind(
      AutismServerInfoOverlay.PluginResultKind current, AutismServerInfoOverlay.PluginResultKind candidate
   ) {
      if (candidate == null) {
         return current == null ? AutismServerInfoOverlay.PluginResultKind.PLUGIN : current;
      } else if (current == null) {
         return candidate;
      } else {
         return current != AutismServerInfoOverlay.PluginResultKind.PLUGIN && candidate != AutismServerInfoOverlay.PluginResultKind.PLUGIN
            ? AutismServerInfoOverlay.PluginResultKind.FEATURE
            : AutismServerInfoOverlay.PluginResultKind.PLUGIN;
      }
   }

   private AutismServerInfoOverlay.PluginResultKind resultKindForStoredPlugin(
      String plugin, AutismServerInfoOverlay.PluginEvidence evidence, AutismServerInfoOverlay.PluginConfidence confidence
   ) {
      String key = this.normalizePluginKey(plugin);
      AutismServerInfoOverlay.PluginResultKind stored = this.pluginResultKinds.get(key);
      if (stored != null) {
         return stored;
      } else if (confidence != AutismServerInfoOverlay.PluginConfidence.EXACT && confidence != AutismServerInfoOverlay.PluginConfidence.STRONG) {
         if (evidence != AutismServerInfoOverlay.PluginEvidence.PAYLOAD_CHANNEL
            && evidence != AutismServerInfoOverlay.PluginEvidence.VERSION_HINT
            && evidence != AutismServerInfoOverlay.PluginEvidence.NAMESPACE) {
            String lower = plugin == null ? "" : plugin.trim().toLowerCase(Locale.ROOT);

            for (String featureLabel : COMMAND_FEATURE_LABELS.values()) {
               if (lower.equals(featureLabel.toLowerCase(Locale.ROOT))) {
                  return AutismServerInfoOverlay.PluginResultKind.FEATURE;
               }
            }

            return AutismServerInfoOverlay.PluginResultKind.PLUGIN;
         } else {
            return AutismServerInfoOverlay.PluginResultKind.PLUGIN;
         }
      } else {
         return AutismServerInfoOverlay.PluginResultKind.PLUGIN;
      }
   }

   private AutismServerInfoOverlay.PluginEvidence fallbackCopyEvidence(String plugin, AutismServerInfoOverlay.PluginEvidence evidence) {
      if (evidence == AutismServerInfoOverlay.PluginEvidence.UNKNOWN || evidence == null) {
         List<String> commands = this.listForPluginKey(this.pluginCommands, plugin);
         if (commands != null && !commands.isEmpty()) {
            return AutismServerInfoOverlay.PluginEvidence.ROOT_HINT;
         }
      }

      return this.isCompactCopyEvidence(evidence) ? evidence : AutismServerInfoOverlay.PluginEvidence.UNKNOWN;
   }

   private List<String> listForPluginKey(Map<String, List<String>> map, String plugin) {
      if (map != null && !map.isEmpty() && plugin != null && !plugin.isBlank()) {
         List<String> direct = map.get(plugin);
         if (direct != null) {
            return direct;
         } else {
            String key = this.normalizePluginKey(plugin);

            for (Entry<String, List<String>> entry : map.entrySet()) {
               if (entry.getKey() != null && this.normalizePluginKey(entry.getKey()).equals(key)) {
                  return entry.getValue();
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private boolean isMoreReadablePluginName(String candidate, String current) {
      if (candidate == null || candidate.isBlank()) {
         return false;
      } else if (current != null && !current.isBlank()) {
         String c = candidate.trim();
         String old = current.trim();
         if (c.equalsIgnoreCase(old)) {
            return false;
         } else {
            String cKey = this.normalizePluginKey(c);
            String oldKey = this.normalizePluginKey(old);
            if (!cKey.equals(oldKey)) {
               return false;
            } else {
               boolean candidateHasCase = !c.equals(c.toLowerCase(Locale.ROOT));
               boolean currentHasCase = !old.equals(old.toLowerCase(Locale.ROOT));
               boolean candidateHasSpace = c.indexOf(32) >= 0;
               boolean currentHasSpace = old.indexOf(32) >= 0;
               return candidateHasCase && !currentHasCase || candidateHasSpace && !currentHasSpace;
            }
         }
      } else {
         return true;
      }
   }

   private void applyPluginEntries(Map<String, AutismServerInfoOverlay.PluginScanEntry> entries, boolean resetSelection) {
      Map<String, AutismServerInfoOverlay.PluginScanEntry> normalizedEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      if (entries != null) {
         normalizedEntries.putAll(entries);
      }

      this.cleanupPluginCommandOwnership(normalizedEntries);
      this.detectedPlugins.clear();
      this.pluginCommands.clear();
      this.pluginChannels.clear();
      this.pluginGuis.clear();
      this.pluginEvidence.clear();
      this.pluginCopyEvidence.clear();
      this.pluginCopyCommands.clear();
      this.pluginConfidence.clear();
      this.pluginResultKinds.clear();
      List<AutismServerInfoOverlay.PluginScanEntry> sortedEntries = new ArrayList<>(normalizedEntries.values());
      sortedEntries.removeIf(entryx -> entryx == null || entryx.displayName == null || entryx.displayName.isBlank());
      sortedEntries.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.displayName, b.displayName));

      for (AutismServerInfoOverlay.PluginScanEntry entry : sortedEntries) {
         String plugin = entry.displayName;
         List<String> commands = new ArrayList<>(entry.commands);
         commands.removeIf(cmd -> cmd == null || cmd.isBlank());
         commands.sort(String.CASE_INSENSITIVE_ORDER);
         this.detectedPlugins.add(plugin);
         this.pluginCommands.put(plugin, commands);
         this.pluginChannels.put(plugin, this.dedupeChannelRows(entry.channels));
         List<String> guis = new ArrayList<>(entry.guis);
         guis.removeIf(gui -> gui == null || gui.isBlank());
         guis.sort(String.CASE_INSENSITIVE_ORDER);
         this.pluginGuis.put(plugin, List.copyOf(guis));
         this.pluginEvidence.put(this.normalizePluginKey(plugin), entry.evidence == null ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : entry.evidence);
         this.pluginCopyEvidence
            .put(this.normalizePluginKey(plugin), entry.copyEvidence == null ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : entry.copyEvidence);
         List<String> copyCommands = new ArrayList<>(entry.copyCommands);
         copyCommands.removeIf(cmd -> cmd == null || cmd.isBlank());
         copyCommands.sort(String.CASE_INSENSITIVE_ORDER);
         this.pluginCopyCommands.put(plugin, List.copyOf(copyCommands));
         this.pluginConfidence.put(this.normalizePluginKey(plugin), entry.confidence == null ? this.confidenceForEvidence(entry.evidence) : entry.confidence);
         this.pluginResultKinds
            .put(this.normalizePluginKey(plugin), entry.resultKind == null ? AutismServerInfoOverlay.PluginResultKind.PLUGIN : entry.resultKind);
      }

      if (resetSelection) {
         this.pluginScrollState.jumpTo(0, 0);
         this.selectedPlugin = null;
      } else if (this.selectedPlugin != null && !this.hasDetectedPlugin(this.selectedPlugin)) {
         this.selectedPlugin = null;
      }

      this.invalidatePluginRows();
   }

   private boolean isKnownPluginNamespace(String key) {
      for (String namespace : COMMON_PLUGIN_NAMESPACES) {
         if (namespace.equalsIgnoreCase(key)) {
            return true;
         }
      }

      return false;
   }

   private String normalizeCommandToken(String raw) {
      if (raw == null) {
         return "";
      } else {
         String token = raw.trim();
         if (token.isEmpty()) {
            return "";
         } else {
            while (token.startsWith("/")) {
               token = token.substring(1).trim();
            }

            int spaceIndex = token.indexOf(32);
            if (spaceIndex >= 0) {
               token = token.substring(0, spaceIndex).trim();
            }

            while (token.endsWith(":")) {
               token = token.substring(0, token.length() - 1).trim();
            }

            return token.toLowerCase(Locale.ROOT);
         }
      }
   }

   private boolean isTrackableCommandToken(String token) {
      if (token != null && !token.isBlank()) {
         String clean = token.trim().toLowerCase(Locale.ROOT);
         if (clean.length() < 2) {
            return false;
         } else if (!VANILLA_NAMESPACES.contains(clean) && !VANILLA_COMMAND_ROOTS.contains(clean)) {
            return this.getOnlinePlayerNames().contains(clean)
               ? false
               : clean.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == 95 || ch == 45 || ch == 46 || ch == 58);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean mergeCommandEvidence(
      Map<String, AutismServerInfoOverlay.PluginScanEntry> entries, String rawCommand, AutismServerInfoOverlay.PluginEvidence source
   ) {
      String token = this.normalizeCommandToken(rawCommand);
      if (!this.isTrackableCommandToken(token)) {
         return false;
      } else {
         this.observedPluginCommands.add(token);
         if (token.contains(":")) {
            String[] parts = token.split(":", 2);
            String namespace = parts[0].trim().toLowerCase(Locale.ROOT);
            String command = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "";
            return this.isTrackableCommandToken(namespace) && !VANILLA_NAMESPACES.contains(namespace)
               ? this.mergePluginEntry(
                  entries,
                  namespace,
                  namespace,
                  command.isBlank() ? List.of() : List.of(command),
                  List.of(),
                  List.of(),
                  !command.isBlank(),
                  AutismServerInfoOverlay.PluginEvidence.NAMESPACE,
                  AutismServerInfoOverlay.PluginConfidence.EXACT
               )
               : false;
         } else {
            String plugin = null;
            AutismServerInfoOverlay.PluginConfidence confidence = AutismServerInfoOverlay.PluginConfidence.FEATURE;
            AutismServerInfoOverlay.PluginEvidence evidence = source == null ? AutismServerInfoOverlay.PluginEvidence.FEATURE : source;
            if (this.isKnownPluginNamespace(token)) {
               plugin = token;
               confidence = AutismServerInfoOverlay.PluginConfidence.EXACT;
               evidence = AutismServerInfoOverlay.PluginEvidence.NAMESPACE;
            } else if (ROOT_COMMAND_PLUGIN_ALIASES.containsKey(token)) {
               plugin = ROOT_COMMAND_PLUGIN_ALIASES.get(token);
               confidence = AutismServerInfoOverlay.PluginConfidence.STRONG;
               evidence = AutismServerInfoOverlay.PluginEvidence.ROOT_HINT;
            }

            if (plugin != null && !plugin.isBlank()) {
               return this.mergePluginEntry(
                  entries, null, plugin, List.of(token), List.of(), List.of(), true, evidence, confidence, AutismServerInfoOverlay.PluginResultKind.PLUGIN
               );
            } else {
               String ownerKey = this.bestCommandOwnerKey(entries, token);
               if (ownerKey != null) {
                  AutismServerInfoOverlay.PluginScanEntry owner = entries.get(ownerKey);
                  if (owner == null) {
                     return false;
                  } else {
                     boolean changed = owner.commands.add(token);
                     if (this.isCopyCommandEvidence(owner.copyEvidence)) {
                        changed |= owner.copyCommands.add(token);
                     }

                     return changed;
                  }
               } else {
                  return source != AutismServerInfoOverlay.PluginEvidence.USER_AUTOCOMPLETE
                        && source != AutismServerInfoOverlay.PluginEvidence.COMMAND_GUI
                        && source != AutismServerInfoOverlay.PluginEvidence.SCANNER_AUTOCOMPLETE
                        && !COMMAND_FEATURE_LABELS.containsKey(token)
                     ? false
                     : this.mergePluginEntry(
                        entries,
                        "command_" + token,
                        "/" + token,
                        List.of(token),
                        List.of(),
                        List.of(),
                        true,
                        AutismServerInfoOverlay.PluginEvidence.SCANNER_AUTOCOMPLETE,
                        AutismServerInfoOverlay.PluginConfidence.FEATURE,
                        AutismServerInfoOverlay.PluginResultKind.PLUGIN
                     );
               }
            }
         }
      }
   }

   private Set<String> getOnlinePlayerNames() {
      if (MC.getConnection() == null) {
         return Set.of();
      } else {
         Set<String> names = new HashSet<>();

         for (PlayerInfo player : MC.getConnection().getListedOnlinePlayers()) {
            if (player != null && player.getProfile() != null) {
               //? if >=1.21.9 {
               String name = player.getProfile().name();
               //?} else {
               /*String name = player.getProfile().getName();*///?}
               if (name != null && !name.isBlank()) {
                  names.add(name.trim().toLowerCase(Locale.ROOT));
               }
            }
         }

         return names;
      }
   }

   private boolean isLikelyPluginNameCandidate(String candidate, AutismServerInfoOverlay.PluginProbeKind kind) {
      if (candidate == null) {
         return false;
      } else {
         String clean = candidate.trim();
         String key = this.normalizePluginKey(clean);
         if (key.isEmpty()) {
            return false;
         } else if (key.length() < 2) {
            return false;
         } else if (clean.contains(" ") || clean.contains("/") || clean.contains(":")) {
            return false;
         } else if (VANILLA_NAMESPACES.contains(key)) {
            return false;
         } else if (this.getOnlinePlayerNames().contains(key)) {
            return false;
         } else {
            return kind != AutismServerInfoOverlay.PluginProbeKind.PLUGIN_LIST && kind != AutismServerInfoOverlay.PluginProbeKind.VERSION
                  || !key.equals("plugins") && !key.equals("plugin") && !key.equals("version") && !key.equals("about")
               ? key.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == 95 || ch == 45 || ch == 46)
               : false;
         }
      }
   }

   private void addObservedPluginCommand(String raw) {
      String token = this.normalizeCommandToken(raw);
      if (this.isTrackableCommandToken(token)) {
         this.observedPluginCommands.add(token);
      }
   }

   private void inferPluginsFromObservedCommands(Map<String, AutismServerInfoOverlay.PluginScanEntry> entries) {
      this.inferLegacyPluginsFromObservedCommands(entries);
   }

   private void inferLegacyPluginsFromObservedCommands(Map<String, AutismServerInfoOverlay.PluginScanEntry> entries) {
      if (!this.observedPluginCommands.isEmpty()) {
         Map<String, Set<String>> inferredMatches = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

         for (String command : this.observedPluginCommands) {
            String plugin = null;
            if (this.isKnownPluginNamespace(command)) {
               plugin = command;
            } else if (ROOT_COMMAND_PLUGIN_ALIASES.containsKey(command)) {
               plugin = ROOT_COMMAND_PLUGIN_ALIASES.get(command);
            }

            if (plugin != null && !plugin.isBlank()) {
               inferredMatches.computeIfAbsent(plugin, unused -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)).add(command);
            }
         }

         for (Entry<String, Set<String>> entry : inferredMatches.entrySet()) {
            this.mergePluginEntry(entries, entry.getKey(), entry.getValue(), true, AutismServerInfoOverlay.PluginEvidence.ROOT_HINT);
         }
      }
   }

   private boolean mergeScannerAutocompleteHint(Map<String, AutismServerInfoOverlay.PluginScanEntry> entries, String rawCommand) {
      String token = this.normalizeCommandToken(rawCommand);
      if (!this.isScannerAutocompleteFallbackCandidate(token)) {
         return false;
      } else if (token.contains(":")) {
         return false;
      } else {
         String ownerKey = this.bestCommandOwnerKey(entries, token);
         if (ownerKey != null) {
            AutismServerInfoOverlay.PluginScanEntry owner = entries.get(ownerKey);
            if (owner != null && !owner.commands.contains(token)) {
               owner.commands.add(token);
               if (this.isCopyCommandEvidence(owner.copyEvidence)) {
                  owner.copyCommands.add(token);
               }

               return true;
            } else {
               return false;
            }
         } else {
            return this.mergePluginEntry(
               entries,
               "command_" + token,
               "/" + token,
               List.of(token),
               List.of(),
               List.of(),
               true,
               AutismServerInfoOverlay.PluginEvidence.SCANNER_AUTOCOMPLETE,
               AutismServerInfoOverlay.PluginConfidence.FEATURE,
               AutismServerInfoOverlay.PluginResultKind.PLUGIN
            );
         }
      }
   }

   private boolean isScannerAutocompleteFallbackCandidate(String token) {
      if (!this.isTrackableCommandToken(token)) {
         return false;
      } else {
         String clean = token.trim().toLowerCase(Locale.ROOT);
         if (clean.length() < 3) {
            return false;
         } else if (SCANNER_AUTOCOMPLETE_BLOCKLIST.contains(clean)) {
            return false;
         } else if (VANILLA_NAMESPACES.contains(clean) || VANILLA_COMMAND_ROOTS.contains(clean)) {
            return false;
         } else {
            return COMMAND_FEATURE_LABELS.containsKey(clean) ? false : !ROOT_COMMAND_PLUGIN_ALIASES.containsKey(clean) && !this.isKnownPluginNamespace(clean);
         }
      }
   }

   private boolean mergePayloadFingerprintEntries(Map<String, AutismServerInfoOverlay.PluginScanEntry> entries) {
      String address = this.currentServerAddress();
      if (address != null && !address.isBlank()) {
         boolean changed = false;

         for (AutismPluginPayloadFingerprints.PluginFingerprint fingerprint : AutismPluginPayloadFingerprints.pluginsFor(address, this.getLiveBrand())) {
            if (fingerprint != null && fingerprint.plugin() != null && !fingerprint.plugin().isBlank()) {
               changed |= this.promoteMatchingFeatureEntries(entries, fingerprint);
               AutismServerInfoOverlay.PluginConfidence confidence = this.payloadFingerprintConfidence(fingerprint);
               changed |= this.mergePluginEntry(
                  entries,
                  fingerprint.key(),
                  fingerprint.plugin(),
                  List.of(),
                  fingerprint.channels(),
                  List.of(),
                  false,
                  AutismServerInfoOverlay.PluginEvidence.PAYLOAD_CHANNEL,
                  confidence
               );
            }
         }

         return changed;
      } else {
         return false;
      }
   }

   private AutismServerInfoOverlay.PluginConfidence payloadFingerprintConfidence(AutismPluginPayloadFingerprints.PluginFingerprint fingerprint) {
      String basis = fingerprint == null ? "" : fingerprint.basis();
      return "embedded".equalsIgnoreCase(basis) ? AutismServerInfoOverlay.PluginConfidence.STRONG : AutismServerInfoOverlay.PluginConfidence.EXACT;
   }

   private boolean promoteMatchingFeatureEntries(
      Map<String, AutismServerInfoOverlay.PluginScanEntry> entries, AutismPluginPayloadFingerprints.PluginFingerprint fingerprint
   ) {
      if (entries != null && !entries.isEmpty() && fingerprint != null) {
         Set<String> keywords = this.fingerprintKeywords(fingerprint);
         if (keywords.isEmpty()) {
            return false;
         } else {
            boolean changed = false;
            List<String> removeKeys = new ArrayList<>();

            for (Entry<String, AutismServerInfoOverlay.PluginScanEntry> mapEntry : entries.entrySet()) {
               String key = mapEntry.getKey();
               AutismServerInfoOverlay.PluginScanEntry entry = mapEntry.getValue();
               if (key != null && key.startsWith("feature") && entry != null && this.featureEntryMatches(entry, keywords)) {
                  changed |= this.mergePluginEntry(
                     entries,
                     fingerprint.key(),
                     fingerprint.plugin(),
                     entry.commands,
                     fingerprint.channels(),
                     entry.guis,
                     entry.commandBacked,
                     AutismServerInfoOverlay.PluginEvidence.PAYLOAD_CHANNEL,
                     this.payloadFingerprintConfidence(fingerprint)
                  );
                  removeKeys.add(key);
               }
            }

            for (String key : removeKeys) {
               entries.remove(key);
               this.pluginEvidence.remove(key);
               this.pluginConfidence.remove(key);
               this.pluginResultKinds.remove(key);
               changed = true;
            }

            return changed;
         }
      } else {
         return false;
      }
   }

   private Set<String> fingerprintKeywords(AutismPluginPayloadFingerprints.PluginFingerprint fingerprint) {
      LinkedHashSet<String> keywords = new LinkedHashSet<>();
      this.addFingerprintKeywords(keywords, fingerprint.plugin());
      this.addFingerprintKeywords(keywords, fingerprint.key());
      if (fingerprint.channels() != null) {
         for (String channel : fingerprint.channels()) {
            this.addFingerprintKeywords(keywords, channel);
         }
      }

      keywords.removeIf(keyword -> keyword.length() < 3);
      return keywords;
   }

   private void addFingerprintKeywords(Set<String> out, String text) {
      if (out != null && text != null && !text.isBlank()) {
         String lower = text.toLowerCase(Locale.ROOT);

         for (Entry<String, String> entry : COMMAND_FEATURE_LABELS.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            String label = entry.getValue().toLowerCase(Locale.ROOT);
            if (lower.contains(key) || lower.contains(label.replace(" ", "")) || lower.contains(label)) {
               out.add(key);
               out.add(label);
            }
         }
      }
   }

   private boolean featureEntryMatches(AutismServerInfoOverlay.PluginScanEntry entry, Set<String> keywords) {
      if (entry != null && keywords != null && !keywords.isEmpty()) {
         List<String> evidenceText = new ArrayList<>();
         evidenceText.add(entry.displayName);
         evidenceText.addAll(entry.commands);
         evidenceText.addAll(entry.guis);

         for (String text : evidenceText) {
            if (text != null) {
               String lower = text.toLowerCase(Locale.ROOT);

               for (String keyword : keywords) {
                  if (keyword != null && !keyword.isBlank() && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void syncPayloadFingerprintsIntoCurrentScan() {
      if (this.pluginScanInProgress) {
         if (this.mergePayloadFingerprintEntries(this.scanWorkingEntries)) {
            this.cleanupPluginCommandOwnership(this.scanWorkingEntries);
            this.pluginScanLastResponseAt = System.currentTimeMillis();
         }
      } else if (this.pluginScanDone) {
         String address = this.currentServerAddress();
         long revision = AutismPluginPayloadFingerprints.revisionFor(address, this.getLiveBrand());
         if (revision != this.lastPayloadFingerprintRevision) {
            this.lastPayloadFingerprintRevision = revision;
            if (revision > 0L) {
               Map<String, AutismServerInfoOverlay.PluginScanEntry> entries = this.buildCurrentPluginEntries();
               if (this.mergePayloadFingerprintEntries(entries)) {
                  this.applyPluginEntries(entries, false);
                  if (this.pluginScanDone) {
                     this.cacheCurrentScan();
                  }
               }
            }
         }
      }
   }

   private int addProbeVariants(
      Map<Integer, AutismServerInfoOverlay.PluginProbeSpec> probes,
      Set<String> seen,
      int nextId,
      AutismServerInfoOverlay.PluginProbeKind kind,
      String... baseCommands
   ) {
      for (String base : baseCommands) {
         if (base != null && !base.isBlank()) {
            String trimmed = base.trim();
            nextId = this.addPluginProbe(probes, seen, nextId, trimmed, kind, null);
            nextId = this.addPluginProbe(probes, seen, nextId, trimmed + " ", kind, null);
         }
      }

      return nextId;
   }

   private Map<Integer, AutismServerInfoOverlay.PluginProbeSpec> buildPluginProbes() {
      Map<Integer, AutismServerInfoOverlay.PluginProbeSpec> probes = new LinkedHashMap<>();
      Set<String> seen = new HashSet<>();
      int nextId = 1337;
      nextId = this.addPluginProbe(probes, seen, nextId, "/", AutismServerInfoOverlay.PluginProbeKind.ROOT, null);
      nextId = this.addPluginProbe(probes, seen, nextId, "/ ", AutismServerInfoOverlay.PluginProbeKind.ROOT, null);
      nextId = this.addProbeVariants(
         probes, seen, nextId, AutismServerInfoOverlay.PluginProbeKind.PLUGIN_LIST, "/plugins", "/pl", "/bukkit:plugins", "/bukkit:pl"
      );
      nextId = this.addProbeVariants(
         probes,
         seen,
         nextId,
         AutismServerInfoOverlay.PluginProbeKind.VERSION,
         "/ver",
         "/version",
         "/about",
         "/icanhasbukkit",
         "/bukkit:ver",
         "/bukkit:version"
      );
      nextId = this.addProbeVariants(probes, seen, nextId, AutismServerInfoOverlay.PluginProbeKind.HELP, "/help", "/?", "/bukkit:help", "/minecraft:help");

      for (int i = 0; i < "abcdefghijklmnopqrstuvwxyz0123456789".length(); i++) {
         char prefix = "abcdefghijklmnopqrstuvwxyz0123456789".charAt(i);
         String rootProbe = "/" + prefix;
         nextId = this.addPluginProbe(probes, seen, nextId, rootProbe, AutismServerInfoOverlay.PluginProbeKind.ROOT, String.valueOf(prefix));
         nextId = this.addPluginProbe(probes, seen, nextId, "/help " + prefix, AutismServerInfoOverlay.PluginProbeKind.HELP, String.valueOf(prefix));
         nextId = this.addPluginProbe(probes, seen, nextId, "/? " + prefix, AutismServerInfoOverlay.PluginProbeKind.HELP, String.valueOf(prefix));
      }

      for (String namespace : COMMON_PLUGIN_NAMESPACES) {
         nextId = this.addPluginProbe(probes, seen, nextId, "/" + namespace + ":", AutismServerInfoOverlay.PluginProbeKind.NAMESPACE, namespace);
      }

      for (String plugin : HIGH_VALUE_PLUGIN_HINTS) {
         nextId = this.addPluginProbe(probes, seen, nextId, "/version " + plugin, AutismServerInfoOverlay.PluginProbeKind.VERSION, plugin);
         nextId = this.addPluginProbe(probes, seen, nextId, "/ver " + plugin, AutismServerInfoOverlay.PluginProbeKind.VERSION, plugin);
         nextId = this.addPluginProbe(probes, seen, nextId, "/help " + plugin, AutismServerInfoOverlay.PluginProbeKind.HELP, plugin);
      }

      return probes;
   }

   public boolean isAutoProbeConnectionReady() {
      return MC != null && MC.getConnection() != null && !this.currentServerAddress().isBlank();
   }

   public boolean isAutoProbeContextReady() {
      try {
         if (MC != null && MC.getConnection() != null) {
            CommandDispatcher<?> dispatcher = MC.getConnection().getCommands();
            return dispatcher != null && dispatcher.getRoot() != null && !dispatcher.getRoot().getChildren().isEmpty();
         } else {
            return false;
         }
      } catch (Exception var2) {
         return false;
      }
   }

   public boolean autoProbeOnSpawn() {
      if (!this.isAutoProbeConnectionReady()) {
         return false;
      } else {
         String address = this.currentServerAddress();
         String context = this.currentPluginContextSignature();
         this.syncScanStateForCurrentServer();
         if (this.pluginScanInProgress) {
            return true;
         } else if (context.equals(this.lastAutoScanContext) && this.pluginScanDone) {
            return true;
         } else {
            long cachedAt = AutismConfig.getGlobal().getPluginScanTimestamp(address, context);
            boolean fresh = cachedAt > 0L && System.currentTimeMillis() - cachedAt < 1209600000L;
            if (fresh && this.pluginScanDone) {
               this.lastAutoScanContext = context;
               return true;
            } else {
               this.lastAutoScanContext = context;
               AutismConfig.getGlobal().removePluginScan(address, context);
               this.resetScan();
               this.scanPlugins();
               return this.pluginScanInProgress;
            }
         }
      }
   }

   public void resetAutoProbeContext() {
      this.lastAutoScanContext = null;
   }

   private void finalizePluginScan() {
      if (this.pluginScanInProgress || !this.pluginScanDone) {
         Map<String, AutismServerInfoOverlay.PluginScanEntry> finalEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
         this.mergeScanEntries(finalEntries, this.legacyScanEntries);
         this.mergeScanEntries(finalEntries, this.scanWorkingEntries);
         this.mergePayloadFingerprintEntries(finalEntries);
         this.cleanupPluginCommandOwnership(finalEntries);
         this.tracePluginScan(
            "final roots="
               + this.pluginScanCommandTreeRoots
               + " suggestionReplies="
               + this.pluginScanSuggestionReplies
               + " suggestionEntries="
               + this.pluginScanSuggestionEntries
               + " legacy="
               + this.countPluginEntries(this.legacyScanEntries)
               + " working="
               + this.countPluginEntries(this.scanWorkingEntries)
               + " final="
               + this.countPluginEntries(finalEntries)
         );
         this.applyPluginEntries(finalEntries, true);
         this.pluginScanInProgress = false;
         this.pluginScanDone = true;
         this.pendingPluginProbeIds.clear();
         this.pluginProbes.clear();
         this.observedPluginCommands.clear();
         this.queuedPluginProbes.clear();
         this.nextPluginProbeSendAt = 0L;
         this.scanWorkingEntries.clear();
         this.legacyScanEntries.clear();
         this.pluginScanStartedAt = 0L;
         this.pluginScanLastResponseAt = 0L;
         this.pluginScanTotalSteps = 0;
         this.pluginScanCommandTreeRoots = 0;
         this.pluginScanSuggestionReplies = 0;
         this.pluginScanSuggestionEntries = 0;
         this.applyPluginResultsLayout();
         this.cacheCurrentScan();
         this.announcePluginScanComplete();
         this.activeProbeDelayMs = this.pluginProbeDelayMs;
      }
   }

   private void dispatchQueuedPluginProbes() {
      if (PackHideState.isHardLocked()) {
         this.stopPluginScanSilently();
      } else if (this.pluginScanInProgress && MC.getConnection() != null) {
         long now = System.currentTimeMillis();
         if (now >= this.nextPluginProbeSendAt) {
            AutismServerInfoOverlay.PluginProbeRequest request = this.queuedPluginProbes.pollFirst();
            if (request != null) {
               try {
                  ServerboundCommandSuggestionPacket packet = new ServerboundCommandSuggestionPacket(request.id, request.spec.query);
                  MC.getConnection().send(packet);
                  this.nextPluginProbeSendAt = now + this.activeProbeDelayMs;
               } catch (Exception var5) {
                  this.pendingPluginProbeIds.remove(request.id);
                  this.pluginProbes.remove(request.id);
               }
            }
         }
      }
   }

   private int getTotalPluginProbeCount() {
      return Math.max(0, this.pluginScanTotalSteps - 1);
   }

   private long getPluginScanSendWindowMs() {
      return Math.max((long)this.activeProbeDelayMs, (long)this.getTotalPluginProbeCount() * this.activeProbeDelayMs);
   }

   private long getPluginScanFinishedSendingAt() {
      if (this.pluginScanStartedAt <= 0L) {
         return 0L;
      } else if (!this.queuedPluginProbes.isEmpty()) {
         return 0L;
      } else {
         long candidate = this.nextPluginProbeSendAt - this.activeProbeDelayMs;
         return Math.max(this.pluginScanStartedAt, candidate);
      }
   }

   private long getPluginScanHardTimeoutMs() {
      return Math.max(12000L, this.getPluginScanSendWindowMs() + 1400L + 700L);
   }

   private int lerpColor(int from, int to, float t) {
      float clamped = Math.max(0.0F, Math.min(1.0F, t));
      int a1 = from >>> 24 & 0xFF;
      int r1 = from >>> 16 & 0xFF;
      int g1 = from >>> 8 & 0xFF;
      int b1 = from & 0xFF;
      int a2 = to >>> 24 & 0xFF;
      int r2 = to >>> 16 & 0xFF;
      int g2 = to >>> 8 & 0xFF;
      int b2 = to & 0xFF;
      int a = Math.round(a1 + (a2 - a1) * clamped);
      int r = Math.round(r1 + (r2 - r1) * clamped);
      int g = Math.round(g1 + (g2 - g1) * clamped);
      int b = Math.round(b1 + (b2 - b1) * clamped);
      return a << 24 | r << 16 | g << 8 | b;
   }

   private int getSentPluginProbeCount() {
      int total = this.getTotalPluginProbeCount();
      return Math.max(0, Math.min(total, total - this.queuedPluginProbes.size()));
   }

   private int getAnsweredPluginProbeCount() {
      int total = this.getTotalPluginProbeCount();
      return Math.max(0, Math.min(total, total - this.pendingPluginProbeIds.size()));
   }

   private float getPluginScanProgress() {
      if (this.pluginScanDone) {
         return 1.0F;
      } else {
         int total = this.getTotalPluginProbeCount();
         if (total <= 0) {
            return 0.0F;
         } else {
            float sentRatio = (float)this.getSentPluginProbeCount() / total;
            float answeredRatio = (float)this.getAnsweredPluginProbeCount() / total;
            if (!this.queuedPluginProbes.isEmpty()) {
               float progress = 0.8F * sentRatio;
               progress += 0.05F * answeredRatio;
               return Math.max(0.0F, Math.min(0.85F, progress));
            } else {
               float progress = 0.85F;
               long finishedSendingAt = this.getPluginScanFinishedSendingAt();
               long waitWindow = 1400L;
               if (finishedSendingAt > 0L && waitWindow > 0L) {
                  long now = System.currentTimeMillis();
                  float settle = Math.max(0.0F, Math.min(1.0F, (float)(now - finishedSendingAt) / (float)waitWindow));
                  progress += 0.14F * settle;
               }

               return Math.max(0.0F, Math.min(0.99F, progress));
            }
         }
      }
   }

   private int getWorkingPluginCount() {
      return (int)this.scanWorkingEntries.values().stream().filter(entry -> entry != null && entry.displayName != null && !entry.displayName.isBlank()).count();
   }

   private int getDisplayedPluginCount() {
      return this.pluginScanInProgress ? this.getWorkingPluginCount() : this.detectedPlugins.size();
   }

   private String getPluginScanStatusLabel() {
      int foundCount = this.getDisplayedPluginCount();
      return foundCount > 0 ? "Scanning plugins | found " + foundCount : "Scanning plugins";
   }

   private String getPluginScanPhaseLabel() {
      return this.queuedPluginProbes.isEmpty() ? "Analyzing replies" : "Sending probes";
   }

   private int getPluginScanPhaseColor() {
      return this.queuedPluginProbes.isEmpty() ? AutismColors.packetCyan() : AutismColors.packetYellow();
   }

   private String getPluginScanPhaseDetail() {
      int total = Math.max(1, this.getTotalPluginProbeCount());
      return this.queuedPluginProbes.isEmpty() ? this.getAnsweredPluginProbeCount() + "/" + total : this.getSentPluginProbeCount() + "/" + total;
   }

   private void announcePluginScanComplete() {
      if (!this.pluginScanCompletionAnnounced) {
         this.pluginScanCompletionAnnounced = true;
         int count = this.detectedPlugins.size();
         if (count <= 0) {
            AutismClientMessaging.sendPrefixed("Plugin probing finished: no plugins found.");
         } else if (count == 1) {
            AutismClientMessaging.sendPrefixed("Plugin probing finished: found 1 plugin.");
         } else {
            AutismClientMessaging.sendPrefixed("Plugin probing finished: found " + count + " plugins.");
         }
      }
   }

   public boolean shouldRenderBackgroundProbeBanner() {
      return this.pluginScanInProgress;
   }

   public boolean isPacketObservationActive() {
      return this.visible || this.pluginScanInProgress || this.pluginScanDone || !this.detectedPlugins.isEmpty();
   }

   public void renderBackgroundProbeBanner(GuiGraphicsExtractor ctx) {
      if (this.shouldRenderBackgroundProbeBanner() && MC != null && MC.getWindow() != null && this.textRenderer != null) {
         DirectViewport viewport = this.surface.viewport();
         String status = this.getPluginScanStatusLabel();
         String phase = this.getPluginScanPhaseLabel();
         String detail = this.getPluginScanPhaseDetail();
         int progressPercent = Math.max(0, Math.min(100, Math.round(this.getPluginScanProgress() * 100.0F)));
         String percentComponent = progressPercent + "%";
         int screenW = Math.round(viewport.uiWidth());
         int statusWidth = UiText.width(this.textRenderer, status, this.theme.fontFor(UiTone.MUTED), this.theme.color(UiTone.MUTED));
         int phaseWidth = UiText.width(this.textRenderer, phase, this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY));
         int detailWidth = UiText.width(this.textRenderer, detail, this.theme.fontFor(UiTone.MUTED), this.getPluginScanPhaseColor());
         int percentWidth = UiText.width(this.textRenderer, percentComponent, this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY));
         int contentW = Math.max(180, Math.max(statusWidth, Math.max(phaseWidth + 12 + detailWidth, percentWidth + 150)));
         int boxW = Math.max(1, Math.min(Math.max(1, screenW - 16), Math.max(236, contentW + 24)));
         int boxH = 42;
         int boxX = Math.max(8, (screenW - boxW) / 2);
         int boxY = 8;
         int innerX = boxX + 8;
         int barY = boxY + 17;
         int barW = boxW - 16;
         int barH = 10;
         viewport.push(ctx);

         try {
            DirectRenderContext bannerContext = new DirectRenderContext(ctx, this.textRenderer, viewport, this.theme, 0.0F, 0.0F, 0.0F);
            int border = this.theme.borderColor();
            int headerAccent = this.theme.headerAccent();
            UiRenderer.window(
               ctx,
               UiBounds.of(boxX, boxY, boxW, boxH),
               14,
               bannerContext.applyAlpha(this.theme.windowFill()),
               bannerContext.applyAlpha(this.theme.headerFill()),
               bannerContext.applyAlpha(this.theme.windowFill()),
               bannerContext.applyAlpha(border),
               bannerContext.applyAlpha(headerAccent),
               1.0F
            );
            UiText.draw(ctx, this.textRenderer, status, this.theme.fontFor(UiTone.MUTED), this.theme.color(UiTone.MUTED), innerX, boxY + 3, false);
            UiText.draw(
               ctx,
               this.textRenderer,
               percentComponent,
               this.theme.fontFor(UiTone.BODY),
               this.getPluginScanPhaseColor(),
               boxX + boxW - 8 - percentWidth,
               boxY + 3,
               false
            );
            UiRenderer.frame(
               ctx, UiBounds.of(innerX, barY, barW, barH), bannerContext.applyAlpha(this.theme.overlaySurfaceSoft(657676)), bannerContext.applyAlpha(-7392975)
            );
            int fillW = Math.max(0, Math.min(barW - 2, Math.round((barW - 2) * this.getPluginScanProgress())));
            if (fillW > 0) {
               int fillColor = UiSizing.lerpColor(-42406, -10035062, this.getPluginScanProgress());
               UiRenderer.rect(ctx, UiBounds.of(innerX + 1, barY + 1, fillW, barH - 2), bannerContext.applyAlpha(fillColor));
            }

            UiText.draw(ctx, this.textRenderer, phase, this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY), innerX, barY + 13, false);
            UiText.draw(
               ctx,
               this.textRenderer,
               detail,
               this.theme.fontFor(UiTone.MUTED),
               this.getPluginScanPhaseColor(),
               boxX + boxW - 8 - detailWidth,
               barY + 13,
               false
            );
         } finally {
            viewport.pop(ctx);
         }
      }
   }

   private void updatePluginScanLifecycle() {
      if (this.pluginScanInProgress) {
         this.dispatchQueuedPluginProbes();
         long now = System.currentTimeMillis();
         boolean allProbesSent = this.queuedPluginProbes.isEmpty();
         boolean allResponsesReceived = this.pendingPluginProbeIds.isEmpty();
         long finishedSendingAt = this.getPluginScanFinishedSendingAt();
         long settleWindow = allResponsesReceived ? 1400L : Math.max(1400L, 450L);
         boolean settledAfterSend = allProbesSent && finishedSendingAt > 0L && now - finishedSendingAt >= settleWindow;
         boolean settledAfterLastResponse = this.pluginScanLastResponseAt > 0L && now - this.pluginScanLastResponseAt >= 700L;
         boolean timedOut = this.pluginScanStartedAt > 0L && now - this.pluginScanStartedAt >= this.getPluginScanHardTimeoutMs();
         if (allProbesSent && settledAfterSend && (!allResponsesReceived || settledAfterLastResponse) || timedOut) {
            this.finalizePluginScan();
         }
      }
   }

   public void tickBackground() {
      this.syncScanStateForCurrentServer();
      this.syncPayloadFingerprintsIntoCurrentScan();
      if (this.pluginScanInProgress) {
         this.updatePluginScanLifecycle();
      }
   }

   private void parseCommandTree(RootCommandNode<?> root) {
      Map<String, AutismServerInfoOverlay.PluginScanEntry> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      entries.putAll(this.scanWorkingEntries);
      Map<String, AutismServerInfoOverlay.PluginScanEntry> legacyEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      legacyEntries.putAll(this.legacyScanEntries);

      for (CommandNode<?> child : root.getChildren()) {
         String name = child.getName();
         this.pluginScanCommandTreeRoots++;
         if (name.contains(":")) {
            String[] parts = name.split(":", 2);
            String ns = parts[0].toLowerCase();
            String cmd = parts[1];
            if (!VANILLA_NAMESPACES.contains(ns)) {
               this.mergePluginEntry(legacyEntries, ns, List.of(cmd), true, AutismServerInfoOverlay.PluginEvidence.COMMAND_TREE);
               this.mergeCommandEvidence(entries, ns + ":" + cmd, AutismServerInfoOverlay.PluginEvidence.COMMAND_TREE);
               this.addObservedPluginCommand(cmd);
            }
         } else {
            this.addObservedPluginCommand(name);
            String token = this.normalizeCommandToken(name);
            if (COMMAND_FEATURE_LABELS.containsKey(token)) {
               this.mergeCommandEvidence(entries, name, AutismServerInfoOverlay.PluginEvidence.COMMAND_TREE);
            }
         }
      }

      this.inferLegacyPluginsFromObservedCommands(legacyEntries);
      this.inferPluginsFromObservedCommands(entries);
      this.mergeScanEntries(entries, legacyEntries);
      this.cleanupPluginCommandOwnership(entries);
      this.legacyScanEntries.clear();
      this.legacyScanEntries.putAll(legacyEntries);
      this.scanWorkingEntries.clear();
      this.scanWorkingEntries.putAll(entries);
      this.tracePluginScan(
         "command tree roots="
            + this.pluginScanCommandTreeRoots
            + " legacy="
            + this.countPluginEntries(this.legacyScanEntries)
            + " working="
            + this.countPluginEntries(this.scanWorkingEntries)
      );
   }

   public void resetScan() {
      this.clearLocalScanState(null, "");
      this.searchField.setText("");
      this.searchField.setFocused(false);
      this.resolvedIp = null;
      this.lastResolvedAddress = null;
      this.invalidatePluginContextSignature();
   }

   public void stopPluginScanSilently() {
      this.pluginScanInProgress = false;
      this.pluginScanStartedAt = 0L;
      this.pluginScanLastResponseAt = 0L;
      this.pluginScanTotalSteps = 0;
      this.pluginScanCommandTreeRoots = 0;
      this.pluginScanSuggestionReplies = 0;
      this.pluginScanSuggestionEntries = 0;
      this.pendingPluginProbeIds.clear();
      this.pluginProbes.clear();
      this.queuedPluginProbes.clear();
      this.observedSuggestionRequests.clear();
      this.nextPluginProbeSendAt = 0L;
      this.uiRebuildRequested = true;
   }

   private void startManualPluginScan() {
      String address = this.currentServerAddress();
      AutismConfig.getGlobal().removePluginScan(address);
      this.resetScan();
      this.scanPlugins(true);
      this.saveState();
   }

   private int addPluginProbe(
      Map<Integer, AutismServerInfoOverlay.PluginProbeSpec> probes,
      Set<String> seen,
      int nextId,
      String query,
      AutismServerInfoOverlay.PluginProbeKind kind,
      String hint
   ) {
      if (query != null && !query.isBlank()) {
         String key = kind.name() + "|" + query + "|" + (hint == null ? "" : hint);
         if (!seen.add(key)) {
            return nextId;
         } else {
            probes.put(nextId++, new AutismServerInfoOverlay.PluginProbeSpec(query, kind, hint));
            return nextId;
         }
      } else {
         return nextId;
      }
   }

   private void scanPlugins() {
      this.scanPlugins(false);
   }

   private void scanPlugins(boolean forceLive) {
      if (!PackHideState.isHardLocked()) {
         if (!forceLive) {
            this.syncScanStateForCurrentServer();
         }

         if ((forceLive || !this.pluginScanDone) && !this.pluginScanInProgress) {
            if (MC.getConnection() == null) {
               this.pluginScanDone = true;
               this.cacheCurrentScan();
            } else {
               this.activeProbeDelayMs = this.pluginProbeDelayMs;
               this.scannedServerAddress = this.currentServerAddress();
               this.scannedPluginContextSignature = this.currentPluginContextSignature();
               this.activeTab = 1;
               this.pluginScanInProgress = true;
               this.pluginScanDone = false;
               this.pluginScanStartedAt = System.currentTimeMillis();
               this.pluginScanLastResponseAt = this.pluginScanStartedAt;
               this.pendingPluginProbeIds.clear();
               this.pluginProbes.clear();
               this.observedPluginCommands.clear();
               this.queuedPluginProbes.clear();
               this.pluginEvidence.clear();
               this.pluginConfidence.clear();
               this.pluginResultKinds.clear();
               this.scanWorkingEntries.clear();
               this.legacyScanEntries.clear();
               this.pluginScanCommandTreeRoots = 0;
               this.pluginScanSuggestionReplies = 0;
               this.pluginScanSuggestionEntries = 0;
               this.nextPluginProbeSendAt = this.pluginScanStartedAt + this.activeProbeDelayMs;
               this.applyPluginScanningLayout();

               try {
                  CommandDispatcher<?> dispatcher = MC.getConnection().getCommands();
                  if (dispatcher != null) {
                     RootCommandNode<?> root = dispatcher.getRoot();
                     if (root != null && !root.getChildren().isEmpty()) {
                        this.parseCommandTree(root);
                     }
                  }

                  this.mergePayloadFingerprintEntries(this.scanWorkingEntries);
                  Map<Integer, AutismServerInfoOverlay.PluginProbeSpec> probes = this.buildPluginProbes();
                  this.pluginScanTotalSteps = probes.size() + 1;

                  for (Entry<Integer, AutismServerInfoOverlay.PluginProbeSpec> probe : probes.entrySet()) {
                     this.pendingPluginProbeIds.add(probe.getKey());
                     this.pluginProbes.put(probe.getKey(), probe.getValue());
                     this.queuedPluginProbes.addLast(new AutismServerInfoOverlay.PluginProbeRequest(probe.getKey(), probe.getValue()));
                  }

                  this.updatePluginScanLifecycle();
               } catch (Exception var6) {
                  this.finalizePluginScan();
               }
            }
         }
      }
   }

   public void onCommandSuggestionRequest(Packet<?> packet) {
      if (packet != null && !PackHideState.isActive()) {
         if ("ServerboundCommandSuggestionPacket".equals(packet.getClass().getSimpleName())) {
            int id = this.intValue(packet, -1, "getId", "id");
            String command = this.stringValue(packet, "getCommand", "command");
            if (id >= 0 && command != null && !command.isBlank()) {
               if (!this.pluginProbes.containsKey(id)) {
                  this.observedSuggestionRequests
                     .put(id, new AutismServerInfoOverlay.PluginProbeSpec(command, AutismServerInfoOverlay.PluginProbeKind.USER, null));

                  while (this.observedSuggestionRequests.size() > 256) {
                     Integer first = this.observedSuggestionRequests.keySet().iterator().next();
                     this.observedSuggestionRequests.remove(first);
                  }
               }
            }
         }
      }
   }

   public void onOutgoingCommandPacket(Packet<?> packet) {
      if (packet != null && !PackHideState.isActive()) {
         String simple = packet.getClass().getSimpleName();
         String command = null;
         if ("ServerboundChatCommandPacket".equals(simple) || "ServerboundChatCommandSignedPacket".equals(simple)) {
            command = this.stringValue(packet, "command", "getCommand");
         } else if ("ServerboundChatPacket".equals(simple)) {
            String message = this.stringValue(packet, "message", "getMessage");
            if (message != null && message.trim().startsWith("/")) {
               command = message.trim().substring(1);
            }
         }

         if (command != null && !command.isBlank()) {
            String root = this.normalizeCommandToken(command);
            if (this.isTrackableCommandToken(root)) {
               this.registerRecentCommand(root, command);
               this.mergeLiveCommandEvidence(command, AutismServerInfoOverlay.PluginEvidence.USER_AUTOCOMPLETE, null);
            }
         }
      }
   }

   public void onOpenScreenPacket(Packet<?> packet) {
      if (packet != null && !PackHideState.isActive()) {
         if ("ClientboundOpenScreenPacket".equals(packet.getClass().getSimpleName())) {
            String title = this.componentText(this.invokeFirstNoArg(packet, "getTitle", "title"));
            if (title != null && !title.isBlank()) {
               AutismServerInfoOverlay.ObservedCommand command = this.findRecentCommand(System.currentTimeMillis());
               if (command != null) {
                  this.mergeLiveCommandEvidence(command.fullCommand, AutismServerInfoOverlay.PluginEvidence.COMMAND_GUI, title);
               } else {
                  String feature = this.featureLabelFromGuiTitle(title);
                  if (feature != null && !feature.isBlank()) {
                     this.mergeLiveGuiFeature(feature, title);
                  }
               }
            }
         }
      }
   }

   private void registerRecentCommand(String root, String fullCommand) {
      if (root != null && !root.isBlank()) {
         this.recentCommands.addLast(new AutismServerInfoOverlay.ObservedCommand(root, fullCommand == null ? root : fullCommand, System.currentTimeMillis()));

         while (this.recentCommands.size() > 24) {
            this.recentCommands.removeFirst();
         }
      }
   }

   private AutismServerInfoOverlay.ObservedCommand findRecentCommand(long now) {
      AutismServerInfoOverlay.ObservedCommand best = null;

      while (!this.recentCommands.isEmpty() && now - this.recentCommands.peekFirst().timestampMs > 5000L) {
         this.recentCommands.removeFirst();
      }

      for (AutismServerInfoOverlay.ObservedCommand command : this.recentCommands) {
         if (command != null && now - command.timestampMs <= 5000L) {
            best = command;
         }
      }

      return best;
   }

   private void mergeLiveCommandEvidence(String rawCommand, AutismServerInfoOverlay.PluginEvidence evidence, String guiTitle) {
      if (rawCommand != null && !rawCommand.isBlank()) {
         MC.execute(
            () -> {
               if (MC.getConnection() != null) {
                  this.syncScanStateForCurrentServer();
                  this.registerRecentCommand(this.normalizeCommandToken(rawCommand), rawCommand);
                  Map<String, AutismServerInfoOverlay.PluginScanEntry> entries = (Map<String, AutismServerInfoOverlay.PluginScanEntry>)(this.pluginScanInProgress
                     ? new TreeMap<>(this.scanWorkingEntries)
                     : this.buildCurrentPluginEntries());
                  boolean changed = this.mergeCommandEvidence(entries, rawCommand, evidence);
                  if (guiTitle != null && !guiTitle.isBlank()) {
                     changed |= this.attachGuiToCommandEntry(entries, rawCommand, guiTitle);
                  }

                  if (changed) {
                     this.applyLivePluginEntries(entries);
                  }
               }
            }
         );
      }
   }

   private void mergeLiveGuiFeature(String feature, String guiTitle) {
   }

   private boolean attachGuiToCommandEntry(Map<String, AutismServerInfoOverlay.PluginScanEntry> entries, String rawCommand, String guiTitle) {
      String token = this.normalizeCommandToken(rawCommand);
      if (!token.isBlank() && guiTitle != null && !guiTitle.isBlank()) {
         String ownerKey = this.bestCommandOwnerKey(entries, token);
         if (ownerKey == null) {
            return false;
         } else {
            AutismServerInfoOverlay.PluginScanEntry entry = entries.get(ownerKey);
            if (entry == null) {
               return false;
            } else {
               int before = entry.guis.size();
               entry.guis.add(guiTitle.trim());
               if (!this.isWeakCommandEvidence(entry.evidence)) {
                  entry.evidence = this.mergeEvidence(entry.evidence, AutismServerInfoOverlay.PluginEvidence.COMMAND_GUI);
               }

               entry.confidence = this.mergeConfidence(entry.confidence, AutismServerInfoOverlay.PluginConfidence.FEATURE);
               this.pluginEvidence.put(ownerKey, this.mergeEvidence(this.pluginEvidence.get(ownerKey), entry.evidence));
               this.pluginConfidence.put(ownerKey, this.mergeConfidence(this.pluginConfidence.get(ownerKey), AutismServerInfoOverlay.PluginConfidence.FEATURE));
               return before != entry.guis.size();
            }
         }
      } else {
         return false;
      }
   }

   private void applyLivePluginEntries(Map<String, AutismServerInfoOverlay.PluginScanEntry> entries) {
      this.mergePayloadFingerprintEntries(entries);
      this.cleanupPluginCommandOwnership(entries);
      if (this.pluginScanInProgress) {
         this.scanWorkingEntries.clear();
         this.scanWorkingEntries.putAll(entries);
         this.pluginScanLastResponseAt = System.currentTimeMillis();
         this.invalidatePluginRows();
      } else if (this.pluginScanDone) {
         this.scannedServerAddress = this.currentServerAddress();
         this.scannedPluginContextSignature = this.currentPluginContextSignature();
         this.pluginScanDone = true;
         this.pluginScanInProgress = false;
         this.applyPluginEntries(entries, false);
         this.cacheCurrentScan();
      }
   }

   private String featureLabelFromGuiTitle(String title) {
      if (title != null && !title.isBlank()) {
         String clean = title.toLowerCase(Locale.ROOT).replaceAll("§.", "");

         for (Entry<String, String> entry : COMMAND_FEATURE_LABELS.entrySet()) {
            String key = entry.getKey();
            if (key.length() >= 3 && clean.contains(key)) {
               return entry.getValue();
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public void onCommandSuggestions(int id, ClientboundCommandSuggestionsPacket packet) {
      AutismServerInfoOverlay.PluginProbeSpec probe = this.pluginProbes.get(id);
      AutismServerInfoOverlay.PluginProbeSpec observed = this.observedSuggestionRequests.remove(id);
      boolean scanProbe = probe != null || this.pendingPluginProbeIds.contains(id);
      if (scanProbe || observed != null) {
         MC.execute(
            () -> {
               boolean liveObservation = !scanProbe && observed != null;
               if (this.scannedServerAddress != null && !this.scannedServerAddress.isBlank() || liveObservation) {
                  String currentAddress = this.currentServerAddress();
                  String currentContextSignature = this.currentPluginContextSignature();
                  if (liveObservation || currentAddress.equals(this.scannedServerAddress)) {
                     if (!liveObservation && !currentContextSignature.isEmpty() && !currentContextSignature.equals(this.scannedPluginContextSignature)) {
                        this.syncScanStateForCurrentServer();
                     } else {
                        List<net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket.Entry> list = packet.suggestions();
                        Map<String, AutismServerInfoOverlay.PluginScanEntry> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                        if (this.pluginScanInProgress) {
                           entries.putAll(this.scanWorkingEntries);
                        } else {
                           entries.putAll(this.buildCurrentPluginEntries());
                        }

                        Map<String, AutismServerInfoOverlay.PluginScanEntry> legacyEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                        legacyEntries.putAll(this.legacyScanEntries);
                        if (scanProbe) {
                           this.pendingPluginProbeIds.remove(id);
                           this.pluginScanLastResponseAt = System.currentTimeMillis();
                           this.pluginScanSuggestionReplies++;
                           this.pluginScanSuggestionEntries = this.pluginScanSuggestionEntries + (list == null ? 0 : list.size());
                        }

                        for (net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket.Entry s : list) {
                           String text = s.text();
                           String normalizedToken = this.normalizeCommandToken(text);
                           if (!normalizedToken.isEmpty()) {
                              this.addObservedPluginCommand(normalizedToken);
                           }

                           if (text.contains(":")) {
                              String[] parts = text.split(":", 2);
                              String ns = parts[0].toLowerCase(Locale.ROOT);
                              String cmd = parts.length > 1 ? parts[1] : "";
                              if (!VANILLA_NAMESPACES.contains(ns)) {
                                 this.mergePluginEntry(legacyEntries, ns, List.of(cmd), true, AutismServerInfoOverlay.PluginEvidence.COMMAND_TREE);
                                 this.addObservedPluginCommand(cmd);
                              }

                              this.mergeCommandEvidence(
                                 entries,
                                 text,
                                 scanProbe ? AutismServerInfoOverlay.PluginEvidence.COMMAND_TREE : AutismServerInfoOverlay.PluginEvidence.USER_AUTOCOMPLETE
                              );
                           } else if (probe != null) {
                              if (probe.kind == AutismServerInfoOverlay.PluginProbeKind.NAMESPACE && probe.hint != null && !normalizedToken.isEmpty()) {
                                 this.mergePluginEntry(
                                    legacyEntries, probe.hint, List.of(normalizedToken), true, AutismServerInfoOverlay.PluginEvidence.NAMESPACE
                                 );
                                 this.mergePluginEntry(entries, probe.hint, List.of(normalizedToken), true, AutismServerInfoOverlay.PluginEvidence.NAMESPACE);
                              } else if ((
                                    probe.kind == AutismServerInfoOverlay.PluginProbeKind.PLUGIN_LIST
                                       || probe.kind == AutismServerInfoOverlay.PluginProbeKind.VERSION
                                 )
                                 && text != null) {
                                 String pluginCandidate = text.trim();
                                 String pluginKey = this.normalizePluginKey(pluginCandidate);
                                 if (this.isLikelyPluginNameCandidate(pluginCandidate, probe.kind) && !ROOT_COMMAND_PLUGIN_ALIASES.containsKey(pluginKey)) {
                                    AutismServerInfoOverlay.PluginEvidence candidateEvidence = probe.kind
                                          == AutismServerInfoOverlay.PluginProbeKind.PLUGIN_LIST
                                       ? AutismServerInfoOverlay.PluginEvidence.PLUGIN_LIST
                                       : AutismServerInfoOverlay.PluginEvidence.VERSION_HINT;
                                    AutismServerInfoOverlay.PluginConfidence candidateConfidence = this.confidenceForEvidence(candidateEvidence);
                                    this.mergePluginEntry(
                                       legacyEntries,
                                       null,
                                       pluginCandidate,
                                       List.of(),
                                       List.of(),
                                       List.of(),
                                       false,
                                       candidateEvidence,
                                       candidateConfidence,
                                       AutismServerInfoOverlay.PluginResultKind.PLUGIN
                                    );
                                    this.mergePluginEntry(
                                       entries,
                                       null,
                                       pluginCandidate,
                                       List.of(),
                                       List.of(),
                                       List.of(),
                                       false,
                                       candidateEvidence,
                                       candidateConfidence,
                                       AutismServerInfoOverlay.PluginResultKind.PLUGIN
                                    );
                                 }
                              } else if (probe.kind == AutismServerInfoOverlay.PluginProbeKind.HELP && probe.hint != null && !normalizedToken.isEmpty()) {
                                 this.mergePluginEntry(
                                    legacyEntries, probe.hint, List.of(normalizedToken), true, AutismServerInfoOverlay.PluginEvidence.HELP_HINT
                                 );
                                 this.mergePluginEntry(entries, probe.hint, List.of(normalizedToken), true, AutismServerInfoOverlay.PluginEvidence.HELP_HINT);
                              } else if (probe.kind == AutismServerInfoOverlay.PluginProbeKind.ROOT && !normalizedToken.isEmpty()) {
                                 this.mergeScannerAutocompleteHint(entries, normalizedToken);
                              }
                           } else if (observed != null && !normalizedToken.isEmpty()) {
                              this.mergeCommandEvidence(entries, normalizedToken, AutismServerInfoOverlay.PluginEvidence.USER_AUTOCOMPLETE);
                           }
                        }

                        this.inferLegacyPluginsFromObservedCommands(legacyEntries);
                        this.inferPluginsFromObservedCommands(entries);
                        this.mergeScanEntries(entries, legacyEntries);
                        this.cleanupPluginCommandOwnership(entries);
                        if (!scanProbe && !this.pluginScanInProgress) {
                           this.applyLivePluginEntries(entries);
                        } else {
                           this.legacyScanEntries.clear();
                           this.legacyScanEntries.putAll(legacyEntries);
                           this.scanWorkingEntries.clear();
                           this.scanWorkingEntries.putAll(entries);
                           this.tracePluginScan(
                              "suggestions replies="
                                 + this.pluginScanSuggestionReplies
                                 + " suggestions="
                                 + this.pluginScanSuggestionEntries
                                 + " legacy="
                                 + this.countPluginEntries(this.legacyScanEntries)
                                 + " working="
                                 + this.countPluginEntries(this.scanWorkingEntries)
                           );
                           this.updatePluginScanLifecycle();
                        }
                     }
                  }
               }
            }
         );
      }
   }

   public void onCommandTreeChanged() {
      this.invalidatePluginContextSignature();
      MC.execute(() -> {
         String currentAddress = this.currentServerAddress();
         if (!currentAddress.isEmpty()) {
            String currentContextSignature = this.currentPluginContextSignature();
            if (!currentAddress.equals(this.scannedServerAddress) || !currentContextSignature.equals(this.scannedPluginContextSignature)) {
               this.syncScanStateForCurrentServer();
            }
         }
      });
   }

   public void onPayloadFingerprintUpdated() {
      this.lastPayloadFingerprintRevision = -1L;
      this.invalidatePluginRows();
      this.invalidatePluginContextSignature();
   }

   public void openInfoTab() {
      this.setVisible(true);
      this.selectTab(0);
   }

   public void openPluginsTab() {
      this.setVisible(true);
      this.selectTab(1);
   }

   public void toggle() {
      this.setVisible(!this.visible);
   }

   @Override
   public void setVisible(boolean v) {
      this.visible = v;
      if (v) {
         this.windowNode.syncShowBody(!this.collapsed);
         AutismOverlayManager.get().bringToFront(this);
         this.syncScanStateForCurrentServer();
         if (this.activeTab == 0) {
            this.applyInfoLayout();
         } else if (this.pluginScanInProgress) {
            this.applyPluginScanningLayout();
         } else if (!this.pluginScanDone) {
            this.applyPluginSetupLayout();
         } else {
            this.applyPluginResultsLayout();
         }
      }

      this.saveState();
   }

   @Override
   public boolean isVisible() {
      return this.visible;
   }

   @Override
   public boolean isCollapsed() {
      return this.collapsed;
   }

   @Override
   public void setCollapsed(boolean c) {
      if (this.collapsed != c) {
         this.collapsed = c;
         this.windowNode.syncShowBody(!this.collapsed);
         if (c) {
            this.clearHiddenInteractionState();
         }

         this.saveState();
      }
   }

   @Override
   public String getOverlayId() {
      return "autism-serverinfo";
   }

   @Override
   public boolean isMouseOver(double mx, double my) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiX = viewport.toUiX(mx);
         float uiY = viewport.toUiY(my);
         return uiX >= this.panelX && uiX <= this.panelX + this.panelW && uiY >= this.panelY && uiY <= this.panelY + this.panelH;
      }
   }

   @Override
   public boolean isOverDragBar(double mx, double my) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         return this.isOverHeaderUi(viewport.toUiX(mx), viewport.toUiY(my));
      }
   }

   @Override
   public boolean hasTextFieldFocused() {
      return this.surface.hasFocusedTextInput();
   }

   @Override
   public AutismWindowLayout getBounds() {
      return new AutismWindowLayout(this.panelX, this.panelY, this.panelW, this.panelH, this.visible, this.collapsed);
   }

   @Override
   public void setBounds(AutismWindowLayout b) {
      if (b != null) {
         AutismWindowLayout clamped = this.clampToViewport(b);
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.panelW = clamped.width;
         this.panelH = clamped.height;
         this.visible = clamped.visible;
         this.collapsed = clamped.collapsed;
         this.windowNode.syncShowBody(!this.collapsed);
         this.rememberCurrentTabSize();
      }
   }

   @Override
   public int getMinWidth() {
      return this.getSharedPanelWidth();
   }

   @Override
   public int getMinHeight() {
      if (this.activeTab == 2) {
         return this.getRadarPanelHeight();
      } else if (this.activeTab == 1) {
         if (this.pluginScanInProgress) {
            return this.getPluginScanningPanelHeight();
         } else {
            return !this.pluginScanDone ? this.getPluginSetupPanelHeight() : this.getPluginResultsMinHeight();
         }
      } else {
         return this.measurePreferredInfoPanelHeight();
      }
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
      if (this.visible) {
         this.syncScanStateForCurrentServer();
         this.syncPayloadFingerprintsIntoCurrentScan();
         this.updatePluginScanLifecycle();
         long nowMs = System.currentTimeMillis();
         if (this.uiRebuildRequested
            || this.pluginScanInProgress && nowMs - this.lastUiRebuildMs >= 100L
            || this.activeTab == 2 && nowMs - this.lastUiRebuildMs >= 500L) {
            this.clickRegions.clear();
            this.rebuildUi();
            this.lastUiRebuildMs = nowMs;
         }

         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mx);
         float uiMouseY = viewport.toUiY(my);
         boolean active = AutismOverlayManager.get().isFocusedOverlay(this) || AutismOverlayManager.get().isTopOverlay(this);
         boolean headerHovered = this.isOverHeaderUi(uiMouseX, uiMouseY);
         DirectRenderContext metrics = new DirectRenderContext(ctx, this.textRenderer, viewport, this.theme, uiMouseX, uiMouseY, delta);
         this.windowNode.setShowBody(!this.collapsed);
         this.windowNode.setActive(active);
         this.windowNode.setHeaderHovered(headerHovered);
         int preferredHeight = Math.round(this.windowNode.preferredHeight(metrics, this.panelW));
         if (this.collapsed) {
            this.panelH = preferredHeight;
         } else if (this.activeTab == 0) {
            this.panelH = this.getSharedPanelHeight();
         } else if (this.activeTab == 2) {
            this.panelH = this.getRadarPanelHeight();
         } else if (this.pluginScanInProgress) {
            this.panelH = this.getPluginScanningPanelHeight();
         } else if (!this.pluginScanDone) {
            this.panelH = this.getPluginSetupPanelHeight();
         } else {
            this.panelH = Math.max(this.getPluginResultsMinHeight(), Math.min(this.getPluginResultsMaxHeight(), this.getSharedPanelHeight()));
         }

         AutismWindowLayout clamped = this.clampToViewport(
            new AutismWindowLayout(this.panelX, this.panelY, this.panelW, this.panelH, this.visible, this.collapsed)
         );
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.panelW = clamped.width;
         this.panelH = clamped.height;
         this.windowNode.setBounds(this.panelX, this.panelY, this.panelW, this.panelH);
         this.surface.render(ctx, mx, my, delta);
         if (!this.collapsed && this.activeTab == 1 && this.pluginScanDone && !this.pluginScanInProgress) {
            this.clickRegions.clear();
            this.renderPluginResultsViewport(ctx, viewport, uiMouseX, uiMouseY, delta);
         } else if (!this.collapsed && this.activeTab == 2 && this.isRadarAuthenticated()) {
            this.clickRegions.clear();
            this.renderRadarViewport(ctx, viewport, uiMouseX, uiMouseY, delta);
         }
      }
   }

   private String getReportedVersion(ServerData entry) {
      return entry != null && entry.version != null ? entry.version.getString() : "--";
   }

   private String getRealServerVersion() {
      String version = AutismSharedState.get().getRealServerVersion(this.getDisplayedServerAddress());
      if (version != null && !version.isBlank()) {
         return version;
      } else {
         String brand = this.getLiveBrand();
         if (brand != null && !brand.isBlank() && !"--".equals(brand)) {
            String extracted = this.extractVersionFromBrand(brand);
            if (extracted != null) {
               return extracted;
            }
         }

         return "--";
      }
   }

   private String extractVersionFromBrand(String brand) {
      if (brand != null && !brand.isBlank()) {
         String lower = brand.toLowerCase(Locale.ROOT);
         int dashIdx = lower.indexOf(45);
         if (dashIdx >= 0 && dashIdx + 1 < brand.length()) {
            String afterDash = brand.substring(dashIdx + 1);
            int spaceIdx = afterDash.indexOf(32);
            return spaceIdx >= 0 ? afterDash.substring(0, spaceIdx) : afterDash;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private void renderPluginResultsViewport(GuiGraphicsExtractor ctx, DirectViewport viewport, float uiMouseX, float uiMouseY, float delta) {
      int x = Math.round(this.pluginListSlot.x());
      int y = Math.round(this.pluginListSlot.y());
      int rowW = Math.round(this.pluginListSlot.width());
      int bodyBottom = this.panelY + this.panelH - 10;
      int rawViewH = Math.max(0, Math.min(Math.round(this.pluginListSlot.height()), bodyBottom - y));
      int viewH = this.getPluginlistViewportHeight(rawViewH);
      int listTop = y;
      int listBottom = y + viewH;
      if (rowW > 0 && viewH > 0) {
         int clipLeft = x + 1;
         int clipTop = y + 1;
         int clipRight = x + rowW - 1;
         int clipBottom = listBottom - 1;
         int innerViewH = this.getPluginListInnerHeight(viewH);
         if (innerViewH > 0) {
            int rowContentW = Math.max(48, clipRight - clipLeft);
            List<AutismServerInfoOverlay.PluginListRow> rows = this.getCachedPluginRows(this.searchField.text(), rowContentW);
            int estimatedContentHeight = this.estimatePluginContentHeight(rows);
            int maxScroll = Math.max(0, estimatedContentHeight - innerViewH);
            int quantizedTarget = this.quantizeScrollOffset(this.pluginScrollState.targetOffset(), this.pluginListRowStep(), maxScroll);
            this.pluginScrollState.setTarget(quantizedTarget, maxScroll);
            int visualScroll = this.quantizeScrollOffset(this.pluginScrollState.tick(delta, maxScroll), this.pluginListRowStep(), maxScroll);
            this.pluginContentHeight = estimatedContentHeight;
            CompactScrollbar.Metrics scrollbarMetrics = this.getPluginScrollbarMetrics(x, y, rowW, viewH);
            int scrolledRowContentW = Math.max(48, clipRight - clipLeft - (scrollbarMetrics.hasScroll() ? 10 : 0));
            if (scrolledRowContentW != rowContentW) {
               rowContentW = scrolledRowContentW;
               rows = this.getCachedPluginRows(this.searchField.text(), scrolledRowContentW);
               estimatedContentHeight = this.estimatePluginContentHeight(rows);
               maxScroll = Math.max(0, estimatedContentHeight - innerViewH);
               quantizedTarget = this.quantizeScrollOffset(this.pluginScrollState.targetOffset(), this.pluginListRowStep(), maxScroll);
               this.pluginScrollState.setTarget(quantizedTarget, maxScroll);
               visualScroll = this.quantizeScrollOffset(this.pluginScrollState.tick(0.0F, maxScroll), this.pluginListRowStep(), maxScroll);
               this.pluginContentHeight = estimatedContentHeight;
               scrollbarMetrics = this.getPluginScrollbarMetrics(x, y, rowW, viewH);
            }

            boolean active = AutismOverlayManager.get().isFocusedOverlay(this) || AutismOverlayManager.get().isTopOverlay(this);
            viewport.push(ctx);

            try {
               CompactListRenderer.drawFrame(ctx, x, listTop, rowW, viewH, active);
               viewport.enableScissor(ctx, clipLeft, clipTop, clipRight, clipBottom);

               try {
                  int startIndex = this.firstVisiblePluginRowIndex(rows, visualScroll);
                  int cy = clipTop - visualScroll + this.pluginRowOffset(startIndex);

                  for (int rowIndex = startIndex; rowIndex < rows.size(); rowIndex++) {
                     AutismServerInfoOverlay.PluginListRow row = rows.get(rowIndex);
                     int rowBottom = cy + row.height;
                     boolean rowVisible = rowBottom > clipTop && cy < clipBottom;
                     if (row.header()) {
                        if (rowVisible) {
                           CompactSurfaces.header(ctx, clipLeft, cy, clipRight - clipLeft, rowBottom - cy);
                           int headerTextY = UiSizing.alignTextY(cy, row.height, this.theme.fontHeight(UiTone.MUTED), this.theme.bodyTextNudge());
                           UiText.draw(
                              ctx, this.textRenderer, row.title, this.theme.fontFor(UiTone.MUTED), -4743522, clipLeft + this.rowTextInset(), headerTextY, false
                           );
                        }
                     } else if (row.type == AutismServerInfoOverlay.PluginRowType.PLUGIN) {
                        boolean standaloneCommandHint = this.isStandaloneCommandHintRow(row);
                        boolean selected = !standaloneCommandHint && this.isSamePlugin(row.plugin, this.selectedPlugin);
                        boolean hovered = rowVisible && uiMouseX >= clipLeft && uiMouseX < clipLeft + rowContentW && uiMouseY >= cy && uiMouseY < rowBottom;
                        if (rowVisible) {
                           CompactSurfaces.row(ctx, clipLeft, cy, rowContentW, rowBottom - cy, hovered, selected);
                        }

                        int rowTextY = UiSizing.alignTextY(cy, row.height, this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge());
                        if (rowVisible && !standaloneCommandHint) {
                           String arrow = selected ? "v" : ">";
                           int arrowColor = !hovered && !selected ? -4743522 : -791321;
                           UiText.draw(ctx, this.textRenderer, arrow, this.theme.fontFor(UiTone.BODY), arrowColor, clipLeft + 2, rowTextY, false);
                        }

                        boolean isAnticheat = row.plugin != null && ANTICHEATS.contains(row.plugin.toLowerCase(Locale.ROOT));
                        int nameColor = isAnticheat ? -43691 : this.getConfidenceColor(row.confidence, hovered);
                        String label = isAnticheat ? "! " + row.plugin : row.plugin;
                        int labelX = clipLeft + (standaloneCommandHint ? 5 : 13);
                        int labelMaxWidth = Math.max(40, rowContentW - 18);
                        if (row.countLabel != null && !row.countLabel.isBlank()) {
                           int cw = UiText.width(this.textRenderer, row.countLabel, this.theme.fontFor(UiTone.MUTED), -4743522);
                           int countX = clipLeft + rowContentW - cw - 4;
                           labelMaxWidth = Math.max(36, countX - labelX - 4);
                           int countTextY = UiSizing.alignTextY(cy, row.height, this.theme.fontHeight(UiTone.MUTED), this.theme.bodyTextNudge());
                           if (rowVisible) {
                              UiText.draw(ctx, this.textRenderer, row.countLabel, this.theme.fontFor(UiTone.MUTED), -4743522, countX, countTextY, false);
                           }
                        }

                        if (rowVisible) {
                           String displayLabel = UiText.trimToWidth(this.textRenderer, label, labelMaxWidth, this.theme.fontFor(UiTone.BODY), nameColor);
                           UiText.draw(ctx, this.textRenderer, displayLabel, this.theme.fontFor(UiTone.BODY), nameColor, labelX, rowTextY, false);
                           if (!standaloneCommandHint) {
                              String clickedPlugin = row.plugin;
                              int hitTop = Math.max(cy, clipTop);
                              int hitBottom = Math.min(rowBottom, clipBottom);
                              this.clickRegions
                                 .add(new AutismServerInfoOverlay.ClickRegion(clipLeft, hitTop, rowContentW, Math.max(1, hitBottom - hitTop), () -> {
                                    this.selectedPlugin = this.isSamePlugin(clickedPlugin, this.selectedPlugin) ? null : clickedPlugin;
                                    this.invalidatePluginRowView();
                                    this.saveState();
                                 }));
                           }
                        }
                     } else {
                        boolean commandRow = row.type == AutismServerInfoOverlay.PluginRowType.COMMAND;
                        boolean channelRow = row.type == AutismServerInfoOverlay.PluginRowType.CHANNEL
                           && row.actionCommand != null
                           && !row.actionCommand.isBlank();
                        boolean clickableRow = commandRow || channelRow;
                        boolean hoveredx = clickableRow
                           && rowVisible
                           && uiMouseX >= clipLeft + 9
                           && uiMouseX < clipLeft + rowContentW - 6
                           && uiMouseY >= cy
                           && uiMouseY < rowBottom;
                        if (rowVisible) {
                           int detailColor = this.pluginDetailColor(row);
                           CompactSurfaces.row(ctx, clipLeft + 5, cy, rowContentW - 9, rowBottom - cy, hoveredx, false);
                           CompactSurfaces.indicator(ctx, clipLeft + 5, cy, 1, rowBottom - cy, detailColor);
                           int textColor = hoveredx ? -791321 : detailColor;
                           String detailLabel = UiText.trimToWidth(
                              this.textRenderer, row.label, Math.max(20, rowContentW - 20), this.theme.fontFor(UiTone.MUTED), textColor
                           );
                           int detailTextY = UiSizing.alignTextY(cy, row.height, this.theme.fontHeight(UiTone.MUTED), this.theme.bodyTextNudge());
                           UiText.draw(ctx, this.textRenderer, detailLabel, this.theme.fontFor(UiTone.MUTED), textColor, clipLeft + 11, detailTextY, false);
                        }

                        if (rowVisible && clickableRow && row.actionCommand != null && !row.actionCommand.isBlank()) {
                           String actionValue = row.actionCommand;
                           int hitTop = Math.max(cy, clipTop);
                           int hitBottom = Math.min(rowBottom, clipBottom);
                           this.clickRegions
                              .add(
                                 new AutismServerInfoOverlay.ClickRegion(
                                    clipLeft + 9, hitTop, Math.max(1, rowContentW - 15), Math.max(1, hitBottom - hitTop), () -> {
                                       this.clearTextFieldFocus();
                                       this.clickRegions.clear();
                                       if (commandRow) {
                                          String fullCmd = actionValue.startsWith("/") ? actionValue : "/" + actionValue;
                                          AutismOverlayManager.get().setTemporarilyHidden(this, true);
                                          //? if >=1.21.9 {
                                          MC.setScreen(new ChatScreen(fullCmd, false));
                                          //?} else {
                                          /*MC.setScreen(new ChatScreen(fullCmd));*///?}
                                       } else {
                                          this.openPayloadSenderForChannel(actionValue);
                                       }
                                    }
                                 )
                              );
                        }
                     }

                     cy += row.height;
                     if (cy > clipBottom + this.pluginListRowStep()) {
                        break;
                     }
                  }
               } finally {
                  viewport.disableScissor(ctx);
               }

               CompactScrollbar.draw(ctx, scrollbarMetrics, scrollbarMetrics.contains(uiMouseX, uiMouseY), this.pluginScrollbarDragging);
            } finally {
               viewport.pop(ctx);
            }
         }
      }
   }

   private int pluginDetailColor(AutismServerInfoOverlay.PluginListRow row) {
      if (row == null) {
         return -4743522;
      } else {
         return switch (row.type) {
            case HEADER, PLUGIN -> -4743522;
            case COMMAND -> -1716016;
            case GUI -> AutismColors.packetYellow();
            case CHANNEL -> this.channelDetailColor(row.label == null ? null : row.label.replaceFirst("^ch\\s+", ""));
            case WHY -> this.getConfidenceColor(row.confidence, false);
         };
      }
   }

   private boolean isStandaloneCommandHintRow(AutismServerInfoOverlay.PluginListRow row) {
      return row != null
         && row.type == AutismServerInfoOverlay.PluginRowType.PLUGIN
         && row.evidence == AutismServerInfoOverlay.PluginEvidence.SCANNER_AUTOCOMPLETE
         && row.plugin != null
         && row.plugin.startsWith("/");
   }

   private void renderRadarViewport(GuiGraphicsExtractor ctx, DirectViewport viewport, float uiMouseX, float uiMouseY, float delta) {
      int x = Math.round(this.radarListSlot.x());
      int y = Math.round(this.radarListSlot.y());
      int rowW = Math.round(this.radarListSlot.width());
      int bodyBottom = this.panelY + this.panelH - 10;
      int rawViewH = Math.max(0, Math.min(Math.round(this.radarListSlot.height()), bodyBottom - y));
      int viewH = this.getPluginlistViewportHeight(rawViewH);
      if (rowW > 0 && viewH > 0) {
         int clipLeft = x + 1;
         int clipTop = y + 1;
         int clipRight = x + rowW - 1;
         int clipBottom = y + viewH - 1;
         int innerViewH = this.getPluginListInnerHeight(viewH);
         if (innerViewH > 0) {
            int rowContentW = Math.max(48, clipRight - clipLeft);
            List<AutismServerInfoOverlay.RadarListRow> rows = this.buildRadarRows(rowContentW);
            int estimatedContentHeight = this.estimateRadarContentHeight(rows);
            int maxScroll = Math.max(0, estimatedContentHeight - innerViewH);
            this.radarScrollState.setTarget(this.radarScrollState.targetOffset(), maxScroll);
            int visualScroll = this.radarScrollState.tick(delta, maxScroll);
            this.radarContentHeight = estimatedContentHeight;
            CompactScrollbar.Metrics scrollbarMetrics = this.getRadarScrollbarMetrics(x, y, rowW, viewH);
            if (scrollbarMetrics.hasScroll()) {
               rowContentW = Math.max(48, clipRight - clipLeft - 10);
               rows = this.buildRadarRows(rowContentW);
               estimatedContentHeight = this.estimateRadarContentHeight(rows);
               maxScroll = Math.max(0, estimatedContentHeight - innerViewH);
               this.radarScrollState.setTarget(this.radarScrollState.targetOffset(), maxScroll);
               visualScroll = this.radarScrollState.tick(0.0F, maxScroll);
               this.radarContentHeight = estimatedContentHeight;
               scrollbarMetrics = this.getRadarScrollbarMetrics(x, y, rowW, viewH);
            }

            boolean active = AutismOverlayManager.get().isFocusedOverlay(this) || AutismOverlayManager.get().isTopOverlay(this);
            viewport.push(ctx);

            try {
               CompactListRenderer.drawFrame(ctx, x, y, rowW, viewH, active);
               viewport.enableScissor(ctx, clipLeft, clipTop, clipRight, clipBottom);

               try {
                  int startIndex = this.firstVisibleRadarRowIndex(rows, visualScroll);
                  int cy = clipTop - visualScroll + this.radarRowOffset(rows, startIndex);

                  for (int rowIndex = startIndex; rowIndex < rows.size(); rowIndex++) {
                     AutismServerInfoOverlay.RadarListRow row = rows.get(rowIndex);
                     int rowBottom = cy + row.height;
                     boolean rowVisible = rowBottom > clipTop && cy < clipBottom;
                     if (rowVisible) {
                        this.renderRadarRow(ctx, row, clipLeft, cy, rowContentW, uiMouseX, uiMouseY, clipTop, clipBottom);
                     }

                     cy += row.height;
                     if (cy > clipBottom + this.pluginListRowStep()) {
                        break;
                     }
                  }
               } finally {
                  viewport.disableScissor(ctx);
               }

               CompactScrollbar.draw(ctx, scrollbarMetrics, scrollbarMetrics.contains(uiMouseX, uiMouseY), this.radarScrollbarDragging);
            } finally {
               viewport.pop(ctx);
            }
         }
      }
   }

   private void renderRadarRow(
      GuiGraphicsExtractor ctx, AutismServerInfoOverlay.RadarListRow row, int x, int y, int width, float uiMouseX, float uiMouseY, int clipTop, int clipBottom
   ) {
      if (row.type == AutismServerInfoOverlay.RadarRowType.HEADER) {
         CompactSurfaces.header(ctx, x, y, width, row.height);
         int textY = UiSizing.alignTextY(y, row.height, this.theme.fontHeight(UiTone.MUTED), this.theme.bodyTextNudge());
         UiText.draw(ctx, this.textRenderer, row.title, this.theme.fontFor(UiTone.MUTED), -4743522, x + this.rowTextInset(), textY, false);
      } else if (row.type == AutismServerInfoOverlay.RadarRowType.EMPTY) {
         CompactSurfaces.row(ctx, x, y, width, row.height, false, false);
         int textY = UiSizing.alignTextY(y, row.height, this.theme.fontHeight(UiTone.MUTED), this.theme.bodyTextNudge());
         String text = UiText.trimToWidth(this.textRenderer, row.title, Math.max(20, width - 10), this.theme.fontFor(UiTone.MUTED), -4743522);
         UiText.draw(ctx, this.textRenderer, text, this.theme.fontFor(UiTone.MUTED), -4743522, x + 5, textY, false);
      } else if (row.type == AutismServerInfoOverlay.RadarRowType.MATCH && row.match != null) {
         AutismDupeRadar.RadarMatch match = row.match;
         String key = this.radarMatchKey(match);
         boolean expanded = this.expandedRadarPlugins.contains(key);
         boolean hovered = uiMouseX >= x && uiMouseX < x + width && uiMouseY >= y && uiMouseY < y + row.height;
         CompactSurfaces.row(ctx, x, y, width, row.height, hovered, expanded);
         int textY = UiSizing.alignTextY(y, row.height, this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge());
         UiText.draw(ctx, this.textRenderer, expanded ? "v" : ">", this.theme.fontFor(UiTone.BODY), hovered ? -791321 : -4743522, x + 2, textY, false);
         int rowColor = this.radarMatchColor(match);
         String count = match.findings().size() + "x";
         int countW = UiText.width(this.textRenderer, count, this.theme.fontFor(UiTone.MUTED), -4743522) + 4;
         int countX = x + width - countW - 5;
         String label = match.displayLabel();
         int labelMax = Math.max(30, countX - (x + 13) - 4);
         UiText.draw(
            ctx,
            this.textRenderer,
            UiText.trimToWidth(this.textRenderer, label, labelMax, this.theme.fontFor(UiTone.BODY), rowColor),
            this.theme.fontFor(UiTone.BODY),
            rowColor,
            x + 13,
            textY,
            false
         );
         UiText.draw(ctx, this.textRenderer, count, this.theme.fontFor(UiTone.MUTED), -4743522, countX, textY, false);
         int hitTop = Math.max(y, clipTop);
         int hitBottom = Math.min(y + row.height, clipBottom);
         this.clickRegions.add(new AutismServerInfoOverlay.ClickRegion(x, hitTop, width, Math.max(1, hitBottom - hitTop), () -> {
            if (this.expandedRadarPlugins.contains(key)) {
               this.expandedRadarPlugins.remove(key);
            } else {
               this.expandedRadarPlugins.add(key);
            }

            this.requestUiRebuild();
         }));
      } else {
         if (row.type == AutismServerInfoOverlay.RadarRowType.FINDING && row.finding != null && row.match != null) {
            AutismDupeRadar.RadarFinding finding = row.finding;
            boolean hovered = uiMouseX >= x + 5 && uiMouseX < x + width - 5 && uiMouseY >= y && uiMouseY < y + row.height;
            CompactSurfaces.row(ctx, x + 5, y, width - 9, row.height, hovered, false);
            int color = this.radarMatchColor(row.match);
            CompactSurfaces.indicator(ctx, x + 5, y, 1, row.height, color);
            int textY = UiSizing.alignTextY(y, row.height, this.theme.fontHeight(UiTone.MUTED), this.theme.bodyTextNudge());
            int openW = 28;
            int openX = x + width - openW - 5;
            String title = finding.title();
            if (finding.summary() != null && !finding.summary().isBlank()) {
               title = title + " - " + finding.summary();
            }

            String label = UiText.trimToWidth(
               this.textRenderer, title, Math.max(20, openX - x - 20), this.theme.fontFor(UiTone.MUTED), hovered ? -791321 : color
            );
            UiText.draw(ctx, this.textRenderer, label, this.theme.fontFor(UiTone.MUTED), hovered ? -791321 : color, x + 11, textY, false);
            this.drawRadarChip(ctx, openX, y + 3, openW, row.height - 6, "Open", AutismColors.packetCyan());
            int hitTop = Math.max(y, clipTop);
            int hitBottom = Math.min(y + row.height, clipBottom);
            this.clickRegions
               .add(
                  new AutismServerInfoOverlay.ClickRegion(
                     openX, hitTop, openW, Math.max(1, hitBottom - hitTop), () -> AutismDupeRadar.open(finding.sourceUrl())
                  )
               );
         }
      }
   }

   private void drawRadarChip(GuiGraphicsExtractor ctx, int x, int y, int width, int height, String label, int color) {
      UiRenderer.rect(ctx, UiBounds.of(x, y, width, height), 1711276032);
      UiRenderer.outline(ctx, UiBounds.of(x, y, width, height), color);
      int textY = UiSizing.alignTextY(y, height, this.theme.fontHeight(UiTone.MUTED), this.theme.bodyTextNudge());
      String text = UiText.trimToWidth(this.textRenderer, label, Math.max(8, width - 4), this.theme.fontFor(UiTone.MUTED), color);
      int tx = x + Math.max(2, (width - UiText.width(this.textRenderer, text, this.theme.fontFor(UiTone.MUTED), color)) / 2);
      UiText.draw(ctx, this.textRenderer, text, this.theme.fontFor(UiTone.MUTED), color, tx, textY, false);
   }

   private int radarMatchColor(AutismDupeRadar.RadarMatch match) {
      if (match == null) {
         return -4743522;
      } else {
         return match.matchSource() != AutismDupeRadar.MatchSource.SERVER_DOMAIN && match.matchSource() != AutismDupeRadar.MatchSource.SERVER_IP
            ? AutismColors.packetOrange()
            : AutismColors.packetGreen();
      }
   }

   private CompactScrollbar.Metrics getRadarScrollbarMetrics(int x, int y, int width, int height) {
      int innerHeight = this.getPluginListInnerHeight(height);
      int maxScroll = Math.max(0, this.radarContentHeight - innerHeight);
      return CompactScrollbar.compute(
         this.radarContentHeight, Math.max(1, innerHeight), x + width - 5, y + 1, 3, Math.max(1, innerHeight), this.radarScrollState.tick(0.0F, maxScroll)
      );
   }

   private List<AutismServerInfoOverlay.RadarListRow> buildRadarRows(int rowContentW) {
      AutismDupeRadar.RadarState state = AutismDupeRadar.state();
      String query = this.radarSearchField.text() == null ? "" : this.radarSearchField.text().trim().toLowerCase(Locale.ROOT);
      List<AutismServerInfoOverlay.RadarListRow> rows = new ArrayList<>();
      if (state.checking() || state.authenticating()) {
         rows.add(AutismServerInfoOverlay.RadarListRow.empty(state.status(), 22));
      }

      List<AutismDupeRadar.RadarMatch> matches = new ArrayList<>();

      for (AutismDupeRadar.RadarMatch match : state.matches()) {
         if (match != null && this.radarMatchPassesFilter(match)) {
            String search = this.radarSearchBlob(match);
            if (query.isEmpty() || search.contains(query)) {
               matches.add(match);
            }
         }
      }

      if (matches.isEmpty()) {
         String emptyText = state.matches().isEmpty() ? "No server matches" : "No search matches.";
         rows.add(AutismServerInfoOverlay.RadarListRow.empty(emptyText, 22));
         return rows;
      } else {
         matches.sort(
            Comparator.comparingLong(AutismDupeRadar.RadarMatch::sortTimestampMs)
               .reversed()
               .thenComparing(AutismDupeRadar.RadarMatch::displayLabel, String.CASE_INSENSITIVE_ORDER)
         );

         for (AutismDupeRadar.RadarMatch matchx : matches) {
            rows.add(AutismServerInfoOverlay.RadarListRow.match(matchx, 22, this.radarSearchBlob(matchx)));
            if (this.expandedRadarPlugins.contains(this.radarMatchKey(matchx))) {
               for (AutismDupeRadar.RadarFinding finding : matchx.findings()) {
                  if (finding != null && (query.isEmpty() || this.radarFindingSearchBlob(matchx, finding).contains(query))) {
                     rows.add(AutismServerInfoOverlay.RadarListRow.finding(matchx, finding, 18, this.radarFindingSearchBlob(matchx, finding)));
                  }
               }
            }
         }

         return rows;
      }
   }

   private boolean radarMatchPassesFilter(AutismDupeRadar.RadarMatch match) {
      return match != null;
   }

   private String radarSearchBlob(AutismDupeRadar.RadarMatch match) {
      if (match == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder();
         sb.append(match.detectedPlugin())
            .append(' ')
            .append(match.providerPlugin())
            .append(' ')
            .append(match.matchConfidence())
            .append(' ')
            .append(match.matchSource().label())
            .append(' ')
            .append(match.matchedInput())
            .append(' ')
            .append(match.provider());

         for (AutismDupeRadar.RadarFinding finding : match.findings()) {
            sb.append(' ').append(this.radarFindingSearchBlob(match, finding));
         }

         return sb.toString().toLowerCase(Locale.ROOT);
      }
   }

   private String radarFindingSearchBlob(AutismDupeRadar.RadarMatch match, AutismDupeRadar.RadarFinding finding) {
      return finding == null ? "" : this.lowerSearch(match == null ? "" : match.detectedPlugin(), finding.title(), finding.summary(), finding.provider());
   }

   private String radarMatchKey(AutismDupeRadar.RadarMatch match) {
      return match == null
         ? ""
         : (match.matchSource().name() + "|" + match.detectedPlugin() + "|" + match.providerPlugin() + "|" + match.matchedInput() + "|" + match.sourceUrl())
            .toLowerCase(Locale.ROOT);
   }

   private int estimateRadarContentHeight(List<AutismServerInfoOverlay.RadarListRow> rows) {
      if (rows != null && !rows.isEmpty()) {
         int total = 0;

         for (AutismServerInfoOverlay.RadarListRow row : rows) {
            total += row == null ? 0 : row.height;
         }

         return total;
      } else {
         return 0;
      }
   }

   private int radarRowOffset(List<AutismServerInfoOverlay.RadarListRow> rows, int index) {
      if (rows != null && !rows.isEmpty() && index > 0) {
         int total = 0;

         for (int i = 0; i < Math.min(index, rows.size()); i++) {
            total += rows.get(i) == null ? 0 : rows.get(i).height;
         }

         return total;
      } else {
         return 0;
      }
   }

   private int firstVisibleRadarRowIndex(List<AutismServerInfoOverlay.RadarListRow> rows, int scrollOffset) {
      if (rows != null && !rows.isEmpty() && scrollOffset > 0) {
         int total = 0;

         for (int i = 0; i < rows.size(); i++) {
            AutismServerInfoOverlay.RadarListRow row = rows.get(i);
            total += row == null ? 0 : row.height;
            if (total > scrollOffset) {
               return i;
            }
         }

         return Math.max(0, rows.size() - 1);
      } else {
         return 0;
      }
   }

   private CompactScrollbar.Metrics getPluginScrollbarMetrics(int x, int y, int width, int height) {
      int innerHeight = this.getPluginListInnerHeight(height);
      int maxScroll = Math.max(0, this.pluginContentHeight - innerHeight);
      return CompactScrollbar.compute(
         this.pluginContentHeight,
         Math.max(1, innerHeight),
         x + width - 5,
         y + 1,
         3,
         Math.max(1, innerHeight),
         this.quantizeScrollOffset(this.pluginScrollState.tick(0.0F, maxScroll), this.pluginListRowStep(), maxScroll)
      );
   }

   private String detailCountLabel(List<String> commands, List<String> guis, List<String> channels) {
      int commandCount = commands == null ? 0 : commands.size();
      int guiCount = guis == null ? 0 : guis.size();
      int observedChannelCount = 0;
      int probableChannelCount = 0;
      boolean noChannelObserved = false;
      if (channels != null) {
         for (String channel : channels) {
            if (channel != null && !channel.isBlank()) {
               if (this.isNoChannelRow(channel)) {
                  noChannelObserved = true;
               } else if (this.isProbableChannelRow(channel)) {
                  probableChannelCount++;
               } else {
                  observedChannelCount++;
               }
            }
         }
      }

      int channelCount = observedChannelCount + probableChannelCount;
      String channelLabel;
      if (observedChannelCount > 0) {
         channelLabel = observedChannelCount + " ch";
      } else if (probableChannelCount > 0) {
         channelLabel = probableChannelCount + "? ch";
      } else if (noChannelObserved) {
         channelLabel = "no ch";
      } else {
         channelLabel = "0 ch";
      }

      List<String> parts = new ArrayList<>();
      if (commandCount > 0) {
         parts.add(commandCount + "c");
      }

      if (guiCount > 0) {
         parts.add(guiCount + "g");
      }

      if (channelCount > 0 || noChannelObserved) {
         parts.add(channelLabel);
      }

      return !parts.isEmpty() ? String.join("/", parts) : channelLabel;
   }

   private String detailCountLabel(List<String> commands, List<String> channels) {
      return this.detailCountLabel(commands, List.of(), channels);
   }

   private List<String> getPluginChannelDetailRows(String plugin) {
      List<String> observed = this.getPluginChannels(plugin);
      if (observed != null && !observed.isEmpty()) {
         return observed;
      } else {
         List<String> probableNamespaces = AutismPluginPayloadFingerprints.probableNamespacesForPlugin(plugin);
         if (!probableNamespaces.isEmpty()) {
            List<String> rows = new ArrayList<>();

            for (String namespace : probableNamespaces) {
               if (namespace != null && !namespace.isBlank()) {
                  rows.add("? " + namespace.trim().toLowerCase(Locale.ROOT) + ":*");
               }
            }

            rows = this.dedupeChannelRows(rows);
            if (!rows.isEmpty()) {
               return rows;
            }
         }

         return List.of("no channel");
      }
   }

   private int channelDetailColor(String detail) {
      if (detail == null) {
         return -4743522;
      } else {
         String lower = detail.toLowerCase(Locale.ROOT);
         if (this.isNoChannelRow(lower)) {
            return -7371387;
         } else if (this.isProbableChannelRow(lower)) {
            return -7371387;
         } else if (lower.contains("[registered")) {
            return AutismColors.packetGreen();
         } else {
            return lower.contains("[live") ? AutismColors.packetCyan() : AutismColors.packetCyan();
         }
      }
   }

   private String channelDetailLabel(String detail) {
      if (detail == null || detail.isBlank()) {
         return "--";
      } else if (this.isNoChannelRow(detail)) {
         return "no channel";
      } else {
         String base = this.channelDetailBase(detail);
         if (base.isBlank()) {
            return "--";
         } else {
            return this.isProbableChannelRow(detail) ? "? " + base : base;
         }
      }
   }

   private List<String> dedupeChannelRows(Collection<String> source) {
      if (source != null && !source.isEmpty()) {
         Map<String, String> unique = new LinkedHashMap<>();

         for (String row : source) {
            if (row != null && !row.isBlank()) {
               String clean = row.trim();
               String key = this.channelDetailKey(clean);
               if (!key.isBlank()) {
                  String existing = unique.get(key);
                  if (existing == null || this.channelRowRank(clean) > this.channelRowRank(existing)) {
                     unique.put(key, clean);
                  }
               }
            }
         }

         List<String> out = new ArrayList<>(unique.values());
         out.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(this.channelDetailLabel(a), this.channelDetailLabel(b)));
         return out;
      } else {
         return List.of();
      }
   }

   private String channelDetailKey(String detail) {
      return this.isNoChannelRow(detail) ? "no-channel" : this.channelDetailBase(detail).toLowerCase(Locale.ROOT);
   }

   private String channelDetailBase(String detail) {
      if (detail == null) {
         return "";
      } else {
         String text = detail.trim();
         if (text.regionMatches(true, 0, "ch ", 0, 3)) {
            text = text.substring(3).trim();
         }

         if (text.startsWith("?")) {
            text = text.substring(1).trim();
         }

         int bracket = text.indexOf(" [");
         if (bracket >= 0) {
            text = text.substring(0, bracket).trim();
         }

         return text;
      }
   }

   private String payloadChannelActionTarget(String detail) {
      if (detail != null && !this.isNoChannelRow(detail)) {
         String base = this.channelDetailBase(detail);
         String channel = this.bestGuessPayloadChannel(base);
         return channel.isBlank() ? null : channel;
      } else {
         return null;
      }
   }

   private String bestGuessPayloadChannel(String rawChannel) {
      if (rawChannel == null) {
         return "";
      } else {
         String channel = rawChannel.trim();
         if (channel.isBlank()) {
            return "";
         } else {
            String legacy = channel.replace(" ", "");
            if ("REGISTER".equalsIgnoreCase(legacy) || "minecraft:register".equalsIgnoreCase(legacy)) {
               return "minecraft:register";
            } else if ("UNREGISTER".equalsIgnoreCase(legacy) || "minecraft:unregister".equalsIgnoreCase(legacy)) {
               return "minecraft:unregister";
            } else if (!"BungeeCord".equalsIgnoreCase(legacy) && !"bungeecord".equalsIgnoreCase(legacy)) {
               int wildcard = channel.indexOf(42);
               if (wildcard >= 0) {
                  channel = channel.substring(0, wildcard);

                  while (channel.endsWith(":") || channel.endsWith("/") || channel.endsWith(".")) {
                     channel = channel.substring(0, channel.length() - 1);
                  }

                  if (channel.isBlank()) {
                     return "";
                  }

                  if (!channel.contains(":")) {
                     channel = channel + ":main";
                  } else if (channel.endsWith(":")) {
                     channel = channel + "main";
                  }
               }

               channel = channel.toLowerCase(Locale.ROOT).replace('\\', '/').replaceAll("[^a-z0-9_./:-]", "_");
               int colon = channel.indexOf(58);
               if (colon < 0) {
                  channel = channel + ":main";
               } else if (colon == 0) {
                  channel = "minecraft" + channel;
               } else if (colon == channel.length() - 1) {
                  channel = channel + "main";
               }

               try {
                  AutismPayloadSupport.parseChannel(channel);
                  return channel;
               } catch (IllegalArgumentException var7) {
                  return "";
               }
            } else {
               return "bungeecord:main";
            }
         }
      }
   }

   private void openPayloadSenderForChannel(String channel) {
      String target = channel == null ? "" : channel.trim();
      if (!target.isBlank()) {
         ActionEditorOverlay existing = ActionEditorOverlay.getSharedOverlayIfExists();
         if (existing == null || !existing.updateOpenPayloadChannel(target)) {
            PayloadAction action = new PayloadAction();
            action.channel = target;
            action.payloadDirection = "C2S";
            action.sourceDirection = "C2S";
            action.payloadPhase = "PLAY";
            action.sourceProtocol = "play";
            action.payloadProvenance = "serverInfoChannel";
            ActionEditorOverlay editor = ActionEditorOverlay.getSharedOverlay();
            editor.openStandalonePayloadEditor(action);
            AutismOverlayManager.get().bringToFront(editor);
         }
      }
   }

   private boolean isNoChannelRow(String detail) {
      if (detail == null) {
         return false;
      } else {
         String lower = detail.trim().toLowerCase(Locale.ROOT);
         return lower.equals("no channel") || lower.startsWith("no client payload");
      }
   }

   private boolean isProbableChannelRow(String detail) {
      if (detail == null) {
         return false;
      } else {
         String lower = detail.trim().toLowerCase(Locale.ROOT);
         return lower.startsWith("? ") || lower.contains("not observed") || lower.contains("[probable");
      }
   }

   private int channelRowRank(String detail) {
      if (detail == null) {
         return 0;
      } else {
         String lower = detail.toLowerCase(Locale.ROOT);
         if (lower.contains("[live")) {
            return 4;
         } else if (lower.contains("[registered")) {
            return 3;
         } else if (lower.contains("[embedded")) {
            return 2;
         } else if (this.isProbableChannelRow(lower)) {
            return 1;
         } else {
            return this.isNoChannelRow(lower) ? 0 : 3;
         }
      }
   }

   private String getLiveBrand() {
      if (MC.getConnection() == null) {
         return "--";
      } else {
         String brand = MC.getConnection().serverBrand();
         return brand != null && !brand.isBlank() ? brand : "--";
      }
   }

   private boolean isOverHeaderUi(float uiMouseX, float uiMouseY) {
      return uiMouseX >= this.panelX && uiMouseX < this.panelX + this.panelW && uiMouseY >= this.panelY && uiMouseY < this.panelY + this.theme.headerHeight();
   }

   private boolean isOverCloseButton(float uiMouseX, float uiMouseY) {
      return OverlayTopBar.isOverClose(
         UiBounds.of(this.panelX, this.panelY, this.panelW, Math.max(this.theme.headerHeight(), this.panelH)), this.theme.headerHeight(), uiMouseX, uiMouseY
      );
   }

   private int panelPadding() {
      return 5;
   }

   private int contentGap() {
      return 3;
   }

   private int searchFieldHeight() {
      return 15;
   }

   private int actionButtonHeight() {
      return 15;
   }

   private int buttonRowGap() {
      return 2;
   }

   private int infoLabelWidth() {
      return 82;
   }

   private int pluginSetupLabelWidth() {
      return 46;
   }

   private int pluginScanLabelWidth() {
      return 40;
   }

   private int pluginSearchWidth() {
      return 128;
   }

   private int pluginSearchReserveWidth() {
      return 28;
   }

   private float pluginViewportMinHeight() {
      return 66.0F;
   }

   private int sharedPanelWidth() {
      return 204;
   }

   private int pluginSetupWidth() {
      return this.sharedPanelWidth();
   }

   private int pluginScanningWidth() {
      return this.sharedPanelWidth();
   }

   private int infoMinWidth() {
      return this.sharedPanelWidth();
   }

   private int infoMinHeight() {
      return 246;
   }

   private int pluginResultsMinWidth() {
      return this.sharedPanelWidth();
   }

   private int pluginResultsMinHeightPreset() {
      return 266;
   }

   private int pluginSetupHeight() {
      return 120;
   }

   private int pluginScanningHeight() {
      return 92;
   }

   private int viewportHeightMargin() {
      return 24;
   }

   private int pluginHeaderHeight() {
      return this.pluginListRowStep();
   }

   private int pluginRowHeight() {
      return 15;
   }

   private int pluginDetailRowHeight() {
      return this.pluginListRowStep();
   }

   private int pluginDetailPadding() {
      return 0;
   }

   private int headerControlSize() {
      return 12;
   }

   private int headerArrowWidth() {
      return 10;
   }

   private int headerArrowGap() {
      return 3;
   }

   private int rowTextInset() {
      return 3;
   }

   private int pluginListRowStep() {
      return this.pluginRowHeight();
   }

   private int getPluginlistViewportHeight(int rawHeight) {
      return rawHeight <= 0 ? 0 : Math.min(rawHeight, this.getPluginListInnerHeight(rawHeight) + 2);
   }

   private int getPluginListInnerHeight(int viewHeight) {
      return Math.max(0, this.alignViewportHeight(Math.max(0, viewHeight - 2), this.pluginListRowStep()));
   }

   private AutismWindowLayout clampToViewport(AutismWindowLayout bounds) {
      DirectViewport viewport = this.surface.viewport();
      int viewportWidth = Math.round(viewport.uiWidth());
      int viewportHeight = Math.round(viewport.uiHeight());
      int margin = 4;
      int availableWidth = Math.max(1, viewportWidth - margin * 2);
      int availableHeight = Math.max(this.theme.headerHeight(), viewportHeight - margin * 2);
      int minWidth = Math.min(this.getMinWidth(), availableWidth);
      int width = Math.max(minWidth, Math.min(bounds.width, availableWidth));
      int minHeight = bounds.collapsed ? this.theme.headerHeight() : Math.min(this.getMinHeight(), availableHeight);
      int height = Math.max(minHeight, Math.min(bounds.height, availableHeight));
      int renderedHeight = bounds.collapsed ? this.theme.headerHeight() : height;
      int x = Math.max(margin, Math.min(bounds.x, Math.max(margin, viewportWidth - margin - width)));
      int y = Math.max(margin, Math.min(bounds.y, Math.max(margin, viewportHeight - margin - renderedHeight)));
      return new AutismWindowLayout(x, y, width, height, bounds.visible, bounds.collapsed);
   }

   private String getSoftwareGuess(ServerData entry) {
      LinkedHashSet<String> guesses = new LinkedHashSet<>();
      String brand = this.getLiveBrand().toLowerCase(Locale.ROOT);
      if (brand.contains("purpur")) {
         guesses.add("Purpur");
      } else if (brand.contains("pufferfish")) {
         guesses.add("Pufferfish");
      } else if (brand.contains("folia")) {
         guesses.add("Folia");
      } else if (brand.contains("paper")) {
         guesses.add("Paper");
      } else if (brand.contains("spigot")) {
         guesses.add("Spigot");
      } else if (brand.contains("craftbukkit") || brand.contains("bukkit")) {
         guesses.add("Bukkit");
      } else if (brand.contains("waterfall")) {
         guesses.add("Waterfall");
      } else if (brand.contains("velocity")) {
         guesses.add("Velocity");
      } else if (brand.contains("bungeecord") || brand.contains("bungee")) {
         guesses.add("Bungee");
      }

      if (this.detectedPlugins
         .stream()
         .anyMatch(plugin -> "viaversion".equalsIgnoreCase(plugin) || "viabackwards".equalsIgnoreCase(plugin) || "viarewind".equalsIgnoreCase(plugin))) {
         guesses.add("ViaVersion");
      }

      if (this.detectedPlugins.stream().anyMatch(plugin -> "geysermc".equalsIgnoreCase(plugin) || "floodgate".equalsIgnoreCase(plugin))) {
         guesses.add("Bedrock Bridge");
      }

      return guesses.isEmpty() ? "--" : String.join(" + ", guesses);
   }

   private String getVersionNote(ServerData entry) {
      String brand = this.getLiveBrand();
      String reportedVersion = this.getReportedVersion(entry);
      String brandLower = brand.toLowerCase(Locale.ROOT);
      String versionLower = reportedVersion.toLowerCase(Locale.ROOT);
      if (entry != null && entry.protocol == 0) {
         return "Ping Spoof";
      } else if ("--".equals(brand)
         || !versionLower.contains("paper")
            && !versionLower.contains("spigot")
            && !versionLower.contains("purpur")
            && !versionLower.contains("velocity")
            && !versionLower.contains("bungee")
         || (!brandLower.contains("paper") || versionLower.contains("paper"))
            && (!brandLower.contains("spigot") || versionLower.contains("spigot"))
            && (!brandLower.contains("purpur") || versionLower.contains("purpur"))
            && (!brandLower.contains("velocity") || versionLower.contains("velocity"))
            && (!brandLower.contains("bungee") || versionLower.contains("bungee"))) {
         return this.detectedPlugins
               .stream()
               .anyMatch(
                  plugin -> "viaversion".equalsIgnoreCase(plugin)
                     || "viabackwards".equalsIgnoreCase(plugin)
                     || "viarewind".equalsIgnoreCase(plugin)
                     || "protocolsupport".equalsIgnoreCase(plugin)
               )
            ? "Protocol Bridge"
            : "--";
      } else {
         return "Brand Mismatch";
      }
   }

   private String getDisplayedServerAddress() {
      ServerData entry = MC.getCurrentServer();
      if (entry != null && entry.ip != null && !entry.ip.isBlank()) {
         return entry.ip.trim();
      } else {
         if (MC.getConnection() != null && MC.getConnection().getConnection() != null) {
            SocketAddress address = MC.getConnection().getConnection().getRemoteAddress();
            if (address instanceof InetSocketAddress inet) {
               String host = inet.getHostString();
               if ((host == null || host.isBlank()) && inet.getAddress() != null) {
                  host = inet.getAddress().getHostAddress();
               }

               if (host != null && !host.isBlank()) {
                  return host + ":" + inet.getPort();
               }
            } else if (address != null) {
               String raw = address.toString();
               if (raw != null && !raw.isBlank()) {
                  return raw.replaceFirst("^/", "").trim();
               }
            }
         }

         return "--";
      }
   }

   private String extractLookupHost(String rawAddress) {
      if (rawAddress != null && !rawAddress.isBlank() && !"--".equals(rawAddress)) {
         String trimmed = rawAddress.trim();
         if (trimmed.startsWith("[") && trimmed.contains("]")) {
            return trimmed.substring(1, trimmed.indexOf(93));
         } else {
            int colonCount = 0;

            for (int i = 0; i < trimmed.length(); i++) {
               if (trimmed.charAt(i) == ':') {
                  colonCount++;
               }
            }

            return colonCount == 1 && trimmed.contains(":") ? trimmed.substring(0, trimmed.lastIndexOf(58)) : trimmed;
         }
      } else {
         return "";
      }
   }

   private String getLiveSocketIp() {
      if (MC.getConnection() != null && MC.getConnection().getConnection() != null) {
         if (MC.getConnection().getConnection().getRemoteAddress() instanceof InetSocketAddress inet && inet.getAddress() != null) {
            String hostAddress = inet.getAddress().getHostAddress();
            return hostAddress == null ? "" : hostAddress.trim();
         } else {
            return "";
         }
      } else {
         return "";
      }
   }

   private Integer extractPort(String rawAddress) {
      if (rawAddress != null && !rawAddress.isBlank() && !"--".equals(rawAddress)) {
         String trimmed = rawAddress.trim();
         if (trimmed.startsWith("[") && trimmed.contains("]")) {
            int closing = trimmed.indexOf(93);
            if (closing >= 0 && closing + 1 < trimmed.length() && trimmed.charAt(closing + 1) == ':') {
               try {
                  return Integer.parseInt(trimmed.substring(closing + 2));
               } catch (NumberFormatException var6) {
                  return null;
               }
            } else {
               return null;
            }
         } else {
            int firstColon = trimmed.indexOf(58);
            int lastColon = trimmed.lastIndexOf(58);
            if (firstColon >= 0 && firstColon == lastColon) {
               try {
                  return Integer.parseInt(trimmed.substring(lastColon + 1));
               } catch (NumberFormatException var7) {
                  return null;
               }
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private String appendPortIfPresent(String host, Integer port) {
      if (host == null || host.isBlank() || port == null) {
         return host;
      } else {
         return host.indexOf(58) >= 0 && !host.startsWith("[") && !host.endsWith("]") ? "[" + host + "]:" + port : host + ":" + port;
      }
   }

   private Object invokeFirstNoArg(Object target, String... names) {
      if (target != null && names != null) {
         for (String name : names) {
            if (name != null && !name.isBlank()) {
               try {
                  Method method = target.getClass().getMethod(name);
                  method.setAccessible(true);
                  return method.invoke(target);
               } catch (RuntimeException | ReflectiveOperationException var8) {
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private String stringValue(Object target, String... names) {
      Object value = this.invokeFirstNoArg(target, names);
      if (value == null) {
         return "";
      } else {
         return value instanceof Component component ? component.getString() : String.valueOf(value);
      }
   }

   private int intValue(Object target, int fallback, String... names) {
      Object value = this.invokeFirstNoArg(target, names);
      if (value instanceof Number number) {
         return number.intValue();
      } else {
         if (value != null) {
            try {
               return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException var6) {
            }
         }

         return fallback;
      }
   }

   private String componentText(Object value) {
      if (value instanceof Component component) {
         return component.getString();
      } else {
         return value == null ? "" : String.valueOf(value);
      }
   }

   private void copyClipboardValue(String value, String successMessage, String unavailableMessage) {
      String trimmed = value == null ? "" : value.trim();
      if (!trimmed.isEmpty() && !"--".equals(trimmed) && !"Resolving...".equalsIgnoreCase(trimmed) && !"Failed".equalsIgnoreCase(trimmed)) {
         MC.keyboardHandler.setClipboard(trimmed);
         AutismNotifications.copied(successMessage);
      } else {
         AutismNotifications.error(unavailableMessage);
      }
   }

   private void copyResolvedServerIp() {
      String displayedAddress = this.getDisplayedServerAddress();
      String realIp = this.getDisplayedRealIp(displayedAddress);
      String resolvedWithPort = this.appendPortIfPresent(realIp, this.extractPort(displayedAddress));
      this.copyClipboardValue(resolvedWithPort, "Real IP copied.", "Real IP unavailable.");
   }

   private void ensureResolvedIpLookup(String rawAddress) {
      String host = this.extractLookupHost(rawAddress);
      if (!host.isEmpty()) {
         if (!host.equals(this.lastResolvedAddress)) {
            this.resolvedIp = null;
            this.lastResolvedAddress = host;
            this.resolvingIp = false;
         }

         if (this.resolvedIp == null && !this.resolvingIp) {
            this.resolvingIp = true;
            Thread t = new Thread(() -> {
               try {
                  this.resolvedIp = InetAddress.getByName(host).getHostAddress();
               } catch (Exception var6) {
                  this.resolvedIp = "Failed";
               } finally {
                  this.resolvingIp = false;
               }
            }, "Autism-DNS");
            t.setDaemon(true);
            t.start();
         }
      }
   }

   private String getDisplayedRealIp(String rawAddress) {
      this.ensureResolvedIpLookup(rawAddress);
      String socketIp = this.getLiveSocketIp();
      if (!socketIp.isBlank()) {
         return socketIp;
      } else if (this.resolvedIp != null) {
         return this.resolvedIp;
      } else {
         return this.resolvingIp ? "Resolving..." : "--";
      }
   }

   private List<String> getDetectedAnticheats() {
      return this.detectedPlugins
         .stream()
         .filter(Objects::nonNull)
         .filter(p -> ANTICHEATS.contains(p.toLowerCase(Locale.ROOT)))
         .sorted(String.CASE_INSENSITIVE_ORDER)
         .collect(Collectors.toList());
   }

   private String getCurrentWorldName() {
      if (MC.level == null) {
         return "--";
      } else {
         Identifier worldId = MC.level.dimension().identifier();
         if (worldId == null) {
            return "--";
         } else {
            String namespace = worldId.getNamespace();
            String path = worldId.getPath();
            if ("minecraft".equals(namespace)) {
               return switch (path) {
                  case "overworld" -> "Overworld";
                  case "the_nether" -> "The Nether";
                  case "the_end" -> "The End";
                  default -> path;
               };
            } else {
               return namespace + ":" + path;
            }
         }
      }
   }

   private List<AutismServerInfoOverlay.PluginListRow> buildPluginRows(List<AutismServerInfoOverlay.PluginDetail> filteredPlugins, String query) {
      String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
      boolean searchActive = !normalizedQuery.isBlank();
      Map<String, List<AutismServerInfoOverlay.PluginListRow>> grouped = new LinkedHashMap<>();

      for (String title : List.of(
         "Exact Plugins",
         "Strong Matches",
         "Command Tree",
         "Command Root Hints",
         "Plugin Command Hints",
         "Version Hints",
         "Payload Channels",
         "Command Hints",
         "Other"
      )) {
         grouped.put(title, new ArrayList<>());
      }

      for (AutismServerInfoOverlay.PluginDetail detail : filteredPlugins) {
         if (detail != null && !detail.displayName.isBlank()) {
            String plugin = detail.displayName;
            String groupTitle = this.pluginGroupTitle(detail.resultKind, detail.confidence, detail.evidence);
            boolean standaloneCommandHint = this.isStandaloneAutocompleteCommand(detail);
            grouped.computeIfAbsent(groupTitle, unused -> new ArrayList<>())
               .add(
                  AutismServerInfoOverlay.PluginListRow.plugin(
                     plugin,
                     detail.resultKind,
                     detail.evidence,
                     detail.confidence,
                     this.pluginRowHeight(),
                     standaloneCommandHint ? "" : detail.countLabel,
                     detail.searchText
                  )
               );
            boolean selected = this.isSamePlugin(plugin, this.selectedPlugin);
            boolean showDetails = !standaloneCommandHint && (selected || searchActive);
            if (showDetails) {
               for (String command : detail.commands) {
                  if (!searchActive || this.pluginDetailMatchesQuery(normalizedQuery, plugin, "/" + command, detail.sourceLabel)) {
                     grouped.get(groupTitle)
                        .add(
                           AutismServerInfoOverlay.PluginListRow.detail(
                              AutismServerInfoOverlay.PluginRowType.COMMAND,
                              plugin,
                              "cmd /" + command,
                              "/" + command,
                              detail.evidence,
                              detail.confidence,
                              this.pluginDetailRowHeight(),
                              this.lowerSearch(plugin, command, detail.sourceLabel)
                           )
                        );
                  }
               }

               for (String gui : detail.guis) {
                  if (!searchActive || this.pluginDetailMatchesQuery(normalizedQuery, plugin, gui, detail.sourceLabel)) {
                     grouped.get(groupTitle)
                        .add(
                           AutismServerInfoOverlay.PluginListRow.detail(
                              AutismServerInfoOverlay.PluginRowType.GUI,
                              plugin,
                              "gui " + gui,
                              null,
                              detail.evidence,
                              detail.confidence,
                              this.pluginDetailRowHeight(),
                              this.lowerSearch(plugin, gui, detail.sourceLabel)
                           )
                        );
                  }
               }

               for (String channel : detail.channels.isEmpty() ? this.getPluginChannelDetailRows(plugin) : detail.channels) {
                  String label = this.channelDetailLabel(channel);
                  if (!searchActive || this.pluginDetailMatchesQuery(normalizedQuery, plugin, label, detail.sourceLabel)) {
                     grouped.get(groupTitle)
                        .add(
                           AutismServerInfoOverlay.PluginListRow.detail(
                              AutismServerInfoOverlay.PluginRowType.CHANNEL,
                              plugin,
                              "ch " + label,
                              this.payloadChannelActionTarget(channel),
                              detail.evidence,
                              detail.confidence,
                              this.pluginDetailRowHeight(),
                              this.lowerSearch(plugin, label, detail.sourceLabel)
                           )
                        );
                  }
               }
            }
         }
      }

      List<AutismServerInfoOverlay.PluginListRow> rows = new ArrayList<>();

      for (Entry<String, List<AutismServerInfoOverlay.PluginListRow>> group : grouped.entrySet()) {
         if (!group.getValue().isEmpty()) {
            group.getValue().sort((a, b) -> {
               int byPlugin = String.CASE_INSENSITIVE_ORDER.compare(a.plugin == null ? "" : a.plugin, b.plugin == null ? "" : b.plugin);
               return byPlugin != 0 ? byPlugin : Integer.compare(this.pluginRowTypeOrder(a.type), this.pluginRowTypeOrder(b.type));
            });
            rows.add(AutismServerInfoOverlay.PluginListRow.header(group.getKey(), this.pluginHeaderHeight()));
            rows.addAll(group.getValue());
         }
      }

      return rows;
   }

   private int pluginRowTypeOrder(AutismServerInfoOverlay.PluginRowType type) {
      return switch (type == null ? AutismServerInfoOverlay.PluginRowType.WHY : type) {
         case HEADER -> 0;
         case PLUGIN -> 1;
         case COMMAND -> 2;
         case GUI -> 3;
         case CHANNEL -> 4;
         case WHY -> 5;
      };
   }

   private boolean isStandaloneAutocompleteCommand(AutismServerInfoOverlay.PluginDetail detail) {
      if (detail != null && detail.evidence == AutismServerInfoOverlay.PluginEvidence.SCANNER_AUTOCOMPLETE && detail.commands.size() == 1) {
         String command = detail.commands.get(0);
         return command != null && detail.displayName.equalsIgnoreCase("/" + command);
      } else {
         return false;
      }
   }

   private boolean pluginDetailMatchesQuery(String query, String plugin, String detail, String sourceLabel) {
      return query != null && !query.isBlank() ? this.lowerSearch(plugin, detail, sourceLabel).contains(query) : true;
   }

   private String pluginGroupTitle(
      AutismServerInfoOverlay.PluginResultKind resultKind, AutismServerInfoOverlay.PluginConfidence confidence, AutismServerInfoOverlay.PluginEvidence evidence
   ) {
      if (evidence == AutismServerInfoOverlay.PluginEvidence.SCANNER_AUTOCOMPLETE) {
         return "Command Hints";
      } else if (evidence == AutismServerInfoOverlay.PluginEvidence.COMMAND_TREE) {
         return "Command Tree";
      } else if (evidence == AutismServerInfoOverlay.PluginEvidence.ROOT_HINT) {
         return "Command Root Hints";
      } else if (evidence == AutismServerInfoOverlay.PluginEvidence.PLUGIN_LIST) {
         return "Plugin Command Hints";
      } else if (evidence == AutismServerInfoOverlay.PluginEvidence.VERSION_HINT) {
         return "Version Hints";
      } else if (evidence == AutismServerInfoOverlay.PluginEvidence.PAYLOAD_CHANNEL) {
         return "Payload Channels";
      } else if (resultKind != AutismServerInfoOverlay.PluginResultKind.FEATURE && !this.isWeakCommandEvidence(evidence)) {
         return switch (confidence == null ? AutismServerInfoOverlay.PluginConfidence.UNKNOWN : confidence) {
            case EXACT -> "Exact Plugins";
            case STRONG -> "Strong Matches";
            case FEATURE -> "Other";
            case UNKNOWN -> "Other";
         };
      } else {
         return "Command Hints";
      }
   }

   private String sourceLabelFor(
      AutismServerInfoOverlay.PluginResultKind resultKind, AutismServerInfoOverlay.PluginEvidence evidence, AutismServerInfoOverlay.PluginConfidence confidence
   ) {
      if (resultKind == AutismServerInfoOverlay.PluginResultKind.FEATURE) {
         return "feature command";
      } else {
         return switch (evidence == null ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : evidence) {
            case PAYLOAD_CHANNEL -> "payload channel";
            case COMMAND_GUI -> "command and gui";
            case COMMAND_TREE -> "command tree";
            case USER_AUTOCOMPLETE -> "user autocomplete";
            case SCANNER_AUTOCOMPLETE -> "command hint";
            case NAMESPACE -> "namespace";
            case ROOT_HINT -> "root hint";
            case HELP_HINT -> "help hint";
            case PLUGIN_LIST -> "plugin command hint";
            case VERSION_HINT -> "version hint";
            case FEATURE -> "feature command";
            case UNKNOWN -> confidence == AutismServerInfoOverlay.PluginConfidence.UNKNOWN ? "unknown" : "weak hint";
         };
      }
   }

   private List<String> copyPluginGroupOrder() {
      return List.of("Command Tree", "Command Root Hint", "Plugin Command Hint", "Version Command Hint", "Payload Channel");
   }

   private String copyPluginGroupTitle(AutismServerInfoOverlay.PluginDetail detail) {
      if (detail == null) {
         return "Other";
      } else {
         AutismServerInfoOverlay.PluginEvidence evidence = detail.copyEvidence == null ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : detail.copyEvidence;

         return switch (evidence) {
            case PAYLOAD_CHANNEL -> "Payload Channel";
            case COMMAND_GUI, USER_AUTOCOMPLETE, FEATURE -> "Other";
            case COMMAND_TREE -> "Command Tree";
            case SCANNER_AUTOCOMPLETE, HELP_HINT, UNKNOWN -> "Other";
            case NAMESPACE, ROOT_HINT -> "Command Root Hint";
            case PLUGIN_LIST -> "Plugin Command Hint";
            case VERSION_HINT -> "Version Command Hint";
         };
      }
   }

   private int copyPluginGroupRank(String title) {
      List<String> order = this.copyPluginGroupOrder();
      int index = order.indexOf(title);
      return index < 0 ? order.size() : index;
   }

   private String pluginSearchBlob(
      String plugin, List<String> commands, List<String> guis, List<String> channels, String sourceLabel, AutismServerInfoOverlay.PluginConfidence confidence
   ) {
      StringBuilder sb = new StringBuilder();
      sb.append(plugin == null ? "" : plugin).append(' ').append(this.confidenceLabel(confidence)).append(' ').append(sourceLabel == null ? "" : sourceLabel);

      for (String command : commands) {
         sb.append(' ').append(command);
      }

      for (String gui : guis) {
         sb.append(' ').append(gui);
      }

      for (String channel : channels) {
         sb.append(' ').append(this.channelDetailLabel(channel));
      }

      return sb.toString().toLowerCase(Locale.ROOT);
   }

   private String lowerSearch(String... parts) {
      StringBuilder sb = new StringBuilder();
      if (parts != null) {
         for (String part : parts) {
            if (part != null && !part.isBlank()) {
               if (!sb.isEmpty()) {
                  sb.append(' ');
               }

               sb.append(part);
            }
         }
      }

      return sb.toString().toLowerCase(Locale.ROOT);
   }

   private List<String> dedupePluginNames(Collection<String> plugins) {
      if (plugins != null && !plugins.isEmpty()) {
         Map<String, String> unique = new LinkedHashMap<>();

         for (String plugin : plugins) {
            if (plugin != null && !plugin.isBlank()) {
               String clean = plugin.trim();
               String key = this.normalizePluginKey(clean);
               if (!key.isBlank()) {
                  String existing = unique.get(key);
                  if (existing == null || this.isMoreReadablePluginName(clean, existing)) {
                     unique.put(key, clean);
                  }
               }
            }
         }

         List<String> out = new ArrayList<>(unique.values());
         out.sort(String.CASE_INSENSITIVE_ORDER);
         return out;
      } else {
         return List.of();
      }
   }

   private boolean isSamePlugin(String a, String b) {
      return a != null && b != null ? this.normalizePluginKey(a).equals(this.normalizePluginKey(b)) : false;
   }

   private boolean hasDetectedPlugin(String plugin) {
      if (plugin != null && !plugin.isBlank()) {
         String key = this.normalizePluginKey(plugin);

         for (String detected : this.detectedPlugins) {
            if (detected != null && this.normalizePluginKey(detected).equals(key)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private Collection<AutismServerInfoOverlay.PluginDetail> getCachedPluginDetails() {
      if (this.cachedPluginDetailsRevision == this.pluginDataRevision) {
         return this.cachedPluginDetails.values();
      } else {
         Map<String, AutismServerInfoOverlay.PluginDetailBuilder> builders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

         for (String plugin : this.dedupePluginNames(this.detectedPlugins)) {
            String key = this.normalizePluginKey(plugin);
            if (!key.isBlank()) {
               AutismServerInfoOverlay.PluginDetailBuilder builder = builders.computeIfAbsent(key, unused -> new AutismServerInfoOverlay.PluginDetailBuilder());
               if (builder.displayName == null || builder.displayName.isBlank() || this.isMoreReadablePluginName(plugin, builder.displayName)) {
                  builder.displayName = plugin.trim();
               }
            }
         }

         this.mergePluginDetailCommands(builders, this.pluginCommands);
         this.mergePluginDetailCopyCommands(builders, this.pluginCopyCommands);
         this.mergePluginDetailGuis(builders, this.pluginGuis);
         this.mergePluginDetailChannels(builders, this.pluginChannels);
         Map<String, AutismServerInfoOverlay.PluginDetail> details = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

         for (Entry<String, AutismServerInfoOverlay.PluginDetailBuilder> entry : builders.entrySet()) {
            String key = entry.getKey();
            AutismServerInfoOverlay.PluginDetailBuilder builder = entry.getValue();
            if (builder != null) {
               String displayName = builder.displayName != null && !builder.displayName.isBlank() ? builder.displayName.trim() : key;
               List<String> commands = List.copyOf(builder.commands);
               List<String> copyCommands = builder.copyCommands.isEmpty() ? commands : List.copyOf(builder.copyCommands);
               List<String> guis = List.copyOf(builder.guis);
               List<String> channels = this.dedupeChannelRows(builder.channels);
               AutismServerInfoOverlay.PluginEvidence evidence = this.pluginEvidence
                  .getOrDefault(key, commands.isEmpty() ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : AutismServerInfoOverlay.PluginEvidence.ROOT_HINT);
               AutismServerInfoOverlay.PluginEvidence copyEvidence = this.pluginCopyEvidence
                  .getOrDefault(key, this.fallbackCopyEvidence(displayName, evidence));
               AutismServerInfoOverlay.PluginConfidence confidence = this.pluginConfidence.getOrDefault(key, this.confidenceForEvidence(evidence));
               AutismServerInfoOverlay.PluginResultKind resultKind = this.resultKindForStoredPlugin(displayName, evidence, confidence);
               String sourceLabel = this.sourceLabelFor(resultKind, evidence, confidence);
               String countLabel = this.detailCountLabel(commands, guis, channels);
               String searchText = this.pluginSearchBlob(displayName, commands, guis, channels, sourceLabel, confidence);
               details.put(
                  key,
                  new AutismServerInfoOverlay.PluginDetail(
                     displayName,
                     key,
                     commands,
                     guis,
                     channels,
                     resultKind,
                     evidence,
                     copyEvidence,
                     confidence,
                     sourceLabel,
                     countLabel,
                     copyCommands,
                     searchText
                  )
               );
            }
         }

         this.cachedPluginDetails = Collections.unmodifiableMap(new TreeMap<>(details));
         this.cachedPluginDetailsRevision = this.pluginDataRevision;
         return this.cachedPluginDetails.values();
      }
   }

   private AutismServerInfoOverlay.PluginDetail getPluginDetail(String plugin) {
      if (plugin != null && !plugin.isBlank()) {
         this.getCachedPluginDetails();
         return this.cachedPluginDetails.get(this.normalizePluginKey(plugin));
      } else {
         return null;
      }
   }

   private void mergePluginDetailCommands(Map<String, AutismServerInfoOverlay.PluginDetailBuilder> builders, Map<String, List<String>> source) {
      if (source != null && !source.isEmpty()) {
         for (Entry<String, List<String>> entry : source.entrySet()) {
            String key = this.normalizePluginKey(entry.getKey());
            if (!key.isBlank()) {
               AutismServerInfoOverlay.PluginDetailBuilder builder = builders.computeIfAbsent(key, unused -> new AutismServerInfoOverlay.PluginDetailBuilder());
               if ((builder.displayName == null || builder.displayName.isBlank()) && entry.getKey() != null) {
                  builder.displayName = entry.getKey().trim();
               }

               if (entry.getValue() != null) {
                  for (String command : entry.getValue()) {
                     if (command != null && !command.isBlank()) {
                        builder.commands.add(command.trim());
                     }
                  }
               }
            }
         }
      }
   }

   private void mergePluginDetailCopyCommands(Map<String, AutismServerInfoOverlay.PluginDetailBuilder> builders, Map<String, List<String>> source) {
      if (source != null && !source.isEmpty()) {
         for (Entry<String, List<String>> entry : source.entrySet()) {
            String key = this.normalizePluginKey(entry.getKey());
            if (!key.isBlank()) {
               AutismServerInfoOverlay.PluginDetailBuilder builder = builders.computeIfAbsent(key, unused -> new AutismServerInfoOverlay.PluginDetailBuilder());
               if ((builder.displayName == null || builder.displayName.isBlank()) && entry.getKey() != null) {
                  builder.displayName = entry.getKey().trim();
               }

               if (entry.getValue() != null) {
                  for (String command : entry.getValue()) {
                     if (command != null && !command.isBlank()) {
                        builder.copyCommands.add(command.trim());
                     }
                  }
               }
            }
         }
      }
   }

   private void mergePluginDetailGuis(Map<String, AutismServerInfoOverlay.PluginDetailBuilder> builders, Map<String, List<String>> source) {
      if (source != null && !source.isEmpty()) {
         for (Entry<String, List<String>> entry : source.entrySet()) {
            String key = this.normalizePluginKey(entry.getKey());
            if (!key.isBlank()) {
               AutismServerInfoOverlay.PluginDetailBuilder builder = builders.computeIfAbsent(key, unused -> new AutismServerInfoOverlay.PluginDetailBuilder());
               if ((builder.displayName == null || builder.displayName.isBlank()) && entry.getKey() != null) {
                  builder.displayName = entry.getKey().trim();
               }

               if (entry.getValue() != null) {
                  for (String gui : entry.getValue()) {
                     if (gui != null && !gui.isBlank()) {
                        builder.guis.add(gui.trim());
                     }
                  }
               }
            }
         }
      }
   }

   private void mergePluginDetailChannels(Map<String, AutismServerInfoOverlay.PluginDetailBuilder> builders, Map<String, List<String>> source) {
      if (source != null && !source.isEmpty()) {
         for (Entry<String, List<String>> entry : source.entrySet()) {
            String key = this.normalizePluginKey(entry.getKey());
            if (!key.isBlank()) {
               AutismServerInfoOverlay.PluginDetailBuilder builder = builders.computeIfAbsent(key, unused -> new AutismServerInfoOverlay.PluginDetailBuilder());
               if ((builder.displayName == null || builder.displayName.isBlank()) && entry.getKey() != null) {
                  builder.displayName = entry.getKey().trim();
               }

               if (entry.getValue() != null) {
                  builder.channels.addAll(entry.getValue());
               }
            }
         }
      }
   }

   private List<String> getPluginCommands(String plugin) {
      AutismServerInfoOverlay.PluginDetail detail = this.getPluginDetail(plugin);
      return detail == null ? List.of() : detail.commands;
   }

   private List<String> getPluginGuis(String plugin) {
      AutismServerInfoOverlay.PluginDetail detail = this.getPluginDetail(plugin);
      return detail == null ? List.of() : detail.guis;
   }

   private List<String> getPluginChannels(String plugin) {
      AutismServerInfoOverlay.PluginDetail detail = this.getPluginDetail(plugin);
      return detail == null ? List.of() : detail.channels;
   }

   private List<AutismServerInfoOverlay.PluginListRow> getCachedPluginRows(String queryText, int rowContentWidth) {
      String query = queryText == null ? "" : queryText.trim().toLowerCase(Locale.ROOT);
      String selectedKey = this.selectedPlugin == null ? "" : this.normalizePluginKey(this.selectedPlugin);
      if (this.cachedPluginRowsRevision == this.pluginRowsRevision
         && Objects.equals(this.cachedPluginRowsQuery, query)
         && Objects.equals(this.cachedPluginRowsSelectedKey, selectedKey)
         && this.cachedPluginRowsWidth == rowContentWidth) {
         return this.cachedPluginRows;
      } else {
         List<AutismServerInfoOverlay.PluginDetail> filtered = new ArrayList<>();

         for (AutismServerInfoOverlay.PluginDetail detail : this.getCachedPluginDetails()) {
            if (detail != null && (query.isEmpty() || detail.searchText.contains(query))) {
               filtered.add(detail);
            }
         }

         this.setCachedPluginRows(this.buildPluginRows(filtered, query));
         this.cachedPluginRowsQuery = query;
         this.cachedPluginRowsSelectedKey = selectedKey;
         this.cachedPluginRowsWidth = rowContentWidth;
         this.cachedPluginRowsRevision = this.pluginRowsRevision;
         return this.cachedPluginRows;
      }
   }

   private boolean pluginDetailsMatchQuery(String plugin, String query) {
      if (plugin != null && query != null && !query.isBlank()) {
         AutismServerInfoOverlay.PluginDetail detail = this.getPluginDetail(plugin);
         return detail != null && detail.searchText.contains(query);
      } else {
         return false;
      }
   }

   private void invalidatePluginRows() {
      this.pluginDataRevision++;
      this.pluginRowsRevision++;
      this.cachedPluginRowsRevision = -1;
      this.cachedPluginRowsQuery = null;
      this.cachedPluginRowsSelectedKey = null;
      this.cachedPluginRowsWidth = -1;
      this.cachedPluginRows = List.of();
      this.cachedPluginRowOffsets = new int[0];
      this.cachedPluginRowsHeight = 0;
      this.cachedPluginDetailsRevision = -1;
      this.cachedPluginDetails = Map.of();
      this.requestUiRebuild();
   }

   private void invalidatePluginRowView() {
      this.pluginRowsRevision++;
      this.cachedPluginRowsRevision = -1;
      this.cachedPluginRowsQuery = null;
      this.cachedPluginRowsSelectedKey = null;
      this.cachedPluginRowsWidth = -1;
      this.cachedPluginRows = List.of();
      this.cachedPluginRowOffsets = new int[0];
      this.cachedPluginRowsHeight = 0;
      this.requestUiRebuild();
   }

   private int estimatePluginContentHeight(List<AutismServerInfoOverlay.PluginListRow> rows) {
      if (rows == this.cachedPluginRows) {
         return this.cachedPluginRowsHeight;
      } else {
         int total = 0;

         for (AutismServerInfoOverlay.PluginListRow row : rows) {
            if (row != null) {
               total += row.height;
            }
         }

         return total;
      }
   }

   private void setCachedPluginRows(List<AutismServerInfoOverlay.PluginListRow> rows) {
      List<AutismServerInfoOverlay.PluginListRow> safeRows = rows == null ? List.of() : List.copyOf(rows);
      int[] offsets = new int[safeRows.size()];
      int cursor = 0;

      for (int i = 0; i < safeRows.size(); i++) {
         offsets[i] = cursor;
         AutismServerInfoOverlay.PluginListRow row = safeRows.get(i);
         cursor += row == null ? 0 : row.height;
      }

      this.cachedPluginRows = safeRows;
      this.cachedPluginRowOffsets = offsets;
      this.cachedPluginRowsHeight = cursor;
   }

   private int pluginRowOffset(int index) {
      return index >= 0 && index < this.cachedPluginRowOffsets.length ? this.cachedPluginRowOffsets[index] : 0;
   }

   private int firstVisiblePluginRowIndex(List<AutismServerInfoOverlay.PluginListRow> rows, int scrollOffset) {
      if (rows != null && !rows.isEmpty() && this.cachedPluginRowOffsets.length != 0 && scrollOffset > 0) {
         int idx = Arrays.binarySearch(this.cachedPluginRowOffsets, scrollOffset);
         if (idx < 0) {
            idx = Math.max(0, -idx - 2);
         }

         while (idx > 0) {
            AutismServerInfoOverlay.PluginListRow row = rows.get(idx);
            int rowBottom = this.cachedPluginRowOffsets[idx] + (row == null ? 0 : row.height);
            if (rowBottom <= scrollOffset) {
               break;
            }

            idx--;
         }

         while (idx < rows.size()) {
            AutismServerInfoOverlay.PluginListRow row = rows.get(idx);
            int rowBottom = this.cachedPluginRowOffsets[idx] + (row == null ? 0 : row.height);
            if (rowBottom > scrollOffset) {
               break;
            }

            idx++;
         }

         return Math.max(0, Math.min(idx, rows.size() - 1));
      } else {
         return 0;
      }
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mx);
         float uiMouseY = viewport.toUiY(my);
         if (this.isOverCloseButton(uiMouseX, uiMouseY)) {
            this.setVisible(false);
            this.isDragging = false;
            this.pluginScrollbarDragging = false;
            this.radarScrollbarDragging = false;
            this.dragMoved = false;
            return true;
         } else if (button == 0 && this.isOverHeaderUi(uiMouseX, uiMouseY)) {
            this.isDragging = true;
            this.dragMoved = false;
            this.dragOffsetX = uiMouseX - this.panelX;
            this.dragOffsetY = uiMouseY - this.panelY;
            this.pressStartUiX = uiMouseX;
            this.pressStartUiY = uiMouseY;
            this.pressStartPanelX = this.panelX;
            this.pressStartPanelY = this.panelY;
            return true;
         } else if (this.collapsed) {
            return false;
         } else if (button != 0) {
            return this.isMouseOver(mx, my);
         } else {
            if (this.activeTab == 1 && this.pluginScanDone && !this.pluginScanInProgress) {
               CompactScrollbar.Metrics scrollbarMetrics = this.getPluginScrollbarMetrics(
                  Math.round(this.pluginListSlot.x()),
                  Math.round(this.pluginListSlot.y()),
                  Math.round(this.pluginListSlot.width()),
                  this.getPluginlistViewportHeight(
                     Math.max(0, Math.min(Math.round(this.pluginListSlot.height()), this.panelY + this.panelH - 10 - Math.round(this.pluginListSlot.y())))
                  )
               );
               if (scrollbarMetrics.hasScroll() && scrollbarMetrics.contains(uiMouseX, uiMouseY)) {
                  this.pluginScrollbarDragging = true;
                  this.pluginScrollbarGrabOffset = Math.max(0, Math.round(uiMouseY) - scrollbarMetrics.thumbY());
                  this.pluginScrollState.setFromThumbStepped(scrollbarMetrics, Math.round(uiMouseY), this.pluginScrollbarGrabOffset, this.pluginListRowStep());
                  this.saveState();
                  return true;
               }
            }

            if (this.activeTab == 2 && this.isRadarAuthenticated()) {
               CompactScrollbar.Metrics scrollbarMetrics = this.getRadarScrollbarMetrics(
                  Math.round(this.radarListSlot.x()),
                  Math.round(this.radarListSlot.y()),
                  Math.round(this.radarListSlot.width()),
                  this.getPluginlistViewportHeight(
                     Math.max(0, Math.min(Math.round(this.radarListSlot.height()), this.panelY + this.panelH - 10 - Math.round(this.radarListSlot.y())))
                  )
               );
               if (scrollbarMetrics.hasScroll() && scrollbarMetrics.contains(uiMouseX, uiMouseY)) {
                  this.radarScrollbarDragging = true;
                  this.radarScrollbarGrabOffset = Math.max(0, Math.round(uiMouseY) - scrollbarMetrics.thumbY());
                  this.radarScrollState.setFromThumb(scrollbarMetrics, Math.round(uiMouseY), this.radarScrollbarGrabOffset);
                  this.saveState();
                  return true;
               }
            }

            if (this.activeTab == 1 && this.pluginScanDone && !this.pluginScanInProgress || this.activeTab == 2 && this.isRadarAuthenticated()) {
               for (AutismServerInfoOverlay.ClickRegion region : this.clickRegions) {
                  if (region.contains(uiMouseX, uiMouseY)) {
                     this.surface.clearFocusedTextInputs();
                     region.action.run();
                     return true;
                  }
               }
            }

            if (this.surface.mouseClicked(mx, my, button)) {
               return true;
            } else {
               this.surface.clearFocusedTextInputs();
               return this.isMouseOver(mx, my);
            }
         }
      }
   }

   @Override
   public boolean mouseReleased(double mx, double my, int button) {
      if (button == 0 && this.pluginScrollbarDragging) {
         this.pluginScrollbarDragging = false;
         this.saveState();
         return true;
      } else if (button == 0 && this.radarScrollbarDragging) {
         this.radarScrollbarDragging = false;
         this.saveState();
         return true;
      } else if (this.isDragging) {
         boolean shouldCollapse = !this.dragMoved;
         this.isDragging = false;
         if (shouldCollapse) {
            this.setCollapsed(!this.collapsed);
            if (!this.collapsed) {
               if (this.activeTab == 0) {
                  this.applyInfoLayout();
               } else if (this.activeTab == 2) {
                  this.applyRadarLayout();
               } else if (this.pluginScanInProgress) {
                  this.applyPluginScanningLayout();
               } else if (!this.pluginScanDone) {
                  this.applyPluginSetupLayout();
               } else {
                  this.applyPluginResultsLayout();
               }
            }
         }

         this.saveState();
         return true;
      } else {
         return this.surface.mouseReleased(mx, my, button);
      }
   }

   @Override
   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (this.pluginScrollbarDragging && button == 0) {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseY = viewport.toUiY(my);
         CompactScrollbar.Metrics scrollbarMetrics = this.getPluginScrollbarMetrics(
            Math.round(this.pluginListSlot.x()),
            Math.round(this.pluginListSlot.y()),
            Math.round(this.pluginListSlot.width()),
            this.getPluginlistViewportHeight(
               Math.max(0, Math.min(Math.round(this.pluginListSlot.height()), this.panelY + this.panelH - 10 - Math.round(this.pluginListSlot.y())))
            )
         );
         this.pluginScrollState.setFromThumbStepped(scrollbarMetrics, Math.round(uiMouseY), this.pluginScrollbarGrabOffset, this.pluginListRowStep());
         return true;
      } else if (this.radarScrollbarDragging && button == 0) {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseY = viewport.toUiY(my);
         CompactScrollbar.Metrics scrollbarMetrics = this.getRadarScrollbarMetrics(
            Math.round(this.radarListSlot.x()),
            Math.round(this.radarListSlot.y()),
            Math.round(this.radarListSlot.width()),
            this.getPluginlistViewportHeight(
               Math.max(0, Math.min(Math.round(this.radarListSlot.height()), this.panelY + this.panelH - 10 - Math.round(this.radarListSlot.y())))
            )
         );
         this.radarScrollState.setFromThumb(scrollbarMetrics, Math.round(uiMouseY), this.radarScrollbarGrabOffset);
         return true;
      } else if (!this.isDragging) {
         return this.surface.mouseDragged(mx, my, button, dx, dy);
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mx);
         float uiMouseY = viewport.toUiY(my);
         AutismWindowLayout clamped = this.clampToViewport(
            new AutismWindowLayout(
               Math.round(uiMouseX - (float)this.dragOffsetX),
               Math.round(uiMouseY - (float)this.dragOffsetY),
               this.panelW,
               this.panelH,
               this.visible,
               this.collapsed
            )
         );
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.dragMoved = this.dragMoved
            || Math.abs(uiMouseX - this.pressStartUiX) >= 3.0F
            || Math.abs(uiMouseY - this.pressStartUiY) >= 3.0F
            || this.panelX != this.pressStartPanelX
            || this.panelY != this.pressStartPanelY;
         return true;
      }
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double amount) {
      if (!this.visible || this.collapsed || !this.isMouseOver(mx, my)) {
         return false;
      } else if (this.activeTab == 1 && this.pluginScanDone && !this.pluginScanInProgress) {
         int slotY = Math.round(this.pluginListSlot.y());
         int slotH = Math.round(this.pluginListSlot.height());
         int rawViewH = Math.max(0, Math.min(slotH, this.panelY + this.panelH - 10 - slotY));
         int viewH = this.getPluginlistViewportHeight(rawViewH);
         int maxScroll = Math.max(0, this.pluginContentHeight - this.getPluginListInnerHeight(viewH));
         this.pluginScrollState.nudge(amount, this.pluginListRowStep(), maxScroll);
         return true;
      } else if (this.activeTab == 2 && this.isRadarAuthenticated()) {
         int slotY = Math.round(this.radarListSlot.y());
         int slotH = Math.round(this.radarListSlot.height());
         int rawViewH = Math.max(0, Math.min(slotH, this.panelY + this.panelH - 10 - slotY));
         int viewH = this.getPluginlistViewportHeight(rawViewH);
         int maxScroll = Math.max(0, this.radarContentHeight - this.getPluginListInnerHeight(viewH));
         this.radarScrollState.nudge(amount, this.pluginListRowStep(), maxScroll);
         return true;
      } else {
         return this.surface.mouseScrolled(mx, my, amount);
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      return this.visible && !this.collapsed && this.surface.keyPressed(keyCode, scanCode, modifiers);
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      return this.visible && !this.collapsed && this.surface.charTyped(chr, modifiers);
   }

   private List<AutismDupeRadar.RadarPluginSnapshot> radarPluginSnapshots() {
      if (!this.pluginScanDone) {
         return List.of();
      } else {
         List<AutismDupeRadar.RadarPluginSnapshot> snapshots = new ArrayList<>();

         for (AutismServerInfoOverlay.PluginDetail detail : this.getCachedPluginDetails()) {
            if (detail != null && !detail.displayName.isBlank()) {
               boolean feature = detail.resultKind == AutismServerInfoOverlay.PluginResultKind.FEATURE
                  || detail.confidence == AutismServerInfoOverlay.PluginConfidence.FEATURE
                  || detail.evidence == AutismServerInfoOverlay.PluginEvidence.SCANNER_AUTOCOMPLETE
                  || detail.evidence == AutismServerInfoOverlay.PluginEvidence.USER_AUTOCOMPLETE
                  || detail.evidence == AutismServerInfoOverlay.PluginEvidence.FEATURE
                  || detail.evidence == AutismServerInfoOverlay.PluginEvidence.HELP_HINT
                  || detail.evidence == AutismServerInfoOverlay.PluginEvidence.UNKNOWN;
               if (!feature
                  && (
                     detail.confidence == AutismServerInfoOverlay.PluginConfidence.EXACT
                        || detail.confidence == AutismServerInfoOverlay.PluginConfidence.STRONG
                  )) {
                  snapshots.add(
                     new AutismDupeRadar.RadarPluginSnapshot(
                        detail.displayName, detail.key, this.confidenceLabel(detail.confidence), detail.commands.size(), detail.channels, detail.guis, false
                     )
                  );
               }
            }
         }

         snapshots.sort(Comparator.comparing(AutismDupeRadar.RadarPluginSnapshot::displayName, String.CASE_INSENSITIVE_ORDER));
         return snapshots;
      }
   }

   private void maybeAutoCheckRadar(AutismDupeRadar.RadarState state, int pluginCount) {
      if (state != null && state.authenticated() && !state.authenticating() && !state.checking()) {
         AutismDupeRadar.RadarServerSnapshot serverSnapshot = this.radarServerSnapshot();
         boolean hasServerIdentity = serverSnapshot != null && !serverSnapshot.identities().isEmpty();
         if (pluginCount > 0 || hasServerIdentity) {
            long now = System.currentTimeMillis();
            String signature = this.radarCheckSignature();
            boolean noResults = state.updatedAtMs() <= 0L;
            boolean stale = state.updatedAtMs() > 0L && now - state.updatedAtMs() > 1800000L;
            boolean changed = !signature.equals(this.lastRadarAutoCheckSignature);
            if (noResults || stale || changed) {
               if (changed || now - this.lastRadarAutoCheckAt >= 5000L) {
                  this.lastRadarAutoCheckSignature = signature;
                  this.lastRadarAutoCheckAt = now;
                  AutismDupeRadar.checkServer(this.radarPluginSnapshots(), serverSnapshot, stale);
               }
            }
         }
      }
   }

   private String radarCheckSignature() {
      StringBuilder sb = new StringBuilder();
      sb.append(this.currentServerAddress()).append('|');

      for (AutismDupeRadar.RadarPluginSnapshot snapshot : this.radarPluginSnapshots()) {
         sb.append(snapshot.canonicalKey()).append(',');
      }

      for (AutismDupeRadar.RadarServerIdentity identity : this.radarServerSnapshot().identities()) {
         sb.append(identity.ip() ? "ip:" : "domain:").append(identity.value()).append(',');
      }

      return sb.toString();
   }

   private AutismDupeRadar.RadarServerSnapshot radarServerSnapshot() {
      List<AutismDupeRadar.RadarServerIdentity> identities = new ArrayList<>();
      this.addRadarServerIdentity(identities, this.getDisplayedServerAddress());
      this.addRadarServerIdentity(identities, this.currentServerAddress());
      this.addRadarServerIdentity(identities, this.extractLookupHost(this.getDisplayedServerAddress()));
      String liveIp = this.getLiveSocketIp();
      if (!liveIp.isBlank()) {
         this.addRadarServerIdentity(identities, liveIp);
      }

      String displayedIp = this.getDisplayedRealIp(this.getDisplayedServerAddress());
      if (!displayedIp.isBlank() && !"--".equals(displayedIp) && !"Resolving...".equals(displayedIp) && !"Failed".equals(displayedIp)) {
         this.addRadarServerIdentity(identities, displayedIp);
      }

      return new AutismDupeRadar.RadarServerSnapshot(identities);
   }

   private String radarServerLabel() {
      String displayed = this.extractLookupHost(this.getDisplayedServerAddress());
      if (displayed != null && !displayed.isBlank() && !"--".equals(displayed)) {
         return displayed;
      } else {
         String current = this.extractLookupHost(this.currentServerAddress());
         return current != null && !current.isBlank() && !"--".equals(current) ? current : "--";
      }
   }

   private void addRadarServerIdentity(List<AutismDupeRadar.RadarServerIdentity> identities, String raw) {
      if (identities != null && raw != null && !raw.isBlank() && !"--".equals(raw)) {
         String clean = raw.trim().toLowerCase(Locale.ROOT);
         if (clean.startsWith("/")) {
            clean = clean.substring(1);
         }

         String host = this.extractLookupHost(clean);
         if (host == null || host.isBlank() || "--".equals(host)) {
            host = clean;
         }

         host = host.trim().toLowerCase(Locale.ROOT);
         if (!host.isBlank() && !"localhost".equals(host)) {
            boolean ip = host.matches("\\d{1,3}(?:\\.\\d{1,3}){3}") || host.contains(":") && !host.contains(".");
            identities.add(new AutismDupeRadar.RadarServerIdentity(host, ip));
         }
      }
   }

   private void copyRadarReport() {
      MC.keyboardHandler.setClipboard(AutismDupeRadar.copyReport());
      AutismNotifications.copied("Radar report copied.");
   }

   private void copyServerData() {
      StringBuilder sb = new StringBuilder();
      ServerData entry = MC.getCurrentServer();
      String ip = this.getDisplayedServerAddress();
      String realIp = this.getDisplayedRealIp(ip);
      List<String> detectedAcs = this.getDetectedAnticheats();
      String software = this.getSoftwareGuess(entry);
      String versionNote = this.getVersionNote(entry);
      sb.append("=== Server Info ===\n");
      sb.append("IP:         ").append(ip).append("\n");
      if (!realIp.equals("--")) {
         sb.append("Real IP:    ").append(realIp).append("\n");
      }

      sb.append("Version:    ").append(this.getReportedVersion(entry)).append("\n");
      sb.append("RealVersion: ").append(this.getRealServerVersion()).append("\n");
      sb.append("Brand:      ").append(this.getLiveBrand()).append("\n");
      if (!"--".equals(software)) {
         sb.append("Software:   ").append(software).append("\n");
      }

      if (MC.getConnection() != null) {
         sb.append("Players:    ").append(MC.getConnection().getListedOnlinePlayers().size());
         if (entry != null && entry.players != null) {
            sb.append(" / ").append(entry.players.max());
         }

         sb.append("\n");
      } else {
         sb.append("Players:    --\n");
      }

      sb.append("Protocol:   ").append(entry != null ? entry.protocol : "--").append("\n");
      if (!"--".equals(versionNote)) {
         sb.append("VersionNote: ").append(versionNote).append("\n");
      }

      sb.append("Difficulty: ").append(MC.level != null ? MC.level.getDifficulty().getDisplayName().getString() : "--").append("\n");
      sb.append("World:      ").append(this.getCurrentWorldName()).append("\n");
      if (MC.level != null) {
         long dayCount = MC.level.getDayTime() / 24000L;
         long timeOfDay = MC.level.getDayTime() % 24000L;
         int hours = (int)((timeOfDay / 1000L + 6L) % 24L);
         int minutes = (int)(timeOfDay % 1000L * 60L / 1000L);
         sb.append("Time:       Day ").append(dayCount).append(" (").append(String.format("%02d:%02d", hours, minutes)).append(")\n");
      } else {
         sb.append("Time:       --\n");
      }

      if (this.pluginScanInProgress) {
         sb.append("AntiCheats: Scanning...\n");
      } else if (!this.pluginScanDone) {
         sb.append("AntiCheats: Probe Plugins First\n");
      } else if (detectedAcs.isEmpty()) {
         sb.append("AntiCheats: None detected\n");
      } else {
         sb.append("AntiCheats: ").append(String.join(", ", detectedAcs)).append("\n");
      }

      MC.keyboardHandler.setClipboard(sb.toString());
      AutismNotifications.copied("Server info copied.");
   }

   private void copyPluginList() {
      if (this.detectedPlugins.isEmpty()) {
         AutismNotifications.error("No plugins detected.");
      } else {
         Map<String, List<AutismServerInfoOverlay.PluginDetail>> grouped = new TreeMap<>((a, b) -> {
            int rank = Integer.compare(this.copyPluginGroupRank(a), this.copyPluginGroupRank(b));
            return rank != 0 ? rank : String.CASE_INSENSITIVE_ORDER.compare(a, b);
         });
         int copyCount = 0;

         for (String plugin : this.dedupePluginNames(this.detectedPlugins)) {
            if (plugin != null && !plugin.isBlank()) {
               AutismServerInfoOverlay.PluginDetail detail = this.getPluginDetail(plugin);
               if (detail != null
                  && detail.copyEvidence != AutismServerInfoOverlay.PluginEvidence.HELP_HINT
                  && detail.copyEvidence != AutismServerInfoOverlay.PluginEvidence.UNKNOWN) {
                  String uiGroupTitle = this.pluginGroupTitle(detail.resultKind, detail.confidence, detail.evidence);
                  if (!"Command Hints".equals(uiGroupTitle)
                     && !"Other".equals(uiGroupTitle)
                     && (
                        detail.resultKind != AutismServerInfoOverlay.PluginResultKind.FEATURE
                           || detail.copyEvidence == AutismServerInfoOverlay.PluginEvidence.COMMAND_GUI
                     )) {
                     grouped.computeIfAbsent(this.copyPluginGroupTitle(detail), unused -> new ArrayList<>()).add(detail);
                     copyCount++;
                  }
               }
            }
         }

         if (copyCount <= 0) {
            AutismNotifications.error("No non-hint plugins to copy.");
         } else {
            StringBuilder sb = new StringBuilder();
            sb.append("Plugins (").append(copyCount).append("):\n");

            for (Entry<String, List<AutismServerInfoOverlay.PluginDetail>> group : grouped.entrySet()) {
               List<AutismServerInfoOverlay.PluginDetail> details = group.getValue();
               if (details != null && !details.isEmpty()) {
                  details.sort(Comparator.comparing(detailx -> detailx.displayName, String.CASE_INSENSITIVE_ORDER));
                  sb.append("\n[").append(group.getKey()).append("]\n");

                  for (AutismServerInfoOverlay.PluginDetail detail : details) {
                     boolean ac = ANTICHEATS.contains(detail.key.toLowerCase(Locale.ROOT));
                     String groupTitle = this.copyPluginGroupTitle(detail);
                     sb.append(ac ? "[AC] " : "- ").append(detail.displayName);
                     if (!detail.copyCommands.isEmpty()
                        && !"Plugin Command Hint".equals(groupTitle)
                        && !"Version Command Hint".equals(groupTitle)
                        && !"Payload Channel".equals(groupTitle)) {
                        sb.append(" (").append(detail.copyCommands.size()).append(" cmds)");
                     }

                     sb.append("\n");
                  }
               }
            }

            MC.keyboardHandler.setClipboard(sb.toString());
            AutismNotifications.copied("Plugin list copied.");
         }
      }
   }

   private static class ClickRegion {
      final int x;
      final int y;
      final int width;
      final int height;
      final Runnable action;

      ClickRegion(int x, int y, int width, int height, Runnable action) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.action = action;
      }

      boolean contains(double mx, double my) {
         return mx >= this.x && mx < this.x + this.width && my >= this.y && my < this.y + this.height;
      }
   }

   private record ObservedCommand(String root, String fullCommand, long timestampMs) {
   }

   private static enum PluginConfidence {
      EXACT,
      STRONG,
      FEATURE,
      UNKNOWN;
   }

   private static final class PluginDetail {
      final String displayName;
      final String key;
      final List<String> commands;
      final List<String> guis;
      final List<String> channels;
      final AutismServerInfoOverlay.PluginResultKind resultKind;
      final AutismServerInfoOverlay.PluginEvidence evidence;
      final AutismServerInfoOverlay.PluginEvidence copyEvidence;
      final AutismServerInfoOverlay.PluginConfidence confidence;
      final String sourceLabel;
      final String countLabel;
      final List<String> copyCommands;
      final String searchText;

      PluginDetail(
         String displayName,
         String key,
         List<String> commands,
         List<String> guis,
         List<String> channels,
         AutismServerInfoOverlay.PluginResultKind resultKind,
         AutismServerInfoOverlay.PluginEvidence evidence,
         AutismServerInfoOverlay.PluginEvidence copyEvidence,
         AutismServerInfoOverlay.PluginConfidence confidence,
         String sourceLabel,
         String countLabel,
         List<String> copyCommands,
         String searchText
      ) {
         this.displayName = displayName == null ? "" : displayName;
         this.key = key == null ? "" : key;
         this.commands = commands == null ? List.of() : commands;
         this.guis = guis == null ? List.of() : guis;
         this.channels = channels == null ? List.of() : channels;
         this.resultKind = resultKind == null ? AutismServerInfoOverlay.PluginResultKind.PLUGIN : resultKind;
         this.evidence = evidence == null ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : evidence;
         this.copyEvidence = copyEvidence == null ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : copyEvidence;
         this.confidence = confidence == null ? AutismServerInfoOverlay.PluginConfidence.UNKNOWN : confidence;
         this.sourceLabel = sourceLabel == null ? "" : sourceLabel;
         this.countLabel = countLabel == null ? "" : countLabel;
         this.copyCommands = copyCommands == null ? List.of() : copyCommands;
         this.searchText = searchText == null ? "" : searchText;
      }
   }

   private static final class PluginDetailBuilder {
      String displayName;
      final Set<String> commands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      final Set<String> copyCommands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      final Set<String> guis = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      final List<String> channels = new ArrayList<>();
   }

   private static enum PluginEvidence {
      PAYLOAD_CHANNEL,
      COMMAND_GUI,
      COMMAND_TREE,
      USER_AUTOCOMPLETE,
      SCANNER_AUTOCOMPLETE,
      NAMESPACE,
      ROOT_HINT,
      HELP_HINT,
      PLUGIN_LIST,
      VERSION_HINT,
      FEATURE,
      UNKNOWN;
   }

   private static final class PluginListRow {
      final AutismServerInfoOverlay.PluginRowType type;
      final String title;
      final String plugin;
      final String label;
      final String actionCommand;
      final AutismServerInfoOverlay.PluginResultKind resultKind;
      final AutismServerInfoOverlay.PluginEvidence evidence;
      final AutismServerInfoOverlay.PluginConfidence confidence;
      final int height;
      final String countLabel;
      final String searchText;

      private PluginListRow(
         AutismServerInfoOverlay.PluginRowType type,
         String title,
         String plugin,
         String label,
         String actionCommand,
         AutismServerInfoOverlay.PluginResultKind resultKind,
         AutismServerInfoOverlay.PluginEvidence evidence,
         AutismServerInfoOverlay.PluginConfidence confidence,
         int height,
         String countLabel,
         String searchText
      ) {
         this.type = type;
         this.title = title;
         this.plugin = plugin;
         this.label = label;
         this.actionCommand = actionCommand;
         this.resultKind = resultKind == null ? AutismServerInfoOverlay.PluginResultKind.PLUGIN : resultKind;
         this.evidence = evidence == null ? AutismServerInfoOverlay.PluginEvidence.UNKNOWN : evidence;
         this.confidence = confidence == null ? AutismServerInfoOverlay.PluginConfidence.UNKNOWN : confidence;
         this.height = Math.max(1, height);
         this.countLabel = countLabel == null ? "" : countLabel;
         this.searchText = searchText == null ? "" : searchText;
      }

      static AutismServerInfoOverlay.PluginListRow header(String title, int height) {
         return new AutismServerInfoOverlay.PluginListRow(
            AutismServerInfoOverlay.PluginRowType.HEADER,
            title,
            null,
            title,
            null,
            AutismServerInfoOverlay.PluginResultKind.PLUGIN,
            AutismServerInfoOverlay.PluginEvidence.UNKNOWN,
            AutismServerInfoOverlay.PluginConfidence.UNKNOWN,
            height,
            "",
            lower(title)
         );
      }

      static AutismServerInfoOverlay.PluginListRow plugin(
         String plugin,
         AutismServerInfoOverlay.PluginResultKind resultKind,
         AutismServerInfoOverlay.PluginEvidence evidence,
         AutismServerInfoOverlay.PluginConfidence confidence,
         int height,
         String countLabel,
         String searchText
      ) {
         return new AutismServerInfoOverlay.PluginListRow(
            AutismServerInfoOverlay.PluginRowType.PLUGIN, null, plugin, plugin, null, resultKind, evidence, confidence, height, countLabel, searchText
         );
      }

      static AutismServerInfoOverlay.PluginListRow detail(
         AutismServerInfoOverlay.PluginRowType type,
         String plugin,
         String label,
         String actionCommand,
         AutismServerInfoOverlay.PluginEvidence evidence,
         AutismServerInfoOverlay.PluginConfidence confidence,
         int height,
         String searchText
      ) {
         return new AutismServerInfoOverlay.PluginListRow(
            type, null, plugin, label, actionCommand, AutismServerInfoOverlay.PluginResultKind.PLUGIN, evidence, confidence, height, "", searchText
         );
      }

      boolean header() {
         return this.type == AutismServerInfoOverlay.PluginRowType.HEADER;
      }

      private static String lower(String text) {
         return text == null ? "" : text.toLowerCase(Locale.ROOT);
      }
   }

   private static enum PluginProbeKind {
      ROOT,
      HELP,
      PLUGIN_LIST,
      VERSION,
      NAMESPACE,
      USER;
   }

   private static final class PluginProbeRequest {
      final int id;
      final AutismServerInfoOverlay.PluginProbeSpec spec;

      PluginProbeRequest(int id, AutismServerInfoOverlay.PluginProbeSpec spec) {
         this.id = id;
         this.spec = spec;
      }
   }

   private static final class PluginProbeSpec {
      final String query;
      final AutismServerInfoOverlay.PluginProbeKind kind;
      final String hint;

      PluginProbeSpec(String query, AutismServerInfoOverlay.PluginProbeKind kind, String hint) {
         this.query = query;
         this.kind = kind;
         this.hint = hint;
      }
   }

   private static enum PluginResultKind {
      PLUGIN,
      FEATURE;
   }

   private static enum PluginRowType {
      HEADER,
      PLUGIN,
      COMMAND,
      GUI,
      CHANNEL,
      WHY;
   }

   private static final class PluginScanEntry {
      String displayName;
      boolean commandBacked;
      AutismServerInfoOverlay.PluginResultKind resultKind;
      AutismServerInfoOverlay.PluginEvidence evidence = AutismServerInfoOverlay.PluginEvidence.UNKNOWN;
      AutismServerInfoOverlay.PluginEvidence copyEvidence = AutismServerInfoOverlay.PluginEvidence.UNKNOWN;
      AutismServerInfoOverlay.PluginConfidence confidence = AutismServerInfoOverlay.PluginConfidence.FEATURE;
      final Set<String> commands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      final Set<String> copyCommands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      final Set<String> channels = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      final Set<String> guis = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
   }

   private static final class RadarListRow {
      final AutismServerInfoOverlay.RadarRowType type;
      final String title;
      final AutismDupeRadar.RadarMatch match;
      final AutismDupeRadar.RadarFinding finding;
      final int height;
      final String searchText;

      private RadarListRow(
         AutismServerInfoOverlay.RadarRowType type,
         String title,
         AutismDupeRadar.RadarMatch match,
         AutismDupeRadar.RadarFinding finding,
         int height,
         String searchText
      ) {
         this.type = type == null ? AutismServerInfoOverlay.RadarRowType.EMPTY : type;
         this.title = title == null ? "" : title;
         this.match = match;
         this.finding = finding;
         this.height = Math.max(1, height);
         this.searchText = searchText == null ? "" : searchText;
      }

      static AutismServerInfoOverlay.RadarListRow header(String title, int height) {
         return new AutismServerInfoOverlay.RadarListRow(AutismServerInfoOverlay.RadarRowType.HEADER, title, null, null, height, lower(title));
      }

      static AutismServerInfoOverlay.RadarListRow match(AutismDupeRadar.RadarMatch match, int height, String searchText) {
         return new AutismServerInfoOverlay.RadarListRow(AutismServerInfoOverlay.RadarRowType.MATCH, "", match, null, height, searchText);
      }

      static AutismServerInfoOverlay.RadarListRow finding(AutismDupeRadar.RadarMatch match, AutismDupeRadar.RadarFinding finding, int height, String searchText) {
         return new AutismServerInfoOverlay.RadarListRow(AutismServerInfoOverlay.RadarRowType.FINDING, "", match, finding, height, searchText);
      }

      static AutismServerInfoOverlay.RadarListRow empty(String text, int height) {
         return new AutismServerInfoOverlay.RadarListRow(AutismServerInfoOverlay.RadarRowType.EMPTY, text, null, null, height, lower(text));
      }

      private static String lower(String text) {
         return text == null ? "" : text.toLowerCase(Locale.ROOT);
      }
   }

   private static enum RadarRowType {
      HEADER,
      MATCH,
      FINDING,
      EMPTY;
   }
}
