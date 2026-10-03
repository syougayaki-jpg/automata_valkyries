package com.automatavalkyries.weapon;

/**
 * 星葬冠『ルミナス・レクイエム』の背の光輪と 6 振りの剣を描くためだけのアイテム（手に入らない。クリエイティブのタブにも並べない）。
 * 持ち主（プレイヤーでも兵姫でも）がルミナス・レクイエムを持っている間、背に描く（{@code client/RequiemBackdropClient}）。
 */
public class RequiemBackdropItem extends TacticalWeaponItem {

    public RequiemBackdropItem(Properties properties) {
        super(properties, 0, 0F, false);
    }
}
