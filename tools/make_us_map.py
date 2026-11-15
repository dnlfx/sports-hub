#!/usr/bin/env python3
"""
Builds app/src/main/assets/us_map.txt, the outline and state lines drawn under the NFL
coverage map, from the U.S. Census Bureau's state boundaries as packaged by the us-atlas
npm package (ISC license):

    npm pack us-atlas@3.0.1 && tar xzf us-atlas-3.0.1.tgz
    python3 tools/make_us_map.py package/states-10m.json app/src/main/assets/us_map.txt

Only the lower 48 states and D.C. Shared borders are simplified once, so neighbouring
states still meet exactly. Output lines, all in longitude,latitude pairs:
    P ...  one ring of a state's outline (filled, and used to clip the coverage colors)
    B ...  a border between two states
    C ...  coastline or international border
"""
import json
import sys

NOT_LOWER_48 = {"02", "15", "60", "66", "69", "72", "78"}
TOLERANCE = 0.045  # degrees


def decode_arcs(topo):
    sx, sy = topo["transform"]["scale"]
    tx, ty = topo["transform"]["translate"]
    arcs = []
    for arc in topo["arcs"]:
        x = y = 0
        pts = []
        for dx, dy in arc:
            x += dx
            y += dy
            pts.append((x * sx + tx, y * sy + ty))
        arcs.append(pts)
    return arcs


def simplify(points, tol):
    """Douglas-Peucker, keeping both ends."""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        (ax, ay), (bx, by) = points[a], points[b]
        dx, dy = bx - ax, by - ay
        norm = (dx * dx + dy * dy) ** 0.5
        best, index = 0.0, -1
        for i in range(a + 1, b):
            px, py = points[i]
            if norm == 0:
                d = ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
            else:
                d = abs(dy * px - dx * py + bx * ay - by * ax) / norm
            if d > best:
                best, index = d, i
        if best > tol and index > 0:
            keep[index] = True
            stack += [(a, index), (index, b)]
    return [p for p, k in zip(points, keep) if k]


def main(src, dst):
    topo = json.load(open(src))
    arcs = decode_arcs(topo)
    states = [g for g in topo["objects"]["states"]["geometries"] if g.get("id") not in NOT_LOWER_48]

    uses = [0] * len(arcs)
    polygons = []
    for g in states:
        polys = g["arcs"] if g["type"] == "MultiPolygon" else [g["arcs"]]
        for poly in polys:
            polygons.append(poly)
            for ring in poly:
                for i in ring:
                    uses[i if i >= 0 else ~i] += 1

    simple = [simplify(a, TOLERANCE) for a in arcs]

    def ring_points(ring):
        pts = []
        for i in ring:
            seg = simple[i] if i >= 0 else list(reversed(simple[~i]))
            pts.extend(seg if not pts else seg[1:])
        return pts

    def area(pts):
        return abs(sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1]))) / 2

    fmt = lambda pts: " ".join(f"{x:.2f},{y:.2f}" for x, y in pts)
    lines = []
    for poly in polygons:
        for ring in poly:
            pts = ring_points(ring)
            if len(pts) >= 4 and area(pts) > 0.02:
                lines.append("P " + fmt(pts))
    for i, n in enumerate(uses):
        if n == 0 or len(simple[i]) < 2:
            continue
        # Drop the outlines of tiny islands that didn't make it as a ring.
        if n == 1 and len(simple[i]) <= 3 and simple[i][0] == simple[i][-1]:
            continue
        lines.append(("B " if n > 1 else "C ") + fmt(simple[i]))
    with open(dst, "w") as out:
        out.write("# Lower 48 states. Source: U.S. Census Bureau via us-atlas 3.0.1. See tools/make_us_map.py.\n")
        out.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
