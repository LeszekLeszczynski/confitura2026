package com.decerto.leszek.confitura2026.demo02_table;

import org.openjdk.jol.info.GraphLayout;
import java.time.LocalDate;

public class ObjectTable {

    public static void main(String[] args) {
        LocalDate[] dates = new LocalDate[1_000_000];
        for (int i = 0; i < dates.length; i++) {
            dates[i] = LocalDate.of(2000 + i % 27, 1 + i % 12, 1 + i % 28);
        }

        System.out.println(GraphLayout.parseInstance((Object) dates).totalSize());
        System.out.println(GraphLayout.parseInstance((Object) dates).toFootprint());

    }
}
