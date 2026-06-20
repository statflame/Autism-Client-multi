package autismclient.util;

import autismclient.modules.PackHideState;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class AutismCraftingHelper {
   private static Field recipeBookRecipesField;
   public static final long CRAFT_REFRESH_DEBOUNCE_MS = 350L;

   private AutismCraftingHelper() {
   }

   public static List<AutismCraftingHelper.CraftableRecipeOption> getCraftableRecipes(Minecraft mc) {
      if (mc != null && mc.player != null && mc.level != null) {
         AbstractCraftingMenu activeCrafting = mc.player.containerMenu instanceof AbstractCraftingMenu handler ? handler : null;
         BlockPos nearbyTablePos = findReachableCraftingTable(mc);
         List<AutismCraftingHelper.RecipeListingContext> contexts = new ArrayList<>();
         if (activeCrafting != null) {
            contexts.add(createActiveListingContext(mc, activeCrafting));
            if (isPlayerCraftingHandler(mc, activeCrafting)) {
               contexts.add(createVirtualTableContext(mc));
            }
         } else {
            contexts.add(createVirtualTableContext(mc));
         }

         if (contexts.isEmpty()) {
            return List.of();
         } else {
            Map<String, AutismCraftingHelper.CraftableRecipeOption> optionsByKey = new HashMap<>();
            List<AutismLocalCraftingRegistry.LocalCraftingRecipe> localRecipes = AutismLocalCraftingRegistry.getRecipes(mc);

            for (AutismCraftingHelper.RecipeListingContext context : contexts) {
               for (AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe : mergeLocalAndSyncedRecipes(localRecipes, context.syncedRecipes)) {
                  if (localRecipe.fits(context.gridWidth, context.gridHeight) && !localRecipe.result.isEmpty()) {
                     int maxCraftsByMaterials = computeMaxCraftsByMaterials(
                        localRecipe, context.gridWidth, context.gridHeight, context.inputStacks, mc.player.getInventory()
                     );
                     int maxCraftsByStorage = computeMaxCraftsByStorage(localRecipe, context.inputStacks, mc.player.getInventory(), maxCraftsByMaterials);
                     RecipeDisplayEntry syncedEntry = context.syncedBySignature.get(localRecipe.signature);
                     AutismCraftingHelper.CraftableRecipeOption option = new AutismCraftingHelper.CraftableRecipeOption(
                        localRecipe, syncedEntry, maxCraftsByMaterials, maxCraftsByStorage, context.source
                     );
                     mergeRecipeOption(optionsByKey, option);
                  }
               }
            }

            List<AutismCraftingHelper.CraftableRecipeOption> options = new ArrayList<>(optionsByKey.values());
            options.sort(
               Comparator.<AutismCraftingHelper.CraftableRecipeOption, Boolean>comparing(optionx -> !optionx.craftableNow)
                  .thenComparing(optionx -> optionx.craftSource != AutismCraftingHelper.CraftSource.PLAYER_2X2)
                  .thenComparing(optionx -> -optionx.maxCraftsNow)
                  .thenComparing(optionx -> optionx.label, String.CASE_INSENSITIVE_ORDER)
                  .thenComparing(optionx -> optionx.recipeKey, String.CASE_INSENSITIVE_ORDER)
            );
            return options;
         }
      } else {
         return List.of();
      }
   }

   private static AutismCraftingHelper.RecipeListingContext createActiveListingContext(Minecraft mc, AbstractCraftingMenu craftingHandler) {
      ContextMap params = SlotDisplayContext.fromLevel(mc.level);
      FeatureFlagSet enabledFeatures = getEnabledFeatures(mc);
      List<RecipeDisplayEntry> syncedEntries = getSyncedCraftingEntries(mc, craftingHandler.getGridWidth(), craftingHandler.getGridHeight());
      List<AutismLocalCraftingRegistry.LocalCraftingRecipe> syncedRecipes = buildSyncedLocalRecipes(syncedEntries, params, enabledFeatures);
      Map<String, RecipeDisplayEntry> syncedBySignature = buildSyncedSignatureMap(syncedEntries, params, enabledFeatures);
      return new AutismCraftingHelper.RecipeListingContext(
         isPlayerCraftingHandler(mc, craftingHandler) ? AutismCraftingHelper.CraftSource.PLAYER_2X2 : AutismCraftingHelper.CraftSource.TABLE_3X3,
         craftingHandler.getGridWidth(),
         craftingHandler.getGridHeight(),
         getInputStacks(craftingHandler),
         syncedBySignature,
         syncedRecipes
      );
   }

   private static AutismCraftingHelper.RecipeListingContext createVirtualTableContext(Minecraft mc) {
      ContextMap params = mc != null && mc.level != null ? SlotDisplayContext.fromLevel(mc.level) : null;
      FeatureFlagSet enabledFeatures = getEnabledFeatures(mc);
      List<RecipeDisplayEntry> syncedEntries = params == null ? List.of() : getSyncedCraftingEntries(mc, 3, 3);
      List<AutismLocalCraftingRegistry.LocalCraftingRecipe> syncedRecipes = params == null
         ? List.of()
         : buildSyncedLocalRecipes(syncedEntries, params, enabledFeatures);
      Map<String, RecipeDisplayEntry> syncedBySignature = params == null ? Map.of() : buildSyncedSignatureMap(syncedEntries, params, enabledFeatures);
      return new AutismCraftingHelper.RecipeListingContext(AutismCraftingHelper.CraftSource.TABLE_3X3, 3, 3, List.of(), syncedBySignature, syncedRecipes);
   }

   private static boolean isPlayerCraftingHandler(Minecraft mc, AbstractCraftingMenu craftingHandler) {
      return mc != null && mc.player != null && craftingHandler == mc.player.inventoryMenu;
   }

   private static FeatureFlagSet getEnabledFeatures(Minecraft mc) {
      if (mc == null) {
         return FeatureFlagSet.of();
      } else if (mc.getConnection() != null) {
         return mc.getConnection().enabledFeatures();
      } else {
         return mc.level != null ? mc.level.enabledFeatures() : FeatureFlagSet.of();
      }
   }

   private static List<ItemStack> getInputStacks(AbstractCraftingMenu craftingHandler) {
      List<ItemStack> inputStacks = new ArrayList<>();

      for (Slot slot : craftingHandler.getInputGridSlots()) {
         if (slot != null && !slot.getItem().isEmpty()) {
            inputStacks.add(slot.getItem().copy());
         }
      }

      return inputStacks;
   }

   private static void mergeRecipeOption(
      Map<String, AutismCraftingHelper.CraftableRecipeOption> optionsByKey, AutismCraftingHelper.CraftableRecipeOption candidate
   ) {
      if (candidate != null && candidate.recipeKey != null && !candidate.recipeKey.isBlank()) {
         String mergeKey = candidate.recipeKey;
         AutismCraftingHelper.CraftableRecipeOption existing = optionsByKey.get(mergeKey);
         if (existing == null) {
            optionsByKey.put(mergeKey, candidate);
         } else {
            boolean preferCandidate = false;
            if (candidate.result.getCount() != existing.result.getCount()) {
               preferCandidate = candidate.result.getCount() > existing.result.getCount();
            } else if (candidate.craftableNow != existing.craftableNow) {
               preferCandidate = candidate.craftableNow;
            } else if (candidate.craftSource != existing.craftSource) {
               preferCandidate = candidate.craftSource == AutismCraftingHelper.CraftSource.PLAYER_2X2;
            } else if (candidate.maxCraftsNow != existing.maxCraftsNow) {
               preferCandidate = candidate.maxCraftsNow > existing.maxCraftsNow;
            } else if (!existing.hasSyncedRecipe() && candidate.hasSyncedRecipe()) {
               preferCandidate = true;
            }

            if (preferCandidate) {
               optionsByKey.put(mergeKey, candidate);
            }
         }
      }
   }

   private static List<AutismLocalCraftingRegistry.LocalCraftingRecipe> mergeLocalAndSyncedRecipes(
      List<AutismLocalCraftingRegistry.LocalCraftingRecipe> localRecipes, List<AutismLocalCraftingRegistry.LocalCraftingRecipe> syncedRecipes
   ) {
      List<AutismLocalCraftingRegistry.LocalCraftingRecipe> merged = new ArrayList<>();
      Set<String> seenSignatures = new HashSet<>();
      if (localRecipes != null) {
         for (AutismLocalCraftingRegistry.LocalCraftingRecipe recipe : localRecipes) {
            if (recipe != null) {
               merged.add(recipe);
               if (recipe.signature != null && !recipe.signature.isBlank()) {
                  seenSignatures.add(recipe.signature);
               }
            }
         }
      }

      if (syncedRecipes != null) {
         for (AutismLocalCraftingRegistry.LocalCraftingRecipe recipex : syncedRecipes) {
            if (recipex != null && (recipex.signature == null || !seenSignatures.contains(recipex.signature))) {
               merged.add(recipex);
               if (recipex.signature != null && !recipex.signature.isBlank()) {
                  seenSignatures.add(recipex.signature);
               }
            }
         }
      }

      return merged;
   }

   public static AutismCraftingHelper.CraftableRecipeOption findCraftableRecipe(Minecraft mc, int recipeId) {
      for (AutismCraftingHelper.CraftableRecipeOption option : getCraftableRecipes(mc)) {
         if (option.recipeId == recipeId || option.syncedRecipeId == recipeId) {
            return option;
         }
      }

      return null;
   }

   public static AutismCraftingHelper.CraftableRecipeOption findCraftableRecipe(Minecraft mc, String recipeKey) {
      if (recipeKey != null && !recipeKey.isBlank()) {
         for (AutismCraftingHelper.CraftableRecipeOption option : getCraftableRecipes(mc)) {
            if (recipeKey.equalsIgnoreCase(option.recipeKey)) {
               return option;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static AutismCraftingHelper.CraftableRecipeOption findRecipeMatchingOutput(Minecraft mc, ItemStack output) {
      if (output != null && !output.isEmpty()) {
         AutismCraftingHelper.CraftableRecipeOption best = null;

         for (AutismCraftingHelper.CraftableRecipeOption option : getCraftableRecipes(mc)) {
            if (sameResult(option.result, output)) {
               if (best == null) {
                  best = option;
               } else if (option.craftableNow && !best.craftableNow) {
                  best = option;
               } else if (option.craftableNow == best.craftableNow && option.maxCraftsNow > best.maxCraftsNow) {
                  best = option;
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   public static ItemStack getCurrentCraftOutput(Minecraft mc) {
      if (mc != null && mc.player != null) {
         return mc.player.containerMenu instanceof AbstractCraftingMenu craftingHandler ? craftingHandler.getResultSlot().getItem().copy() : ItemStack.EMPTY;
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static int getRequiredCraftClicks(int outputPerCraft, int desiredAmount) {
      int safeOutput = Math.max(1, outputPerCraft);
      int safeDesired = Math.max(1, desiredAmount);
      return Math.max(1, (int)Math.ceil((double)safeDesired / safeOutput));
   }

   public static int getRoundedCraftOutput(int outputPerCraft, int desiredAmount) {
      int safeOutput = Math.max(1, outputPerCraft);
      return getRequiredCraftClicks(safeOutput, desiredAmount) * safeOutput;
   }

   public static int getRoundedCraftOutput(AutismCraftingHelper.CraftableRecipeOption option, int desiredAmount) {
      if (option == null) {
         return getRoundedCraftOutput(1, desiredAmount);
      } else {
         int desiredCrafts = Math.min(option.maxCraftsNow, getRequiredCraftClicks(option.result.getCount(), desiredAmount));
         return Math.max(0, desiredCrafts * Math.max(1, option.result.getCount()));
      }
   }

   public static int getEffectiveRequestedOutput(AutismCraftingHelper.CraftableRecipeOption option, int desiredAmount, boolean useMaxAmount) {
      if (option == null) {
         return useMaxAmount ? 0 : Math.max(1, desiredAmount);
      } else {
         return useMaxAmount ? option.maxOutputNow : Math.max(1, desiredAmount);
      }
   }

   public static String getAvailabilityLabel(AutismCraftingHelper.CraftableRecipeOption option) {
      if (option == null) {
         return "No recipe";
      } else if (option.craftableNow) {
         return "Crafts x" + option.maxCraftsNow;
      } else {
         return option.hasMaterialsNow && !option.hasInventoryRoomNow ? "No Room" : "Need Mats";
      }
   }

   public static List<Packet<?>> buildCraftSequence(Minecraft mc, int recipeId, int desiredAmount) {
      return buildCraftSequence(mc, null, recipeId, desiredAmount);
   }

   public static List<Packet<?>> buildCraftSequence(Minecraft mc, String recipeKey, int recipeId, int desiredAmount) {
      if (mc != null && mc.player != null && mc.level != null) {
         if (!(mc.player.containerMenu instanceof AbstractCraftingMenu craftingHandler)) {
            return List.of();
         } else {
            AutismCraftingHelper.CraftableRecipeOption option = resolveCraftOption(mc, recipeKey, recipeId);
            if (option != null && option.maxCraftsNow > 0 && option.hasSyncedRecipe()) {
               int remainingCrafts = Math.min(option.maxCraftsNow, getRequiredCraftClicks(option.result.getCount(), desiredAmount));
               if (remainingCrafts <= 0) {
                  return List.of();
               } else {
                  AutismCraftingHelper.CraftPhasePlan phasePlan = createSyncedCraftPhasePlan(mc, option, craftingHandler);
                  if (phasePlan.maxCraftsPerPhase <= 0) {
                     return List.of();
                  } else {
                     List<Packet<?>> packets = new ArrayList<>();
                     int availableCraftsLeft = phasePlan.totalCraftsAvailable;

                     while (remainingCrafts > 0) {
                        int phaseCrafts = Math.min(remainingCrafts, phasePlan.maxCraftsPerPhase);
                        boolean useCraftAll = shouldUseCraftAll(phaseCrafts, availableCraftsLeft, phasePlan.maxCraftsPerPhase);
                        int fillRequests = useCraftAll ? 1 : phaseCrafts;

                        for (int i = 0; i < fillRequests; i++) {
                           packets.add(new ServerboundPlaceRecipePacket(craftingHandler.containerId, option.syncedEntry.id(), useCraftAll));
                        }

                        ServerboundContainerClickPacket click = autismclient.util.AutismPacketCompat.click(
                           craftingHandler.containerId,
                           craftingHandler.getStateId(),
                           (short)craftingHandler.getResultSlot().index,
                           (byte)0,
                           ClickType.QUICK_MOVE
                        );
                        packets.add(PacketRegenerator.regenerate(click));
                        remainingCrafts -= phaseCrafts;
                        availableCraftsLeft = Math.max(0, availableCraftsLeft - phaseCrafts);
                     }

                     return packets;
                  }
               }
            } else {
               return List.of();
            }
         }
      } else {
         return List.of();
      }
   }

   public static AutismCraftingHelper.CraftExecutionResult executeCraftImmediately(Minecraft mc, int recipeId, int desiredAmount) {
      return executeCraftImmediately(mc, null, recipeId, desiredAmount);
   }

   public static AutismCraftingHelper.CraftExecutionResult executeCraftImmediately(Minecraft mc, String recipeKey, int recipeId, int desiredAmount) {
      if (mc != null && mc.player != null && mc.getConnection() != null) {
         AutismCraftingHelper.CraftableRecipeOption requestedOption = resolveCraftOption(mc, recipeKey, recipeId);
         if (requestedOption == null) {
            return AutismCraftingHelper.CraftExecutionResult.failure("That recipe is not available right now.");
         } else if (requestedOption.craftableNow && requestedOption.maxCraftsNow > 0) {
            AutismCraftingHelper.PreparedCraftContext preparedContext = prepareCraftExecution(mc, requestedOption);
            if (preparedContext != null && preparedContext.handler != null) {
               try {
                  AutismCraftingHelper.CraftableRecipeOption option = resolveCraftOption(mc, recipeKey, recipeId);
                  if (option == null) {
                     option = requestedOption;
                  }

                  if (!option.craftableNow || option.maxCraftsNow <= 0) {
                     return AutismCraftingHelper.CraftExecutionResult.failure("Missing materials for " + option.label + ".");
                  } else {
                     if (option.hasSyncedRecipe()) {
                        AutismCraftingHelper.CraftExecutionResult fastResult = executeCraftViaSyncedRecipe(mc, preparedContext.handler, option, desiredAmount);
                        if (fastResult.success || fastResult.craftedAmount > 0) {
                           return fastResult;
                        }
                     }

                     return executeCraftManually(mc, preparedContext.handler, option, desiredAmount);
                  }
               } finally {
                  finishPreparedCraftContext(mc, preparedContext);
               }
            } else {
               return requestedOption.craftSource == AutismCraftingHelper.CraftSource.TABLE_3X3
                  ? AutismCraftingHelper.CraftExecutionResult.failure("No reachable crafting table is available.")
                  : AutismCraftingHelper.CraftExecutionResult.failure("Open your inventory or a crafting screen first.");
            }
         } else {
            return AutismCraftingHelper.CraftExecutionResult.failure("Missing materials for " + requestedOption.label + ".");
         }
      } else {
         return AutismCraftingHelper.CraftExecutionResult.failure("No network connection.");
      }
   }

   private static AutismCraftingHelper.CraftableRecipeOption resolveCraftOption(Minecraft mc, String recipeKey, int recipeId) {
      AutismCraftingHelper.CraftableRecipeOption byKey = recipeKey != null && !recipeKey.isBlank() ? findCraftableRecipe(mc, recipeKey) : null;
      return byKey != null ? byKey : findCraftableRecipe(mc, recipeId);
   }

   private static AutismCraftingHelper.PreparedCraftContext prepareCraftExecution(Minecraft mc, AutismCraftingHelper.CraftableRecipeOption option) {
      if (mc != null && mc.player != null && option != null) {
         if (mc.player.containerMenu instanceof AbstractCraftingMenu activeHandler
            && option.localRecipe.fits(activeHandler.getGridWidth(), activeHandler.getGridHeight())) {
            return new AutismCraftingHelper.PreparedCraftContext(activeHandler, false, null);
         } else if (option.craftSource == AutismCraftingHelper.CraftSource.PLAYER_2X2 && mc.player.containerMenu == mc.player.inventoryMenu) {
            return new AutismCraftingHelper.PreparedCraftContext(mc.player.inventoryMenu, false, null);
         } else {
            BlockPos tablePos = findReachableCraftingTable(mc);
            if (tablePos == null) {
               return null;
            } else {
               Screen restoreScreen = mc.player.containerMenu == mc.player.inventoryMenu ? mc.screen : null;
               if (!openCraftingTable(mc, tablePos)) {
                  return null;
               } else {
                  AbstractCraftingMenu openedHandler = waitForCraftingHandler(mc, 3, 3, 1500L);
                  return openedHandler == null ? null : new AutismCraftingHelper.PreparedCraftContext(openedHandler, true, restoreScreen);
               }
            }
         }
      } else {
         return null;
      }
   }

   private static void finishPreparedCraftContext(Minecraft mc, AutismCraftingHelper.PreparedCraftContext context) {
      if (mc != null && mc.player != null && context != null && context.closeAfter) {
         runClientTask(mc, () -> {
            if (mc.player != null) {
               if (mc.player.containerMenu != mc.player.inventoryMenu) {
                  mc.player.closeContainer();
               }

               if (context.restoreScreen != null) {
                  mc.setScreen(context.restoreScreen);
                  mc.player.containerMenu = mc.player.inventoryMenu;
               }
            }
         });
      }
   }

   private static boolean openCraftingTable(Minecraft mc, BlockPos tablePos) {
      if (mc != null && mc.player != null && mc.gameMode != null && tablePos != null) {
         BlockHitResult hitResult = createCraftingTableHitResult(mc, tablePos);
         return hitResult == null ? false : runClientTask(mc, () -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult));
      } else {
         return false;
      }
   }

   private static AbstractCraftingMenu waitForCraftingHandler(Minecraft mc, int requiredWidth, int requiredHeight, long timeoutMs) {
      long deadline = System.nanoTime() + timeoutMs * 1000000L;

      while (System.nanoTime() < deadline) {
         if (mc.player != null
            && mc.player.containerMenu instanceof AbstractCraftingMenu craftingHandler
            && craftingHandler.getGridWidth() >= requiredWidth
            && craftingHandler.getGridHeight() >= requiredHeight
            && craftingHandler.getStateId() > 0) {
            return craftingHandler;
         }

         try {
            Thread.sleep(5L);
         } catch (InterruptedException var9) {
            Thread.currentThread().interrupt();
            return null;
         }
      }

      return null;
   }

   private static BlockPos findReachableCraftingTable(Minecraft mc) {
      if (mc != null && mc.player != null && mc.level != null) {
         double reach = Math.max(4.5, mc.player.blockInteractionRange());
         double reachSq = reach * reach;
         int radius = (int)Math.ceil(reach);
         BlockPos origin = mc.player.blockPosition();
         BlockPos bestPos = null;
         double bestDistanceSq = Double.MAX_VALUE;

         for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            if (mc.level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
               Vec3 center = Vec3.atCenterOf(pos);
               double distanceSq = mc.player.distanceToSqr(center);
               if (!(distanceSq > reachSq) && !(distanceSq >= bestDistanceSq)) {
                  BlockHitResult hitResult = createCraftingTableHitResult(mc, pos);
                  if (hitResult != null) {
                     bestPos = pos.immutable();
                     bestDistanceSq = distanceSq;
                  }
               }
            }
         }

         return bestPos;
      } else {
         return null;
      }
   }

   private static BlockHitResult createCraftingTableHitResult(Minecraft mc, BlockPos tablePos) {
      if (mc != null && mc.player != null && mc.level != null && tablePos != null) {
         Vec3 center = Vec3.atCenterOf(tablePos);
         double reach = Math.max(4.5, mc.player.blockInteractionRange());
         if (mc.player.distanceToSqr(center) > reach * reach) {
            return null;
         } else {
            ClipContext context = new ClipContext(mc.player.getEyePosition(), center, Block.OUTLINE, Fluid.NONE, mc.player);
            BlockHitResult result = mc.level.clip(context);
            return result.getType() == Type.BLOCK && result.getBlockPos().equals(tablePos) ? result : new BlockHitResult(center, Direction.UP, tablePos, false);
         }
      } else {
         return null;
      }
   }

   private static AutismCraftingHelper.CraftExecutionResult executeCraftViaSyncedRecipe(
      Minecraft mc, AbstractCraftingMenu craftingHandler, AutismCraftingHelper.CraftableRecipeOption option, int desiredAmount
   ) {
      if (mc.gameMode == null) {
         return AutismCraftingHelper.CraftExecutionResult.failure("Crafting interaction manager is unavailable.");
      } else {
         int safeDesiredAmount = Math.max(1, desiredAmount);
         int outputPerCraft = Math.max(1, option.result.getCount());
         int desiredCrafts = Math.min(option.maxCraftsNow, getRequiredCraftClicks(outputPerCraft, safeDesiredAmount));
         int roundedTarget = Math.min(option.maxOutputNow, desiredCrafts * outputPerCraft);
         int syncId = craftingHandler.containerId;
         int outputSlotId = craftingHandler.getResultSlot().index;
         int beforeCount = countInventoryItems(mc, option.result);
         AtomicInteger remainingCrafts = new AtomicInteger(desiredCrafts);
         AtomicInteger dispatchedCrafts = new AtomicInteger(0);
         if (!runClientTask(mc, () -> {
            if (mc.player != null && mc.gameMode != null) {
               if (mc.player.containerMenu instanceof AbstractCraftingMenu currentHandler) {
                  if (currentHandler.containerId == syncId) {
                     while (remainingCrafts.get() > 0) {
                        AutismCraftingHelper.CraftableRecipeOption currentOption = resolveCraftOption(mc, option.recipeKey, option.recipeId);
                        if (currentOption == null || currentOption.maxCraftsNow <= 0 || !currentOption.hasSyncedRecipe()) {
                           break;
                        }

                        AutismCraftingHelper.CraftPhasePlan phasePlan = createSyncedCraftPhasePlan(mc, currentOption, currentHandler);
                        if (phasePlan.maxCraftsPerPhase <= 0) {
                           break;
                        }

                        int phaseCrafts = Math.min(remainingCrafts.get(), Math.min(currentOption.maxCraftsNow, phasePlan.maxCraftsPerPhase));
                        boolean useCraftAll = shouldUseCraftAll(phaseCrafts, currentOption.maxCraftsNow, phasePlan.maxCraftsPerPhase);
                        int fillRequests = useCraftAll ? 1 : phaseCrafts;

                        for (int i = 0; i < fillRequests; i++) {
                           mc.gameMode.handlePlaceRecipe(syncId, currentOption.syncedEntry.id(), useCraftAll);
                        }

                        mc.gameMode.handleInventoryMouseClick(syncId, outputSlotId, 0, ClickType.QUICK_MOVE, mc.player);
                        dispatchedCrafts.addAndGet(phaseCrafts);
                        remainingCrafts.addAndGet(-phaseCrafts);
                     }
                  }
               }
            }
         })) {
            return AutismCraftingHelper.CraftExecutionResult.failure("Crafting interaction manager is unavailable.");
         } else {
            int expectedCount = beforeCount + Math.min(roundedTarget, dispatchedCrafts.get() * outputPerCraft);
            waitForInventoryCount(mc, option.result, expectedCount, 700L);
            int craftedAmount = Math.max(0, countInventoryItems(mc, option.result) - beforeCount);
            return craftedAmount <= 0
               ? AutismCraftingHelper.CraftExecutionResult.failure("Craft failed or your inventory had no room.")
               : buildCraftResult(option, craftedAmount, roundedTarget);
         }
      }
   }

   private static AutismCraftingHelper.CraftExecutionResult executeCraftManually(
      Minecraft mc, AbstractCraftingMenu craftingHandler, AutismCraftingHelper.CraftableRecipeOption option, int desiredAmount
   ) {
      int outputPerCraft = Math.max(1, option.result.getCount());
      int desiredCrafts = Math.min(option.maxCraftsNow, getRequiredCraftClicks(outputPerCraft, desiredAmount));
      if (desiredCrafts <= 0) {
         return AutismCraftingHelper.CraftExecutionResult.failure("Missing materials for " + option.label + ".");
      } else {
         int beforeCount = countInventoryItems(mc, option.result);
         int craftedCrafts = 0;
         int syncId = craftingHandler.containerId;
         int outputSlotId = craftingHandler.getResultSlot().index;
         int roundedTarget = Math.min(option.maxOutputNow, desiredCrafts * outputPerCraft);

         while (craftedCrafts < desiredCrafts) {
            AutismCraftingHelper.CraftableRecipeOption currentOption = resolveCraftOption(mc, option.recipeKey, option.recipeId);
            if (currentOption == null || currentOption.maxCraftsNow <= 0) {
               break;
            }

            int phaseLimit = computeManualMaxCraftsPerPhase(currentOption.localRecipe, craftingHandler, mc.player.getInventory());
            if (phaseLimit <= 0) {
               break;
            }

            int phaseCrafts = Math.min(desiredCrafts - craftedCrafts, Math.min(currentOption.maxCraftsNow, phaseLimit));
            if (!clearCraftGrid(mc, craftingHandler, syncId)
               || !placeRecipeCrafts(mc, craftingHandler, currentOption.localRecipe, syncId, phaseCrafts)
               || !waitForOutput(mc, syncId, currentOption.result, 400L)
               || !runClientTask(mc, () -> {
                  if (mc.player != null && mc.gameMode != null) {
                     if (mc.player.containerMenu instanceof AbstractCraftingMenu currentHandler) {
                        if (currentHandler.containerId == syncId) {
                           mc.gameMode.handleInventoryMouseClick(syncId, outputSlotId, 0, ClickType.QUICK_MOVE, mc.player);
                        }
                     }
                  }
               })) {
               break;
            }

            craftedCrafts += phaseCrafts;
            waitForInventoryCount(mc, option.result, beforeCount + craftedCrafts * outputPerCraft, 500L);
         }

         int craftedAmount = Math.max(0, countInventoryItems(mc, option.result) - beforeCount);
         return craftedAmount <= 0
            ? AutismCraftingHelper.CraftExecutionResult.failure("Craft failed or your inventory had no room.")
            : buildCraftResult(option, craftedAmount, roundedTarget);
      }
   }

   private static boolean clearCraftGrid(Minecraft mc, AbstractCraftingMenu craftingHandler, int syncId) {
      for (Slot inputSlot : craftingHandler.getInputGridSlots()) {
         if (inputSlot != null && !inputSlot.getItem().isEmpty() && !runClientTask(mc, () -> {
            if (mc.player != null && mc.gameMode != null) {
               if (mc.player.containerMenu instanceof AbstractCraftingMenu currentHandler) {
                  if (currentHandler.containerId == syncId) {
                     mc.gameMode.handleInventoryMouseClick(syncId, inputSlot.index, 0, ClickType.QUICK_MOVE, mc.player);
                  }
               }
            }
         })) {
            return false;
         }
      }

      return waitForInputGridClear(mc, syncId, 300L);
   }

   private static boolean placeRecipeOnce(
      Minecraft mc, AbstractCraftingMenu craftingHandler, AutismLocalCraftingRegistry.LocalCraftingRecipe recipe, int syncId
   ) {
      return placeRecipeCrafts(mc, craftingHandler, recipe, syncId, 1);
   }

   private static boolean placeRecipeCrafts(
      Minecraft mc, AbstractCraftingMenu craftingHandler, AutismLocalCraftingRegistry.LocalCraftingRecipe recipe, int syncId, int craftCount
   ) {
      if (mc.player == null) {
         return false;
      } else {
         int requiredPerSlot = Math.max(1, craftCount);
         List<Slot> inputSlots = craftingHandler.getInputGridSlots();
         if (recipe.shaped) {
            for (int row = 0; row < recipe.height; row++) {
               for (int col = 0; col < recipe.width; col++) {
                  int recipeIndex = row * recipe.width + col;
                  if (recipeIndex >= 0 && recipeIndex < recipe.gridIngredients.size()) {
                     Ingredient ingredient = recipe.gridIngredients.get(recipeIndex);
                     if (ingredient != null && !ingredient.isEmpty()) {
                        int gridIndex = row * craftingHandler.getGridWidth() + col;
                        if (gridIndex < 0 || gridIndex >= inputSlots.size()) {
                           return false;
                        }

                        if (!placeIngredientCountIntoSlot(mc, craftingHandler, syncId, inputSlots.get(gridIndex).index, ingredient, requiredPerSlot)) {
                           return false;
                        }
                     }
                  }
               }
            }

            return true;
         } else {
            for (int i = 0; i < recipe.requirements.size(); i++) {
               if (i < 0 || i >= inputSlots.size()) {
                  return false;
               }

               Ingredient ingredient = recipe.requirements.get(i);
               if (ingredient != null
                  && !ingredient.isEmpty()
                  && !placeIngredientCountIntoSlot(mc, craftingHandler, syncId, inputSlots.get(i).index, ingredient, requiredPerSlot)) {
                  return false;
               }
            }

            return true;
         }
      }
   }

   private static boolean placeIngredientIntoSlot(Minecraft mc, AbstractCraftingMenu craftingHandler, int syncId, int targetSlotId, Ingredient ingredient) {
      return placeIngredientCountIntoSlot(mc, craftingHandler, syncId, targetSlotId, ingredient, 1);
   }

   private static boolean placeIngredientCountIntoSlot(
      Minecraft mc, AbstractCraftingMenu craftingHandler, int syncId, int targetSlotId, Ingredient ingredient, int requiredCount
   ) {
      if (mc.player != null && mc.gameMode != null) {
         int remaining = Math.max(1, requiredCount);

         while (remaining > 0) {
            Integer sourceSlotId = findBestMatchingInventorySlot(craftingHandler, mc.player.getInventory(), ingredient, remaining);
            if (sourceSlotId == null) {
               return false;
            }

            Slot sourceSlot = craftingHandler.getSlot(sourceSlotId);
            if (sourceSlot == null || sourceSlot.getItem().isEmpty()) {
               return false;
            }

            int sourceCount = sourceSlot.getItem().getCount();
            int placeCount = Math.min(sourceCount, remaining);
            if (!runClientTask(mc, () -> {
               clickSlot(mc, syncId, sourceSlotId, 0, ClickType.PICKUP);
               if (placeCount >= sourceCount) {
                  clickSlot(mc, syncId, targetSlotId, 0, ClickType.PICKUP);
               } else {
                  for (int i = 0; i < placeCount; i++) {
                     clickSlot(mc, syncId, targetSlotId, 1, ClickType.PICKUP);
                  }

                  clickSlot(mc, syncId, sourceSlotId, 0, ClickType.PICKUP);
               }
            })) {
               return false;
            }

            remaining -= placeCount;
         }

         return waitForTargetIngredientCount(mc, syncId, targetSlotId, ingredient, requiredCount, 300L);
      } else {
         return false;
      }
   }

   private static void clickSlot(Minecraft mc, int syncId, int slotId, int button, ClickType actionType) {
      if (!PackHideState.isHardLocked()) {
         if (mc.player != null && mc.gameMode != null) {
            mc.gameMode.handleInventoryMouseClick(syncId, slotId, button, actionType, mc.player);
         }
      }
   }

   private static Integer findMatchingInventorySlot(AbstractCraftingMenu craftingHandler, Inventory inventory, Ingredient ingredient) {
      Integer exactOne = null;
      Integer fallback = null;
      int fallbackCount = Integer.MAX_VALUE;

      for (Slot slot : craftingHandler.slots) {
         if (slot != null && slot.container == inventory && !slot.getItem().isEmpty()) {
            ItemStack stack = slot.getItem();
            if (ingredient.test(stack)) {
               if (stack.getCount() == 1) {
                  exactOne = slot.index;
                  break;
               }

               if (stack.getCount() < fallbackCount) {
                  fallback = slot.index;
                  fallbackCount = stack.getCount();
               }
            }
         }
      }

      return exactOne != null ? exactOne : fallback;
   }

   private static Integer findBestMatchingInventorySlot(AbstractCraftingMenu craftingHandler, Inventory inventory, Ingredient ingredient, int desiredCount) {
      Integer exact = null;
      Integer bestWhole = null;
      int bestWholeCount = -1;
      Integer bestPartial = null;
      int bestPartialCount = Integer.MAX_VALUE;

      for (Slot slot : craftingHandler.slots) {
         if (slot != null && slot.container == inventory && !slot.getItem().isEmpty()) {
            ItemStack stack = slot.getItem();
            if (ingredient.test(stack)) {
               int count = stack.getCount();
               if (count == desiredCount) {
                  exact = slot.index;
                  break;
               }

               if (count < desiredCount && count > bestWholeCount) {
                  bestWhole = slot.index;
                  bestWholeCount = count;
               } else if (count > desiredCount && count < bestPartialCount) {
                  bestPartial = slot.index;
                  bestPartialCount = count;
               }
            }
         }
      }

      if (exact != null) {
         return exact;
      } else if (bestWhole != null) {
         return bestWhole;
      } else {
         return bestPartial != null ? bestPartial : findMatchingInventorySlot(craftingHandler, inventory, ingredient);
      }
   }

   private static boolean waitForInputGridClear(Minecraft mc, int syncId, long timeoutMs) {
      long deadline = System.nanoTime() + timeoutMs * 1000000L;

      while (System.nanoTime() < deadline) {
         if (mc.player != null && mc.player.containerMenu instanceof AbstractCraftingMenu craftingHandler) {
            if (craftingHandler.containerId != syncId) {
               return false;
            }

            boolean anyInput = false;

            for (Slot inputSlot : craftingHandler.getInputGridSlots()) {
               if (inputSlot != null && !inputSlot.getItem().isEmpty()) {
                  anyInput = true;
                  break;
               }
            }

            if (!anyInput) {
               return true;
            }
         }

         try {
            Thread.sleep(5L);
         } catch (InterruptedException var10) {
            Thread.currentThread().interrupt();
            return false;
         }
      }

      return false;
   }

   private static boolean waitForTargetIngredient(Minecraft mc, int syncId, int targetSlotId, Ingredient ingredient, long timeoutMs) {
      return waitForTargetIngredientCount(mc, syncId, targetSlotId, ingredient, 1, timeoutMs);
   }

   private static boolean waitForTargetIngredientCount(Minecraft mc, int syncId, int targetSlotId, Ingredient ingredient, int requiredCount, long timeoutMs) {
      long deadline = System.nanoTime() + timeoutMs * 1000000L;

      while (System.nanoTime() < deadline) {
         if (mc.player != null) {
            AbstractContainerMenu slot = mc.player.containerMenu;
            if (slot instanceof AbstractContainerMenu) {
               if (slot.containerId != syncId) {
                  return false;
               }

               Slot slotx = slot.getSlot(targetSlotId);
               if (slotx != null) {
                  ItemStack stack = slotx.getItem();
                  if (stack != null && !stack.isEmpty() && ingredient.test(stack) && stack.getCount() >= requiredCount) {
                     return true;
                  }
               }
            }
         }

         try {
            Thread.sleep(5L);
         } catch (InterruptedException var12) {
            Thread.currentThread().interrupt();
            return false;
         }
      }

      return false;
   }

   private static boolean waitForOutput(Minecraft mc, int syncId, ItemStack expectedResult, long timeoutMs) {
      long deadline = System.nanoTime() + timeoutMs * 1000000L;

      while (System.nanoTime() < deadline) {
         if (mc.player != null && mc.player.containerMenu instanceof AbstractCraftingMenu craftingHandler) {
            if (craftingHandler.containerId != syncId) {
               return false;
            }

            ItemStack output = craftingHandler.getResultSlot().getItem();
            if (!output.isEmpty() && output.getItem() == expectedResult.getItem()) {
               return true;
            }
         }

         try {
            Thread.sleep(5L);
         } catch (InterruptedException var9) {
            Thread.currentThread().interrupt();
            return false;
         }
      }

      return false;
   }

   private static Map<String, RecipeDisplayEntry> buildSyncedSignatureMap(List<RecipeDisplayEntry> entries, ContextMap params, FeatureFlagSet enabledFeatures) {
      Map<String, RecipeDisplayEntry> signatures = new HashMap<>();

      for (RecipeDisplayEntry entry : entries) {
         String signature = buildSyncedSignature(entry, params, enabledFeatures);
         if (signature != null && !signatures.containsKey(signature)) {
            signatures.put(signature, entry);
         }
      }

      return signatures;
   }

   private static List<AutismLocalCraftingRegistry.LocalCraftingRecipe> buildSyncedLocalRecipes(
      List<RecipeDisplayEntry> entries, ContextMap params, FeatureFlagSet enabledFeatures
   ) {
      List<AutismLocalCraftingRegistry.LocalCraftingRecipe> recipes = new ArrayList<>();
      if (entries != null && params != null) {
         Set<String> seenKeys = new HashSet<>();

         for (RecipeDisplayEntry entry : entries) {
            if (entry != null && entry.display() != null) {
               String recipeKey = "synced:" + entry.id().index();
               AutismLocalCraftingRegistry.LocalCraftingRecipe recipe = AutismLocalCraftingRegistry.createFromSyncedDisplay(
                  recipeKey, entry.display(), entry.craftingRequirements(), enabledFeatures, params
               );
               if (recipe != null && seenKeys.add(recipe.recipeKey)) {
                  recipes.add(recipe);
               }
            }
         }

         return recipes;
      } else {
         return recipes;
      }
   }

   private static String buildSyncedSignature(RecipeDisplayEntry entry, ContextMap params, FeatureFlagSet enabledFeatures) {
      if (entry != null && entry.display() != null) {
         AutismLocalCraftingRegistry.LocalCraftingRecipe recipe = AutismLocalCraftingRegistry.createFromSyncedDisplay(
            "synced:" + entry.id().index(), entry.display(), entry.craftingRequirements(), enabledFeatures, params
         );
         return recipe == null ? null : recipe.signature;
      } else {
         return null;
      }
   }

   private static String signatureForIngredient(Ingredient ingredient, ContextMap params) {
      return ingredient != null && !ingredient.isEmpty() ? signatureForStacks(ingredient.display().resolveForStacks(params)) : "_";
   }

   private static String signatureForStacks(List<ItemStack> stacks) {
      if (stacks != null && !stacks.isEmpty()) {
         Set<String> ids = new HashSet<>();

         for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty() && stack.getItem() != null) {
               Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
               if (id != null) {
                  ids.add(id.toString());
               }
            }
         }

         if (ids.isEmpty()) {
            return "_";
         } else {
            List<String> ordered = new ArrayList<>(ids);
            ordered.sort(String.CASE_INSENSITIVE_ORDER);
            return String.join(",", ordered);
         }
      } else {
         return "_";
      }
   }

   private static int computeMaxCraftsByMaterials(
      AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe, AbstractCraftingMenu craftingHandler, Inventory playerInventory
   ) {
      return computeMaxCraftsByMaterials(
         localRecipe,
         craftingHandler == null ? 0 : craftingHandler.getGridWidth(),
         craftingHandler == null ? 0 : craftingHandler.getGridHeight(),
         getInputStacks(craftingHandler),
         playerInventory
      );
   }

   private static int computeMaxCraftsByMaterials(
      AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe, int gridWidth, int gridHeight, List<ItemStack> inputStacks, Inventory playerInventory
   ) {
      if (localRecipe == null || localRecipe.requirements.isEmpty()) {
         return 0;
      } else if (!localRecipe.fits(gridWidth, gridHeight)) {
         return 0;
      } else {
         StackedItemContents matcher = new StackedItemContents();
         addInventoryToMatcher(matcher, playerInventory);
         addInputStacksToMatcher(matcher, inputStacks);
         return getMaximumCrafts(matcher, localRecipe.requirements);
      }
   }

   private static int getMaximumCrafts(StackedItemContents matcher, List<Ingredient> requirements) {
      if (matcher != null && requirements != null && !requirements.isEmpty()) {
         return matcher.canCraft(requirements, null) ? 64 : 0;
      } else {
         return 0;
      }
   }

   private static int computeMaxCraftsByStorage(
      AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe, List<ItemStack> inputStacks, Inventory playerInventory, int maxCraftsByMaterials
   ) {
      if (localRecipe != null && !localRecipe.result.isEmpty() && playerInventory != null) {
         int craftLimit = Math.max(0, maxCraftsByMaterials);
         if (craftLimit <= 0) {
            return 0;
         } else {
            List<ItemStack> inventorySlots = new ArrayList<>(36);
            int playerSlotCount = Math.min(36, playerInventory.getContainerSize());

            for (int i = 0; i < playerSlotCount; i++) {
               ItemStack stack = playerInventory.getItem(i);
               inventorySlots.add(stack == null ? ItemStack.EMPTY : stack.copy());
            }

            List<ItemStack> gridPools = new ArrayList<>();
            if (inputStacks != null) {
               for (ItemStack stack : inputStacks) {
                  if (stack != null && !stack.isEmpty()) {
                     gridPools.add(stack.copy());
                  }
               }
            }

            int crafted = 0;

            while (
               crafted < craftLimit && consumeOneCraftFromPools(localRecipe, inventorySlots, gridPools) && storeCraftResult(localRecipe.result, inventorySlots)
            ) {
               crafted++;
            }

            return crafted;
         }
      } else {
         return 0;
      }
   }

   private static boolean consumeOneCraftFromPools(
      AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe, List<ItemStack> inventorySlots, List<ItemStack> gridPools
   ) {
      if (localRecipe != null && !localRecipe.requirements.isEmpty()) {
         for (Ingredient ingredient : localRecipe.requirements) {
            if (ingredient != null
               && !ingredient.isEmpty()
               && !consumeMatchingIngredient(inventorySlots, ingredient, true)
               && !consumeMatchingIngredient(gridPools, ingredient, false)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static boolean consumeMatchingIngredient(List<ItemStack> stacks, Ingredient ingredient, boolean preferSmallestStack) {
      if (stacks != null && !stacks.isEmpty() && ingredient != null && !ingredient.isEmpty()) {
         int bestIndex = -1;
         int bestCount = Integer.MAX_VALUE;

         for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack != null && !stack.isEmpty() && ingredient.test(stack)) {
               int count = stack.getCount();
               if (bestIndex < 0 || preferSmallestStack && count < bestCount) {
                  bestIndex = i;
                  bestCount = count;
                  if (preferSmallestStack && count <= 1) {
                     break;
                  }
               }
            }
         }

         if (bestIndex < 0) {
            return false;
         } else {
            ItemStack chosen = stacks.get(bestIndex);
            chosen.shrink(1);
            if (chosen.isEmpty()) {
               stacks.set(bestIndex, ItemStack.EMPTY);
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private static boolean storeCraftResult(ItemStack resultStack, List<ItemStack> inventorySlots) {
      if (resultStack != null && !resultStack.isEmpty() && inventorySlots != null) {
         int remaining = resultStack.getCount();
         int maxStackSize = Math.max(1, resultStack.getMaxStackSize());

         for (int i = 0; i < inventorySlots.size() && remaining > 0; i++) {
            ItemStack existing = inventorySlots.get(i);
            if (existing != null && !existing.isEmpty() && existing.getItem() == resultStack.getItem()) {
               int existingMax = Math.max(1, Math.min(existing.getMaxStackSize(), maxStackSize));
               if (existing.getCount() < existingMax) {
                  int add = Math.min(existingMax - existing.getCount(), remaining);
                  existing.grow(add);
                  remaining -= add;
               }
            }
         }

         for (int ix = 0; ix < inventorySlots.size() && remaining > 0; ix++) {
            ItemStack existing = inventorySlots.get(ix);
            if (existing == null || existing.isEmpty()) {
               int add = Math.min(maxStackSize, remaining);
               ItemStack placed = resultStack.copy();
               placed.setCount(add);
               inventorySlots.set(ix, placed);
               remaining -= add;
            }
         }

         return remaining <= 0;
      } else {
         return false;
      }
   }

   private static int computeManualMaxCraftsPerPhase(
      AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe, AbstractCraftingMenu craftingHandler, Inventory playerInventory
   ) {
      if (localRecipe != null && playerInventory != null) {
         int maxCrafts = Integer.MAX_VALUE;

         for (Ingredient ingredient : localRecipe.shaped ? localRecipe.gridIngredients : localRecipe.requirements) {
            if (ingredient != null && !ingredient.isEmpty()) {
               int slotLimit = findMaxIngredientStackSize(craftingHandler, playerInventory, ingredient);
               if (slotLimit <= 0) {
                  return 0;
               }

               maxCrafts = Math.min(maxCrafts, slotLimit);
            }
         }

         return maxCrafts == Integer.MAX_VALUE ? 0 : Math.max(1, maxCrafts);
      } else {
         return 0;
      }
   }

   private static void addInventoryToMatcher(StackedItemContents matcher, Inventory playerInventory) {
      if (matcher != null && playerInventory != null) {
         for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            ItemStack stack = playerInventory.getItem(i);
            if (stack != null && !stack.isEmpty()) {
               matcher.accountStack(stack);
            }
         }
      }
   }

   private static int findMaxIngredientStackSize(AbstractCraftingMenu craftingHandler, Inventory playerInventory, Ingredient ingredient) {
      int best = 0;

      for (Slot slot : craftingHandler.slots) {
         if (slot != null && slot.container == playerInventory && !slot.getItem().isEmpty()) {
            ItemStack stack = slot.getItem();
            if (ingredient.test(stack)) {
               best = Math.max(best, Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack)));
            }
         }
      }

      return best;
   }

   private static void addInputGridToMatcher(StackedItemContents matcher, AbstractCraftingMenu craftingHandler) {
      if (matcher != null && craftingHandler != null) {
         for (Slot slot : craftingHandler.getInputGridSlots()) {
            if (slot != null) {
               ItemStack stack = slot.getItem();
               if (stack != null && !stack.isEmpty()) {
                  matcher.accountStack(stack);
               }
            }
         }
      }
   }

   private static void addInputStacksToMatcher(StackedItemContents matcher, List<ItemStack> inputStacks) {
      if (matcher != null && inputStacks != null) {
         for (ItemStack stack : inputStacks) {
            if (stack != null && !stack.isEmpty()) {
               matcher.accountStack(stack);
            }
         }
      }
   }

   private static List<RecipeDisplayEntry> getSyncedCraftingEntries(Minecraft mc, int gridWidth, int gridHeight) {
      List<RecipeDisplayEntry> entries = new ArrayList<>();

      for (RecipeDisplayEntry entry : getRawSyncedRecipeEntries(mc)) {
         if (isSupportedCraftingEntry(entry, gridWidth, gridHeight)) {
            entries.add(entry);
         }
      }

      return entries;
   }

   private static Collection<RecipeDisplayEntry> getRawSyncedRecipeEntries(Minecraft mc) {
      if (mc != null && mc.player != null) {
         ClientRecipeBook fallback = mc.player.getRecipeBook();
         if (fallback instanceof ClientRecipeBook) {
            ClientRecipeBook recipeBook = fallback;

            try {
               if (recipeBookRecipesField == null) {
                  recipeBookRecipesField = ClientRecipeBook.class.getDeclaredField("known");
                  recipeBookRecipesField.setAccessible(true);
               }

               if (recipeBookRecipesField.get(recipeBook) instanceof Map<?, ?> recipeMap) {
                  List<RecipeDisplayEntry> entries = new ArrayList<>(recipeMap.size());

                  for (Object value : recipeMap.values()) {
                     if (value instanceof RecipeDisplayEntry entry) {
                        entries.add(entry);
                     }
                  }

                  if (!entries.isEmpty()) {
                     return entries;
                  }
               }
            } catch (Exception var8) {
            }

            fallback.rebuildCollections();
            List<RecipeDisplayEntry> fallbackx = new ArrayList<>();

            for (RecipeCollection collection : fallback.getCollections()) {
               fallbackx.addAll(collection.getRecipes());
            }

            return fallbackx;
         } else {
            return List.of();
         }
      } else {
         return List.of();
      }
   }

   private static boolean isSupportedCraftingEntry(RecipeDisplayEntry entry, int gridWidth, int gridHeight) {
      if (entry != null && entry.display() != null) {
         if (!(entry.display() instanceof ShapedCraftingRecipeDisplay shaped)) {
            return entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless ? shapeless.ingredients().size() <= gridWidth * gridHeight : false;
         } else {
            return shaped.width() <= gridWidth && shaped.height() <= gridHeight;
         }
      } else {
         return false;
      }
   }

   private static AutismCraftingHelper.CraftPhasePlan createSyncedCraftPhasePlan(
      Minecraft mc, AutismCraftingHelper.CraftableRecipeOption option, AbstractCraftingMenu craftingHandler
   ) {
      if (mc.level != null && option != null && option.maxCraftsNow > 0 && option.syncedEntry != null) {
         ContextMap params = SlotDisplayContext.fromLevel(mc.level);
         int maxCraftsPerPhase = computeMaxCraftsPerPhase(option.syncedEntry, craftingHandler, params, option.maxCraftsNow);
         return new AutismCraftingHelper.CraftPhasePlan(option.maxCraftsNow, maxCraftsPerPhase);
      } else {
         return new AutismCraftingHelper.CraftPhasePlan(0, 0);
      }
   }

   private static int computeMaxCraftsPerPhase(RecipeDisplayEntry entry, AbstractCraftingMenu craftingHandler, ContextMap params, int totalCraftsAvailable) {
      int safeTotal = Math.max(0, totalCraftsAvailable);
      if (safeTotal <= 0) {
         return 0;
      } else {
         int phaseCrafts = safeTotal;
         List<Ingredient> ingredients = getPlanningIngredients(entry, params);
         if (ingredients.isEmpty()) {
            return Math.min(safeTotal, 64);
         } else {
            for (Ingredient ingredient : ingredients) {
               int bestMaxCount = 0;

               for (Slot slot : craftingHandler.slots) {
                  if (slot != null) {
                     ItemStack stack = slot.getItem();
                     if (stack != null && !stack.isEmpty() && ingredient.test(stack)) {
                        bestMaxCount = Math.max(bestMaxCount, stack.getMaxStackSize());
                     }
                  }
               }

               if (bestMaxCount <= 0) {
                  return 0;
               }

               phaseCrafts = Math.min(phaseCrafts, bestMaxCount);
            }

            return Math.max(1, phaseCrafts);
         }
      }
   }

   private static List<Ingredient> getPlanningIngredients(RecipeDisplayEntry entry, ContextMap params) {
      if (entry.craftingRequirements().isPresent() && !((List)entry.craftingRequirements().get()).isEmpty()) {
         return new ArrayList<>((Collection<? extends Ingredient>)entry.craftingRequirements().get());
      } else {
         List<Ingredient> derived = new ArrayList<>();

         for (SlotDisplay slotDisplay : getIngredientDisplays(entry)) {
            if (slotDisplay != null) {
               List<ItemStack> displayStacks = slotDisplay.resolveForStacks(params);
               List<Item> items = new ArrayList<>();

               for (ItemStack stack : displayStacks) {
                  if (stack != null && !stack.isEmpty() && stack.getItem() != null) {
                     items.add(stack.getItem());
                  }
               }

               if (!items.isEmpty()) {
                  derived.add(Ingredient.of(items.stream()));
               }
            }
         }

         return derived;
      }
   }

   private static List<AutismCraftingHelper.ExpectedGridSlot> buildExpectedGridSlots(
      RecipeDisplayEntry entry, AbstractCraftingMenu craftingHandler, ContextMap params, int craftCount
   ) {
      List<AutismCraftingHelper.ExpectedGridSlot> expected = new ArrayList<>();
      List<Ingredient> ingredients = getPlanningIngredients(entry, params);
      if (ingredients.isEmpty()) {
         return expected;
      } else {
         List<Slot> inputSlots = craftingHandler.getInputGridSlots();
         int ingredientIndex = 0;
         if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) {
            for (int row = 0; row < shaped.height(); row++) {
               for (int col = 0; col < shaped.width(); col++) {
                  int shapedIndex = row * shaped.width() + col;
                  if (shapedIndex >= 0 && shapedIndex < shaped.ingredients().size()) {
                     SlotDisplay slotDisplay = (SlotDisplay)shaped.ingredients().get(shapedIndex);
                     if (!isEmptySlotDisplay(slotDisplay, params)) {
                        int gridIndex = row * craftingHandler.getGridWidth() + col;
                        if (gridIndex >= 0 && gridIndex < inputSlots.size() && ingredientIndex < ingredients.size()) {
                           expected.add(
                              new AutismCraftingHelper.ExpectedGridSlot(inputSlots.get(gridIndex).index, ingredients.get(ingredientIndex), craftCount)
                           );
                           ingredientIndex++;
                        }
                     }
                  }
               }
            }

            return expected;
         } else {
            if (entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless) {
               for (SlotDisplay slotDisplay : shapeless.ingredients()) {
                  if (!isEmptySlotDisplay(slotDisplay, params) && ingredientIndex < ingredients.size() && ingredientIndex < inputSlots.size()) {
                     expected.add(
                        new AutismCraftingHelper.ExpectedGridSlot(inputSlots.get(ingredientIndex).index, ingredients.get(ingredientIndex), craftCount)
                     );
                     ingredientIndex++;
                  }
               }
            }

            return expected;
         }
      }
   }

   private static boolean isEmptySlotDisplay(SlotDisplay slotDisplay, ContextMap params) {
      return slotDisplay == null || slotDisplay.resolveForStacks(params).stream().noneMatch(stack -> stack != null && !stack.isEmpty());
   }

   private static List<SlotDisplay> getIngredientDisplays(RecipeDisplayEntry entry) {
      if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) {
         return shaped.ingredients();
      } else {
         return entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless ? shapeless.ingredients() : List.of();
      }
   }

   private static boolean shouldUseCraftAll(int phaseCrafts, int totalCraftsAvailable, int maxCraftsPerPhase) {
      return phaseCrafts > 0 && (phaseCrafts >= totalCraftsAvailable || phaseCrafts >= maxCraftsPerPhase);
   }

   private static boolean sameResult(ItemStack left, ItemStack right) {
      return left != null
         && right != null
         && !left.isEmpty()
         && !right.isEmpty()
         && left.getCount() == right.getCount()
         && ItemStack.isSameItemSameComponents(left, right);
   }

   private static int countInventoryItems(Minecraft mc, ItemStack template) {
      if (mc.player != null && template != null && !template.isEmpty()) {
         int total = 0;

         for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == template.getItem()) {
               total += stack.getCount();
            }
         }

         return total;
      } else {
         return 0;
      }
   }

   private static boolean waitForCraftGridState(
      Minecraft mc, int syncId, List<AutismCraftingHelper.ExpectedGridSlot> expectedSlots, ItemStack expectedResult, long timeoutMs
   ) {
      long deadline = System.nanoTime() + timeoutMs * 1000000L;

      while (System.nanoTime() < deadline) {
         if (mc.player != null && mc.player.containerMenu instanceof AbstractCraftingMenu craftingHandler) {
            if (craftingHandler.containerId != syncId) {
               return false;
            }

            if (matchesExpectedCraftGrid(craftingHandler, expectedSlots, expectedResult)) {
               return true;
            }
         }

         try {
            Thread.sleep(5L);
         } catch (InterruptedException var10) {
            Thread.currentThread().interrupt();
            return false;
         }
      }

      return false;
   }

   private static boolean matchesExpectedCraftGrid(
      AbstractCraftingMenu craftingHandler, List<AutismCraftingHelper.ExpectedGridSlot> expectedSlots, ItemStack expectedResult
   ) {
      if (expectedResult != null && !expectedResult.isEmpty()) {
         ItemStack output = craftingHandler.getResultSlot().getItem();
         if (!output.isEmpty() && output.getItem() == expectedResult.getItem()) {
            Set<Integer> expectedSlotIds = new HashSet<>();

            for (AutismCraftingHelper.ExpectedGridSlot expectedSlot : expectedSlots) {
               expectedSlotIds.add(expectedSlot.slotId);
               Slot slot = craftingHandler.getSlot(expectedSlot.slotId);
               if (slot == null) {
                  return false;
               }

               ItemStack stack = slot.getItem();
               if (stack == null || stack.isEmpty() || stack.getCount() < expectedSlot.expectedCount) {
                  return false;
               }

               if (expectedSlot.ingredient != null && !expectedSlot.ingredient.test(stack)) {
                  return false;
               }
            }

            for (Slot inputSlot : craftingHandler.getInputGridSlots()) {
               if (inputSlot != null && !expectedSlotIds.contains(inputSlot.index) && !inputSlot.getItem().isEmpty()) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean waitForInventoryCount(Minecraft mc, ItemStack expectedResult, int requiredCount, long timeoutMs) {
      long deadline = System.nanoTime() + timeoutMs * 1000000L;

      while (System.nanoTime() < deadline) {
         if (countInventoryItems(mc, expectedResult) >= requiredCount) {
            return true;
         }

         try {
            Thread.sleep(5L);
         } catch (InterruptedException var8) {
            Thread.currentThread().interrupt();
            return false;
         }
      }

      return false;
   }

   private static AutismCraftingHelper.CraftExecutionResult buildCraftResult(
      AutismCraftingHelper.CraftableRecipeOption option, int craftedAmount, int roundedTarget
   ) {
      return craftedAmount < roundedTarget
         ? AutismCraftingHelper.CraftExecutionResult.success("Crafted " + craftedAmount + "/" + roundedTarget + " " + option.label + ".", craftedAmount)
         : AutismCraftingHelper.CraftExecutionResult.success("Crafted " + craftedAmount + " " + option.label + ".", craftedAmount);
   }

   private static boolean runClientTask(Minecraft mc, Runnable task) {
      if (PackHideState.isHardLocked()) {
         return false;
      } else if (mc.isSameThread()) {
         task.run();
         return true;
      } else {
         CountDownLatch latch = new CountDownLatch(1);
         AtomicBoolean ran = new AtomicBoolean(false);
         AtomicReference<RuntimeException> runtimeError = new AtomicReference<>();
         mc.execute(() -> {
            try {
               task.run();
               ran.set(true);
            } catch (RuntimeException var8) {
               runtimeError.set(var8);
            } finally {
               latch.countDown();
            }
         });

         try {
            if (!latch.await(2L, TimeUnit.SECONDS)) {
               return false;
            }
         } catch (InterruptedException var6) {
            Thread.currentThread().interrupt();
            return false;
         }

         if (runtimeError.get() != null) {
            throw (RuntimeException)runtimeError.get();
         } else {
            return ran.get();
         }
      }
   }

   public static List<AutismCraftingHelper.CraftableRecipeOption> filterRecipes(List<AutismCraftingHelper.CraftableRecipeOption> all, String query) {
      if (query != null && !query.isBlank()) {
         String lower = query.toLowerCase();
         return all.stream().filter(o -> o.searchKey.contains(lower)).toList();
      } else {
         return new ArrayList<>(all);
      }
   }

   public static AutismCraftingHelper.CraftableRecipeOption findInList(List<AutismCraftingHelper.CraftableRecipeOption> recipes, String recipeKey, int recipeId) {
      if (recipes == null) {
         return null;
      } else {
         for (AutismCraftingHelper.CraftableRecipeOption option : recipes) {
            if (!recipeKey.isBlank() && recipeKey.equalsIgnoreCase(option.recipeKey)) {
               return option;
            }

            if (recipeId >= 0 && (option.recipeId == recipeId || option.syncedRecipeId == recipeId)) {
               return option;
            }
         }

         return null;
      }
   }

   public static final class CraftExecutionResult {
      public final boolean success;
      public final String message;
      public final int craftedAmount;

      private CraftExecutionResult(boolean success, String message, int craftedAmount) {
         this.success = success;
         this.message = message;
         this.craftedAmount = craftedAmount;
      }

      public static AutismCraftingHelper.CraftExecutionResult success(String message, int craftedAmount) {
         return new AutismCraftingHelper.CraftExecutionResult(true, message, craftedAmount);
      }

      public static AutismCraftingHelper.CraftExecutionResult failure(String message) {
         return new AutismCraftingHelper.CraftExecutionResult(false, message, 0);
      }
   }

   private static final class CraftPhasePlan {
      private final int totalCraftsAvailable;
      private final int maxCraftsPerPhase;

      private CraftPhasePlan(int totalCraftsAvailable, int maxCraftsPerPhase) {
         this.totalCraftsAvailable = Math.max(0, totalCraftsAvailable);
         this.maxCraftsPerPhase = Math.max(0, maxCraftsPerPhase);
      }
   }

   public static enum CraftSource {
      PLAYER_2X2,
      TABLE_3X3;
   }

   public static final class CraftableRecipeOption {
      public final String recipeKey;
      public final int recipeId;
      public final int syncedRecipeId;
      public final RecipeDisplayEntry syncedEntry;
      public final AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe;
      public final ItemStack result;
      public final Component labelComponent;
      public final String label;
      public final String registryId;
      public final String searchKey;
      public final boolean hasMaterialsNow;
      public final boolean hasInventoryRoomNow;
      public final int maxCraftsByMaterials;
      public final int maxCraftsByStorage;
      public final boolean craftableNow;
      public final int maxCraftsNow;
      public final int maxOutputNow;
      public final AutismCraftingHelper.CraftSource craftSource;

      public CraftableRecipeOption(
         AutismLocalCraftingRegistry.LocalCraftingRecipe localRecipe,
         RecipeDisplayEntry syncedEntry,
         int maxCraftsByMaterials,
         int maxCraftsByStorage,
         AutismCraftingHelper.CraftSource craftSource
      ) {
         this.localRecipe = localRecipe;
         this.syncedEntry = syncedEntry;
         this.recipeKey = localRecipe.recipeKey;
         this.recipeId = localRecipe.recipeKey.hashCode();
         this.syncedRecipeId = syncedEntry != null ? syncedEntry.id().index() : -1;
         this.result = localRecipe.result.copy();
         this.labelComponent = this.result.getHoverName().copy();
         this.label = this.labelComponent.getString();
         this.maxCraftsByMaterials = Math.max(0, maxCraftsByMaterials);
         this.maxCraftsByStorage = Math.max(0, maxCraftsByStorage);
         this.hasMaterialsNow = this.maxCraftsByMaterials > 0;
         this.hasInventoryRoomNow = this.maxCraftsByStorage > 0;
         this.maxCraftsNow = Math.max(0, Math.min(this.maxCraftsByMaterials, this.maxCraftsByStorage));
         this.craftableNow = this.maxCraftsNow > 0;
         this.maxOutputNow = this.maxCraftsNow * Math.max(1, this.result.getCount());
         this.craftSource = craftSource == null ? AutismCraftingHelper.CraftSource.PLAYER_2X2 : craftSource;
         Identifier id = this.result.getItem() == null ? null : BuiltInRegistries.ITEM.getKey(this.result.getItem());
         this.registryId = id == null ? "" : id.toString();
         this.searchKey = (this.label + " " + this.registryId + " " + this.recipeKey).toLowerCase();
      }

      public boolean hasSyncedRecipe() {
         return this.syncedEntry != null && this.syncedRecipeId >= 0;
      }
   }

   private static final class ExpectedGridSlot {
      private final int slotId;
      private final Ingredient ingredient;
      private final int expectedCount;

      private ExpectedGridSlot(int slotId, Ingredient ingredient, int expectedCount) {
         this.slotId = slotId;
         this.ingredient = ingredient;
         this.expectedCount = expectedCount;
      }
   }

   private static final class PreparedCraftContext {
      private final AbstractCraftingMenu handler;
      private final boolean closeAfter;
      private final Screen restoreScreen;

      private PreparedCraftContext(AbstractCraftingMenu handler, boolean closeAfter, Screen restoreScreen) {
         this.handler = handler;
         this.closeAfter = closeAfter;
         this.restoreScreen = restoreScreen;
      }
   }

   private static final class RecipeListingContext {
      private final AutismCraftingHelper.CraftSource source;
      private final int gridWidth;
      private final int gridHeight;
      private final List<ItemStack> inputStacks;
      private final Map<String, RecipeDisplayEntry> syncedBySignature;
      private final List<AutismLocalCraftingRegistry.LocalCraftingRecipe> syncedRecipes;

      private RecipeListingContext(
         AutismCraftingHelper.CraftSource source,
         int gridWidth,
         int gridHeight,
         List<ItemStack> inputStacks,
         Map<String, RecipeDisplayEntry> syncedBySignature,
         List<AutismLocalCraftingRegistry.LocalCraftingRecipe> syncedRecipes
      ) {
         this.source = source;
         this.gridWidth = gridWidth;
         this.gridHeight = gridHeight;
         this.inputStacks = inputStacks == null ? List.of() : List.copyOf(inputStacks);
         this.syncedBySignature = syncedBySignature == null ? Map.of() : Map.copyOf(syncedBySignature);
         this.syncedRecipes = syncedRecipes == null ? List.of() : List.copyOf(syncedRecipes);
      }
   }
}
