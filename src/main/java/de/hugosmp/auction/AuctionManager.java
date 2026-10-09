package de.hugosmp.auction;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import de.hugosmp.auction.net.AuctionListPayload;
import de.hugosmp.auction.net.AuctionView;
import de.hugosmp.auction.net.BidView;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Serverseitige Logik: verwaltet alle Auktionen, speichert sie in der Welt
 * (hugo_auction.json) und schickt den aktuellen Stand an alle Spieler.
 *
 * Wichtig: Es wird KEIN echtes Geld bewegt. Die Gebote sind Zahlen. Der Gewinner
 * bekommt das Item und soll den Betrag per /pay an den Verkäufer zahlen.
 */
public final class AuctionManager {
    public static final AuctionManager INSTANCE = new AuctionManager();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_PER_PLAYER = 5;
    private static final long MAX_AMOUNT = 999_999_999_999L;

    public static final class Bid {
        UUID uuid;
        String name;
        long amount;
    }

    public static final class Auction {
        int id;
        UUID seller;
        String sellerName;
        ItemStack stack;
        long minBid;
        long endTime;
        List<Bid> bids = new ArrayList<>();
    }

    private record Pending(UUID player, ItemStack stack, String note) {}

    private final List<Auction> auctions = new ArrayList<>();
    private final List<Pending> pending = new ArrayList<>();
    private int nextId = 1;
    private MinecraftServer server;
    private Path file;

    private AuctionManager() {}

    // ------------------------------------------------------------------ Lebenszyklus

    public void start(MinecraftServer server) {
        this.server = server;
        this.file = server.getSavePath(WorldSavePath.ROOT).resolve("hugo_auction.json");
        auctions.clear();
        pending.clear();
        nextId = 1;
        load();
    }

    public void stop(MinecraftServer server) {
        save();
        this.server = null;
    }

    public void tick() {
        if (server == null) return;
        long now = System.currentTimeMillis();
        for (Auction a : new ArrayList<>(auctions)) {
            if (now >= a.endTime) finish(a);
        }
    }

