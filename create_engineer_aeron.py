from PIL import Image

def build_engineer_texture():
    # Load original downloaded skin as the base
    base = Image.open('downloaded_skin.png').convert('RGBA')
    tex = base.copy()

    def set_px(x, y, color):
        tex.putpixel((x, y), color)

    # Palette definitions
    SKIN_BASE = (189, 139, 114, 255)
    SKIN_SHADOW = (181, 123, 103, 255)
    SKIN_DARK = (144, 94, 67, 255)
    NOSE_HIGHLIGHT = (175, 118, 90, 255)

    BRASS_HI = (245, 210, 85, 255)
    BRASS_MID = (210, 165, 45, 255)
    BRASS_SHADOW = (160, 115, 25, 255)
    BRASS_DARK = (115, 80, 20, 255)

    CYAN_SPEC = (210, 250, 255, 255)
    CYAN_HI = (75, 230, 250, 255)
    CYAN_MID = (25, 175, 210, 255)
    CYAN_SHADOW = (15, 120, 155, 255)

    LEATHER_DARK = (45, 30, 20, 255)
    LEATHER_MID = (65, 45, 30, 255)
    LEATHER_WARM = (85, 58, 38, 255)
    LEATHER_HI = (105, 72, 48, 255)

    LINEN_HI = (230, 222, 205, 255)
    LINEN_MID = (212, 202, 182, 255)
    LINEN_SHADOW = (178, 168, 148, 255)

    PANTS_MID = (54, 44, 36, 255)
    PANTS_SHADOW = (42, 34, 28, 255)
    PANTS_HI = (66, 54, 44, 255)

    BOOT_BASE = (35, 30, 26, 255)
    BOOT_SHADOW = (24, 20, 18, 255)
    BOOT_STEEL = (145, 150, 158, 255)
    BOOT_STEEL_HI = (185, 190, 198, 255)

    IRON_WRENCH = (165, 170, 178, 255)
    IRON_WRENCH_HI = (205, 210, 218, 255)
    IRON_WRENCH_SHADOW = (115, 120, 128, 255)

    SOOT_GREASE = (120, 95, 82, 255)

    # -------------------------------------------------------------
    # 1. HEAD ENHANCEMENTS
    # -------------------------------------------------------------
    # Subtle mechanic grease smudge on base face (right cheek: x=9, y=14)
    set_px(9, 14, SOOT_GREASE)

    # --- HAT LAYER (x=32..63, y=0..15) ---
    # Keep the 3D protruding villager nose at x=43..44, y=13..15:
    set_px(43, 13, NOSE_HIGHLIGHT)
    set_px(44, 13, NOSE_HIGHLIGHT)
    set_px(43, 14, SKIN_DARK)
    set_px(44, 14, SKIN_DARK)
    set_px(43, 15, (130, 82, 58, 255))
    set_px(44, 15, (130, 82, 58, 255))

    # Steampunk / Skywright Engineer Goggles on forehead (Hat layer):
    # Front face of Hat is x=40..47, y=8..15.
    # Forehead is y=8..10.
    # Right goggle (character's right, facing viewer's left: x=41..43):
    # Left goggle (character's left, facing viewer's right: x=44..46):
    # Rims, lenses, and bridge:
    # Row y=8 (top rim):
    set_px(41, 8, BRASS_SHADOW)
    set_px(42, 8, BRASS_HI)
    set_px(43, 8, BRASS_MID)
    set_px(44, 8, BRASS_MID)
    set_px(45, 8, BRASS_HI)
    set_px(46, 8, BRASS_SHADOW)

    # Row y=9 (lens centers & bridge):
    set_px(40, 9, LEATHER_DARK)      # strap anchor
    set_px(41, 9, BRASS_MID)         # outer rim
    set_px(42, 9, CYAN_SPEC)         # glowing lens highlight
    set_px(43, 9, BRASS_SHADOW)      # bridge right
    set_px(44, 9, BRASS_SHADOW)      # bridge left
    set_px(45, 9, CYAN_HI)           # glowing lens
    set_px(46, 9, BRASS_MID)         # outer rim
    set_px(47, 9, LEATHER_DARK)      # strap anchor

    # Row y=10 (bottom rim / lower lens):
    set_px(41, 10, BRASS_SHADOW)
    set_px(42, 10, CYAN_SHADOW)      # lower lens glow
    set_px(43, 10, BRASS_MID)
    set_px(44, 10, BRASS_MID)
    set_px(45, 10, CYAN_SHADOW)      # lower lens glow
    set_px(46, 10, BRASS_SHADOW)

    # Leather Goggle Strap on Hat layer:
    # Right side of head (x=32..39, y=8..15):
    for x in range(32, 40):
        set_px(x, 9, LEATHER_MID)
        set_px(x, 10, LEATHER_DARK)
    # Brass buckle on right temple (x=35):
    set_px(35, 9, BRASS_HI)
    set_px(35, 10, BRASS_MID)

    # Left side of head (x=48..55, y=8..15):
    for x in range(48, 56):
        set_px(x, 9, LEATHER_MID)
        set_px(x, 10, LEATHER_DARK)
    # Brass rivet on left temple (x=52):
    set_px(52, 9, BRASS_MID)

    # Back of head (x=56..63, y=8..15):
    for x in range(56, 64):
        set_px(x, 9, LEATHER_MID)
        set_px(x, 10, LEATHER_DARK)

    # Top of goggles (Top face of Hat: x=40..47, y=0..7):
    # Front edge of top face is at y=7:
    for x in range(41, 47):
        set_px(x, 7, BRASS_SHADOW)
    set_px(40, 7, LEATHER_DARK)
    set_px(47, 7, LEATHER_DARK)

    # -------------------------------------------------------------
    # 2. TORSO / BODY (Base: x=16..40, y=16..31 | Jacket: x=16..40, y=32..47)
    # -------------------------------------------------------------
    # --- BASE TORSO ---
    # Front Torso Base (x=20..27, y=20..31):
    # Neck & linen undershirt (y=20..21):
    set_px(20, 20, LEATHER_MID)
    set_px(21, 20, LINEN_HI)
    set_px(22, 20, LINEN_HI)
    set_px(23, 20, SKIN_BASE)     # exposed neck
    set_px(24, 20, SKIN_BASE)
    set_px(25, 20, LINEN_HI)
    set_px(26, 20, LINEN_HI)
    set_px(27, 20, LEATHER_MID)

    set_px(20, 21, LEATHER_WARM)
    set_px(21, 21, LINEN_SHADOW)
    set_px(22, 21, LINEN_MID)
    set_px(23, 21, LINEN_HI)
    set_px(24, 21, LINEN_HI)
    set_px(25, 21, LINEN_MID)
    set_px(26, 21, LINEN_SHADOW)
    set_px(27, 21, LEATHER_WARM)

    # Work Coat / Apron body with leather harness & pressure gauge (y=22..27):
    for y in range(22, 28):
        for x in range(20, 28):
            set_px(x, y, LEATHER_MID)

    # Diagonal harness across chest (from x=21, y=22 to x=26, y=27):
    set_px(21, 22, LEATHER_DARK)
    set_px(22, 23, LEATHER_DARK)
    set_px(23, 24, LEATHER_DARK)
    set_px(24, 25, LEATHER_DARK)
    set_px(25, 26, LEATHER_DARK)
    set_px(26, 27, LEATHER_DARK)
    # Brass rivets on harness:
    set_px(21, 22, BRASS_MID)
    set_px(24, 25, BRASS_HI)

    # Artificer / Skywright Pressure Gauge on upper-left chest (x=25..26, y=22..23):
    set_px(25, 22, BRASS_HI)
    set_px(26, 22, BRASS_MID)
    set_px(25, 23, CYAN_HI)        # glowing dial center
    set_px(26, 23, BRASS_SHADOW)

    # Utility Belt (y=28..31):
    for x in range(20, 28):
        set_px(x, 28, LEATHER_DARK)
        set_px(x, 29, LEATHER_DARK)
        set_px(x, 30, LEATHER_DARK)
        set_px(x, 31, LEATHER_MID)
    # Big Brass Belt Buckle at (x=23..24, y=28..30):
    set_px(23, 28, BRASS_MID)
    set_px(24, 28, BRASS_MID)
    set_px(23, 29, BRASS_HI)
    set_px(24, 29, BRASS_DARK)
    set_px(23, 30, BRASS_SHADOW)
    set_px(24, 30, BRASS_SHADOW)

    # Right side of torso base (x=16..19, y=20..31):
    for y in range(20, 28):
        for x in range(16, 20):
            set_px(x, y, LEATHER_MID)
    for y in range(28, 32):
        for x in range(16, 20):
            set_px(x, y, LEATHER_DARK)

    # Left side of torso base (x=28..31, y=20..31):
    for y in range(20, 28):
        for x in range(28, 32):
            set_px(x, y, LEATHER_MID)
    for y in range(28, 32):
        for x in range(28, 32):
            set_px(x, y, LEATHER_DARK)

    # Back of torso base (x=32..39, y=20..31):
    for y in range(20, 28):
        for x in range(32, 40):
            set_px(x, y, LEATHER_MID)
    # Harness crossing on back:
    for i in range(4):
        set_px(34 + i, 22 + i, LEATHER_DARK)
        set_px(37 - i, 22 + i, LEATHER_DARK)
    set_px(35, 23, BRASS_MID)
    for y in range(28, 32):
        for x in range(32, 40):
            set_px(x, y, LEATHER_DARK)

    # Top of torso base (x=20..27, y=16..19):
    for y in range(16, 20):
        for x in range(20, 28):
            set_px(x, y, LEATHER_MID)
    # Undershirt neck band on top:
    set_px(23, 18, LINEN_HI)
    set_px(24, 18, LINEN_HI)
    set_px(23, 19, SKIN_BASE)
    set_px(24, 19, SKIN_BASE)

    # --- OUTER JACKET LAYER (3D) (x=16..39, y=32..47) ---
    # Front Jacket (x=20..27, y=36..47):
    # 3D Coat Lapels around neck (y=36..38):
    set_px(20, 36, LEATHER_WARM)
    set_px(21, 36, LEATHER_HI)
    set_px(26, 36, LEATHER_HI)
    set_px(27, 36, LEATHER_WARM)

    set_px(20, 37, LEATHER_WARM)
    set_px(21, 37, LEATHER_HI)
    set_px(26, 37, LEATHER_HI)
    set_px(27, 37, LEATHER_WARM)

    set_px(21, 38, LEATHER_WARM)
    set_px(26, 38, LEATHER_WARM)

    # 3D Harness Strap popping out (y=39..42):
    set_px(22, 39, LEATHER_DARK)
    set_px(23, 40, LEATHER_DARK)
    set_px(24, 41, LEATHER_DARK)
    set_px(23, 40, BRASS_HI)

    # 3D Belt Pouches & Tools on Jacket (y=44..47):
    # Right tool pouch (x=20..21, y=44..46):
    set_px(20, 44, LEATHER_HI)
    set_px(21, 44, LEATHER_HI)
    set_px(20, 45, LEATHER_WARM)
    set_px(21, 45, BRASS_MID)     # brass pouch clasp
    set_px(20, 46, LEATHER_DARK)
    set_px(21, 46, LEATHER_DARK)

    # Miniature Wrench tucked into left coat pocket (x=26..27, y=43..46):
    set_px(26, 43, IRON_WRENCH_HI)      # wrench upper jaw
    set_px(27, 43, IRON_WRENCH)
    set_px(26, 44, IRON_WRENCH)         # wrench head
    set_px(27, 44, (0, 0, 0, 0))        # jaw opening
    set_px(26, 45, IRON_WRENCH_SHADOW)  # wrench handle
    set_px(26, 46, LEATHER_DARK)        # pocket rim

    # Right side jacket (x=16..19, y=36..47) - Hip pouch:
    set_px(17, 44, LEATHER_HI)
    set_px(18, 44, LEATHER_HI)
    set_px(17, 45, LEATHER_WARM)
    set_px(18, 45, BRASS_MID)
    set_px(17, 46, LEATHER_DARK)
    set_px(18, 46, LEATHER_DARK)

    # Left side jacket (x=28..31, y=36..47) - Holster loop:
    set_px(29, 44, LEATHER_DARK)
    set_px(30, 44, LEATHER_DARK)
    set_px(29, 45, LEATHER_WARM)
    set_px(30, 45, LEATHER_WARM)

    # Back jacket (x=32..39, y=36..47) - Rear equipment pouch:
    for x in range(35, 38):
        set_px(x, 44, LEATHER_HI)
        set_px(x, 45, LEATHER_WARM)
        set_px(x, 46, LEATHER_DARK)
    set_px(36, 45, BRASS_MID)     # center snap

    # Top of jacket collar (x=20..27, y=32..35):
    for x in range(20, 28):
        set_px(x, 32, LEATHER_MID)
        set_px(x, 35, LEATHER_WARM)
    set_px(20, 33, LEATHER_HI)
    set_px(21, 33, LEATHER_HI)
    set_px(26, 33, LEATHER_HI)
    set_px(27, 33, LEATHER_HI)
    set_px(20, 34, LEATHER_WARM)
    set_px(27, 34, LEATHER_WARM)

    # -------------------------------------------------------------
    # 3. RIGHT ARM (Character's Right Arm: Mechanic Gauntlet & Rolled Sleeve)
    # Base: x=40..55, y=16..31 | Sleeve: x=40..55, y=32..47
    # -------------------------------------------------------------
    # Shoulder / rolled linen sleeve (y=20..22):
    for y in range(20, 23):
        for x in range(40, 56):
            set_px(x, y, LINEN_MID)
    # Top of shoulder:
    for y in range(16, 20):
        for x in range(44, 48):
            set_px(x, y, LINEN_HI)

    # Forearm (y=23..27): Bare arm with Artificer Skywright Gauntlet!
    # Front face of right arm is x=44..47, y=20..31.
    set_px(44, 23, SKIN_BASE)
    set_px(45, 23, SKIN_BASE)
    set_px(46, 23, SKIN_BASE)
    set_px(47, 23, SKIN_BASE)

    # Brass Gauntlet on front forearm (y=24..27):
    for y in range(24, 28):
        for x in range(44, 48):
            set_px(x, y, BRASS_MID)
    # Inlaid cyan power conduit on gauntlet:
    set_px(45, 24, BRASS_HI)
    set_px(46, 24, BRASS_HI)
    set_px(45, 25, CYAN_HI)
    set_px(46, 25, CYAN_SPEC)
    set_px(45, 26, CYAN_MID)
    set_px(46, 26, CYAN_HI)
    set_px(45, 27, BRASS_SHADOW)
    set_px(46, 27, BRASS_SHADOW)

    # Right hand (y=28..31): Fingerless glove & mechanic wrist:
    for y in range(28, 30):
        for x in range(44, 48):
            set_px(x, y, LEATHER_DARK)
    for y in range(30, 32):
        for x in range(44, 48):
            set_px(x, y, SKIN_DARK)

    # Other sides of right arm base (x=40..43, 48..55, y=20..31):
    for y in range(24, 28):
        for x in list(range(40, 44)) + list(range(48, 56)):
            set_px(x, y, BRASS_MID)
    for y in range(28, 32):
        for x in list(range(40, 44)) + list(range(48, 56)):
            set_px(x, y, LEATHER_DARK)

    # --- 3D RIGHT SLEEVE LAYER (x=40..55, y=32..47) ---
    # 3D Rolled-up sleeve cuff at y=38..39 (corresponds to arm y=22..23):
    for x in range(40, 56):
        set_px(x, 38, LINEN_HI)
        set_px(x, 39, LINEN_SHADOW)
    # 3D Gauntlet rim at y=41 (corresponds to arm y=25):
    for x in range(44, 48):
        set_px(x, 41, BRASS_HI)
    set_px(44, 42, BRASS_MID)
    set_px(47, 42, BRASS_MID)

    # -------------------------------------------------------------
    # 4. LEFT ARM (Character's Left Arm: Heavy Work Glove & Rolled Sleeve)
    # Base: x=32..47, y=48..63 | Sleeve: x=48..63, y=48..63
    # -------------------------------------------------------------
    # Shoulder / rolled linen sleeve (y=52..54):
    for y in range(52, 55):
        for x in range(32, 48):
            set_px(x, y, LINEN_MID)
    # Top of shoulder:
    for y in range(48, 52):
        for x in range(36, 40):
            set_px(x, y, LINEN_HI)

    # Forearm bare skin (y=55..57):
    for y in range(55, 58):
        for x in range(32, 48):
            set_px(x, y, SKIN_BASE)

    # Left hand heavy leather work glove (y=58..63):
    for y in range(58, 64):
        for x in range(32, 48):
            set_px(x, y, LEATHER_DARK)
    # Glove cuff highlight on front (x=36..39, y=58):
    for x in range(36, 40):
        set_px(x, 58, LEATHER_WARM)

    # --- 3D LEFT SLEEVE LAYER (x=48..63, y=48..63) ---
    # 3D Rolled-up sleeve cuff at y=54..55:
    for x in range(48, 64):
        set_px(x, 54, LINEN_HI)
        set_px(x, 55, LINEN_SHADOW)
    # 3D Glove cuff at y=58 on sleeve:
    for x in range(52, 56):
        set_px(x, 58, LEATHER_WARM)

    # -------------------------------------------------------------
    # 5. RIGHT LEG (Base: x=0..15, y=16..31 | Pants: x=0..15, y=32..47)
    # -------------------------------------------------------------
    # Trousers (y=20..26):
    for y in range(20, 27):
        for x in range(0, 16):
            set_px(x, y, PANTS_MID)
    # Knee reinforcement patch on front (x=4..7, y=24..26):
    for y in range(24, 27):
        for x in range(4, 8):
            set_px(x, y, LEATHER_WARM)
    set_px(4, 24, BRASS_MID)   # brass rivets on knee pad
    set_px(7, 24, BRASS_MID)
    set_px(4, 26, BRASS_SHADOW)
    set_px(7, 26, BRASS_SHADOW)

    # Heavy Engineer Boot (y=27..31):
    for y in range(27, 32):
        for x in range(0, 16):
            set_px(x, y, BOOT_BASE)
    # Steel toe cap on front (x=4..7, y=30..31):
    for x in range(4, 8):
        set_px(x, 30, BOOT_STEEL_HI)
        set_px(x, 31, BOOT_STEEL)

    # --- 3D RIGHT PANTS LAYER (x=0..15, y=32..47) ---
    # 3D Knee pad (x=4..7, y=40..42):
    for y in range(40, 43):
        for x in range(4, 8):
            set_px(x, y, LEATHER_HI)
    set_px(4, 40, BRASS_HI)
    set_px(7, 40, BRASS_HI)

    # 3D Boot cuff (x=0..15, y=43):
    for x in range(0, 16):
        set_px(x, 43, LEATHER_DARK)

    # 3D Tool loop / strap on side of thigh (x=0..3, y=38..39):
    for x in range(0, 4):
        set_px(x, 38, LEATHER_DARK)
        set_px(x, 39, BRASS_MID)

    # -------------------------------------------------------------
    # 6. LEFT LEG (Base: x=16..31, y=48..63 | Pants: x=0..15, y=48..63)
    # -------------------------------------------------------------
    # Trousers (y=52..58):
    for y in range(52, 59):
        for x in range(16, 32):
            set_px(x, y, PANTS_MID)
    # Knee reinforcement patch on front (x=20..23, y=56..58):
    for y in range(56, 59):
        for x in range(20, 24):
            set_px(x, y, LEATHER_WARM)
    set_px(20, 56, BRASS_MID)
    set_px(23, 56, BRASS_MID)
    set_px(20, 58, BRASS_SHADOW)
    set_px(23, 58, BRASS_SHADOW)

    # Heavy Engineer Boot (y=59..63):
    for y in range(59, 64):
        for x in range(16, 32):
            set_px(x, y, BOOT_BASE)
    # Steel toe cap on front (x=20..23, y=62..63):
    for x in range(20, 24):
        set_px(x, 62, BOOT_STEEL_HI)
        set_px(x, 63, BOOT_STEEL)

    # --- 3D LEFT PANTS LAYER (x=0..15, y=48..63) ---
    # 3D Knee pad (x=4..7, y=56..58):
    for y in range(56, 59):
        for x in range(4, 8):
            set_px(x, y, LEATHER_HI)
    set_px(4, 56, BRASS_HI)
    set_px(7, 56, BRASS_HI)

    # 3D Boot cuff (x=0..15, y=59):
    for x in range(0, 16):
        set_px(x, 59, LEATHER_DARK)

    # Save output files
    target_path = 'src/main/resources/assets/elemental/textures/entity/chronicle_guide.png'
    tex.save(target_path)
    tex.save('chronicle_guide_engineer.png')
    print('Successfully generated engineer skin texture at', target_path)

build_engineer_texture()
