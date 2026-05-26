package com.sosuisha.classdiagram.intention;

/** 配置制約の方向を表す列挙型。 */
public enum PlaceDirection {
    /** ターゲットを基準クラスの上のレイヤーに配置する。 */
    ABOVE,
    /** ターゲットを基準クラスの下のレイヤーに配置する。 */
    BELOW,
    /** ターゲットを基準クラスの右に配置する（同一レイヤー内）。 */
    RIGHT_OF,
    /** ターゲットを基準クラスの左に配置する（同一レイヤー内）。 */
    LEFT_OF
}
