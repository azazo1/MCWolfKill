package com.azazo1.wolfkill;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Level;

/**
 * 每局游戏开始时自动创建新世界，并在中心点附近生成源和目标以及信标
 * todo 自动从源库存世界随机获取源并在游戏世界中center附近放置源
 *  在游戏世界中随机选择目标
 *  在游戏世界中源的附近放置会议按钮
 *  在游戏世界中目标附近放置检查按钮
 */
public class GameWorldIniter {
    public Location center; // 可行动范围的中心点
    public Location sourceStart; // 源的起始位置
    public Location sourceEnd; // 源的末端位置

    public Location targetStart; // 目标的起始位置
    public Location meetingButton; // 紧急会议按钮
    public Location checkButton; // 检查目标房间是否造好的按钮
    public World gameWorld;
    public final int radius; // 玩家可行动半径
    public final int beaconsNum; // 玩家可行动半径
    public final HashMap<Location, Boolean> beaconLocations = new HashMap<>(); // 信标坐标,是否领取


    public GameWorldIniter(int radius, int beaconsNum) {
        this.radius = radius;
        this.beaconsNum = beaconsNum;
    }

    public GameWorldIniter() {
        this(200, 3);
    }

    public World initWorld() {
        var format = new SimpleDateFormat("yyyy.MM.dd.hh.mm.ss");
        WorldCreator wc = new WorldCreator("GameWorld_" + format.format(new Date()));
        wc.environment(World.Environment.NORMAL);
        gameWorld = wc.createWorld();
        if (gameWorld == null) {
            Bukkit.getLogger().log(Level.SEVERE, "无法创建游戏世界");
            Bukkit.shutdown();
        }
        // 世界配置
        gameWorld.setDifficulty(Difficulty.PEACEFUL);
        gameWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        gameWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        return gameWorld;
    }

    /**
     * 选定一局游戏的中心点
     */
    public void selectCenter() {
        center = gameWorld.getSpawnLocation();
    }

    /**
     * 在游戏世界中选择信标位置
     */
    public void selectBeacons() {
        for (int i = 0; i < beaconsNum; i++) {
            Location beaconLoc = randomLocationInCircle(center, radius);
            beaconLocations.put(beaconLoc, false);
            placeBeacon(beaconLoc);
        }
    }

    /**
     * 让玩家都传送到游戏世界
     */
    public void tpPlayerIn(Player player) {
        player.teleport(center);
    }

    /**
     * 将所有玩家都传送到游戏世界
     */
    public void tpAllPlayersIn() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.teleport(center);
        });
    }

    /**
     * 将信标防止在指定位置
     */
    protected void placeBeacon(Location signalLocation) {
        signalLocation.getBlock().setType(Material.BEACON);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Location base = signalLocation.clone().add(new org.bukkit.util.Vector(dx, -1, dz));
                base.getBlock().setType(Material.EMERALD_BLOCK); // 绿宝石基座
            }
        }
    }

    /**
     * 以某点为中心，指定长度为半径，在这个范围内随机取一点（顶层）
     */
    protected Location randomLocationInCircle(Location centerPoint, double radius) {
        double angle = new Random().nextDouble(0, 2 * Math.PI);
        double length = new Random().nextDouble(0, radius);
        int x = (int) (centerPoint.getBlockX() + Math.cos(angle) * length);
        int z = (int) (centerPoint.getBlockZ() + Math.sin(angle) * length);
        Block targetBlock = gameWorld.getHighestBlockAt(x, z);
        return targetBlock.getLocation();
    }

}
