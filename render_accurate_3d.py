import numpy as np
from PIL import Image, ImageDraw

def render_model_to_image(skin_path, out_path):
    skin = Image.open(skin_path).convert('RGBA')
    skin_np = np.array(skin)

    W, H = 840, 520
    # Depth buffer and color buffer
    img_buf = np.zeros((H, W, 4), dtype=np.uint8)
    # Background gradient
    for y in range(H):
        t = y / H
        img_buf[y, :, 0] = int(22 + 10 * t)
        img_buf[y, :, 1] = int(26 + 14 * t)
        img_buf[y, :, 2] = int(34 + 18 * t)
        img_buf[y, :, 3] = 255

    z_buf = np.full((H, W), -1e9, dtype=np.float32)

    def project(pt3d, ox, oy, scale, ay, ax):
        x, y, z = pt3d
        # Rotate around Y
        cy, sy = np.cos(ay), np.sin(ay)
        x1 = x * cy + z * sy
        z1 = -x * sy + z * cy

        # Rotate around X
        cx, sx = np.cos(ax), np.sin(ax)
        y1 = y * cx - z1 * sx
        z2 = y * sx + z1 * cx

        # Screen coords
        sx_out = ox + x1 * scale
        sy_out = oy + y1 * scale
        return sx_out, sy_out, z2

    def rasterize_triangle(p0, p1, p2, uv0, uv1, uv2, shade, is_outer):
        # Bounding box
        min_x = max(0, int(np.floor(min(p0[0], p1[0], p2[0]))))
        max_x = min(W - 1, int(np.ceil(max(p0[0], p1[0], p2[0]))))
        min_y = max(0, int(np.floor(min(p0[1], p1[1], p2[1]))))
        max_y = min(H - 1, int(np.ceil(max(p0[1], p1[1], p2[1]))))

        if min_x > max_x or min_y > max_y:
            return

        denom = ((p1[1] - p2[1]) * (p0[0] - p2[0]) + (p2[0] - p1[0]) * (p0[1] - p2[1]))
        if abs(denom) < 1e-6:
            return

        inv_denom = 1.0 / denom

        # Grid of sample points
        xs, ys = np.meshgrid(np.arange(min_x, max_x + 1), np.arange(min_y, max_y + 1))
        w0 = ((p1[1] - p2[1]) * (xs - p2[0]) + (p2[0] - p1[0]) * (ys - p2[1])) * inv_denom
        w1 = ((p2[1] - p0[1]) * (xs - p2[0]) + (p0[0] - p2[0]) * (ys - p2[1])) * inv_denom
        w2 = 1.0 - w0 - w1

        mask = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
        if not np.any(mask):
            return

        w0_m = w0[mask]
        w1_m = w1[mask]
        w2_m = w2[mask]

        z_interp = w0_m * p0[2] + w1_m * p1[2] + w2_m * p2[2]

        u_interp = w0_m * uv0[0] + w1_m * uv1[0] + w2_m * uv2[0]
        v_interp = w0_m * uv0[1] + w1_m * uv1[1] + w2_m * uv2[1]

        u_px = np.clip(np.floor(u_interp).astype(int), 0, 63)
        v_px = np.clip(np.floor(v_interp).astype(int), 0, 63)

        sampled = skin_np[v_px, u_px]
        alpha = sampled[:, 3]

        # Only draw if not transparent
        draw_mask = alpha > 15
        if not np.any(draw_mask):
            return

        valid_xs = xs[mask][draw_mask]
        valid_ys = ys[mask][draw_mask]
        valid_zs = z_interp[draw_mask]
        valid_c = sampled[draw_mask].astype(np.float32)

        # Depth test (closer object has larger/less negative Z depending on convention; here camera looks towards +Z, so smaller z2 is closer to camera)
        for i in range(len(valid_xs)):
            vx = valid_xs[i]
            vy = valid_ys[i]
            vz = valid_zs[i]
            # Since camera looks from front, smaller z is in front of larger z
            # We want vz < z_buf
            if z_buf[vy, vx] == -1e9 or vz < z_buf[vy, vx]:
                src_a = valid_c[i, 3] / 255.0
                if src_a >= 0.95:
                    z_buf[vy, vx] = vz
                    img_buf[vy, vx, 0] = int(np.clip(valid_c[i, 0] * shade, 0, 255))
                    img_buf[vy, vx, 1] = int(np.clip(valid_c[i, 1] * shade, 0, 255))
                    img_buf[vy, vx, 2] = int(np.clip(valid_c[i, 2] * shade, 0, 255))
                    img_buf[vy, vx, 3] = 255
                else:
                    # Alpha blend over existing
                    cur_r = img_buf[vy, vx, 0]
                    cur_g = img_buf[vy, vx, 1]
                    cur_b = img_buf[vy, vx, 2]
                    out_r = valid_c[i, 0] * shade * src_a + cur_r * (1.0 - src_a)
                    out_g = valid_c[i, 1] * shade * src_a + cur_g * (1.0 - src_a)
                    out_b = valid_c[i, 2] * shade * src_a + cur_b * (1.0 - src_a)
                    img_buf[vy, vx, 0] = int(np.clip(out_r, 0, 255))
                    img_buf[vy, vx, 1] = int(np.clip(out_g, 0, 255))
                    img_buf[vy, vx, 2] = int(np.clip(out_b, 0, 255))

    def render_cuboid(pos, size, uvs, ox, oy, scale, ay, ax, dilation=0.0, is_outer=False):
        x0, y0, z0 = pos
        dx, dy, dz = size
        d = dilation
        x0, y0, z0 = x0 - d, y0 - d, z0 - d
        x1, y1, z1 = x0 + dx + 2*d, y0 + dy + 2*d, z0 + dz + 2*d

        # 8 corners
        c = [
            (x0, y0, z0), # 0: top-left-front
            (x1, y0, z0), # 1: top-right-front
            (x1, y1, z0), # 2: bottom-right-front
            (x0, y1, z0), # 3: bottom-left-front
            (x0, y0, z1), # 4: top-left-back
            (x1, y0, z1), # 5: top-right-back
            (x1, y1, z1), # 6: bottom-right-back
            (x0, y1, z1), # 7: bottom-left-back
        ]

        proj = [project(pt, ox, oy, scale, ay, ax) for pt in c]

        # Normal directions & shading
        # Front (-Z): corners 0, 1, 2, 3
        # Back (+Z): corners 5, 4, 7, 6
        # Top (-Y): corners 4, 5, 1, 0
        # Bottom (+Y): corners 3, 2, 6, 7
        # Right (-X): corners 4, 0, 3, 7
        # Left (+X): corners 1, 5, 6, 2

        face_defs = [
            # ('front', (c0, c1, c2, c3), uv_rect, normal, shade)
            ('front', (0, 1, 2, 3), uvs['front'], (0, 0, -1), 1.0),
            ('back', (5, 4, 7, 6), uvs['back'], (0, 0, 1), 0.75),
            ('top', (4, 5, 1, 0), uvs['top'], (0, -1, 0), 1.12),
            ('bottom', (3, 2, 6, 7), uvs['bottom'], (0, 1, 0), 0.65),
            ('right', (4, 0, 3, 7), uvs['right'], (-1, 0, 0), 0.85),
            ('left', (1, 5, 6, 2), uvs['left'], (1, 0, 0), 0.90),
        ]

        for fname, (i0, i1, i2, i3), (u1, v1, u2, v2), normal, shade in face_defs:
            # Check backface culling
            cy, sy = np.cos(ay), np.sin(ay)
            nx1 = normal[0] * cy + normal[2] * sy
            nz1 = -normal[0] * sy + normal[2] * cy
            cx, sx = np.cos(ax), np.sin(ax)
            nz2 = normal[1] * sx + nz1 * cx

            if nz2 < 0.05:  # Facing camera
                p0, p1, p2, p3 = proj[i0], proj[i1], proj[i2], proj[i3]
                # UV corners:
                # Top-Left: (u1, v1)
                # Top-Right: (u2, v1)
                # Bottom-Right: (u2, v2)
                # Bottom-Left: (u1, v2)
                uv_tl = (u1, v1)
                uv_tr = (u2, v1)
                uv_br = (u2, v2)
                uv_bl = (u1, v2)

                # Triangle 1: p0, p1, p2
                rasterize_triangle(p0, p1, p2, uv_tl, uv_tr, uv_br, shade, is_outer)
                # Triangle 2: p0, p2, p3
                rasterize_triangle(p0, p2, p3, uv_tl, uv_br, uv_bl, shade, is_outer)

    # Define model parts
    parts = [
        # Head (8x8x8): X in -4..4, Y in -8..0, Z in -4..4
        {
            'pos': (-4, -8, -4), 'size': (8, 8, 8),
            'base': {'top': (8, 0, 16, 8), 'bottom': (16, 0, 24, 8), 'right': (0, 8, 8, 16), 'front': (8, 8, 16, 16), 'left': (16, 8, 24, 16), 'back': (24, 8, 32, 16)},
            'outer': {'top': (40, 0, 48, 8), 'bottom': (48, 0, 56, 8), 'right': (32, 8, 40, 16), 'front': (40, 8, 48, 16), 'left': (48, 8, 56, 16), 'back': (56, 8, 64, 16)},
            'd': 0.5
        },
        # Body (8x12x4): X in -4..4, Y in 0..12, Z in -2..2
        {
            'pos': (-4, 0, -2), 'size': (8, 12, 4),
            'base': {'top': (20, 16, 28, 20), 'bottom': (28, 16, 36, 20), 'right': (16, 20, 20, 32), 'front': (20, 20, 28, 32), 'left': (28, 20, 32, 32), 'back': (32, 20, 40, 32)},
            'outer': {'top': (20, 32, 28, 36), 'bottom': (28, 32, 36, 36), 'right': (16, 36, 20, 48), 'front': (20, 36, 28, 48), 'left': (28, 36, 32, 48), 'back': (32, 36, 40, 48)},
            'd': 0.35
        },
        # Right Arm (4x12x4): X in -8..-4, Y in 0..12, Z in -2..2
        {
            'pos': (-8, 0, -2), 'size': (4, 12, 4),
            'base': {'top': (44, 16, 48, 20), 'bottom': (48, 16, 52, 20), 'right': (40, 20, 44, 32), 'front': (44, 20, 48, 32), 'left': (48, 20, 52, 32), 'back': (52, 20, 56, 32)},
            'outer': {'top': (44, 32, 48, 36), 'bottom': (48, 32, 52, 36), 'right': (40, 36, 44, 48), 'front': (44, 36, 48, 48), 'left': (48, 36, 52, 48), 'back': (52, 36, 56, 48)},
            'd': 0.3
        },
        # Left Arm (4x12x4): X in 4..8, Y in 0..12, Z in -2..2
        {
            'pos': (4, 0, -2), 'size': (4, 12, 4),
            'base': {'top': (36, 48, 40, 52), 'bottom': (40, 48, 44, 52), 'right': (32, 52, 36, 64), 'front': (36, 52, 40, 64), 'left': (40, 52, 44, 64), 'back': (44, 52, 48, 64)},
            'outer': {'top': (52, 48, 56, 52), 'bottom': (56, 48, 60, 52), 'right': (48, 52, 52, 64), 'front': (52, 52, 56, 64), 'left': (56, 52, 60, 64), 'back': (60, 52, 64, 64)},
            'd': 0.3
        },
        # Right Leg (4x12x4): X in -4..0, Y in 12..24, Z in -2..2
        {
            'pos': (-4, 12, -2), 'size': (4, 12, 4),
            'base': {'top': (4, 16, 8, 20), 'bottom': (8, 16, 12, 20), 'right': (0, 20, 4, 32), 'front': (4, 20, 8, 32), 'left': (8, 20, 12, 32), 'back': (12, 20, 16, 32)},
            'outer': {'top': (4, 32, 8, 36), 'bottom': (8, 32, 12, 36), 'right': (0, 36, 4, 48), 'front': (4, 36, 8, 48), 'left': (8, 36, 12, 48), 'back': (12, 36, 16, 48)},
            'd': 0.3
        },
        # Left Leg (4x12x4): X in 0..4, Y in 12..24, Z in -2..2
        {
            'pos': (0, 12, -2), 'size': (4, 12, 4),
            'base': {'top': (20, 48, 24, 52), 'bottom': (24, 48, 28, 52), 'right': (16, 52, 20, 64), 'front': (20, 52, 24, 64), 'left': (24, 52, 28, 64), 'back': (28, 52, 32, 64)},
            'outer': {'top': (4, 48, 8, 52), 'bottom': (8, 48, 12, 52), 'right': (0, 52, 4, 64), 'front': (4, 52, 8, 64), 'left': (8, 52, 12, 64), 'back': (12, 52, 16, 64)},
            'd': 0.3
        },
    ]

    def render_view(ox, oy, ay, ax, scale=11.0):
        # 1. Base cuboids
        for p in parts:
            render_cuboid(p['pos'], p['size'], p['base'], ox, oy, scale, ay, ax, dilation=0.0, is_outer=False)
        # 2. Outer cuboids
        for p in parts:
            render_cuboid(p['pos'], p['size'], p['outer'], ox, oy, scale, ay, ax, dilation=p['d'], is_outer=True)

    # Render Front-Left 3/4 View
    render_view(230, 220, ay=-0.45, ax=0.15, scale=11.2)
    # Render Back-Right 3/4 View
    render_view(610, 220, ay=2.65, ax=0.15, scale=11.2)

    result_img = Image.fromarray(img_buf, 'RGBA')
    draw = ImageDraw.Draw(result_img)
    draw.text((32, 28), "Aeron, the Last Skywright", fill=(255, 255, 255, 255))
    draw.text((32, 50), "Artificer Engineer Villager Model (Minecraft 3D Render)", fill=(80, 215, 245, 255))
    draw.text((180, 455), "Front View", fill=(175, 185, 200, 255))
    draw.text((560, 455), "Back View", fill=(175, 185, 200, 255))

    result_img.save(out_path)
    print("Successfully rendered accurate 3D preview to", out_path)

render_model_to_image('src/main/resources/assets/elemental/textures/entity/chronicle_guide.png', 'aeron_3d_accurate.png')
