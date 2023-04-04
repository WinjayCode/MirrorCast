package com.winjay.mirrorcast;

import java.text.DecimalFormat;

/**
 * @author F2848777
 * @date 2022-11-22
 */
public class Test {
    public static void main(String[] args) {
        int a = 2400;
        int b = 1736;
        DecimalFormat decimalFormat = new DecimalFormat("#.00");
        String c = decimalFormat.format((float)a / (float)b);
        System.out.println("c=" + c);
    }
}
