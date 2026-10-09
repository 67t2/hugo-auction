package de.hugosmp.auction.client;

import de.hugosmp.auction.net.AuctionView;
import de.hugosmp.auction.net.BidPayload;
import de.hugosmp.auction.net.BidView;
import de.hugosmp.auction.net.CreatePayload;
import de.hugosmp.auction.net.EndPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

/**
 * Minimalistisches Auktionshaus-Menü.
 * Links: Liste der Auktionen. Rechts: Details + nummerierte Bieterliste.
 * Oben rechts: Umschalter zum "Item versteigern"-Bereich.
 */
public class AuctionScreen extends Screen {
    private static final int W = 420, H = 236;
    private static final int ACCENT = 0xFFF2C14E;
    private static final int TEXT = 0xFFE8EAF0;
    private static final int MUTED = 0xFF8A93A5;
    private static final int GREEN = 0xFF5BE49B;
    private static final int RED = 0xFFFF6B6B;
    private static final int LINE = 0xFF2A2F3A;
    private static final int[] DURATIONS = {1, 5, 10, 30, 60};
    private static final int ROWS = 6;

    private boolean createMode = false;
    private int selectedId = -1;
    private int scroll = 0;
    private int durIdx = 1;
    private int seenVersion;
    private String bidText = "";
    private String minText = "0";
    private boolean bidFocused = false;
    private long copiedUntil = 0;

    private TextFieldWidget bidField;
    private TextFieldWidget minField;
    private ItemStack held = ItemStack.EMPTY;
    private int left, top;

    public AuctionScreen() {
        super(Text.literal("6-7 T2 Auction House"));
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        seenVersion = AuctionClient.version;
        held = client.player.getMainHandStack().copy();
        bidField = null;
        minField = null;

        addDrawableChild(ButtonWidget.builder(Text.literal(createMode ? "< Zurück" : "+ Versteigern"), b -> {
            saveFields();
            createMode = !createMode;
            clearAndInit();
        }).dimensions(left + W - 104, top + 9, 94, 18).build());

        if (createMode) initCreate();
        else initBrowse();
    }

    private void saveFields() {
        if (bidField != null) {
            bidText = bidField.getText();
            bidFocused = bidField.isFocused();
        }
        if (minField != null) minText = minField.getText();
    }

