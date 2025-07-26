package gecko10000.storagepots.guis

import com.google.common.collect.HashMultimap
import gecko10000.geckolib.extensions.*
import gecko10000.geckolib.inventorygui.GUI
import gecko10000.geckolib.inventorygui.InventoryGUI
import gecko10000.geckolib.inventorygui.ItemButton
import gecko10000.geckolib.misc.EventListener
import gecko10000.geckolib.misc.Task
import gecko10000.storagepots.GUIManager
import gecko10000.storagepots.PotManager
import gecko10000.storagepots.StoragePots
import gecko10000.storagepots.di.MyKoinComponent
import gecko10000.storagepots.model.Pot
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.*
import org.koin.core.component.inject
import java.util.*
import kotlin.math.ceil
import kotlin.math.min
import kotlin.properties.Delegates

class StoragePotGUI(private var pot: Pot) : InventoryHolder, MyKoinComponent {

    companion object {
        private const val INPUT_SLOT = 2
        private const val OUTPUT_SLOT = 3
        private const val UPGRADE_SLOT = 5
        private const val AUTO_SLOT = 6
        private const val DESTROY_SLOT = 8
        private const val SIZE = 9

        private const val LEFT_CLICK_UPGRADES = 1
        private const val RIGHT_CLICK_UPGRADES = 10
    }

    private val plugin: StoragePots by inject()
    private val potManager: PotManager by inject()
    private val guiManager: GUIManager by inject()

    private val viewers = mutableSetOf<UUID>()
    private val inventory: InventoryGUI
    private var outputItemCount by Delegates.notNull<Int>()

    init {
        inventory = createInventory()
    }

    private fun displayItem(): ItemStack {
        val displayItem = pot.info.item?.clone() ?: return GUI.FILLER
        val isLocked = pot.info.isLocked
        val name = MM.deserialize(
            "<lock> <item>",
            Placeholder.parsed(
                "lock",
                if (isLocked) "<red>\uD83D\uDD12</red>" else "<green>\uD83D\uDD13</green>"
            ),
            Placeholder.component("item", displayItem.name())
        ).withDefaults()
        val loreStrings =
            if (isLocked) listOf("<dark_aqua>This pot has been upgraded", "<dark_aqua>and is <red><b>locked</b></red>.")
            else listOf(
                "<dark_aqua>This pot has not been upgraded",
                "<dark_aqua>and is <green><b>unlocked</b></green>."
            )
        displayItem.editMeta { meta ->
            meta.displayName(name)
            meta.lore(
                loreStrings.map { parseMM(it) }
            )
        }
        return displayItem
    }

    private fun MutableList<Component>.lockedDisclaimer() {
        if (!pot.info.isLocked) {
            add(Component.empty())
            add(parseMM("<red>Warning: upgrading the storage pot"))
            add(parseMM("<red>will lock it to the current item."))
        }
    }

    private fun upgradeButton(): ItemButton {
        val item = ItemStack.of(Material.PAPER)
        item.setData(DataComponentTypes.ITEM_MODEL, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE.key())
        item.editMeta {
            it.displayName(parseMM("<dark_aqua><b>Upgrade Max Storage"))
            it.lore(buildList {
                addAll(
                    listOf(
                        MM.deserialize(
                            "<dark_green>Exchange rate: <green>1</green> item per <green><amount></green> storage",
                            Placeholder.unparsed("amount", plugin.config.storageUpgradeAmount.toString())
                        ).withDefaults(),
                        parseMM("<dark_green><green>Left click</green> to upgrade <green>$LEFT_CLICK_UPGRADES</green> time"),
                        parseMM("<dark_green><green>Right click</green> to upgrade <green>$RIGHT_CLICK_UPGRADES</green> times")
                    )
                )
                this.lockedDisclaimer()
            })
            // Hides attributes
            it.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            it.attributeModifiers = HashMultimap.create()
        }
        return ItemButton.create(item) { e ->
            if (e.isLeftClick) potManager.upgrade(pot, LEFT_CLICK_UPGRADES)
            if (e.isRightClick) potManager.upgrade(pot, RIGHT_CLICK_UPGRADES)
            updateInventory()
        }
    }

