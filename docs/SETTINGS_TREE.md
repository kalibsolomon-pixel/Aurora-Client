# Aurora Client: settings and features tree

A map of everything a player can see, turn on or change in Aurora, as of 2026-11-12, including the Settings sub-tabs. Written to be edited and handed back.

## How to edit this file

- **Move anything anywhere.** Cut a line or a whole block and paste it under a different heading. Indentation shows what is inside what.
- **Rename freely.** Change the bold label to the name you want shown in game.
- **Keep the `{tags}`.** Each item ends with a tag such as `{zoomLevel}` or `{card:zoom}`. That is how I find the real setting in the code when you hand the file back. If you rename or move an item, leave its tag on it. A new item you want added can have no tag; write `{new}`.
- **Leave notes inline** with `>>` at the start of a line, e.g. `>> merge this with Hit Color`. Delete an item by striking it through (`~~like this~~`) rather than removing the line, so I know it was deliberate.
- Each item reads: **Label**: control type · default · what it does. `slider 1–8` gives the range; `toggle` is on/off; `dropdown` lists its choices; `color` is a color picker with opacity; `key` is a keybind; `button` runs an action.

---

## 1. Ways to open Aurora

- **Title screen → "Aurora Settings" button**: Aurora's own title screen has Singleplayer, Multiplayer, Aurora Settings, Options, Quit. `{entry:title}`
- **Vanilla title screen → "Aurora Settings" button**: when Custom Title is off, the button is added to the vanilla menu instead. `{entry:vanillaTitle}`
- **In game → HUD Editor (Right Shift) → "Aurora Settings" button**: there is no direct in-game key for the main screen; this is the path. `{entry:hudEditor}`
- **Better Hitreg "Open Settings Key"**: unbound by default; opens the Aurora screen from in game. `{hitregSettingsKey}`
- **Mod Menu**: support exists but is dormant until a compatible Mod Menu release. `{entry:modmenu}`

---

## 2. Always on (no switch)

These run without a setting of their own, or are part of how Aurora works rather than features.

- **Fail-soft startup**: if one of Aurora's hooks cannot attach (usually a mod conflict), only the feature that needs it turns off for the session and Aurora says which; the game still starts. `{always:mixinHealth}`
- **Glass material**: the blurred-glass look of Aurora's windows and buttons. Shape and strength follow the Theme settings; there is no separate on/off. Paused for a moment when you take an F2 screenshot. `{always:glass}`
- **Aurora styling on vanilla buttons and text fields**: themed buttons on world select, multiplayer and other vanilla screens (tied to Custom Title's hook). `{always:vanillaButtonStyle}`
- **Flat item icons in Aurora screens**: item icons in Aurora's own lists are drawn flat. `{always:flatItemIcons}`
- **Numeric ping in the server list**: part of the Ping feature's hook; shows ping as a number in Multiplayer. `{always:serverListPing}`
- **Automatic latency reduction**: GPU timing used to keep the render queue short; works alongside Settings → Display → Low Latency. `{always:reflex}`
- **Server throttle detection**: notices servers that bundle movement packets and strengthens entity movement smoothing to match. Feeds Animations → Smooth Entity Movement. `{always:throttleDetector}`
- **Per-world cache clearing**: forgets per-entity data (health seen, pops counted) when you change worlds. `{always:worldJoinReset}`
- **Map refresh on block change**: keeps the minimap and world map current when blocks change. `{always:mapBlockRefresh}`
- **Aurora Launcher activity report**: only when started by Aurora Launcher; tells the launcher which world or server you are in so it can show Discord status. The mod itself has no Discord code. `{always:launcherBridge}`
- **Playtime tracking**: counts time in worlds for the Info HUD's Playtime row, whether or not the row is shown. `{always:playtime}`
- **Diagnostics report**: produced on demand from Settings → General → Support → Copy Diagnostics. Nothing is sent anywhere. `{always:diagnostics}`

---

## 3. HUD items

### 3.1 HUD Editor (Right Shift, in game) `{hudEditorKey}`

- **Drag a module**: move it. `{hud:drag}`
- **Drag a corner**: resize it (scale). `{hud:resize}`
- **Shift + left-click**: open that module's Aurora settings. `{hud:openSettings}`
- **Right-click**: hide or show the module in the editor. `{hud:visibility}`
- **Shift + right-click**: lock or unlock its position and size. `{hud:lock}`
- **X badge**: turn the module's feature off. `{hud:disable}`
- **Reset Positions** button: clears all saved positions. `{hud:resetPositions}`
- **Aurora Settings** button: opens the main Aurora screen. `{hud:openAurora}`
- **Esc**: save the layout and leave. `{hud:exit}`

### 3.2 HUD modules

Each one is drawn only when its feature (in the Mods tab) is on. Its settings live in that feature's detail screen.

| HUD module | Default position | Turned on by | Settings in |
|---|---|---|---|
| Info readout `{hud:info}` | top left | Info HUD card (off) | Mods → Info HUD |
| CPS counter `{hud:cps}` | middle left | CPS card (off) | Mods → CPS |
| Armor `{hud:armor}` | bottom right, by the hotbar | Armor HUD card (on) | Mods → Armor HUD |
| Reach `{hud:reach}` | top left | Reach Display card (off) | Mods → Reach Display |
| Potion effects `{hud:potion}` | top right | Potion HUD card (on) | Mods → Potion HUD |
| Ping `{hud:ping}` | top left (hidden in editor by default) | Ping card → Show Ping HUD (off) | Mods → Ping |
| Totem pops `{hud:totem_pop}` | top left | Totem Pop Counter card (off) | Mods → Totem Pop Counter |
| Toggle Sprint/Sneak status, combined `{hud:toggle_sprint_sneak}` | top left | Toggle Sprint/Sneak card (off) + Show Status HUD | Mods → Toggle Sprint/Sneak |
| Toggle Sprint status, separate `{hud:toggle_sprint}` | top left | HUD Display Mode = Individual | Mods → Toggle Sprint/Sneak |
| Toggle Sneak status, separate `{hud:toggle_sneak}` | top left | HUD Display Mode = Individual | Mods → Toggle Sprint/Sneak |
| Keystrokes `{hud:keystrokes}` | top left | Keystrokes card (on) | Mods → Keystrokes |
| Minimap `{hud:minimap}` | bottom right | Minimap card (off) | Mods → Minimap |

Other things drawn on the HUD that are not movable modules:

- **Crosshair** (Mods → Crosshair). `{hud:crosshair}`
- **Saturation on the food bar** (Mods → Saturation Bar). `{hud:saturation}`
- **Alert popups** (Mods → Alerts). `{hud:alerts}`
- **Practice scoreboard**, top left while a score is non-zero (Mods → Better Hitreg → Keybinds). `{hud:hitregScore}`
- **Hotbar bounce** on pickup (Mods → Animations → Hotbar). `{hud:hotbarBounce}`

---

## 4. Aurora Settings screen

### 4.1 Screen layout

- **Mods** tab: the feature cards. `{ui:tabMods}`
- **Settings** tab: global settings on four pages, chosen from sub-tabs across the top (see 4.3). `{ui:tabSettings}`
- **Profiles** button: opens the profile manager (an action, not a tab). `{ui:profiles}`
- **Search modules…** field (Mods tab): filters cards by name. `{ui:search}`
- **List / Grid** layout buttons (Mods tab) · default Grid. `{moduleGridView}`
- **On a card**: left-click or Enter turns the feature on/off; right-click opens its detail screen. `{ui:card}`
- **On a detail screen**: the feature's on/off toggle, its settings, **Reset** (restores this feature's defaults) and **Done**. `{ui:detail}`

