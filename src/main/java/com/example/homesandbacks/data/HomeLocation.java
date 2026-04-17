package com.example.homesandbacks.data;

public class HomeLocation {

    public final String world;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;

    public HomeLocation(String world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}
