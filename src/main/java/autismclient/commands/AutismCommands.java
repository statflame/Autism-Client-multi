package autismclient.commands;

import autismclient.addons.AddonManager;
import autismclient.api.AddonRegistrationResult;
import autismclient.commands.impl.BindCommand;
import autismclient.commands.impl.BindsCommand;
import autismclient.commands.impl.CommandsCommand;
import autismclient.commands.impl.DamageCommand;
import autismclient.commands.impl.DelayCommand;
import autismclient.commands.impl.DisconnectCommand;
import autismclient.commands.impl.DismountCommand;
import autismclient.commands.impl.DropCommand;
import autismclient.commands.impl.GamemodeCommand;
import autismclient.commands.impl.GiveCommand;
import autismclient.commands.impl.HClipCommand;
import autismclient.commands.impl.HelpCommand;
import autismclient.commands.impl.MacroCommand;
import autismclient.commands.impl.ModulesCommand;
import autismclient.commands.impl.NbtCommand;
import autismclient.commands.impl.PluginsCommand;
import autismclient.commands.impl.PrefixCommand;
import autismclient.commands.impl.SayCommand;
import autismclient.commands.impl.SendCommand;
import autismclient.commands.impl.ServerCommand;
import autismclient.commands.impl.ToggleCommand;
import autismclient.commands.impl.VClipCommand;
import autismclient.commands.impl.XCarryCommand;
import autismclient.modules.PackHideState;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismCompatManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public final class AutismCommands {
   private static final CommandDispatcher<AutismCommandSource> DISPATCHER = new CommandDispatcher();
   private static final List<Command> ALL = new ArrayList<>();
   private static final Map<String, Command> BY_NAME = new LinkedHashMap<>();
   private static final Map<String, String> ADDON_COMMAND_OWNERS = new LinkedHashMap<>();
   private static final Map<Command, String> ADDON_COMMAND_OBJECT_OWNERS = new IdentityHashMap<>();
   private static final Set<String> DISABLED_ADDON_COMMAND_NAMES = new HashSet<>();
   private static final List<String> PANIC_PREFIX_FALLBACKS = List.of(
      ".", ",", ";", ":", "'", "\"", "\\", "|", "-", "_", "+", "=", "*", "#", "@", "!", "$", "%", "&", "~"
   );
   private static boolean initialized = false;

   private AutismCommands() {
   }

   public static synchronized void init() {
      if (!initialized) {
         initialized = true;
         register(new ToggleCommand());
         register(new MacroCommand());
         register(new DelayCommand());
         register(new SendCommand());
         register(new VClipCommand());
         register(new HClipCommand());
         register(new NbtCommand());
         register(new ServerCommand());
         register(new PluginsCommand());
         register(new PrefixCommand());
         register(new CommandsCommand());
         register(new HelpCommand());
         register(new ModulesCommand());
         register(new BindsCommand());
         register(new BindCommand());
         register(new DismountCommand());
         register(new DisconnectCommand());
         register(new SayCommand());
         register(new DropCommand());
         register(new GamemodeCommand());
         register(new GiveCommand());
         register(new DamageCommand());
         register(new XCarryCommand());
      }
   }

   private static void register(Command command) {
      registerWithName(command, command.name(), true);
   }

   private static List<String> registerWithName(Command command, String primaryName, boolean registerAliases) {
      List<String> registeredNames = new ArrayList<>();
      ALL.add(command);
      LiteralArgumentBuilder<AutismCommandSource> primary = LiteralArgumentBuilder.literal(primaryName);
      command.build(primary);
      DISPATCHER.register(primary);
      BY_NAME.put(primaryName.toLowerCase(Locale.ROOT), command);
      DISABLED_ADDON_COMMAND_NAMES.remove(primaryName.toLowerCase(Locale.ROOT));
      registeredNames.add(primaryName.toLowerCase(Locale.ROOT));
      if (!registerAliases) {
         return registeredNames;
      } else {
         for (String alias : command.aliases()) {
            if (alias != null && !alias.isBlank() && !BY_NAME.containsKey(alias.toLowerCase(Locale.ROOT))) {
               LiteralArgumentBuilder<AutismCommandSource> aliasBuilder = LiteralArgumentBuilder.literal(alias);
               command.build(aliasBuilder);
               DISPATCHER.register(aliasBuilder);
               BY_NAME.put(alias.toLowerCase(Locale.ROOT), command);
               DISABLED_ADDON_COMMAND_NAMES.remove(alias.toLowerCase(Locale.ROOT));
               registeredNames.add(alias.toLowerCase(Locale.ROOT));
            }
         }

         return registeredNames;
      }
   }

   public static synchronized void registerAddonCommand(Command command, String addonId) {
      registerAddonCommandDetailed(command, addonId);
   }

   public static synchronized AddonRegistrationResult registerAddonCommandDetailed(Command command, String addonId) {
      if (command == null) {
         return AddonRegistrationResult.rejected("command", "", "command was null");
      } else if (addonId != null && !addonId.isBlank()) {
         String name = command.name();
         if (name != null && !name.isBlank()) {
            boolean collision = BY_NAME.containsKey(name.toLowerCase(Locale.ROOT));
            List<String> registered;
            if (collision) {
               String namespaced = (addonId != null && !addonId.isBlank() ? addonId : "addon") + ":" + name;
               autismclient.AutismClientAddon.LOG
                  .warn(
                     "[Commands] Command name '{}' from addon '{}' collides with an existing command; registering it as '{}' instead",
                     new Object[]{name, addonId, namespaced}
                  );
               registered = registerWithName(command, namespaced, false);
            } else {
               registered = registerWithName(command, name, true);
            }

            ADDON_COMMAND_OBJECT_OWNERS.put(command, addonId);

            for (String registeredName : registered) {
               ADDON_COMMAND_OWNERS.put(registeredName, addonId);
            }

            String id = registered.isEmpty() ? name : registered.get(0);
            AddonManager.recordAcceptedRegistration("command", id);
            return AddonRegistrationResult.accepted("command", id);
         } else {
            return rejectAddonCommand(addonId, "", "blank command name");
         }
      } else {
         return rejectAddonCommand(addonId, "", "registration outside an addon lifecycle");
      }
   }

   private static AddonRegistrationResult rejectAddonCommand(String addonId, String id, String reason) {
      autismclient.AutismClientAddon.LOG.warn("[Commands] Rejecting addon command '{}': {}", id, reason);
      AddonManager.recordRejectedRegistration(addonId, "command", id, reason);
      return AddonRegistrationResult.rejected("command", id, reason);
   }

   public static synchronized void unregisterAddonCommands(String addonId) {
      if (addonId != null && !addonId.isBlank()) {
         List<String> removeNames = new ArrayList<>();

         for (Entry<String, String> owner : ADDON_COMMAND_OWNERS.entrySet()) {
            if (addonId.equals(owner.getValue())) {
               removeNames.add(owner.getKey());
            }
         }

         for (String name : removeNames) {
            BY_NAME.remove(name);
            ADDON_COMMAND_OWNERS.remove(name);
            DISABLED_ADDON_COMMAND_NAMES.add(name);
         }

         ALL.removeIf(command -> addonId.equals(ADDON_COMMAND_OBJECT_OWNERS.get(command)));
         ADDON_COMMAND_OBJECT_OWNERS.entrySet().removeIf(entry -> addonId.equals(entry.getValue()));
      }
   }

   public static CommandDispatcher<AutismCommandSource> dispatcher() {
      return DISPATCHER;
   }

   public static List<Command> all() {
      return Collections.unmodifiableList(ALL);
   }

   public static Command find(String nameOrAlias) {
      return nameOrAlias == null ? null : BY_NAME.get(nameOrAlias.trim().toLowerCase(Locale.ROOT));
   }

   public static String effectivePrefix() {
      return AutismCompatManager.effectiveCommandPrefix();
   }

   public static boolean isAutismCommandMessage(String message) {
      if (message != null && !message.isBlank()) {
         String trimmed = message.trim();
         String prefix = effectivePrefix();
         return !prefix.isEmpty() && trimmed.startsWith(prefix);
      } else {
         return false;
      }
   }

   public static boolean commandsBlockedByPanic() {
      return PackHideState.isHardLocked();
   }

   public static boolean isBlockedPanicCommandMessage(String message) {
      return commandsBlockedByPanic() && isPanicBlockedCommandMessage(message);
   }

   private static boolean isPanicBlockedCommandMessage(String message) {
      if (message != null && !message.isBlank()) {
         String trimmed = message.trim();
         if ("^toggleautism".equalsIgnoreCase(trimmed)) {
            return true;
         } else {
            String prefix = effectivePrefix();
            if (!prefix.isEmpty() && trimmed.startsWith(prefix)) {
               return true;
            } else {
               for (String fallback : PANIC_PREFIX_FALLBACKS) {
                  if (fallback != null && !fallback.isEmpty() && !fallback.equals(prefix) && trimmed.startsWith(fallback)) {
                     String body = trimmed.substring(fallback.length()).trim();
                     if (!body.isEmpty()) {
                        String first = firstToken(body).toLowerCase(Locale.ROOT);
                        if (BY_NAME.containsKey(first) || DISABLED_ADDON_COMMAND_NAMES.contains(first)) {
                           return true;
                        }
                     }
                  }
               }

               return false;
            }
         }
      } else {
         return false;
      }
   }

   public static String commandBody(String message) {
      if (!isAutismCommandMessage(message)) {
         return "";
      } else {
         String trimmed = message.trim();
         int prefixLength = effectivePrefix().length();
         return trimmed.length() <= prefixLength ? "" : trimmed.substring(prefixLength).trim();
      }
   }

   public static boolean dispatch(String body) {
      if (commandsBlockedByPanic()) {
         return true;
      } else if (body == null) {
         return false;
      } else {
         String trimmed = body.trim();
         if (trimmed.isEmpty()) {
            return false;
         } else if (DISABLED_ADDON_COMMAND_NAMES.contains(firstToken(trimmed).toLowerCase(Locale.ROOT))) {
            sendSyntaxError(trimmed, CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create());
            return true;
         } else {
            try {
               DISPATCHER.execute(trimmed, AutismCommandSource.INSTANCE);
               return true;
            } catch (CommandSyntaxException var3) {
               sendSyntaxError(trimmed, var3);
               return true;
            } catch (Exception var4) {
               autismclient.AutismClientAddon.LOG.warn("[Commands] dispatch failed for '{}'", trimmed, var4);
               AutismClientMessaging.sendPrefixed("§cCommand error: " + (var4.getMessage() == null ? var4.getClass().getSimpleName() : var4.getMessage()));
               return true;
            }
         }
      }
   }

   private static void sendSyntaxError(String body, CommandSyntaxException e) {
      String first = firstToken(body);
      Command command = find(first);
      String prefix = effectivePrefix();
      if (command == null) {
         AutismClientMessaging.sendPrefixed("§cUnknown AUTISM command: §f" + first);
         AutismClientMessaging.sendPrefixed("§7Use §f" + prefix + "commands §7or §f" + prefix + "help§7.");
      } else {
         String message = e.getMessage();
         if (message == null || message.isBlank()) {
            message = "Incomplete or invalid command.";
         }

         AutismClientMessaging.sendPrefixed("§c" + message);
         AutismClientMessaging.sendPrefixed("§7Use §f" + prefix + "help " + command.name() + "§7.");
      }
   }

   private static String firstToken(String body) {
      if (body == null) {
         return "";
      } else {
         String trimmed = body.trim();
         if (trimmed.isEmpty()) {
            return "";
         } else {
            int space = trimmed.indexOf(32);
            return space < 0 ? trimmed : trimmed.substring(0, space);
         }
      }
   }
}
