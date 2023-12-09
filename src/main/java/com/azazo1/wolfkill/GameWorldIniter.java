package com.azazo1.wolfkill;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Level;

/**
 * 每局游戏开始时自动创建新世界，并在中心点附近生成源和目标以及信标
 */
public class GameWorldIniter {
    public static final Vector sourceSize = new Vector(8, 8, 8); // 包括基岩底座的源大小
    public Location center; // 可行动范围的中心点
    public Location sourceStart; // 源的起始位置
    public Location sourceEnd; // 源的末端位置

    public Location targetStart; // 目标的起始位置
    public Location meetingButton; // 紧急会议按钮
    public Location checkButton; // 检查目标房间是否造好的按钮
    public World gameWorld;
    public World sourceLibWorld; // 源库世界
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
        sourceLibWorld = Bukkit.createWorld(new WorldCreator("source_lib"));
        if (sourceLibWorld == null) {
            Bukkit.getLogger().log(Level.SEVERE, "无法加载源库世界");
            Bukkit.shutdown();
            return null;
        }

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
        selectCenter();
        selectSource();
        Location sourceLibStart = chooseSource();
        if (sourceLibStart == null) {
            Bukkit.getLogger().log(Level.SEVERE, "源库世界没有任何源");
            Bukkit.shutdown();
            return null;
        }
        placeSource(sourceLibStart);
        selectTarget();
        placeTargetBase();
        selectBeacons();
        prepareCheckButton();
        prepareMeetingButton();
        return gameWorld;
    }

    /**
     * 选定一局游戏的中心点
     */
    private void selectCenter() {
        center = gameWorld.getSpawnLocation();
    }

    /**
     * 在游戏世界中选择信标位置
     */
    private void selectBeacons() {
        for (int i = 0; i < beaconsNum; i++) {
            Location beaconLoc = randomLocationInCircle(center, radius * 0.8, radius);
            Bukkit.getLogger().log(Level.INFO, String.format("信标 %d 位置 (%d, %d, %d)", i, beaconLoc.getBlockX(), beaconLoc.getBlockY(), beaconLoc.getBlockZ()));
            beaconLocations.put(beaconLoc, false);
            placeBeacon(beaconLoc);
        }
    }

    /**
     * 选择源房屋的坐标
     */
    private void selectSource() {
        sourceStart = center.clone().add(10, 0, 0);
        sourceEnd = sourceStart.clone().add(sourceSize.getX() - 1, sourceSize.getY() - 1, sourceSize.getZ() - 1);
    }

    /**
     * 从源库中随机找一个源
     */
    private Location chooseSource() {
        // 查询可用的源数量
        Location initialLoc = new Location(sourceLibWorld, 0, 0, 0);
        int cnt = 0;
        while (initialLoc.getBlock().getType() == Material.BEDROCK) {
            initialLoc.add(8, 0, 0);
            cnt++;
        }
        int chosen;
        if (cnt == 0) {
            return null;
        } else {
            chosen = new Random().nextInt(cnt);
            Bukkit.getLogger().log(Level.INFO, String.format("一共 %d 个源可用, 选择了第 %d 个", cnt, chosen + 1));
        }
        return new Location(sourceLibWorld, chosen * 8, 0, 0);
    }

    /**
     * 放置目标房屋
     *
     * @param sourceLibStart 源库中的房屋起点
     */
    private void placeSource(Location sourceLibStart) {
        for (int x = 0; x < sourceSize.getBlockX(); x++) {
            for (int y = 0; y < sourceSize.getBlockY(); y++) {
                for (int z = 0; z < sourceSize.getBlockZ(); z++) {
                    Block source = sourceStart.clone().add(x, y, z).getBlock();
                    Block sourceLib = sourceLibStart.clone().add(x, y, z).getBlock();
                    source.setType(sourceLib.getType());
                    source.setBlockData(sourceLib.getBlockData());
                }
            }
        }
    }

    /**
     * 选择目标房屋的坐标 (为此村民需要寻找目标位置)
     */
    private void selectTarget() {
        targetStart = randomLocationInCircle(center, 0.5 * radius, 0.6 * radius);
        Bukkit.getLogger().log(Level.INFO, String.format("目标位置 (%d, %d, %d)", targetStart.getBlockX(), targetStart.getBlockY(), targetStart.getBlockZ()));
    }

    /**
     * 放置目标的基座
     */
    private void placeTargetBase() {
        for (int x = 0; x < sourceSize.getBlockX(); x++) {
            for (int z = 0; z < sourceSize.getBlockY(); z++) {
                Block targetBase = targetStart.clone().add(x, 0, z).getBlock();
                targetBase.setType(Material.BEDROCK);
            }
        }
    }

    /**
     * 选择并放置会议按钮
     */
    private void prepareMeetingButton() {
        meetingButton = sourceStart.clone().add(-1, 1, -1);
        for (int i = -1; i <= 0; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 0; k++) {
                    meetingButton.clone().add(i, j, k).getBlock().setType(Material.AIR); // 为按钮腾空空间
                }
            }
        }
        Block buttonBlock = meetingButton.getBlock();
        buttonBlock.setType(Material.EMERALD_BLOCK);
        meetingButton.clone().add(0, -1, 0).getBlock().setType(Material.OAK_WOOD);
    }

    /**
     * 选择并放置检查按钮
     */
    private void prepareCheckButton() {
        checkButton = targetStart.clone().add(-1, 1, -1);
        for (int i = -1; i <= 0; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 0; k++) {
                    checkButton.clone().add(i, j, k).getBlock().setType(Material.AIR); // 为按钮腾空空间
                }
            }
        }
        Block buttonBlock = checkButton.getBlock();
        buttonBlock.setType(Material.EMERALD_BLOCK);
        checkButton.clone().add(0, -1, 0).getBlock().setType(Material.OAK_WOOD);
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
    private void placeBeacon(Location signalLocation) {
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
    protected Location randomLocationInCircle(Location centerPoint, double minRadius, double maxRadius) {
        double angle = new Random().nextDouble(0, 2 * Math.PI);
        double length = new Random().nextDouble(minRadius, maxRadius);
        int x = (int) (centerPoint.getBlockX() + Math.cos(angle) * length);
        int z = (int) (centerPoint.getBlockZ() + Math.sin(angle) * length);
        Block targetBlock = gameWorld.getHighestBlockAt(x, z);
        return targetBlock.getLocation();
    }

}
