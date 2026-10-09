package de.hugosmp.auction.client;

import de.hugosmp.auction.net.AuctionView;

import java.util.List;

/** Letzter vom Server empfangener Stand. */
public final class AuctionClient {
    public static volatile List<AuctionView> auctions = List.of();
    public static volatile long receivedAt = System.currentTimeMillis();
    public static volatile int version = 0;

    private AuctionClient() {}

    public static void update(List<AuctionView> list) {
        auctions = list;
        receivedAt = System.currentTimeMillis();
        version++;
    }
}