### 4.2 Mods tab: 33 cards, in grid order

The default shown after each card title is its card toggle in a fresh install. A card marked *combined* is on when any of its parts is on, and switching it flips every part together.

#### Zoom · on `{card:zoom}`
Narrow your view while a key is held, like a spyglass.
- **Hold Key**: key · C · hold to zoom. `{zoomKey}`
- **Zoom Level**: slider 1.5–50 · 4× · how far it magnifies. `{zoomLevel}`
- **Smoothness**: slider 0.01–2 · 0.2 · how gradually it eases in and out. `{zoomSmoothness}`
- *(Scroll wheel adjusts zoom while held; no setting.)* `{always:zoomScroll}`

#### Full Bright · off `{card:full_bright}`
Brightness past the vanilla maximum.
- **Brightness %**: slider 100–1500 · 500%. `{fullBrightGamma}`

#### No Fog · off `{card:no_fog}`
Pushes distance fog back.
- **Distance Multiplier**: slider 1–100 · 4×. `{fogDistanceMultiplier}`

#### Pack Tweaks · off · combined `{card:pack_tweaks}`
Small visual tweaks: totem particles, shield, fluid clarity.
- *Particles / Presentation*
  - **Particle Size %**: slider 25–100 · 56% · size of the totem pop burst. `{totemParticleScale}`
- *Shield*
  - **Custom Model**: toggle · off · moves your first-person shield. `{shieldTweaksEnabled}`
  - **Style**: dropdown Vanilla / Lowered / Side / Compact · Side. `{shieldStyle}`
  - **Shield Status**: toggle · off · tints shields by cooldown state. `{shieldStatusEnabled}`
  - **Available Color**: color · green, 40% opacity. `{shieldStatusAvailableColor}`
  - **Disabled Color**: color · red, 40% opacity · shown after an axe disables your shield. `{shieldStatusDisabledColor}`
- *Environment / Clarity*
  - **Underwater Fog Multiplier**: slider 1–50 · 8×. `{underwaterFogMultiplier}`
  - **Lava Fog Multiplier**: slider 1–50 · 8×. `{lavaFogMultiplier}`
  - **Hide 1st Person Fire**: toggle · on · hides the lava and on-fire screen tint. `{lavaHideOverlay}`
- >> Hidden parts of the card switch with no row of their own: totem tweaks on/off `{totemTweaksEnabled}`, underwater clarity on/off `{underwaterClarityEnabled}`, lava clarity on/off `{lavaClarityEnabled}`.

#### Entity Health · off `{card:player_health}`
Health shown above mobs (and optionally players).
- **Display Mode**: dropdown Number / Hearts · Number. `{playerHealthDisplayMode}`
- **Only Attacked**: toggle · off · only entities you have hit. `{playerHealthOnlyAttacked}`
- **Show Players**: toggle · off · include other players. `{playerHealthShowPlayers}`

#### FreeLook · off `{card:free_look}`
Look around without turning your character.
- **Hold Key**: key · X. `{freeLookKey}`
- **Camera Sensitivity**: slider 0.1–3 · 1.0. `{freeLookSensitivity}`

#### Saturation Bar · on `{card:saturation_bar}`
Shows hidden saturation on the food bar. No other settings.

