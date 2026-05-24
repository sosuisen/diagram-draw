package com.sosuisha.classdiagram;

import java.util.Objects;

/**
 * クラス間の依存関係を表す。
 *
 * <p>依存元・依存先の {@link ClassBox} と依存の種類 {@link DependencyType} を格納し、
 * UMLの関係線としてSVGを出力する。
 */
public final class Dependency implements SvgElement {

    private static final int DIAMOND_HALF_LEN = 10;
    private static final int DIAMOND_HALF_WIDTH = 5;
    private static final int TRIANGLE_LEN = 20;
    private static final int TRIANGLE_HALF_WIDTH = 8;
    private static final int ARROWHEAD_LEN = 10;
    private static final double ARROWHEAD_HALF_ANGLE = Math.PI / 6.0; // 30 degrees
    private static final double CURVE_OFFSET_MIN = 30.0;

    private final ClassBox source;
    private final ClassBox target;
    private final DependencyType type;

    /**
     * 依存関係を生成する。
     *
     * @param source 依存元ClassBox
     * @param target 依存先ClassBox
     * @param type 依存の種類
     * @throws NullPointerException source、target、またはtypeがnullの場合
     */
    public Dependency(ClassBox source, ClassBox target, DependencyType type) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(type, "type must not be null");
        this.source = source;
        this.target = target;
        this.type = type;
    }

    /** @return 依存元ClassBox */
    public ClassBox source() { return source; }

    /** @return 依存先ClassBox */
    public ClassBox target() { return target; }

    /** @return 依存の種類 */
    public DependencyType type() { return type; }

    /**
     * 依存関係のSVG表現を返す。
     *
     * <p>アルゴリズム概要:
     * <ol>
     *   <li>ソース中心とターゲット中心を結ぶ方向ベクトルを正規化する。</li>
     *   <li>ソースボックスの辺との交差点を求め、ダイアモンドの中心とする。</li>
     *   <li>ターゲットボックスの辺との交差点を求め、線の終点とする。</li>
     *   <li>ダイアモンドの前端（ターゲット方向）から終点へ線を引く。</li>
     * </ol>
     *
     * @return SVGのgタグ文字列
     */
    @Override
    public String draw() {
        double scx = source.x() + source.width() / 2.0;
        double scy = source.y() + source.height() / 2.0;
        double tcx = target.x() + target.width() / 2.0;
        double tcy = target.y() + target.height() / 2.0;

        double dx = tcx - scx;
        double dy = tcy - scy;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001) {
            return "";
        }
        double nx = dx / len;
        double ny = dy / len;

        double[] sp = edgeIntersection(source, nx, ny);
        double[] tp = edgeIntersection(target, -nx, -ny);
        double[] exitDir = outwardNormal(source, sp[0], sp[1]);
        double[] entryDir = outwardNormal(target, tp[0], tp[1]);

        if (type == DependencyType.REALIZATION) {
            return drawRealization(sp, tp, exitDir, entryDir);
        }

        if (type == DependencyType.DEPENDENCY) {
            return drawDependency(sp, tp, exitDir, entryDir);
        }

        // ダイアモンドの後端をソース辺上に合わせ、外向き法線方向に外側へ伸ばす。
        double diamondCx = sp[0] + exitDir[0] * DIAMOND_HALF_LEN;
        double diamondCy = sp[1] + exitDir[1] * DIAMOND_HALF_LEN;
        double[] diamond = calcDiamond(diamondCx, diamondCy, exitDir[0], exitDir[1]);
        // 曲線の始点はダイアモンドの前端
        double lineX1 = diamondCx + exitDir[0] * DIAMOND_HALF_LEN;
        double lineY1 = diamondCy + exitDir[1] * DIAMOND_HALF_LEN;

        var sb = new StringBuilder();
        sb.append("<g data-diagram-draw=\"dependency\" data-diagram-draw-type=\"%s\">".formatted(type.name().toLowerCase()));
        sb.append(drawDiamond(diamond));
        sb.append("<path d=\"%s\" fill=\"none\" stroke=\"black\"/>".formatted(
            cubicBezierPath(lineX1, lineY1, exitDir, tp[0], tp[1], entryDir)));
        sb.append("</g>");
        return sb.toString();
    }

    /**
     * 矩形辺上の点 {@code (px, py)} における外向き法線ベクトルを返す。
     */
    private static double[] outwardNormal(ClassBox box, double px, double py) {
        double tol = 0.5;
        if (Math.abs(px - box.x()) < tol) return new double[]{-1.0, 0.0};
        if (Math.abs(px - (box.x() + box.width())) < tol) return new double[]{1.0, 0.0};
        if (Math.abs(py - box.y()) < tol) return new double[]{0.0, -1.0};
        if (Math.abs(py - (box.y() + box.height())) < tol) return new double[]{0.0, 1.0};
        return new double[]{0.0, 1.0};
    }

    /**
     * 2 点間を結ぶ三次ベジエ曲線の SVG path コマンド文字列を返す。
     * 制御点は始点・終点それぞれの外向き法線方向に同じオフセットだけ突き出す。これにより
     * 曲線は両端で辺に垂直な方向に出入りし、中央で滑らかに向きが変わる（dagre/d3 風）。
     */
    private static String cubicBezierPath(
            double sx, double sy, double[] exitDir,
            double ex, double ey, double[] entryDir) {
        double dx = ex - sx;
        double dy = ey - sy;
        double exitProj = Math.abs(dx * exitDir[0] + dy * exitDir[1]);
        double entryProj = Math.abs(dx * entryDir[0] + dy * entryDir[1]);
        double offset = Math.max(CURVE_OFFSET_MIN, Math.max(exitProj, entryProj) / 2.0);
        double c1x = sx + exitDir[0] * offset;
        double c1y = sy + exitDir[1] * offset;
        double c2x = ex + entryDir[0] * offset;
        double c2y = ey + entryDir[1] * offset;
        return "M %.1f,%.1f C %.1f,%.1f %.1f,%.1f %.1f,%.1f".formatted(
            sx, sy, c1x, c1y, c2x, c2y, ex, ey);
    }

    /**
     * ボックスの中心から方向(dirX, dirY)に向かう光線が、ボックスの辺と最初に交差する点を返す。
     *
     * <p>アルゴリズム概要（パラメトリック光線-矩形交差）:
     * <ul>
     *   <li>光線: P(t) = center + t * direction (t &gt; 0)</li>
     *   <li>左辺: t = (box.x − cx) / dirX</li>
     *   <li>右辺: t = (box.x + box.width − cx) / dirX</li>
     *   <li>上辺: t = (box.y − cy) / dirY</li>
     *   <li>下辺: t = (box.y + box.height − cy) / dirY</li>
     *   <li>各 t について交点が辺内に収まるか確認し、最小の正の t を採用する。</li>
     * </ul>
     *
     * @param box 対象のClassBox
     * @param dirX 方向ベクトルのX成分（正規化済み）
     * @param dirY 方向ベクトルのY成分（正規化済み）
     * @return ボックス辺上の交差点 [x, y]
     */
    private static double[] edgeIntersection(ClassBox box, double dirX, double dirY) {
        double cx = box.x() + box.width() / 2.0;
        double cy = box.y() + box.height() / 2.0;
        double minT = Double.MAX_VALUE;

        if (Math.abs(dirX) > 1e-9) {
            double t = (box.x() - cx) / dirX;
            if (t > 1e-9) {
                double iy = cy + t * dirY;
                if (iy >= box.y() && iy <= box.y() + box.height()) {
                    minT = Math.min(minT, t);
                }
            }
            t = (box.x() + box.width() - cx) / dirX;
            if (t > 1e-9) {
                double iy = cy + t * dirY;
                if (iy >= box.y() && iy <= box.y() + box.height()) {
                    minT = Math.min(minT, t);
                }
            }
        }

        if (Math.abs(dirY) > 1e-9) {
            double t = (box.y() - cy) / dirY;
            if (t > 1e-9) {
                double ix = cx + t * dirX;
                if (ix >= box.x() && ix <= box.x() + box.width()) {
                    minT = Math.min(minT, t);
                }
            }
            t = (box.y() + box.height() - cy) / dirY;
            if (t > 1e-9) {
                double ix = cx + t * dirX;
                if (ix >= box.x() && ix <= box.x() + box.width()) {
                    minT = Math.min(minT, t);
                }
            }
        }

        return new double[]{ cx + minT * dirX, cy + minT * dirY };
    }

    private double[] calcDiamond(double cx, double cy, double nx, double ny) {
        double px = -ny;
        double py = nx;
        return new double[]{
            cx + nx * DIAMOND_HALF_LEN, cy + ny * DIAMOND_HALF_LEN,
            cx + px * DIAMOND_HALF_WIDTH, cy + py * DIAMOND_HALF_WIDTH,
            cx - nx * DIAMOND_HALF_LEN, cy - ny * DIAMOND_HALF_LEN,
            cx - px * DIAMOND_HALF_WIDTH, cy - py * DIAMOND_HALF_WIDTH
        };
    }

    private String drawDiamond(double[] pts) {
        String fill = type == DependencyType.COMPOSITION ? "black" : "none";
        return "<polygon points=\"%.1f,%.1f %.1f,%.1f %.1f,%.1f %.1f,%.1f\" fill=\"%s\" stroke=\"black\"/>".formatted(
            pts[0], pts[1], pts[2], pts[3], pts[4], pts[5], pts[6], pts[7], fill);
    }

    private String drawRealization(double[] sp, double[] tp, double[] exitDir, double[] entryDir) {
        // 三角形は曲線の入射方向（= -entryDir）に沿って配置する。基線は入射方向に直交。
        double px = -entryDir[1];
        double py = entryDir[0];
        double baseCx = tp[0] + entryDir[0] * TRIANGLE_LEN;
        double baseCy = tp[1] + entryDir[1] * TRIANGLE_LEN;
        double bx1 = baseCx + px * TRIANGLE_HALF_WIDTH;
        double by1 = baseCy + py * TRIANGLE_HALF_WIDTH;
        double bx2 = baseCx - px * TRIANGLE_HALF_WIDTH;
        double by2 = baseCy - py * TRIANGLE_HALF_WIDTH;

        var sb = new StringBuilder();
        sb.append("<g data-diagram-draw=\"dependency\" data-diagram-draw-type=\"%s\">".formatted(type.name().toLowerCase()));
        sb.append("<path d=\"%s\" fill=\"none\" stroke=\"black\" stroke-dasharray=\"8,4\"/>".formatted(
            cubicBezierPath(sp[0], sp[1], exitDir, baseCx, baseCy, entryDir)));
        sb.append("<polygon points=\"%.1f,%.1f %.1f,%.1f %.1f,%.1f\" fill=\"white\" stroke=\"black\"/>".formatted(
            tp[0], tp[1], bx1, by1, bx2, by2));
        sb.append("</g>");
        return sb.toString();
    }

    private String drawDependency(double[] sp, double[] tp, double[] exitDir, double[] entryDir) {
        // 矢頭はターゲットへの入射方向（= -entryDir）を指す。
        double angle = Math.atan2(-entryDir[1], -entryDir[0]);
        double ax1 = tp[0] - ARROWHEAD_LEN * Math.cos(angle - ARROWHEAD_HALF_ANGLE);
        double ay1 = tp[1] - ARROWHEAD_LEN * Math.sin(angle - ARROWHEAD_HALF_ANGLE);
        double ax2 = tp[0] - ARROWHEAD_LEN * Math.cos(angle + ARROWHEAD_HALF_ANGLE);
        double ay2 = tp[1] - ARROWHEAD_LEN * Math.sin(angle + ARROWHEAD_HALF_ANGLE);

        var sb = new StringBuilder();
        sb.append("<g data-diagram-draw=\"dependency\" data-diagram-draw-type=\"%s\">".formatted(type.name().toLowerCase()));
        sb.append("<path d=\"%s\" fill=\"none\" stroke=\"black\" stroke-dasharray=\"8,4\"/>".formatted(
            cubicBezierPath(sp[0], sp[1], exitDir, tp[0], tp[1], entryDir)));
        sb.append("<polyline points=\"%.1f,%.1f %.1f,%.1f %.1f,%.1f\" fill=\"none\" stroke=\"black\"/>".formatted(
            ax1, ay1, tp[0], tp[1], ax2, ay2));
        sb.append("</g>");
        return sb.toString();
    }
}
