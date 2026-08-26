import os
from PIL import Image, ImageDraw, ImageFont

def create_icon_canvas():
    # Returns a 1536x1536 transparent image and its draw object
    return Image.new("RGBA", (1536, 1536), (0, 0, 0, 0))

def draw_arrow(draw, start, end, width, head_size=80):
    # Draws a line with an arrowhead
    draw.line([start, end], fill=(255, 255, 255, 255), width=width)
    # Simple arrowhead facing right
    if end[0] > start[0] and end[1] == start[1]: # Right-pointing
        draw.polygon([
            (end[0], end[1]),
            (end[0] - head_size, end[1] - head_size // 2),
            (end[0] - head_size, end[1] + head_size // 2)
        ], fill=(255, 255, 255, 255))

def draw_pixel_grid_outline(draw, grid, cell_size=96, fill_color=(255, 255, 255, 255)):
    # Find all outline cells: a cell that is 1 and has at least one 0 neighbor (up, down, left, right)
    rows = len(grid)
    cols = len(grid[0])
    for r in range(rows):
        for c in range(cols):
            if grid[r][c] == 1:
                # Check neighbors
                is_outline = False
                for dr, dc in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                    nr, nc = r + dr, c + dc
                    if nr < 0 or nr >= rows or nc < 0 or nc >= cols or grid[nr][nc] == 0:
                        is_outline = True
                        break
                if is_outline:
                    x0 = c * cell_size
                    y0 = r * cell_size
                    x1 = x0 + cell_size
                    y1 = y0 + cell_size
                    draw.rectangle([x0, y0, x1 - 1, y1 - 1], fill=fill_color)

def save_image(img, name):
    output_dir = "src/main/resources/assets/aurora/textures/gui/module_icons"
    os.makedirs(output_dir, exist_ok=True)
    
    # Custom scale adjustments on top of the base 66%
    scale_adjustments = {
        "hitbox": 0.80,
        "cps": 0.85,
        "reach_display": 1.33,
        "stats": 0.80,
        "waypoints": 0.90,
        "item_physics": 1.25,
    }
    
    factor = 0.66 * scale_adjustments.get(name, 1.0)
    
    # Scale down the image to requested size
    w, h = img.size
    scaled_w = int(w * factor)
    scaled_h = int(h * factor)
    
    # Use LANCZOS high-quality resampling
    try:
        resample_method = Image.Resampling.LANCZOS
    except AttributeError:
        resample_method = Image.LANCZOS
        
    scaled_img = img.resize((scaled_w, scaled_h), resample=resample_method)
    
    # Create a fresh 1536x1536 canvas
    final_img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    # Center the scaled image
    offset_x = (w - scaled_w) // 2
    offset_y = (h - scaled_h) // 2
    final_img.paste(scaled_img, (offset_x, offset_y), scaled_img)
    
    final_img.save(os.path.join(output_dir, f"{name}.png"), "PNG")
    print(f"Generated and scaled {name}.png (Scale factor: {factor:.3f})")

def make_theme():
    # Theme - Paint Palette
    img = create_icon_canvas()
    draw = ImageDraw.Draw(img)
    # Draw a palette base shape
    draw.ellipse([300, 300, 1236, 1236], outline=(255, 255, 255, 255), width=50)
    # Thumb hole
    draw.ellipse([450, 850, 600, 1000], fill=(0, 0, 0, 0), outline=(255, 255, 255, 255), width=40)
    # Paint drops (circles)
    paint_positions = [
        (550, 480), (768, 420), (980, 520), (1050, 750), (950, 980)
    ]
    for pos in paint_positions:
        r = 60
        draw.ellipse([pos[0]-r, pos[1]-r, pos[0]+r, pos[1]+r], fill=(255, 255, 255, 255))
    save_image(img, "theme")

def make_pack_tweaks():
    # Pack Tweaks - Folder
    img = create_icon_canvas()
    draw = ImageDraw.Draw(img)
    # Draw folder tab
    draw.rounded_rectangle([300, 380, 650, 500], radius=40, outline=(255, 255, 255, 255), width=50)
    # Cover the overlap line
    draw.rectangle([325, 475, 625, 525], fill=(0, 0, 0, 0))
    # Main folder body
    draw.rounded_rectangle([300, 480, 1236, 1156], radius=60, outline=(255, 255, 255, 255), width=50)
    save_image(img, "pack_tweaks")

def make_saturation_bar():
    # Saturation Bar - Minecraft Hunger Icon Outline
    img = create_icon_canvas()
    draw = ImageDraw.Draw(img)
    
    # 16x16 hunger icon grid
    hunger_grid = [
        [0,0,0,0,0,0,0,0,0,0,1,1,1,0,0,0],
        [0,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0],
        [0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0],
        [0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0],
        [0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,0],
        [0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,0],
        [0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0],
        [0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0],
        [0,0,0,0,0,0,0,0,1,1,1,1,0,0,0,0],
        [0,0,0,0,0,0,0,1,1,1,1,0,0,0,0,0],
        [0,0,0,0,0,1,1,1,1,0,0,0,0,0,0,0],
        [0,0,0,0,1,1,1,1,0,0,0,0,0,0,0,0],
        [0,0,0,1,1,1,0,0,0,0,0,0,0,0,0,0],
        [0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0],
        [0,1,1,0,1,1,0,0,0,0,0,0,0,0,0,0],
        [0,1,0,0,1,1,0,0,0,0,0,0,0,0,0,0],
    ]
    
    draw_pixel_grid_outline(draw, hunger_grid)
    save_image(img, "saturation_bar")

def make_block_overlay():
    # Block Overlay - Cube
    img = create_icon_canvas()
    draw = ImageDraw.Draw(img)
    
    # 3D isometric cube coordinates
    cx, cy = 768, 768
    top = (cx, cy - 420)
    bottom = (cx, cy + 420)
    left_top = (cx - 380, cy - 200)
    right_top = (cx + 380, cy - 200)
    left_bottom = (cx - 380, cy + 200)
    right_bottom = (cx + 380, cy + 200)
    
    # Outer boundaries
    draw.line([top, left_top, left_bottom, bottom, right_bottom, right_top, top], fill=(255, 255, 255, 255), width=50, joint="round")
    # Inner faces
    draw.line([left_top, (cx, cy), right_top], fill=(255, 255, 255, 255), width=50, joint="round")
    draw.line([(cx, cy), bottom], fill=(255, 255, 255, 255), width=50, joint="round")
    
    save_image(img, "block_overlay")

def make_hitbox():
    # Hitbox - Tall rectangle
    img = create_icon_canvas()
    draw = ImageDraw.Draw(img)
    # Tall bounding box rectangle
    draw.rectangle([450, 200, 1086, 1336], outline=(255, 255, 255, 255), width=50)
    # Eye-level line inside
    draw.line([(450, 550), (1086, 550)], fill=(255, 255, 255, 180), width=30)
    # Front-facing look indicator arrow
    draw_arrow(draw, (768, 550), (950, 550), width=35, head_size=60)
    save_image(img, "hitbox")

def make_cps():
    # CPS - Computer mouse
    img = create_icon_canvas()
    draw = ImageDraw.Draw(img)
    # Mouse body
    draw.rounded_rectangle([450, 250, 1086, 1286], radius=200, outline=(255, 255, 255, 255), width=50)
    # Button dividing line (horizontal and vertical)
    draw.line([(450, 680), (1086, 680)], fill=(255, 255, 255, 255), width=40)
    draw.line([(768, 250), (768, 680)], fill=(255, 255, 255, 255), width=40)
    # Scroll wheel
    draw.rounded_rectangle([738, 380, 798, 550], radius=25, fill=(255, 255, 255, 255))
    save_image(img, "cps")

def make_armor_hud():
    # Armor HUD - Outline a Minecraft chestplate from vanilla image grid
    img = create_icon_canvas()
    draw = ImageDraw.Draw(img)
    
    # 16x16 chestplate grid
    chestplate_grid = [
        [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
        [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
        [0,0,0,1,1,0,0,0,0,0,0,1,1,0,0,0],
        [0,0,1,1,1,1,0,0,0,0,1,1,1,1,0,0],
        [0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0],
        [0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0],
        [0,0,1,1,1,0,0,1,1,0,0,1,1,1,0,0],
        [0,0,1,1,0,0,0,1,1,0,0,0,1,1,0,0],
        [0,0,1,1,0,0,0,1,1,0,0,0,1,1,0,0],
        [0,0,1,1,0,0,1,1,1,1,0,0,1,1,0,0],
        [0,0,1,1,0,0,1,1,1,1,0,0,1,1,0,0],
        [0,0,1,1,0,0,1,1,1,1,0,0,1,1,0,0],
        [0,0,1,1,0,0,1,1,1,1,0,0,1,1,0,0],
        [0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0],
        [0,0,0,1,1,1,1,1,1,1,1,1,1,0,0,0],
        [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
    ]
    
    draw_pixel_grid_outline(draw, chestplate_grid)
    save_image(img, "armor_hud")

def make_reach_display():
    # Reach Display - Text "[3.0 Blocks]"
    img = create_icon_canvas()
    draw = ImageDraw.Draw(img)
    # Load Arial or fall back to default
    try:
        font = ImageFont.truetype("arial.ttf", 160)
    except IOError:
        font = ImageFont.load_default()
    
    text = "[3.0 Blocks]"
    # Center text
    bbox = draw.textbbox((0, 0), text, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    tx = (1536 - tw) // 2
    ty = (1536 - th) // 2
    
    draw.text((tx, ty), text, font=font, fill=(255, 255, 255, 255))
    save_image(img, "reach_display")

def make_stats():
    # Stats Overlay - Stylized page with lines
    img = create_icon_canvas()
    draw = ImageDraw.Draw(img)
    # Page border
    draw.rounded_rectangle([350, 250, 1186, 1286], radius=40, outline=(255, 255, 255, 255), width=50)
    # Dog ear fold at top right (cut off)
    draw.polygon([(950, 250), (1186, 486), (1186, 250)], fill=(0, 0, 0, 0))
    draw.line([(950, 250), (1186, 486)], fill=(255, 255, 255, 255), width=50)
    draw.line([(950, 250), (950, 486), (1186, 486)], fill=(255, 255, 255, 255), width=45)
    # Horizontal lines of text
    lines_y = [580, 750, 920, 1090]
    lengths = [550, 680, 450, 600]
    for y, length in zip(lines_y, lengths):
        draw.rounded_rectangle([450, y, 450 + length, y + 40], radius=15, fill=(255, 255, 255, 220))
    save_image(img, "stats")

def make_waypoints():
    # Waypoints - Sign with an arrow pointing right
    img = create_icon_canvas()
    draw = ImageDraw.Draw(img)
    # Post
    draw.rectangle([738, 700, 798, 1300], fill=(255, 255, 255, 255))
    # Sign Board
    draw.rounded_rectangle([350, 320, 1186, 720], radius=40, outline=(255, 255, 255, 255), width=50)
    # Arrow pointing right inside
    draw_arrow(draw, (480, 520), (1050, 520), width=45, head_size=110)
    save_image(img, "waypoints")

def make_container_preview():
    # Container Preview - Chest
    img = create_icon_canvas()
    draw = ImageDraw.Draw(img)
    # Chest main outline
    draw.rounded_rectangle([350, 380, 1186, 1156], radius=50, outline=(255, 255, 255, 255), width=50)
    # Lid line
    draw.line([(350, 640), (1186, 640)], fill=(255, 255, 255, 255), width=50)
    # Lock/latch plate in center
    draw.rounded_rectangle([718, 570, 818, 710], radius=15, fill=(255, 255, 255, 255))
    save_image(img, "container_preview")

def make_item_physics():
    # Item Physics - Apple falling
    img = create_icon_canvas()
    draw = ImageDraw.Draw(img)
    # Apple main body
    draw.ellipse([500, 480, 1036, 1016], outline=(255, 255, 255, 255), width=50)
    # Apple top and bottom indent lines
    draw.ellipse([668, 430, 868, 510], fill=(0, 0, 0, 0))
    # Stem
    draw.line([(768, 480), (818, 320)], fill=(255, 255, 255, 255), width=45)
    # Leaf
    draw.ellipse([800, 330, 890, 400], fill=(255, 255, 255, 255))
    # Falling motion ripples / downward arrows
    draw.line([(400, 400), (400, 650)], fill=(255, 255, 255, 255), width=35)
    draw.polygon([(400, 650), (360, 600), (440, 600)], fill=(255, 255, 255, 255))
    
    draw.line([(1136, 400), (1136, 650)], fill=(255, 255, 255, 255), width=35)
    draw.polygon([(1136, 650), (1096, 600), (1176, 600)], fill=(255, 255, 255, 255))
    
    save_image(img, "item_physics")

def make_particles():
    # Particles - Diamonds, stars, dots cluster
    img = create_icon_canvas()
    draw = ImageDraw.Draw(img)
    
    # Helper for a 4-point star burst
    def draw_star(cx, cy, size):
        draw.line([(cx - size, cy), (cx + size, cy)], fill=(255, 255, 255, 255), width=30)
        draw.line([(cx, cy - size), (cx, cy + size)], fill=(255, 255, 255, 255), width=30)
        
    # Helper for diamond
    def draw_diamond(cx, cy, size):
        draw.polygon([
            (cx, cy - size),
            (cx + size, cy),
            (cx, cy + size),
            (cx - size, cy)
        ], fill=(255, 255, 255, 255))
        
    # Draw a cluster around center
    draw_star(768, 768, 200)
    draw_diamond(480, 500, 100)
    draw_diamond(1050, 500, 80)
    draw_diamond(520, 1020, 90)
    draw_diamond(980, 1050, 110)
    
    # Some dots
    dots = [(768, 420), (420, 768), (1116, 768), (768, 1116), (580, 620), (950, 620), (580, 910), (950, 910)]
    for dot in dots:
        draw.ellipse([dot[0] - 25, dot[1] - 25, dot[0] + 25, dot[1] + 25], fill=(255, 255, 255, 255))
        
    save_image(img, "particles")

def main():
    make_theme()
    make_pack_tweaks()
    make_saturation_bar()
    make_block_overlay()
    make_hitbox()
    make_cps()
    make_armor_hud()
    make_reach_display()
    make_stats()
    make_waypoints()
    make_container_preview()
    make_item_physics()
    make_particles()
    print("All icons generated, refined, and scaled successfully!")

if __name__ == "__main__":
    main()