#### Block Overlay · on `{card:block_overlay}`
Restyles the outline of the block you are looking at.
- **Show Outline**: toggle · on. `{blockOverlayOutlineEnabled}`
- **Outline Color**: color · black. `{blockOverlayOutlineColor}`
- **Outline Width**: slider 1–6 · 2. `{blockOverlayLineWidth}`
- **Show Fill**: toggle · off. `{blockOverlayFillEnabled}`
- **Fill Color**: color · white, 25% opacity. `{blockOverlayFillColor}`
- **Color Mode**: dropdown Solid / Rainbow · Solid. `{blockOverlayMode}`
- **Rainbow Speed**: slider 0.1–10 · 1. `{blockOverlayRainbowSpeed}`

#### Toggle Sprint/Sneak · off `{card:toggle_sprint_sneak}`
Press once to sprint or sneak instead of holding.
- **Text Color**: color · white · HUD text. `{toggleSprintSneakTextColor}`
- **Toggle Sprint Key**: key · Left Ctrl. `{toggleSprintKey}`
- **Toggle Sneak Key**: key · unbound. `{toggleSneakKey}`
- **Show Status HUD**: toggle · on. `{toggleSprintSneakHudEnabled}`
- **HUD Display Mode**: dropdown Both / Individual / Bracketed Text · Both. `{toggleSprintSneakDisplayMode}`
- **HUD Background**: dropdown None / Solid / Vanilla · None. `{sprintHudBgMode}`
- **HUD Background Color (when SOLID)**: color · black, 50%. `{sprintHudBgColor}`

#### Alerts · off · combined `{card:alerts}`
Warnings for expiring effects and low durability.
- *Effect Expiry*
  - **Effect Expiry Alert**: toggle · off · master for this section. `{effectExpiryAlertEnabled}`
  - **Expiry Threshold (seconds)**: slider 1–60 · 10. `{effectExpiryThresholdSeconds}`
- *Per-Effect Alerts*
  - **Effect list**: search and add the effects that should alert · empty. `{effectExpiryIncludedEffects}`
- *Armor & Tools*
  - **Armor Durability Alert**: toggle · off. `{armorAlertEnabled}`
  - **Also Alert Held Tools**: toggle · off. `{toolDurabilityAlertEnabled}`
  - **Threshold (%)**: slider 1–100 · 10%. `{armorAlertThresholdPct}`
- *Sound*
  - **Play Sound**: toggle · on. `{armorAlertSound}`
  - **Sound Choice**: dropdown Pling / Experience Orb / Bell / Armor Equip / Anvil Land / Note Bass · Pling. `{armorAlertSoundChoice}`
  - **Test Sound**: button · plays the chosen sound. `{action:alertTestSound}`

#### Crosshair · on `{card:crosshair}`
Replaces the crosshair.
- **Style**: dropdown Cross / Dot / Circle / Square / Custom · Dot. `{crosshairStyle}`
- **Size**: slider 1–20 · 2. `{crosshairSize}`
- **Thickness**: slider 1–6 · 1 · hidden for Custom. `{crosshairThickness}`
- **Gap**: slider 0–10 · 0 · hidden for Custom. `{crosshairGap}`
- **Color**: color · white. `{crosshairColor}`
- **Custom Canvas**: pixel editor, shown only for Custom · vanilla 15×15 cross. `{crosshairCustomPixels}`
- **Vanilla Attack Indicator**: toggle · on · attack cooldown under the crosshair. `{crosshairAttackIndicatorEnabled}`
- **Attack Indicator Size %**: slider 25–300 · 100%. `{crosshairAttackIndicatorSize}`
- **Indicator (entity in reach)**: toggle · off · a second crosshair shown when aiming at an entity. `{crosshairIndicatorEnabled}`
  - **Indicator Style**: dropdown (same five) · Custom. `{crosshairIndicatorStyle}`
  - **Indicator Size**: slider 1–20 · 4. `{crosshairIndicatorSize}`
  - **Indicator Thickness**: slider 1–6 · 1. `{crosshairIndicatorThickness}`
  - **Indicator Gap**: slider 0–10 · 0. `{crosshairIndicatorGap}`
  - **Indicator Color**: color · red, about 50%. `{crosshairIndicatorColor}`
  - **Indicator Custom Canvas**: pixel editor. `{crosshairIndicatorCustomPixels}`

#### Hitbox · on · combined `{card:hitbox}`
Draws entity hitboxes like F3+B, with your colors.
- *General*
  - **Toggle Key**: key · B · turns hitboxes on/off. `{hitboxToggleKey}`
  - **Show Eye-Level Line**: toggle · off. `{hitboxEyeLine}`
  - **Show Look Direction**: toggle · off. `{hitboxLookDirection}`
  - **Look Length**: slider 0.1–20 blocks · 0.1. `{hitboxLookLength}`
- *Appearance*
  - **Line Width**: slider 0.1–6 · 2. `{hitboxLineWidth}`
  - **Hitbox Color**: color · yellow. `{hitboxColor}`
- *Projectiles*
  - **Separate Projectile Color**: toggle · on. `{hitboxProjectileColorEnabled}`
  - **Projectile Color**: color · yellow. `{hitboxProjectileColor}`
  - **Projectile Line Width**: slider 0.1–6 · 2. `{hitboxProjectileLineWidth}`
- *Targeted Entity*
  - **Targeted Eye-Level Line**: toggle · off. `{hitboxTargetEyeLine}`
  - **Targeted Look Direction**: toggle · off. `{hitboxTargetLookDirection}`
  - **Targeted Color**: color · dark red. `{hitboxTargetColor}`

