package com.vowtaker.service;

import com.vowtaker.model.GodAlignment;
import com.vowtaker.model.VowDefinition;
import com.vowtaker.model.VowType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VowRegistry
{
    private static final Map<String, VowDefinition> VOWS = build();

    /** PERMANENT vows that count as "Major" alongside god vows. Only one Major is worn at a time. */
    private static final Set<String> MAJOR_FILLER_IDS = new HashSet<>(Arrays.asList(
        // Equipment slot lockouts.
        "no_cape_slot", "no_chest_slot", "no_legs_slot", "no_boots_slot", "no_gloves_slot",
        "no_helmet_slot", "no_shield_slot", "no_ring_slot", "no_amulet_slot",
        // Gear tier / material lockouts.
        "no_armor_above_80", "no_jewellery_above_80", "no_barrows_armour", "no_metal_armour",
        "no_degradeable_gear", "one_hand_only", "naked_ironman",
        "no_armour_above_30", "no_armour_above_40", "no_armour_above_50", "no_armour_above_60",
        "no_armour_above_70", "no_melee_armour", "no_ranged_armour", "no_magic_armour",
        // Combat lockouts.
        "no_damage_boosting_prayers", "no_special_attacks", "no_poison_weapons",
        "no_ranged_training", "no_magic_training",
        // Consumable lockouts.
        "no_combat_potions", "no_prayer_restores", "no_brews",
        // Travel lockouts.
        "no_teleport_items"
    ));

    private VowRegistry()
    {
    }

    /** Majors are the god's own vows plus the heavy permanent restrictions. */
    public static boolean isMajor(VowDefinition vow)
    {
        if (vow == null) return false;
        return vow.getType() == VowType.GOD || MAJOR_FILLER_IDS.contains(vow.getId());
    }

    public static boolean isMajorFillerId(String id)
    {
        return id != null && MAJOR_FILLER_IDS.contains(id);
    }

    public static List<VowDefinition> all()
    {
        return new ArrayList<>(VOWS.values());
    }

    public static List<VowDefinition> permanentVows()
    {
        List<VowDefinition> list = new ArrayList<>();
        for (VowDefinition vow : VOWS.values())
        {
            if (vow.getType() == VowType.PERMANENT)
            {
                list.add(vow);
            }
        }
        return list;
    }

    public static List<VowDefinition> godVows(GodAlignment god)
    {
        List<VowDefinition> list = new ArrayList<>();
        for (VowDefinition vow : VOWS.values())
        {
            if (vow.getType() == VowType.GOD && vow.getGodAlignment() == god)
            {
                list.add(vow);
            }
        }
        return list;
    }

    public static List<VowDefinition> ritualVows()
    {
        List<VowDefinition> list = new ArrayList<>();
        for (VowDefinition vow : VOWS.values())
        {
            if (vow.getType() == VowType.RITUAL)
            {
                list.add(vow);
            }
        }
        return list;
    }

    public static List<VowDefinition> draftReviewQueue(VowStorageService storage)
    {
        List<VowDefinition> draft = new ArrayList<>();
        for (VowDefinition vow : VOWS.values())
        {
            if (!storage.isApproved(vow.getId()) && !storage.isDeclined(vow.getId()))
            {
                draft.add(vow);
            }
        }
        return draft;
    }

    public static List<VowDefinition> approvedVows(VowStorageService storage)
    {
        List<VowDefinition> approved = new ArrayList<>();
        for (VowDefinition vow : VOWS.values())
        {
            if (storage.isApproved(vow.getId()))
            {
                approved.add(vow);
            }
        }
        return approved;
    }

    public static VowDefinition getById(String id)
    {
        return VOWS.get(id);
    }

    private static Map<String, VowDefinition> build()
    {
        Map<String, VowDefinition> map = new LinkedHashMap<>();
        seedEquipmentVows(map);
        seedArmourCapVows(map);
        seedUtilityVows(map);
        seedConsumableVows(map);
        seedCombatVows(map);
        seedMinorVows(map);
        seedSaradominVows(map);
        seedZamorakVows(map);
        seedGuthixVows(map);
        seedArmadylVows(map);
        seedBandosVows(map);
        seedZarosVows(map);
        seedRituals(map);
        return Collections.unmodifiableMap(map);
    }

    private static List<String> tags(String... t)
    {
        return Arrays.asList(t);
    }

    // ================== MAJOR / PERMANENT ==================
    private static void seedEquipmentVows(Map<String, VowDefinition> map)
    {
        add(map, new VowDefinition("no_cape_slot", "No Cape Slot", "You cannot equip a cape or cloak.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "medium", true, tags("cape")));
        add(map, new VowDefinition("no_chest_slot", "No Chest Slot", "You cannot equip a chest armour item.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "medium", true, tags("chest")));
        add(map, new VowDefinition("no_legs_slot", "No Legs Slot", "You cannot equip leg armour.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "medium", true, tags("legs")));
        add(map, new VowDefinition("no_boots_slot", "No Boots Slot", "You cannot equip boots.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "medium", true, tags("boots")));
        add(map, new VowDefinition("no_gloves_slot", "No Gloves Slot", "You cannot equip gloves or vambraces.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "medium", true, tags("gloves")));
        add(map, new VowDefinition("no_helmet_slot", "No Helmet Slot", "You cannot equip a helmet, coif or hood.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "medium", true, tags("helmet")));
        add(map, new VowDefinition("no_shield_slot", "No Shield Slot", "You cannot equip a shield, ward or defender.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "medium", true, tags("shield")));
        add(map, new VowDefinition("no_ring_slot", "No Ring Slot", "You cannot equip a ring.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "medium", true, tags("ring")));
        add(map, new VowDefinition("no_amulet_slot", "No Amulet Slot", "You cannot equip an amulet or necklace.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "medium", true, tags("amulet")));
        add(map, new VowDefinition("no_armor_above_80", "No Gear Above Tier 80", "Armour above tier 80 is forbidden.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "hard", true, tags("armour_t80")));
        add(map, new VowDefinition("no_jewelry_above_70", "No Jewellery Above Tier 70", "Jewellery above tier 70 is forbidden.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "medium", true, tags("jewellery_t70", "jewellery_t80")));
        add(map, new VowDefinition("no_jewellery_above_80", "No Jewellery Above Tier 80", "Only the highest-tier jewellery is forbidden.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "medium", true, tags("jewellery_t80")));
        add(map, new VowDefinition("no_barrows_armour", "No Barrows Armour", "Grave-robbed Barrows equipment is forbidden.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "medium", true, tags("armour_barrows")));
        add(map, new VowDefinition("no_metal_armour", "No Metal Armour", "Smithed metal armour is forbidden. Hide, cloth and crystal remain.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "hard", true, tags("armour_metal")));
        add(map, new VowDefinition("no_degradeable_gear", "No Degradeable Gear", "Equipment that wears out with use is forbidden.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "hard", true, tags("weapon_degradeable")));
        add(map, new VowDefinition("one_hand_only", "One Hand Only", "Two-handed weapons are forbidden.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "hard", true, tags("weapon_two_handed")));
        add(map, new VowDefinition("naked_ironman", "Naked Ironman", "You may not wear any armour and must rely on raw skill.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "hard", false,
            tags("helmet", "chest", "legs", "boots", "gloves", "shield", "cape")));
    }

    private static void seedUtilityVows(Map<String, VowDefinition> map)
    {
        add(map, new VowDefinition("no_teleport_spells", "No Teleport Spells", "Teleport spells are forbidden.", VowType.PERMANENT, GodAlignment.NONE, "utility", "milestone", "medium", true));
        add(map, new VowDefinition("no_teleport_items", "No Teleport Items", "Teleport jewellery, tabs and scrolls are forbidden.", VowType.PERMANENT, GodAlignment.NONE, "utility", "milestone", "hard", true, tags("travel_teleport_item")));
        add(map, new VowDefinition("no_fairy_rings", "No Fairy Rings", "Fairy ring travel is forbidden.", VowType.PERMANENT, GodAlignment.NONE, "utility", "milestone", "medium", true, tags("travel_fairy_ring")));
        add(map, new VowDefinition("no_spirit_trees", "No Spirit Trees", "Spirit tree travel is forbidden.", VowType.PERMANENT, GodAlignment.NONE, "utility", "milestone", "medium", true, tags("travel_spirit_tree")));
        add(map, new VowDefinition("no_poh_teleports", "No POH Teleports", "House teleports, portal nexus and mounted jewellery are forbidden.", VowType.PERMANENT, GodAlignment.NONE, "utility", "milestone", "medium", true, tags("travel_poh")));
    }

    private static void seedConsumableVows(Map<String, VowDefinition> map)
    {
        add(map, new VowDefinition("no_stamina_potions", "No Stamina Potions", "Stamina and energy potions are forbidden.", VowType.PERMANENT, GodAlignment.NONE, "consumable", "milestone", "medium", true, tags("potion_stamina")));
        add(map, new VowDefinition("no_food_above_60", "No Food Above Tier 60", "Only lower-tier food may be eaten.", VowType.PERMANENT, GodAlignment.NONE, "consumable", "milestone", "medium", true, tags("food_t60")));
        add(map, new VowDefinition("no_potions_above_70", "No Potions Above Tier 70", "Only lower-tier potions are allowed.", VowType.PERMANENT, GodAlignment.NONE, "consumable", "milestone", "medium", true, tags("potion_t70")));
        add(map, new VowDefinition("no_combat_potions", "No Combat Potions", "Stat-boosting combat potions are forbidden.", VowType.PERMANENT, GodAlignment.NONE, "consumable", "milestone", "hard", true, tags("potion_combat")));
        add(map, new VowDefinition("no_prayer_restores", "No Prayer Restores", "Prayer potions, restores and serums are forbidden.", VowType.PERMANENT, GodAlignment.NONE, "consumable", "milestone", "hard", true, tags("potion_prayer")));
        add(map, new VowDefinition("no_brews", "No Brews", "Saradomin brews and Xeric's aid are forbidden.", VowType.PERMANENT, GodAlignment.NONE, "consumable", "milestone", "hard", true, tags("food_brews")));
    }

    private static void seedCombatVows(Map<String, VowDefinition> map)
    {
        add(map, new VowDefinition("no_damage_boosting_prayers", "No Damage Boosting Prayers", "Damage prayers are unusable. Overheads remain allowed.", VowType.PERMANENT, GodAlignment.NONE, "prayer", "milestone", "hard", true));
        add(map, new VowDefinition("no_special_attacks", "No Special Attacks", "Special attack abilities are disabled.", VowType.PERMANENT, GodAlignment.NONE, "combat", "milestone", "hard", true, tags("spec_attack")));
        add(map, new VowDefinition("no_poison_weapons", "No Poison Weapons", "Poisoned and venomous weapons are forbidden.", VowType.PERMANENT, GodAlignment.NONE, "combat", "milestone", "medium", true, tags("poison_weapon")));
        add(map, new VowDefinition("no_ranged_training", "No Ranged Weapons", "You may not equip ranged weaponry.", VowType.PERMANENT, GodAlignment.NONE, "combat", "milestone", "hard", true, tags("weapon_ranged")));
        add(map, new VowDefinition("no_magic_training", "No Magic Weapons", "You may not equip staves, wands or tridents.", VowType.PERMANENT, GodAlignment.NONE, "combat", "milestone", "hard", true, tags("weapon_magic")));
        // Not default-approved: blanket prayer denial locks most boss content.
        add(map, new VowDefinition("no_prayer_book", "No Prayer Book", "Prayer use is suppressed entirely.", VowType.PERMANENT, GodAlignment.NONE, "prayer", "milestone", "hard", false, tags("prayer_any")));
    }

    // ================== MAJOR: armour tier caps ==================
    // Bands are exclusive, so a cap blocks every band above it.
    private static void seedArmourCapVows(Map<String, VowDefinition> map)
    {
        add(map, new VowDefinition("no_armour_above_30", "No Armour Above Tier 30", "Nothing above black or mithril may be worn.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "hard", true,
            tags("armour_t40", "armour_t50", "armour_t60", "armour_t70", "armour_t80", "armour_t90")));
        add(map, new VowDefinition("no_armour_above_40", "No Armour Above Tier 40", "Nothing above adamant, green d'hide or mystic may be worn.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "hard", true,
            tags("armour_t50", "armour_t60", "armour_t70", "armour_t80", "armour_t90")));
        add(map, new VowDefinition("no_armour_above_50", "No Armour Above Tier 50", "Nothing above rune or blue d'hide may be worn.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "hard", true,
            tags("armour_t60", "armour_t70", "armour_t80", "armour_t90")));
        add(map, new VowDefinition("no_armour_above_60", "No Armour Above Tier 60", "Nothing above dragon or red d'hide may be worn.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "hard", true,
            tags("armour_t70", "armour_t80", "armour_t90")));
        add(map, new VowDefinition("no_armour_above_70", "No Armour Above Tier 70", "Nothing above Barrows grade may be worn.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "medium", true,
            tags("armour_t80", "armour_t90")));
        add(map, new VowDefinition("no_melee_armour", "No Melee Armour", "Plate, chain and other melee-defence gear is forbidden.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "hard", true, tags("armour_melee")));
        add(map, new VowDefinition("no_ranged_armour", "No Ranged Armour", "Hide, coifs and other ranged gear are forbidden.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "hard", true, tags("armour_ranged")));
        add(map, new VowDefinition("no_magic_armour", "No Magic Armour", "Robes and other magic gear are forbidden.", VowType.PERMANENT, GodAlignment.NONE, "equipment", "milestone", "hard", true, tags("armour_magic")));
    }

    // ================== MINOR: small stacking restrictions ==================
    private static void seedMinorVows(Map<String, VowDefinition> map)
    {
        GodAlignment n = GodAlignment.NONE;
        // Consumables.
        add(map, new VowDefinition("no_antipoison", "No Antidote", "Antipoison, antidote and anti-venom are forbidden.", VowType.PERMANENT, n, "consumable", "milestone", "light", true, tags("potion_antipoison")));
        add(map, new VowDefinition("no_antifire", "No Antifire", "Antifire and anti-dragon potions are forbidden.", VowType.PERMANENT, n, "consumable", "milestone", "medium", true, tags("potion_antifire")));
        add(map, new VowDefinition("no_divine_potions", "No Divine Potions", "Divine variants are forbidden. Ordinary potions still work.", VowType.PERMANENT, n, "consumable", "milestone", "light", true, tags("potion_divine")));
        add(map, new VowDefinition("no_overloads", "No Overloads", "Overloads and raid elixirs are forbidden.", VowType.PERMANENT, n, "consumable", "milestone", "medium", true, tags("potion_overload")));
        add(map, new VowDefinition("no_combo_eating", "No Combo Eating", "Karambwan and brews are forbidden. Heal one item at a time.", VowType.PERMANENT, n, "consumable", "milestone", "medium", true, tags("combo_food")));

        // Combat accessories.
        add(map, new VowDefinition("no_slayer_helm", "No Slayer Helmet", "Slayer helmets and black masks are forbidden.", VowType.PERMANENT, n, "combat", "milestone", "medium", true, tags("slayer_helmet")));
        add(map, new VowDefinition("no_salve_amulet", "No Salve Amulet", "The salve amulet is forbidden.", VowType.PERMANENT, n, "combat", "milestone", "light", true, tags("salve_amulet")));
        add(map, new VowDefinition("no_god_capes", "No God Capes", "God capes, max capes and Ava's devices are forbidden.", VowType.PERMANENT, n, "combat", "milestone", "medium", true, tags("god_cape")));
        add(map, new VowDefinition("no_imbued_rings", "No Imbued Rings", "Imbued ring variants are forbidden.", VowType.PERMANENT, n, "combat", "milestone", "light", true, tags("imbued_ring")));
        add(map, new VowDefinition("no_spec_weapons", "No Special Attack Weapons", "Weapons carrying a notable special attack are forbidden.", VowType.PERMANENT, n, "combat", "milestone", "medium", true, tags("spec_weapon")));
        add(map, new VowDefinition("no_defensive_shields", "No Defensive Shields", "Dragonfire shields and spirit shields are forbidden.", VowType.PERMANENT, n, "combat", "milestone", "medium", true, tags("shield_defensive")));
        add(map, new VowDefinition("no_crystal_gear", "No Crystal Equipment", "Crystal armour and crystal weapons are forbidden.", VowType.PERMANENT, n, "combat", "milestone", "medium", true, tags("crystal_equipment")));
        add(map, new VowDefinition("no_blessed_gear", "No Blessed Items", "Blessed equipment and god books are forbidden.", VowType.PERMANENT, n, "combat", "milestone", "light", true, tags("blessed_item")));

        // Ammunition.
        add(map, new VowDefinition("no_high_ammo", "No High-Tier Ammunition", "Rune-grade and better ammunition is forbidden.", VowType.PERMANENT, n, "combat", "milestone", "medium", true, tags("ammo_t50")));
        add(map, new VowDefinition("no_enchanted_bolts", "No Enchanted Bolts", "Enchanted bolt effects are forbidden.", VowType.PERMANENT, n, "combat", "milestone", "light", true, tags("ammo_enchanted_bolts")));

        // Quality of life.
        add(map, new VowDefinition("no_storage_bags", "No Storage Bags", "Looting bags, herb sacks, rune pouches and quivers are forbidden.", VowType.PERMANENT, n, "utility", "milestone", "medium", true, tags("util_storage_bag")));
        add(map, new VowDefinition("no_bone_devices", "No Bone Devices", "Bonecrushers, sanctifiers and offerings are forbidden.", VowType.PERMANENT, n, "utility", "milestone", "light", true, tags("util_bone_device")));
        add(map, new VowDefinition("no_graceful", "No Graceful", "Graceful, agility capes and boots of lightness are forbidden.", VowType.PERMANENT, n, "utility", "milestone", "light", true, tags("graceful_outfit")));

        // Skilling.
        add(map, new VowDefinition("no_high_tier_tools", "No High-Tier Tools", "Dragon-grade and better pickaxes, axes and harpoons are forbidden.", VowType.PERMANENT, n, "skilling", "milestone", "medium", true, tags("tool_high_tier")));
        add(map, new VowDefinition("no_skilling_outfits", "No Skilling Outfits", "Experience-boosting skilling outfits are forbidden.", VowType.PERMANENT, n, "skilling", "milestone", "light", true, tags("tool_skilling_boost")));
    }

    // ================== GOD VOWS (10 per god) ==================
    private static void seedSaradominVows(Map<String, VowDefinition> map)
    {
        GodAlignment g = GodAlignment.SARADOMIN;
        add(map, new VowDefinition("apostle_saradomin", "Apostle of Saradomin", "Prayer must remain above 20 before combat.", VowType.GOD, g, "god", "quest", "medium", true));
        add(map, new VowDefinition("shieldbearer_of_light", "Shieldbearer of Light", "A shield must remain equipped before attacking.", VowType.GOD, g, "god", "quest", "medium", true));
        add(map, new VowDefinition("hand_of_purity", "Hand of Purity", "Poison and venom weapons are forbidden.", VowType.GOD, g, "god", "quest", "medium", true, tags("poison_weapon")));
        add(map, new VowDefinition("untainted_steel", "Untainted Steel", "Barrows equipment is looted from the dead. You may not wear it.", VowType.GOD, g, "god", "quest", "medium", true, tags("armour_barrows")));
        add(map, new VowDefinition("temperance", "Temperance", "The faithful need no elixirs. Combat potions are forbidden.", VowType.GOD, g, "god", "quest", "hard", true, tags("potion_combat")));
        add(map, new VowDefinition("alms_of_the_faithful", "Alms of the Faithful", "Give away your riches. Tier 80 jewellery is forbidden.", VowType.GOD, g, "god", "quest", "medium", true, tags("jewellery_t80")));
        add(map, new VowDefinition("plainsong", "Plainsong", "Sorcery is vanity. Staves, wands and tridents are forbidden.", VowType.GOD, g, "god", "quest", "hard", true, tags("weapon_magic")));
        add(map, new VowDefinition("open_hand", "Open Hand", "One hand stays free for the shield. Two-handed weapons are forbidden.", VowType.GOD, g, "god", "quest", "hard", true, tags("weapon_two_handed")));
        add(map, new VowDefinition("unadorned", "Unadorned", "Vanity is a sin. You may not equip a ring.", VowType.GOD, g, "god", "quest", "medium", true, tags("ring")));
        add(map, new VowDefinition("lightbringer", "Lightbringer", "The light needs no cover of shadow. Barrows gear and poison weapons are forbidden.", VowType.GOD, g, "god", "quest", "hard", true, tags("armour_barrows", "poison_weapon")));
    }

    private static void seedZamorakVows(Map<String, VowDefinition> map)
    {
        GodAlignment g = GodAlignment.ZAMORAK;
        add(map, new VowDefinition("apostle_zamorak", "Apostle of Zamorak", "To enter combat with a monster you must first defeat a Man, Woman or Citizen.", VowType.GOD, g, "god", "boss", "hard", true));
        add(map, new VowDefinition("chaos_tethered", "Chaos-Tethered", "Run energy must be below 50% before combat.", VowType.GOD, g, "god", "boss", "hard", true));
        add(map, new VowDefinition("mark_of_blood", "Mark of Blood", "A raw meat item must be in inventory before combat.", VowType.GOD, g, "god", "boss", "hard", true));
        add(map, new VowDefinition("blood_oath_zealot", "Blood Oath Zealot", "Suffering is devotion. Prayer restores are forbidden.", VowType.GOD, g, "god", "boss", "hard", true, tags("potion_prayer")));
        add(map, new VowDefinition("reckless_abandon", "Reckless Abandon", "Defence is cowardice. You may not equip a shield.", VowType.GOD, g, "god", "boss", "hard", true, tags("shield")));
        add(map, new VowDefinition("scorn_of_comfort", "Scorn of Comfort", "Brews dull the pain that feeds you. They are forbidden.", VowType.GOD, g, "god", "boss", "hard", true, tags("food_brews")));
        add(map, new VowDefinition("bare_faced", "Bare-Faced", "Meet your enemy eye to eye. Helmets are forbidden.", VowType.GOD, g, "god", "boss", "medium", true, tags("helmet")));
        add(map, new VowDefinition("blood_at_arms_length", "Blood at Arm's Length", "Killing from afar is bloodless. Ranged weapons are forbidden.", VowType.GOD, g, "god", "boss", "hard", true, tags("weapon_ranged")));
        add(map, new VowDefinition("no_retreat", "No Retreat", "Flight is heresy. Teleport items are forbidden.", VowType.GOD, g, "god", "boss", "hard", true, tags("travel_teleport_item")));
        add(map, new VowDefinition("feast_on_ash", "Feast on Ash", "Fine food is for the weak. Tier 60 food is forbidden.", VowType.GOD, g, "god", "boss", "medium", true, tags("food_t60")));
    }

    private static void seedGuthixVows(Map<String, VowDefinition> map)
    {
        GodAlignment g = GodAlignment.GUTHIX;
        add(map, new VowDefinition("apostle_guthix", "Apostle of Guthix", "Combat style must change after leaving combat.", VowType.GOD, g, "god", "milestone", "hard", true));
        add(map, new VowDefinition("equilibrium_seeker", "Equilibrium Seeker", "Offensive and defensive prayers are forbidden. Overheads remain permitted.", VowType.GOD, g, "god", "quest", "hard", true));
        add(map, new VowDefinition("keeper_of_balance", "Keeper of Balance", "Inventory weight must stay between 10 and 20kg before combat.", VowType.GOD, g, "god", "quest", "hard", true));
        add(map, new VowDefinition("seeker_of_balance", "Seeker of Balance", "Balance is its own boost. Combat potions are forbidden.", VowType.GOD, g, "god", "milestone", "hard", true, tags("potion_combat")));
        add(map, new VowDefinition("ironshun", "Ironshun", "Torn from the earth and beaten. Metal armour is forbidden.", VowType.GOD, g, "god", "milestone", "hard", true, tags("armour_metal")));
        add(map, new VowDefinition("nothing_lasts", "Nothing Lasts", "All things decay. Degradeable equipment is forbidden.", VowType.GOD, g, "god", "milestone", "hard", true, tags("weapon_degradeable")));
        add(map, new VowDefinition("modest_means", "Modest Means", "Excess upsets the balance. Tier 80 armour is forbidden.", VowType.GOD, g, "god", "milestone", "hard", true, tags("armour_t80")));
        add(map, new VowDefinition("unbound_throat", "Unbound Throat", "Wear no collar, not even a blessed one. Amulets are forbidden.", VowType.GOD, g, "god", "quest", "medium", true, tags("amulet")));
        add(map, new VowDefinition("own_two_feet", "Own Two Feet", "Walk at nature's pace. Stamina and energy potions are forbidden.", VowType.GOD, g, "god", "quest", "medium", true, tags("potion_stamina")));
        add(map, new VowDefinition("balanced_grip", "Balanced Grip", "Neither hand may dominate. Two-handed weapons are forbidden.", VowType.GOD, g, "god", "milestone", "hard", true, tags("weapon_two_handed")));
    }

    private static void seedArmadylVows(Map<String, VowDefinition> map)
    {
        GodAlignment g = GodAlignment.ARMADYL;
        add(map, new VowDefinition("apostle_armadyl", "Apostle of Armadyl", "Open combat with a ranged attack.", VowType.GOD, g, "god", "boss", "hard", true));
        add(map, new VowDefinition("windwalker", "Windwalker", "Metal chest and leg armour are forbidden.", VowType.GOD, g, "god", "boss", "medium", true));
        add(map, new VowDefinition("skybound", "Skybound", "Run energy must be at least 50% before combat.", VowType.GOD, g, "god", "boss", "hard", true));
        add(map, new VowDefinition("skywarden", "Skywarden", "The sky bears no iron. All metal armour is forbidden.", VowType.GOD, g, "god", "boss", "hard", true, tags("armour_metal")));
        add(map, new VowDefinition("feather_tread", "Feather-Tread", "Never be rooted to the ground. Boots are forbidden.", VowType.GOD, g, "god", "boss", "medium", true, tags("boots")));
        add(map, new VowDefinition("unburdened_flight", "Unburdened Flight", "Weight is the enemy of flight. Tier 80 armour is forbidden.", VowType.GOD, g, "god", "boss", "hard", true, tags("armour_t80")));
        add(map, new VowDefinition("both_hands_to_the_bow", "Both Hands to the Bow", "A shield is dead weight aloft. Shields are forbidden.", VowType.GOD, g, "god", "boss", "hard", true, tags("shield")));
        add(map, new VowDefinition("sky_over_grave", "Sky Over Grave", "Take nothing from the tomb. Barrows equipment is forbidden.", VowType.GOD, g, "god", "boss", "medium", true, tags("armour_barrows")));
        add(map, new VowDefinition("thin_air", "Thin Air", "Live light. Brews are forbidden.", VowType.GOD, g, "god", "boss", "hard", true, tags("food_brews")));
        add(map, new VowDefinition("open_sky", "Open Sky", "Nothing between you and the sky. Helmets are forbidden.", VowType.GOD, g, "god", "boss", "medium", true, tags("helmet")));
    }

    private static void seedBandosVows(Map<String, VowDefinition> map)
    {
        GodAlignment g = GodAlignment.BANDOS;
        add(map, new VowDefinition("apostle_bandos", "Apostle of Bandos", "You must always wield a melee weapon before combat.", VowType.GOD, g, "god", "boss", "hard", true));
        add(map, new VowDefinition("berserker_of_bandos", "Berserker of Bandos", "You may not eat while an enemy is targeting you.", VowType.GOD, g, "god", "boss", "hard", true));
        add(map, new VowDefinition("brute_of_bandos", "Brute of Bandos", "You may not wear body armour above tier 30.", VowType.GOD, g, "god", "boss", "medium", true));
        add(map, new VowDefinition("scorn_of_wizardry", "Scorn of Wizardry", "Magic is a coward's trick. Staves and wands are forbidden.", VowType.GOD, g, "god", "boss", "hard", true, tags("weapon_magic")));
        add(map, new VowDefinition("scorn_of_cowardice", "Scorn of Cowardice", "Strike them where they stand. Ranged weapons are forbidden.", VowType.GOD, g, "god", "boss", "hard", true, tags("weapon_ranged")));
        add(map, new VowDefinition("trinketless", "Trinketless", "Baubles win no wars. Tier 80 jewellery is forbidden.", VowType.GOD, g, "god", "boss", "medium", true, tags("jewellery_t80")));
        add(map, new VowDefinition("no_crutch", "No Crutch", "Lean on nothing. Prayer restores are forbidden.", VowType.GOD, g, "god", "boss", "hard", true, tags("potion_prayer")));
        add(map, new VowDefinition("all_offence", "All Offence", "A shield is an admission of weakness. Shields are forbidden.", VowType.GOD, g, "god", "boss", "hard", true, tags("shield")));
        add(map, new VowDefinition("march_never_blink", "March, Never Blink", "An army walks. Teleport items are forbidden.", VowType.GOD, g, "god", "boss", "hard", true, tags("travel_teleport_item")));
        add(map, new VowDefinition("bare_knuckles", "Bare Knuckles", "Feel the weapon in your grip. Gloves are forbidden.", VowType.GOD, g, "god", "boss", "medium", true, tags("gloves")));
    }

    private static void seedZarosVows(Map<String, VowDefinition> map)
    {
        GodAlignment g = GodAlignment.ZAROS;
        add(map, new VowDefinition("apostle_zaros", "Apostle of Zaros", "Only spells from the Ancient Spellbook may be cast.", VowType.GOD, g, "god", "quest", "hard", true));
        add(map, new VowDefinition("shadow_marked", "Shadow-Marked", "You must kill a creature in Arceuus every 30 minutes to keep favour.", VowType.GOD, g, "god", "quest", "hard", true));
        add(map, new VowDefinition("fatebound", "Fatebound", "You cannot flee from combat once engaged.", VowType.GOD, g, "god", "boss", "hard", true));
        add(map, new VowDefinition("the_last_tide", "The Last Tide", "The shadow strikes near or by sorcery. Ranged weapons are forbidden.", VowType.GOD, g, "god", "quest", "hard", true, tags("weapon_ranged")));
        add(map, new VowDefinition("unmarked", "Unmarked", "Wear no banner. Capes and cloaks are forbidden.", VowType.GOD, g, "god", "quest", "medium", true, tags("cape")));
        add(map, new VowDefinition("older_than_graves", "Older Than Graves", "The Empty Lord predates those tombs. Barrows equipment is forbidden.", VowType.GOD, g, "god", "quest", "medium", true, tags("armour_barrows")));
        add(map, new VowDefinition("shadowsilk", "Shadowsilk", "Iron rings out and betrays you. Metal armour is forbidden.", VowType.GOD, g, "god", "quest", "hard", true, tags("armour_metal")));
        add(map, new VowDefinition("ascetic_of_the_empty_lord", "Ascetic of the Empty Lord", "Comfort is a leash. Brews are forbidden.", VowType.GOD, g, "god", "quest", "hard", true, tags("food_brews")));
        add(map, new VowDefinition("unadulterated", "Unadulterated", "Your power is your own. Combat potions are forbidden.", VowType.GOD, g, "god", "quest", "hard", true, tags("potion_combat")));
        add(map, new VowDefinition("no_bindings", "No Bindings", "Swear to nothing you can take off. Rings are forbidden.", VowType.GOD, g, "god", "quest", "medium", true, tags("ring")));
    }

    private static void seedRituals(Map<String, VowDefinition> map)
    {
        add(map, new VowDefinition("the_pilgrim", "The Pilgrim", "Walk to the Chaos Altar and pray.", VowType.RITUAL, GodAlignment.NONE, "ritual", "altar", "medium", true));
        add(map, new VowDefinition("the_purifier", "The Purifier", "Burn bones on a Gilded Altar.", VowType.RITUAL, GodAlignment.NONE, "ritual", "altar", "medium", true));
        add(map, new VowDefinition("the_tidewalker", "The Tidewalker", "Bury a fish at Zeah's shore.", VowType.RITUAL, GodAlignment.NONE, "ritual", "shore", "medium", true));
        add(map, new VowDefinition("the_shadowbound", "The Shadowbound", "Read the Dark Manuscript at the Dark Altar.", VowType.RITUAL, GodAlignment.NONE, "ritual", "altar", "hard", true));
        add(map, new VowDefinition("the_ashwalker", "The Ashwalker", "Scatter volcanic ash at Mor Ul Rek.", VowType.RITUAL, GodAlignment.NONE, "ritual", "location", "medium", true));
        add(map, new VowDefinition("the_wayfarer", "The Wayfarer", "Walk from Lumbridge to Varrock and speak to Reldo in the palace library.", VowType.RITUAL, GodAlignment.NONE, "ritual", "travel", "medium", true));
        add(map, new VowDefinition("the_blood_oath", "The Blood Oath", "Take damage from a Wilderness monster and escape alive.", VowType.RITUAL, GodAlignment.NONE, "ritual", "wilderness", "hard", true));
        add(map, new VowDefinition("the_stonebearer", "The Stonebearer", "Mine granite and drop it at Al Kharid palace.", VowType.RITUAL, GodAlignment.NONE, "ritual", "trade", "medium", true));
        add(map, new VowDefinition("the_herbalist", "The Herbalist", "Collect herbs at Taverley and bless them at the shrine.", VowType.RITUAL, GodAlignment.NONE, "ritual", "location", "medium", false));
        add(map, new VowDefinition("the_sailor", "The Sailor", "Take a boat to Karamja and stand on the dock before combat.", VowType.RITUAL, GodAlignment.NONE, "ritual", "travel", "medium", false));
        add(map, new VowDefinition("the_wanderer", "The Wanderer", "Travel to a random city and speak to a local elder.", VowType.RITUAL, GodAlignment.NONE, "ritual", "travel", "medium", false));
    }

    private static void add(Map<String, VowDefinition> map, VowDefinition definition)
    {
        map.put(definition.getId(), definition);
    }
}
