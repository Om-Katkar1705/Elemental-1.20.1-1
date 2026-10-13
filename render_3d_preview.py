from PIL import Image, ImageDraw
import numpy as np

def render_minecraft_model(skin_path, out_path):
    skin = Image.open(skin_path).convert('RGBA')

    # Canvas
    W, H = 800, 500
    canvas = Image.new('RGBA', (W, H), (24, 26, 32, 255))
    draw = ImageDraw.Draw(canvas)

    # Gradient background
    for y in range(H):
        t = y / H
        r = int(24 + 12 * t)
        g = int(28 + 15 * t)
        b = int(36 + 20 * t)
        draw.line([(0, y), (W, y)], fill=(r, g, b, 255))

    # We will do a clean 3D isometric box render for each body part!
    # A box has 6 faces: top, bottom, front, back, left, right.
    # In Minecraft coordinates (X: right/left, Y: down, Z: forward/back)
    
    def project(x, y, z, ox, oy, scale=9.0, angle_y=0.45, angle_x=0.25):
        # Rotate around Y axis
        cos_y, sin_y = np.cos(angle_y), np.sin(angle_y)
        x1 = x * cos_y + z * sin_y
        z1 = -x * sin_y + z * cos_y
        
        # Rotate around X axis
        cos_x, sin_x = np.cos(angle_x), np.sin(angle_x)
        y1 = y * cos_x - z1 * sin_x
        z2 = y * sin_x + z1 * cos_x
        
        # Isometric projection
        px = ox + x1 * scale
        py = oy + y1 * scale
        return px, py, z2

    def draw_box(uvs, box_pos, box_size, ox, oy, scale, angle_y, angle_x, dilation=0.0):
        bx, by, bz = box_pos
        dx, dy, dz = box_size
        d = dilation

        # 8 vertices of box
        verts = [
            (bx - d, by - d, bz - d),           # 0: -X, -Y, -Z
            (bx + dx + d, by - d, bz - d),      # 1: +X, -Y, -Z
            (bx + dx + d, by + dy + d, bz - d), # 2: +X, +Y, -Z
            (bx - d, by + dy + d, bz - d),      # 3: -X, +Y, -Z
            (bx - d, by - d, bz + dz + d),      # 4: -X, -Y, +Z
            (bx + dx + d, by - d, bz + dz + d), # 5: +X, -Y, +Z
            (bx + dx + d, by + dy + d, bz + dz + d), # 6: +X, +Y, +Z
            (bx - d, by + dy + d, bz + dz + d), # 7: -X, +Y, +Z
        ]

        proj_verts = [project(v[0], v[1], v[2], ox, oy, scale, angle_y, angle_x) for v in verts]

        # Faces defined by (vertex indices, normal vector, uv rect (u1, v1, u2, v2), light shade factor)
        # Note: In standard player skin:
        # Front face is -Z (or +Z depending on coord).
        # Let -Z be Front, +Z be Back, -X be Right, +X be Left.
        faces = [
            # Front face (-Z): verts 0, 1, 2, 3
            ('front', [3, 2, 1, 0], (0, 0, -1), uvs['front'], 1.0),
            # Back face (+Z): verts 5, 4, 7, 6
            ('back', [4, 5, 6, 7], (0, 0, 1), uvs['back'], 0.75),
            # Top face (-Y): verts 4, 5, 1, 0
            ('top', [0, 1, 5, 4], (0, -1, 0), uvs['top'], 1.15),
            # Bottom face (+Y): verts 3, 2, 6, 7
            ('bottom', [7, 6, 2, 3], (0, 1, 0), uvs['bottom'], 0.6),
            # Right face (-X): verts 4, 0, 3, 7
            ('right', [4, 0, 3, 7], (-1, 0, 0), uvs['right'], 0.85),
            # Left face (+X): verts 1, 5, 6, 2
            ('left', [1, 5, 6, 2], (1, 0, 0), uvs['left'], 0.9),
        ]

        # Compute average Z for depth sorting
        rendered_faces = []
        for name, v_idx, normal, uv, shade in faces:
            # Transform normal to see if face is pointing forward
            cos_y, sin_y = np.cos(angle_y), np.sin(angle_y)
            nx1 = normal[0] * cos_y + normal[2] * sin_y
            nz1 = -normal[0] * sin_y + normal[2] * cos_y
            cos_x, sin_x = np.cos(angle_x), np.sin(angle_x)
            ny1 = normal[1] * cos_x - nz1 * sin_x
            nz2 = normal[1] * sin_x + nz1 * cos_x

            # If nz2 < 0, face points towards camera!
            if nz2 < 0:
                avg_z = sum(proj_verts[i][2] for i in v_idx) / 4.0
                poly_pts = [(proj_verts[i][0], proj_verts[i][1]) for i in v_idx]
                rendered_faces.append((avg_z, name, poly_pts, uv, shade))

        return rendered_faces

    # Define all model parts
    # (Head, Body, Right Arm, Left Arm, Right Leg, Left Leg)
    parts = [
        # Head (8x8x8) centered at X=0, Y=-8 to 0, Z=-4 to 4
        {
            'pos': (-4, -8, -4), 'size': (8, 8, 8),
            'base_uvs': {
                'top': (8, 0, 16, 8), 'bottom': (16, 0, 24, 8),
                'right': (0, 8, 8, 16), 'front': (8, 8, 16, 16),
                'left': (16, 8, 24, 16), 'back': (24, 8, 32, 16)
            },
            'outer_uvs': {
                'top': (40, 0, 48, 8), 'bottom': (48, 0, 56, 8),
                'right': (32, 8, 40, 16), 'front': (40, 8, 48, 16),
                'left': (48, 8, 56, 16), 'back': (56, 8, 64, 16)
            },
            'dilation': 0.5
        },
        # Body (8x12x4) from Y=0 to 12
        {
            'pos': (-4, 0, -2), 'size': (8, 12, 4),
            'base_uvs': {
                'top': (20, 16, 28, 20), 'bottom': (28, 16, 36, 20),
                'right': (16, 20, 20, 32), 'front': (20, 20, 28, 32),
                'left': (28, 20, 32, 32), 'back': (32, 20, 40, 32)
            },
            'outer_uvs': {
                'top': (20, 32, 28, 36), 'bottom': (28, 32, 36, 36),
                'right': (16, 36, 20, 48), 'front': (20, 36, 28, 48),
                'left': (28, 36, 32, 48), 'back': (32, 36, 40, 48)
            },
            'dilation': 0.35
        },
        # Right Arm (4x12x4) at X=-8 to -4
        {
            'pos': (-8, 0, -2), 'size': (4, 12, 4),
            'base_uvs': {
                'top': (44, 16, 48, 20), 'bottom': (48, 16, 52, 20),
                'right': (40, 20, 44, 32), 'front': (44, 20, 48, 32),
                'left': (48, 20, 52, 32), 'back': (52, 20, 56, 32)
            },
            'outer_uvs': {
                'top': (44, 32, 48, 36), 'bottom': (48, 32, 52, 36),
                'right': (40, 36, 44, 48), 'front': (44, 36, 48, 48),
                'left': (48, 36, 52, 48), 'back': (52, 36, 56, 48)
            },
            'dilation': 0.3
        },
        # Left Arm (4x12x4) at X=4 to 8
        {
            'pos': (4, 0, -2), 'size': (4, 12, 4),
            'base_uvs': {
                'top': (36, 48, 40, 52), 'bottom': (40, 48, 44, 52),
                'right': (32, 52, 36, 64), 'front': (36, 52, 40, 64),
                'left': (40, 52, 44, 64), 'back': (44, 52, 48, 64)
            },
            'outer_uvs': {
                'top': (52, 48, 56, 52), 'bottom': (56, 48, 60, 52),
                'right': (48, 52, 52, 64), 'front': (52, 52, 56, 64),
                'left': (56, 52, 60, 64), 'back': (60, 52, 64, 64)
            },
            'dilation': 0.3
        },
        # Right Leg (4x12x4) at X=-4 to 0, Y=12 to 24
        {
            'pos': (-4, 12, -2), 'size': (4, 12, 4),
            'base_uvs': {
                'top': (4, 16, 8, 20), 'bottom': (8, 16, 12, 20),
                'right': (0, 20, 4, 32), 'front': (4, 20, 8, 32),
                'left': (8, 20, 12, 32), 'back': (12, 20, 16, 32)
            },
            'outer_uvs': {
                'top': (4, 32, 8, 36), 'bottom': (8, 32, 12, 36),
                'right': (0, 36, 4, 48), 'front': (4, 36, 8, 48),
                'left': (8, 36, 12, 48), 'back': (12, 36, 16, 48)
            },
            'dilation': 0.3
        },
        # Left Leg (4x12x4) at X=0 to 4, Y=12 to 24
        {
            'pos': (0, 12, -2), 'size': (4, 12, 4),
            'base_uvs': {
                'top': (20, 48, 24, 52), 'bottom': (24, 48, 28, 52),
                'right': (16, 52, 20, 64), 'front': (20, 52, 24, 64),
                'left': (24, 52, 28, 64), 'back': (28, 52, 32, 64)
            },
            'outer_uvs': {
                'top': (4, 48, 8, 52), 'bottom': (8, 48, 12, 52),
                'right': (0, 52, 4, 64), 'front': (4, 52, 8, 64),
                'left': (8, 52, 12, 64), 'back': (12, 52, 16, 64)
            },
            'dilation': 0.3
        },
    ]

    def render_character_view(ox, oy, angle_y, angle_x, scale=10.5):
        all_faces = []
        for p in parts:
            # Base layer
            base_faces = draw_box(p['base_uvs'], p['pos'], p['size'], ox, oy, scale, angle_y, angle_x, dilation=0.0)
            all_faces.extend([(f[0], False, f[1], f[2], f[3], f[4]) for f in base_faces])
            # Outer layer
            outer_faces = draw_box(p['outer_uvs'], p['pos'], p['size'], ox, oy, scale, angle_y, angle_x, dilation=p['dilation'])
            all_faces.extend([(f[0], True, f[1], f[2], f[3], f[4]) for f in outer_faces])

        # Sort all faces by depth (back to front: highest Z first)
        all_faces.sort(key=lambda item: item[0], reverse=True)

        # Texture mapping with bilinear texture quadrilateral sampling
        for avg_z, is_outer, name, pts, uv, shade in all_faces:
            u1, v1, u2, v2 = uv
            uw, vh = u2 - u1, v2 - v1
            
            # Sub-sample polygon pixels
            # Generate face texture
            patch = skin.crop((u1, v1, u2, v2))
            
            # Check if outer layer has any non-transparent pixels
            if is_outer:
                has_visible = any(p[3] > 10 for p in patch.getdata())
                if not has_visible:
                    continue

            # Transform patch onto quadrilateral pts
            # pts are 4 vertices: top-left, top-right, bottom-right, bottom-left
            p0, p1, p2, p3 = pts[0], pts[1], pts[2], pts[3]

            # Warp patch onto canvas
            # We can use Image.transform with QUAD
            # Bounding box of quad
            min_x = int(min(p[0] for p in pts) - 1)
            max_x = int(max(p[0] for p in pts) + 2)
            min_y = int(min(p[1] for p in pts) - 1)
            max_y = int(max(p[1] for p in pts) + 2)
            qw = max_x - min_x
            qh = max_y - min_y
            if qw <= 0 or qh <= 0:
                continue

            # Quad coordinates relative to crop bounding box
            local_pts = [(p[0] - min_x, p[1] - min_y) for p in [p0, p3, p2, p1]]
            flat_quad = [coord for pt in local_pts for coord in pt]

            # Upscale patch first for clean pixel art rendering
            upscaled_patch = patch.resize((uw * 16, vh * 16), Image.NEAREST)
            # Apply shading
            if shade != 1.0:
                r, g, b, a = upscaled_patch.split()
                # Multiply RGB by shade
                r = r.point(lambda x: min(255, int(x * shade)))
                g = g.point(lambda x: min(255, int(x * shade)))
                b = b.point(lambda x: min(255, int(x * shade)))
                upscaled_patch = Image.merge('RGBA', (r, g, b, a))

            warped = upscaled_patch.transform(
                (qw, qh),
                Image.QUAD,
                flat_quad,
                resample=Image.NEAREST
            )

            canvas.alpha_composite(warped, (min_x, min_y))

    # Render Front 3/4 View on Left side
    render_character_view(230, 210, angle_y=-0.55, angle_x=0.18, scale=10.0)
    # Render Back 3/4 View on Right side
    render_character_view(570, 210, angle_y=2.5, angle_x=0.18, scale=10.0)

    # Draw Title & Labels
    draw = ImageDraw.Draw(canvas)
    draw.text((30, 30), "Aeron, the Last Skywright", fill=(255, 255, 255, 255))
    draw.text((30, 52), "Engineer Villager Model & Skin (3D Preview)", fill=(120, 210, 240, 255))
    draw.text((180, 440), "Front View", fill=(180, 190, 205, 255))
    draw.text((520, 440), "Back View", fill=(180, 190, 205, 255))

    canvas.save(out_path)
    print("Saved 3D render preview to", out_path)

render_minecraft_model('chronicle_guide_engineer.png', 'aeron_3d_preview.png')
