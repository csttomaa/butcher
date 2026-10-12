package com.butcher.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.dragon.EnderDragonModel;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The real vanilla Ender Dragon, posed by the game's own code, as polygons for the Seraph's scene.
 *
 * Nothing here is copied: it builds {@link EnderDragonModel} from its own layer, fills an
 * {@link EnderDragonRenderState} (the flap clock, the flight history its neck and tail curve from,
 * whether it sits), runs the model's own {@code setupAnim}, applies EnderDragonRenderer's root
 * transform (turn by the yaw 7 ticks ago, pitch by the height change from 10 to 5 ticks ago, the flip),
 * and walks every cube with {@link ModelPart#visit} - each polygon comes out with its four posed corners
 * and its own UVs on the 256 x 256 dragon texture. The same class serves the game and the offline
 * preview (which runs the client jar), so they cannot differ.
 */
public final class DragonRig implements SeraphScene.DragonPoser {

    private final EnderDragonModel model;
    private final ModelPart root;
    private final EnderDragonRenderState state = new EnderDragonRenderState();

    public DragonRig() {
        this.root = EnderDragonModel.createBodyLayer().bakeRoot();
        this.model = new EnderDragonModel(root);
    }

    @Override
    public List<float[]> pose(SeraphScene.DragonPose p) {
        // The flight history: oldest first, a sample a tick (y in blocks, yaw in degrees).
        for (int i = p.histY.length - 1; i >= 0; i--) state.flightHistory.record(p.histY[i], p.histYaw[i]);
        state.flapTime = p.flapTime;
        state.partialTicks = 0f;
        state.isSitting = p.sitting;
        state.isLandingOrTakingOff = p.landing;
        state.distanceToEgg = p.distanceToEgg;
        state.deathTime = 0f;
        model.setupAnim(state);
        // The scene's own touches on top of vanilla's pose: the jaw opened wide for the breath and the roar.
        if (p.jawOpen > 0f) {
            ModelPart jaw = root.getChild("head").getChild("jaw");
            jaw.xRot = Math.max(jaw.xRot, p.jawOpen);
        }
        if (p.headPitch != 0f) root.getChild("head").xRot += p.headPitch;

        PoseStack ps = new PoseStack();
        // EnderDragonRenderer.submit, in order.
        ps.mulPose(Axis.YP.rotationDegrees(-state.getHistoricalPos(7).yRot()));
        float dy = (float) (state.getHistoricalPos(5).y() - state.getHistoricalPos(10).y());
        ps.mulPose(Axis.XP.rotationDegrees(dy * 10f + p.extraPitch));
        ps.translate(0f, 0f, 1f);
        ps.scale(-1f, -1f, 1f);
        ps.translate(0f, -1.501f, 0f);

        List<float[]> out = new ArrayList<>();
        Vector3f v = new Vector3f();
        root.visit(ps, (pose, path, index, cube) -> {
            Matrix4f m = pose.pose();
            for (ModelPart.Polygon poly : cube.polygons) {
                ModelPart.Vertex[] vs = poly.vertices();
                float[] q = new float[4 * 5 + 2];
                for (int k = 0; k < 4; k++) {
                    m.transformPosition(vs[k].worldX(), vs[k].worldY(), vs[k].worldZ(), v);
                    q[k * 5] = v.x;
                    q[k * 5 + 1] = v.y;
                    q[k * 5 + 2] = v.z;
                    q[k * 5 + 3] = vs[k].u();
                    q[k * 5 + 4] = vs[k].v();
                }
                // The parts' paths run from the root: "/head", "/head/jaw".
                q[20] = path.contains("jaw") ? 2f : (path.contains("head") ? 1f : 0f);
                // The wing membranes are single sheets, seen from either side: never culled.
                q[21] = path.contains("wing") ? 1f : 0f;
                out.add(q);
            }
        });
        return out;
    }
}