#### Better Hitreg · off `{card:better_hitreg}`
Plays your hit feedback the instant you swing. Original project by Jass.
- *Hitreg*
  - **Custom Hitreg**: toggle · on · the original mod's master switch. `{hitregCustomHitreg}`
  - **Hitreg Delay (ms)**: slider 0–300 · 0. `{hitregDelayMs}`
  - **Safe Regs Only**: toggle · on · only when the hit very likely registers. `{hitregSafeRegsOnly}`
  - **Ignore Shield Holders**: toggle · off. `{hitregIgnoreShieldHolders}`
- *Tracking* (each toggle shows a live figure under its label)
  - **Alert Delays**: toggle · off · average server registration delay. `{hitregAlertDelays}`
  - **Alert Ghosts**: toggle · off · share of hits that never registered. `{hitregAlertGhosts}`
  - **Alert Misplaces**: toggle · off · share registered as a different hit type. `{hitregAlertInconsistencies}`
- *Audio*
  - **Mute Other Fights**: toggle · off. `{hitregSilenceOtherFights}`
  - **1.8 Hit Sounds**: toggle · off. `{hitregLegacySounds}`
  - **Mute Non-hit Sounds**: toggle · off. `{hitregSilenceNonHits}`
  - **Mute Your Hits**: toggle · off. `{hitregSilenceSelf}`
  - **Mute Their Hits**: toggle · off. `{hitregSilenceThem}`
  - **Hit Muffling**: slider 0–100% · 0%. `{hitregMuffleAmount}`
  - **Hit Sharpening**: slider 0–100% · 0%. `{hitregSharpenAmount}`
  - **Metronome (ticks)**: slider 0–25 · off (below 10 = off). `{hitregMetronome}`
- *Render*
  - **Hide Other Fights**: toggle · off. `{hitregHideOtherFights}`
  - **Hide Animations**: toggle · off. `{hitregHideAnimations}`
  - **Hide Armor**: toggle · off. `{hitregHideArmor}`
  - **Hide All Particles**: toggle · off. `{hitregHideAllParticles}`
  - **Hide Other Particles**: toggle · off. `{hitregHideOtherParticles}`
  - **Always Hit Particles**: toggle · off. `{hitregParticlesEveryHit}`
  - **Show Target Hitbox**: toggle · off. `{hitregRenderHitbox}`
  - **Show Target Cross**: toggle · off. `{hitregRenderCross}`
  - **Show Server Hitbox**: toggle · off. `{hitregRenderServerHitbox}`
  - **Show Your Hit Range**: toggle · off · 3-block ring. `{hitregRenderYourReach}`
  - **Show Their Hit Range**: toggle · off. `{hitregRenderTheirReach}`
  - **Show Your Jump Range**: toggle · off · 4-block ring. `{hitregRenderYourJump}`
  - **Show Their Jump Range**: toggle · off. `{hitregRenderTheirJump}`
  - **Perfect Hit Color**: toggle · off. `{hitregPerfectHitColor}`
  - **Jump Reset Color**: toggle · off. `{hitregJumpResetColor}`
- *Practice Arena*
  - **Unrender World**: toggle · off · hides all terrain. `{hitregVoidWorld}`
  - **Solid Floor**: toggle · off. `{hitregSolidFloor}`
  - **Floor Grid Size (blocks)**: slider 0–32 · 0 (off). `{hitregFloorGridSize}`
- *Colors* (19 color pickers)
  - Target Hitbox (Out of Reach / In Reach) `{hitregColorHitboxFar}` `{hitregColorHitboxNear}`
  - Target Cross (Out of Reach / In Reach) `{hitregColorCrossFar}` `{hitregColorCrossNear}`
  - Cross with Hitbox (Out of Reach / In Reach) `{hitregColorCrossFarWithHitbox}` `{hitregColorCrossNearWithHitbox}`
  - Server Hitbox `{hitregColorServerHitbox}`
  - Your Hit Range (Out / In) `{hitregColorYourReachFar}` `{hitregColorYourReachNear}`
  - Their Hit Range (Out / In) `{hitregColorTheirReachFar}` `{hitregColorTheirReachNear}`
  - Your Jump Range (Out / In) `{hitregColorYourJumpFar}` `{hitregColorYourJumpNear}`
  - Their Jump Range (Out / In) `{hitregColorTheirJumpFar}` `{hitregColorTheirJumpNear}`
  - Perfect Hit Flash `{hitregColorPerfectHit}`
  - Jump Reset Flash `{hitregColorJumpReset}`
  - Floor Grid `{hitregColorGrid}`
  - Solid Floor `{hitregColorFloor}`
- *Keybinds* (all unbound)
  - **Open Settings Key** `{hitregSettingsKey}`
  - **Switch Hand Key**: swaps main hand (changes the vanilla Main Hand option). `{hitregSwitchHandKey}`
  - **Score: Left +1** `{hitregScoreLeftKey}`
  - **Score: Right +1** `{hitregScoreRightKey}`
  - **Score: Send to Chat**: posts "L-R" in chat. `{hitregScoreSendKey}`
  - **Score: Reset** `{hitregScoreResetKey}`
- *Maintenance*
  - **Reset Tracked Stats**: button · clears the last-100-hits sample. `{action:hitregResetStats}`

#### Hit Color · on `{card:hit_color}`
Recolors the damage flash.
- **Flash Color**: color · red, 40%. `{hitColor}`
- **Tint Armor**: toggle · on. `{hitColorTintArmor}`
- **Tint Armor Trims**: toggle · on · needs Tint Armor. `{hitColorTintArmorTrims}`

#### Glint Color · on `{card:glint_color}`
Recolors the enchantment glint.
- **Glint Color**: color · black. `{glintColor}`
- **Alternative Glint**: toggle · on · lets dark colors show as moving shadow. `{glintColorAlternative}`
- **Intensity (%)**: slider 0–100 · 67%. `{glintColorIntensity}`
- **Tint Armor**: toggle · on. `{glintColorTintArmor}`

