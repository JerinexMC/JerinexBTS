package org.stepan1411.pvp_bot.bot;

import net.minecraft.class_2390;
import net.minecraft.class_2398;
import net.minecraft.class_243;
import net.minecraft.class_3218;
import net.minecraft.server.MinecraftServer;
import java.util.List;

public class BotPathVisualizer {
    
    private static int tickCounter = 0;
    private static final int UPDATE_INTERVAL = 10;
    private static final int COLOR_GREEN = 0x00FF00;
    
    
    public static void update(MinecraftServer server) {
        tickCounter++;
        
        if (tickCounter < UPDATE_INTERVAL) {
            return;
        }
        tickCounter = 0;
        

        class_3218 world = server.method_30002();
        if (world == null) {
            return;
        }
        

        for (String pathName : BotPath.getVisiblePaths()) {
            BotPath.PathData path = BotPath.getPath(pathName);
            if (path != null && !path.points.isEmpty()) {
                visualizePath(world, path);
            }
        }
    }
    
    
    private static void visualizePath(class_3218 world, BotPath.PathData path) {
        List<class_243> points = path.points;
        

        for (int i = 0; i < points.size(); i++) {
            class_243 point = points.get(i);
            

            for (int angle = 0; angle < 360; angle += 30) {
                double rad = Math.toRadians(angle);
                double offsetX = Math.cos(rad) * 0.3;
                double offsetZ = Math.sin(rad) * 0.3;
                
                world.method_65096(
                    class_2398.field_29642,
                    point.field_1352 + offsetX,
                    point.field_1351 + 0.5,
                    point.field_1350 + offsetZ,
                    1,
                    0, 0, 0,
                    0
                );
            }
            

            world.method_65096(
                class_2398.field_29642,
                point.field_1352,
                point.field_1351 + 1.5,
                point.field_1350,
                3,
                0.1, 0.1, 0.1,
                0
            );
        }
        

        for (int i = 0; i < points.size() - 1; i++) {
            class_243 start = points.get(i);
            class_243 end = points.get(i + 1);
            drawLine(world, start, end);
        }
        

        if (!path.loop && points.size() > 1) {
            class_243 start = points.get(points.size() - 1);
            class_243 end = points.get(0);
            drawLine(world, start, end);
        }
    }
    
    
    private static void drawLine(class_3218 world, class_243 start, class_243 end) {
        double distance = start.method_1022(end);
        int particleCount = (int) (distance * 2);
        

        class_2390 greenDust = new class_2390(COLOR_GREEN, 1.0f);
        
        for (int i = 0; i <= particleCount; i++) {
            double t = (double) i / particleCount;
            double x = start.field_1352 + (end.field_1352 - start.field_1352) * t;
            double y = start.field_1351 + (end.field_1351 - start.field_1351) * t + 0.5;
            double z = start.field_1350 + (end.field_1350 - start.field_1350) * t;
            
            world.method_65096(
                greenDust,
                x, y, z,
                1,
                0, 0, 0,
                0
            );
        }
    }
}