    private fun autoUpgradeButton(): ItemButton {
        val auto = pot.info.isAutoUpgrading
        val item = ItemStack.of(if (auto) Material.NETHER_STAR else Material.STRUCTURE_VOID)
        item.editMeta {
            val enabled = if (auto) "<green>Enabled" else "<red>Disabled"
            it.displayName(parseMM("<b><dark_aqua>Auto Upgrade $enabled"))
            it.lore(
                buildList {
                    add(parseMM("<aqua>Click to toggle"))
                    this.lockedDisclaimer()
                }
            )
        }
        return ItemButton.create(item) { e ->
            if (!e.whoClicked.hasPermission("storagepots.toggleauto")) {
                e.whoClicked.sendMessage(plugin.lang.noPermissionMessage)
                return@create
            }
            potManager.toggleAutoUpgrades(pot)
            updateInventory()
        }
    }

    private fun destroyButton(): ItemButton {
        val item = ItemStack.of(Material.TNT)
        item.editMeta {
            it.displayName(parseMM("<red>Destroy Pot"))
            it.lore(
                listOf(
                    parseMM("<red>Warning: this will also destroy"),
                    MM.deserialize(
                        "<red>all <u><amount></u> items in the pot.",
                        Placeholder.unparsed("amount", pot.info.amount.toString())
                    ).withDefaults(),
                    Component.empty(),
                    parseMM("<yellow>Want to extract items instead?"),
                    parseMM("<yellow>Left click the pot block to take"),
                    parseMM("<yellow>out a stack of items."),
                )
            )
        }
        return ItemButton.create(item) { e ->
            DestroyConfirmationGUI(e.whoClicked as Player, pot)
        }
    }

    // Two cases:
    // - Item swapped into input slot: add to count if it's the right item
    //   (cancel and remove partial if it's too full?)
    // - Item taken out of output slot: ensure nothing is being swapped
    //   in its place. Subtract amount that's taken out.
    private fun handleHotbarSwap(e: InventoryClickEvent) {
        // Item into input slot: e.currentItem is empty,
        // use hotbar key number to get item
        val itemInHotbar = e.whoClicked.inventory.getItem(e.hotbarButton)
        if (e.slot == INPUT_SLOT && !itemInHotbar.isEmpty()) {
            // Either we do nothing if it's the wrong item
            if (pot.info.item != null && !itemInHotbar!!.isSimilar(pot.info.item)) {
                return
            }
            // Or we try to add to the pot and set
            // the item to the leftovers.
            val leftover = potManager.tryAdd(pot, itemInHotbar!!, updateGUI = false)
            itemInHotbar.amount = leftover
        }
        if (e.slot == OUTPUT_SLOT) {
            // Swapping with different
            // item not allowed
            if (!itemInHotbar.isEmpty() && !itemInHotbar!!.isSimilar(pot.info.item)) return
            val diff = outputItemCount - (itemInHotbar?.amount ?: 0)
            potManager.remove(pot, diff, updateGUI = false)
            e.whoClicked.inventory.setItem(e.hotbarButton, pot.info.item!!.asQuantity(outputItemCount))
        }
    }

    // Cases:
    // - Item is placed into input slot. If valid, we try to add it and give back the rest
    // - Item is picked up from output slot. If cursor is empty, we remove it and put item on cursor.
    private fun handleLeftClick(e: InventoryClickEvent) {
        val cursor = e.cursor
        if (e.slot == INPUT_SLOT) {
            if (cursor.isEmpty) return
            val leftover = potManager.tryAdd(pot, cursor, updateGUI = false)
            e.whoClicked.openInventory.setCursor(cursor.asQuantity(leftover))
            return
        }
        if (e.slot == OUTPUT_SLOT) {
            if (!cursor.isEmpty) return
            potManager.remove(pot, outputItemCount, updateGUI = false)
            e.whoClicked.openInventory.setCursor(pot.info.item!!.asQuantity(outputItemCount))
        }
    }