#### Info HUD · off `{card:info_module}`
Corner readout of FPS, coordinates and more.
- **Text Color**: color · orange. `{infoModuleTextColor}`
- **Background**: dropdown None / Solid / Vanilla · None. `{infoBgMode}`
- **Background Color (when SOLID)**: color · black, 50%. `{infoBgColor}`
- *Performance*
  - **Show FPS**: toggle · on. `{showFps}`
  - **Show Frame Time**: toggle · off. `{infoShowFrameTime}`
  - **Show Memory**: toggle · off. `{infoShowMemory}`
  - **Show Ping**: toggle · off. `{infoShowPing}`
  - **Show CPS**: toggle · off. `{infoShowCps}`
- *Position*
  - **Show Coords**: toggle · off. `{showCoords}`
  - **Show Facing**: toggle · off. `{infoShowFacing}`
  - **Show In-chunk Position**: toggle · off. `{infoShowChunkLocal}`
  - **Show Chunk Coords**: toggle · off. `{infoShowChunkCoords}`
  - **Show Y vs Sea Level**: toggle · off. `{infoShowYRelSea}`
- *World*
  - **Show Time**: toggle · off. `{showTime}`
  - **Show Biome**: toggle · off. `{infoShowBiome}`
  - **Show Dimension**: toggle · off. `{infoShowDimension}`
  - **Show Light Level**: toggle · off. `{infoShowLightAtFeet}`
- *Playtime*
  - **Show Playtime**: toggle · off. `{infoShowPlaytime}`
  - **Playtime per-World**: toggle · on · this world only vs lifetime total. `{infoPlaytimePerWorld}`

#### CPS · off `{card:cps}`
Clicks-per-second counter.
- **Text Color**: color · HUD default. `{cpsTextColor}`
- **Background**: dropdown · None. `{cpsBgMode}`
- **Background Color (when SOLID)**: color · black, 50%. `{cpsBgColor}`

#### Armor HUD · on `{card:armor_hud}`
Your armor and its durability.
- **Text Color**: color · white. `{armorHudTextColor}`
- **Durability Format**: dropdown Remaining / Remaining of Max / Percentage / None · Remaining. `{armorDurabilityFormat}`
- **Show Durability Bars**: toggle · off. `{armorShowDurabilityBars}`
- **Horizontal Layout**: toggle · on. `{armorHorizontal}`
- **Background**: dropdown · Vanilla (slot frames). `{armorHudBgMode}`
- **Background Color (when SOLID)**: color · black, 50%. `{armorHudBgColor}`

#### Reach Display · off `{card:reach_display}`
Distance of your last hit.
- **Text Color**: color · HUD default. `{reachDisplayTextColor}`
- **Background**: dropdown · None. `{reachBgMode}`
- **Background Color (when SOLID)**: color · black, 50%. `{reachBgColor}`

