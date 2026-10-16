package dr4kun3.cosmiccore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * Seit Minecraft 1.21.9 wird nicht mehr direkt gezeichnet, sondern Geometrie beim {@link SubmitNodeCollector} eingereicht.
 * Diese Puffer nehmen die Vertices der bestehenden Zeichenroutinen auf (bereits fertig transformiert) und reichen sie als
 * eigene Geometrie ein. Wie frueher beendet ein Wechsel des Render-Typs den vorigen Abschnitt; die Abschnitte werden in
 * ihrer Reihenfolge gezeichnet (wichtig fuer Tiefen-Abschluesse, die sonst Leuchteffekte verdecken).
 */
public final class DeferredBuffers implements MultiBufferSource {
    private final SubmitNodeCollector collector;
    private final List<RenderType> types = new ArrayList<>();
    private final List<Recorder> recorders = new ArrayList<>();

    public DeferredBuffers(SubmitNodeCollector collector) {
        this.collector = collector;
    }

    /** Fuer Dinge, die eigene Einreichungen brauchen (Items, Blockmodelle). */
    public SubmitNodeCollector collector() {
        return collector;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        int last = types.size() - 1;
        if (last >= 0 && types.get(last) == type) return recorders.get(last);
        Recorder r = new Recorder();
        types.add(type);
        recorders.add(r);
        return r;
    }

    /** Reicht alles Aufgezeichnete ein (Vertices liegen schon im Kameraraum, daher mit leerer Matrix). */
    public void submit() {
        PoseStack identity = new PoseStack();
        for (int i = 0; i < types.size(); i++) {
            Recorder r = recorders.get(i);
            if (r.count == 0) continue;
            collector.order(i).submitCustomGeometry(identity, types.get(i), (pose, vc) -> r.replay(vc));
        }
        types.clear();
        recorders.clear();
    }

    /** Speichert Vertices mit allen gesetzten Attributen. */
    private static final class Recorder implements VertexConsumer {
        private static final int STRIDE = 12; // x y z | Farbe | u v | Overlay | Licht | nx ny nz | Maske
        private float[] data = new float[STRIDE * 64];
        private int count;

        private int base() {
            return (count - 1) * STRIDE;
        }

        private void mark(int bit) {
            int i = base() + 11;
            data[i] = Float.intBitsToFloat(Float.floatToRawIntBits(data[i]) | bit);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if ((count + 1) * STRIDE > data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
            count++;
            int b = base();
            java.util.Arrays.fill(data, b, b + STRIDE, 0.0F);
            data[b] = x;
            data[b + 1] = y;
            data[b + 2] = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            data[base() + 3] = Float.intBitsToFloat((a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF));
            mark(1);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            data[base() + 4] = u;
            data[base() + 5] = v;
            mark(2);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            data[base() + 6] = Float.intBitsToFloat((v & 0xFFFF) << 16 | (u & 0xFFFF));
            mark(4);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            data[base() + 7] = Float.intBitsToFloat((v & 0xFFFF) << 16 | (u & 0xFFFF));
            mark(8);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            data[base() + 8] = x;
            data[base() + 9] = y;
            data[base() + 10] = z;
            mark(16);
            return this;
        }

        void replay(VertexConsumer vc) {
            for (int i = 0; i < count; i++) {
                int b = i * STRIDE;
                int mask = Float.floatToRawIntBits(data[b + 11]);
                vc.addVertex(data[b], data[b + 1], data[b + 2]);
                if ((mask & 1) != 0) {
                    int c = Float.floatToRawIntBits(data[b + 3]);
                    vc.setColor(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24);
                }
                if ((mask & 2) != 0) vc.setUv(data[b + 4], data[b + 5]);
                if ((mask & 4) != 0) {
                    int o = Float.floatToRawIntBits(data[b + 6]);
                    vc.setUv1(o & 0xFFFF, o >>> 16);
                }
                if ((mask & 8) != 0) {
                    int l = Float.floatToRawIntBits(data[b + 7]);
                    vc.setUv2(l & 0xFFFF, l >>> 16);
                }
                if ((mask & 16) != 0) vc.setNormal(data[b + 8], data[b + 9], data[b + 10]);
            }
        }
    }
}