    private void initBrowse() {
        List<AuctionView> list = AuctionClient.auctions;
        if (selectedId == -1 || find(selectedId) == null) {
            selectedId = list.isEmpty() ? -1 : list.get(0).id();
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - ROWS)));

        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            if (idx >= list.size()) break;
            AuctionView a = list.get(idx);
            int y = top + 40 + i * 30;
            String top1 = a.bids().isEmpty() ? "-" : fmt(a.bids().get(0).amount());
            String label = textRenderer.trimToWidth(a.stack().getName().getString(), 88) + "  " + top1;
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                saveFields();
                selectedId = a.id();
                clearAndInit();
            }).dimensions(left + 34, y, 150, 24).build());
        }
        if (list.size() > ROWS) {
            addDrawableChild(ButtonWidget.builder(Text.literal("^"), b -> {
                saveFields();
                scroll = Math.max(0, scroll - 1);
                clearAndInit();
            }).dimensions(left + 188, top + 40, 16, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("v"), b -> {
                saveFields();
                scroll = Math.min(list.size() - ROWS, scroll + 1);
                clearAndInit();
            }).dimensions(left + 188, top + 40 + (ROWS - 1) * 30 + 4, 16, 20).build());
        }

        AuctionView sel = find(selectedId);
        if (sel == null) return;
        boolean mine = sel.sellerName().equals(myName());
        int fy = top + H - 52;

        if (mine) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Auktion jetzt beenden"), b ->
                    ClientPlayNetworking.send(new EndPayload(sel.id())))
                    .dimensions(left + 215, fy, 195, 18).build());
        } else {
            bidField = new TextFieldWidget(textRenderer, left + 215, fy, 100, 18, Text.literal("Gebot"));
            bidField.setMaxLength(12);
            bidField.setTextPredicate(s -> s.matches("\\d*"));
            bidField.setPlaceholder(Text.literal("Dein Gebot"));
            bidField.setText(bidText);
            addDrawableChild(bidField);
            addDrawableChild(ButtonWidget.builder(Text.literal("Bieten"), b -> {
                String s = bidField.getText();
                if (s.isEmpty()) return;
                ClientPlayNetworking.send(new BidPayload(sel.id(), Long.parseLong(s)));
                bidText = "";
                bidField.setText("");
            }).dimensions(left + 320, fy, 90, 18).build());
            if (bidFocused) setFocused(bidField);
        }

        if (!sel.bids().isEmpty()) {
            String winner = sel.bids().get(0).name();
            addDrawableChild(ButtonWidget.builder(Text.literal("Namen des Höchstbietenden kopieren"), b -> {
                client.keyboard.setClipboard(winner);
                copiedUntil = System.currentTimeMillis() + 1800;
            }).dimensions(left + 215, fy + 22, 195, 18).build());
        }
    }

    private void initCreate() {
        minField = new TextFieldWidget(textRenderer, left + 215, top + 80, 195, 18, Text.literal("Mindestgebot"));
        minField.setMaxLength(12);
        minField.setTextPredicate(s -> s.matches("\\d*"));
        minField.setPlaceholder(Text.literal("0 = kein Mindestgebot"));
        minField.setText(minText);
        addDrawableChild(minField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Dauer: " + DURATIONS[durIdx] + " Min."), b -> {
            saveFields();
            durIdx = (durIdx + 1) % DURATIONS.length;
            clearAndInit();
        }).dimensions(left + 215, top + 120, 195, 18).build());

        ButtonWidget start = ButtonWidget.builder(Text.literal("Auktion starten"), b -> {
            String s = minField.getText();
            long min = s.isEmpty() ? 0 : Long.parseLong(s);
            ClientPlayNetworking.send(new CreatePayload(min, DURATIONS[durIdx]));
            close();
        }).dimensions(left + 215, top + H - 44, 195, 24).build();
        start.active = !held.isEmpty();
        addDrawableChild(start);
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        super.renderBackground(ctx, mx, my, delta);
        ctx.fill(left, top, left + W, top + H, 0xF00E1015);
        ctx.drawBorder(left, top, W, H, LINE);
        ctx.fill(left, top, left + W, top + 2, ACCENT);
        ctx.fill(left + 12, top + 32, left + W - 12, top + 33, LINE);
        ctx.fill(left + 207, top + 40, left + 208, top + H - 12, LINE);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);

        ctx.drawText(textRenderer, Text.literal("6-7 T2 Auction House").formatted(Formatting.BOLD),
                left + 14, top + 14, ACCENT, true);

        ItemStack tooltipStack = null;
        if (createMode) tooltipStack = renderCreate(ctx, mx, my);
        else tooltipStack = renderBrowse(ctx, mx, my);

        if (tooltipStack != null) ctx.drawItemTooltip(textRenderer, tooltipStack, mx, my);
    }

    private ItemStack renderBrowse(DrawContext ctx, int mx, int my) {
        List<AuctionView> list = AuctionClient.auctions;
        ItemStack tip = null;

        if (list.isEmpty()) {
            ctx.drawText(textRenderer, "Keine aktiven Auktionen", left + 14, top + 46, MUTED, false);
            ctx.drawText(textRenderer, "Starte oben rechts eine neue.", left + 14, top + 58, MUTED, false);
        }
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            if (idx >= list.size()) break;
            AuctionView a = list.get(idx);
            int y = top + 40 + i * 30;
            if (a.id() == selectedId) ctx.fill(left + 8, y, left + 10, y + 24, ACCENT);
            ctx.drawItem(a.stack(), left + 14, y + 4);
            ctx.drawStackOverlay(textRenderer, a.stack(), left + 14, y + 4);
        }

        AuctionView sel = find(selectedId);
        int rx = left + 215;
        if (sel == null) {
            ctx.drawText(textRenderer, "Wähle eine Auktion aus.", rx, top + 46, MUTED, false);
            return null;
        }

        int y = top + 40;
        ctx.drawItem(sel.stack(), rx, y);
        ctx.drawStackOverlay(textRenderer, sel.stack(), rx, y);
        if (mx >= rx && mx < rx + 16 && my >= y && my < y + 16) tip = sel.stack();
        ctx.drawText(textRenderer, textRenderer.trimToWidth(sel.stack().getName().getString(), 170),
                rx + 22, y, TEXT, true);
        ctx.drawText(textRenderer, "von " + sel.sellerName(), rx + 22, y + 9, MUTED, false);

        ctx.drawText(textRenderer, "Mindestgebot: " + (sel.minBid() == 0 ? "keins" : fmt(sel.minBid())),
                rx, top + 64, MUTED, false);
        long rem = sel.remainingMs() - (System.currentTimeMillis() - AuctionClient.receivedAt);
        String time = rem <= 0 ? "Wird beendet ..." : "Endet in " + clock(rem);
        ctx.drawText(textRenderer, time, rx, top + 75, rem < 30_000 ? RED : MUTED, false);

        ctx.drawText(textRenderer, "Gebote", rx, top + 92, ACCENT, false);
        ctx.fill(rx, top + 102, rx + 195, top + 103, LINE);

        List<BidView> bids = sel.bids();
        if (bids.isEmpty()) {
            ctx.drawText(textRenderer, "Noch keine Gebote.", rx, top + 108, MUTED, false);
        }
        int shown = Math.min(bids.size(), 6);
        for (int i = 0; i < shown; i++) {
            BidView b = bids.get(i);
            int ly = top + 107 + i * 10;
            int col = i == 0 ? GREEN : TEXT;
            ctx.drawText(textRenderer, (i + 1) + ". " + textRenderer.trimToWidth(b.name(), 110), rx, ly, col, false);
            String amt = fmt(b.amount());
            ctx.drawText(textRenderer, amt, rx + 195 - textRenderer.getWidth(amt), ly, col, false);
        }
        if (bids.size() > shown) {
            ctx.drawText(textRenderer, "... +" + (bids.size() - shown) + " weitere", rx,
                    top + 107 + shown * 10, MUTED, false);
        }

        if (System.currentTimeMillis() < copiedUntil) {
            ctx.drawText(textRenderer, "Kopiert!", rx + 150, top + H - 52 + 22 + 5, GREEN, false);
        }
        return tip;
    }

    private ItemStack renderCreate(DrawContext ctx, int mx, int my) {
        ItemStack tip = null;
        ctx.drawText(textRenderer, "Item in deiner Hand", left + 14, top + 46, MUTED, false);
        if (held.isEmpty()) {
            ctx.drawText(textRenderer, "Du hältst nichts in der Hand.", left + 14, top + 64, RED, false);
            ctx.drawText(textRenderer, "Nimm ein Item in die Hand und", left + 14, top + 82, MUTED, false);
            ctx.drawText(textRenderer, "drücke K erneut.", left + 14, top + 92, MUTED, false);
        } else {
            ctx.drawItem(held, left + 14, top + 62);
            ctx.drawStackOverlay(textRenderer, held, left + 14, top + 62);
            if (mx >= left + 14 && mx < left + 30 && my >= top + 62 && my < top + 78) tip = held;
            ctx.drawText(textRenderer, textRenderer.trimToWidth(held.getName().getString(), 160),
                    left + 38, top + 62, TEXT, true);
            ctx.drawText(textRenderer, "Menge: " + held.getCount(), left + 38, top + 72, MUTED, false);
        }
        ctx.drawText(textRenderer, "Das Item wird bis zum Ende", left + 14, top + H - 38, MUTED, false);
        ctx.drawText(textRenderer, "der Auktion verwahrt.", left + 14, top + H - 28, MUTED, false);

        ctx.drawText(textRenderer, "Mindestgebot", left + 215, top + 68, ACCENT, false);
        ctx.drawText(textRenderer, "Dauer", left + 215, top + 108, ACCENT, false);
        return tip;
    }

    // ------------------------------------------------------------------ Sonstiges

    @Override
    public void tick() {
        super.tick();
        if (AuctionClient.version != seenVersion) {
            saveFields();
            clearAndInit();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private AuctionView find(int id) {
        for (AuctionView a : AuctionClient.auctions) if (a.id() == id) return a;
        return null;
    }

    private String myName() {
        return client.player.getName().getString();
    }

    private static String fmt(long n) {
        return String.format(Locale.GERMANY, "%,d", n);
    }

    private static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format("%02d:%02d", s / 60, s % 60);
    }
}