#### Potion HUD · on `{card:potion_hud}`
Active effects with timers (replaces vanilla's effect icons).
- **Text Color**: color · white. `{potionHudTextColor}`
- **Background**: dropdown · Vanilla. `{potionBgMode}`
- **Background Color (when SOLID)**: color · black, 50%. `{potionBgColor}`

#### Ping · off · combined `{card:ping}`
Latency as numbers.
- **Text Color**: color · HUD default. `{pingHudTextColor}`
- **Show Ping HUD**: toggle · off. `{pingHudEnabled}`
- **Show in Tab List**: toggle · off. `{tabPingEnabled}`
- **Show in Player Nametags**: toggle · off. `{nametagPingEnabled}`

#### Totem Pop Counter · off `{card:totem_pop}`
Counts totem pops.
- **Text Color**: color · HUD default. `{totemPopTextColor}`
- **Reset Counter Key**: key · K. `{totemResetKey}`
- **Show Other Players**: toggle · off. `{totemShowOthers}`
- **Top N Others**: slider 1–10 · 3. `{totemTopOthers}`
- **Reset On Death**: toggle · on. `{totemResetOnDeath}`
- **Show in Player Nametags**: toggle · off. `{nametagTotemPopsEnabled}`
- **Background**: dropdown · None. `{totemPopBgMode}`
- **Background Color (when SOLID)**: color · black, 50%. `{totemPopBgColor}`
- **Reset Now**: button · clears all counts. `{action:totemResetNow}`

#### Waypoints · off `{card:waypoints}`
Markers in the world, plus automatic death waypoints.
- **Manage Waypoints…**: button · opens the Waypoint Manager. `{action:waypointManager}`
  - *Waypoint Manager screen*: search by name, group, dimension or description; Drop at Player Position; per waypoint: Copy coordinates, Edit (name, coordinates, visibility and more; Save / Cancel), Delete. `{screen:waypointManager}`
- **Drop at Player Position**: button. `{action:waypointDrop}`
- **Drop Key**: key · unbound. `{waypointDropKey}`
- **Open Manager Key**: key · unbound. `{waypointManagerKey}`
- **Display Style**: dropdown Beam / Block / Both · Beam. `{waypointDisplay}`
- **Beam Width**: slider 0.1–1 · 0.3. `{waypointBeamWidth}`
- **Block Highlight Radius**: slider 0–4 · 0. `{waypointBlockRadius}`
- **Show Label**: toggle · on. `{waypointShowLabel}`
- **Label Scale**: slider 0.5–2.5 · 1. `{waypointLabelScale}`
- **Death Waypoint**: toggle · on. `{deathWaypointEnabled}`
- **Replace Previous Death Waypoint**: toggle · on. `{deathWaypointReplacesPrevious}`
- **Max Death Waypoints**: slider 1–20 · 3. `{deathWaypointMaxCount}`
- *Visibility & Limits*
  - **Visible Marker Limit**: slider 8–128 · 64. `{waypointMaxVisible}`
  - **World Beam Limit**: slider 1–64 · 16. `{waypointWorldMarkerLimit}`
  - **In-World Range**: slider 64–30000 · 10000. `{waypointMaxDistance}`
  - **Death Waypoint Color**: color · red. `{deathWaypointColor}`

#### Container Preview · on `{card:container_preview}`
Shulker and ender chest contents as a grid tooltip.
- **Shulker Boxes**: toggle · on. `{containerPreviewShulker}`
- **Ender Chest**: toggle · on. `{containerPreviewEnderChest}`
- **Show Stack Counts**: toggle · on. `{containerPreviewShowCounts}`

#### Item Physics · on `{card:item_physics}`
Dropped items lie flat and tumble.
- **Lie Flat on Ground**: toggle · on. `{itemPhysicsFlatOnGround}`
- **Air Tumble Strength**: slider 0–2 · 1. `{itemPhysicsTumbleStrength}`

#### Particles · on `{card:particles}`
Per-particle controls, with a search bar.
- **Particle list**: one row per vanilla particle type, each with Visible toggle, Scale 0–150% and Color tint. Defaults: Enchanted Hit hidden and 22%; Crit 75%. `{particleVisibility}` `{particleScale}` `{particleColor}`

#### Item Scale · off `{card:item_scale}`
Size, rotation and position of held items.
- **Main-Hand Default Scale**: slider 50–150% · 100%. `{mainHandDefaultScale}`
- **Off-Hand Default Scale**: slider 50–150% · 100%. `{offHandDefaultScale}`
- **Per-item overrides**: search an item, "+" to add; each has scale, X/Y/Z position, pitch, yaw, roll. Defaults: Golden Apple 80%, Totem of Undying 75%. `{itemScales}`

#### Held Item Seam Fix · on `{card:held_item_seams}`
Hides hairline seams on held items.
- **Fix Strength**: slider 0–50 (thousandths) · 1. `{heldItemInflation}`

#### Animations · on `{card:animations}`
Swing, view bob, damage tilt, idle sway, entity smoothing.
- *Swing*
  - **Swing Curve**: dropdown Linear / Sine / Smoothstep / Quartic / Quintic / Cubic Out / Quartic Out · Linear. `{swingAnimationCurve}`
- *View Bob*
  - **View Bob Curve**: dropdown (same curves) · Smoothstep. `{viewBobCurve}`
  - **View Bob Amplitude**: slider 0–200% · 100%. `{viewBobAmplitude}`
- *Damage Tilt*
  - **Enable Damage Tilt**: toggle · off · 1.8-style camera roll. `{damageTiltEnabled}`
  - **Damage Tilt Strength**: slider 0–100% · 100%. `{damageTiltStrength}`
- *Idle Sway*
  - **Idle Sway**: toggle · off. `{idleSwayEnabled}`
  - **Idle Sway Curve**: dropdown Sine Breath / Lissajous / Smoothstep Drift / Double Sine / Pendulum / Circular · Sine Breath. `{idleSwayCurve}`
  - **Idle Sway Strength**: slider 0–10 · 1. `{idleSwayStrength}`
- *Hotbar*
  - **Hotbar Bounce**: toggle · off. `{hotbarBounceEnabled}`
- *Entity Movement*
  - **Smooth Entity Movement**: toggle · on. `{entityMovementSmoothingEnabled}`
  - **Smoothing Strength**: slider 0–100% · 30%. `{entityMovementSmoothingStrength}`

#### Keystrokes · on `{card:keystrokes}`
Shows your key and mouse presses.
- **Text Color**: color · theme text color. `{keystrokesTextColor}`
- **Show WASD Keys**: toggle · on. `{keystrokesShowMovement}`
- **Show Mouse Buttons**: toggle · on. `{keystrokesShowMouse}`
- **Show Middle Mouse**: toggle · off. `{keystrokesShowMiddleMouse}`
- **Show CPS Counter**: toggle · off. `{keystrokesShowCps}`
- **Show Spacebar**: toggle · on. `{keystrokesShowSpacebar}`
- **Show Sneak/Sprint**: toggle · off. `{keystrokesShowExtra}`
- **Extra Keys**: key list · none · "+ Add Key". `{keystrokesExtraKeys}`
- **Accent Color**: color · color of a pressed key. `{keystrokesAccentColor}`
- **Panel Opacity**: slider 0–100% · 0%. `{keystrokesBgOpacity}`
- **Scale**: slider · about 1.29 (same as resizing in the HUD Editor). `{hud:keystrokes.scale}`
- **Background**: dropdown · Solid. `{keystrokesBgMode}`
- **Background Color (when SOLID)**: color · near-black. `{keystrokesBgColor}`

#### Minimap · off `{card:minimap}`
Top-down map in a HUD corner.
- **Toggle Key**: key · unbound · show/hide the minimap. `{minimapToggleKey}`
- *Shape & Size*
  - **Size (px)**: slider 64–256 · 128. `{minimapSize}`
  - **Circular**: toggle · off. `{minimapCircular}`
  - **Rotate with Player**: toggle · off. `{minimapRotateWithPlayer}`
- *Terrain*
  - **Show Terrain**: toggle · on. `{minimapShowTerrain}`
  - **Terrain Alpha**: slider 0–1 · 1. `{minimapTerrainAlpha}`
- *Overlays*
  - **Show Waypoints**: toggle · on. `{minimapShowWaypoints}`
  - **Waypoint Distance**: toggle · off. `{minimapShowWaypointDistance}`
  - **Show Entities**: toggle · off. `{minimapShowEntities}`
  - **Show Hostile Mobs**: toggle · off. `{minimapShowHostiles}`
  - **Show Passive Mobs**: toggle · off. `{minimapShowPassives}`
  - **Show Players**: toggle · off. `{minimapShowPlayers}`
  - **Show Self Arrow**: toggle · on. `{minimapShowSelfArrow}`
  - **Show Compass**: toggle · on. `{minimapShowCompass}`
  - **Show Coordinates**: toggle · on. `{minimapShowCoords}`
  - **Show Biome**: toggle · off. `{minimapShowBiome}`
- *Zoom*
  - **Zoom (blocks across)**: slider 16–256 · 100. `{minimapZoom}`
- *Appearance*
  - **Border Color**: color · follows theme accent. `{minimapBorderColor}`
  - **Coordinate Text Color**: color · white. `{minimapCoordColor}`

#### World Map · off `{card:world_map}`
Full-screen map of explored terrain.
- *Navigation & Appearance*
  - **Open World Map**: button · needs a world loaded and the feature on. `{action:openWorldMap}`
    - *World Map screen*: drag to pan, scroll to zoom, dimension button, waypoint markers. `{screen:worldMap}`
  - **Waypoint Names**: toggle · on. `{worldMapShowWaypointNames}`
- *Capture & Caching*
  - **Open Key**: key · Y. `{worldMapKey}`
  - **Capture Budget**: slider 1–8 · 2 chunks per tick. `{worldMapCaptureBudget}`
  - **Region Cache**: slider 8–192 · 64 tiles. `{worldMapCacheRegions}`
- *Related Settings & Data*
  - **Minimap Settings**: link to the Minimap detail screen. `{link:minimap}`
  - **Waypoint Settings**: link to the Waypoints detail screen. `{link:waypoints}`
  - **Legacy Data Import**: opens "Mapping data": copy legacy terrain / copy legacy waypoints. `{screen:mappingData}`

#### Resourcepack Browser · on `{card:resourcepack_browser}`
Install resource packs from Modrinth.
- **Open Browser…**: button · search, preview, one-click install. `{action:openPackBrowser}`

#### Display Settings: moved `{card:miscellaneous}`
No longer a card. Its sections are on the Settings tab (4.3): Frame Pacer, Frame Cap and Low Latency on Display; Smooth Camera and Decoupled Input on Controls; Drag-to-Reorder Servers and Support on General. Each section keeps its own Enabled toggle; the card's combined on/off switch is gone. It stays one feature for fair play and its reset.

### 4.3 Settings tab: four sub-tabs across the top

The tab shows one page at a time, chosen from the sub-tabs in the row above the content (where the Modules tab has its layout buttons and search). Each page keeps its own scroll position. On a page, each entry is a header followed by its rows; only Theme and Custom Title have a switch in the header.

```
 Settings
 ┌────────────┬─────────┬──────────┬─────────┐
 │ Appearance │ Display │ Controls │ General │
 └────────────┴─────────┴──────────┴─────────┘
```

#### Sub-tab: Appearance `{subtab:appearance}`

- *Theme* · header toggle on (off = fixed factory palette instead of your colors) `{card:theme}`
  - **Accent Color**: swatch grid + picker · green (#43A047). `{theme.accent}`
  - **Background Color**: swatch grid + picker · deep purple (#22163A). `{theme.background}`
  - **Corner Style**: segmented Round / Slightly Round / Square · Round. `{theme.roundness}`
  - **Background Opacity**: slider · 33%. `{theme.backgroundOpacity}`
- *Text & Fonts* · no toggle `{card:text_fonts}`
  - **Text Renderer**: dropdown Off / Aurora UI Only / All Text · All Text. `{textRendererMode}`
  - **Client Font**: dropdown Inter Medium / Inter Italic / JetBrains Mono / Roboto / Poppins / Oswald / Nunito / Quicksand / Fira Sans / SansSerif · Fira Sans. `{activeFont}`

#### Sub-tab: Display `{subtab:display}`

- *Frame Rate*
  - **Global Frame Cap**: slider 0–2000, typeable · Off · caps the whole game, menus included. `{frameCapFps}`
  - **UI FPS Limit**: dropdown 15 / 25 / 30 / 60 / 120 FPS / Unlimited · 60 FPS · only while an Aurora screen is open. Replaces the old Interface entry, whose on/off toggle is gone (Unlimited means off). `{guiFpsLimit}`
- *Frame Pacer*
  - **Enabled**: toggle · on. `{smoothFramePacer}`
  - **Strategy**: dropdown Vanilla / Park / Hybrid / Spin · Park. `{framePacingStrategy}`
  - **Low CPU (Hybrid only)**: toggle · off. `{framePacerLowCpuMode}`
  - **Spin Threshold (µs)**: slider 100–2000 · 500. `{framePacerSpinThresholdMicros}`
  - **Park Threshold (µs)**: slider 1000–8000 · 2000. `{framePacerParkThresholdMicros}`
- *Low Latency*
  - **Enabled**: toggle · on · master for this section. `{lowLatencyRender}`
  - **Disable VSync (causes tearing without VRR)**: toggle · off. `{disableVSync}`
  - **Adaptive Render Sleeping (Reflex-style)**: toggle · off · needs Frame Pacer on with a non-Vanilla strategy. `{adaptiveRenderSleeping}`

#### Sub-tab: Controls `{subtab:controls}`

- *Smooth Camera*
  - **Enabled**: toggle · off · adds inertia to mouse look. `{smoothCamera}`
  - **Strength**: slider 0.05–1 · 0.5. `{smoothCameraStrength}`
- *Decoupled Input*
  - **Enabled**: toggle · off · applies mouse movement at the top of each frame. `{inputSamplingDecoupled}`
- *Aurora Keys*
  - **HUD Editor Key**: key · Right Shift. New row; also in vanilla Controls. `{hudEditorKey}`
  - >> Optional later: a read-only list of every Aurora keybind with links to its feature (section 5). `{new}`

#### Sub-tab: General `{subtab:general}`

- *Custom Title* · header toggle on · Aurora's title screen and themed vanilla menus; applies the next time you return to the main menu. `{card:custom_title}`
- *Drag-to-Reorder Servers*
  - **Enabled**: toggle · on · reorder the Multiplayer list by dragging. `{serverListDragReorder}`
- *Support*
  - **Copy Diagnostics**: button · 5-second measurement, report copied to clipboard. `{action:copyDiagnostics}`
  - >> Optional later: an "About" line (version, Minecraft line). `{new}`

### 4.4 Profiles `{ui:profiles}`

A profile is a full snapshot of almost every setting: feature on/offs, all values, keybinds, theme, HUD positions, particle and item-scale lists, waypoints. Not in profiles: which profile is active, and playtime totals.

- **Profile list**: the active one has an "Active" badge; click another to switch to it ("Click to switch"). `{profile:switch}`
- **New Profile**: inline name field, Enter to create, Esc to cancel. `{profile:create}`
- **Rename**: inline, on the name. `{profile:rename}`
- **Duplicate**: per profile, copies all its settings. `{profile:duplicate}`
- **Delete**: per profile; the active profile and `default` cannot be deleted. `{profile:delete}`
- **Done**: back. `{profile:done}`

---

## 5. Keybinds at a glance

All are also listed in vanilla Options → Controls under Aurora. Defaults are for a fresh install.

| Key | Default | Where set |
|---|---|---|
| Zoom (hold) `{zoomKey}` | C | Zoom |
| FreeLook (hold) `{freeLookKey}` | X | FreeLook |
| Toggle Sprint `{toggleSprintKey}` | Left Ctrl | Toggle Sprint/Sneak |
| Toggle Sneak `{toggleSneakKey}` | unbound | Toggle Sprint/Sneak |
| HUD Editor `{hudEditorKey}` | Right Shift | Settings → Controls → Aurora Keys |
| Hitbox toggle `{hitboxToggleKey}` | B | Hitbox |
| Totem reset `{totemResetKey}` | K | Totem Pop Counter |
| Waypoint drop `{waypointDropKey}` | unbound | Waypoints |
| Waypoint manager `{waypointManagerKey}` | unbound | Waypoints |
| World map `{worldMapKey}` | Y | World Map |
| Minimap toggle `{minimapToggleKey}` | unbound | Minimap |
| Better Hitreg × 6 | unbound | Better Hitreg → Keybinds |
| Blur test `{blurTestKey}` | unbound | **only in vanilla Controls** (developer tool) |

---

## 6. Hidden settings (saved in the config, no control on any screen)

Listed so you can decide whether any should get a control, or be removed.

- **Swing Style** (Vanilla / Legacy 1.8 arc) · Vanilla. Its row was removed 2026-10-30, but a saved choice still applies. `{swingStyle}`
- **Theme Mode** (Dark / Light) · Dark. Production is Dark only. `{theme.mode}`
- **Smooth FOV** and its speed · off, 0.15. `{smoothFovEnabled}` `{smoothFovSpeed}`
- **Container Preview: other blocks** · on. `{containerPreviewOtherBlocks}`
- **Minimap background mode** · Solid. `{minimapBgMode}`
- **Minimap uses world map cache** · on. `{minimapUseWorldMapCache}`
- **Waypoint block outline color override** · 0 (none). `{waypointBlockOutlineColorOverride}`
- **Better Hitreg: Track Fights** · on (lifetime fight counters). `{hitregTrackFights}`
- **Global HUD color** · 0 (theme). Fallback for each HUD's Text Color. `{hudColor}`
- **Legacy animation curve** · Smoothstep. `{animationCurve}`
- Internal bookkeeping, not settings: active profile, keybind format version, migration markers, playtime and fight-stat totals, per-world waypoint storage, the old effect-exclusion list.

---

## 7. Things I noticed that you may want to decide on

- **Key defaults disagree with their descriptions.** The Hitbox Toggle Key and Totem Reset Counter Key rows say "Defaults to unbound", but a fresh install binds them to B and K.
- **"Reset to default" in vanilla Controls uses different keys** from a fresh install for FreeLook (V vs X), Toggle Sprint (J vs Left Ctrl), Toggle Sneak (K vs unbound) and World Map (M vs Y).
- **The Alerts card text mentions low hunger**; there is no hunger alert.
- ~~**The HUD Editor key has no row in Aurora.**~~ *(Done: Settings → Controls → Aurora Keys.)*
- **Some features have one card toggle covering several hidden switches** (Pack Tweaks, Alerts, Hitbox, Ping). Turning the card on switches every part on, including parts that ship off. *(Display Settings had this too; its combined switch is gone.)*
- ~~**Display Settings is a "for now" grouping**: five unrelated sections plus Support.~~ *(Done: split across Display, Controls and General.)*
- **Keystrokes Scale** is the only HUD size that has a slider; every other HUD is sized only by dragging in the HUD Editor.
