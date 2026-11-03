# Aurora Client 3.2.1

Maintenance update for Minecraft 1.21.11 on Fabric.

- Smooth Camera now responds consistently across frame rates and resets when the player instance changes. Smooth Camera and FreeLook also apply on the minecart camera path.
- Removed the optional Entity Movement Smoothing filter. Entities use vanilla interpolation.
- Existing configs and profiles can still load with the retired smoothing settings. Fresh installs use the same reviewed HUD defaults as 3.2.0.
