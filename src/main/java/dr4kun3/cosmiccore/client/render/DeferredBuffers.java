package dr4kun3.cosmiccore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dr4kun3.cosmiccore.client.render.MultiBufferSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Seit Minecraft 1.21.9 wird nicht mehr direkt gezeichnet, sondern Geometrie beim {@link SubmitNodeCollector} eingereicht.
 * Diese Puffer nehmen die Vertices der bestehenden Zeichenroutinen auf (bereits fertig transformiert) und reichen sie als
 * eigene Geometrie ein. Wie früher beendet ein Wechsel des Render-Typs den vorigen Abschnitt; die Abschnitte werden in
 * ihrer Reihenfolge gezeichnet (wichtig für Tiefen-Abschlüsse, die sonst Leuchteffekte verdecken).
 */
public final class DeferredBuffers implements MultiBufferSource {
    private final SubmitNodeCollector collector;
    private final List<RenderType> types = new ArrayList<>();
    private final List<Recorder> recorders = new ArrayList<>();

    private final int orderBase;
    private boolean closed;

    @Override
    public void endBatch(RenderType type) {
        closed = true;
    }

    /**
     * Legt die Puffer für einen Sammler an (Reihenfolge ab 0).
     *
     * @param collector Sammler der Zeichenaufträge
     */
    public DeferredBuffers(SubmitNodeCollector collector) {
        this(collector, 0);
    }

    /**
     * Legt die Puffer für einen Sammler an.
     *
     * @param collector Sammler der Zeichenaufträge
     * @param orderBase Zeichenreihenfolge des ersten Abschnitts (kleiner = früher, z. B. Kulissen hinter allem)
     */
    public DeferredBuffers(SubmitNodeCollector collector, int orderBase) {
        this.collector = collector;
        this.orderBase = orderBase;
    }

    /**
     * Für Dinge, die eigene Einreichungen brauchen (Items, Blockmodelle).
     *
     * @return der Sammler
     */
    public SubmitNodeCollector collector() {
        return collector;
    }

    /**
     * Puffer für einen Render-Typ; ein Wechsel des Typs beginnt einen neuen Abschnitt.
     *
     * @param type Render-Typ
     * @return der aufzeichnende Puffer
     */
    @Override
    public VertexConsumer getBuffer(RenderType type) {
        int last = types.size() - 1;
        if (last >= 0 && types.get(last) == type && !closed) return recorders.get(last);
        closed = false;
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
            collector.order(orderBase + i).submitCustomGeometry(identity, types.get(i), (pose, vc) -> r.replay(vc));
        }
        types.clear();
        recorders.clear();
    }

    /** Speichert Vertices mit allen gesetzten Attributen. */
    private static final class Recorder implements VertexConsumer {
        private static final int STRIDE = 13; // x y z | Farbe | u v | Overlay | Licht | nx ny nz | Maske | Linienbreite
        private float[] data = new float[STRIDE * 64];
        private int count;

        /**
         * Startindex des aktuellen Vertex im Datenfeld.
         *
         * @return Index
         */
        private int base() {
            return (count - 1) * STRIDE;
        }

        /**
         * Merkt, dass ein Attribut am aktuellen Vertex gesetzt wurde.
         *
         * @param bit Kennbit des Attributs
         */
        private void mark(int bit) {
            int i = base() + 11;
            data[i] = Float.intBitsToFloat(Float.floatToRawIntBits(data[i]) | bit);
        }

        /**
         * Beginnt einen neuen Vertex.
         *
         * @param x Position x
         * @param y Position y
         * @param z Position z
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if ((count + 1) * STRIDE > data.length) data = Arrays.copyOf(data, data.length * 2);
            count++;
            int b = base();
            Arrays.fill(data, b, b + STRIDE, 0.0F);
            data[b] = x;
            data[b + 1] = y;
            data[b + 2] = z;
            return this;
        }

        /**
         * Farbe des aktuellen Vertex.
         *
         * @param r Rot 0..255
         * @param g Grün 0..255
         * @param b Blau 0..255
         * @param a Deckkraft 0..255
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            data[base() + 3] = Float.intBitsToFloat((a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF));
            mark(1);
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
        }

        /**
         * Texturkoordinate des aktuellen Vertex.
         *
         * @param u u-Koordinate
         * @param v v-Koordinate
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer setUv(float u, float v) {
            data[base() + 4] = u;
            data[base() + 5] = v;
            mark(2);
            return this;
        }

        /**
         * Overlay-Koordinate des aktuellen Vertex.
         *
         * @param u u-Wert
         * @param v v-Wert
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer setUv1(int u, int v) {
            data[base() + 6] = Float.intBitsToFloat((v & 0xFFFF) << 16 | (u & 0xFFFF));
            mark(4);
            return this;
        }

        /**
         * Lichtkoordinate des aktuellen Vertex.
         *
         * @param u Blocklicht
         * @param v Himmelslicht
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer setUv2(int u, int v) {
            data[base() + 7] = Float.intBitsToFloat((v & 0xFFFF) << 16 | (u & 0xFFFF));
            mark(8);
            return this;
        }

        /**
         * Normale des aktuellen Vertex.
         *
         * @param x Normale x
         * @param y Normale y
         * @param z Normale z
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            data[base() + 8] = x;
            data[base() + 9] = y;
            data[base() + 10] = z;
            mark(16);
            return this;
        }

        /**
         * Linienbreite des aktuellen Vertex.
         *
         * @param width Breite in Pixeln
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer setLineWidth(float width) {
            data[base() + 12] = width;
            mark(32);
            return this;
        }

        /**
         * Spielt alle Vertices mit ihren gesetzten Attributen in einen echten Puffer ab.
         *
         * @param vc Zielpuffer
         */
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
                if ((mask & 32) != 0) vc.setLineWidth(data[b + 12]);
            }
        }
    }
}