    // Almost the same as left click, just slightly different math.
    // Placing: 1 item at a time
    // Picking up: half the stack, rounded up.
    private fun handleRightClick(e: InventoryClickEvent) {
        val cursor = e.cursor
        if (e.slot == INPUT_SLOT) {
            if (cursor.isEmpty) return
            val leftover = potManager.tryAdd(pot, cursor.asOne(), updateGUI = false)
            e.whoClicked.openInventory.setCursor(cursor.asQuantity(cursor.amount - 1 + leftover))
            return
        }
        if (e.slot == OUTPUT_SLOT) {
            if (!cursor.isEmpty) return
            val toPickUp = ceil(outputItemCount / 2.0).toInt()
            potManager.remove(pot, toPickUp, updateGUI = false)
            e.whoClicked.openInventory.setCursor(pot.info.item!!.asQuantity(toPickUp))
        }
    }

    private val shiftClickOrder: List<Int> = buildList {
        addAll(8 downTo 0)
        addAll(35 downTo 9)
    }

    // Returns the leftovers.
    private fun tryAdd(inv: PlayerInventory, item: ItemStack, slot: Int, onlyOnExisting: Boolean): Int {
        val inSlot = inv.getItem(slot)
        if (!onlyOnExisting && inSlot.isEmpty()) {
            inv.setItem(slot, item.clone())
            return 0
        }
        if (inSlot.isEmpty() || !item.isSimilar(inSlot)) {
            return item.amount
        }
        val transferredAmount = min(inSlot!!.maxStackSize - inSlot.amount, item.amount)
        inSlot.amount += transferredAmount
        return item.amount - transferredAmount
    }

    // First we try to add to existing stacks.
    // Then, we place into empty slots.
    // Am I autistic?
    private fun simulateShiftClickInto(inv: PlayerInventory, item: ItemStack): Int {
        var trackerItem = item
        fun addStep(index: Int, onlyOnExisting: Boolean) {
            if (trackerItem.isEmpty) return
            val leftover = tryAdd(inv, trackerItem, index, onlyOnExisting)
            trackerItem = trackerItem.asQuantity(leftover)
            return
        }
        for (index in shiftClickOrder) {
            addStep(index, true)
        }
        for (index in shiftClickOrder) {
            addStep(index, false)
        }
        return trackerItem.amount
    }

    // Either we shift+click in the GUI, or we shift+click in our inventory.
    // For GUI: we try to add the items to the player's inventory.
    // If there is anything left over, we don't subtract that from the count.
    // For inventory: we try to add to the pot. Leftovers stay behind.
    private fun handleShiftClick(e: InventoryClickEvent) {
        if (e.clickedInventory == null) return
        val isPlayerInventory = e.clickedInventory == e.whoClicked.inventory
        if (isPlayerInventory) {
            val clickedItem = e.whoClicked.inventory.getItem(e.slot) ?: return
            val leftover = potManager.tryAdd(pot, clickedItem, updateGUI = false)
            clickedItem.amount = leftover
            return
        }
        // Here we are sure it's the GUI
        if (e.slot == OUTPUT_SLOT) {
            val leftover = simulateShiftClickInto(e.whoClicked.inventory, pot.info.item!!.asQuantity(outputItemCount))
            potManager.remove(pot, outputItemCount - leftover, updateGUI = false)
        }
    }

    // Can swap out of offhand into input,
    // or out of output into offhand.
    private fun handleOffhandSwap(e: InventoryClickEvent) {
        val offhandItem = e.whoClicked.inventory.itemInOffHand
        if (e.slot == INPUT_SLOT && offhandItem.isSimilar(pot.info.item)) {
            val leftover = potManager.tryAdd(pot, offhandItem, updateGUI = false)
            offhandItem.amount = leftover
            return
        }
        val item = pot.info.item ?: return
        if (e.slot == OUTPUT_SLOT && offhandItem.isEmpty) {
            potManager.remove(pot, outputItemCount, updateGUI = false)
            e.whoClicked.inventory.setItemInOffHand(item.asQuantity(outputItemCount))
        }
    }

    private val nextDropTick = mutableMapOf<UUID, Long>()

    private fun handleDrop(e: InventoryClickEvent, amount: Int) {
        if (e.slot != OUTPUT_SLOT) return
        val currentTick = plugin.server.currentTick
        val uuid = e.whoClicked.uniqueId
        if (nextDropTick.getOrDefault(uuid, 0) > currentTick) return
        e.whoClicked.dropItem(pot.info.item!!.asQuantity(amount))
        potManager.remove(pot, amount, updateGUI = false)
        nextDropTick[uuid] = currentTick + plugin.config.dropCooldownTicks
    }

