package com.azazo1.wolfkill;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class WolfKill extends JavaPlugin implements Listener {
    // 开始游戏后每个玩家获取分配身份提示(不是玩家进入世界时)
    // 时间提醒（进度条）
    // 会议时间外限制玩家发言
    // 检查玩家是否还在主世界（不能去其他世界，除了会议时间）
    // 限制玩家游动范围
    // 死亡改为旁观模式
    // 游戏限时
    // 死亡不显示消息
    // 会议世界投票
    // 提交验证房子按钮
    // 防止玩家超出行走范围
    public static final long GAME_DURATION = 35 * 60 * 1000; // 游戏最长持续时间（毫秒）
    public static final int RADIUS = 200; // 可行动的范围（半径，距离源）
    public static final int MEETING_DURATION_SEC = 60 * 2; // 会议时间（秒）
    public static final int STARTING_EFFECT_DURATION = 1 * 60; // 开局药水效果时长（秒）
    public Location center; // 可行动范围的中心点
    public Location sourceStart; // 源的起始位置
    public Location sourceEnd; // 源的末端位置

    public Location targetStart; // 目标的起始位置
    public Location meetingButton; // 紧急会议按钮
    public Location meetingSpawnLocation; // 紧急会议传送点
    public Location checkButton; // 检查目标房间是否造好的按钮
    public final HashMap<Location, Boolean> signLocations = new HashMap<>(); // 信标坐标,是否领取
    public final Vector<ItemStack> signRewards = new Vector<>(); // 信标奖励（狼人专属，随机选择一个）
    public final HashMap<Player, Boolean> isWolf = new HashMap<>(); // 玩家进入世界时获得身份（未开始游戏时）
    public final HashMap<Player, Location> playerLastLocation = new HashMap<>(); // 玩家上一时刻位置（用于把玩家在会议结束时传送回主世界和防止玩家超出行走范围
    public static final ItemStack meetingKeyItem = new ItemStack(Material.GOLD_INGOT, 5); // 玩家开启会议的必需物品
    public volatile GameState state = GameState.PRE_GAMING;
    public World overworld; // 主世界
    public World meetingWorld; // 会议世界
    public final HashMap<Player, Player> voting = new HashMap<>(); // 会议中的投票情况（在非会议过程中此值无效）
    public volatile long gameStartTime = -1; // 游戏开始时间

    enum GameState {
        PRE_GAMING, GAMING, MEETING, OVER_VILLAGER_WIN, OVER_WOLF_WIN
    }

    public BossBar remainTimeBar;

    @Override
    public void onEnable() {
        Bukkit.getPluginCommand("startwolfkill").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        // 保存源和目的房屋位置
        overworld = Bukkit.getWorld("world");
        if (overworld == null) {
            Bukkit.getLogger().log(Level.SEVERE, "无法加载主世界");
            Bukkit.shutdown();
            return;
        }
        sourceStart = new Location(overworld, -105, 69, -2);
        sourceEnd = new Location(overworld, -98, 75, 5);
        targetStart = new Location(overworld, -168, 71, 46);
        checkButton = new Location(overworld, -165, 73, 43);
        center = new Location(overworld, -129, 70, -22);
        meetingButton = new Location(overworld, -115, 69, -7);

        signLocations.put(new Location(overworld, 8, 121, -20), false);
        signLocations.put(new Location(overworld, -177, 111, 141), false);
        signLocations.put(new Location(overworld, -257, 71, -52), false);

        signRewards.add(new ItemStack(Material.CREEPER_SPAWN_EGG, 1));
        ItemStack killingSword = new ItemStack(Material.GOLDEN_SWORD, 1);
        ItemMeta im = killingSword.getItemMeta();
        im.addEnchant(Enchantment.DAMAGE_ALL, 6, true);
        ((Damageable) im).setDamage(31);
        killingSword.setItemMeta(im);
        signRewards.add(killingSword);
        signRewards.add(new ItemStack(Material.DIRT, 1));

        meetingWorld = Bukkit.createWorld(new WorldCreator("meeting"));
        if (meetingWorld == null) {
            Bukkit.getLogger().log(Level.SEVERE, "无法加载紧急会议世界");
            Bukkit.shutdown();
            return;
        }
        meetingSpawnLocation = new Location(meetingWorld, 9, -57, 1);

    }

    // 检查目标和源是否相同
    public boolean checkBuildFinish() {
        for (int x = 0; x <= sourceEnd.getBlockX() - sourceStart.getBlockX(); x++) {
            for (int y = 0; y <= sourceEnd.getBlockY() - sourceStart.getBlockY(); y++) {
                for (int z = 0; z < sourceEnd.getBlockZ() - sourceStart.getBlockZ(); z++) {
                    Block target = targetStart.clone().add(x, y, z).getBlock();
                    Block source = sourceStart.clone().add(x, y, z).getBlock();
                    if (target.getType() != source.getType()) {
//                        Bukkit.broadcast(Component.text(String.format("%s %s %d %d %d", target.getType().name(), source.getType().name(), x, y, z)));
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    // 会议模式禁止破坏方块
    // 会议按钮，检查是否符合开会条件（5个金锭）
    // 保证会议按钮和下面的木桩不被破坏
    // 让狼人获取宝箱物品而村民无法(右键信标)
    // 保护信标周围方块不被破坏
    // 保护源房子不被破坏
    // 保护checkButton不被破坏
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (block == null) {
            return;
        }
        if (state != GameState.GAMING) { // 非游戏过程中不能破坏方块
            event.setCancelled(true);
            return;
        }
        if (sourceStart.getBlockX() <= block.getX() && block.getX() <= sourceEnd.getBlockX() &&
                sourceStart.getBlockZ() <= block.getZ() && block.getZ() <= sourceEnd.getBlockZ() &&
                sourceStart.getBlockY() <= block.getY() && block.getY() <= sourceEnd.getBlockY()
        ) {
            event.setCancelled(true);
            player.sendMessage(Component.text("你不能破坏样本房屋").color(TextColor.color(0xff0000)));
        }
        for (Location signLocation : signLocations.keySet()) { // 检查是否与信标交互（与信标周围方块交互都视为与信标交互）
            if (block.getX() >= signLocation.getBlockX() - 1 && block.getX() <= signLocation.getBlockX() + 1 &&
                    block.getY() >= signLocation.getBlockY() - 1 && block.getY() <= signLocation.getBlockY() + 1 &&
                    block.getZ() >= signLocation.getBlockZ() - 1 && block.getZ() <= signLocation.getBlockZ() + 1
            ) {
                // 防止信标被破坏
                event.setCancelled(true);
                // 检查信标是否已经领取过
                if (signLocations.get(signLocation)) {
                    player.sendMessage(Component.text("此狼人道具已被领取"));
                } else
                    // 检查是否是狼人
                    if (isWolf.get(player)) {
                        player.sendMessage(Component.text("你获取了狼人道具，请检查背包"));
                        // 提供道具
                        ItemStack is = signRewards.remove(new Random().nextInt(0, signRewards.size()));
                        player.getInventory().addItem(is);
//                        Bukkit.broadcast(Component.text("广播：有狼人道具被获取！").color(TextColor.color(0xF0AF3E)));
                        signLocations.put(signLocation, true);
                        signLocation.clone().add(new org.bukkit.util.Vector(0, -1, 0)).getBlock().setType(Material.AIR); // 将信标下面的方块置空，让信标无法发出射线
                    } else {
                        player.sendMessage(Component.text("你不是狼人，无法获取狼人专属道具").color(TextColor.color(0xff0000)));
                    }
                break;
            }
        }
        // checkButton
        if (locationEquals(block.getLocation(), checkButton)) {
            if (state == GameState.GAMING) {
                if (checkBuildFinish()) {
                    Bukkit.broadcast(Component.text("广播：村民完成了房屋建造").color(TextColor.color(0x00ff00)));
                    setVillagerWin();
                } else {
                    player.sendMessage(Component.text("未完成").color(TextColor.color(0xff0000)));
                }
            }
            event.setCancelled(true);
        } else if (locationEquals(block.getLocation(), checkButton.clone().add(new org.bukkit.util.Vector(0, -1, 0)))) {
            event.setCancelled(true);
        }
        // meetingButton
        if (locationEquals(block.getLocation(), meetingButton)) {
            if (state == GameState.GAMING) {
                // 检查是否有开启会议的物品
                if (hasKeyItemAndRemove(player)) {
                    Bukkit.broadcast(Component.text(String.format("广播：%s 开启了紧急会议", player.getName())).color(TextColor.color(0x598FF)));
                    launchMeeting();
                } else {
                    player.sendMessage(Component.text("您不满足开启会议的条件").color(TextColor.color(0xff0000)));
                }
            }
            event.setCancelled(true);
        } else if (locationEquals(block.getLocation(), meetingButton.clone().add(new org.bukkit.util.Vector(0, -1, 0)))) {
            event.setCancelled(true);
        }
    }

    /**
     * 让玩家无声死亡
     */
    public void markDeath(Player player) {
        player.sendMessage(Component.text("你死了").color(TextColor.color(0xff0000)));
        player.setGameMode(GameMode.SPECTATOR);
        player.getLocation().getBlock().setType(Material.BEDROCK); // 放置基岩（作为尸体）
        // 判断哪方阵营被歼灭了
        boolean villagerHasAlive = false;
        boolean wolfHasAlive = false;
        for (Player player1 : isWolf.keySet()) {
            if (player1.getGameMode() != GameMode.SPECTATOR) {
                if (isWolf.get(player1)) {
                    wolfHasAlive = true;
                } else {
                    villagerHasAlive = true;
                }
            }
        }
        if (villagerHasAlive && !wolfHasAlive) {
            Bukkit.broadcast(Component.text("广播：狼被歼灭"));
            setVillagerWin();
        } else if (!villagerHasAlive && wolfHasAlive) {
            Bukkit.broadcast(Component.text("广播：村民被歼灭"));
            setWolfWin();
        }
    }

    // 2分钟后结束会议
    // 传送玩家到会议世界
    // 检查投票结果
    // 测试投票部分是否正常
    private void launchMeeting() {
        state = GameState.MEETING;
        // 传送到会议世界
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.teleport(meetingSpawnLocation);
        }
        new BukkitRunnable() { // 结束会议
            @Override
            public void run() {
                // 结束会议状态
                state = GameState.GAMING;
                // 检查投票结果
                Player votedPlayer = getMostVoted();
                if (votedPlayer == null) {
                    Bukkit.broadcast(Component.text("广播：投票结束，没人被票出"));
                } else {
                    Bukkit.broadcast(Component.text(String.format("广播：投票结束，%s 被票出", votedPlayer.getName())));
                    markDeath(votedPlayer);
                }
                // 传送回主世界
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.teleport(playerLastLocation.get(onlinePlayer));
                }
            }
        }.runTaskLater(this, MEETING_DURATION_SEC * 20); // 两分钟后
    }

    /**
     * 获取会议中被投票数最多的玩家
     * 在平票或者没人投票时返回null
     */
    public Player getMostVoted() {
        HashMap<Player, Integer> playerVotedCnt = new HashMap<>();
        int maxVotes = 0;
        Vector<Player> maxVoted = new Vector<>();
        for (Player player : voting.values()) {
            int newVote;
            if (playerVotedCnt.containsKey(player)) {
                newVote = playerVotedCnt.get(player) + 1;
            } else {
                newVote = 1;
            }
            playerVotedCnt.put(player, newVote);
            if (newVote > maxVotes) {
                maxVoted.clear();
                maxVoted.add(player);
                maxVotes = newVote;
            } else if (newVote == maxVotes) {
                maxVoted.add(player);
            }
        }

        if (maxVoted.size() == 1) {
            return maxVoted.get(0);
        } else { // 平票或者没人投票
            return null;
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (state == GameState.MEETING) { // 会议中意外死亡
            event.setCancelled(true);
            event.getPlayer().spigot().respawn();
            event.getPlayer().teleport(meetingSpawnLocation);
            event.getPlayer().sendMessage(Component.text("你在会议中意外死亡，已经将你重新加入会议").color(TextColor.color(0x0000ff)));
        } else if (state == GameState.GAMING) { // 无声死亡
            event.setCancelled(true);
            markDeath(event.getPlayer());
        }
    }

    @EventHandler
    public void onEntityHit(EntityDamageByEntityEvent event) {
        if (state == GameState.MEETING) { // 会议中攻击变成投票
            if (event.getEntity() instanceof Player player) {
                if (event.getDamager() instanceof Player damager) { // 玩家之间的攻击
                    voting.put(damager, player);
                    damager.sendMessage(Component.text(String.format("你将票投给了 %s", player.getName())));
                }
                event.setCancelled(true);
            }
        } else if (state == GameState.GAMING) {
            // ignore
        } else { // 其他游戏阶段禁止pvp
            if (event.getEntity() instanceof Player player) {
                if (event.getDamager() instanceof Player damager) { // 玩家之间的攻击
                    event.setCancelled(true);
                }
                if (event.getDamager() instanceof Arrow arrow) { // 玩家被弓箭射击
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerSpoke(AsyncChatEvent event) {
        if (state == GameState.GAMING || event.getPlayer().getGameMode() == GameMode.SPECTATOR) { // 游戏中不能聊天
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        player.teleport(center);
        playerLastLocation.put(player, center);
        // 在不同游戏阶段进入世界的游戏模式
        if (state == GameState.PRE_GAMING || state == GameState.OVER_WOLF_WIN || state == GameState.OVER_VILLAGER_WIN) {
            player.setGameMode(GameMode.SURVIVAL);
        } else {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location newLocation = player.getLocation();
        Location horizontalizedLocation = newLocation.clone();
        horizontalizedLocation.setY(center.getY());

        // 检查玩家是否在主世界或者会议世界中
        if (newLocation.getWorld().getUID() != overworld.getUID() && newLocation.getWorld().getUID() != meetingWorld.getUID()) {
            Bukkit.broadcast(Component.text(String.format("广播：玩家 %s 试图逃离世界", player.getName())).color(TextColor.color(0xFF00D2)));
            player.teleport(center);
            return;
        }

        if (newLocation.getWorld().getUID() == overworld.getUID() && horizontalizedLocation.distance(center) > RADIUS) { // 防止超出行走范围（不计算Y轴距离）
            player.teleport(playerLastLocation.get(player));
        } else if (newLocation.getWorld().getUID() == overworld.getUID()) { // 在主世界内且在游走范围内则保存位置
            playerLastLocation.put(player, newLocation);
        }
    }

    public void onGameOver() {
        var players = Bukkit.getOnlinePlayers();
        players.forEach((Consumer<Player>) player -> {
            player.setGameMode(GameMode.SURVIVAL);
        });
    }

    public void setVillagerWin() {
        state = GameState.OVER_VILLAGER_WIN;
        Bukkit.broadcast(Component.text("广播：村民胜利"));
        onGameOver();
    }

    public void setWolfWin() {
        state = GameState.OVER_WOLF_WIN;
        Bukkit.broadcast(Component.text("广播：狼胜利"));
        onGameOver();
    }

    public void startGame() {
        state = GameState.GAMING;
        // 选定玩家当狼人(四个玩家就一个狼人，五个及以上就两个狼人）
        var players = Bukkit.getOnlinePlayers();
        players.forEach(player -> {
            player.getInventory().clear(); // 开始游戏时时清除物品
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20);
            player.clearActivePotionEffects();
            player.setSaturation(20);
            player.setFoodLevel(20);
            player.teleport(center);
            // 开局隐身和保持血量一段时间
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, STARTING_EFFECT_DURATION * 20, 1, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, STARTING_EFFECT_DURATION * 20, 3, true, false));
        });
        if (players.size() < 5) {
            AtomicInteger cnt = new AtomicInteger(0);
            int wolf = new Random().nextInt(0, players.size());
            players.forEach((Consumer<Player>) player -> {
                boolean isWolf_ = cnt.getAndIncrement() == wolf;
                isWolf.put(player, isWolf_);
                player.sendMessage(Component.text(String.format("游戏开始，你%s是狼人", isWolf_ ? "" : "不")).color(TextColor.color(0x3464FF)));
            });
        } else {
            int wolf1 = new Random().nextInt(0, players.size());
            int wolf2 = new Random().nextInt(0, players.size() - 1);
            if (wolf2 >= wolf1) { // 保证 2 和 1 不重复
                wolf2++;
            }
            final int wolf2_ = wolf2;
            AtomicInteger cnt = new AtomicInteger(0);
            players.forEach((Consumer<Player>) player -> {
                int i = cnt.getAndIncrement();
                boolean isWolf_ = i == wolf1 || i == wolf2_;
                isWolf.put(player, isWolf_);
                player.sendMessage(Component.text(String.format("游戏开始，你%s是狼人", isWolf_ ? "" : "不")).color(TextColor.color(0x3464FF)));
            });
        }
        // 显示进度条
        gameStartTime = System.currentTimeMillis();
        remainTimeBar = Bukkit.createBossBar("剩余时间", BarColor.PURPLE, BarStyle.SOLID, BarFlag.PLAY_BOSS_MUSIC);
        for (Player player : players) {
            remainTimeBar.addPlayer(player);
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (state == GameState.OVER_WOLF_WIN || state == GameState.OVER_VILLAGER_WIN) {
                    remainTimeBar.setVisible(false);
                    this.cancel();
                    return;
                }
                long nowTime;
                boolean isTimeout = (nowTime = System.currentTimeMillis()) > gameStartTime + GAME_DURATION;
                boolean inMeeting = state == GameState.MEETING;
                if (!isTimeout || inMeeting) { // 直到会议结束才判断游戏结束
                    remainTimeBar.setProgress(Math.max(0, 1 - (nowTime - gameStartTime) * 1.0 / GAME_DURATION));
                } else { // 游戏超时
                    remainTimeBar.setVisible(false);
                    if (state == GameState.GAMING) {
                        Bukkit.broadcast(Component.text("广播：游戏时间结束"));
                        setWolfWin();
                    }
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0, 20); // 一秒循环一次
    }

    /**
     * 检查玩家有开启会议的物品，有则删去指定数量
     */
    private boolean hasKeyItemAndRemove(Player player) {
        PlayerInventory inventory = player.getInventory();
        if (inventory.containsAtLeast(meetingKeyItem, meetingKeyItem.getAmount())) {
            inventory.removeItem(meetingKeyItem);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("startwolfkill")) {
            startGame();
            return true;
        }
        return false;
    }

    /**
     * 判断两个方块坐标是否相等
     */
    public boolean locationEquals(Location loc1, Location loc2) {
        if (loc1.getBlockX() == loc2.getBlockX()) {
            if (loc1.getBlockY() == loc2.getBlockY()) {
                return loc1.getBlockZ() == loc2.getBlockZ();
            }
        }
        return false;
    }
}
