package com.scathabutcher.render.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Builds a {@link Mesh} from a Blockbench glTF export.
 *
 * <h2>Why bother</h2>
 *
 * Every mesh in this mod is hand-coded boxes - {@link BoxMesh} calls with numbers worked
 * out by eye. That is why three separate attempts at the Scatha all missed: getting a
 * shape right by typing coordinates and relaunching the game is not a workflow, it is a
 * guessing game. Loading a real exported model takes the guessing out: shape the thing in
 * Blockbench, look at it there, export, done.
 *
 * SkyMaxx does exactly this for its tiki totem and Titanoboa, which is where the approach
 * comes from. This is a narrower version - it produces our {@link Mesh} and skips the
 * framing metadata theirs carries, because {@link Mesh} works its own bounds out.
 *
 * <h2>What a model has to look like</h2>
 *
 * This is deliberately <em>not</em> a general glTF importer, and it will not become one.
 * It reads the one shape Blockbench emits for a box model, and rejects anything else
 * rather than half-loading it:
 *
 * <ul>
 *   <li><b>Quads, four vertices each, in face order.</b> glTF only stores triangles, but a
 *       box exporter writes the four corners of each face once and pairs them up in the
 *       index buffer. So POSITION comes in groups of four and the indices can be ignored.
 *       A model triangulated some other way - anything sculpted, or run through a decimator
 *       - will not have that layout and is refused.
 *   <li><b>Axis-aligned rectangular UVs.</b> Each face's four UVs must span a rectangle,
 *       because a {@link Mesh.Quad} is drawn as a texture <em>rect</em> through an affine
 *       blit. There is no way to draw a rotated or trapezoidal UV island this way. Keep
 *       "Box UV" on in Blockbench and this holds automatically.
 *   <li><b>Embedded buffers.</b> {@code .gltf} with base64 data URIs, or {@code .glb}.
 *       A {@code .gltf} pointing at a separate {@code .bin} is refused - it is one file
 *       going missing away from a crash, and Blockbench offers the embedded form anyway.
 * </ul>
 *
 * <h2>Two conventions that silently ruin a model</h2>
 *
 * <ul>
 *   <li><b>glTF faces -Z; our camera sits at +Z.</b> So X and Z are negated on the way in
 *       ({@link #towardsCamera}). Skip this and the model loads perfectly and is mirrored,
 *       which is the kind of wrong that survives review.
 *   <li><b>V is measured downwards.</b> glTF's V origin is the top-left of the image, the
 *       same as a blit's, so no flip is wanted here. If a model ever comes out with its
 *       texture upside down, that is the thing to check first.
 * </ul>
 */
public final class GltfLoader {

    /** A pixel this transparent does not count as drawn. */
    private static final int ALPHA_FLOOR = 12;
    /** glTF's code for 32-bit float; the only component type worth supporting. */
    private static final int COMPONENT_FLOAT = 5126;
    /** First four bytes of a binary glTF container. */
    private static final int GLB_MAGIC = 0x46546C67;   // "glTF", little-endian
    private static final int GLB_CHUNK_JSON = 0x4E4F534A;
    private static final int GLB_CHUNK_BIN = 0x004E4942;

    /**
     * Why the last load found nothing usable, for the error message.
     *
     * A field rather than a return value because the walk that discovers it is several
     * frames down and returns nothing. Single-threaded: models load on the render thread
     * when a cutscene first asks for one.
     */
    private static String rejected;

    private GltfLoader() {}

    /**
     * A loaded model and the texture it came with.
     *
     * They travel together because a {@link Mesh}'s UV rects are pixel coordinates into
     * one specific image - hand it a different skin and every face samples the wrong
     * place. The caller registers {@code skin} with the texture bank under whatever name
     * it then passes to the renderer.
     */
    public record Loaded(Mesh mesh, BufferedImage skin) {}

    /**
     * Reads a model.
     *
     * @param in      the {@code .gltf} or {@code .glb}. Closed by the caller, not here.
     * @param skin    the texture to use, or null to take the one embedded in the model
     * @param posed   apply each node's own rotation. Off gives the model in its rest pose,
     *                which is what you want when the animation is going to place the parts
     *                itself - as {@link ScathaBody} does for the worm.
     * @throws IOException if the file is not readable, or is not the narrow shape above.
     *     The message says which, because "the model did not load" with no reason is the
     *     single most annoying way for this to fail.
     */

    /**
     * Reads a model from a file, resolving any sibling {@code .bin} and texture.
     *
     * The stream form below cannot do that - it has no idea where the file came from -
     * so a downloaded folder of {@code scene.gltf} + {@code scene.bin} + {@code textures/}
     * was refused outright and had to be re-exported by hand. Given the whole point of
     * the models folder is dropping downloads into it, that was the wrong default.
     */
    public static Loaded load(java.nio.file.Path file, BufferedImage skin, boolean posed)
            throws IOException {
        try (InputStream in = java.nio.file.Files.newInputStream(file)) {
            return load(in, skin, posed, file.toAbsolutePath().getParent());
        }
    }

    public static Loaded load(InputStream in, BufferedImage skin, boolean posed) throws IOException {
        return load(in, skin, posed, null);
    }

    /** @param beside directory to resolve relative uris against, or null for none */
    public static Loaded load(InputStream in, BufferedImage skin, boolean posed,
                              java.nio.file.Path beside) throws IOException {
        byte[] bytes = in.readAllBytes();
        JsonObject root;
        byte[] glbBin = null;

        if (bytes.length >= 12 && littleInt(bytes, 0) == GLB_MAGIC) {
            Glb glb = readGlb(bytes);
            root = glb.json();
            glbBin = glb.bin();
        } else {
            root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        byte[][] buffers = readBuffers(root, glbBin, beside);
        BufferedImage texture = skin != null ? skin : readImage(root, buffers, beside);
        if (texture == null) {
            throw new IOException("the model carries no texture and none was supplied");
        }

        rejected = null;
        List<Mesh.Quad> quads = new ArrayList<>();
        JsonArray nodes = array(root, "nodes");
        for (JsonElement rootNode : sceneRoots(root)) {
            walk(root, buffers, texture, nodes, rootNode.getAsInt(),
                    0f, 0f, 0f, identity(), posed, quads);
        }
        if (quads.isEmpty()) {
            throw new IOException(rejected != null
                    ? "not a box model: " + rejected
                    : "the model has no faces with anything drawn on them");
        }

        BoxMesh out = new BoxMesh(texture.getWidth(), texture.getHeight());
        for (Mesh.Quad q : quads) out.quad(q);
        return new Loaded(out.build(), texture);
    }

    // ------------------------------------------------------------------ the glb container

    private record Glb(JsonObject json, byte[] bin) {}

    /**
     * Unpacks a binary glTF: a 12-byte header then a run of length-prefixed chunks, the
     * first of which is the JSON and the second (if present) the buffer data.
     *
     * Supported because it is one file rather than two, and a single file is much harder
     * for a user to half-copy into the config folder.
     */
    private static Glb readGlb(byte[] bytes) throws IOException {
        JsonObject json = null;
        byte[] bin = null;
        int at = 12;                                   // magic, version, total length
        while (at + 8 <= bytes.length) {
            int length = littleInt(bytes, at);
            int type = littleInt(bytes, at + 4);
            int from = at + 8;
            if (length < 0 || from + length > bytes.length) {
                throw new IOException("the .glb is truncated");
            }
            if (type == GLB_CHUNK_JSON && json == null) {
                json = JsonParser.parseString(new String(bytes, from, length, StandardCharsets.UTF_8))
                        .getAsJsonObject();
            } else if (type == GLB_CHUNK_BIN && bin == null) {
                bin = java.util.Arrays.copyOfRange(bytes, from, from + length);
            }
            at = from + length + (3 - (length + 3) % 4) % 4;   // chunks are 4-byte aligned
        }
        if (json == null) throw new IOException("the .glb has no JSON chunk");
        return new Glb(json, bin);
    }

    private static int littleInt(byte[] b, int at) {
        return (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8 | (b[at + 2] & 0xFF) << 16 | (b[at + 3] & 0xFF) << 24;
    }

    // ------------------------------------------------------------------ the node tree

    /**
     * Walks the scene, accumulating each node's transform, and emits the meshes it finds.
     *
     * Only translation and rotation are carried. Blockbench does not write scale on a box
     * model, and honouring a scale would mean the UV rect no longer matches the face's
     * size in the way {@link Mesh.Quad} assumes.
     */
    private static void walk(JsonObject root, byte[][] buffers, BufferedImage skin,
                             JsonArray nodes, int index,
                             float tx, float ty, float tz, float[] rot, boolean posed,
                             List<Mesh.Quad> out) {
        if (index < 0 || index >= nodes.size()) return;
        JsonObject node = nodes.get(index).getAsJsonObject();

        float[] local = posed && node.has("rotation")
                ? quaternionToMatrix(floats(node.getAsJsonArray("rotation")))
                : identity();
        float[] combined = multiply(rot, local);

        float lx = 0f, ly = 0f, lz = 0f;
        if (node.has("translation")) {
            float[] t = floats(node.getAsJsonArray("translation"));
            lx = t[0]; ly = t[1]; lz = t[2];
        }
        // The child's offset is expressed in the PARENT's frame, so it rotates by the
        // parent's matrix, not by the combined one.
        float wx = tx + rot[0] * lx + rot[1] * ly + rot[2] * lz;
        float wy = ty + rot[3] * lx + rot[4] * ly + rot[5] * lz;
        float wz = tz + rot[6] * lx + rot[7] * ly + rot[8] * lz;

        if (node.has("mesh")) {
            emit(root, buffers, skin, node.get("mesh").getAsInt(), wx, wy, wz, combined, out);
        }
        if (node.has("children")) {
            for (JsonElement child : node.getAsJsonArray("children")) {
                walk(root, buffers, skin, nodes, child.getAsInt(), wx, wy, wz, combined, posed, out);
            }
        }
    }

    private static void emit(JsonObject root, byte[][] buffers, BufferedImage skin, int meshIndex,
                             float tx, float ty, float tz, float[] rot, List<Mesh.Quad> out)
            throws IllegalStateException {
        JsonArray meshes = array(root, "meshes");
        if (meshIndex < 0 || meshIndex >= meshes.size()) return;

        for (JsonElement element : meshes.get(meshIndex).getAsJsonObject().getAsJsonArray("primitives")) {
            JsonObject prim = element.getAsJsonObject();
            JsonObject attributes = prim.getAsJsonObject("attributes");
            if (!attributes.has("POSITION") || !attributes.has("NORMAL") || !attributes.has("TEXCOORD_0")) {
                continue;
            }
            float[][] pos = readAccessor(root, buffers, attributes.get("POSITION").getAsInt(), 3);
            float[][] nrm = readAccessor(root, buffers, attributes.get("NORMAL").getAsInt(), 3);
            float[][] uv = readAccessor(root, buffers, attributes.get("TEXCOORD_0").getAsInt(), 2);
            if (pos == null || nrm == null || uv == null) continue;
            if (pos.length % 4 != 0 || nrm.length < pos.length || uv.length < pos.length) {
                // Worth naming rather than silently skipping: this is the check almost
                // every downloaded model fails, and "no faces" on its own tells the
                // player nothing about what is wrong with their file.
                rejected = pos.length + " vertices, which is not four per face - this is a"
                        + " triangulated or sculpted mesh, not a box model";
                continue;
            }

            int faces = pos.length / 4;
            int[][] rects = new int[faces][];
            float[][] normals = new float[faces][];
            for (int f = 0; f < faces; f++) {
                rects[f] = rectFor(uv, f * 4, skin.getWidth(), skin.getHeight());
                normals[f] = nrm[f * 4];
            }

            for (int f = 0; f < faces; f++) {
                if (rects[f] == null) continue;
                // A face whose UV rect is entirely transparent draws nothing, so it is
                // dropped here rather than costing a slot in every frame's depth sort.
                if (ink(skin, rects[f]) == 0) continue;
                Mesh.Quad quad = buildQuad(pos, uv, normals[f], f * 4, rects[f],
                        blankFor(rects, normals, f, skin), tx, ty, tz, rot, skin);
                if (quad != null) out.add(quad);
            }
        }
    }

    /**
     * The rect to show when this face is {@linkplain Mesh.Quad#blank blanked}: the one
     * belonging to the face pointing the opposite way.
     *
     * On a box model that is the back of the same box - plain skin, by construction, since
     * whatever detail makes a face the "front" is on the front. So a segment can be shown
     * with its eye replaced by bare hide without a second model, which is what
     * {@code r-cull}'s blanked variant is for.
     */
    private static int[] blankFor(int[][] rects, float[][] normals, int face, BufferedImage skin) {
        float[] n = normals[face];
        if (n[0] == 0f && n[1] == 0f && n[2] == 0f) return null;
        for (int other = 0; other < rects.length; other++) {
            if (other == face || rects[other] == null) continue;
            float[] m = normals[other];
            if (n[0] + m[0] == 0f && n[1] + m[1] == 0f && n[2] + m[2] == 0f) {
                return ink(skin, rects[other]) > 0 ? rects[other] : null;
            }
        }
        return null;
    }

    /**
     * Turns one face's four vertices into a quad.
     *
     * A {@link Mesh.Quad} is a corner plus the two edges leaving it, and which corner is
     * which is decided by the UVs, not by winding: the vertex at the rect's top-left is the
     * origin, the one sharing its V is the end of the U edge, the one sharing its U is the
     * end of the V edge. Reading it off the geometry instead would put the texture on
     * rotated or mirrored, and it would still render.
     */
    private static Mesh.Quad buildQuad(float[][] pos, float[][] uv, float[] normal, int base,
                                       int[] rect, int[] blank,
                                       float tx, float ty, float tz, float[] rot,
                                       BufferedImage skin) {
        int origin = -1, alongU = -1, alongV = -1;
        for (int i = 0; i < 4; i++) {
            int u = Math.round(uv[base + i][0] * skin.getWidth());
            int v = Math.round(uv[base + i][1] * skin.getHeight());
            boolean left = u == rect[0];
            boolean top = v == rect[1];
            if (left && top) origin = base + i;
            else if (top) alongU = base + i;
            else if (left) alongV = base + i;
        }
        if (origin < 0 || alongU < 0 || alongV < 0) return null;   // not a rectangular island

        float[] o = towardsCamera(transform(pos[origin], rot, tx, ty, tz));
        float[] a = towardsCamera(transform(pos[alongU], rot, tx, ty, tz));
        float[] b = towardsCamera(transform(pos[alongV], rot, tx, ty, tz));
        float[] n = towardsCamera(rotate(normal, rot));

        int[] blankRect = blank == null
                ? null : new int[]{blank[0], blank[1], blank[2] - blank[0], blank[3] - blank[1]};
        return new Mesh.Quad(
                o[0], o[1], o[2],
                a[0] - o[0], a[1] - o[1], a[2] - o[2],
                b[0] - o[0], b[1] - o[1], b[2] - o[2],
                n[0], n[1], n[2],
                rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1],
                n[2] > 0.5f, blankRect);
    }

    /**
     * glTF models face -Z. Our camera sits at +Z, so the model is turned to face it.
     *
     * Negating X as well as Z is a 180 degree spin about Y rather than a mirror - do only
     * Z and the model comes out inside out, with its texture reversed on every face.
     */
    private static float[] towardsCamera(float[] p) {
        return new float[]{-p[0], p[1], -p[2]};
    }

    // ------------------------------------------------------------------ buffers

    private static byte[][] readBuffers(JsonObject root, byte[] glbBin,
                                        java.nio.file.Path beside) throws IOException {
        JsonArray buffers = array(root, "buffers");
        byte[][] out = new byte[buffers.size()][];
        for (int i = 0; i < buffers.size(); i++) {
            JsonObject buffer = buffers.get(i).getAsJsonObject();
            if (!buffer.has("uri")) {
                // No uri means "the .glb's own BIN chunk", which is only ever buffer 0.
                if (i == 0 && glbBin != null) { out[i] = glbBin; continue; }
                throw new IOException("buffer " + i + " has no data attached to it");
            }
            String uri = buffer.get("uri").getAsString();
            out[i] = decodeDataUri(uri);
            if (out[i] == null) out[i] = readBeside(beside, uri);
            if (out[i] == null) {
                throw new IOException("buffer " + i + " points at \"" + uri
                        + "\", which is not next to the model; keep the files together,"
                        + " or export with buffers embedded or as .glb");
            }
        }
        return out;
    }

    private static BufferedImage readImage(JsonObject root, byte[][] buffers,
                                           java.nio.file.Path beside) throws IOException {
        if (!root.has("images")) return null;
        JsonArray images = root.getAsJsonArray("images");
        if (images.isEmpty()) return null;
        JsonObject image = images.get(0).getAsJsonObject();

        if (image.has("uri")) {
            String uri = image.get("uri").getAsString();
            byte[] png = decodeDataUri(uri);
            if (png == null) png = readBeside(beside, uri);
            return png == null ? null : ImageIO.read(new ByteArrayInputStream(png));
        }
        // A .glb keeps its texture in the BIN chunk, addressed by a buffer view.
        if (image.has("bufferView")) {
            JsonArray views = array(root, "bufferViews");
            int index = image.get("bufferView").getAsInt();
            if (index < 0 || index >= views.size()) return null;
            JsonObject view = views.get(index).getAsJsonObject();
            byte[] data = buffers[view.get("buffer").getAsInt()];
            int offset = optInt(view, "byteOffset", 0);
            int length = view.get("byteLength").getAsInt();
            return ImageIO.read(new ByteArrayInputStream(data, offset, length));
        }
        return null;
    }

    /**
     * Reads a file the model refers to by relative path.
     *
     * Refuses anything that climbs out of the model's own directory. A model file is
     * data from wherever the player downloaded it, and its uris are strings in that
     * data - they do not get to name a path anywhere on the disk.
     */
    private static byte[] readBeside(java.nio.file.Path beside, String uri) {
        if (beside == null || uri == null || uri.isBlank()) return null;
        if (uri.startsWith("data:")) return null;
        try {
            String cleaned = java.net.URLDecoder.decode(uri, StandardCharsets.UTF_8);
            java.nio.file.Path target = beside.resolve(cleaned).normalize();
            if (!target.startsWith(beside)) return null;
            return java.nio.file.Files.isRegularFile(target)
                    ? java.nio.file.Files.readAllBytes(target) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] decodeDataUri(String uri) {
        int comma = uri.indexOf(',');
        if (!uri.startsWith("data:") || comma < 0) return null;
        return Base64.getDecoder().decode(uri.substring(comma + 1));
    }

    private static float[][] readAccessor(JsonObject root, byte[][] buffers, int index, int components) {
        JsonArray accessors = array(root, "accessors");
        if (index < 0 || index >= accessors.size()) return null;
        JsonObject accessor = accessors.get(index).getAsJsonObject();
        if (!accessor.has("bufferView")) return null;
        if (accessor.get("componentType").getAsInt() != COMPONENT_FLOAT) return null;

        JsonArray views = array(root, "bufferViews");
        int viewIndex = accessor.get("bufferView").getAsInt();
        if (viewIndex < 0 || viewIndex >= views.size()) return null;
        JsonObject view = views.get(viewIndex).getAsJsonObject();
        int bufferIndex = view.get("buffer").getAsInt();
        if (bufferIndex < 0 || bufferIndex >= buffers.length || buffers[bufferIndex] == null) return null;

        byte[] data = buffers[bufferIndex];
        int count = accessor.get("count").getAsInt();
        int offset = optInt(view, "byteOffset", 0) + optInt(accessor, "byteOffset", 0);
        int stride = optInt(view, "byteStride", components * 4);
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        float[][] out = new float[count][components];
        for (int i = 0; i < count; i++) {
            int at = offset + i * stride;
            if (at < 0 || at + components * 4 > data.length) return null;
            for (int c = 0; c < components; c++) out[i][c] = bb.getFloat(at + c * 4);
        }
        return out;
    }

    // ------------------------------------------------------------------ small helpers

    /** The pixel bounds of one face's UV island, as {@code {u0, v0, u1, v1}}, or null. */
    private static int[] rectFor(float[][] uv, int base, int width, int height) {
        int u0 = Integer.MAX_VALUE, v0 = Integer.MAX_VALUE;
        int u1 = Integer.MIN_VALUE, v1 = Integer.MIN_VALUE;
        for (int i = 0; i < 4; i++) {
            int u = Math.round(uv[base + i][0] * width);
            int v = Math.round(uv[base + i][1] * height);
            u0 = Math.min(u0, u); v0 = Math.min(v0, v);
            u1 = Math.max(u1, u); v1 = Math.max(v1, v);
        }
        u0 = Math.max(0, u0); v0 = Math.max(0, v0);
        u1 = Math.min(width, u1); v1 = Math.min(height, v1);
        return u1 > u0 && v1 > v0 ? new int[]{u0, v0, u1, v1} : null;
    }

    /** How many pixels in this rect are actually drawn. */
    private static int ink(BufferedImage skin, int[] rect) {
        int count = 0;
        for (int y = rect[1]; y < rect[3]; y++) {
            for (int x = rect[0]; x < rect[2]; x++) {
                if ((skin.getRGB(x, y) >>> 24) > ALPHA_FLOOR) count++;
            }
        }
        return count;
    }

    private static JsonArray sceneRoots(JsonObject root) {
        if (root.has("scenes")) {
            int index = optInt(root, "scene", 0);
            JsonArray scenes = root.getAsJsonArray("scenes");
            if (index >= 0 && index < scenes.size()) {
                JsonObject scene = scenes.get(index).getAsJsonObject();
                if (scene.has("nodes")) return scene.getAsJsonArray("nodes");
            }
        }
        // No scene list: walk every node and let the transform accumulation sort it out.
        JsonArray all = new JsonArray();
        for (int i = 0; i < array(root, "nodes").size(); i++) all.add(i);
        return all;
    }

    private static JsonArray array(JsonObject root, String name) {
        return root.has(name) ? root.getAsJsonArray(name) : new JsonArray();
    }

    private static int optInt(JsonObject o, String name, int fallback) {
        return o.has(name) ? o.get(name).getAsInt() : fallback;
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
        return out;
    }

    private static float[] identity() {
        return new float[]{1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};
    }

    private static float[] multiply(float[] a, float[] b) {
        float[] out = new float[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
            }
        }
        return out;
    }

    private static float[] rotate(float[] p, float[] m) {
        return new float[]{
                m[0] * p[0] + m[1] * p[1] + m[2] * p[2],
                m[3] * p[0] + m[4] * p[1] + m[5] * p[2],
                m[6] * p[0] + m[7] * p[1] + m[8] * p[2],
        };
    }

    private static float[] transform(float[] p, float[] m, float tx, float ty, float tz) {
        float[] r = rotate(p, m);
        return new float[]{r[0] + tx, r[1] + ty, r[2] + tz};
    }

    private static float[] quaternionToMatrix(float[] q) {
        float x = q[0], y = q[1], z = q[2], w = q[3];
        return new float[]{
                1f - 2f * (y * y + z * z), 2f * (x * y - z * w),      2f * (x * z + y * w),
                2f * (x * y + z * w),      1f - 2f * (x * x + z * z), 2f * (y * z - x * w),
                2f * (x * z - y * w),      2f * (y * z + x * w),      1f - 2f * (x * x + y * y),
        };
    }
}