    private fun handleIOClick(e: InventoryClickEvent) {
        e.isCancelled = true
        when (e.click) {
            ClickType.NUMBER_KEY -> handleHotbarSwap(e)
            ClickType.LEFT -> handleLeftClick(e)
            ClickType.RIGHT -> handleRightClick(e)
            ClickType.SHIFT_LEFT -> handleShiftClick(e)
            ClickType.SHIFT_RIGHT -> handleShiftClick(e)
            ClickType.SWAP_OFFHAND -> handleOffhandSwap(e)
            ClickType.DROP -> handleDrop(e, 1)
            ClickType.CONTROL_DROP -> handleDrop(e, min(pot.info.item?.maxStackSize ?: 0, pot.info.amount.toInt()))
            else -> {}
        }
        updateInventory()
    }

    // Maybe if they'd add proper API for changing title...
    private fun changeTitle(newName: Component) {
        val players = viewers.mapNotNull(Bukkit::getPlayer)
        players.forEach { p ->
            p.openInventory.title = LegacyComponentSerializer.legacySection().serialize(newName)
        }
        Task.syncDelayed { ->
            players.forEach { p ->
                if (!viewers.contains(p.uniqueId)) return@forEach
                p.updateInventory()
            }
        }
    }

    private fun updateInventory(gui: InventoryGUI = this.inventory, isBeforeOpen: Boolean = false) {
        val prevInfo = pot.info
        pot = potManager.getPot(pot.block) ?: return run {
            destroy()
        }
        if (!isBeforeOpen && pot.info == prevInfo) return
        outputItemCount = pot.info.item?.let {
            min(pot.info.amount, it.maxStackSize.toLong()).toInt()
        } ?: 0
        if (!isBeforeOpen) changeTitle(plugin.lang.potGUIName(pot.info))

        gui.fill(0, INPUT_SLOT, GUI.FILLER)
        gui.fill(OUTPUT_SLOT + 1, SIZE, GUI.FILLER)
        val item = pot.info.item
        if (item != null) {
            gui.inventory.setItem(0, displayItem())
        }
        gui.openSlot(INPUT_SLOT)
        gui.inventory.setItem(INPUT_SLOT, null)
        val outputItem = if (pot.info.item != null && outputItemCount > 0) {
            gui.openSlot(OUTPUT_SLOT)
            pot.info.item?.asQuantity(outputItemCount)
        } else {
            gui.closeSlot(OUTPUT_SLOT)
            ItemStack.of(Material.BARRIER).also {
                it.editMeta {
                    it.displayName(
                        parseMM("<red>No item")
                    )
                }
            }
        }
        gui.inventory.setItem(OUTPUT_SLOT, outputItem)
        gui.setReturnsItems(false)

        gui.addButton(UPGRADE_SLOT, upgradeButton())
        gui.addButton(AUTO_SLOT, autoUpgradeButton())
        gui.addButton(DESTROY_SLOT, destroyButton())
    }

    // Public-facing update method
    // without parameters.
    fun update() {
        updateInventory()
    }

    fun open(player: Player) {
        Task.syncDelayed { ->
            viewers.add(player.uniqueId)
            player.openInventory(inventory.inventory)
            updateInventory()
        }
    }

    fun destroy() {
        viewers.forEach {
            val player = Bukkit.getPlayer(it) ?: return@forEach
            player.closeInventory()
        }
    }

    override fun getInventory(): Inventory {
        return inventory.inventory
    }

    private fun createInventory(): InventoryGUI {
        val gui = InventoryGUI(Bukkit.createInventory(this, SIZE, plugin.lang.potGUIName(pot.info)))
        gui.setOnClickOpenSlot { e -> handleIOClick(e) }
        gui.setOnDragOpenSlot { e -> e.isCancelled = true }
        gui.setOnDestroy { guiManager.remove(pot.block) }
        EventListener(InventoryCloseEvent::class.java) { e -> viewers.remove(e.player.uniqueId) }
        EventListener(PlayerQuitEvent::class.java) { e -> viewers.remove(e.player.uniqueId) }
        updateInventory(gui, isBeforeOpen = true)
        return gui
    }
}
