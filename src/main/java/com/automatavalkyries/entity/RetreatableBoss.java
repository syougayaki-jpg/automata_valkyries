package com.automatavalkyries.entity;

/** 退く演出を持つボス。撤退の号令（{@code item/RetreatOrderItem}）で退かせる */
public interface RetreatableBoss {

    /** 退く演出を始める。もう退いている・倒れている・倒れかけているなら何もせず false */
    boolean beginRetreat();
}
