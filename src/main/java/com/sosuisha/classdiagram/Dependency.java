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

        // ダイアモンドの後端をソース辺上に合わせ、全体をボックス外に配置する
        double diamondCx = sp[0] + nx * DIAMOND_HALF_LEN;
        double diamondCy = sp[1] + ny * DIAMOND_HALF_LEN;
        double[] diamond = calcDiamond(diamondCx, diamondCy, nx, ny);
        // 線の始点はダイアモンドの前端
        double lineX1 = diamondCx + nx * DIAMOND_HALF_LEN;
        double lineY1 = diamondCy + ny * DIAMOND_HALF_LEN;

        var sb = new StringBuilder();
        sb.append("<g data-diagram-draw=\"dependency\" data-diagram-draw-type=\"%s\">".formatted(type.name().toLowerCase()));
        sb.append(drawDiamond(diamond));
        sb.append("<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"black\"/>".formatted(lineX1, lineY1, tp[0], tp[1]));
        sb.append("</g>");
        return sb.toString();
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
}