    public void onJoin(ServerPlayerEntity p) {
        if (server == null) return;
        boolean changed = false;
        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending pe = it.next();
            if (pe.player().equals(p.getUuid())) {
                it.remove();
                p.getInventory().offerOrDrop(pe.stack());
                p.sendMessage(Text.literal(pe.note()), false);
                changed = true;
            }
        }
        if (changed) save();
        push(p);
    }

    // ------------------------------------------------------------------ Aktionen

    public void create(ServerPlayerEntity p, long minBid, int minutes) {
        if (server == null) return;
        if (minBid < 0 || minBid > MAX_AMOUNT) {
            err(p, "Ungültiges Mindestgebot.");
            return;
        }
        minutes = Math.max(1, Math.min(1440, minutes));
        long mine = auctions.stream().filter(a -> a.seller.equals(p.getUuid())).count();
        if (mine >= MAX_PER_PLAYER) {
            err(p, "Du kannst maximal " + MAX_PER_PLAYER + " Auktionen gleichzeitig haben.");
            return;
        }
        ItemStack hand = p.getMainHandStack();
        if (hand.isEmpty()) {
            err(p, "Du hältst kein Item in der Hand.");
            return;
        }
        Auction a = new Auction();
        a.id = nextId++;
        a.seller = p.getUuid();
        a.sellerName = p.getName().getString();
        a.stack = hand.copy();
        a.minBid = minBid;
        a.endTime = System.currentTimeMillis() + minutes * 60_000L;
        p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);

        auctions.add(a);
        save();
        pushAll();
        broadcast("§6[Auction] §e" + a.sellerName + " §fversteigert §e" + label(a.stack)
                + " §f– Mindestgebot: §a" + (minBid == 0 ? "keins" : num(minBid))
                + " §f(" + minutes + " Min.) §7– Taste K");
    }

    public void bid(ServerPlayerEntity p, int id, long amount) {
        if (server == null) return;
        Auction a = byId(id);
        if (a == null) {
            err(p, "Diese Auktion existiert nicht mehr.");
            return;
        }
        if (a.seller.equals(p.getUuid())) {
            err(p, "Auf deine eigene Auktion kannst du nicht bieten.");
            return;
        }
        if (System.currentTimeMillis() >= a.endTime) {
            err(p, "Diese Auktion ist bereits beendet.");
            return;
        }
        if (amount < 1 || amount > MAX_AMOUNT) {
            err(p, "Ungültiges Gebot.");
            return;
        }
        if (amount < a.minBid) {
            err(p, "Das Mindestgebot ist " + num(a.minBid) + ".");
            return;
        }
        Bid top = a.bids.isEmpty() ? null : a.bids.get(0);
        if (top != null && amount <= top.amount) {
            err(p, "Du musst mehr als " + num(top.amount) + " bieten.");
            return;
        }

        Bid mine = null;
        for (Bid b : a.bids) {
            if (b.uuid.equals(p.getUuid())) {
                mine = b;
                break;
            }
        }
        if (mine == null) {
            mine = new Bid();
            mine.uuid = p.getUuid();
            a.bids.add(mine);
        }
        mine.name = p.getName().getString();
        mine.amount = amount;
        a.bids.sort((x, y) -> Long.compare(y.amount, x.amount));

        save();
        pushAll();

        p.sendMessage(Text.literal("§aGebot platziert: " + num(amount)), true);
        if (top != null && !top.uuid.equals(p.getUuid())) {
            ServerPlayerEntity prev = server.getPlayerManager().getPlayer(top.uuid);
            if (prev != null) {
                prev.sendMessage(Text.literal("§6[Auction] §fDu wurdest bei §e" + label(a.stack)
                        + " §füberboten: §a" + num(amount)), false);
            }
        }
        ServerPlayerEntity seller = server.getPlayerManager().getPlayer(a.seller);
        if (seller != null) {
            seller.sendMessage(Text.literal("§6[Auction] §e" + mine.name + " §fbietet §a" + num(amount)
                    + " §fauf §e" + label(a.stack)), true);
        }
    }

    public void end(ServerPlayerEntity p, int id) {
        if (server == null) return;
        Auction a = byId(id);
        if (a == null) return;
        if (!a.seller.equals(p.getUuid())) {
            err(p, "Das ist nicht deine Auktion.");
            return;
        }
        finish(a);
    }

    private void finish(Auction a) {
        if (!auctions.remove(a)) return;
        if (a.bids.isEmpty()) {
            deliver(a.seller, a.stack, "§6[Auction] §fKeine Gebote – du hast dein Item zurückbekommen.");
            broadcast("§6[Auction] §fDie Auktion von §e" + a.sellerName + " §f(§e" + label(a.stack)
                    + "§f) endete ohne Gebote.");
        } else {
            Bid w = a.bids.get(0);
            deliver(w.uuid, a.stack, "§6[Auction] §aDu hast gewonnen: §e" + label(a.stack)
                    + " §7– bitte zahle §a" + num(w.amount) + " §7an §e" + a.sellerName + " §7(/pay).");
            broadcast("§6[Auction] §e" + w.name + " §fgewinnt §e" + label(a.stack) + " §fvon §e" + a.sellerName
                    + " §ffür §a" + num(w.amount) + "§f. §7/pay " + a.sellerName + " " + w.amount);
        }
        save();
        pushAll();
    }

    // ------------------------------------------------------------------ Hilfsfunktionen

    private Auction byId(int id) {
        for (Auction a : auctions) if (a.id == id) return a;
        return null;
    }

    private void deliver(UUID uuid, ItemStack stack, String note) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        if (p != null) {
            p.getInventory().offerOrDrop(stack);
            p.sendMessage(Text.literal(note), false);
        } else {
            pending.add(new Pending(uuid, stack, note));
        }
    }

    private void err(ServerPlayerEntity p, String msg) {
        p.sendMessage(Text.literal("§c" + msg), true);
    }

    private void broadcast(String msg) {
        server.getPlayerManager().broadcast(Text.literal(msg), false);
    }

    private static String num(long n) {
        return String.format(Locale.GERMANY, "%,d", n);
    }

    private static String label(ItemStack s) {
        return s.getCount() + "x " + s.getName().getString();
    }

    private void pushAll() {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) push(p);
    }

    private void push(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, AuctionListPayload.ID)) return;
        long now = System.currentTimeMillis();
        List<AuctionView> views = new ArrayList<>();
        for (Auction a : auctions) {
            List<BidView> bv = new ArrayList<>();
            for (Bid b : a.bids) bv.add(new BidView(b.name, b.amount));
            views.add(new AuctionView(a.id, a.sellerName, a.stack.copy(), a.minBid,
                    Math.max(0, a.endTime - now), bv));
        }
        ServerPlayNetworking.send(p, new AuctionListPayload(views));
    }

    // ------------------------------------------------------------------ Speichern / Laden

    private JsonElement encode(ItemStack s) {
        return ItemStack.CODEC.encodeStart(server.getRegistryManager().getOps(JsonOps.INSTANCE), s).getOrThrow();
    }

    private ItemStack decode(JsonElement e) {
        return ItemStack.CODEC.parse(server.getRegistryManager().getOps(JsonOps.INSTANCE), e)
                .result().orElse(ItemStack.EMPTY);
    }

    private void save() {
        if (server == null || file == null) return;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("nextId", nextId);

            JsonArray as = new JsonArray();
            for (Auction a : auctions) {
                JsonObject o = new JsonObject();
                o.addProperty("id", a.id);
                o.addProperty("seller", a.seller.toString());
                o.addProperty("sellerName", a.sellerName);
                o.add("item", encode(a.stack));
                o.addProperty("minBid", a.minBid);
                o.addProperty("endTime", a.endTime);
                JsonArray bs = new JsonArray();
                for (Bid b : a.bids) {
                    JsonObject bo = new JsonObject();
                    bo.addProperty("uuid", b.uuid.toString());
                    bo.addProperty("name", b.name);
                    bo.addProperty("amount", b.amount);
                    bs.add(bo);
                }
                o.add("bids", bs);
                as.add(o);
            }
            root.add("auctions", as);

            JsonArray ps = new JsonArray();
            for (Pending pe : pending) {
                JsonObject o = new JsonObject();
                o.addProperty("player", pe.player().toString());
                o.add("item", encode(pe.stack()));
                o.addProperty("note", pe.note());
                ps.add(o);
            }
            root.add("pending", ps);

            Files.writeString(file, GSON.toJson(root));
        } catch (Exception e) {
            HugoAuction.LOGGER.error("Konnte Auktionen nicht speichern", e);
        }
    }

    private void load() {
        if (file == null || !Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            nextId = root.get("nextId").getAsInt();

            for (JsonElement el : root.getAsJsonArray("auctions")) {
                JsonObject o = el.getAsJsonObject();
                Auction a = new Auction();
                a.id = o.get("id").getAsInt();
                a.seller = UUID.fromString(o.get("seller").getAsString());
                a.sellerName = o.get("sellerName").getAsString();
                a.stack = decode(o.get("item"));
                a.minBid = o.get("minBid").getAsLong();
                a.endTime = o.get("endTime").getAsLong();
                for (JsonElement be : o.getAsJsonArray("bids")) {
                    JsonObject bo = be.getAsJsonObject();
                    Bid b = new Bid();
                    b.uuid = UUID.fromString(bo.get("uuid").getAsString());
                    b.name = bo.get("name").getAsString();
                    b.amount = bo.get("amount").getAsLong();
                    a.bids.add(b);
                }
                a.bids.sort((x, y) -> Long.compare(y.amount, x.amount));
                if (!a.stack.isEmpty()) auctions.add(a);
            }
            for (JsonElement el : root.getAsJsonArray("pending")) {
                JsonObject o = el.getAsJsonObject();
                ItemStack s = decode(o.get("item"));
                if (!s.isEmpty()) {
                    pending.add(new Pending(UUID.fromString(o.get("player").getAsString()), s,
                            o.get("note").getAsString()));
                }
            }
        } catch (Exception e) {
            HugoAuction.LOGGER.error("Konnte Auktionen nicht laden", e);
        }
    }
}
